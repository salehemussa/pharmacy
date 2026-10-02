package com.pharmacy.prescription;

import com.pharmacy.audit.AuditService;
import com.pharmacy.catalog.CatalogService;
import com.pharmacy.catalog.Medicine;
import com.pharmacy.common.BusinessException;
import com.pharmacy.common.PageResponse;
import com.pharmacy.common.Pages;
import com.pharmacy.common.PrescriptionStatus;
import com.pharmacy.common.ReferenceGenerator;
import com.pharmacy.common.ReferenceType;
import com.pharmacy.common.SearchText;
import com.pharmacy.common.StockMovementType;
import com.pharmacy.customer.Customer;
import com.pharmacy.customer.CustomerService;
import com.pharmacy.inventory.Batch;
import com.pharmacy.inventory.InventoryService;
import com.pharmacy.security.AuthContext;
import com.pharmacy.security.CurrentUserService;
import com.pharmacy.settings.SettingsService;
import com.pharmacy.user.UserAccount;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final PrescriptionRepository prescriptions;
    private final DispensingRepository dispensings;
    private final CustomerService customers;
    private final CatalogService catalogService;
    private final InventoryService inventoryService;
    private final CurrentUserService currentUserService;
    private final SettingsService settingsService;
    private final ReferenceGenerator references;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<PrescriptionSummary> list(String q, PrescriptionStatus status, Long customerId, int page, int size) {
        Specification<Prescription> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!SearchText.blank(q)) {
                String like = SearchText.like(q);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("reference")), like, '\\'),
                        cb.like(cb.lower(root.get("customer").get("fullName")), like, '\\')
                ));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), customerId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(prescriptions.findAll(spec, Pages.of(page, size, Sort.by(Sort.Direction.DESC, "prescriptionDate", "id"))).map(PrescriptionSummary::from));
    }

    @Transactional(readOnly = true)
    public PrescriptionResponse get(Long id) {
        return PrescriptionResponse.from(load(id));
    }

    @Transactional
    public PrescriptionResponse create(PrescriptionRequest request) {
        Prescription prescription = new Prescription();
        prescription.setReference(references.next("RX", settingsService.today()));
        prescription.setCreatedBy(currentUserService.requireEntity());
        prescription.setStatus(PrescriptionStatus.PENDING);
        apply(prescription, request);
        prescriptions.save(prescription);
        auditService.record("PRESCRIPTION_CREATE", "PRESCRIPTION", AuditService.id(prescription.getId()), Map.of("reference", prescription.getReference()));
        return PrescriptionResponse.from(prescription);
    }

    @Transactional
    public PrescriptionResponse update(Long id, PrescriptionRequest request) {
        Prescription prescription = load(id);
        if (prescription.getStatus() != PrescriptionStatus.PENDING) {
            throw BusinessException.conflict("PRESCRIPTION_LOCKED", "Only a pending prescription can be edited.");
        }
        apply(prescription, request);
        auditService.record("PRESCRIPTION_UPDATE", "PRESCRIPTION", AuditService.id(prescription.getId()), Map.of("reference", prescription.getReference()));
        return PrescriptionResponse.from(prescription);
    }

    @Transactional
    public PrescriptionResponse review(Long id) {
        Prescription prescription = load(id);
        if (prescription.getStatus() != PrescriptionStatus.PENDING) {
            throw BusinessException.conflict("PRESCRIPTION_LOCKED", "Only a pending prescription can be reviewed.");
        }
        prescription.setStatus(PrescriptionStatus.REVIEWED);
        prescription.setReviewedBy(currentUserService.requireEntity());
        prescription.setReviewedAt(Instant.now());
        auditService.record("PRESCRIPTION_REVIEW", "PRESCRIPTION", AuditService.id(prescription.getId()), Map.of("reference", prescription.getReference()));
        return PrescriptionResponse.from(prescription);
    }

    @Transactional
    public PrescriptionResponse cancel(Long id, String reason) {
        if (reason == null || reason.trim().length() < 3) {
            throw BusinessException.badRequest("REASON_REQUIRED", "Enter a cancellation reason.");
        }
        Prescription prescription = load(id);
        if (prescription.getStatus() == PrescriptionStatus.CANCELLED) {
            throw BusinessException.conflict("PRESCRIPTION_LOCKED", "This prescription is already cancelled.");
        }
        boolean dispensed = prescription.getItems().stream().anyMatch(item -> item.getQuantityDispensed() > 0);
        if (dispensed) {
            throw BusinessException.conflict("ALREADY_DISPENSED", "A prescription with dispensed medicines cannot be cancelled.");
        }
        prescription.setStatus(PrescriptionStatus.CANCELLED);
        prescription.setCancelledBy(currentUserService.requireEntity());
        prescription.setCancelledAt(Instant.now());
        prescription.setCancellationReason(reason.trim());
        auditService.record("PRESCRIPTION_CANCEL", "PRESCRIPTION", AuditService.id(prescription.getId()), Map.of("reference", prescription.getReference(), "reason", reason.trim()));
        return PrescriptionResponse.from(prescription);
    }

    @Transactional
    public DispensingResponse dispense(Long id, DispenseRequest request) {
        Prescription prescription = load(id);
        if (prescription.getStatus() != PrescriptionStatus.REVIEWED && prescription.getStatus() != PrescriptionStatus.PARTIALLY_DISPENSED) {
            throw BusinessException.conflict("NOT_READY", "Review the prescription before dispensing.");
        }
        boolean allowExpired = authorizeExpired(request.authorizeExpired(), request.expiredReason());
        UserAccount user = currentUserService.requireEntity();
        Map<Long, Integer> reserved = new HashMap<>();
        List<Planned> planned = new ArrayList<>();
        for (DispenseItemRequest line : request.items()) {
            PrescriptionItem item = prescription.getItems().stream()
                    .filter(candidate -> candidate.getId().equals(line.prescriptionItemId()))
                    .findFirst()
                    .orElseThrow(() -> BusinessException.badRequest("UNKNOWN_ITEM", "A dispense line does not belong to this prescription."));
            int remaining = item.getQuantity() - item.getQuantityDispensed();
            if (line.quantity() > remaining) {
                throw BusinessException.conflict("QUANTITY_EXCEEDED", "Cannot dispense more than the remaining quantity for " + item.getMedicine().getName() + ".");
            }
            if (!item.getMedicine().isActive()) {
                throw BusinessException.badRequest("INACTIVE_MEDICINE", item.getMedicine().getName() + " is inactive.");
            }
            if (line.batchId() != null) {
                Batch batch = inventoryService.lockRequestedBatch(line.batchId(), item.getMedicine().getId(), line.quantity(), allowExpired, request.expiredReason(), reserved);
                planned.add(new Planned(item, batch, line.quantity()));
            } else {
                inventoryService.planIssue(item.getMedicine().getId(), line.quantity(), allowExpired, request.expiredReason(), reserved)
                        .forEach(allocation -> planned.add(new Planned(item, allocation.batch(), allocation.quantity())));
            }
        }
        Dispensing dispensing = new Dispensing();
        dispensing.setReference(references.next("DSP", settingsService.today()));
        dispensing.setPrescription(prescription);
        dispensing.setDispensedBy(user);
        dispensing.setDispensedAt(Instant.now());
        dispensing.setNotes(SearchText.trimToNull(request.notes()));
        dispensing.setExpiredAuthorized(allowExpired);
        dispensing.setExpiredReason(allowExpired ? request.expiredReason().trim() : null);
        dispensings.saveAndFlush(dispensing);
        for (Planned line : planned) {
            inventoryService.applyOut(line.batch(), line.quantity(), StockMovementType.DISPENSED, ReferenceType.DISPENSING, dispensing.getId(), "Dispensed", user);
            line.item().setQuantityDispensed(line.item().getQuantityDispensed() + line.quantity());
            DispensingItem saved = new DispensingItem();
            saved.setDispensing(dispensing);
            saved.setPrescriptionItem(line.item());
            saved.setBatch(line.batch());
            saved.setQuantity(line.quantity());
            dispensing.getItems().add(saved);
        }
        boolean complete = prescription.getItems().stream().allMatch(item -> item.getQuantityDispensed() == item.getQuantity());
        boolean any = prescription.getItems().stream().anyMatch(item -> item.getQuantityDispensed() > 0);
        prescription.setStatus(complete ? PrescriptionStatus.FULLY_DISPENSED : any ? PrescriptionStatus.PARTIALLY_DISPENSED : prescription.getStatus());
        dispensings.saveAndFlush(dispensing);
        auditService.record("DISPENSE", "DISPENSING", AuditService.id(dispensing.getId()), Map.of("reference", dispensing.getReference(), "prescription", prescription.getReference()));
        return DispensingResponse.from(dispensing);
    }

    @Transactional(readOnly = true)
    public PageResponse<DispensingResponse> listDispensing(Long prescriptionId, int page, int size) {
        Specification<Dispensing> spec = (root, query, cb) -> prescriptionId == null
                ? cb.conjunction()
                : cb.equal(root.get("prescription").get("id"), prescriptionId);
        return PageResponse.from(dispensings.findAll(spec, Pages.of(page, size, Sort.by(Sort.Direction.DESC, "dispensedAt"))).map(DispensingResponse::from));
    }

    private void apply(Prescription prescription, PrescriptionRequest request) {
        if (request.prescriptionDate().isAfter(settingsService.today())) {
            throw BusinessException.badRequest("INVALID_DATE", "Prescription date cannot be in the future.");
        }
        Customer customer = customers.requireActive(request.customerId());
        prescription.setCustomer(customer);
        prescription.setPrescriptionDate(request.prescriptionDate());
        prescription.setPrescriberName(SearchText.trimToNull(request.prescriberName()));
        prescription.setPrescriberLicense(SearchText.trimToNull(request.prescriberLicense()));
        prescription.setNotes(SearchText.trimToNull(request.notes()));
        prescription.getItems().clear();
        for (PrescriptionItemRequest line : request.items()) {
            Medicine medicine = catalogService.requireMedicine(line.medicineId());
            if (!medicine.isActive()) {
                throw BusinessException.badRequest("INACTIVE_MEDICINE", medicine.getName() + " is inactive.");
            }
            PrescriptionItem item = new PrescriptionItem();
            item.setPrescription(prescription);
            item.setMedicine(medicine);
            item.setDosage(line.dosage().trim());
            item.setFrequency(line.frequency().trim());
            item.setDuration(line.duration().trim());
            item.setQuantity(line.quantity());
            item.setInstructions(SearchText.trimToNull(line.instructions()));
            item.setQuantityDispensed(0);
            prescription.getItems().add(item);
        }
    }

    private boolean authorizeExpired(boolean requested, String reason) {
        if (!requested) {
            return false;
        }
        if (!settingsService.getBoolean("inventory.allow_authorized_expired_use") || !AuthContext.has("DISPENSE_EXPIRED")) {
            throw BusinessException.forbidden("You are not allowed to dispense expired stock.");
        }
        if (reason == null || reason.isBlank()) {
            throw BusinessException.badRequest("EXPIRED_REASON_REQUIRED", "A reason is required to dispense expired stock.");
        }
        return true;
    }

    private Prescription load(Long id) {
        return prescriptions.findById(id).orElseThrow(() -> BusinessException.notFound("Prescription was not found."));
    }

    private record Planned(PrescriptionItem item, Batch batch, int quantity) {
    }

    public record PrescriptionItemRequest(
            @jakarta.validation.constraints.NotNull Long medicineId,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 80) String dosage,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 80) String frequency,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 80) String duration,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100000) Integer quantity,
            @jakarta.validation.constraints.Size(max = 500) String instructions
    ) {
    }

    public record PrescriptionRequest(
            @jakarta.validation.constraints.NotNull Long customerId,
            @jakarta.validation.constraints.NotNull LocalDate prescriptionDate,
            @jakarta.validation.constraints.Size(max = 150) String prescriberName,
            @jakarta.validation.constraints.Size(max = 80) String prescriberLicense,
            @jakarta.validation.constraints.Size(max = 1000) String notes,
            @jakarta.validation.constraints.NotEmpty @jakarta.validation.Valid List<PrescriptionItemRequest> items
    ) {
    }

    public record DispenseItemRequest(
            @jakarta.validation.constraints.NotNull Long prescriptionItemId,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(1) Integer quantity,
            Long batchId
    ) {
    }

    public record DispenseRequest(
            @jakarta.validation.constraints.Size(max = 1000) String notes,
            boolean authorizeExpired,
            @jakarta.validation.constraints.Size(max = 500) String expiredReason,
            @jakarta.validation.constraints.NotEmpty @jakarta.validation.Valid List<DispenseItemRequest> items
    ) {
    }

    public record CancelRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500) String reason) {
    }

    public record PrescriptionItemResponse(Long id, Long medicineId, String medicineName, String dosage, String frequency, String duration, int quantity, int quantityDispensed, String instructions) {
    }

    public record PrescriptionSummary(Long id, String reference, Long customerId, String customerName, LocalDate prescriptionDate, PrescriptionStatus status, String prescriberName) {
        static PrescriptionSummary from(Prescription prescription) {
            return new PrescriptionSummary(prescription.getId(), prescription.getReference(), prescription.getCustomer().getId(), prescription.getCustomer().getFullName(), prescription.getPrescriptionDate(), prescription.getStatus(), prescription.getPrescriberName());
        }
    }

    public record PrescriptionResponse(
            Long id, String reference, Long customerId, String customerName, LocalDate prescriptionDate, String prescriberName,
            String prescriberLicense, PrescriptionStatus status, String notes, String createdBy, String reviewedBy, Instant reviewedAt,
            String cancellationReason, List<PrescriptionItemResponse> items
    ) {
        static PrescriptionResponse from(Prescription prescription) {
            return new PrescriptionResponse(
                    prescription.getId(), prescription.getReference(), prescription.getCustomer().getId(), prescription.getCustomer().getFullName(),
                    prescription.getPrescriptionDate(), prescription.getPrescriberName(), prescription.getPrescriberLicense(), prescription.getStatus(),
                    prescription.getNotes(), prescription.getCreatedBy().getUsername(),
                    prescription.getReviewedBy() == null ? null : prescription.getReviewedBy().getUsername(), prescription.getReviewedAt(),
                    prescription.getCancellationReason(),
                    prescription.getItems().stream().map(item -> new PrescriptionItemResponse(item.getId(), item.getMedicine().getId(), item.getMedicine().getName(), item.getDosage(), item.getFrequency(), item.getDuration(), item.getQuantity(), item.getQuantityDispensed(), item.getInstructions())).toList()
            );
        }
    }

    public record DispensingItemResponse(Long prescriptionItemId, Long medicineId, String medicineName, Long batchId, String batchNumber, java.time.LocalDate expiryDate, int quantity) {
    }

    public record DispensingResponse(Long id, String reference, Long prescriptionId, String prescriptionReference, String dispensedBy, Instant dispensedAt, String notes, boolean expiredAuthorized, String expiredReason, List<DispensingItemResponse> items) {
        static DispensingResponse from(Dispensing dispensing) {
            return new DispensingResponse(
                    dispensing.getId(), dispensing.getReference(), dispensing.getPrescription().getId(), dispensing.getPrescription().getReference(),
                    dispensing.getDispensedBy().getUsername(), dispensing.getDispensedAt(), dispensing.getNotes(), dispensing.isExpiredAuthorized(), dispensing.getExpiredReason(),
                    dispensing.getItems().stream().map(item -> new DispensingItemResponse(item.getPrescriptionItem().getId(), item.getPrescriptionItem().getMedicine().getId(), item.getPrescriptionItem().getMedicine().getName(), item.getBatch().getId(), item.getBatch().getBatchNumber(), item.getBatch().getExpiryDate(), item.getQuantity())).toList()
            );
        }
    }
}
