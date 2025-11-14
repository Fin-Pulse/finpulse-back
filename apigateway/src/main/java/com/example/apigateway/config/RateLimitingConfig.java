package com.example.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitingConfig {

    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(10, 20, 1);
    }

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // Сначала пытаемся получить user ID
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null) {
                return Mono.just(userId);
            }
            // Если нет user ID, используем IP
            return Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
        };
    }
}