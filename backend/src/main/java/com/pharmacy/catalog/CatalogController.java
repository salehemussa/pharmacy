package com.pharmacy.catalog;

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

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/medicines")
    @PreAuthorize("hasAnyAuthority('MEDICINE_VIEW','SALE_CREATE','DISPENSE')")
    public PageResponse<CatalogService.MedicineResponse> medicines(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return catalogService.listMedicines(q, categoryId, locationId, active, page, size);
    }

    @GetMapping("/medicines/{id}")
    @PreAuthorize("hasAnyAuthority('MEDICINE_VIEW','SALE_CREATE','DISPENSE')")
    public CatalogService.MedicineResponse medicine(@PathVariable Long id) {
        return catalogService.getMedicine(id);
    }

    @PostMapping("/medicines")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MEDICINE_MANAGE')")
    public CatalogService.MedicineResponse createMedicine(@Valid @RequestBody CatalogService.MedicineRequest request) {
        return catalogService.createMedicine(request);
    }

    @PutMapping("/medicines/{id}")
    @PreAuthorize("hasAuthority('MEDICINE_MANAGE')")
    public CatalogService.MedicineResponse updateMedicine(@PathVariable Long id, @Valid @RequestBody CatalogService.MedicineRequest request) {
        return catalogService.updateMedicine(id, request);
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAnyAuthority('MEDICINE_VIEW','MEDICINE_MANAGE')")
    public List<CatalogService.CategoryResponse> categories() {
        return catalogService.listCategories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MEDICINE_MANAGE')")
    public CatalogService.CategoryResponse createCategory(@Valid @RequestBody CatalogService.CategoryRequest request) {
        return catalogService.saveCategory(null, request);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('MEDICINE_MANAGE')")
    public CatalogService.CategoryResponse updateCategory(@PathVariable Long id, @Valid @RequestBody CatalogService.CategoryRequest request) {
        return catalogService.saveCategory(id, request);
    }

    @GetMapping("/locations")
    @PreAuthorize("hasAnyAuthority('LOCATION_VIEW','LOCATION_MANAGE','MEDICINE_VIEW','PURCHASE_MANAGE')")
    public List<CatalogService.LocationResponse> locations(@RequestParam(required = false) Boolean active) {
        return catalogService.listLocations(active);
    }

    @PostMapping("/locations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('LOCATION_MANAGE')")
    public CatalogService.LocationResponse createLocation(@Valid @RequestBody CatalogService.LocationRequest request) {
        return catalogService.saveLocation(null, request);
    }

    @PutMapping("/locations/{id}")
    @PreAuthorize("hasAuthority('LOCATION_MANAGE')")
    public CatalogService.LocationResponse updateLocation(@PathVariable Long id, @Valid @RequestBody CatalogService.LocationRequest request) {
        return catalogService.saveLocation(id, request);
    }

    @GetMapping("/dosage-forms")
    @PreAuthorize("hasAnyAuthority('MEDICINE_VIEW','SETTINGS_MANAGE')")
    public List<CatalogService.NamedResponse> dosageForms(@RequestParam(required = false) Boolean active) {
        return catalogService.listDosageForms(active);
    }

    @PostMapping("/dosage-forms")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public CatalogService.NamedResponse createDosageForm(@Valid @RequestBody CatalogService.NamedRequest request) {
        return catalogService.saveDosageForm(null, request);
    }

    @PutMapping("/dosage-forms/{id}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public CatalogService.NamedResponse updateDosageForm(@PathVariable Long id, @Valid @RequestBody CatalogService.NamedRequest request) {
        return catalogService.saveDosageForm(id, request);
    }

    @GetMapping("/units")
    @PreAuthorize("hasAnyAuthority('MEDICINE_VIEW','SETTINGS_MANAGE')")
    public List<CatalogService.NamedResponse> units(@RequestParam(required = false) Boolean active) {
        return catalogService.listUnits(active);
    }

    @PostMapping("/units")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public CatalogService.NamedResponse createUnit(@Valid @RequestBody CatalogService.NamedRequest request) {
        return catalogService.saveUnit(null, request);
    }

    @PutMapping("/units/{id}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public CatalogService.NamedResponse updateUnit(@PathVariable Long id, @Valid @RequestBody CatalogService.NamedRequest request) {
        return catalogService.saveUnit(id, request);
    }
}
