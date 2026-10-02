package com.pharmacy.audit;

import com.pharmacy.common.ClientIp;
import com.pharmacy.security.AuthContext;
import com.pharmacy.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, String entityId, Map<String, Object> details) {
        record(action, entityType, entityId, details, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, String entityId, Map<String, Object> details, String usernameOverride) {
        AuditLog log = new AuditLog();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            log.setUserId(principal.getId());
            log.setUsername(principal.getUsername());
        } else if (usernameOverride != null) {
            log.setUsername(usernameOverride);
        }
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details == null || details.isEmpty() ? null : details);
        log.setIpAddress(ClientIp.current());
        log.setCreatedAt(Instant.now());
        repository.save(log);
    }

    public static String id(Long value) {
        return value == null ? null : value.toString();
    }

    public static boolean isAuthenticated() {
        try {
            AuthContext.require();
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }
}
