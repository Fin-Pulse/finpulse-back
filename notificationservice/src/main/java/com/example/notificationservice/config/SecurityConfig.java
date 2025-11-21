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
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/api-docs/**",
                                "/webjars/**", "/swagger-resources/**").permitAll()
                        .requestMatchers("/ws/**", "/topic/**", "/app/**", "/user/**").permitAll()
                        .requestMatchers("/api/notifications/**").permitAll()
                        .anyRequest().permitAll()
                )
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions().disable());
        // УБРАЛИ: .cors() - CORS теперь обрабатывается только в API Gateway

        return http.build();
    }

    // УБРАЛИ ВЕСЬ CorsFilter бин - чтобы не дублировать CORS headers
}