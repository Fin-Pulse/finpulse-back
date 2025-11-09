// aggregationservice/service/UserGroupService.java
package com.example.aggregationservice.service;

import com.example.aggregationservice.client.UserServiceClient;
import com.example.aggregationservice.model.enums.TimeGroup;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserGroupService {

    private final UserServiceClient userServiceClient;
    private final RedisTemplate<String, Object> redisTemplate;

    public UserGroupService(UserServiceClient userServiceClient,
                           @Qualifier("objectRedisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.userServiceClient = userServiceClient;
        this.redisTemplate = redisTemplate;
    }

    private static final String USER_GROUP_KEY = "user_groups:%s";
    private static final String ALL_USERS_KEY = "all_active_users";

    /**
     * 🔥 Определяет временную группу пользователя
     */
    public TimeGroup getTimeGroupForUser(UUID userId) {
        int groupIndex = Math.abs(userId.hashCode()) % 4;
        return TimeGroup.values()[groupIndex];
    }

    /**
     * 🔥 Получает пользователей временной группы из Redis
     */
    public List<UUID> getUserIdsByTimeGroup(TimeGroup timeGroup) {
        String cacheKey = String.format(USER_GROUP_KEY, timeGroup.getCode());

        @SuppressWarnings("unchecked")
        List<UUID> cachedUsers = (List<UUID>) redisTemplate.opsForValue().get(cacheKey);

        if (cachedUsers != null) {
            return cachedUsers;
        }

        // Если кэш пустой - обновляем синхронно
        log.info("🔄 Cache miss for group {}, refreshing...", timeGroup.getCode());
        refreshUserGroupsCache();

        @SuppressWarnings("unchecked")
        List<UUID> users = (List<UUID>) redisTemplate.opsForValue().get(cacheKey);
        return users != null ? users : List.of();
    }

    /**
     * 🔥 Получает всех активных пользователей из Redis
     */
    public List<UUID> getAllActiveUserIds() {
        @SuppressWarnings("unchecked")
        List<UUID> cachedUsers = (List<UUID>) redisTemplate.opsForValue().get(ALL_USERS_KEY);

        if (cachedUsers != null) {
            return cachedUsers;
        }

        // Обновляем кэш
        refreshUserGroupsCache();

        @SuppressWarnings("unchecked")
        List<UUID> users = (List<UUID>) redisTemplate.opsForValue().get(ALL_USERS_KEY);
        return users != null ? users : List.of();
    }

    /**
     * 🔥 Обновляет кэш пользователей через FeignClient
     */
    public void refreshUserGroupsCache() {
        log.info("🔄 Refreshing user groups cache...");

        try {
            // 🔥 ВЫЗЫВАЕМ UserService через FeignClient
            List<UUID> allUsers = userServiceClient.getAllActiveUserIds();
            log.info("📥 Retrieved {} active users via Feign", allUsers.size());

            if (allUsers.isEmpty()) {
                log.warn("⚠️ No active users returned");
                return;
            }

            // Сохраняем всех пользователей
            redisTemplate.opsForValue().set(ALL_USERS_KEY, allUsers, 24, TimeUnit.HOURS);

            // Распределяем по группам
            for (TimeGroup group : TimeGroup.values()) {
                List<UUID> groupUsers = allUsers.stream()
                        .filter(userId -> getTimeGroupForUser(userId) == group)
                        .collect(Collectors.toList());

                String cacheKey = String.format(USER_GROUP_KEY, group.getCode());
                redisTemplate.opsForValue().set(cacheKey, groupUsers, 24, TimeUnit.HOURS);

                log.info("💾 Cached {} users for group {}", groupUsers.size(), group.getCode());
            }

            log.info("✅ User groups cache refreshed");

        } catch (FeignException.Forbidden e) {
            log.error("❌ Access forbidden (403) when calling UserService. Check if endpoint requires authentication or internal access configuration. URL: {}", e.request().url(), e);
            throw e; // Пробрасываем дальше, чтобы вызывающий код мог обработать
        } catch (FeignException e) {
            log.error("❌ Feign error when calling UserService. Status: {}, URL: {}", e.status(), e.request() != null ? e.request().url() : "unknown", e);
            throw e;
        } catch (Exception e) {
            log.error("❌ Failed to refresh user groups cache", e);
            throw e; // Пробрасываем, чтобы вызывающий код мог обработать
        }
    }
}