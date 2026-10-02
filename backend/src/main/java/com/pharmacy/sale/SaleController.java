package com.pharmacy.sale;

import com.pharmacy.common.PageResponse;
import com.pharmacy.common.SaleStatus;
import com.pharmacy.inventory.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @PostMapping("/sales")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SALE_CREATE')")
    public SaleService.SaleResponse create(@Valid @RequestBody SaleService.SaleRequest request) {
        return saleService.complete(request);
    }

    @GetMapping("/sales")
    @PreAuthorize("hasAuthority('SALE_VIEW')")
    public PageResponse<SaleService.SaleSummary> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) SaleStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return saleService.list(q, status, page, size);
    }

    @GetMapping("/sales/{id}")
    @PreAuthorize("hasAuthority('SALE_VIEW')")
    public SaleService.SaleResponse get(@PathVariable Long id) {
        return saleService.get(id);
    }

    @GetMapping("/sales/{id}/receipt")
    @PreAuthorize("hasAnyAuthority('SALE_VIEW','SALE_CREATE')")
    public SaleService.ReceiptResponse receipt(@PathVariable Long id) {
        return saleService.receipt(id);
    }

    @PostMapping("/sales/{id}/cancel")
    @PreAuthorize("hasAuthority('SALE_CANCEL')")
    public SaleService.SaleResponse cancel(@PathVariable Long id, @Valid @RequestBody SaleService.CancelRequest request) {
        return saleService.cancel(id, request.reason());
    }

    @GetMapping("/pos/medicines")
    @PreAuthorize("hasAuthority('SALE_CREATE')")
    public List<InventoryService.StockRow> pos(@RequestParam String q) {
        return saleService.posSearch(q);
    }

    @GetMapping("/payment-methods")
    @PreAuthorize("hasAnyAuthority('SALE_CREATE','SETTINGS_MANAGE','PURCHASE_MANAGE','RETURN_CREATE')")
    public List<SaleService.PaymentMethodResponse> paymentMethods(@RequestParam(required = false) Boolean active) {
        return saleService.paymentMethods(active);
    }

    @PostMapping("/payment-methods")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public SaleService.PaymentMethodResponse createPaymentMethod(@Valid @RequestBody SaleService.PaymentMethodRequest request) {
        return saleService.savePaymentMethod(null, request);
    }

    @PutMapping("/payment-methods/{id}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public SaleService.PaymentMethodResponse updatePaymentMethod(@PathVariable Long id, @Valid @RequestBody SaleService.PaymentMethodRequest request) {
        return saleService.savePaymentMethod(id, request);
    }
}
