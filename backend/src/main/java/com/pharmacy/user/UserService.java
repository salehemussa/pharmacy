package com.pharmacy.user;

import com.pharmacy.audit.AuditService;
import com.pharmacy.common.BusinessException;
import com.pharmacy.common.PageResponse;
import com.pharmacy.common.Pages;
import com.pharmacy.common.PasswordPolicy;
import com.pharmacy.common.RoleName;
import com.pharmacy.common.SearchText;
import com.pharmacy.common.Tokens;
import com.pharmacy.security.AuthContext;
import com.pharmacy.settings.SettingsService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final SettingsService settingsService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String q, Boolean active, String role, int page, int size) {
        return PageResponse.from(users.findAll(filter(q, active, role), Pages.of(page, size, Sort.by("fullName").ascending()))
                .map(UserResponse::from));
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(load(id));
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());
        if (users.existsByUsername(username)) {
            throw BusinessException.conflict("DUPLICATE_USERNAME", "That username is already in use.");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw BusinessException.conflict("DUPLICATE_EMAIL", "That email address is already in use.");
        }
        PasswordPolicy.validate(request.password(), settingsService.getInt("security.password_min_length"));
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPhone(SearchText.trimToNull(request.phone()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setActive(request.active() == null || request.active());
        user.setMustChangePassword(true);
        user.setRoles(resolveRoles(request.roles()));
        users.save(user);
        auditService.record("USER_CREATE", "USER", AuditService.id(user.getId()), Map.of(
                "username", user.getUsername(),
                "roles", roleNames(user)
        ));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        UserAccount user = load(id);
        Map<String, Object> before = snapshot(user);
        String email = normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw BusinessException.conflict("DUPLICATE_EMAIL", "That email address is already in use.");
        }
        Set<Role> newRoles = resolveRoles(request.roles());
        assertAdministratorRemains(user, newRoles, user.isActive());
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPhone(SearchText.trimToNull(request.phone()));
        user.setRoles(newRoles);
        auditService.record("USER_UPDATE", "USER", AuditService.id(user.getId()), Map.of("before", before, "after", snapshot(user)));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse setActive(Long id, boolean active) {
        UserAccount user = load(id);
        if (!active && user.getId().equals(AuthContext.require().getId())) {
            throw BusinessException.conflict("CANNOT_DEACTIVATE_SELF", "You cannot deactivate your own account.");
        }
        if (!active) {
            assertAdministratorRemains(user, user.getRoles(), false);
            refreshTokens.revokeActiveForUser(user.getId());
        }
        user.setActive(active);
        auditService.record(active ? "USER_ACTIVATE" : "USER_DEACTIVATE", "USER", AuditService.id(user.getId()), Map.of("username", user.getUsername()));
        return UserResponse.from(user);
    }

    @Transactional
    public PasswordResetResponse resetPassword(Long id) {
        UserAccount user = load(id);
        String temporary = Tokens.temporaryPassword();
        PasswordPolicy.validate(temporary, settingsService.getInt("security.password_min_length"));
        user.setPasswordHash(passwordEncoder.encode(temporary));
        user.setMustChangePassword(true);
        refreshTokens.revokeActiveForUser(user.getId());
        auditService.record("PASSWORD_RESET", "USER", AuditService.id(user.getId()), Map.of("username", user.getUsername()));
        return new PasswordResetResponse(user.getId(), user.getUsername(), temporary);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roles.findAllByOrderByNameAsc().stream().map(RoleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissions.findAllByOrderByModuleAscCodeAsc().stream().map(PermissionResponse::from).toList();
    }

    @Transactional
    public RoleResponse updateRolePermissions(Long id, Set<String> permissionCodes) {
        Role role = roles.findWithPermissionsById(id).orElseThrow(() -> BusinessException.notFound("Role was not found."));
        if (RoleName.ADMINISTRATOR.name().equals(role.getName())) {
            throw BusinessException.conflict("ADMIN_ROLE_LOCKED", "Administrator permissions cannot be removed. This protects the system from lockout.");
        }
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            throw BusinessException.badRequest("ROLE_EMPTY", "A role must keep at least one permission.");
        }
        List<Permission> found = permissions.findByCodeIn(permissionCodes);
        if (found.size() != permissionCodes.size()) {
            throw BusinessException.badRequest("UNKNOWN_PERMISSION", "One or more permissions are not recognized.");
        }
        Map<String, Object> before = Map.of("permissions", role.getPermissions().stream().map(Permission::getCode).sorted().toList());
        role.setPermissions(Set.copyOf(found));
        auditService.record("ROLE_PERMISSIONS_UPDATE", "ROLE", AuditService.id(role.getId()), Map.of(
                "role", role.getName(),
                "before", before,
                "after", found.stream().map(Permission::getCode).sorted().toList()
        ));
        return RoleResponse.from(role);
    }

    private UserAccount load(Long id) {
        return users.findWithRolesById(id).orElseThrow(() -> BusinessException.notFound("User was not found."));
    }

    private Set<Role> resolveRoles(Set<String> requested) {
        if (requested == null || requested.isEmpty()) {
            throw BusinessException.badRequest("ROLE_REQUIRED", "Assign at least one role.");
        }
        Set<Role> resolved = requested.stream()
                .map(name -> roles.findByName(name.trim().toUpperCase())
                        .orElseThrow(() -> BusinessException.badRequest("UNKNOWN_ROLE", "Unknown role: " + name)))
                .collect(Collectors.toSet());
        if (resolved.isEmpty()) {
            throw BusinessException.badRequest("ROLE_REQUIRED", "Assign at least one role.");
        }
        return resolved;
    }

    private void assertAdministratorRemains(UserAccount user, Set<Role> rolesAfterChange, boolean activeAfterChange) {
        boolean wasActiveAdmin = user.isActive() && hasAdministrator(user.getRoles());
        boolean remainsActiveAdmin = activeAfterChange && hasAdministrator(rolesAfterChange);
        if (wasActiveAdmin && !remainsActiveAdmin && users.countOtherActiveAdministrators(user.getId()) == 0) {
            throw BusinessException.conflict("LAST_ADMINISTRATOR", "The system must keep at least one active administrator.");
        }
    }

    private boolean hasAdministrator(Set<Role> assigned) {
        return assigned.stream().anyMatch(role -> RoleName.ADMINISTRATOR.name().equals(role.getName()));
    }

    private Map<String, Object> snapshot(UserAccount user) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("email", user.getEmail());
        values.put("fullName", user.getFullName());
        values.put("phone", user.getPhone());
        values.put("active", user.isActive());
        values.put("roles", roleNames(user));
        return values;
    }

    private List<String> roleNames(UserAccount user) {
        return user.getRoles().stream().map(Role::getName).sorted().toList();
    }

    private String normalizeUsername(String username) {
        String value = username.trim().toLowerCase();
        if (!value.matches("^[a-z0-9._]{3,50}$")) {
            throw BusinessException.badRequest("INVALID_USERNAME", "Username must be 3 to 50 characters and use letters, numbers, dots or underscores.");
        }
        return value;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private Specification<UserAccount> filter(String q, Boolean active, String role) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!SearchText.blank(q)) {
                String like = SearchText.like(q);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), like, '\\'),
                        cb.like(cb.lower(root.get("fullName")), like, '\\'),
                        cb.like(cb.lower(root.get("email")), like, '\\')
                ));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (!SearchText.blank(role)) {
                Join<UserAccount, Role> rolesJoin = root.join("roles");
                predicates.add(cb.equal(rolesJoin.get("name"), role.trim().toUpperCase()));
                query.distinct(true);
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public record UserRequest(
            @jakarta.validation.constraints.NotBlank String username,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String email,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 150) String fullName,
            @jakarta.validation.constraints.Size(max = 30) String phone,
            @jakarta.validation.constraints.NotBlank String password,
            Boolean active,
            @jakarta.validation.constraints.NotEmpty Set<String> roles
    ) {
    }

    public record UserUpdateRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String email,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 150) String fullName,
            @jakarta.validation.constraints.Size(max = 30) String phone,
            @jakarta.validation.constraints.NotEmpty Set<String> roles
    ) {
    }

    public record ActivationRequest(boolean active) {
    }

    public record RolePermissionRequest(@jakarta.validation.constraints.NotEmpty Set<String> permissionCodes) {
    }

    public record UserResponse(Long id, String username, String email, String fullName, String phone, boolean active, boolean mustChangePassword, List<String> roles) {
        static UserResponse from(UserAccount user) {
            return new UserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getFullName(),
                    user.getPhone(),
                    user.isActive(),
                    user.isMustChangePassword(),
                    user.getRoles().stream().map(Role::getName).sorted().toList()
            );
        }
    }

    public record PasswordResetResponse(Long userId, String username, String temporaryPassword) {
    }

    public record PermissionResponse(Long id, String code, String description, String module) {
        static PermissionResponse from(Permission permission) {
            return new PermissionResponse(permission.getId(), permission.getCode(), permission.getDescription(), permission.getModule());
        }
    }

    public record RoleResponse(Long id, String name, String description, List<String> permissions) {
        static RoleResponse from(Role role) {
            return new RoleResponse(
                    role.getId(),
                    role.getName(),
                    role.getDescription(),
                    role.getPermissions().stream().map(Permission::getCode).sorted().toList()
            );
        }
    }
}
