// dto/BankTransactionResponse.java
package com.example.aggregationservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class BankTransactionResponse {
    private Datas data;
    private Links links;
    private Meta meta;

    @Data
    public static class Datas {
        @JsonProperty("transaction")
        private List<BankTransaction> transactions;
    }

    @Data
    public static class Links {
        private String self;
        private String next;
    }

    @Data
    public static class Meta {
        private int totalPages;
        private int totalRecords;
        private int currentPage;
        private int pageSize;
    }
}