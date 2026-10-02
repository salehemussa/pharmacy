package com.pharmacy.security;

import com.pharmacy.user.UserAccount;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String fullName;
    private final boolean active;
    private final boolean mustChangePassword;
    private final Set<String> roles;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(
            Long id,
            String username,
            String password,
            String fullName,
            boolean active,
            boolean mustChangePassword,
            Set<String> roles,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.active = active;
        this.mustChangePassword = mustChangePassword;
        this.roles = roles;
        this.authorities = authorities;
    }

    public static UserPrincipal from(UserAccount user) {
        Set<String> roles = user.getRoles().stream().map(role -> role.getName()).collect(Collectors.toSet());
        Set<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> new SimpleGrantedAuthority(permission.getCode()))
                .collect(Collectors.toSet());
        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getFullName(),
                user.isActive(),
                user.isMustChangePassword(),
                roles,
                authorities
        );
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
