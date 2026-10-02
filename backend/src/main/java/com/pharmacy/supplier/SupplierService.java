package com.pharmacy.supplier;

import com.pharmacy.audit.AuditService;
import com.pharmacy.common.BusinessException;
import com.pharmacy.common.PageResponse;
import com.pharmacy.common.Pages;
import com.pharmacy.common.PurchaseStatus;
import com.pharmacy.common.SearchText;
import com.pharmacy.purchase.PurchaseRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository suppliers;
    private final PurchaseRepository purchases;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> list(String q, Boolean active, int page, int size) {
        Specification<Supplier> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!SearchText.blank(q)) {
                String like = SearchText.like(q);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("contactPerson"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("phone"), "")), like, '\\')
                ));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(suppliers.findAll(spec, Pages.of(page, size, Sort.by("name"))).map(SupplierResponse::from));
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(Long id) {
        return SupplierResponse.from(load(id));
    }

    @Transactional
    public SupplierResponse save(Long id, SupplierRequest request) {
        Supplier supplier = id == null ? new Supplier() : load(id);
        supplier.setName(request.name().trim());
        supplier.setContactPerson(SearchText.trimToNull(request.contactPerson()));
        supplier.setPhone(SearchText.trimToNull(request.phone()));
        supplier.setEmail(SearchText.trimToNull(request.email()) == null ? null : request.email().trim().toLowerCase());
        supplier.setAddress(SearchText.trimToNull(request.address()));
        supplier.setActive(request.active() == null || request.active());
        if (!supplier.isActive() && id != null && purchases.countBySupplier_IdAndStatusIn(id, List.of(PurchaseStatus.DRAFT)) > 0) {
            throw BusinessException.conflict("SUPPLIER_HAS_DRAFTS", "Confirm or cancel draft purchases before deactivating this supplier.");
        }
        suppliers.save(supplier);
        auditService.record(id == null ? "SUPPLIER_CREATE" : "SUPPLIER_UPDATE", "SUPPLIER", AuditService.id(supplier.getId()), Map.of("name", supplier.getName(), "active", supplier.isActive()));
        return SupplierResponse.from(supplier);
    }

    public Supplier requireActive(Long id) {
        Supplier supplier = load(id);
        if (!supplier.isActive()) {
            throw BusinessException.badRequest("INACTIVE_SUPPLIER", "The selected supplier is inactive.");
        }
        return supplier;
    }

    public Supplier load(Long id) {
        return suppliers.findById(id).orElseThrow(() -> BusinessException.notFound("Supplier was not found."));
    }

    public record SupplierRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String name,
            @jakarta.validation.constraints.Size(max = 150) String contactPerson,
            @jakarta.validation.constraints.Size(max = 30) String phone,
            @jakarta.validation.constraints.Email @jakarta.validation.constraints.Size(max = 150) String email,
            @jakarta.validation.constraints.Size(max = 500) String address,
            Boolean active
    ) {
    }

    public record SupplierResponse(Long id, String name, String contactPerson, String phone, String email, String address, boolean active) {
        static SupplierResponse from(Supplier supplier) {
            return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getContactPerson(), supplier.getPhone(), supplier.getEmail(), supplier.getAddress(), supplier.isActive());
        }
    }
}
