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
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankAuthService implements ApplicationRunner {

    private final RestTemplate restTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final BankApiProperties bankApiProperties;

    private static final String REDIS_TOKEN_PREFIX = "bank:token:";
    private static final String REDIS_LOCK_PREFIX = "bank:refresh:lock:";

    // Список банков для итерации
    private static final List<String> SUPPORTED_BANKS = List.of("vbank", "abank", "sbank");

    /**
     * Получает токен для конкретного банка
     */
    public String getBankToken(String bankCode) {
        String redisKey = getTokenRedisKey(bankCode);
        String token = redisTemplate.opsForValue().get(redisKey);

        if (token != null && !isTokenExpired(token)) {
            log.debug("Using cached token for bank: {}", bankCode);
            return token;
        }

        log.info("Token missing or expired for bank: {}, refreshing...", bankCode);
        return refreshAndCacheToken(bankCode);
    }

    /**
     * Принудительное обновление токена для конкретного банка
     */
    public String refreshAndCacheToken(String bankCode) {
        String lockKey = getLockRedisKey(bankCode);

        Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "locked", Duration.ofSeconds(30));

        if (Boolean.TRUE.equals(lockAcquired)) {
            try {
                log.info("Acquired refresh lock for bank: {}, fetching new token...", bankCode);
                BankTokenResponse tokenResponse = fetchNewTokenFromBank(bankCode);
                cacheToken(bankCode, tokenResponse.getAccessToken());
                return tokenResponse.getAccessToken();
            } finally {
                redisTemplate.delete(lockKey);
                log.info("Released refresh lock for bank: {}", bankCode);
            }
        } else {
            log.info("Another instance is refreshing token for bank: {}, waiting...", bankCode);
            return waitForTokenRefresh(bankCode);
        }
    }

    /**
     * Получение нового токена от конкретного банка
     */
    private BankTokenResponse fetchNewTokenFromBank(String bankCode) {
        BankApiProperties.BankConfig bankConfig = bankApiProperties.getBankConfig(bankCode);
        if (bankConfig == null) {
            throw new RuntimeException("Bank configuration not found for: " + bankCode);
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(bankConfig.getBaseUrl())
                    .path(bankConfig.getTokenEndpoint())
                    .queryParam("client_id", bankConfig.getClientId())
                    .queryParam("client_secret", bankConfig.getClientSecret())
                    .toUriString();

            log.info("Requesting new token from bank: {} - {}", bankCode,
                    url.replace(bankConfig.getClientSecret(), "***"));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/x-www-form-urlencoded");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<BankTokenResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, BankTokenResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Successfully obtained new token for bank: {}", bankCode);
                return response.getBody();
            } else {
                throw new RuntimeException("Bank API returned status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to fetch token from bank {}: {}", bankCode, e.getMessage());
            throw new RuntimeException("Bank token refresh failed for: " + bankCode, e);
        }
    }

    /**
     * Кэширование токена для конкретного банка
     */
    private void cacheToken(String bankCode, String token) {
        try {
            BankApiProperties.BankConfig bankConfig = bankApiProperties.getBankConfig(bankCode);
            long ttlHours = bankConfig != null ? bankConfig.getTokenTtlHours() : 24;

            redisTemplate.opsForValue().set(
                    getTokenRedisKey(bankCode),
                    token,
                    ttlHours,
                    TimeUnit.HOURS
            );
            log.info("Token cached for bank: {} for {} hours", bankCode, ttlHours);
        } catch (Exception e) {
            log.error("Failed to cache token for bank {}: {}", bankCode, e.getMessage());
            throw new RuntimeException("Token caching failed for: " + bankCode, e);
        }
    }

    /**
     * Ожидание пока другой инстанс обновит токен для конкретного банка
     */
    private String waitForTokenRefresh(String bankCode) {
        int maxAttempts = 10;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                Thread.sleep(2000);
                String token = redisTemplate.opsForValue().get(getTokenRedisKey(bankCode));
                if (token != null && !isTokenExpired(token)) {
                    log.info("Token refreshed by another instance for bank: {}, using it", bankCode);
                    return token;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Token refresh wait interrupted for: " + bankCode, e);
            }
        }
        throw new RuntimeException("Timeout waiting for token refresh for bank: " + bankCode);
    }

    /**
     * Автообновление токенов для всех банков по расписанию
     */
    @Scheduled(fixedRate = 3600000) // Каждый час
    public void scheduledTokenRefresh() {
        for (String bankCode : SUPPORTED_BANKS) {
            try {
                String token = redisTemplate.opsForValue().get(getTokenRedisKey(bankCode));
                if (token == null || isTokenExpiringSoon(bankCode, token)) {
                    log.info("Scheduled token refresh triggered for bank: {}", bankCode);
                    refreshAndCacheToken(bankCode);
                }
            } catch (Exception e) {
                log.error("Scheduled token refresh failed for bank {}: {}", bankCode, e.getMessage());
            }
        }
    }

    /**
     * Инициализация токенов для всех банков при старте приложения
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("Initializing bank tokens on application startup...");

        for (String bankCode : SUPPORTED_BANKS) {
            try {
                if (bankApiProperties.getBankConfig(bankCode) != null) {
                    refreshAndCacheToken(bankCode);
                    log.info("Bank token initialized successfully for: {}", bankCode);
                }
            } catch (Exception e) {
                log.error("Failed to initialize bank token for {} on startup: {}. Token will be fetched on first request.",
                        bankCode, e.getMessage());
            }
        }
    }

    // Вспомогательные методы
    private String getTokenRedisKey(String bankCode) {
        return REDIS_TOKEN_PREFIX + bankCode;
    }

    private String getLockRedisKey(String bankCode) {
        return REDIS_LOCK_PREFIX + bankCode;
    }

    private boolean isTokenExpired(String token) {
        try {
            return token == null || token.length() < 10;
        } catch (Exception e) {
            log.warn("Token validation error, considering expired: {}", e.getMessage());
            return true;
        }
    }

    private boolean isTokenExpiringSoon(String bankCode, String token) {
        Long ttl = redisTemplate.getExpire(getTokenRedisKey(bankCode), TimeUnit.HOURS);
        return ttl != null && ttl < 4; // Меньше 4 часов осталось
    }

    /**
     * Получение списка поддерживаемых банков
     */
    public List<String> getSupportedBanks() {
        return SUPPORTED_BANKS;
    }
}