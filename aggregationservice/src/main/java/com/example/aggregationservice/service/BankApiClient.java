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
    private final BankAuthService bankAuthService;

    /**
     * Универсальный метод для вызова банковского API с автоматическим получением токена
     */
    private <T> ResponseEntity<T> callBankApi(Bank bank, String url, HttpMethod method,
                                              HttpEntity<?> requestEntity, Class<T> responseType) {
        try {
            // ✅ Автоматически получаем токен для конкретного банка
            String token = bankAuthService.getBankToken(bank.getCode());

            // Клонируем headers чтобы добавить авторизацию
            HttpHeaders headers = new HttpHeaders();
            if (requestEntity.getHeaders() != null) {
                headers.putAll(requestEntity.getHeaders());
            }
            headers.setBearerAuth(token);
            headers.set("X-Requesting-Bank", "team214");

            HttpEntity<?> entityWithAuth = new HttpEntity<>(requestEntity.getBody(), headers);

            log.debug("Calling bank API: {} {} for bank: {}", method, url, bank.getCode());

            return restTemplate.exchange(url, method, entityWithAuth, responseType);

        } catch (Exception e) {
            log.error("Bank API call failed for bank {}: {}", bank.getCode(), e.getMessage());
            throw new RuntimeException("Bank API call failed for " + bank.getCode(), e);
        }
    }

    /**
     * Запрос согласия - БЕЗ параметра teamToken
     */
    public Optional<ConsentResponse> requestConsent(Bank bank, String clientId) {
        String url = bank.getBaseUrl() + "/account-consents/request";

        ConsentRequest body = ConsentRequest.builder()
                .client_id(clientId)
                .permissions(new String[]{"ReadAccountsDetail", "ReadBalances", "ReadTransactionsDetail"})
                .reason("Агрегация счетов для HackAPI")
                .requesting_bank("team214")
                .requesting_bank_name("Team 214 App")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<ConsentRequest> request = new HttpEntity<>(body, headers);

        try {
            // ✅ Используем callBankApi который САМ получит токен
            ResponseEntity<Map> response = callBankApi(bank, url, HttpMethod.POST, request, Map.class);

            log.info("Bank {} response: status={}, body={}", bank.getCode(), response.getStatusCode(), response.getBody());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();

                String status = (String) responseBody.get("status");
                String requestId = (String) responseBody.get("request_id");

                if ("approved".equals(status)) {
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
                    ConsentResponse consentResponse = ConsentResponse.builder()
                            .requestId(requestId)
                            .consentId(null)
                            .status(status)
                            .permissions(new String[]{"ReadAccountsDetail", "ReadBalances", "ReadTransactionsDetail"})
                            .createdAt(parseDateTime((String) responseBody.get("created_at")))
                            .expiresAt(null)
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

    /**
     * Проверка статуса согласия - БЕЗ параметра teamToken
     */
    public Optional<ConsentResponse> checkConsentStatus(Bank bank, String requestId) {
        String url = bank.getBaseUrl() + "/account-consents/" + requestId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-fapi-interaction-id", "team214");
        headers.set("Accept", "application/json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            // ✅ Используем callBankApi который САМ получит токен
            ResponseEntity<Map> response = callBankApi(bank, url, HttpMethod.GET, request, Map.class);

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

                    if ("Authorized".equals(status) && consentId != null) {
                        Instant expiresAt = Instant.parse((String) data.get("expirationDateTime"));

                        ConsentResponse consentResponse = ConsentResponse.builder()
                                .consentId(consentId)
                                .status("approved")
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

    /**
     * Получение счетов - БЕЗ параметра teamToken
     */
    public List<Account> fetchAccounts(Bank bank, String consentId, String clientId) {
        String url = bank.getBaseUrl() + "/accounts?client_id=" + clientId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Consent-ID", consentId);
        headers.set("Accept", "application/json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            // ✅ Используем callBankApi который САМ получит токен
            ResponseEntity<Map> response = callBankApi(bank, url, HttpMethod.GET, request, Map.class);

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

                        if (accountData.get("openingDate") != null) {
                            try {
                                account.setOpeningDate(LocalDate.parse((String) accountData.get("openingDate")));
                            } catch (Exception e) {
                                log.warn("Failed to parse openingDate: {}", accountData.get("openingDate"));
                            }
                        }

                        List<Map<String, Object>> accountDetailsList = (List<Map<String, Object>>) accountData.get("account");
                        if (accountDetailsList != null && !accountDetailsList.isEmpty()) {
                            Map<String, Object> accountDetails = accountDetailsList.get(0);
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

    /**
     * Получение транзакций - БЕЗ параметра teamToken
     */
    public List<Transaction> fetchAccountTransactions(Bank bank, String consentId,
                                                      String accountId, LocalDateTime fromDate, LocalDateTime toDate) {
        String fromDateStr = fromDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String toDateStr = toDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        String encodedFromDate = java.net.URLEncoder.encode(fromDateStr, java.nio.charset.StandardCharsets.UTF_8);
        String encodedToDate = java.net.URLEncoder.encode(toDateStr, java.nio.charset.StandardCharsets.UTF_8);

        String url = bank.getBaseUrl() + "/accounts/" + accountId + "/transactions" +
                "?fromBookingDate=" + encodedFromDate +
                "&toBookingDate=" + encodedToDate;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Consent-ID", consentId);
        headers.set("Accept", "application/json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            // ✅ Используем callBankApi который САМ получит токен
            ResponseEntity<BankTransactionResponse> response = callBankApi(bank, url, HttpMethod.GET, request, BankTransactionResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                BankTransactionResponse responseBody = response.getBody();
                List<BankTransactionResponse.Transaction> bankTransactions =
                        responseBody.getData() != null && responseBody.getData().getTransaction() != null ?
                                responseBody.getData().getTransaction() : Collections.emptyList();

                List<Transaction> transactions = new ArrayList<>();
                for (BankTransactionResponse.Transaction bankTx : bankTransactions) {
                    BigDecimal amount = parseAmount(bankTx.getAmount());
                    BigDecimal absoluteAmount = amount.abs();
                    String creditDebitIndicator = bankTx.getCreditDebitIndicator();
                    
                    // Вычисляем is_expense: true для Debit, false для Credit
                    Boolean isExpense = "Debit".equalsIgnoreCase(creditDebitIndicator);

                    // Определяем категорию
                    String category = determineCategory(bankTx);

                    Transaction transaction = Transaction.builder()
                            .externalTransactionId(bankTx.getTransactionId())
                            .amount(amount)
                            .absoluteAmount(absoluteAmount)
                            .isExpense(isExpense)
                            .creditDebitIndicator(creditDebitIndicator)
                            .bookingDate(parseBookingDate(bankTx.getBookingDateTime()))
                            .transactionInformation(bankTx.getTransactionInformation())
                            .category(category)
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

    /**
     * Получение баланса - БЕЗ параметра teamToken
     */
    public Optional<Map<String, Object>> fetchAccountBalance(Bank bank, String consentId, String accountId) {
        String url = bank.getBaseUrl() + "/accounts/" + accountId + "/balances";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Consent-ID", consentId);
        headers.set("Accept", "application/json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            // ✅ Используем callBankApi который САМ получит токен
            ResponseEntity<Map> response = callBankApi(bank, url, HttpMethod.GET, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to fetch balance for account {}: {}", accountId, e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Старый метод для обратной совместимости (можно удалить после обновления всех сервисов)
     */
    @Deprecated
    public List<Transaction> getAccountTransactions(String bankClientId, String accountId,
                                                    LocalDateTime fromDate, LocalDateTime toDate) {
        log.warn("Using deprecated getAccountTransactions method - should be replaced with fetchAccountTransactions");
        BigDecimal amount = new BigDecimal("100.50");
        return List.of(
                Transaction.builder()
                        .externalTransactionId("TX_" + System.currentTimeMillis())
                        .amount(amount)
                        .absoluteAmount(amount.abs())
                        .isExpense(false)
                        .creditDebitIndicator("Credit")
                        .bookingDate(LocalDateTime.now())
                        .transactionInformation("Test transaction from Bank API")
                        .category("other")
                        .build()
        );
    }

    // Вспомогательные методы (оставляем без изменений)
    private Instant parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null) return Instant.now();

        try {
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

    private String normalizeDateTimeString(String dateTimeStr) {
        if (dateTimeStr == null) return null;

        int dotIndex = dateTimeStr.indexOf('.');
        if (dotIndex == -1) {
            return dateTimeStr;
        }

        int endIndex = dateTimeStr.indexOf('T', dotIndex);
        if (endIndex == -1) {
            endIndex = dateTimeStr.length();
        }

        String fractionalPart = dateTimeStr.substring(dotIndex + 1, endIndex);
        if (fractionalPart.length() > 3) {
            fractionalPart = fractionalPart.substring(0, 3);
        }

        return dateTimeStr.substring(0, dotIndex + 1) + fractionalPart +
                (endIndex < dateTimeStr.length() ? dateTimeStr.substring(endIndex) : "");
    }

    private Instant calculateExpiresAt(Map<String, Object> responseBody) {
        try {
            Instant createdAt = parseDateTime((String) responseBody.get("created_at"));
            return createdAt.plus(90, java.time.temporal.ChronoUnit.DAYS);
        } catch (Exception e) {
            log.warn("Failed to calculate expiresAt, using default 90 days");
            return Instant.now().plus(90, java.time.temporal.ChronoUnit.DAYS);
        }
    }

    /**
     * Парсит сумму из Amount объекта
     */
    private BigDecimal parseAmount(BankTransactionResponse.Transaction.Amount amount) {
        if (amount == null || amount.getAmount() == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(amount.getAmount());
        } catch (Exception e) {
            log.warn("Failed to parse amount: {}", amount.getAmount());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Парсит дату бронирования
     */
    private LocalDateTime parseBookingDate(String bookingDateTime) {
        if (bookingDateTime == null) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(bookingDateTime.replace("Z", "").replace("+00:00", ""));
        } catch (Exception e) {
            try {
                Instant instant = Instant.parse(bookingDateTime);
                return LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
            } catch (Exception e2) {
                log.warn("Failed to parse booking date: {}", bookingDateTime);
                return LocalDateTime.now();
            }
        }
    }


    /**
     * Определяет категорию транзакции
     * 1. Если есть merchant.category - используем её
     * 2. Если есть merchant.mccCode - определяем по MCC
     * 3. Если есть transactionInformation - пытаемся определить по описанию
     * 4. Иначе - "other"
     */
    private String determineCategory(BankTransactionResponse.Transaction bankTx) {
        // 1. Проверяем merchant.category
        if (bankTx.getMerchant() != null && bankTx.getMerchant().getCategory() != null) {
            return bankTx.getMerchant().getCategory();
        }

        // 2. Проверяем MCC код
        if (bankTx.getMerchant() != null && bankTx.getMerchant().getMccCode() != null) {
            String categoryByMcc = getCategoryByMcc(bankTx.getMerchant().getMccCode());
            if (categoryByMcc != null) {
                return categoryByMcc;
            }
        }

        // 3. Пытаемся определить по описанию транзакции
        if (bankTx.getTransactionInformation() != null) {
            String categoryByDescription = getCategoryByDescription(bankTx.getTransactionInformation());
            if (categoryByDescription != null) {
                return categoryByDescription;
            }
        }

        // 4. Для кредитных транзакций (зарплата и т.д.)
        if ("Credit".equals(bankTx.getCreditDebitIndicator())) {
            return "income";
        }

        // 5. По умолчанию
        return "other";
    }

    /**
     * Определяет категорию по MCC коду
     */
    private String getCategoryByMcc(String mccCode) {
        if (mccCode == null) return null;

        // Основные MCC коды
        switch (mccCode) {
            case "5411": // Супермаркеты
                return "grocery";
            case "5812": // Рестораны
                return "restaurant";
            case "5814": // Кафе
                return "cafe";
            case "5651": // Одежда
                return "clothing";
            case "5541": // АЗС
                return "gas";
            case "4814": // Телекоммуникации
                return "telecom";
            case "4900": // Коммунальные услуги
                return "utilities";
            case "5999": // Разное
                return "other";
            default:
                return null;
        }
    }

    /**
     * Определяет категорию по описанию транзакции
     */
    private String getCategoryByDescription(String description) {
        if (description == null) return null;

        String descLower = description.toLowerCase();

        // Транспорт
        if (descLower.contains("транспорт") || descLower.contains("метро") ||
            descLower.contains("автобус") || descLower.contains("такси") ||
            descLower.contains("uber") || descLower.contains("yandex")) {
            return "transport";
        }

        // Зарплата
        if (descLower.contains("зарплата") || descLower.contains("salary") ||
            descLower.contains("заработная плата")) {
            return "income";
        }

        // Продукты
        if (descLower.contains("пятёрочка") || descLower.contains("лента") ||
            descLower.contains("ашан") || descLower.contains("перекрёсток") ||
            descLower.contains("дикси") || descLower.contains("вкусвилл")) {
            return "grocery";
        }

        // Кафе
        if (descLower.contains("starbucks") || descLower.contains("кофе") ||
            descLower.contains("шоколадница") || descLower.contains("coffee")) {
            return "cafe";
        }

        // Рестораны
        if (descLower.contains("макдоналдс") || descLower.contains("mcdonald") ||
            descLower.contains("сбарро") || descLower.contains("ресторан")) {
            return "restaurant";
        }

        return null;
    }
}