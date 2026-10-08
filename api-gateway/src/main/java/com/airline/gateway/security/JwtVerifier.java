package com.airline.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Verifies tokens issued by auth-service (HS256, same shared secret and issuer).
 * Each service verifies for itself, so nothing depends on trusting headers added by a gateway.
 * Claims: sub = user id, email, roles = ["ADMIN", ...].
 */
@Service
@Slf4j
public class JwtVerifier {

    private static final String DEV_SECRET = "dev-only-secret-change-me-dev-only-secret-change-me";

    private final SecretKey key;
    private final String issuer;

    public JwtVerifier(JwtProperties properties) {
        byte[] secret = properties.secret() == null ? new byte[0] : properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must be at least 32 characters long");
        }
        if (DEV_SECRET.equals(properties.secret())) {
            log.warn("Using the built-in DEV JWT secret. Set JWT_SECRET (same value as auth-service) outside local development.");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.issuer = properties.issuer();
    }

    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            String email = claims.get("email", String.class);
            if (email == null || email.isBlank()) {
                return Optional.empty();
            }
            List<?> rawRoles = claims.get("roles", List.class);
            List<String> roles = rawRoles == null ? List.of() : rawRoles.stream().map(String::valueOf).toList();
            return Optional.of(new AuthenticatedUser(userId, email, roles));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected token: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
