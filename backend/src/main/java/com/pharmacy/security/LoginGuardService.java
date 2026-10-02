package com.pharmacy.security;

import com.pharmacy.common.BusinessException;
import com.pharmacy.config.PharmacyProperties;
import com.pharmacy.user.LoginAttempt;
import com.pharmacy.user.LoginAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class LoginGuardService {

    private final LoginAttemptRepository loginAttempts;
    private final PharmacyProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void assertNotLocked(String username) {
        loginAttempts.findById(username).ifPresent(attempt -> {
            if (attempt.getLockedUntil() != null && attempt.getLockedUntil().isAfter(Instant.now())) {
                throw new BusinessException(HttpStatus.UNAUTHORIZED, "ACCOUNT_LOCKED", "This account is temporarily locked. Try again later.");
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String username) {
        LoginAttempt attempt = loginAttempts.findById(username).orElseGet(() -> {
            LoginAttempt created = new LoginAttempt();
            created.setUsername(username);
            created.setFailures(0);
            return created;
        });
        int failures = attempt.getFailures() + 1;
        attempt.setFailures(failures);
        attempt.setUpdatedAt(Instant.now());
        if (failures >= properties.getSecurity().getLoginMaxFailures()) {
            attempt.setLockedUntil(Instant.now().plus(properties.getSecurity().getLoginLockMinutes(), ChronoUnit.MINUTES));
            attempt.setFailures(0);
        }
        loginAttempts.save(attempt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearFailures(String username) {
        if (loginAttempts.existsById(username)) {
            loginAttempts.deleteById(username);
        }
    }
}
