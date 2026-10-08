package com.airline.gateway.web;

import com.airline.gateway.dto.ApiResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

/**
 * Failures inside the gateway (service not registered, connection refused, timeout) answer in the same JSON envelope as
 * every service instead of Spring's default error page. Internal exception text is logged, never sent to the client.
 */
@Component
@Order(-2) // ahead of Spring Boot's default error handler (-1)
@RequiredArgsConstructor
@Slf4j
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatusCode status = statusOf(ex);
        String message = switch (status.value()) {
            case 404 -> "Not found";
            case 503 -> "Service unavailable";
            case 504 -> "Gateway timeout";
            default -> status.is4xxClientError() ? "Bad request" : "Internal server error";
        };
        String detail = switch (status.value()) {
            case 404 -> "No such resource";
            case 503 -> "The requested service is currently unavailable, please try again";
            case 504 -> "The requested service took too long to respond";
            default -> status.is4xxClientError() ? "The request could not be processed" : "An unexpected error occurred";
        };
        if (status.is5xxServerError()) {
            log.warn("{} {} -> {}: {}", exchange.getRequest().getMethod(), exchange.getRequest().getURI().getRawPath(),
                    status.value(), rootMessage(ex));
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(ApiResult.fail(message, detail));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }

    private static HttpStatusCode statusOf(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ResponseStatusException rse) {
                return rse.getStatusCode();                       // e.g. 503 "Unable to find instance for X", 404 no route
            }
            if (t instanceof TimeoutException) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
            if (t instanceof ConnectException) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
