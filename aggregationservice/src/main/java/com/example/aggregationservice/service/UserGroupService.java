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

    public TimeGroup getTimeGroupForUser(UUID userId) {
        int groupIndex = Math.abs(userId.hashCode()) % 4;
        return TimeGroup.values()[groupIndex];
    }

    public List<UUID> getUserIdsByTimeGroup(TimeGroup timeGroup) {
        String cacheKey = String.format(USER_GROUP_KEY, timeGroup.getCode());

        @SuppressWarnings("unchecked")
        List<UUID> cachedUsers = (List<UUID>) redisTemplate.opsForValue().get(cacheKey);

        if (cachedUsers != null) {
            return cachedUsers;
        }

        refreshUserGroupsCache();

        @SuppressWarnings("unchecked")
        List<UUID> users = (List<UUID>) redisTemplate.opsForValue().get(cacheKey);
        return users != null ? users : List.of();
    }

    public List<UUID> getAllActiveUserIds() {
        @SuppressWarnings("unchecked")
        List<UUID> cachedUsers = (List<UUID>) redisTemplate.opsForValue().get(ALL_USERS_KEY);

        if (cachedUsers != null) {
            return cachedUsers;
        }

        refreshUserGroupsCache();

        @SuppressWarnings("unchecked")
        List<UUID> users = (List<UUID>) redisTemplate.opsForValue().get(ALL_USERS_KEY);
        return users != null ? users : List.of();
    }

    public void refreshUserGroupsCache() {

        try {
            List<UUID> allUsers = userServiceClient.getAllActiveUserIds();

            if (allUsers.isEmpty()) {
                log.warn("No active users returned");
                return;
            }

            redisTemplate.opsForValue().set(ALL_USERS_KEY, allUsers, 24, TimeUnit.HOURS);

            for (TimeGroup group : TimeGroup.values()) {
                List<UUID> groupUsers = allUsers.stream()
                        .filter(userId -> getTimeGroupForUser(userId) == group)
                        .collect(Collectors.toList());

                String cacheKey = String.format(USER_GROUP_KEY, group.getCode());
                redisTemplate.opsForValue().set(cacheKey, groupUsers, 24, TimeUnit.HOURS);
            }

        } catch (FeignException.Forbidden e) {
            log.error("Access forbidden (403) when calling UserService. Check if endpoint requires authentication or internal access configuration. URL: {}", e.request().url(), e);
            throw e;
        } catch (FeignException e) {
            log.error("Feign error when calling UserService. Status: {}, URL: {}", e.status(), e.request() != null ? e.request().url() : "unknown", e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to refresh user groups cache", e);
            throw e;
        }
    }
}