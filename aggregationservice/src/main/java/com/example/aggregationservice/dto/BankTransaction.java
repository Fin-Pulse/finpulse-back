// dto/BankTransaction.java
package com.example.aggregationservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class BankTransaction {
    @JsonProperty("accountId")
    private String accountId;

    @JsonProperty("transactionId")
    private String transactionId;

    @JsonProperty("amount")
    private Amount amount;

    @JsonProperty("creditDebitIndicator")
    private String creditDebitIndicator;

    @JsonProperty("status")
    private String status;

    @JsonProperty("bookingDateTime")
    private Instant bookingDateTime;

    @JsonProperty("valueDateTime")
    private Instant valueDateTime;

    @JsonProperty("transactionInformation")
    private String transactionInformation;

    @JsonProperty("bankTransactionCode")
    private BankTransactionCode bankTransactionCode;

    @Data
    public static class Amount {
        private String amount;
        private String currency;

        public BigDecimal getAmountAsBigDecimal() {
            return new BigDecimal(amount);
        }
    }

    @Data
    public static class BankTransactionCode {
        private String code;
    }
}