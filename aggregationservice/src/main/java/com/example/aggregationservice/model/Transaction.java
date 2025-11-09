// model/Transaction.java
package com.example.aggregationservice.model;

import lombok.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "bank_client_id", nullable = false) // 🔥 ДОБАВЛЕНО
    private String bankClientId;

    @Column(name = "external_transaction_id", nullable = false)
    private String externalTransactionId;

    @Column(nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    private String currency = "RUB";

    @Column(name = "credit_debit_indicator", nullable = false)
    private String creditDebitIndicator;

    private String status = "Booked";

    @Column(name = "booking_date", nullable = false)
    private LocalDateTime bookingDate;

    @Column(name = "value_date")
    private LocalDateTime valueDate;

    @Column(name = "transaction_information")
    private String transactionInformation;

    @Column(name = "bank_transaction_code")
    private String bankTransactionCode;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}