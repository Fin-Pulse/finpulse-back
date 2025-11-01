package com.example.aggregationservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class AccountDto {
    @JsonProperty("account_id")
    private String accountId;

    private String currency;

    @JsonProperty("account_type")
    private String accountType;

    private String nickname;

    private Servicer servicer;

    @Data
    public static class Servicer {
        @JsonProperty("bank_id")
        private String bankId;

        @JsonProperty("bank_name")
        private String bankName;
    }
}