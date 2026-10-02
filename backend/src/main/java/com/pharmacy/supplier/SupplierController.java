package com.pharmacy.supplier;

import com.pharmacy.common.PageResponse;
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

@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SUPPLIER_VIEW','PURCHASE_MANAGE')")
    public PageResponse<SupplierService.SupplierResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return supplierService.list(q, active, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SUPPLIER_VIEW','PURCHASE_MANAGE')")
    public SupplierService.SupplierResponse get(@PathVariable Long id) {
        return supplierService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
    public SupplierService.SupplierResponse create(@Valid @RequestBody SupplierService.SupplierRequest request) {
        return supplierService.save(null, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
    public SupplierService.SupplierResponse update(@PathVariable Long id, @Valid @RequestBody SupplierService.SupplierRequest request) {
        return supplierService.save(id, request);
    }
}
