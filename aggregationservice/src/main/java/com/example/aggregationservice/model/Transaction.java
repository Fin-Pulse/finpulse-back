// model/Transaction.java
package com.example.aggregationservice.model;

import lombok.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
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

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "bank_client_id", nullable = false)
    private String bankClientId;

    @Column(name = "external_transaction_id", nullable = false)
    private String externalTransactionId;

    @Column(name = "booking_date", nullable = false)
    private LocalDateTime bookingDate;

    @Column(nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "absolute_amount", nullable = false)
    private BigDecimal absoluteAmount = BigDecimal.ZERO;

    @Column(name = "is_expense", nullable = false)
    private Boolean isExpense;

    @Column(name = "transaction_information")
    private String transactionInformation;

    @Column(name = "credit_debit_indicator", nullable = false)
    private String creditDebitIndicator;

    @Column(name = "category")
    private String category;
}