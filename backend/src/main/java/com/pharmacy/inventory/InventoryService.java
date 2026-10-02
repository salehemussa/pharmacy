package com.pharmacy.inventory;

import com.pharmacy.audit.AuditService;
import com.pharmacy.catalog.CatalogService;
import com.pharmacy.catalog.LocationPathService;
import com.pharmacy.catalog.Medicine;
import com.pharmacy.catalog.MedicineRepository;
import com.pharmacy.catalog.StorageLocation;
import com.pharmacy.common.BatchDates;
import com.pharmacy.common.BusinessException;
import com.pharmacy.common.Money;
import com.pharmacy.common.PageResponse;
import com.pharmacy.common.Pages;
import com.pharmacy.common.ReferenceType;
import com.pharmacy.common.SearchText;
import com.pharmacy.common.StockDirection;
import com.pharmacy.common.StockMovementType;
import com.pharmacy.purchase.PurchaseItem;
import com.pharmacy.security.CurrentUserService;
import com.pharmacy.settings.SettingsService;
import com.pharmacy.supplier.Supplier;
import com.pharmacy.user.UserAccount;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final BatchRepository batches;
    private final StockMovementRepository movements;
    private final MedicineRepository medicines;
    private final CatalogService catalogService;
    private final LocationPathService locationPaths;
    private final SettingsService settingsService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public record Allocation(Batch batch, int quantity) {
    }

    @Transactional
    public Batch receive(
            Medicine medicine,
            String batchNumber,
            LocalDate manufacturingDate,
            LocalDate expiryDate,
            int quantity,
            BigDecimal purchasePrice,
            BigDecimal sellingPrice,
            Supplier supplier,
            StorageLocation location,
            PurchaseItem purchaseItem,
            UserAccount user,
            Long purchaseId
    ) {
        LocalDate today = settingsService.today();
        BatchDates.validate(manufacturingDate, expiryDate, today);
        if (quantity <= 0) {
            throw BusinessException.badRequest("INVALID_QUANTITY", "Quantity must be greater than zero.");
        }
        String normalizedBatch = normalizeBatch(batchNumber);
        if (batches.existsByMedicine_IdAndBatchNumberIgnoreCase(medicine.getId(), normalizedBatch)) {
            throw BusinessException.conflict("DUPLICATE_BATCH", "Batch " + normalizedBatch + " already exists for " + medicine.getName() + ".");
        }
        Batch batch = new Batch();
        batch.setMedicine(medicine);
        batch.setBatchNumber(normalizedBatch);
        batch.setManufacturingDate(manufacturingDate);
        batch.setExpiryDate(expiryDate);
        batch.setQuantityReceived(quantity);
        batch.setQuantityOnHand(0);
        batch.setPurchasePrice(Money.of(purchasePrice));
        batch.setSellingPrice(Money.of(sellingPrice));
        batch.setSupplier(supplier);
        batch.setLocation(location);
        batch.setPurchaseItem(purchaseItem);
        batches.save(batch);
        apply(batch, quantity, StockDirection.IN, StockMovementType.RECEIVED, ReferenceType.PURCHASE, purchaseId, "Purchase received", user);
        return batch;
    }

    @Transactional
    public Batch lockBatch(Long id) {
        return batches.lockById(id).orElseThrow(() -> BusinessException.notFound("Batch was not found."));
    }

    @Transactional
    public List<Allocation> planIssue(Long medicineId, int quantity, boolean allowExpired, String expiredReason, Map<Long, Integer> reserved) {
        if (quantity <= 0) {
            throw BusinessException.badRequest("INVALID_QUANTITY", "Quantity must be greater than zero.");
        }
        LocalDate today = settingsService.today();
        List<Batch> available = allowExpired ? batches.lockAllWithStock(medicineId) : batches.lockSellable(medicineId, today);
        int remaining = quantity;
        List<Allocation> allocations = new ArrayList<>();
        for (Batch batch : available) {
            int free = batch.getQuantityOnHand() - reserved.getOrDefault(batch.getId(), 0);
            if (free <= 0) {
                continue;
            }
            if (batch.getExpiryDate().isBefore(today)) {
                assertExpiredAllowed(batch, allowExpired, expiredReason);
            }
            int take = Math.min(free, remaining);
            allocations.add(new Allocation(batch, take));
            reserved.merge(batch.getId(), take, Integer::sum);
            remaining -= take;
            if (remaining == 0) {
                break;
            }
        }
        if (remaining > 0) {
            Long expired = batches.expiredOnHand(medicineId, today);
            if (!allowExpired && expired != null && expired > 0) {
                throw BusinessException.conflict("EXPIRED_STOCK", "Not enough unexpired stock. Expired quantity on hand is " + expired + ".");
            }
            throw BusinessException.conflict("INSUFFICIENT_STOCK", "Not enough stock. Short by " + remaining + ".");
        }
        return allocations;
    }

    @Transactional
    public Batch lockRequestedBatch(Long batchId, Long medicineId, int quantity, boolean allowExpired, String expiredReason, Map<Long, Integer> reserved) {
        Batch batch = batches.lockById(batchId).orElseThrow(() -> BusinessException.notFound("Batch was not found."));
        if (!batch.getMedicine().getId().equals(medicineId)) {
            throw BusinessException.badRequest("BATCH_MISMATCH", "The selected batch does not belong to the chosen medicine.");
        }
        assertExpiredAllowed(batch, !batch.getExpiryDate().isBefore(settingsService.today()) || allowExpired, expiredReason);
        if (batch.getExpiryDate().isBefore(settingsService.today())) {
            assertExpiredAllowed(batch, allowExpired, expiredReason);
        }
        int free = batch.getQuantityOnHand() - reserved.getOrDefault(batch.getId(), 0);
        if (free < quantity) {
            throw BusinessException.conflict("INSUFFICIENT_STOCK", "Batch " + batch.getBatchNumber() + " has " + Math.max(free, 0) + " available.");
        }
        reserved.merge(batch.getId(), quantity, Integer::sum);
        return batch;
    }

    @Transactional
    public void applyOut(Batch batch, int quantity, StockMovementType type, ReferenceType referenceType, Long referenceId, String reason, UserAccount user) {
        apply(batch, quantity, StockDirection.OUT, type, referenceType, referenceId, reason, user);
    }

    @Transactional
    public void applyIn(Batch batch, int quantity, StockMovementType type, ReferenceType referenceType, Long referenceId, String reason, UserAccount user) {
        apply(batch, quantity, StockDirection.IN, type, referenceType, referenceId, reason, user);
    }

    @Transactional
    public MovementResponse adjust(AdjustmentRequest request) {
        if (request.reason() == null || request.reason().trim().length() < 5) {
            throw BusinessException.badRequest("REASON_REQUIRED", "Enter a reason of at least 5 characters.");
        }
        if (request.movementType() != StockMovementType.ADJUSTED
                && request.movementType() != StockMovementType.DAMAGED
                && request.movementType() != StockMovementType.EXPIRED) {
            throw BusinessException.badRequest("INVALID_ADJUSTMENT", "Only adjustment, damaged or expired write-off movements are allowed here.");
        }
        if (request.movementType() != StockMovementType.ADJUSTED && request.direction() != StockDirection.OUT) {
            throw BusinessException.badRequest("INVALID_ADJUSTMENT", "Damaged and expired stock can only be written off.");
        }
        Batch batch = batches.lockById(request.batchId()).orElseThrow(() -> BusinessException.notFound("Batch was not found."));
        UserAccount user = currentUserService.requireEntity();
        apply(batch, request.quantity(), request.direction(), request.movementType(), ReferenceType.ADJUSTMENT, batch.getId(), request.reason().trim(), user);
        auditService.record("STOCK_ADJUST", "BATCH", AuditService.id(batch.getId()), Map.of(
                "batchNumber", batch.getBatchNumber(),
                "medicineId", batch.getMedicine().getId(),
                "type", request.movementType().name(),
                "direction", request.direction().name(),
                "quantity", request.quantity(),
                "balanceAfter", batch.getQuantityOnHand(),
                "reason", request.reason().trim()
        ));
        return new MovementResponse(null, batch.getMedicine().getId(), batch.getMedicine().getName(), batch.getId(), batch.getBatchNumber(),
                request.movementType(), request.quantity(), request.direction(), batch.getQuantityOnHand(), ReferenceType.ADJUSTMENT, batch.getId(),
                request.reason().trim(), user.getUsername(), Instant.now());
    }

    @Transactional(readOnly = true)
    public PageResponse<StockRow> stock(String q, String status, int page, int size) {
        return page(loadStock(q, status), page, size);
    }

    @Transactional(readOnly = true)
    public List<StockRow> allStock(String status) {
        return loadStock(null, status);
    }

    private List<StockRow> loadStock(String q, String status) {
        LocalDate today = settingsService.today();
        int warningDays = settingsService.getInt("inventory.expiry_warning_days");
        Map<Long, Aggregate> aggregates = aggregates(today);
        List<Long> nearIds = batches.findNearExpiryMedicineIds(today, today.plusDays(warningDays));
        Map<Long, String> paths = locationPaths.paths();
        String filter = status == null ? "ALL" : status.trim().toUpperCase(Locale.ROOT);
        List<StockRow> rows = new ArrayList<>();
        for (Medicine medicine : medicines.findAllByOrderByNameAsc()) {
            if (!SearchText.blank(q) && !matches(medicine, q)) {
                continue;
            }
            Aggregate aggregate = aggregates.getOrDefault(medicine.getId(), Aggregate.empty());
            boolean near = nearIds.contains(medicine.getId());
            if (!matchesStatus(filter, medicine, aggregate, near)) {
                continue;
            }
            rows.add(new StockRow(
                    medicine.getId(), medicine.getName(), medicine.getGenericName(), medicine.getStrength(), medicine.getDosageForm(),
                    medicine.getUnit(), medicine.getReorderLevel(), aggregate.sellable(), aggregate.onHand(), aggregate.expired(),
                    aggregate.sellableValue(), aggregate.totalValue(),
                    medicine.getLocation() == null ? null : medicine.getLocation().getId(),
                    locationPaths.of(medicine.getLocation(), paths),
                    medicine.isActive(), near
            ));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> availability(Long medicineId) {
        Medicine medicine = catalogService.requireMedicine(medicineId);
        LocalDate today = settingsService.today();
        Map<Long, String> paths = locationPaths.paths();
        return batches.findAll((root, query, cb) -> cb.equal(root.get("medicine").get("id"), medicine.getId())).stream()
                .sorted(Comparator.comparing(Batch::getExpiryDate).thenComparing(Batch::getId))
                .map(batch -> new AvailabilityResponse(
                        batch.getId(), batch.getBatchNumber(), batch.getExpiryDate(), batch.getManufacturingDate(),
                        batch.getQuantityOnHand(), batch.getSellingPrice(), batch.getPurchasePrice(),
                        !batch.getExpiryDate().isBefore(today) && batch.getQuantityOnHand() > 0,
                        batch.getExpiryDate().isBefore(today),
                        locationPaths.of(batch.getLocation() != null ? batch.getLocation() : medicine.getLocation(), paths)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<BatchResponse> listBatches(Long medicineId, String expiryStatus, int page, int size) {
        LocalDate today = settingsService.today();
        LocalDate until = today.plusDays(settingsService.getInt("inventory.expiry_warning_days"));
        Map<Long, String> paths = locationPaths.paths();
        Specification<Batch> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (medicineId != null) {
                predicates.add(cb.equal(root.get("medicine").get("id"), medicineId));
            }
            if ("EXPIRED".equalsIgnoreCase(expiryStatus)) {
                predicates.add(cb.lessThan(root.get("expiryDate"), today));
                predicates.add(cb.greaterThan(root.get("quantityOnHand"), 0));
            } else if ("NEAR".equalsIgnoreCase(expiryStatus)) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expiryDate"), today));
                predicates.add(cb.lessThanOrEqualTo(root.get("expiryDate"), until));
                predicates.add(cb.greaterThan(root.get("quantityOnHand"), 0));
            } else if ("AVAILABLE".equalsIgnoreCase(expiryStatus)) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expiryDate"), today));
                predicates.add(cb.greaterThan(root.get("quantityOnHand"), 0));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(batches.findAll(spec, Pages.of(page, size, Sort.by("expiryDate").ascending()))
                .map(batch -> BatchResponse.from(batch, locationPaths.of(batch.getLocation(), paths))));
    }

    @Transactional(readOnly = true)
    public PageResponse<MovementResponse> movements(Long medicineId, Long batchId, StockMovementType type, int page, int size) {
        Specification<StockMovement> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (medicineId != null) {
                predicates.add(cb.equal(root.get("medicine").get("id"), medicineId));
            }
            if (batchId != null) {
                predicates.add(cb.equal(root.get("batch").get("id"), batchId));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("movementType"), type));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(movements.findAll(spec, Pages.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(MovementResponse::from));
    }

    private void apply(Batch batch, int quantity, StockDirection direction, StockMovementType type, ReferenceType referenceType, Long referenceId, String reason, UserAccount user) {
        if (quantity <= 0) {
            throw BusinessException.badRequest("INVALID_QUANTITY", "Quantity must be greater than zero.");
        }
        int balance = direction == StockDirection.OUT ? batch.getQuantityOnHand() - quantity : batch.getQuantityOnHand() + quantity;
        if (balance < 0) {
            throw BusinessException.conflict("INSUFFICIENT_STOCK", "Batch " + batch.getBatchNumber() + " does not have enough stock.");
        }
        batch.setQuantityOnHand(balance);
        StockMovement movement = new StockMovement();
        movement.setMedicine(batch.getMedicine());
        movement.setBatch(batch);
        movement.setMovementType(type);
        movement.setQuantity(quantity);
        movement.setDirection(direction);
        movement.setBalanceAfter(balance);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setReason(SearchText.trimToNull(reason));
        movement.setPerformedBy(user);
        movement.setCreatedAt(Instant.now());
        movements.save(movement);
    }

    private void assertExpiredAllowed(Batch batch, boolean allowExpired, String reason) {
        if (!batch.getExpiryDate().isBefore(settingsService.today())) {
            return;
        }
        if (!allowExpired) {
            throw BusinessException.conflict("EXPIRED_STOCK", "Batch " + batch.getBatchNumber() + " is expired and cannot be sold or dispensed.");
        }
        if (reason == null || reason.isBlank()) {
            throw BusinessException.badRequest("EXPIRED_REASON_REQUIRED", "A reason is required to use expired stock.");
        }
    }

    private Map<Long, Aggregate> aggregates(LocalDate today) {
        Map<Long, Aggregate> values = new HashMap<>();
        for (Object[] row : batches.aggregateByMedicine(today)) {
            Long medicineId = ((Number) row[0]).longValue();
            values.put(medicineId, new Aggregate(asInt(row[1]), asInt(row[2]), asInt(row[3]), asMoney(row[4]), asMoney(row[5])));
        }
        return values;
    }

    private boolean matches(Medicine medicine, String q) {
        String needle = q.trim().toLowerCase(Locale.ROOT);
        return medicine.getName().toLowerCase(Locale.ROOT).contains(needle)
                || medicine.getGenericName().toLowerCase(Locale.ROOT).contains(needle)
                || (medicine.getBarcode() != null && medicine.getBarcode().equalsIgnoreCase(q.trim()))
                || (medicine.getBrandName() != null && medicine.getBrandName().toLowerCase(Locale.ROOT).contains(needle));
    }

    private boolean matchesStatus(String status, Medicine medicine, Aggregate aggregate, boolean near) {
        return switch (status) {
            case "LOW" -> aggregate.sellable() > 0 && aggregate.sellable() <= medicine.getReorderLevel();
            case "OUT" -> aggregate.sellable() == 0;
            case "EXPIRED" -> aggregate.expired() > 0;
            case "NEAR" -> near;
            case "ALL" -> true;
            default -> throw BusinessException.badRequest("INVALID_STATUS", "Stock status must be ALL, LOW, OUT, EXPIRED or NEAR.");
        };
    }

    private <T> PageResponse<T> page(List<T> rows, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int from = Math.min(safePage * safeSize, rows.size());
        int to = Math.min(from + safeSize, rows.size());
        int totalPages = rows.isEmpty() ? 0 : (int) Math.ceil(rows.size() / (double) safeSize);
        return new PageResponse<>(rows.subList(from, to), safePage, safeSize, rows.size(), totalPages);
    }

    private int asInt(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private BigDecimal asMoney(Object value) {
        if (value == null) {
            return Money.zero();
        }
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    public String normalizeBatch(String batchNumber) {
        String value = batchNumber == null ? "" : batchNumber.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("^[A-Z0-9][A-Z0-9./_-]{0,79}$")) {
            throw BusinessException.badRequest("INVALID_BATCH", "Batch number must use letters, numbers and . / _ -.");
        }
        return value;
    }

    private record Aggregate(int onHand, int sellable, int expired, BigDecimal totalValue, BigDecimal sellableValue) {
        static Aggregate empty() {
            return new Aggregate(0, 0, 0, Money.zero(), Money.zero());
        }
    }

    public record AdjustmentRequest(
            @jakarta.validation.constraints.NotNull Long batchId,
            @jakarta.validation.constraints.NotNull StockMovementType movementType,
            @jakarta.validation.constraints.NotNull StockDirection direction,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(1) Integer quantity,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500) String reason
    ) {
    }

    public record StockRow(
            Long medicineId, String name, String genericName, String strength, String dosageForm, String unit,
            int reorderLevel, int sellableQuantity, int onHandQuantity, int expiredQuantity,
            BigDecimal sellableValue, BigDecimal stockValue, Long locationId, String locationPath, boolean active, boolean nearExpiry
    ) {
    }

    public record AvailabilityResponse(
            Long batchId, String batchNumber, LocalDate expiryDate, LocalDate manufacturingDate, int quantityOnHand,
            BigDecimal sellingPrice, BigDecimal purchasePrice, boolean sellable, boolean expired, String locationPath
    ) {
    }

    public record BatchResponse(
            Long id, Long medicineId, String medicineName, String batchNumber, LocalDate manufacturingDate, LocalDate expiryDate,
            int quantityReceived, int quantityOnHand, BigDecimal purchasePrice, BigDecimal sellingPrice,
            Long supplierId, String supplierName, Long locationId, String locationPath
    ) {
        static BatchResponse from(Batch batch, String locationPath) {
            return new BatchResponse(
                    batch.getId(), batch.getMedicine().getId(), batch.getMedicine().getName(), batch.getBatchNumber(),
                    batch.getManufacturingDate(), batch.getExpiryDate(), batch.getQuantityReceived(), batch.getQuantityOnHand(),
                    batch.getPurchasePrice(), batch.getSellingPrice(),
                    batch.getSupplier() == null ? null : batch.getSupplier().getId(),
                    batch.getSupplier() == null ? null : batch.getSupplier().getName(),
                    batch.getLocation() == null ? null : batch.getLocation().getId(),
                    locationPath
            );
        }
    }

    public record MovementResponse(
            Long id, Long medicineId, String medicineName, Long batchId, String batchNumber, StockMovementType movementType,
            int quantity, StockDirection direction, int balanceAfter, ReferenceType referenceType, Long referenceId,
            String reason, String performedBy, Instant createdAt
    ) {
        static MovementResponse from(StockMovement movement) {
            return new MovementResponse(
                    movement.getId(), movement.getMedicine().getId(), movement.getMedicine().getName(),
                    movement.getBatch().getId(), movement.getBatch().getBatchNumber(), movement.getMovementType(),
                    movement.getQuantity(), movement.getDirection(), movement.getBalanceAfter(), movement.getReferenceType(),
                    movement.getReferenceId(), movement.getReason(), movement.getPerformedBy().getUsername(), movement.getCreatedAt()
            );
        }
    }
}
