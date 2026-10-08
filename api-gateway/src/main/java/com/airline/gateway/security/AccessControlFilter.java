package com.airline.gateway.security;

import com.airline.gateway.dto.ApiResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * Edge security. Runs before routing: verifies the JWT, applies {@link AccessPolicy}, and answers 400 / 401 / 403 / 404
 * in the standard JSON envelope. Allowed requests are forwarded UNCHANGED (including Authorization) - every service
 * verifies the token again itself, so nothing depends on the gateway being the only way in.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccessControlFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String LEGACY_HEADER = "x-access-token";

    private final AccessPolicy policy;
    private final JwtVerifier verifier;
    private final ObjectMapper objectMapper;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;   // before routing and rate limiting
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String rawPath = request.getURI().getRawPath();
        if (isSuspicious(rawPath)) {
            return reject(exchange, HttpStatus.BAD_REQUEST, "Bad request", "The request path is not valid");
        }

        // A bad token is not an error by itself: public endpoints stay reachable, protected ones answer 401.
        AuthenticatedUser user = extractToken(request).flatMap(verifier::parse).orElse(null);
        AccessPolicy.Decision decision = policy.decide(request.getMethod(), rawPath, user);

        return switch (decision) {
            case ALLOW -> chain.filter(exchange);
            case UNAUTHORIZED -> reject(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized", "Missing, invalid or expired token");
            case FORBIDDEN -> reject(exchange, HttpStatus.FORBIDDEN, "Forbidden", "You do not have permission to perform this action");
            case NOT_FOUND -> reject(exchange, HttpStatus.NOT_FOUND, "Not found", "No such resource");
        };
    }

    /**
     * Rejects paths that could be used to slip past a rule: ".." segments, encoded dots or slashes, ';' path parameters,
     * backslashes and doubled slashes. (The rules match the path as written; downstream servers may normalise it.)
     */
    static boolean isSuspicious(String rawPath) {
        String lower = rawPath.toLowerCase(Locale.ROOT);
        return lower.contains("..") || lower.contains("%2e") || lower.contains("%2f") || lower.contains("%5c")
                || lower.contains("%00") || lower.contains(";") || lower.contains("\\") || lower.contains("//");
    }

    private static Optional<String> extractToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return Optional.of(authorization.substring(BEARER_PREFIX.length()).trim());
        }
        String legacy = request.getHeaders().getFirst(LEGACY_HEADER);
        return (legacy == null || legacy.isBlank()) ? Optional.empty() : Optional.of(legacy.trim());
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message, String detail) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.getHeaders().set("WWW-Authenticate", "Bearer");
        }
        try {
            byte[] body = objectMapper.writeValueAsBytes(ApiResult.fail(message, detail));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        } catch (JsonProcessingException e) {
            log.error("Could not write the error response", e);
            return response.setComplete();
        }
    }
}
