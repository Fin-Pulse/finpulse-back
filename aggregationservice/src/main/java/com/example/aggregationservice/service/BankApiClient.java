package com.example.aggregationservice.service;

import com.example.aggregationservice.model.Bank;
import com.example.aggregationservice.dto.*;
import com.example.aggregationservice.model.Account;
import com.example.aggregationservice.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class BankApiClient {

    private final RestTemplate restTemplate;

    public List<Transaction> getAccountTransactions(String bankClientId, String accountId,
                                                    LocalDateTime fromDate, LocalDateTime toDate) {
        log.info("🏦 Fetching transactions for account {} (client: {}) from {} to {}",
                accountId, bankClientId, fromDate, toDate);

        // Заглушка для тестирования - будет реализовано позже
        return List.of(
                Transaction.builder()
                        .externalTransactionId("TX_" + System.currentTimeMillis())
                        .amount(new BigDecimal("100.50"))
                        .currency("RUB")
                        .creditDebitIndicator("CREDIT")
                        .status("Booked")
                        .bookingDate(LocalDateTime.now())
                        .transactionInformation("Test transaction from Bank API")
                        .build()
        );
    }

    /**
     * Получает транзакции через Bank API используя consentId и teamToken
     */
    public List<Transaction> fetchAccountTransactions(Bank bank, String teamToken, String consentId,
                                                      String accountId, LocalDateTime fromDate, LocalDateTime toDate) {
        // Форматируем даты в ISO 8601 формат для API (YYYY-MM-DDTHH:mm:ss)
        String fromDateStr = fromDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String toDateStr = toDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        // URL encoding для параметров
        String encodedFromDate = java.net.URLEncoder.encode(fromDateStr, java.nio.charset.StandardCharsets.UTF_8);
        String encodedToDate = java.net.URLEncoder.encode(toDateStr, java.nio.charset.StandardCharsets.UTF_8);
        
        String url = bank.getBaseUrl() + "/accounts/" + accountId + "/transactions" +
                "?fromBookingDate=" + encodedFromDate +
                "&toBookingDate=" + encodedToDate;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(teamToken);
        headers.set("X-Requesting-Bank", "team214");
        headers.set("X-Consent-ID", consentId);
        headers.set("Accept", "application/json");

        try {
            ResponseEntity<BankTransactionResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), BankTransactionResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                BankTransactionResponse responseBody = response.getBody();
                List<com.example.aggregationservice.dto.BankTransaction> bankTransactions =
                        responseBody.getData() != null ? responseBody.getData().getTransactions() : Collections.emptyList();

                List<Transaction> transactions = new ArrayList<>();
                for (com.example.aggregationservice.dto.BankTransaction bankTx : bankTransactions) {
                    Transaction transaction = Transaction.builder()
                            .externalTransactionId(bankTx.getTransactionId())
                            .amount(bankTx.getAmount() != null ? bankTx.getAmount().getAmountAsBigDecimal() : BigDecimal.ZERO)
                            .currency(bankTx.getAmount() != null ? bankTx.getAmount().getCurrency() : "RUB")
                            .creditDebitIndicator(bankTx.getCreditDebitIndicator())
                            .status(bankTx.getStatus())
                            .bookingDate(bankTx.getBookingDateTime() != null ?
                                    LocalDateTime.ofInstant(bankTx.getBookingDateTime(), java.time.ZoneId.systemDefault()) :
                                    LocalDateTime.now())
                            .valueDate(bankTx.getValueDateTime() != null ?
                                    LocalDateTime.ofInstant(bankTx.getValueDateTime(), java.time.ZoneId.systemDefault()) :
                                    null)
                            .transactionInformation(bankTx.getTransactionInformation())
                            .bankTransactionCode(bankTx.getBankTransactionCode() != null ?
                                    bankTx.getBankTransactionCode().getCode() : null)
                            .build();
                    transactions.add(transaction);
                }

                log.info("✅ Fetched {} transactions for account {}", transactions.size(), accountId);
                return transactions;
            }
        } catch (Exception e) {
            log.error("❌ Failed to fetch transactions for account {}: {}", accountId, e.getMessage());
        }

        return Collections.emptyList();
    }

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

            log.info("Bank {} response: status={}, body={}", bank.getCode(), response.getStatusCode(), response.getBody());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();

                String status = (String) responseBody.get("status");
                String requestId = (String) responseBody.get("request_id");

                if ("approved".equals(status)) {
                    // Существующая логика для approved
                    String consentId = (String) responseBody.get("consent_id");

                    ConsentResponse consentResponse = ConsentResponse.builder()
                            .requestId(requestId)
                            .consentId(consentId)
                            .status(status)
                            .permissions(new String[]{"ReadAccountsDetail", "ReadBalances", "ReadTransactionsDetail"})
                            .createdAt(parseDateTime((String) responseBody.get("created_at")))
                            .expiresAt(calculateExpiresAt(responseBody))
                            .autoApproved((Boolean) responseBody.get("auto_approved"))
                            .build();

                    log.info("✅ Consent APPROVED for client {} in bank {}: {}", clientId, bank.getCode(), consentId);
                    return Optional.of(consentResponse);

                } else if ("pending".equals(status)) {
                    // Новая логика для pending-согласий
                    ConsentResponse consentResponse = ConsentResponse.builder()
                            .requestId(requestId)
                            .consentId(null) // consentId будет позже
                            .status(status)
                            .permissions(new String[]{"ReadAccountsDetail", "ReadBalances", "ReadTransactionsDetail"})
                            .createdAt(parseDateTime((String) responseBody.get("created_at")))
                            .expiresAt(null) // установится после одобрения
                            .autoApproved(false)
                            .build();

                    log.info("⏳ Consent PENDING for client {} in bank {}: {}", clientId, bank.getCode(), requestId);
                    return Optional.of(consentResponse);
                }
            }

        } catch (Exception e) {
            log.error("Consent request failed for {} in bank {}: {}", clientId, bank.getCode(), e.getMessage());
        }

        return Optional.empty();
    }

    private Instant parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null) return Instant.now();

        try {
            // Просто обрезаем микросекунды до 3 цифр и добавляем Z
            if (dateTimeStr.contains(".")) {
                String[] parts = dateTimeStr.split("\\.");
                if (parts.length == 2) {
                    String fractional = parts[1];
                    if (fractional.length() > 3) {
                        fractional = fractional.substring(0, 3);
                    }
                    dateTimeStr = parts[0] + "." + fractional + "Z";
                }
            } else if (!dateTimeStr.endsWith("Z")) {
                dateTimeStr += "Z";
            }

            return Instant.parse(dateTimeStr);
        } catch (Exception e) {
            log.warn("Failed to parse date '{}', using current time", dateTimeStr);
            return Instant.now();
        }
    }

    /**
     * Нормализует строку даты-времени, обрезая микросекунды до миллисекунд
     */
    private String normalizeDateTimeString(String dateTimeStr) {
        if (dateTimeStr == null) return null;

        // Ищем точку с дробной частью
        int dotIndex = dateTimeStr.indexOf('.');
        if (dotIndex == -1) {
            return dateTimeStr; // Нет дробной части
        }

        // Ищем конец дробной части (T или конец строки)
        int endIndex = dateTimeStr.indexOf('T', dotIndex);
        if (endIndex == -1) {
            endIndex = dateTimeStr.length();
        }

        // Берем только первые 3 цифры после точки
        String fractionalPart = dateTimeStr.substring(dotIndex + 1, endIndex);
        if (fractionalPart.length() > 3) {
            fractionalPart = fractionalPart.substring(0, 3);
        }

        // Собираем обратно
        return dateTimeStr.substring(0, dotIndex + 1) + fractionalPart +
                (endIndex < dateTimeStr.length() ? dateTimeStr.substring(endIndex) : "");
    }

    private Instant calculateExpiresAt(Map<String, Object> responseBody) {
        try {
            Instant createdAt = parseDateTime((String) responseBody.get("created_at"));
            // Добавляем 90 дней к дате создания
            return createdAt.plus(90, java.time.temporal.ChronoUnit.DAYS);
        } catch (Exception e) {
            log.warn("Failed to calculate expiresAt, using default 90 days");
            return Instant.now().plus(90, java.time.temporal.ChronoUnit.DAYS);
        }
    }

    public List<Account> fetchAccounts(Bank bank, String teamToken, String consentId, String clientId) {
        String url = bank.getBaseUrl() + "/accounts?client_id=" + clientId;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(teamToken);
        headers.set("X-Requesting-Bank", "team214");
        headers.set("X-Consent-ID", consentId);
        headers.set("Accept", "application/json");

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                log.info("Accounts response: {}", responseBody);

                Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                if (data != null) {
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

                        // Парсим openingDate
                        if (accountData.get("openingDate") != null) {
                            try {
                                account.setOpeningDate(LocalDate.parse((String) accountData.get("openingDate")));
                            } catch (Exception e) {
                                log.warn("Failed to parse openingDate: {}", accountData.get("openingDate"));
                            }
                        }

                        // ВНИМАНИЕ: account - это список, а не Map!
                        List<Map<String, Object>> accountDetailsList = (List<Map<String, Object>>) accountData.get("account");
                        if (accountDetailsList != null && !accountDetailsList.isEmpty()) {
                            Map<String, Object> accountDetails = accountDetailsList.get(0); // берем первый элемент
                            account.setAccountNumber((String) accountDetails.get("identification"));
                            account.setAccountName((String) accountDetails.get("name"));
                        }

                        account.setLastSyncAt(Instant.now());
                        accounts.add(account);
                    }

                    log.info("Fetched {} accounts for consent {}", accounts.size(), consentId);
                    return accounts;
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch accounts for consent {}: {}", consentId, e.getMessage(), e);
        }

        return Collections.emptyList();
    }
    public Optional<ConsentResponse> checkConsentStatus(Bank bank, String teamToken, String requestId) {
        // Используем request_id как параметр в URL
        String url = bank.getBaseUrl() + "/account-consents/" + requestId;

        HttpHeaders headers = new HttpHeaders();
        // Судя по документации - без аутентификации
        headers.set("x-fapi-interaction-id", "team214");
        headers.set("Accept", "application/json");

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            log.info("🔍 Checking consent status for requestId: {}", requestId);
            log.info("📡 Response status: {}", response.getStatusCode());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                log.info("📦 Response body: {}", responseBody);

                Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                if (data != null) {
                    String status = (String) data.get("status");
                    String consentId = (String) data.get("consentId");

                    log.info("🎯 Parsed - Status: {}, ConsentId: {}", status, consentId);

                    // "Authorized" = approved в нашей системе
                    if ("Authorized".equals(status) && consentId != null) {
                        Instant expiresAt = Instant.parse((String) data.get("expirationDateTime"));

                        ConsentResponse consentResponse = ConsentResponse.builder()
                                .consentId(consentId)
                                .status("approved") // приводим к нашему формату
                                .requestId(requestId)
                                .expiresAt(expiresAt)
                                .build();

                        log.info("✅ Consent authorized! ConsentId: {}, Expires: {}", consentId, expiresAt);
                        return Optional.of(consentResponse);
                    } else {
                        log.info("⏳ Consent status: {} for requestId: {}", status, requestId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error checking consent status for request {}: {}", requestId, e.getMessage());
        }

        return Optional.empty();
    }

    public Optional<Map<String, Object>> fetchAccountBalance(Bank bank, String teamToken,
                                                             String consentId, String accountId) {
        String url = bank.getBaseUrl() + "/accounts/" + accountId + "/balances";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(teamToken);
        headers.set("X-Requesting-Bank", "team214");
        headers.set("X-Consent-ID", consentId);
        headers.set("Accept", "application/json");

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to fetch balance for account {}: {}", accountId, e.getMessage());
        }

        return Optional.empty();
    }
}