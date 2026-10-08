package com.airline.gateway.config;

import com.airline.gateway.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class GatewayConfig {

    /**
     * Rate-limit key = route id + client IP. The route id is part of the key because Spring's Redis limiter keys its
     * buckets by this value only: without it, the strict login limit and the general limit would share one bucket.
     * The IP is the TCP peer address. (Behind a reverse proxy, configure Spring's forwarded-header support so this
     * becomes the real client address; X-Forwarded-For is deliberately NOT trusted here because clients can fake it.)
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            String routeId = route != null ? route.getId() : "none";
            ServerHttpRequest request = exchange.getRequest();
            InetSocketAddress remote = request.getRemoteAddress();
            String ip = (remote != null && remote.getAddress() != null) ? remote.getAddress().getHostAddress() : "unknown";
            return Mono.just(routeId + ":" + ip);
        };
    }
}
