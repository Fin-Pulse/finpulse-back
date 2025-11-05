package com.example.aggregationservice.repository;

import com.example.aggregationservice.model.enums.ConsentStatus;
import com.example.aggregationservice.model.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {

    List<UserConsent> findByBankClientId(String bankClientId);

    List<UserConsent> findByBankClientIdAndStatus(String bankClientId, ConsentStatus status);

    Optional<UserConsent> findByBankClientIdAndBankId(String bankClientId, UUID bankId);

    Optional<UserConsent> findByConsentId(String consentId);

    Optional<UserConsent> findByRequestId(String requestId);

    @Query("SELECT uc FROM UserConsent uc WHERE uc.bankClientId = :bankClientId AND uc.status = 'PENDING'")
    List<UserConsent> findPendingConsentsByBankClientId(String bankClientId);

    @Query("SELECT DISTINCT uc.bankClientId FROM UserConsent uc WHERE uc.status = 'ACTIVE'")
    List<String> findDistinctBankClientIdsWithActiveConsents();
}