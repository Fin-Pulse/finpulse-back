package com.example.userservice.controller;

import com.example.userservice.dto.UserProfile;
import com.example.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/bank/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserProfile> getCurrentUser(@AuthenticationPrincipal UUID userId) {
        UserProfile profile = userService.getUserProfile(userId);
        return ResponseEntity.ok(profile);
    }
}