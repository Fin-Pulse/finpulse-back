package com.example.aggregationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccount {
    private String accountId;
    private String status;
    private String currency;
    private String accountType;
    private String accountSubType;
    private String nickname;
    private LocalDate openingDate;
    private AccountDetails account;
}
