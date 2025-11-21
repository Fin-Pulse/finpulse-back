package com.example.aggregationservice.model;

import lombok.Data;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "bank_products",
        indexes = {
                @Index(name = "idx_bank_products_bank_id", columnList = "bank_id"),
                @Index(name = "idx_bank_products_product_id", columnList = "product_id")
        })
public class BankProduct {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "bank_id", nullable = false)
    private UUID bankId;

    @Column(name = "product_id", nullable = false)
    private String productId;  // external product id from bank

    @Column(name = "product_type")
    private String productType;

    @Column(name = "product_name")
    private String productName;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "interest_rate", precision = 8, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "min_amount", precision = 15, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "term_months")
    private Integer termMonths;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
}
