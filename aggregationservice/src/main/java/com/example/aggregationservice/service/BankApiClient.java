
package com.example.aggregationservice.service;

import com.example.aggregationservice.model.Bank;
import com.example.aggregationservice.dto.*;
import com.example.aggregationservice.model.Account;
import com.example.aggregationservice.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class BankApiClient {

    private final RestTemplate restTemplate;
    private final BankAuthService bankAuthService;

    // Основной метод вызова API с улучшенной обработкой ошибок
    private <T> ResponseEntity<T> callBankApi(Bank bank, String url, HttpMethod method,
                                              HttpEntity<?> requestEntity, Class<T> responseType) {
        try {
            String token = bankAuthService.getBankToken(bank.getCode());

            HttpHeaders headers = new HttpHeaders();
            if (requestEntity.getHeaders() != null) {
                headers.putAll(requestEntity.getHeaders());
            }
            headers.setBearerAuth(token);
            headers.set("X-Requesting-Bank", "team214");

            HttpEntity<?> entityWithAuth = new HttpEntity<>(requestEntity.getBody(), headers);

            log.debug("Calling bank API: {} {} for bank: {}", method, url, bank.getCode());

            return restTemplate.exchange(url, method, entityWithAuth, responseType);

        } catch (ResourceAccessException e) {
            // Банк недоступен (таймаут, сетевые проблемы)
            log.error("Bank API unavailable for bank {}: {}", bank.getCode(), e.getMessage());
            throw new BankApiException("Bank API unavailable for " + bank.getCode(), e);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            // HTTP ошибки (4xx, 5xx)
            log.error("Bank API error for bank {}: {} - {}", bank.getCode(), e.getStatusCode(), e.getMessage());
            throw new BankApiException("Bank API error for " + bank.getCode() + ": " + e.getStatusCode(), e);
        } catch (Exception e) {
            // Все остальные ошибки
            log.error("Bank API call failed for bank {}: {}", bank.getCode(), e.getMessage());
            throw new BankApiException("Bank API call failed for " + bank.getCode(), e);
        }
    }

    // Безопасная версия для методов, которые не должны падать при ошибках банка
    private <T> Optional<ResponseEntity<T>> callBankApiSafely(Bank bank, String url, HttpMethod method,
                                                              HttpEntity<?> requestEntity, Class<T> responseType) {
        try {
            return Optional.of(callBankApi(bank, url, method, requestEntity, responseType));
        } catch (BankApiException e) {
            log.warn("Bank API call failed safely for bank {}: {}", bank.getCode(), e.getMessage());
            return Optional.empty();
        }
    }

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

        // Используем безопасный вызов
        Optional<ResponseEntity<Map>> responseOpt = callBankApiSafely(bank, url, HttpMethod.POST, request, Map.class);

        if (responseOpt.isPresent()) {
            ResponseEntity<Map> response = responseOpt.get();
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

                    return Optional.of(consentResponse);
                }
            }
        }

        return Optional.empty();
    }

    public Optional<ConsentResponse> checkConsentStatus(Bank bank, String requestId) {
        String url = bank.getBaseUrl() + "/account-consents/" + requestId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-fapi-interaction-id", "team214");
        headers.set("Accept", "application/json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        // Используем безопасный вызов
        Optional<ResponseEntity<Map>> responseOpt = callBankApiSafely(bank, url, HttpMethod.GET, request, Map.class);

        if (responseOpt.isPresent()) {
            ResponseEntity<Map> response = responseOpt.get();
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                if (data != null) {
                    String status = (String) data.get("status");
                    String consentId = (String) data.get("consentId");

                    if ("Authorized".equals(status) && consentId != null) {
                        Instant expiresAt = Instant.parse((String) data.get("expirationDateTime"));

                        ConsentResponse consentResponse = ConsentResponse.builder()
                                .consentId(consentId)
                                .status("approved")
                                .requestId(requestId)
                                .expiresAt(expiresAt)
                                .build();

                        return Optional.of(consentResponse);
                    }
                }
            }
        }

        return Optional.empty();
    }

    public List<Account> fetchAccounts(Bank bank, String consentId, String clientId) {
        String url = bank.getBaseUrl() + "/accounts?client_id=" + clientId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Consent-ID", consentId);
        headers.set("Accept", "application/json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        // Используем безопасный вызов
        Optional<ResponseEntity<Map>> responseOpt = callBankApiSafely(bank, url, HttpMethod.GET, request, Map.class);

        if (responseOpt.isPresent()) {
            ResponseEntity<Map> response = responseOpt.get();
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
                                log.warn("Failed to parse openingDate Error: {}", e.getMessage());
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

                    return accounts;
                }
            }
        }

        return Collections.emptyList();
    }

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

        // Используем безопасный вызов
        Optional<ResponseEntity<BankTransactionResponse>> responseOpt = callBankApiSafely(bank, url, HttpMethod.GET, request, BankTransactionResponse.class);

        if (responseOpt.isPresent()) {
            ResponseEntity<BankTransactionResponse> response = responseOpt.get();
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

                    Boolean isExpense = "Debit".equalsIgnoreCase(creditDebitIndicator);

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

                return transactions;
            }
        }

        return Collections.emptyList();
    }

    public Optional<Map<String, Object>> fetchAccountBalance(Bank bank, String consentId, String accountId) {
        String url = bank.getBaseUrl() + "/accounts/" + accountId + "/balances";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Consent-ID", consentId);
        headers.set("Accept", "application/json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        // Используем безопасный вызов
        Optional<ResponseEntity<Map>> responseOpt = callBankApiSafely(bank, url, HttpMethod.GET, request, Map.class);

        if (responseOpt.isPresent()) {
            ResponseEntity<Map> response = responseOpt.get();
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        }

        return Optional.empty();
    }

    public Optional<List<Map<String, Object>>> fetchProducts(Bank bank) {
        String url = bank.getBaseUrl() + "/products";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        HttpEntity<String> request = new HttpEntity<>(headers);

        // Используем безопасный вызов
        Optional<ResponseEntity<Map>> responseOpt = callBankApiSafely(bank, url, HttpMethod.GET, request, Map.class);

        if (responseOpt.isPresent()) {
            ResponseEntity<Map> response = responseOpt.get();
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Object dataObj = body.get("data");
                if (dataObj instanceof Map) {
                    Map<String, Object> data = (Map<String, Object>) dataObj;
                    Object productObj = data.get("product");
                    if (productObj instanceof List) {
                        List<Map<String, Object>> products = (List<Map<String, Object>>) productObj;
                        return Optional.of(products);
                    }
                }
            }
        }

        return Optional.empty();
    }

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


    private String determineCategory(BankTransactionResponse.Transaction bankTx) {
        if (bankTx.getMerchant() != null && bankTx.getMerchant().getCategory() != null) {
            return bankTx.getMerchant().getCategory();
        }

        if (bankTx.getMerchant() != null && bankTx.getMerchant().getMccCode() != null) {
            String categoryByMcc = getCategoryByMcc(bankTx.getMerchant().getMccCode());
            if (categoryByMcc != null) {
                return categoryByMcc;
            }
        }

        if (bankTx.getTransactionInformation() != null) {
            String categoryByDescription = getCategoryByDescription(bankTx.getTransactionInformation());
            if (categoryByDescription != null) {
                return categoryByDescription;
            }
        }

        if ("Credit".equals(bankTx.getCreditDebitIndicator())) {
            return "income";
        }

        return "other";
    }

    private String getCategoryByMcc(String mccCode) {
        if (mccCode == null) return null;

        switch (mccCode) {
            case "5411":
                return "grocery";
            case "5812":
                return "restaurant";
            case "5814":
                return "cafe";
            case "5651":
                return "clothing";
            case "5541":
                return "gas";
            case "4814":
                return "telecom";
            case "4900":
                return "utilities";
            case "5999":
                return "other";
            default:
                return null;
        }
    }

    private String getCategoryByDescription(String description) {
        if (description == null) return null;

        String descLower = description.toLowerCase();

        if (descLower.contains("транспорт") || descLower.contains("метро") ||
            descLower.contains("автобус") || descLower.contains("такси") ||
            descLower.contains("uber") || descLower.contains("yandex")) {
            return "transport";
        }

        if (descLower.contains("зарплата") || descLower.contains("salary") ||
            descLower.contains("заработная плата")) {
            return "income";
        }

        if (descLower.contains("пятёрочка") || descLower.contains("лента") ||
            descLower.contains("ашан") || descLower.contains("перекрёсток") ||
            descLower.contains("дикси") || descLower.contains("вкусвилл")) {
            return "grocery";
        }

        if (descLower.contains("starbucks") || descLower.contains("кофе") ||
            descLower.contains("шоколадница") || descLower.contains("coffee")) {
            return "cafe";
        }

        if (descLower.contains("макдоналдс") || descLower.contains("mcdonald") ||
            descLower.contains("сбарро") || descLower.contains("ресторан")) {
            return "restaurant";
        }

        return null;
    }

    /**
     * Fetch products from bank API.
     * Expects bank API to return JSON like:
     * {
     *   "data": {
     *     "product": [ { "productId": "...", "productType": "...", ... }, ... ]
     *   }
     * }
     *
     * Returns Optional<List<Map<String,Object>>> where each map is the raw product object.

    public Optional<List<Map<String, Object>>> fetchProducts(Bank bank) {
        String url = bank.getBaseUrl() + "/products";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = callBankApi(bank, url, HttpMethod.GET, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Object dataObj = body.get("data");
                if (dataObj instanceof Map) {
                    Map<String, Object> data = (Map<String, Object>) dataObj;
                    Object productObj = data.get("product");
                    if (productObj instanceof List) {
                        List<Map<String, Object>> products = (List<Map<String, Object>>) productObj;
                        return Optional.of(products);
                    }
                }
            } else {
                log.warn("Non-2xx response when fetching products from {}: {}", bank.getCode(), response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to fetch products from bank {}: {}", bank.getCode(), e.getMessage(), e);
        }

        return Optional.empty();
    }*/

    private static class BankApiException extends RuntimeException {
        public BankApiException(String message) {
            super(message);
        }

        public BankApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}