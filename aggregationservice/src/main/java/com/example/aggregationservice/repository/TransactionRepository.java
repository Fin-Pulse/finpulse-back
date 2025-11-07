package com.example.aggregationservice.repository;

import com.example.aggregationservice.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    boolean existsByAccountIdAndExternalTransactionId(UUID accountId, String externalTransactionId);

    @Query("SELECT COUNT(t) > 0 FROM Transaction t WHERE t.accountId = :accountId AND t.externalTransactionId = :externalTransactionId")
    boolean transactionExists(@Param("accountId") UUID accountId, @Param("externalTransactionId") String externalTransactionId);
}