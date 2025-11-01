package com.example.aggregationservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BankTokenResponse {
    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("expires_in")
    private Long expiresIn;
}