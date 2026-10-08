package com.airline.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessControlFilterTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-1234";
    private static final String ISSUER = "test-issuer";

    private AccessControlFilter filter;
    private final AtomicBoolean forwarded = new AtomicBoolean();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(true);
        return Mono.empty();
    };

    @BeforeEach
    void setUp() {
        filter = new AccessControlFilter(new AccessPolicy(), new JwtVerifier(new JwtProperties(SECRET, ISSUER)), new ObjectMapper());
    }

    private String token(String secret, String issuer, Duration validFor, String... roles) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder().issuer(issuer).subject("5").claim("email", "ana@example.com").claim("roles", List.of(roles))
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(validFor))).signWith(key, Jwts.SIG.HS256).compact();
    }

    private MockServerWebExchange run(HttpMethod method, String path, String authorizationHeader) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(method, URI.create(path));
        if (authorizationHeader != null) {
            builder.header("Authorization", authorizationHeader);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(builder.build());
        filter.filter(exchange, chain).block();
        return exchange;
    }

    @Test
    void publicEndpointIsForwardedWithoutAToken() {
        run(HttpMethod.GET, "/flightsservice/api/v1/flights", null);
        assertTrue(forwarded.get());
    }

    @Test
    void protectedEndpointWithoutATokenIs401WithTheJsonEnvelope() {
        MockServerWebExchange exchange = run(HttpMethod.GET, "/bookingservice/api/v1/bookings/my", null);

        assertFalse(forwarded.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        String body = exchange.getResponse().getBodyAsString().block();
        assertTrue(body.contains("\"success\":false"));
        assertTrue(body.contains("Unauthorized"));
    }

    @Test
    void validCustomerTokenReachesBookingButNotAdminEndpoints() {
        String customer = "Bearer " + token(SECRET, ISSUER, Duration.ofMinutes(5), "CUSTOMER");

        run(HttpMethod.GET, "/bookingservice/api/v1/bookings/my", customer);
        assertTrue(forwarded.get());

        forwarded.set(false);
        MockServerWebExchange exchange = run(HttpMethod.GET, "/reminderservice/api/v1/tickets", customer);
        assertFalse(forwarded.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void adminTokenReachesTheReminderApi() {
        run(HttpMethod.GET, "/reminderservice/api/v1/tickets", "Bearer " + token(SECRET, ISSUER, Duration.ofMinutes(5), "ADMIN"));
        assertTrue(forwarded.get());
    }

    @Test
    void internalSeatEndpointsAre404EvenForAdmins() {
        MockServerWebExchange exchange = run(HttpMethod.POST, "/flightsservice/api/v1/flights/3/seats/reserve",
                "Bearer " + token(SECRET, ISSUER, Duration.ofMinutes(5), "ADMIN"));

        assertFalse(forwarded.get());
        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
    }

    @Test
    void tamperedExpiredAndForeignTokensAreTreatedAsNoToken() {
        for (String bad : new String[]{
                token("another-secret-another-secret-another-1234", ISSUER, Duration.ofMinutes(5), "ADMIN"),
                token(SECRET, "someone-else", Duration.ofMinutes(5), "ADMIN"),
                token(SECRET, ISSUER, Duration.ofMinutes(-5), "ADMIN"),
                "garbage"}) {
            forwarded.set(false);
            MockServerWebExchange exchange = run(HttpMethod.GET, "/reminderservice/api/v1/tickets", "Bearer " + bad);
            assertFalse(forwarded.get());
            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        }
    }

    @Test
    void aBadTokenDoesNotBlockPublicEndpoints() {
        run(HttpMethod.GET, "/flightsservice/api/v1/airports/search?q=del", "Bearer garbage");
        assertTrue(forwarded.get());
    }

    @Test
    void theLegacyAccessTokenHeaderIsAccepted() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/bookingservice/api/v1/bookings/my")
                .header("x-access-token", token(SECRET, ISSUER, Duration.ofMinutes(5), "CUSTOMER")).build();
        filter.filter(MockServerWebExchange.from(request), chain).block();
        assertTrue(forwarded.get());
    }

    @Test
    void pathTricksAreRejectedBeforeAnyRuleIsApplied() {
        for (String path : new String[]{
                "/flightsservice/api/v1/flights/3/seats%2Freserve",
                "/flightsservice/api/v1/flights/3/%2e%2e/seats/reserve",
                "/flightsservice/../authservice/api/v1/users",
                "/flightsservice/api/v1/flights/3/seats;x=1/reserve",
                "/flightsservice//api/v1/flights"}) {
            forwarded.set(false);
            MockServerWebExchange exchange = run(HttpMethod.POST, path, null);
            assertFalse(forwarded.get(), path);
            assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode(), path);
        }
    }
}
