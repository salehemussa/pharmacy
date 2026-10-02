package com.pharmacy.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED = Set.of(
            "/api/v1/auth/change-password",
            "/api/v1/auth/logout",
            "/api/v1/auth/me",
            "/api/v1/auth/refresh"
    );

    private final SecurityErrorWriter errors;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof UserPrincipal principal
                && principal.isMustChangePassword()
                && !ALLOWED.contains(request.getRequestURI())) {
            errors.write(response, 403, "PASSWORD_CHANGE_REQUIRED", "Change your password before using the system.");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
