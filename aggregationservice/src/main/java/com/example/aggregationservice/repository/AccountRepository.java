package com.example.aggregationservice.repository;

import com.example.aggregationservice.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserConsentId(UUID userConsentId);

    @Query("SELECT COUNT(a) FROM Account a WHERE a.userConsentId IN " +
            "(SELECT uc.id FROM UserConsent uc WHERE uc.userId = :userId)")
    int countAccountsByUserId(UUID userId);
}
