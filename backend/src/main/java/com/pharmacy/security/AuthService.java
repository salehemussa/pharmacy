package com.pharmacy.security;

import com.pharmacy.audit.AuditService;
import com.pharmacy.common.BusinessException;
import com.pharmacy.common.PasswordPolicy;
import com.pharmacy.common.SearchText;
import com.pharmacy.common.Tokens;
import com.pharmacy.config.PharmacyProperties;
import com.pharmacy.settings.SettingsService;
import com.pharmacy.user.RefreshToken;
import com.pharmacy.user.RefreshTokenRepository;
import com.pharmacy.user.UserAccount;
import com.pharmacy.user.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final LoginGuardService loginGuardService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PharmacyProperties properties;
    private final SettingsService settingsService;
    private final AuditService auditService;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String username = request.username().trim().toLowerCase();
        loginGuardService.assertNotLocked(username);
        UserAccount user = users.findByUsername(username).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginGuardService.recordFailure(username);
            auditService.record("LOGIN_FAILURE", "USER", null, Map.of("username", username), username);
            throw new BadCredentialsException("Invalid username or password.");
        }
        if (!user.isActive()) {
            auditService.record("LOGIN_FAILURE", "USER", AuditService.id(user.getId()), Map.of("reason", "deactivated"), username);
            throw new DisabledException("Account is deactivated");
        }
        loginGuardService.clearFailures(username);
        auditService.record("LOGIN_SUCCESS", "USER", AuditService.id(user.getId()), Map.of("username", username));
        return issue(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken current = refreshTokens.findByTokenHash(Tokens.sha256(request.refreshToken()))
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid or expired session."));
        if (current.isRevoked() || current.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid or expired session.");
        }
        UserAccount user = users.findWithRolesById(current.getUser().getId())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid or expired session."));
        if (!user.isActive()) {
            throw new DisabledException("Account is deactivated");
        }
        current.setRevoked(true);
        return issue(user);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            refreshTokens.findByTokenHash(Tokens.sha256(request.refreshToken()))
                    .ifPresent(token -> token.setRevoked(true));
        }
        if (securityContextPresent()) {
            auditService.record("LOGOUT", "USER", AuditService.id(AuthContext.require().getId()), Map.of());
        }
    }

    @Transactional(readOnly = true)
    public UserProfile me() {
        UserAccount user = users.findWithRolesById(AuthContext.require().getId())
                .orElseThrow(() -> BusinessException.notFound("The signed-in user was not found."));
        return profile(user);
    }

    @Transactional
    public UserProfile updateProfile(ProfileUpdateRequest request) {
        UserAccount user = users.findWithRolesById(AuthContext.require().getId())
                .orElseThrow(() -> BusinessException.notFound("The signed-in user was not found."));
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCaseAndIdNot(email, user.getId())) {
            throw BusinessException.conflict("DUPLICATE_EMAIL", "That email address is already in use.");
        }
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(SearchText.trimToNull(request.phone()));
        auditService.record("PROFILE_UPDATE", "USER", AuditService.id(user.getId()), Map.of(
                "fullName", user.getFullName(),
                "email", user.getEmail()
        ));
        return profile(user);
    }

    @Transactional
    public AuthResponse changePassword(ChangePasswordRequest request) {
        UserAccount user = users.findWithRolesById(AuthContext.require().getId())
                .orElseThrow(() -> BusinessException.notFound("The signed-in user was not found."));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw BusinessException.badRequest("INVALID_PASSWORD", "The current password is incorrect.");
        }
        PasswordPolicy.validate(request.newPassword(), settingsService.getInt("security.password_min_length"));
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw BusinessException.badRequest("PASSWORD_REUSE", "Choose a password that is different from the current one.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        refreshTokens.revokeActiveForUser(user.getId());
        auditService.record("PASSWORD_CHANGE", "USER", AuditService.id(user.getId()), Map.of());
        return issue(users.findWithRolesById(user.getId()).orElseThrow());
    }

    private AuthResponse issue(UserAccount user) {
        String refresh = Tokens.randomToken();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(Tokens.sha256(refresh));
        token.setExpiresAt(Instant.now().plus(properties.getJwt().getRefreshTokenHours(), ChronoUnit.HOURS));
        token.setRevoked(false);
        token.setCreatedAt(Instant.now());
        refreshTokens.save(token);
        UserPrincipal principal = UserPrincipal.from(user);
        return new AuthResponse(
                jwtService.accessToken(principal, Instant.now()),
                refresh,
                jwtService.accessTokenSeconds(),
                profile(user)
        );
    }

    private UserProfile profile(UserAccount user) {
        Set<String> roles = user.getRoles().stream().map(role -> role.getName()).collect(Collectors.toSet());
        Set<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.getCode())
                .collect(Collectors.toSet());
        return new UserProfile(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(), user.getPhone(), roles, permissions, user.isMustChangePassword());
    }

    private boolean securityContextPresent() {
        try {
            AuthContext.require();
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    public record LoginRequest(
            @jakarta.validation.constraints.NotBlank String username,
            @jakarta.validation.constraints.NotBlank String password
    ) {
    }

    public record RefreshRequest(@jakarta.validation.constraints.NotBlank String refreshToken) {
    }

    public record LogoutRequest(String refreshToken) {
    }

    public record ProfileUpdateRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 150) String fullName,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email @jakarta.validation.constraints.Size(max = 150) String email,
            @jakarta.validation.constraints.Size(max = 30) String phone
    ) {
    }

    public record ChangePasswordRequest(
            @jakarta.validation.constraints.NotBlank String currentPassword,
            @jakarta.validation.constraints.NotBlank String newPassword
    ) {
    }

    public record AuthResponse(String accessToken, String refreshToken, int expiresIn, UserProfile user) {
    }

    public record UserProfile(
            Long id,
            String username,
            String fullName,
            String email,
            String phone,
            Set<String> roles,
            Set<String> permissions,
            boolean mustChangePassword
    ) {
    }
}
