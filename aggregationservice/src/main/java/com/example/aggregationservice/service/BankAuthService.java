package com.example.aggregationservice.service;

import com.example.aggregationservice.config.BankApiProperties;
import com.example.aggregationservice.dto.BankTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankAuthService implements ApplicationRunner {

    private final RestTemplate restTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final BankApiProperties bankApiProperties;

    private static final String REDIS_TOKEN_KEY = "bank:team:token";
    private static final String REDIS_LOCK_KEY = "bank:token:refresh:lock";

    /**
     * Получает токен из Redis или обновляет его если нужно
     */
    public String getTeamToken() {
        String token = redisTemplate.opsForValue().get(REDIS_TOKEN_KEY);

        if (token != null && !isTokenExpired(token)) {
            log.debug("Using cached bank token from Redis");
            return token;
        }

        log.info("Token missing or expired, refreshing...");
        return refreshAndCacheToken();
    }

    /**
     * Принудительное обновление токена с distributed lock
     */
    public String refreshAndCacheToken() {
        // Пытаемся взять distributed lock
        Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(REDIS_LOCK_KEY, "locked", Duration.ofSeconds(30));

        if (Boolean.TRUE.equals(lockAcquired)) {
            try {
                log.info("Acquired refresh lock, fetching new token from bank...");
                BankTokenResponse tokenResponse = fetchNewTokenFromBank();
                cacheToken(tokenResponse.getAccessToken());
                return tokenResponse.getAccessToken();
            } finally {
                redisTemplate.delete(REDIS_LOCK_KEY);
                log.info("Released refresh lock");
            }
        } else {
            log.info("Another instance is refreshing token, waiting...");
            return waitForTokenRefresh();
        }
    }

    /**
     * Получение нового токена от банка
     */
    private BankTokenResponse fetchNewTokenFromBank() {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(bankApiProperties.getBaseUrl())
                    .path(bankApiProperties.getTokenEndpoint())
                    .queryParam("client_id", bankApiProperties.getClientId())
                    .queryParam("client_secret", bankApiProperties.getClientSecret())
                    .toUriString();

            log.info("Requesting new token from: {}", url.replace(bankApiProperties.getClientSecret(), "***"));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/x-www-form-urlencoded");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<BankTokenResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, BankTokenResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Successfully obtained new bank token");
                return response.getBody();
            } else {
                throw new RuntimeException("Bank API returned status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to fetch token from bank: {}", e.getMessage());
            throw new RuntimeException("Bank token refresh failed", e);
        }
    }

    /**
     * Кэширование токена в Redis
     */
    private void cacheToken(String token) {
        try {
            redisTemplate.opsForValue().set(
                    REDIS_TOKEN_KEY,
                    token,
                    bankApiProperties.getTokenTtlHours(),
                    TimeUnit.HOURS
            );
            log.info("Token cached in Redis for {} hours", bankApiProperties.getTokenTtlHours());
        } catch (Exception e) {
            log.error("Failed to cache token in Redis: {}", e.getMessage());
            throw new RuntimeException("Token caching failed", e);
        }
    }

    /**
     * Ожидание пока другой инстанс обновит токен
     */
    private String waitForTokenRefresh() {
        int maxAttempts = 10;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                Thread.sleep(2000); // Ждем 2 секунды
                String token = redisTemplate.opsForValue().get(REDIS_TOKEN_KEY);
                if (token != null && !isTokenExpired(token)) {
                    log.info("Token refreshed by another instance, using it");
                    return token;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Token refresh wait interrupted", e);
            }
        }
        throw new RuntimeException("Timeout waiting for token refresh");
    }

    /**
     * Проверка истечения срока действия JWT токена
     */
    private boolean isTokenExpired(String token) {
        try {
            // Простая проверка - в реальном приложении используй JWT библиотеку
            // Здесь проверяем только базовые признаки
            return token == null || token.length() < 10;
        } catch (Exception e) {
            log.warn("Token validation error, considering expired: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Автообновление токена по расписанию
     */
    @Scheduled(fixedRate = 3600000) // Каждый час
    public void scheduledTokenRefresh() {
        try {
            String token = redisTemplate.opsForValue().get(REDIS_TOKEN_KEY);
            if (token == null || isTokenExpiringSoon(token)) {
                log.info("Scheduled token refresh triggered");
                refreshAndCacheToken();
            }
        } catch (Exception e) {
            log.error("Scheduled token refresh failed: {}", e.getMessage());
        }
    }

    private boolean isTokenExpiringSoon(String token) {
        // В реальном приложении проверяй expiry claim JWT
        // Здесь просто обновляем если токен старше 20 часов
        Long ttl = redisTemplate.getExpire(REDIS_TOKEN_KEY, TimeUnit.HOURS);
        return ttl != null && ttl < 4; // Меньше 4 часов осталось
    }

    /**
     * Инициализация токена при старте приложения
     * ApplicationRunner выполняется после полной инициализации всех бинов, включая Redis
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("Initializing bank token on application startup...");
        try {
            refreshAndCacheToken();
            log.info("Bank token initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize bank token on startup: {}. Token will be fetched on first request.", e.getMessage());
        }
    }
}