package com.example.userservice.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class AuthRequest {
    @NotBlank
    private String email;

    @NotBlank
    private String password;
}