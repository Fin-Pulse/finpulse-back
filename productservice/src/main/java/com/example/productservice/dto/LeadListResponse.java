package com.example.productservice.dto;


import lombok.Data;
import java.util.List;

@Data
public class LeadListResponse {
    private List<LeadDto> leads;

    public LeadListResponse(List<LeadDto> leads) {
        this.leads = leads;
    }
}