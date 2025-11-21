package com.example.productservice.service;

import com.example.productservice.dto.UserProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final RestTemplate restTemplate;
    private final String userServiceUrl = "http://userservice:8081/api/bank/users";

    @Cacheable(value = "userProfiles", key = "#userId", unless = "#result == null")
    public UserProfile getUserProfile(UUID userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<UserProfile> response = restTemplate.exchange(
                    userServiceUrl + "/me",
                    HttpMethod.GET,
                    entity,
                    UserProfile.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                UserProfile profile = response.getBody();
                log.debug("Профиль пользователя {} успешно получен: {} {}",
                        userId, profile.getFullName(), profile.getEmail());
                return profile;
            } else {
                log.warn("Не удалось получить профиль пользователя {}, статус: {}",
                        userId, response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Ошибка при получении профиля пользователя {}: {}", userId, e.getMessage());
        }
        return null;
    }

    // Метод для принудительного обновления кэша
    @CacheEvict(value = "userProfiles", key = "#userId")
    public void evictUserProfileCache(UUID userId) {
    }

    // Метод для массового обновления кэша
    @CacheEvict(value = "userProfiles", allEntries = true)
    public void evictAllUserProfilesCache() {
    }
}
