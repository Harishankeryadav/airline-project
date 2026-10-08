package com.airline.auth.security;

import com.airline.auth.entity.Role;
import com.airline.auth.entity.RoleName;
import com.airline.auth.entity.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-1234";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60, "test-issuer"));

    private User user() {
        User user = new User();
        user.setId(42L);
        user.setEmail("ana@example.com");
        user.getRoles().add(new Role(RoleName.CUSTOMER));
        user.getRoles().add(new Role(RoleName.ADMIN));
        return user;
    }

    @Test
    void tokenRoundTripKeepsIdentityAndRoles() {
        String token = jwtService.generate(user());

        Optional<AuthenticatedUser> parsed = jwtService.parse(token);

        assertTrue(parsed.isPresent());
        assertEquals(42L, parsed.get().id());
        assertEquals("ana@example.com", parsed.get().email());
        assertEquals(List.of("ADMIN", "CUSTOMER"), parsed.get().roles());
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generate(user());
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertTrue(jwtService.parse(tampered).isEmpty());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService other = new JwtService(new JwtProperties("another-secret-another-secret-another-1234", 60, "test-issuer"));

        assertTrue(jwtService.parse(other.generate(user())).isEmpty());
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        JwtService other = new JwtService(new JwtProperties(SECRET, 60, "someone-else"));

        assertTrue(jwtService.parse(other.generate(user())).isEmpty());
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService shortLived = new JwtService(new JwtProperties(SECRET, -1, "test-issuer"));

        assertTrue(jwtService.parse(shortLived.generate(user())).isEmpty());
    }

    @Test
    void garbageIsRejected() {
        assertTrue(jwtService.parse("not-a-jwt").isEmpty());
        assertTrue(jwtService.parse("").isEmpty());
    }

    @Test
    void shortSecretFailsFast() {
        assertThrows(IllegalStateException.class,
                () -> new JwtService(new JwtProperties("too-short", 60, "test-issuer")));
    }

    @Test
    void reportsTokenLifetimeInSeconds() {
        assertEquals(3600, jwtService.expiresInSeconds());
    }
}
