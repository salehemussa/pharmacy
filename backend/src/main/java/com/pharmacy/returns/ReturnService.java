package com.pharmacy.returns;

import com.pharmacy.audit.AuditService;
import com.pharmacy.common.BusinessException;
import com.pharmacy.common.Money;
import com.pharmacy.common.PageResponse;
import com.pharmacy.common.Pages;
import com.pharmacy.common.ReferenceGenerator;
import com.pharmacy.common.ReferenceType;
import com.pharmacy.common.ReturnStatus;
import com.pharmacy.common.SaleStatus;
import com.pharmacy.common.SearchText;
import com.pharmacy.common.StockMovementType;
import com.pharmacy.inventory.Batch;
import com.pharmacy.inventory.InventoryService;
import com.pharmacy.sale.PaymentMethod;
import com.pharmacy.sale.PaymentMethodRepository;
import com.pharmacy.sale.Sale;
import com.pharmacy.sale.SaleItem;
import com.pharmacy.sale.SaleRepository;
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
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReturnService {

    private final SaleReturnRepository returns;
    private final SaleRepository sales;
    private final PaymentMethodRepository paymentMethods;
    private final InventoryService inventoryService;
    private final CurrentUserService currentUserService;
    private final SettingsService settingsService;
    private final ReferenceGenerator references;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<ReturnResponse> list(String q, ReturnStatus status, int page, int size) {
        Specification<SaleReturn> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!SearchText.blank(q)) {
                predicates.add(cb.like(cb.lower(root.get("reference")), SearchText.like(q), '\\'));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(returns.findAll(spec, Pages.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))).map(ReturnResponse::from));
    }

    @Transactional(readOnly = true)
    public ReturnResponse get(Long id) {
        return ReturnResponse.from(load(id));
    }

    @Transactional
    public ReturnResponse create(ReturnRequest request) {
        Sale sale = sales.findById(request.saleId()).orElseThrow(() -> BusinessException.notFound("Sale was not found."));
        if (sale.getStatus() != SaleStatus.COMPLETED) {
            throw BusinessException.conflict("SALE_NOT_RETURNABLE", "Only a completed sale can be returned.");
        }
        PaymentMethod method = paymentMethods.findByCodeIgnoreCase(request.refundMethodCode())
                .orElseThrow(() -> BusinessException.badRequest("UNKNOWN_PAYMENT_METHOD", "Unknown refund method."));
        if (!method.isActive()) {
            throw BusinessException.badRequest("INACTIVE_PAYMENT_METHOD", "The refund method is inactive.");
        }
        UserAccount user = currentUserService.requireEntity();
        SaleReturn saleReturn = new SaleReturn();
        saleReturn.setReference(references.next("RET", settingsService.today()));
        saleReturn.setSale(sale);
        saleReturn.setStatus(ReturnStatus.PENDING);
        saleReturn.setReason(request.reason().trim());
        saleReturn.setRefundAmount(Money.zero());
        saleReturn.setRefundMethod(method);
        saleReturn.setRefundReference(SearchText.trimToNull(request.refundReference()));
        saleReturn.setCreatedBy(user);
        saleReturn.setCreatedAt(Instant.now());
        List<SaleReturn> existing = returns.findAll((root, query, cb) -> cb.equal(root.get("sale").get("id"), sale.getId()));
        BigDecimal refundTotal = Money.zero();
        for (ReturnLineRequest line : request.items()) {
            SaleItem saleItem = sale.getItems().stream().filter(item -> item.getId().equals(line.saleItemId())).findFirst()
                    .orElseThrow(() -> BusinessException.badRequest("UNKNOWN_ITEM", "A return line does not belong to the original sale."));
            int committed = committedQuantity(existing, saleItem.getId());
            BigDecimal committedRefund = committedRefund(existing, saleItem.getId());
            int available = saleItem.getQuantity() - committed;
            if (line.quantity() > available) {
                throw BusinessException.conflict("RETURN_QUANTITY", "Only " + Math.max(available, 0) + " of " + saleItem.getMedicine().getName() + " can still be returned.");
            }
            BigDecimal refund = line.quantity() + committed == saleItem.getQuantity()
                    ? money(saleItem.getLineTotal().subtract(committedRefund))
                    : money(saleItem.getLineTotal().multiply(BigDecimal.valueOf(line.quantity())).divide(BigDecimal.valueOf(saleItem.getQuantity()), 2, RoundingMode.HALF_UP));
            if (refund.signum() < 0) {
                throw BusinessException.conflict("INVALID_REFUND", "The refund amount could not be calculated.");
            }
            ReturnItem item = new ReturnItem();
            item.setSaleReturn(saleReturn);
            item.setSaleItem(saleItem);
            item.setBatch(saleItem.getBatch());
            item.setQuantity(line.quantity());
            item.setRefundAmount(refund);
            saleReturn.getItems().add(item);
            refundTotal = refundTotal.add(refund);
        }
        saleReturn.setRefundAmount(money(refundTotal));
        returns.save(saleReturn);
        auditService.record("RETURN_CREATE", "RETURN", AuditService.id(saleReturn.getId()), java.util.Map.of("reference", saleReturn.getReference(), "sale", sale.getReference()));
        if (AuthContext.has("RETURN_APPROVE")) {
            approveExisting(saleReturn, user);
        }
        returns.saveAndFlush(saleReturn);
        return ReturnResponse.from(saleReturn);
    }

    @Transactional
    public ReturnResponse approve(Long id) {
        SaleReturn saleReturn = load(id);
        if (saleReturn.getStatus() != ReturnStatus.PENDING) {
            throw BusinessException.conflict("RETURN_LOCKED", "Only a pending return can be approved.");
        }
        approveExisting(saleReturn, currentUserService.requireEntity());
        return ReturnResponse.from(saleReturn);
    }

    @Transactional
    public ReturnResponse reject(Long id, String reason) {
        if (reason == null || reason.trim().length() < 3) {
            throw BusinessException.badRequest("REASON_REQUIRED", "Enter a rejection reason.");
        }
        SaleReturn saleReturn = load(id);
        if (saleReturn.getStatus() != ReturnStatus.PENDING) {
            throw BusinessException.conflict("RETURN_LOCKED", "Only a pending return can be rejected.");
        }
        saleReturn.setStatus(ReturnStatus.REJECTED);
        saleReturn.setRejectedBy(currentUserService.requireEntity());
        saleReturn.setRejectedAt(Instant.now());
        saleReturn.setRejectionReason(reason.trim());
        auditService.record("RETURN_REJECT", "RETURN", AuditService.id(saleReturn.getId()), java.util.Map.of("reference", saleReturn.getReference(), "reason", reason.trim()));
        return ReturnResponse.from(saleReturn);
    }

    private void approveExisting(SaleReturn saleReturn, UserAccount user) {
        for (ReturnItem item : saleReturn.getItems()) {
            Batch batch = inventoryService.lockBatch(item.getBatch().getId());
            inventoryService.applyIn(batch, item.getQuantity(), StockMovementType.RETURNED, ReferenceType.RETURN, saleReturn.getId(), saleReturn.getReason(), user);
            SaleItem saleItem = item.getSaleItem();
            saleItem.setQuantityReturned(saleItem.getQuantityReturned() + item.getQuantity());
            saleItem.setRefundedAmount(money(saleItem.getRefundedAmount().add(item.getRefundAmount())));
        }
        saleReturn.setStatus(ReturnStatus.APPROVED);
        saleReturn.setApprovedBy(user);
        saleReturn.setApprovedAt(Instant.now());
        auditService.record("RETURN_APPROVE", "RETURN", AuditService.id(saleReturn.getId()), java.util.Map.of("reference", saleReturn.getReference(), "refund", saleReturn.getRefundAmount()));
    }

    private int committedQuantity(List<SaleReturn> existing, Long saleItemId) {
        int quantity = 0;
        for (SaleReturn saleReturn : existing) {
            if (saleReturn.getStatus() == ReturnStatus.REJECTED) {
                continue;
            }
            for (ReturnItem item : saleReturn.getItems()) {
                if (item.getSaleItem().getId().equals(saleItemId)) {
                    quantity += item.getQuantity();
                }
            }
        }
        return quantity;
    }

    private BigDecimal committedRefund(List<SaleReturn> existing, Long saleItemId) {
        BigDecimal refund = Money.zero();
        for (SaleReturn saleReturn : existing) {
            if (saleReturn.getStatus() == ReturnStatus.REJECTED) {
                continue;
            }
            for (ReturnItem item : saleReturn.getItems()) {
                if (item.getSaleItem().getId().equals(saleItemId)) {
                    refund = refund.add(item.getRefundAmount());
                }
            }
        }
        return refund;
    }

    private SaleReturn load(Long id) {
        return returns.findById(id).orElseThrow(() -> BusinessException.notFound("Return was not found."));
    }

    private BigDecimal money(BigDecimal value) {
        return Money.of(value);
    }

    public record ReturnLineRequest(
            @jakarta.validation.constraints.NotNull Long saleItemId,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(1) Integer quantity
    ) {
    }

    public record ReturnRequest(
            @jakarta.validation.constraints.NotNull Long saleId,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500) String reason,
            @jakarta.validation.constraints.NotBlank String refundMethodCode,
            @jakarta.validation.constraints.Size(max = 100) String refundReference,
            @jakarta.validation.constraints.NotEmpty @jakarta.validation.Valid List<ReturnLineRequest> items
    ) {
    }

    public record RejectRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500) String reason) {
    }

    public record ReturnItemResponse(Long saleItemId, Long medicineId, String medicineName, String batchNumber, int quantity, BigDecimal refundAmount) {
    }

    public record ReturnResponse(
            Long id, String reference, Long saleId, String saleReference, ReturnStatus status, String reason, BigDecimal refundAmount,
            String refundMethod, String refundReference, String createdBy, Instant createdAt, String approvedBy, String rejectionReason,
            List<ReturnItemResponse> items
    ) {
        static ReturnResponse from(SaleReturn saleReturn) {
            return new ReturnResponse(
                    saleReturn.getId(), saleReturn.getReference(), saleReturn.getSale().getId(), saleReturn.getSale().getReference(),
                    saleReturn.getStatus(), saleReturn.getReason(), saleReturn.getRefundAmount(),
                    saleReturn.getRefundMethod() == null ? null : saleReturn.getRefundMethod().getCode(), saleReturn.getRefundReference(),
                    saleReturn.getCreatedBy().getUsername(), saleReturn.getCreatedAt(),
                    saleReturn.getApprovedBy() == null ? null : saleReturn.getApprovedBy().getUsername(), saleReturn.getRejectionReason(),
                    saleReturn.getItems().stream().map(item -> new ReturnItemResponse(item.getSaleItem().getId(), item.getSaleItem().getMedicine().getId(), item.getSaleItem().getMedicine().getName(), item.getBatch().getBatchNumber(), item.getQuantity(), item.getRefundAmount())).toList()
            );
        }
    }
}
