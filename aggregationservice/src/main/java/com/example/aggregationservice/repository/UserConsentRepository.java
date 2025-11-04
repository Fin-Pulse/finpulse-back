package com.example.aggregationservice.repository;

import com.example.aggregationservice.model.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {

    List<UserConsent> findByBankClientId(String bankClientId);
    Optional<UserConsent> findByBankClientIdAndBankId(String bankClientId, UUID bankId);

    Optional<UserConsent> findByConsentId(String consentId);
}
