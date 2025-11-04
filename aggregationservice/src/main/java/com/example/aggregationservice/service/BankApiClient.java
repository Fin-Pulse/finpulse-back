package com.example.aggregationservice.service;

import com.example.aggregationservice.model.Bank;
import com.example.aggregationservice.dto.*;
import com.example.aggregationservice.model.Account;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class BankApiClient {

    private final RestTemplate restTemplate;

    public Optional<ConsentResponse> requestConsent(Bank bank, String teamToken, String clientId) {
        String url = bank.getBaseUrl() + "/account-consents/request";

        ConsentRequest body = ConsentRequest.builder()
                .client_id(clientId)
                .permissions(new String[]{"ReadAccountsDetail", "ReadBalances", "ReadTransactionsDetail"})
                .reason("Агрегация счетов для HackAPI")
                .requesting_bank("team214")
                .requesting_bank_name("Team 214 App")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(teamToken);
        headers.set("X-Requesting-Bank", "team214");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<ConsentRequest> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");

                // Парсим дату истечения
                Instant expiresAt = Instant.parse((String) data.get("expiresAt"));

                ConsentResponse consentResponse = ConsentResponse.builder()
                        .consentId((String) data.get("consentId"))
                        .permissions(((List<String>) data.get("permissions")).toArray(new String[0]))
                        .expiresAt(expiresAt)
                        .build();

                log.info("Consent created for client {} in bank {}: {}", clientId, bank.getCode(), consentResponse.getConsentId());
                return Optional.of(consentResponse);
            }
        } catch (Exception e) {
            log.warn("Consent request failed for {} in bank {}: {}", clientId, bank.getCode(), e.getMessage());
        }

        return Optional.empty();
    }

    public List<Account> fetchAccounts(Bank bank, String teamToken, String consentId) {
        String url = bank.getBaseUrl() + "/accounts";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(teamToken);
        headers.set("X-Requesting-Bank", "team214");
        headers.set("X-Consent-ID", consentId);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                List<Map<String, Object>> accountsData = (List<Map<String, Object>>) data.get("account");

                List<Account> accounts = new ArrayList<>();

                for (Map<String, Object> accountData : accountsData) {
                    Account account = new Account();
                    account.setExternalAccountId((String) accountData.get("accountId"));
                    account.setAccountType((String) accountData.get("accountType"));
                    account.setAccountSubType((String) accountData.get("accountSubType"));
                    account.setCurrency((String) accountData.get("currency"));
                    account.setNickname((String) accountData.get("nickname"));
                    account.setStatus((String) accountData.get("status"));

                    // Парсим openingDate если есть
                    if (accountData.get("openingDate") != null) {
                        account.setOpeningDate(LocalDate.parse((String) accountData.get("openingDate")));
                    }

                    // Получаем номер счета из вложенной структуры
                    Map<String, Object> accountDetails = (Map<String, Object>) accountData.get("account");
                    if (accountDetails != null) {
                        account.setAccountNumber((String) accountDetails.get("identification"));
                    }

                    account.setLastSyncAt(Instant.now());
                    accounts.add(account);
                }

                log.info("Fetched {} accounts for consent {}", accounts.size(), consentId);
                return accounts;
            }
        } catch (Exception e) {
            log.error("Failed to fetch accounts for consent {}: {}", consentId, e.getMessage());
        }

        return Collections.emptyList();
    }
}