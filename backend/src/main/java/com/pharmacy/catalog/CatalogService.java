package com.pharmacy.catalog;

import com.pharmacy.audit.AuditService;
import com.pharmacy.common.BusinessException;
import com.pharmacy.common.LocationType;
import com.pharmacy.common.Money;
import com.pharmacy.common.PageResponse;
import com.pharmacy.common.Pages;
import com.pharmacy.common.SearchText;
import com.pharmacy.inventory.BatchRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final MedicineRepository medicines;
    private final CategoryRepository categories;
    private final StorageLocationRepository locations;
    private final DosageFormRepository dosageForms;
    private final UnitOfMeasureRepository units;
    private final BatchRepository batches;
    private final LocationPathService locationPaths;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<MedicineResponse> listMedicines(String q, Long categoryId, Long locationId, Boolean active, int page, int size) {
        Map<Long, String> paths = locationPaths.paths();
        return PageResponse.from(medicines.findAll(medicineFilter(q, categoryId, locationId, active), Pages.of(page, size, Sort.by("name").ascending()))
                .map(medicine -> MedicineResponse.from(medicine, locationPaths.of(medicine.getLocation(), paths))));
    }

    @Transactional(readOnly = true)
    public MedicineResponse getMedicine(Long id) {
        Medicine medicine = loadMedicine(id);
        return MedicineResponse.from(medicine, locationPaths.of(medicine.getLocation(), locationPaths.paths()));
    }

    @Transactional
    public MedicineResponse createMedicine(MedicineRequest request) {
        Medicine medicine = new Medicine();
        applyMedicine(medicine, request);
        medicines.save(medicine);
        auditService.record("MEDICINE_CREATE", "MEDICINE", AuditService.id(medicine.getId()), snapshot(medicine));
        return MedicineResponse.from(medicine, locationPaths.of(medicine.getLocation(), locationPaths.paths()));
    }

    @Transactional
    public MedicineResponse updateMedicine(Long id, MedicineRequest request) {
        Medicine medicine = loadMedicine(id);
        Map<String, Object> before = snapshot(medicine);
        applyMedicine(medicine, request);
        auditService.record("MEDICINE_UPDATE", "MEDICINE", AuditService.id(medicine.getId()), Map.of("before", before, "after", snapshot(medicine)));
        return MedicineResponse.from(medicine, locationPaths.of(medicine.getLocation(), locationPaths.paths()));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categories.findAllByOrderByNameAsc().stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse saveCategory(Long id, CategoryRequest request) {
        String name = request.name().trim();
        Category category = id == null ? new Category() : categories.findById(id).orElseThrow(() -> BusinessException.notFound("Category was not found."));
        if (id == null ? categories.findByNameIgnoreCase(name).isPresent() : categories.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw BusinessException.conflict("DUPLICATE_CATEGORY", "A category with that name already exists.");
        }
        category.setName(name);
        category.setDescription(SearchText.trimToNull(request.description()));
        category.setActive(request.active() == null || request.active());
        categories.save(category);
        auditService.record(id == null ? "CATEGORY_CREATE" : "CATEGORY_UPDATE", "CATEGORY", AuditService.id(category.getId()), Map.of("name", category.getName(), "active", category.isActive()));
        return CategoryResponse.from(category);
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> listLocations(Boolean active) {
        Map<Long, String> paths = locationPaths.paths();
        return locations.findAllByOrderByCodeAsc().stream()
                .filter(location -> active == null || location.isActive() == active)
                .map(location -> LocationResponse.from(location, paths.get(location.getId())))
                .toList();
    }

    @Transactional
    public LocationResponse saveLocation(Long id, LocationRequest request) {
        StorageLocation location = id == null ? new StorageLocation() : locations.findById(id).orElseThrow(() -> BusinessException.notFound("Location was not found."));
        String code = normalizeCode(request.code());
        locations.findByCodeIgnoreCase(code).filter(existing -> id == null || !existing.getId().equals(id)).ifPresent(existing -> {
            throw BusinessException.conflict("DUPLICATE_LOCATION", "A location with that code already exists.");
        });
        StorageLocation parent = request.parentId() == null ? null : locations.findById(request.parentId()).orElseThrow(() -> BusinessException.notFound("Parent location was not found."));
        if (parent != null && !parent.isActive() && (request.active() == null || request.active())) {
            throw BusinessException.badRequest("INACTIVE_PARENT", "Choose an active parent location.");
        }
        validateHierarchy(request.locationType(), parent);
        if (id != null && locations.existsByParent_Id(id) && (location.getLocationType() != request.locationType() || !sameParent(location.getParent(), parent))) {
            throw BusinessException.conflict("LOCATION_HAS_CHILDREN", "Change the child locations before changing this location's type or parent.");
        }
        boolean active = request.active() == null || request.active();
        if (id != null && !active) {
            if (locations.existsByParent_IdAndActiveTrue(id)) {
                throw BusinessException.conflict("LOCATION_HAS_CHILDREN", "Deactivate or move the child locations first.");
            }
            if (medicines.countByLocation_Id(id) > 0 || batches.countByLocation_IdAndQuantityOnHandGreaterThan(id, 0) > 0) {
                throw BusinessException.conflict("LOCATION_IN_USE", "Reassign medicines and stock before deactivating this location.");
            }
        }
        location.setCode(code);
        location.setName(request.name().trim());
        location.setLocationType(request.locationType());
        location.setParent(parent);
        location.setActive(active);
        locations.save(location);
        auditService.record(id == null ? "LOCATION_CREATE" : "LOCATION_UPDATE", "LOCATION", AuditService.id(location.getId()), Map.of("code", location.getCode(), "type", location.getLocationType().name(), "active", location.isActive()));
        return LocationResponse.from(location, locationPaths.paths().get(location.getId()));
    }

    @Transactional(readOnly = true)
    public List<NamedResponse> listDosageForms(Boolean active) {
        return dosageForms.findAllByOrderByNameAsc().stream()
                .filter(item -> active == null || item.isActive() == active)
                .map(item -> new NamedResponse(item.getId(), item.getName(), item.isActive()))
                .toList();
    }

    @Transactional
    public NamedResponse saveDosageForm(Long id, NamedRequest request) {
        String name = request.name().trim();
        DosageForm form = id == null ? new DosageForm() : dosageForms.findById(id).orElseThrow(() -> BusinessException.notFound("Dosage form was not found."));
        dosageForms.findByNameIgnoreCase(name).filter(existing -> id == null || !existing.getId().equals(id)).ifPresent(existing -> {
            throw BusinessException.conflict("DUPLICATE_DOSAGE_FORM", "That dosage form already exists.");
        });
        form.setName(name);
        form.setActive(request.active() == null || request.active());
        dosageForms.save(form);
        auditService.record("REFERENCE_UPDATE", "DOSAGE_FORM", AuditService.id(form.getId()), Map.of("name", form.getName(), "active", form.isActive()));
        return new NamedResponse(form.getId(), form.getName(), form.isActive());
    }

    @Transactional(readOnly = true)
    public List<NamedResponse> listUnits(Boolean active) {
        return units.findAllByOrderByNameAsc().stream()
                .filter(item -> active == null || item.isActive() == active)
                .map(item -> new NamedResponse(item.getId(), item.getName(), item.isActive()))
                .toList();
    }

    @Transactional
    public NamedResponse saveUnit(Long id, NamedRequest request) {
        String name = request.name().trim();
        UnitOfMeasure unit = id == null ? new UnitOfMeasure() : units.findById(id).orElseThrow(() -> BusinessException.notFound("Unit was not found."));
        units.findByNameIgnoreCase(name).filter(existing -> id == null || !existing.getId().equals(id)).ifPresent(existing -> {
            throw BusinessException.conflict("DUPLICATE_UNIT", "That unit already exists.");
        });
        unit.setName(name);
        unit.setActive(request.active() == null || request.active());
        units.save(unit);
        auditService.record("REFERENCE_UPDATE", "UNIT", AuditService.id(unit.getId()), Map.of("name", unit.getName(), "active", unit.isActive()));
        return new NamedResponse(unit.getId(), unit.getName(), unit.isActive());
    }

    public Medicine requireMedicine(Long id) {
        return medicines.findById(id).orElseThrow(() -> BusinessException.notFound("Medicine was not found."));
    }

    public StorageLocation requireActiveLocation(Long id) {
        if (id == null) {
            return null;
        }
        StorageLocation location = locations.findById(id).orElseThrow(() -> BusinessException.notFound("Location was not found."));
        if (!location.isActive()) {
            throw BusinessException.badRequest("INACTIVE_LOCATION", "The selected location is inactive.");
        }
        return location;
    }

    private void applyMedicine(Medicine medicine, MedicineRequest request) {
        DosageForm form = dosageForms.findByNameIgnoreCase(request.dosageForm().trim())
                .orElseThrow(() -> BusinessException.badRequest("UNKNOWN_DOSAGE_FORM", "Choose a dosage form from the configured list."));
        UnitOfMeasure unit = units.findByNameIgnoreCase(request.unit().trim())
                .orElseThrow(() -> BusinessException.badRequest("UNKNOWN_UNIT", "Choose a unit from the configured list."));
        if (!form.isActive() || !unit.isActive()) {
            throw BusinessException.badRequest("INACTIVE_REFERENCE", "The dosage form and unit must be active.");
        }
        Category category = null;
        if (request.categoryId() != null) {
            category = categories.findById(request.categoryId()).orElseThrow(() -> BusinessException.notFound("Category was not found."));
            if (!category.isActive()) {
                throw BusinessException.badRequest("INACTIVE_CATEGORY", "The selected category is inactive.");
            }
        }
        String barcode = SearchText.trimToNull(request.barcode());
        if (barcode != null) {
            medicines.findByBarcode(barcode).filter(existing -> medicine.getId() == null || !existing.getId().equals(medicine.getId())).ifPresent(existing -> {
                throw BusinessException.conflict("DUPLICATE_BARCODE", "That barcode is already used by another medicine.");
            });
        }
        medicine.setName(request.name().trim());
        medicine.setGenericName(request.genericName().trim());
        medicine.setBrandName(SearchText.trimToNull(request.brandName()));
        medicine.setCategory(category);
        medicine.setDosageForm(form.getName());
        medicine.setStrength(request.strength().trim());
        medicine.setUnit(unit.getName());
        medicine.setManufacturer(SearchText.trimToNull(request.manufacturer()));
        medicine.setBarcode(barcode);
        medicine.setReorderLevel(request.reorderLevel());
        medicine.setPurchasePrice(Money.of(request.purchasePrice()));
        medicine.setSellingPrice(Money.of(request.sellingPrice()));
        medicine.setLocation(requireActiveLocation(request.locationId()));
        medicine.setActive(request.active() == null || request.active());
    }

    private Medicine loadMedicine(Long id) {
        return medicines.findDetailedById(id).orElseThrow(() -> BusinessException.notFound("Medicine was not found."));
    }

    private void validateHierarchy(LocationType type, StorageLocation parent) {
        switch (type) {
            case STORE -> {
                if (parent != null) {
                    throw BusinessException.badRequest("INVALID_LOCATION", "A store cannot have a parent location.");
                }
            }
            case SHELF -> requireParent(parent, LocationType.STORE, "A shelf must belong to a store.");
            case RACK -> requireParent(parent, LocationType.SHELF, "A rack must belong to a shelf.");
            case POSITION -> requireParent(parent, LocationType.RACK, "A position must belong to a rack.");
        }
    }

    private void requireParent(StorageLocation parent, LocationType expected, String message) {
        if (parent == null || parent.getLocationType() != expected) {
            throw BusinessException.badRequest("INVALID_LOCATION", message);
        }
    }

    private boolean sameParent(StorageLocation current, StorageLocation updated) {
        Long currentId = current == null ? null : current.getId();
        Long updatedId = updated == null ? null : updated.getId();
        return currentId == null ? updatedId == null : currentId.equals(updatedId);
    }

    private String normalizeCode(String code) {
        String value = code.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("^[A-Z0-9][A-Z0-9_-]{0,49}$")) {
            throw BusinessException.badRequest("INVALID_LOCATION_CODE", "Location code must use letters, numbers, hyphens or underscores.");
        }
        return value;
    }

    private Map<String, Object> snapshot(Medicine medicine) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", medicine.getName());
        values.put("genericName", medicine.getGenericName());
        values.put("brandName", medicine.getBrandName());
        values.put("categoryId", medicine.getCategory() == null ? null : medicine.getCategory().getId());
        values.put("dosageForm", medicine.getDosageForm());
        values.put("strength", medicine.getStrength());
        values.put("unit", medicine.getUnit());
        values.put("barcode", medicine.getBarcode());
        values.put("reorderLevel", medicine.getReorderLevel());
        values.put("purchasePrice", medicine.getPurchasePrice());
        values.put("sellingPrice", medicine.getSellingPrice());
        values.put("locationId", medicine.getLocation() == null ? null : medicine.getLocation().getId());
        values.put("active", medicine.isActive());
        return values;
    }

    private Specification<Medicine> medicineFilter(String q, Long categoryId, Long locationId, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!SearchText.blank(q)) {
                String like = SearchText.like(q);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like, '\\'),
                        cb.like(cb.lower(root.get("genericName")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("brandName"), "")), like, '\\'),
                        cb.equal(root.get("barcode"), q.trim())
                ));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (locationId != null) {
                predicates.add(cb.equal(root.get("location").get("id"), locationId));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public record MedicineRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String name,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String genericName,
            @jakarta.validation.constraints.Size(max = 200) String brandName,
            Long categoryId,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 50) String dosageForm,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 50) String strength,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 30) String unit,
            @jakarta.validation.constraints.Size(max = 150) String manufacturer,
            @jakarta.validation.constraints.Size(max = 80) String barcode,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(0) Integer reorderLevel,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer = 12, fraction = 2) BigDecimal purchasePrice,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer = 12, fraction = 2) BigDecimal sellingPrice,
            Long locationId,
            Boolean active
    ) {
    }

    public record MedicineResponse(
            Long id, String name, String genericName, String brandName, Long categoryId, String categoryName,
            String dosageForm, String strength, String unit, String manufacturer, String barcode, int reorderLevel,
            BigDecimal purchasePrice, BigDecimal sellingPrice, Long locationId, String locationCode, String locationPath, boolean active
    ) {
        static MedicineResponse from(Medicine medicine, String locationPath) {
            return new MedicineResponse(
                    medicine.getId(), medicine.getName(), medicine.getGenericName(), medicine.getBrandName(),
                    medicine.getCategory() == null ? null : medicine.getCategory().getId(),
                    medicine.getCategory() == null ? null : medicine.getCategory().getName(),
                    medicine.getDosageForm(), medicine.getStrength(), medicine.getUnit(), medicine.getManufacturer(),
                    medicine.getBarcode(), medicine.getReorderLevel(), medicine.getPurchasePrice(), medicine.getSellingPrice(),
                    medicine.getLocation() == null ? null : medicine.getLocation().getId(),
                    medicine.getLocation() == null ? null : medicine.getLocation().getCode(),
                    locationPath, medicine.isActive()
            );
        }
    }

    public record CategoryRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String name,
            @jakarta.validation.constraints.Size(max = 255) String description,
            Boolean active
    ) {
    }

    public record CategoryResponse(Long id, String name, String description, boolean active) {
        static CategoryResponse from(Category category) {
            return new CategoryResponse(category.getId(), category.getName(), category.getDescription(), category.isActive());
        }
    }

    public record LocationRequest(
            Long parentId,
            @jakarta.validation.constraints.NotNull LocationType locationType,
            @jakarta.validation.constraints.NotBlank String code,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 150) String name,
            Boolean active
    ) {
    }

    public record LocationResponse(Long id, Long parentId, LocationType locationType, String code, String name, String path, boolean active) {
        static LocationResponse from(StorageLocation location, String path) {
            return new LocationResponse(
                    location.getId(),
                    location.getParent() == null ? null : location.getParent().getId(),
                    location.getLocationType(),
                    location.getCode(),
                    location.getName(),
                    path,
                    location.isActive()
            );
        }
    }

    public record NamedRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 50) String name,
            Boolean active
    ) {
    }

    public record NamedResponse(Long id, String name, boolean active) {
    }
}
