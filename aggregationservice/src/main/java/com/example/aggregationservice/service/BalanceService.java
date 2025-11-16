package com.example.aggregationservice.service;

import com.example.aggregationservice.model.Account;
import com.example.aggregationservice.model.Bank;
import com.example.aggregationservice.model.UserConsent;
import com.example.aggregationservice.model.enums.ConsentStatus;
import com.example.aggregationservice.repository.AccountRepository;
import com.example.aggregationservice.repository.BankRepository;
import com.example.aggregationservice.repository.UserConsentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final BankApiClient bankApiClient;
    private final BankAuthService bankAuthService;
    private final AccountRepository accountRepository;
    private final BankRepository bankRepository;
    private final UserConsentRepository userConsentRepository;
    private final ConsentEncryptionService encryptionService;

    @Transactional
    public void updateBalancesForUser(String bankClientId) {

        List<UserConsent> activeConsents = userConsentRepository.findByBankClientIdAndStatus(
                bankClientId, ConsentStatus.ACTIVE);

        for (UserConsent consent : activeConsents) {
            try {
                Bank bank = bankRepository.findById(consent.getBankId())
                        .orElseThrow(() -> new RuntimeException("Bank not found"));

                List<Account> accounts = accountRepository.findByUserConsentId(consent.getId());

                for (Account account : accounts) {
                    updateAccountBalance(account, bank, consent);
                }


            } catch (Exception e) {
                log.error("Error updating balances for consent {}: {}", consent.getId(), e.getMessage());
            }
        }
    }

    private void updateAccountBalance(Account account, Bank bank, UserConsent consent) {
        try {
            String decryptedConsentId = encryptionService.decrypt(consent.getConsentId());

            var balanceResponse = bankApiClient.fetchAccountBalance(
                    bank, decryptedConsentId, account.getExternalAccountId());

            if (balanceResponse.isPresent()) {
                var balanceData = balanceResponse.get();

                BigDecimal newBalance = parseBalanceFromResponse(balanceData);
                if (newBalance != null) {
                    account.setBalance(newBalance);
                    account.setAvailableBalance(newBalance);
                    account.setLastSyncAt(Instant.now());
                    accountRepository.save(account);

                    log.debug("Updated balance for account {}: {}", account.getAccountNumber(), newBalance);
                }
            }
        } catch (Exception e) {
            log.error("Error updating balance for account {}: {}", account.getId(), e.getMessage());
        }
    }

    private BigDecimal parseBalanceFromResponse(Map<String, Object> balanceData) {
        try {
            Map<String, Object> data = (Map<String, Object>) balanceData.get("data");
            if (data != null) {
                List<Map<String, Object>> balances = (List<Map<String, Object>>) data.get("balance");

                if (balances != null && !balances.isEmpty()) {
                    Map<String, Object> firstBalance = balances.get(0);
                    Map<String, Object> amount = (Map<String, Object>) firstBalance.get("amount");

                    if (amount != null) {
                        String amountStr = (String) amount.get("amount");
                        return new BigDecimal(amountStr);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing balance from response: {}", e.getMessage());
        }
        return null;
    }

    @Transactional
    public void updateAllBalances() {

        List<String> bankClientIds = userConsentRepository.findDistinctBankClientIdsWithActiveConsents();

        for (String bankClientId : bankClientIds) {
            try {
                updateBalancesForUser(bankClientId);
            } catch (Exception e) {
                log.error("Failed to update balances for user {}: {}", bankClientId, e.getMessage());
            }
        }
    }
}