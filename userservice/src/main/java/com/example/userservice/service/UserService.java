package com.example.userservice.service;

import com.example.userservice.dto.UserProfile;
import com.example.userservice.entity.User;
import com.example.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

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
}