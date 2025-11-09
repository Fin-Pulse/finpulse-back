package com.example.aggregationservice.repository;

import com.example.aggregationservice.model.Bank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankRepository extends JpaRepository<Bank, UUID> {

    @Query("SELECT b FROM Bank b WHERE b.isActive = true")
    List<Bank> findAllActiveBanks();

    Optional<Bank> findByCode(String code);
}