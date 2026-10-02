package com.pharmacy.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final PharmacyUserDetailsService userDetailsService;
    private final SecurityErrorWriter errors;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = header.substring(7).trim();
        if (token.isEmpty()) {
            errors.write(response, 401, "INVALID_TOKEN", "Invalid or expired session.");
            return;
        }
        try {
            var claims = jwtService.parse(token);
            Object rawId = claims.get("uid");
            if (!(rawId instanceof Number number)) {
                errors.write(response, 401, "INVALID_TOKEN", "Invalid or expired session.");
                return;
            }
            UserPrincipal principal = userDetailsService.loadById(number.longValue());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    principal.getAuthorities()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (Exception exception) {
            SecurityContextHolder.clearContext();
            errors.write(response, 401, "INVALID_TOKEN", "Invalid or expired session.");
        }
    }
}
