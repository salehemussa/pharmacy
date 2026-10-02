package com.pharmacy.user;

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
public class UserController {

    private final UserService userService;

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public PageResponse<UserService.UserResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return userService.list(q, active, role, page, size);
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public UserService.UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserService.UserResponse create(@Valid @RequestBody UserService.UserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserService.UserResponse update(@PathVariable Long id, @Valid @RequestBody UserService.UserUpdateRequest request) {
        return userService.update(id, request);
    }

    @PostMapping("/users/{id}/activation")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserService.UserResponse activation(@PathVariable Long id, @Valid @RequestBody UserService.ActivationRequest request) {
        return userService.setActive(id, request.active());
    }

    @PostMapping("/users/{id}/reset-password")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserService.PasswordResetResponse resetPassword(@PathVariable Long id) {
        return userService.resetPassword(id);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAnyAuthority('USER_VIEW','USER_MANAGE','ROLE_MANAGE')")
    public List<UserService.RoleResponse> roles() {
        return userService.listRoles();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public List<UserService.PermissionResponse> permissions() {
        return userService.listPermissions();
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public UserService.RoleResponse updatePermissions(@PathVariable Long id, @Valid @RequestBody UserService.RolePermissionRequest request) {
        return userService.updateRolePermissions(id, request.permissionCodes());
    }
}
