package com.example.aggregationservice.repository;

import com.example.aggregationservice.model.BankProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankProductRepository extends JpaRepository<BankProduct, UUID> {

    Optional<BankProduct> findByBankIdAndProductId(UUID bankId, String productId);

    List<BankProduct> findByBankId(UUID bankId);
}

