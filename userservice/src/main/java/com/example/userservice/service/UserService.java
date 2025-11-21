package com.example.userservice.service;

import com.example.userservice.dto.UserProfile;
import com.example.userservice.entity.User;
import com.example.userservice.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String USER_BANK_CACHE_PREFIX = "user:bank:";
    private static final String DEMO_USER_LOCK_PREFIX = "demo:user:lock:";

    public UserProfile getUserProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserProfile profile = new UserProfile();
        profile.setId(user.getId());
        profile.setEmail(user.getEmail());
        profile.setPhone(user.getPhone());
        profile.setFullName(user.getFullName());
        profile.setBankClientId(user.getBankClientId());
        profile.setVerified(user.isVerified());
        profile.setCreatedAt(user.getCreatedAt());

        return profile;
    }

    public void verifyUser(String verificationToken) {
        User user = userRepository.findByVerificationToken(verificationToken)
                .orElseThrow(() -> new RuntimeException("Invalid verification token"));

        user.setVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);

        userRepository.save(user);
    }

    public User getAvailableDemoUser() {
        List<User> demoUsers = userRepository.findAllByEmailEndingWith("@gmail.com");

        // Добавляем проверку на пустой список
        if (demoUsers.isEmpty()) {
            throw new RuntimeException("No demo users available");
        }

        log.info("Found {} demo users", demoUsers.size());

        for (User user : demoUsers) {
            String lockKey = DEMO_USER_LOCK_PREFIX + user.getId();
            try {
                Boolean isLocked = redisTemplate.opsForValue().setIfAbsent(
                        lockKey,
                        "locked",
                        Duration.ofMinutes(5)
                );

                if (Boolean.TRUE.equals(isLocked)) {
                    log.info("Selected demo user: {}", user.getEmail());
                    return user;
                }
            } catch (Exception e) {
                log.error("Redis error for user {}: {}", user.getId(), e.getMessage());
                // Продолжаем с следующим пользователем при ошибке Redis
                continue;
            }
        }

        // Если все пользователи заняты, берем первого и сбрасываем блокировки
        log.warn("All demo users are locked, resetting locks and using first user");
        resetAllDemoLocks(demoUsers);

        User fallback = demoUsers.get(0);
        redisTemplate.opsForValue().set(
                DEMO_USER_LOCK_PREFIX + fallback.getId(),
                "locked",
                Duration.ofMinutes(5)
        );

        return fallback;
    }

    private void resetAllDemoLocks(List<User> demoUsers) {
        for (User user : demoUsers) {
            try {
                redisTemplate.delete(DEMO_USER_LOCK_PREFIX + user.getId());
            } catch (Exception e) {
                log.error("Failed to delete lock for user {}: {}", user.getId(), e.getMessage());
            }
        }
    }

    // Добавляем метод для очистки всех блокировок (можно вызвать при старте приложения)
    @PostConstruct
    public void clearAllDemoLocks() {
        try {
            List<User> demoUsers = userRepository.findAllByEmailEndingWith("@gmail.com");
            resetAllDemoLocks(demoUsers);
            log.info("Cleared all demo user locks");
        } catch (Exception e) {
            log.error("Failed to clear demo locks: {}", e.getMessage());
        }
    }

}