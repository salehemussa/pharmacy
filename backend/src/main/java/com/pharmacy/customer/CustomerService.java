package com.pharmacy.customer;

import com.pharmacy.audit.AuditService;
import com.pharmacy.common.BusinessException;
import com.pharmacy.common.PageResponse;
import com.pharmacy.common.Pages;
import com.pharmacy.common.ReferenceGenerator;
import com.pharmacy.common.SearchText;
import com.pharmacy.prescription.PrescriptionRepository;
import com.pharmacy.sale.SaleRepository;
import com.pharmacy.settings.SettingsService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customers;
    private final PrescriptionRepository prescriptions;
    private final SaleRepository sales;
    private final ReferenceGenerator references;
    private final SettingsService settingsService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(String q, Boolean active, int page, int size) {
        Specification<Customer> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!SearchText.blank(q)) {
                String like = SearchText.like(q);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), like, '\\'),
                        cb.like(cb.lower(root.get("reference")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("phone"), "")), like, '\\')
                ));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(customers.findAll(spec, Pages.of(page, size, Sort.by("fullName"))).map(CustomerResponse::from));
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return CustomerResponse.from(load(id));
    }

    @Transactional(readOnly = true)
    public CustomerHistory history(Long id) {
        Customer customer = load(id);
        List<HistoryItem> items = new ArrayList<>();
        prescriptions.findAll((root, query, cb) -> cb.equal(root.get("customer").get("id"), id), PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "prescriptionDate")))
                .forEach(prescription -> items.add(new HistoryItem("PRESCRIPTION", prescription.getId(), prescription.getReference(), prescription.getPrescriptionDate().toString(), prescription.getStatus().name())));
        sales.findAll((root, query, cb) -> cb.equal(root.get("customer").get("id"), id), PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")))
                .forEach(sale -> items.add(new HistoryItem("SALE", sale.getId(), sale.getReference(), sale.getCreatedAt().toString(), sale.getStatus().name())));
        return new CustomerHistory(CustomerResponse.from(customer), items);
    }

    @Transactional
    public CustomerResponse save(Long id, CustomerRequest request) {
        if (request.dateOfBirth() != null && request.dateOfBirth().isAfter(settingsService.today())) {
            throw BusinessException.badRequest("INVALID_DATE", "Date of birth cannot be in the future.");
        }
        Customer customer = id == null ? new Customer() : load(id);
        if (id == null) {
            customer.setReference(references.next("CUST", settingsService.today()));
        }
        customer.setFullName(request.fullName().trim());
        customer.setPhone(SearchText.trimToNull(request.phone()));
        customer.setEmail(SearchText.trimToNull(request.email()) == null ? null : request.email().trim().toLowerCase());
        customer.setAddress(SearchText.trimToNull(request.address()));
        customer.setDateOfBirth(request.dateOfBirth());
        customer.setNotes(SearchText.trimToNull(request.notes()));
        customer.setActive(request.active() == null || request.active());
        customers.save(customer);
        auditService.record(id == null ? "CUSTOMER_CREATE" : "CUSTOMER_UPDATE", "CUSTOMER", AuditService.id(customer.getId()), Map.of("reference", customer.getReference(), "name", customer.getFullName()));
        return CustomerResponse.from(customer);
    }

    public Customer requireActive(Long id) {
        Customer customer = load(id);
        if (!customer.isActive()) {
            throw BusinessException.badRequest("INACTIVE_CUSTOMER", "The selected customer is inactive.");
        }
        return customer;
    }

    public Customer load(Long id) {
        return customers.findById(id).orElseThrow(() -> BusinessException.notFound("Customer was not found."));
    }

    public record CustomerRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 150) String fullName,
            @jakarta.validation.constraints.Size(max = 30) String phone,
            @jakarta.validation.constraints.Email @jakarta.validation.constraints.Size(max = 150) String email,
            @jakarta.validation.constraints.Size(max = 500) String address,
            LocalDate dateOfBirth,
            @jakarta.validation.constraints.Size(max = 1000) String notes,
            Boolean active
    ) {
    }

    public record CustomerResponse(Long id, String reference, String fullName, String phone, String email, String address, LocalDate dateOfBirth, String notes, boolean active) {
        static CustomerResponse from(Customer customer) {
            return new CustomerResponse(customer.getId(), customer.getReference(), customer.getFullName(), customer.getPhone(), customer.getEmail(), customer.getAddress(), customer.getDateOfBirth(), customer.getNotes(), customer.isActive());
        }
    }

    public record HistoryItem(String type, Long id, String reference, String occurredAt, String status) {
    }

    public record CustomerHistory(CustomerResponse customer, List<HistoryItem> items) {
    }
}
