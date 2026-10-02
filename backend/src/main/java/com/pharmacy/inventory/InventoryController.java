package com.pharmacy.inventory;

import com.pharmacy.common.PageResponse;
import com.pharmacy.common.StockMovementType;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/stock")
    @PreAuthorize("hasAuthority('STOCK_VIEW')")
    public PageResponse<InventoryService.StockRow> stock(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return inventoryService.stock(q, status, page, size);
    }

    @GetMapping("/medicines/{id}/availability")
    @PreAuthorize("hasAnyAuthority('STOCK_VIEW','SALE_CREATE','DISPENSE','MEDICINE_VIEW')")
    public List<InventoryService.AvailabilityResponse> availability(@PathVariable Long id) {
        return inventoryService.availability(id);
    }

    @GetMapping("/batches")
    @PreAuthorize("hasAuthority('STOCK_VIEW')")
    public PageResponse<InventoryService.BatchResponse> batches(
            @RequestParam(required = false) Long medicineId,
            @RequestParam(required = false) String expiryStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return inventoryService.listBatches(medicineId, expiryStatus, page, size);
    }

    @GetMapping("/stock-movements")
    @PreAuthorize("hasAuthority('STOCK_VIEW')")
    public PageResponse<InventoryService.MovementResponse> movements(
            @RequestParam(required = false) Long medicineId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) StockMovementType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return inventoryService.movements(medicineId, batchId, type, page, size);
    }

    @PostMapping("/stock/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public InventoryService.MovementResponse adjust(@Valid @RequestBody InventoryService.AdjustmentRequest request) {
        return inventoryService.adjust(request);
    }
}
