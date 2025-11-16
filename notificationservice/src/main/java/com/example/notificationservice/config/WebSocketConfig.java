package com.example.notificationservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        WebSocketHandshakeHandler handshakeHandler = new WebSocketHandshakeHandler();
        WebSocketAuthInterceptor authInterceptor = new WebSocketAuthInterceptor();

        registry.addEndpoint("/ws/notifications")
                .addInterceptors(authInterceptor)
                .setHandshakeHandler(handshakeHandler)
                .setAllowedOriginPatterns("*") // Gateway уже проверил CORS
                .withSockJS();

        registry.addEndpoint("/ws/forecasts")
                .addInterceptors(authInterceptor)
                .setHandshakeHandler(handshakeHandler)
                .setAllowedOriginPatterns("*")
                .withSockJS();

    }
}