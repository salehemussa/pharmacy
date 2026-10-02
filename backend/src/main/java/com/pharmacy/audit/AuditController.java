package com.pharmacy.audit;

import com.pharmacy.common.PageResponse;
import com.pharmacy.common.Pages;
import com.pharmacy.common.SearchText;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
class AuditQueryService {

    private final AuditLogRepository repository;

    @Transactional(readOnly = true)
    public PageResponse<AuditResponse> list(String q, String action, String username, int page, int size) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!SearchText.blank(action)) {
                predicates.add(cb.equal(root.get("action"), action.trim().toUpperCase()));
            }
            if (!SearchText.blank(username)) {
                predicates.add(cb.equal(cb.lower(root.get("username")), username.trim().toLowerCase()));
            }
            if (!SearchText.blank(q)) {
                String like = SearchText.like(q);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("action")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("entityType"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("username"), "")), like, '\\')
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(repository.findAll(spec, Pages.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))).map(AuditResponse::from));
    }

    public record AuditResponse(Long id, Long userId, String username, String action, String entityType, String entityId, Map<String, Object> details, String ipAddress, Instant createdAt) {
        static AuditResponse from(AuditLog log) {
            return new AuditResponse(log.getId(), log.getUserId(), log.getUsername(), log.getAction(), log.getEntityType(), log.getEntityId(), log.getDetails(), log.getIpAddress(), log.getCreatedAt());
        }
    }
}

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
class AuditController {

    private final AuditQueryService auditQueryService;

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    public PageResponse<AuditQueryService.AuditResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return auditQueryService.list(q, action, username, page, size);
    }
}
