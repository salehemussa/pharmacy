package com.pharmacy.sale;

import com.pharmacy.audit.AuditService;
import com.pharmacy.catalog.CatalogService;
import com.pharmacy.catalog.Medicine;
import com.pharmacy.common.BusinessException;
import com.pharmacy.common.Money;
import com.pharmacy.common.PageResponse;
import com.pharmacy.common.Pages;
import com.pharmacy.common.ReferenceGenerator;
import com.pharmacy.common.ReferenceType;
import com.pharmacy.common.SaleStatus;
import com.pharmacy.common.SearchText;
import com.pharmacy.common.StockMovementType;
import com.pharmacy.customer.Customer;
import com.pharmacy.customer.CustomerService;
import com.pharmacy.inventory.Batch;
import com.pharmacy.inventory.InventoryService;
import com.pharmacy.returns.SaleReturnRepository;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository sales;
    private final PaymentMethodRepository paymentMethods;
    private final SaleReturnRepository returns;
    private final CatalogService catalogService;
    private final InventoryService inventoryService;
    private final CustomerService customers;
    private final CurrentUserService currentUserService;
    private final SettingsService settingsService;
    private final ReferenceGenerator references;
    private final AuditService auditService;

    @Transactional
    public SaleResponse complete(SaleRequest request) {
        boolean allowExpired = authorizeExpired(request.authorizeExpired(), request.expiredReason());
        Customer customer = request.customerId() == null ? null : customers.requireActive(request.customerId());
        Map<Long, Integer> reserved = new HashMap<>();
        List<PlannedLine> planned = new ArrayList<>();
        for (SaleLineRequest line : request.items()) {
            Medicine medicine = catalogService.requireMedicine(line.medicineId());
            if (!medicine.isActive()) {
                throw BusinessException.badRequest("INACTIVE_MEDICINE", medicine.getName() + " is inactive and cannot be sold.");
            }
            BigDecimal requestedDiscount = money(line.discountAmount());
            if (line.batchId() != null) {
                Batch batch = inventoryService.lockRequestedBatch(line.batchId(), medicine.getId(), line.quantity(), allowExpired, request.expiredReason(), reserved);
                addPlanned(planned, medicine, batch, line.quantity(), requestedDiscount);
            } else {
                List<InventoryService.Allocation> allocations = inventoryService.planIssue(medicine.getId(), line.quantity(), allowExpired, request.expiredReason(), reserved);
                BigDecimal remainingDiscount = requestedDiscount;
                for (int index = 0; index < allocations.size(); index++) {
                    InventoryService.Allocation allocation = allocations.get(index);
                    BigDecimal share = index == allocations.size() - 1
                            ? remainingDiscount
                            : requestedDiscount.multiply(BigDecimal.valueOf(allocation.quantity()))
                            .divide(BigDecimal.valueOf(line.quantity()), 2, RoundingMode.HALF_UP);
                    if (index < allocations.size() - 1) {
                        remainingDiscount = remainingDiscount.subtract(share);
                    }
                    addPlanned(planned, medicine, allocation.batch(), allocation.quantity(), share);
                }
            }
        }
        BigDecimal subtotal = Money.zero();
        BigDecimal lineDiscounts = Money.zero();
        for (PlannedLine line : planned) {
            subtotal = subtotal.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
            lineDiscounts = lineDiscounts.add(line.lineDiscount());
        }
        subtotal = money(subtotal);
        lineDiscounts = money(lineDiscounts);
        BigDecimal headerDiscount = money(request.discountAmount());
        BigDecimal totalDiscount = money(lineDiscounts.add(headerDiscount));
        if (totalDiscount.signum() > 0 && !AuthContext.has("DISCOUNT_APPLY")) {
            throw BusinessException.forbidden("You are not allowed to apply a discount.");
        }
        if (subtotal.signum() == 0 && totalDiscount.signum() > 0) {
            throw BusinessException.badRequest("INVALID_DISCOUNT", "A zero-value sale cannot have a discount.");
        }
        if (subtotal.signum() > 0) {
            BigDecimal percent = totalDiscount.multiply(BigDecimal.valueOf(100)).divide(subtotal, 4, RoundingMode.HALF_UP);
            if (percent.compareTo(settingsService.getDecimal("sales.max_discount_percent")) > 0) {
                throw BusinessException.badRequest("DISCOUNT_LIMIT", "The discount exceeds the configured maximum of " + settingsService.get("sales.max_discount_percent") + "%.");
            }
        }
        BigDecimal netBeforeHeader = money(subtotal.subtract(lineDiscounts));
        if (headerDiscount.compareTo(netBeforeHeader) > 0) {
            throw BusinessException.badRequest("INVALID_DISCOUNT", "The sale discount is larger than the amount due.");
        }
        List<BigDecimal> lineTotals = new ArrayList<>();
        BigDecimal remainingHeader = headerDiscount;
        for (int index = 0; index < planned.size(); index++) {
            PlannedLine line = planned.get(index);
            BigDecimal lineNet = money(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())).subtract(line.lineDiscount()));
            BigDecimal share;
            if (index == planned.size() - 1 || netBeforeHeader.signum() == 0) {
                share = remainingHeader;
            } else {
                share = headerDiscount.multiply(lineNet).divide(netBeforeHeader, 2, RoundingMode.HALF_UP);
                remainingHeader = remainingHeader.subtract(share);
            }
            BigDecimal lineTotal = money(lineNet.subtract(share));
            if (lineTotal.signum() < 0) {
                throw BusinessException.badRequest("INVALID_DISCOUNT", "A line discount cannot exceed the line amount.");
            }
            lineTotals.add(lineTotal);
        }
        BigDecimal total = money(lineTotals.stream().reduce(Money.zero(), BigDecimal::add));
        UserAccount cashier = currentUserService.requireEntity();
        List<PaymentDraft> paymentDrafts = payments(request.payments(), total);
        Sale sale = new Sale();
        sale.setReference(references.next("SAL", settingsService.today()));
        sale.setCustomer(customer);
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setSubtotal(subtotal);
        sale.setDiscountAmount(totalDiscount);
        sale.setTotalAmount(total);
        sale.setNotes(SearchText.trimToNull(request.notes()));
        sale.setCashier(cashier);
        sale.setExpiredAuthorized(allowExpired);
        sale.setExpiredReason(allowExpired ? request.expiredReason().trim() : null);
        sales.saveAndFlush(sale);
        for (int index = 0; index < planned.size(); index++) {
            PlannedLine line = planned.get(index);
            inventoryService.applyOut(line.batch(), line.quantity(), StockMovementType.SOLD, ReferenceType.SALE, sale.getId(), "Sale", cashier);
            SaleItem item = new SaleItem();
            item.setSale(sale);
            item.setMedicine(line.medicine());
            item.setBatch(line.batch());
            item.setQuantity(line.quantity());
            item.setUnitPrice(line.unitPrice());
            item.setDiscountAmount(line.lineDiscount());
            item.setLineTotal(lineTotals.get(index));
            item.setQuantityReturned(0);
            item.setRefundedAmount(Money.zero());
            sale.getItems().add(item);
        }
        Instant paidAt = Instant.now();
        for (PaymentDraft draft : paymentDrafts) {
            Payment payment = new Payment();
            payment.setSale(sale);
            payment.setPaymentMethod(draft.method());
            payment.setAmount(draft.amount());
            payment.setTenderedAmount(draft.tendered());
            payment.setChangeAmount(draft.change());
            payment.setReference(draft.reference());
            payment.setPaidAt(paidAt);
            payment.setReceivedBy(cashier);
            sale.getPayments().add(payment);
        }
        sales.saveAndFlush(sale);
        auditService.record("SALE_COMPLETE", "SALE", AuditService.id(sale.getId()), Map.of("reference", sale.getReference(), "total", sale.getTotalAmount()));
        return SaleResponse.from(sale);
    }

    @Transactional(readOnly = true)
    public PageResponse<SaleSummary> list(String q, SaleStatus status, int page, int size) {
        Specification<Sale> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!SearchText.blank(q)) {
                predicates.add(cb.like(cb.lower(root.get("reference")), SearchText.like(q), '\\'));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(sales.findAll(spec, Pages.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))).map(SaleSummary::from));
    }

    @Transactional(readOnly = true)
    public SaleResponse get(Long id) {
        return SaleResponse.from(load(id));
    }

    @Transactional(readOnly = true)
    public ReceiptResponse receipt(Long id) {
        Sale sale = load(id);
        return ReceiptResponse.from(sale, settingsService.get("pharmacy.name"), settingsService.get("pharmacy.address"), settingsService.get("pharmacy.phone"), settingsService.get("pharmacy.currency"), settingsService.get("receipt.footer"));
    }

    @Transactional
    public SaleResponse cancel(Long id, String reason) {
        if (reason == null || reason.trim().length() < 3) {
            throw BusinessException.badRequest("REASON_REQUIRED", "Enter a cancellation reason.");
        }
        Sale sale = load(id);
        if (sale.getStatus() != SaleStatus.COMPLETED) {
            throw BusinessException.conflict("SALE_LOCKED", "Only a completed sale can be cancelled.");
        }
        if (returns.existsBySale_IdAndStatusIn(sale.getId(), List.of(com.pharmacy.common.ReturnStatus.PENDING, com.pharmacy.common.ReturnStatus.APPROVED))) {
            throw BusinessException.conflict("SALE_HAS_RETURNS", "Cancel the related returns before voiding this sale, or leave the sale and use returns.");
        }
        UserAccount user = currentUserService.requireEntity();
        for (SaleItem item : sale.getItems()) {
            int restore = item.getQuantity() - item.getQuantityReturned();
            if (restore > 0) {
                Batch batch = inventoryService.lockBatch(item.getBatch().getId());
                inventoryService.applyIn(batch, restore, StockMovementType.SALE_REVERSAL, ReferenceType.SALE, sale.getId(), reason.trim(), user);
            }
        }
        sale.setStatus(SaleStatus.CANCELLED);
        sale.setCancelledBy(user);
        sale.setCancelledAt(Instant.now());
        sale.setCancellationReason(reason.trim());
        auditService.record("SALE_CANCEL", "SALE", AuditService.id(sale.getId()), Map.of("reference", sale.getReference(), "reason", reason.trim()));
        return SaleResponse.from(sale);
    }

    @Transactional(readOnly = true)
    public List<InventoryService.StockRow> posSearch(String q) {
        if (SearchText.blank(q)) {
            return List.of();
        }
        return inventoryService.stock(q, "ALL", 0, 20).content().stream().filter(InventoryService.StockRow::active).toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> paymentMethods(Boolean active) {
        return paymentMethods.findAll().stream()
                .filter(method -> active == null || method.isActive() == active)
                .map(PaymentMethodResponse::from)
                .toList();
    }

    @Transactional
    public PaymentMethodResponse savePaymentMethod(Long id, PaymentMethodRequest request) {
        String code = request.code().trim().toUpperCase();
        if (!code.matches("^[A-Z0-9_]{2,30}$")) {
            throw BusinessException.badRequest("INVALID_CODE", "Payment method code must use letters, numbers or underscores.");
        }
        PaymentMethod method = id == null ? new PaymentMethod() : paymentMethods.findById(id).orElseThrow(() -> BusinessException.notFound("Payment method was not found."));
        paymentMethods.findByCodeIgnoreCase(code).filter(existing -> id == null || !existing.getId().equals(id)).ifPresent(existing -> {
            throw BusinessException.conflict("DUPLICATE_PAYMENT_METHOD", "That payment method code already exists.");
        });
        method.setCode(code);
        method.setName(request.name().trim());
        method.setActive(request.active() == null || request.active());
        paymentMethods.save(method);
        auditService.record("PAYMENT_METHOD_UPDATE", "PAYMENT_METHOD", AuditService.id(method.getId()), Map.of("code", method.getCode(), "active", method.isActive()));
        return PaymentMethodResponse.from(method);
    }

    private void addPlanned(List<PlannedLine> planned, Medicine medicine, Batch batch, int quantity, BigDecimal discount) {
        BigDecimal gross = money(batch.getSellingPrice().multiply(BigDecimal.valueOf(quantity)));
        BigDecimal lineDiscount = money(discount);
        if (lineDiscount.compareTo(gross) > 0) {
            throw BusinessException.badRequest("INVALID_DISCOUNT", "The discount on " + medicine.getName() + " exceeds the line amount.");
        }
        planned.add(new PlannedLine(medicine, batch, quantity, money(batch.getSellingPrice()), lineDiscount));
    }

    private List<PaymentDraft> payments(List<PaymentRequest> requests, BigDecimal total) {
        if (requests == null || requests.isEmpty()) {
            throw BusinessException.badRequest("PAYMENT_REQUIRED", "Record at least one payment.");
        }
        BigDecimal paid = Money.zero();
        List<PaymentDraft> drafts = new ArrayList<>();
        for (PaymentRequest request : requests) {
            PaymentMethod method = paymentMethods.findByCodeIgnoreCase(request.paymentMethodCode())
                    .orElseThrow(() -> BusinessException.badRequest("UNKNOWN_PAYMENT_METHOD", "Unknown payment method."));
            if (!method.isActive()) {
                throw BusinessException.badRequest("INACTIVE_PAYMENT_METHOD", method.getName() + " is not active.");
            }
            BigDecimal amount = money(request.amount());
            if (amount.signum() <= 0) {
                throw BusinessException.badRequest("INVALID_PAYMENT", "Payment amount must be greater than zero.");
            }
            BigDecimal tendered = null;
            BigDecimal change = null;
            if ("CASH".equals(method.getCode()) && request.tenderedAmount() != null) {
                tendered = money(request.tenderedAmount());
                if (tendered.compareTo(amount) < 0) {
                    throw BusinessException.badRequest("INVALID_TENDER", "Cash received cannot be less than the cash amount applied.");
                }
                change = money(tendered.subtract(amount));
            } else if (request.tenderedAmount() != null) {
                throw BusinessException.badRequest("INVALID_TENDER", "Tendered amount is only used for cash payments.");
            }
            if (("MOBILE_MONEY".equals(method.getCode()) || "BANK_CARD".equals(method.getCode())) && SearchText.blank(request.reference())) {
                throw BusinessException.badRequest("REFERENCE_REQUIRED", "Enter a transaction reference for " + method.getName() + ".");
            }
            paid = paid.add(amount);
            drafts.add(new PaymentDraft(method, amount, tendered, change, SearchText.trimToNull(request.reference())));
        }
        if (money(paid).compareTo(total) != 0) {
            throw BusinessException.badRequest("PAYMENT_MISMATCH", "Payments must equal the amount due of " + total + ".");
        }
        return drafts;
    }

    private boolean authorizeExpired(boolean requested, String reason) {
        if (!requested) {
            return false;
        }
        if (!settingsService.getBoolean("inventory.allow_authorized_expired_use") || !AuthContext.has("SALE_EXPIRED")) {
            throw BusinessException.forbidden("You are not allowed to sell expired stock.");
        }
        if (reason == null || reason.isBlank()) {
            throw BusinessException.badRequest("EXPIRED_REASON_REQUIRED", "A reason is required to sell expired stock.");
        }
        return true;
    }

    private Sale load(Long id) {
        return sales.findById(id).orElseThrow(() -> BusinessException.notFound("Sale was not found."));
    }

    private BigDecimal money(BigDecimal value) {
        return Money.of(value == null ? BigDecimal.ZERO : value);
    }

    private record PlannedLine(Medicine medicine, Batch batch, int quantity, BigDecimal unitPrice, BigDecimal lineDiscount) {
    }

    private record PaymentDraft(PaymentMethod method, BigDecimal amount, BigDecimal tendered, BigDecimal change, String reference) {
    }

    public record SaleLineRequest(
            @jakarta.validation.constraints.NotNull Long medicineId,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100000) Integer quantity,
            Long batchId,
            @jakarta.validation.constraints.DecimalMin("0.00") BigDecimal discountAmount
    ) {
    }

    public record PaymentRequest(
            @jakarta.validation.constraints.NotBlank String paymentMethodCode,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0.01") BigDecimal amount,
            @jakarta.validation.constraints.DecimalMin("0.00") BigDecimal tenderedAmount,
            @jakarta.validation.constraints.Size(max = 100) String reference
    ) {
    }

    public record SaleRequest(
            Long customerId,
            @jakarta.validation.constraints.Size(max = 1000) String notes,
            @jakarta.validation.constraints.DecimalMin("0.00") BigDecimal discountAmount,
            boolean authorizeExpired,
            @jakarta.validation.constraints.Size(max = 500) String expiredReason,
            @jakarta.validation.constraints.NotEmpty @jakarta.validation.Valid java.util.List<SaleLineRequest> items,
            @jakarta.validation.constraints.NotEmpty @jakarta.validation.Valid java.util.List<PaymentRequest> payments
    ) {
    }

    public record CancelRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500) String reason) {
    }

    public record PaymentMethodRequest(
            @jakarta.validation.constraints.NotBlank String code,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 80) String name,
            Boolean active
    ) {
    }

    public record PaymentMethodResponse(Long id, String code, String name, boolean active) {
        static PaymentMethodResponse from(PaymentMethod method) {
            return new PaymentMethodResponse(method.getId(), method.getCode(), method.getName(), method.isActive());
        }
    }

    public record SaleItemResponse(Long id, Long medicineId, String medicineName, Long batchId, String batchNumber, java.time.LocalDate expiryDate, int quantity, BigDecimal unitPrice, BigDecimal discountAmount, BigDecimal lineTotal, int quantityReturned) {
    }

    public record PaymentResponse(Long id, String methodCode, String methodName, BigDecimal amount, BigDecimal tenderedAmount, BigDecimal changeAmount, String reference, Instant paidAt, String receivedBy) {
    }

    public record SaleSummary(Long id, String reference, Long customerId, String customerName, SaleStatus status, BigDecimal totalAmount, String cashier, Instant createdAt) {
        static SaleSummary from(Sale sale) {
            return new SaleSummary(sale.getId(), sale.getReference(), sale.getCustomer() == null ? null : sale.getCustomer().getId(), sale.getCustomer() == null ? null : sale.getCustomer().getFullName(), sale.getStatus(), sale.getTotalAmount(), sale.getCashier().getUsername(), sale.getCreatedAt());
        }
    }

    public record SaleResponse(Long id, String reference, Long customerId, String customerName, SaleStatus status, BigDecimal subtotal, BigDecimal discountAmount, BigDecimal totalAmount, String notes, String cashier, Instant createdAt, String cancellationReason, boolean expiredAuthorized, java.util.List<SaleItemResponse> items, java.util.List<PaymentResponse> payments) {
        static SaleResponse from(Sale sale) {
            return new SaleResponse(
                    sale.getId(), sale.getReference(), sale.getCustomer() == null ? null : sale.getCustomer().getId(),
                    sale.getCustomer() == null ? null : sale.getCustomer().getFullName(), sale.getStatus(), sale.getSubtotal(),
                    sale.getDiscountAmount(), sale.getTotalAmount(), sale.getNotes(), sale.getCashier().getUsername(), sale.getCreatedAt(),
                    sale.getCancellationReason(), sale.isExpiredAuthorized(),
                    sale.getItems().stream().map(item -> new SaleItemResponse(item.getId(), item.getMedicine().getId(), item.getMedicine().getName(), item.getBatch().getId(), item.getBatch().getBatchNumber(), item.getBatch().getExpiryDate(), item.getQuantity(), item.getUnitPrice(), item.getDiscountAmount(), item.getLineTotal(), item.getQuantityReturned())).toList(),
                    sale.getPayments().stream().map(payment -> new PaymentResponse(payment.getId(), payment.getPaymentMethod().getCode(), payment.getPaymentMethod().getName(), payment.getAmount(), payment.getTenderedAmount(), payment.getChangeAmount(), payment.getReference(), payment.getPaidAt(), payment.getReceivedBy().getUsername())).toList()
            );
        }
    }

    public record ReceiptResponse(String pharmacyName, String address, String phone, String currency, String footer, SaleResponse sale) {
        static ReceiptResponse from(Sale sale, String name, String address, String phone, String currency, String footer) {
            return new ReceiptResponse(name, address, phone, currency, footer, SaleResponse.from(sale));
        }
    }
}
