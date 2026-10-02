package com.pharmacy.purchase;

import com.pharmacy.audit.AuditService;
import com.pharmacy.catalog.CatalogService;
import com.pharmacy.catalog.Medicine;
import com.pharmacy.catalog.StorageLocation;
import com.pharmacy.common.BatchDates;
import com.pharmacy.common.BusinessException;
import com.pharmacy.common.Money;
import com.pharmacy.common.PageResponse;
import com.pharmacy.common.Pages;
import com.pharmacy.common.PaymentStatus;
import com.pharmacy.common.PurchaseStatus;
import com.pharmacy.common.ReferenceGenerator;
import com.pharmacy.common.SearchText;
import com.pharmacy.common.StockMovementType;
import com.pharmacy.inventory.Batch;
import com.pharmacy.inventory.BatchRepository;
import com.pharmacy.inventory.InventoryService;
import com.pharmacy.security.CurrentUserService;
import com.pharmacy.settings.SettingsService;
import com.pharmacy.supplier.Supplier;
import com.pharmacy.supplier.SupplierService;
import com.pharmacy.user.UserAccount;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchases;
    private final BatchRepository batches;
    private final SupplierService supplierService;
    private final CatalogService catalogService;
    private final InventoryService inventoryService;
    private final CurrentUserService currentUserService;
    private final SettingsService settingsService;
    private final ReferenceGenerator references;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<PurchaseSummary> list(String q, Long supplierId, PurchaseStatus status, LocalDate from, LocalDate to, int page, int size) {
        Specification<Purchase> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!SearchText.blank(q)) {
                predicates.add(cb.like(cb.lower(root.get("reference")), SearchText.like(q), '\\'));
            }
            if (supplierId != null) {
                predicates.add(cb.equal(root.get("supplier").get("id"), supplierId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("purchaseDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("purchaseDate"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(purchases.findAll(spec, Pages.of(page, size, Sort.by(Sort.Direction.DESC, "purchaseDate", "id"))).map(PurchaseSummary::from));
    }

    @Transactional(readOnly = true)
    public PurchaseResponse get(Long id) {
        return PurchaseResponse.from(load(id));
    }

    @Transactional
    public PurchaseResponse create(PurchaseRequest request) {
        Purchase purchase = new Purchase();
        purchase.setReference(references.next("PUR", settingsService.today()));
        purchase.setCreatedBy(currentUserService.requireEntity());
        purchase.setStatus(PurchaseStatus.DRAFT);
        purchase.setPaymentStatus(PaymentStatus.UNPAID);
        purchase.setAmountPaid(Money.zero());
        apply(purchase, request);
        purchases.save(purchase);
        auditService.record("PURCHASE_CREATE", "PURCHASE", AuditService.id(purchase.getId()), Map.of("reference", purchase.getReference(), "total", purchase.getTotalAmount()));
        return PurchaseResponse.from(purchase);
    }

    @Transactional
    public PurchaseResponse update(Long id, PurchaseRequest request) {
        Purchase purchase = load(id);
        if (purchase.getStatus() != PurchaseStatus.DRAFT) {
            throw BusinessException.conflict("PURCHASE_LOCKED", "Only draft purchases can be edited.");
        }
        apply(purchase, request);
        auditService.record("PURCHASE_UPDATE", "PURCHASE", AuditService.id(purchase.getId()), Map.of("reference", purchase.getReference(), "total", purchase.getTotalAmount()));
        return PurchaseResponse.from(purchase);
    }

    @Transactional
    public PurchaseResponse confirm(Long id) {
        Purchase purchase = load(id);
        if (purchase.getStatus() != PurchaseStatus.DRAFT) {
            throw BusinessException.conflict("PURCHASE_LOCKED", "Only a draft purchase can be confirmed.");
        }
        if (purchase.getItems().isEmpty()) {
            throw BusinessException.badRequest("EMPTY_PURCHASE", "Add at least one medicine before confirming.");
        }
        if (!purchase.getSupplier().isActive()) {
            throw BusinessException.badRequest("INACTIVE_SUPPLIER", "The supplier is inactive.");
        }
        UserAccount user = currentUserService.requireEntity();
        purchases.saveAndFlush(purchase);
        for (PurchaseItem item : purchase.getItems()) {
            StorageLocation location = item.getLocation() != null ? item.getLocation() : item.getMedicine().getLocation();
            inventoryService.receive(
                    item.getMedicine(),
                    item.getBatchNumber(),
                    item.getManufacturingDate(),
                    item.getExpiryDate(),
                    item.getQuantity(),
                    item.getPurchasePrice(),
                    item.getSellingPrice(),
                    purchase.getSupplier(),
                    location,
                    item,
                    user,
                    purchase.getId()
            );
            if (item.getMedicine().getLocation() == null && location != null) {
                item.getMedicine().setLocation(location);
            }
        }
        purchase.setStatus(PurchaseStatus.CONFIRMED);
        purchase.setConfirmedBy(user);
        purchase.setConfirmedAt(Instant.now());
        auditService.record("PURCHASE_CONFIRM", "PURCHASE", AuditService.id(purchase.getId()), Map.of("reference", purchase.getReference(), "total", purchase.getTotalAmount()));
        return PurchaseResponse.from(purchase);
    }

    @Transactional
    public PurchaseResponse cancel(Long id, String reason) {
        if (reason == null || reason.trim().length() < 3) {
            throw BusinessException.badRequest("REASON_REQUIRED", "Enter a cancellation reason.");
        }
        Purchase purchase = load(id);
        if (purchase.getStatus() == PurchaseStatus.CANCELLED) {
            throw BusinessException.conflict("PURCHASE_LOCKED", "This purchase is already cancelled.");
        }
        UserAccount user = currentUserService.requireEntity();
        if (purchase.getStatus() == PurchaseStatus.CONFIRMED) {
            List<Batch> locked = new ArrayList<>();
            for (PurchaseItem item : purchase.getItems()) {
                Batch batch = batches.findByPurchaseItem_Id(item.getId()).orElseThrow(() -> BusinessException.conflict("BATCH_MISSING", "A received batch could not be found."));
                Batch current = batches.lockById(batch.getId()).orElseThrow();
                if (current.getQuantityOnHand() != current.getQuantityReceived()) {
                    throw BusinessException.conflict("STOCK_ALREADY_USED", "Purchase " + purchase.getReference() + " cannot be cancelled because batch " + current.getBatchNumber() + " has already been used.");
                }
                locked.add(current);
            }
            for (Batch batch : locked) {
                inventoryService.applyOut(batch, batch.getQuantityOnHand(), StockMovementType.PURCHASE_REVERSAL, com.pharmacy.common.ReferenceType.PURCHASE, purchase.getId(), reason.trim(), user);
            }
        }
        purchase.setStatus(PurchaseStatus.CANCELLED);
        purchase.setCancelledBy(user);
        purchase.setCancelledAt(Instant.now());
        purchase.setCancellationReason(reason.trim());
        auditService.record("PURCHASE_CANCEL", "PURCHASE", AuditService.id(purchase.getId()), Map.of("reference", purchase.getReference(), "reason", reason.trim()));
        return PurchaseResponse.from(purchase);
    }

    @Transactional
    public PurchaseResponse updatePayment(Long id, BigDecimal amountPaid) {
        Purchase purchase = load(id);
        if (purchase.getStatus() != PurchaseStatus.CONFIRMED) {
            throw BusinessException.conflict("PURCHASE_LOCKED", "Record payment only on a confirmed purchase.");
        }
        BigDecimal paid = Money.of(amountPaid);
        if (paid.compareTo(BigDecimal.ZERO) < 0 || paid.compareTo(purchase.getTotalAmount()) > 0) {
            throw BusinessException.badRequest("INVALID_PAYMENT", "Amount paid cannot exceed the purchase total.");
        }
        purchase.setAmountPaid(paid);
        if (paid.compareTo(BigDecimal.ZERO) == 0) {
            purchase.setPaymentStatus(PaymentStatus.UNPAID);
        } else if (paid.compareTo(purchase.getTotalAmount()) == 0) {
            purchase.setPaymentStatus(PaymentStatus.PAID);
        } else {
            purchase.setPaymentStatus(PaymentStatus.PARTIAL);
        }
        auditService.record("PURCHASE_PAYMENT", "PURCHASE", AuditService.id(purchase.getId()), Map.of("reference", purchase.getReference(), "amountPaid", paid, "paymentStatus", purchase.getPaymentStatus().name()));
        return PurchaseResponse.from(purchase);
    }

    private void apply(Purchase purchase, PurchaseRequest request) {
        if (request.purchaseDate().isAfter(settingsService.today())) {
            throw BusinessException.badRequest("INVALID_DATE", "Purchase date cannot be in the future.");
        }
        Supplier supplier = supplierService.requireActive(request.supplierId());
        purchase.setSupplier(supplier);
        purchase.setPurchaseDate(request.purchaseDate());
        purchase.setNotes(SearchText.trimToNull(request.notes()));
        purchase.getItems().clear();
        BigDecimal total = Money.zero();
        Set<String> seen = new HashSet<>();
        for (PurchaseItemRequest line : request.items()) {
            Medicine medicine = catalogService.requireMedicine(line.medicineId());
            String batchNumber = inventoryService.normalizeBatch(line.batchNumber());
            String key = medicine.getId() + "|" + batchNumber;
            if (!seen.add(key)) {
                throw BusinessException.badRequest("DUPLICATE_BATCH", "Batch " + batchNumber + " is repeated for the same medicine.");
            }
            BatchDates.validate(line.manufacturingDate(), line.expiryDate(), settingsService.today());
            PurchaseItem item = new PurchaseItem();
            item.setPurchase(purchase);
            item.setMedicine(medicine);
            item.setQuantity(line.quantity());
            item.setPurchasePrice(Money.of(line.purchasePrice()));
            item.setSellingPrice(Money.of(line.sellingPrice()));
            item.setBatchNumber(batchNumber);
            item.setManufacturingDate(line.manufacturingDate());
            item.setExpiryDate(line.expiryDate());
            item.setLocation(catalogService.requireActiveLocation(line.locationId()));
            item.setLineTotal(Money.of(item.getPurchasePrice().multiply(BigDecimal.valueOf(item.getQuantity()))));
            total = total.add(item.getLineTotal());
            purchase.getItems().add(item);
        }
        purchase.setTotalAmount(Money.of(total));
        if (purchase.getAmountPaid() != null && purchase.getAmountPaid().compareTo(purchase.getTotalAmount()) > 0) {
            purchase.setAmountPaid(Money.zero());
            purchase.setPaymentStatus(PaymentStatus.UNPAID);
        }
    }

    private Purchase load(Long id) {
        return purchases.findById(id).orElseThrow(() -> BusinessException.notFound("Purchase was not found."));
    }

    public record PurchaseItemRequest(
            @jakarta.validation.constraints.NotNull Long medicineId,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100000) Integer quantity,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer = 12, fraction = 2) BigDecimal purchasePrice,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer = 12, fraction = 2) BigDecimal sellingPrice,
            @jakarta.validation.constraints.NotBlank String batchNumber,
            java.time.LocalDate manufacturingDate,
            @jakarta.validation.constraints.NotNull java.time.LocalDate expiryDate,
            Long locationId
    ) {
    }

    public record PurchaseRequest(
            @jakarta.validation.constraints.NotNull Long supplierId,
            @jakarta.validation.constraints.NotNull LocalDate purchaseDate,
            @jakarta.validation.constraints.Size(max = 1000) String notes,
            @jakarta.validation.constraints.NotEmpty @jakarta.validation.Valid List<PurchaseItemRequest> items
    ) {
    }

    public record PaymentUpdateRequest(
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0.00") BigDecimal amountPaid
    ) {
    }

    public record CancelRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500) String reason) {
    }

    public record PurchaseItemResponse(
            Long id, Long medicineId, String medicineName, int quantity, BigDecimal purchasePrice, BigDecimal sellingPrice,
            String batchNumber, LocalDate manufacturingDate, LocalDate expiryDate, Long locationId, String locationCode, BigDecimal lineTotal
    ) {
    }

    public record PurchaseSummary(Long id, String reference, Long supplierId, String supplierName, LocalDate purchaseDate, PurchaseStatus status, PaymentStatus paymentStatus, BigDecimal totalAmount, BigDecimal amountPaid) {
        static PurchaseSummary from(Purchase purchase) {
            return new PurchaseSummary(purchase.getId(), purchase.getReference(), purchase.getSupplier().getId(), purchase.getSupplier().getName(), purchase.getPurchaseDate(), purchase.getStatus(), purchase.getPaymentStatus(), purchase.getTotalAmount(), purchase.getAmountPaid());
        }
    }

    public record PurchaseResponse(
            Long id, String reference, Long supplierId, String supplierName, LocalDate purchaseDate, PurchaseStatus status,
            PaymentStatus paymentStatus, BigDecimal totalAmount, BigDecimal amountPaid, String notes, String createdBy,
            String confirmedBy, Instant confirmedAt, String cancellationReason, List<PurchaseItemResponse> items
    ) {
        static PurchaseResponse from(Purchase purchase) {
            List<PurchaseItemResponse> items = purchase.getItems().stream().map(item -> new PurchaseItemResponse(
                    item.getId(), item.getMedicine().getId(), item.getMedicine().getName(), item.getQuantity(),
                    item.getPurchasePrice(), item.getSellingPrice(), item.getBatchNumber(), item.getManufacturingDate(),
                    item.getExpiryDate(), item.getLocation() == null ? null : item.getLocation().getId(),
                    item.getLocation() == null ? null : item.getLocation().getCode(), item.getLineTotal()
            )).toList();
            return new PurchaseResponse(
                    purchase.getId(), purchase.getReference(), purchase.getSupplier().getId(), purchase.getSupplier().getName(),
                    purchase.getPurchaseDate(), purchase.getStatus(), purchase.getPaymentStatus(), purchase.getTotalAmount(),
                    purchase.getAmountPaid(), purchase.getNotes(), purchase.getCreatedBy().getUsername(),
                    purchase.getConfirmedBy() == null ? null : purchase.getConfirmedBy().getUsername(), purchase.getConfirmedAt(),
                    purchase.getCancellationReason(), items
            );
        }
    }
}
