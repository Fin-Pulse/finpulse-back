package com.example.aggregationservice.model;

import lombok.Data;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_consent_id")
    private UUID userConsentId;

    @Column(name = "external_account_id")
    private String externalAccountId;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "account_type")
    private String accountType;

    @Column(name = "account_sub_type")
    private String accountSubType;

    private String currency = "RUB";
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "available_balance")
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "account_name")
    private String accountName;

    private String nickname;
    private String status = "Enabled";

    @Column(name = "opening_date")
    private LocalDate openingDate;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}