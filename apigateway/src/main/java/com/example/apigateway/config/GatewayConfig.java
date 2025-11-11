package com.example.apigateway.config;

import com.example.apigateway.filter.JwtAuthFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public GatewayConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // User Service Routes - публичные эндпоинты
                .route("user-service-auth", r -> r.path("/api/bank/auth/**")
                        .uri("lb://user-service"))

                // User Service Routes - защищенные эндпоинты
                .route("user-service-users", r -> r.path("/api/bank/users/**")
                        .filters(f -> f.filter(jwtAuthFilter))
                        .uri("lb://user-service"))

                // Notification Service REST Routes
                .route("notification-service-rest", r -> r.path("/api/notifications/**")
                        .filters(f -> f.filter(jwtAuthFilter))
                        .uri("lb://notification-service"))

                // Notification Service WebSocket Routes
                .route("notification-service-ws", r -> r.path("/ws/notifications/**")
                        .filters(f -> f.rewritePath("/ws/notifications/(?<segment>.*)", "/ws/${segment}"))
                        .uri("lb://notification-service"))

                // Forecast Service WebSocket Routes
                .route("forecast-service-ws", r -> r.path("/ws/forecasts/**")
                        .filters(f -> f.rewritePath("/ws/forecasts/(?<segment>.*)", "/ws/${segment}"))
                        .uri("lb://notification-service"))

                // Aggregation Service Routes (только внутренние вызовы)
                .route("aggregation-service-internal", r -> r.path("/api/verification/**")
                        .filters(f -> f.filter(jwtAuthFilter))
                        .uri("lb://aggregation-service"))

                .build();
    }
}