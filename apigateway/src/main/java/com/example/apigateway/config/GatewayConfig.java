package com.example.apigateway.config;

import com.example.apigateway.filter.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class GatewayConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${user.service.url:http://localhost:8081}")
    private String userServiceUrl;

    @Value("${notification.service.url:http://localhost:8084}")
    private String notificationServiceUrl;

    @Value("${aggregation.service.url:http://localhost:8082}")
    private String aggregationServiceUrl;

    public GatewayConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public GatewayFilter jwtGatewayFilter() {
        return jwtAuthFilter.apply(new JwtAuthFilter.Config());
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("user-service-auth", r -> r.path("/api/bank/auth/**")
                        .uri(userServiceUrl))
                .route("user-service-users", r -> r.path("/api/bank/users/**")
                        .filters(f -> f.filter(jwtGatewayFilter()))
                        .uri(userServiceUrl))
                .route("notification-service-rest", r -> r.path("/api/notifications/**")
                        .filters(f -> f.filter(jwtGatewayFilter()))
                        .uri(notificationServiceUrl))
                .route("notification-service-ws", r -> r.path("/ws/notifications/**")
                        .uri(notificationServiceUrl))
                .route("forecast-service-ws", r -> r.path("/ws/forecasts/**")
                        .uri(notificationServiceUrl))
                .route("aggregation-service", r -> r.path("/api/verification/**")
                        .filters(f -> f.filter(jwtGatewayFilter()))
                        .uri(aggregationServiceUrl))
                .route("recommendation-service-ws", r -> r.path("/ws/recommendations/**")
                        .uri(notificationServiceUrl))

                .build();
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOrigins(List.of("http://localhost", "http://127.0.0.1"));
        corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        corsConfig.setAllowedHeaders(List.of("*"));
        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
