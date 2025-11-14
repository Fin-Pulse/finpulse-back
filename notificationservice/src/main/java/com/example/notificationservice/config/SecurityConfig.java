package com.example.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Swagger документация
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/api-docs/**",
                                "/webjars/**", "/swagger-resources/**").permitAll()

                        // WebSocket endpoints - разрешаем без аутентификации (Gateway уже проверил)
                        .requestMatchers("/ws/**", "/topic/**", "/app/**", "/user/**").permitAll()

                        // REST API - разрешаем все (аутентификация в Gateway)
                        .requestMatchers("/api/notifications/**").permitAll()

                        .anyRequest().permitAll()
                )
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Для WebSocket нужно отключить frameOptions
                .headers(headers -> headers.frameOptions().disable())
                // CORS обрабатывается в Gateway
                .cors(cors -> cors.disable());

        return http.build();
    }
}