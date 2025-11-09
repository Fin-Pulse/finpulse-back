// dto/BankTransactionResponse.java
package com.example.aggregationservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class BankTransactionResponse {
    private Data data;
    private Links links;
    private Meta meta;

    @lombok.Data
    public static class Data {
        private List<Transaction> transaction;
    }

    @lombok.Data
    public static class Transaction {
        private String accountId;
        private String transactionId;
        private Amount amount;
        private String creditDebitIndicator;
        private String status;
        private String bookingDateTime;
        private String valueDateTime;
        private String transactionInformation;
        private BankTransactionCode bankTransactionCode;
        private Merchant merchant;
        private TransactionLocation transactionLocation;
        private Card card;
        private Object counterparty;

        @lombok.Data
        public static class Amount {
            private String amount;
            private String currency;
        }

        @lombok.Data
        public static class BankTransactionCode {
            private String code;
        }

        @lombok.Data
        public static class Merchant {
            private String merchantId;
            private String name;
            private String mccCode;
            private String category;
            private String city;
            private String country;
            private String address;
        }

        @lombok.Data
        public static class TransactionLocation {
            private String city;
            private String country;
        }

        @lombok.Data
        public static class Card {
            private String cardId;
            private String cardNumber;
            private String cardType;
            private String cardName;
        }
    }

    @lombok.Data
    public static class Links {
        private String self;
        private String next;
    }

    @lombok.Data
    public static class Meta {
        private int totalPages;
        private int totalRecords;
        private int currentPage;
        private int pageSize;
    }
}