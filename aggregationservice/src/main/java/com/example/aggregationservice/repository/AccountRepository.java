package com.example.aggregationservice.repository;

import com.example.aggregationservice.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserConsentId(UUID userConsentId);

    List<Account> findByUserConsentIdIn(List<UUID> userConsentIds);

    @Query("SELECT COUNT(a) FROM Account a WHERE a.userConsentId IN " +
            "(SELECT uc.id FROM UserConsent uc WHERE uc.bankClientId = :bankClientId)")
    int countAccountsByBankClientId(String bankClientId);

    @Query("SELECT a FROM Account a WHERE a.userConsentId IN " +
            "(SELECT uc.id FROM UserConsent uc WHERE uc.bankClientId = :bankClientId)")
    List<Account> findByBankClientId(String bankClientId);

    @Query("SELECT a FROM Account a " +
            "JOIN UserConsent uc ON a.userConsentId = uc.id " +
            "WHERE uc.bankClientId = :bankClientId AND a.isActive = true")
    List<Account> findActiveAccountsByBankClientId(@Param("bankClientId") String bankClientId);
}