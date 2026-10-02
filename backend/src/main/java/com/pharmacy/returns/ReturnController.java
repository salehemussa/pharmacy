package com.pharmacy.returns;

import com.pharmacy.common.PageResponse;
import com.pharmacy.common.ReturnStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/returns")
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService returnService;

    @GetMapping
    @PreAuthorize("hasAuthority('RETURN_VIEW')")
    public PageResponse<ReturnService.ReturnResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ReturnStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return returnService.list(q, status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('RETURN_VIEW')")
    public ReturnService.ReturnResponse get(@PathVariable Long id) {
        return returnService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETURN_CREATE')")
    public ReturnService.ReturnResponse create(@Valid @RequestBody ReturnService.ReturnRequest request) {
        return returnService.create(request);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('RETURN_APPROVE')")
    public ReturnService.ReturnResponse approve(@PathVariable Long id) {
        return returnService.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('RETURN_APPROVE')")
    public ReturnService.ReturnResponse reject(@PathVariable Long id, @Valid @RequestBody ReturnService.RejectRequest request) {
        return returnService.reject(id, request.reason());
    }
}
