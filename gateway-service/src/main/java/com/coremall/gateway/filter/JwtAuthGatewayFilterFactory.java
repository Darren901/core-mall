package com.coremall.gateway.filter;

import com.coremall.sharedkernel.jwt.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

@Component
public class JwtAuthGatewayFilterFactory extends AbstractGatewayFilterFactory<JwtAuthGatewayFilterFactory.Config> {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthGatewayFilterFactory(JwtTokenProvider jwtTokenProvider) {
        super(Config.class);
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public static class Config {}

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return unauthorized(exchange);
            }
            String token = authHeader.substring(7);
            try {
                String userId = jwtTokenProvider.extractUserId(token);
                ServerWebExchange mutated = exchange.mutate()
                        .request(r -> r.header("X-User-Id", userId))
                        .build();
                return chain.filter(mutated);
            } catch (JwtException e) {
                return unauthorized(exchange);
            }
        };
    }

    private reactor.core.publisher.Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
