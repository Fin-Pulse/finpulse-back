package com.example.notificationservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

        String userId = extractUserIdFromHeaders(request);
        if (userId == null) {
            userId = extractUserIdFromQuery(request.getURI());
        }

        if (userId == null) {
            log.warn("WebSocket handshake - No userId found in headers or query parameters");
            return true;
        }

        String validatedUserId = validateAndCorrectUserId(userId);
        if (validatedUserId != null) {
            attributes.put("userId", validatedUserId);

            if (request instanceof ServletServerHttpRequest) {
                ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
                servletRequest.getServletRequest().setAttribute("userId", validatedUserId);
            }

            return true;
        } else {
            log.error("WebSocket handshake - Invalid userId format: {}", userId);
            return false;
        }
    }

    /**
     * Извлекает userId из заголовков, которые устанавливает API Gateway
     */
    private String extractUserIdFromHeaders(ServerHttpRequest request) {
        String userId = request.getHeaders().getFirst("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            log.debug("Found userId in headers: {}", userId);
            return userId;
        }
        return null;
    }

    /**
     * Извлекает userId из query параметров (для обратной совместимости)
     */
    private String extractUserIdFromQuery(URI uri) {
        String query = uri.getQuery();
        if (query == null || query.isEmpty()) {
            return null;
        }

        try {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("userId=")) {
                    String userId = param.substring(7);
                    if (userId.contains("%")) {
                        userId = java.net.URLDecoder.decode(userId, "UTF-8");
                    }
                    log.debug("Found userId in query: {}", userId);
                    return userId;
                }
            }
        } catch (Exception e) {
            log.error("Error extracting userId from query: {}", e.getMessage());
        }
        return null;
    }

    private String validateAndCorrectUserId(String userId) {
        if (userId == null) return null;

        String cleaned = userId.trim().toLowerCase();

        try {
            UUID.fromString(cleaned);
            return cleaned;
        } catch (IllegalArgumentException e) {
            log.warn("Attempting to correct invalid UUID: {}", cleaned);
        }

        if (cleaned.length() == 31) {
            if (cleaned.startsWith("f9a98d3")) {
                String corrected = "0" + cleaned;
                try {
                    UUID.fromString(corrected);
                    return corrected;
                } catch (IllegalArgumentException ex) {
                }
            }
        }

        log.error("Unable to correct invalid UUID: {}", cleaned);
        return null;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.error("WebSocket handshake failed: {}", exception.getMessage());
        } else {
            log.debug("WebSocket handshake completed successfully");
        }
    }
}