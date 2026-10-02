package com.pharmacy.prescription;

import com.pharmacy.common.PageResponse;
import com.pharmacy.common.PrescriptionStatus;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    @GetMapping("/prescriptions")
    @PreAuthorize("hasAuthority('PRESCRIPTION_VIEW')")
    public PageResponse<PrescriptionService.PrescriptionSummary> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PrescriptionStatus status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return prescriptionService.list(q, status, customerId, page, size);
    }

    @GetMapping("/prescriptions/{id}")
    @PreAuthorize("hasAuthority('PRESCRIPTION_VIEW')")
    public PrescriptionService.PrescriptionResponse get(@PathVariable Long id) {
        return prescriptionService.get(id);
    }

    @PostMapping("/prescriptions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRESCRIPTION_MANAGE')")
    public PrescriptionService.PrescriptionResponse create(@Valid @RequestBody PrescriptionService.PrescriptionRequest request) {
        return prescriptionService.create(request);
    }

    @PutMapping("/prescriptions/{id}")
    @PreAuthorize("hasAuthority('PRESCRIPTION_MANAGE')")
    public PrescriptionService.PrescriptionResponse update(@PathVariable Long id, @Valid @RequestBody PrescriptionService.PrescriptionRequest request) {
        return prescriptionService.update(id, request);
    }

    @PostMapping("/prescriptions/{id}/review")
    @PreAuthorize("hasAuthority('PRESCRIPTION_REVIEW')")
    public PrescriptionService.PrescriptionResponse review(@PathVariable Long id) {
        return prescriptionService.review(id);
    }

    @PostMapping("/prescriptions/{id}/cancel")
    @PreAuthorize("hasAuthority('PRESCRIPTION_MANAGE')")
    public PrescriptionService.PrescriptionResponse cancel(@PathVariable Long id, @Valid @RequestBody PrescriptionService.CancelRequest request) {
        return prescriptionService.cancel(id, request.reason());
    }

    @PostMapping("/prescriptions/{id}/dispense")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DISPENSE')")
    public PrescriptionService.DispensingResponse dispense(@PathVariable Long id, @Valid @RequestBody PrescriptionService.DispenseRequest request) {
        return prescriptionService.dispense(id, request);
    }

    @GetMapping("/dispensings")
    @PreAuthorize("hasAnyAuthority('PRESCRIPTION_VIEW','DISPENSE')")
    public PageResponse<PrescriptionService.DispensingResponse> dispensings(
            @RequestParam(required = false) Long prescriptionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return prescriptionService.listDispensing(prescriptionId, page, size);
    }
}
