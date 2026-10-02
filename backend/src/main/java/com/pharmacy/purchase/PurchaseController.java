package com.pharmacy.purchase;

import com.pharmacy.common.PageResponse;
import com.pharmacy.common.PurchaseStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PURCHASE_VIEW','PURCHASE_MANAGE')")
    public PageResponse<PurchaseService.PurchaseSummary> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) PurchaseStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return purchaseService.list(q, supplierId, status, from, to, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PURCHASE_VIEW','PURCHASE_MANAGE')")
    public PurchaseService.PurchaseResponse get(@PathVariable Long id) {
        return purchaseService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PURCHASE_MANAGE')")
    public PurchaseService.PurchaseResponse create(@Valid @RequestBody PurchaseService.PurchaseRequest request) {
        return purchaseService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PURCHASE_MANAGE')")
    public PurchaseService.PurchaseResponse update(@PathVariable Long id, @Valid @RequestBody PurchaseService.PurchaseRequest request) {
        return purchaseService.update(id, request);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('PURCHASE_MANAGE')")
    public PurchaseService.PurchaseResponse confirm(@PathVariable Long id) {
        return purchaseService.confirm(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PURCHASE_MANAGE')")
    public PurchaseService.PurchaseResponse cancel(@PathVariable Long id, @Valid @RequestBody PurchaseService.CancelRequest request) {
        return purchaseService.cancel(id, request.reason());
    }

    @PutMapping("/{id}/payment")
    @PreAuthorize("hasAuthority('PURCHASE_MANAGE')")
    public PurchaseService.PurchaseResponse payment(@PathVariable Long id, @Valid @RequestBody PurchaseService.PaymentUpdateRequest request) {
        return purchaseService.updatePayment(id, request.amountPaid());
    }
}
