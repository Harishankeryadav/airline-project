package com.airline.auth.security;

import com.airline.auth.entity.Role;
import com.airline.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Issues and verifies the platform's access tokens.
 *
 * TOKEN CONTRACT (the api-gateway implements the verifying side with the same secret):
 *   algorithm HS256 | iss = app.jwt.issuer | sub = user id | email = user email | roles = ["ADMIN", ...] | iat | exp
 */
@Service
@Slf4j
public class JwtService {

    private static final String DEV_SECRET = "dev-only-secret-change-me-dev-only-secret-change-me";

    private final SecretKey key;
    private final Duration ttl;
    private final String issuer;

    public JwtService(JwtProperties properties) {
        byte[] secret = properties.secret() == null ? new byte[0] : properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must be at least 32 characters long");
        }
        if (DEV_SECRET.equals(properties.secret())) {
            log.warn("Using the built-in DEV JWT secret. Set JWT_SECRET to a private value everywhere except local development.");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.ttl = Duration.ofMinutes(properties.expirationMinutes());
        this.issuer = properties.issuer();
    }

    public String generate(User user) {
        Instant now = Instant.now();
        List<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .map(Enum::name)
                .sorted()
                .toList();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("roles", roleNames)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** @return the caller's identity, or empty if the token is malformed, tampered with, expired or from another issuer */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            List<?> rawRoles = claims.get("roles", List.class);
            List<String> roles = rawRoles == null ? List.of() : rawRoles.stream().map(String::valueOf).toList();
            return Optional.of(new AuthenticatedUser(userId, claims.get("email", String.class), roles));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public long expiresInSeconds() {
        return ttl.toSeconds();
    }
}
