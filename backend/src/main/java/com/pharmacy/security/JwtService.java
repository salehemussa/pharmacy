package com.pharmacy.security;

import com.pharmacy.config.PharmacyProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final PharmacyProperties properties;
    private final SecretKey key;

    public JwtService(PharmacyProperties properties) {
        this.properties = properties;
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be set and at least 32 bytes long.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String accessToken(UserPrincipal principal, Instant issuedAt) {
        Instant expiresAt = issuedAt.plusSeconds(properties.getJwt().getAccessTokenMinutes() * 60L);
        return Jwts.builder()
                .subject(principal.getUsername())
                .claim("uid", principal.getId())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public int accessTokenSeconds() {
        return properties.getJwt().getAccessTokenMinutes() * 60;
    }
}
