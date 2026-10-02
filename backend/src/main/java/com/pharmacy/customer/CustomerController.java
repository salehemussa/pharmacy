package com.pharmacy.customer;

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
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public PageResponse<CustomerService.CustomerResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return customerService.list(q, active, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public CustomerService.CustomerResponse get(@PathVariable Long id) {
        return customerService.get(id);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public CustomerService.CustomerHistory history(@PathVariable Long id) {
        return customerService.history(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public CustomerService.CustomerResponse create(@Valid @RequestBody CustomerService.CustomerRequest request) {
        return customerService.save(null, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public CustomerService.CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerService.CustomerRequest request) {
        return customerService.save(id, request);
    }
}
