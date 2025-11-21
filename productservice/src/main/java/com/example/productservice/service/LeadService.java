package com.example.productservice.service;

import com.example.productservice.dto.*;
import com.example.productservice.model.Lead;
import com.example.productservice.repository.LeadRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final UserProfileService userProfileService;
    private final ObjectMapper objectMapper;

    @Transactional
    public UUID createLead(CreateLeadRequest request) {
        if (leadRepository.existsByUserIdAndProductId(request.getUserId(), request.getProductId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Заявка на этот продукт уже существует");
        }

        UserProfile userProfile = userProfileService.getUserProfile(request.getUserId());
        if (userProfile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Пользователь не найден");
        }

        Lead lead = new Lead();
        lead.setUserId(request.getUserId());
        lead.setProductId(request.getProductId());
        lead.setBankId(request.getBankId());

        lead.setPayload(request.getPayload());

        lead.setStatus("NEW");
        lead.setExpiresAt(Instant.now().plusSeconds(90 * 24 * 60 * 60));

        Lead saved = leadRepository.save(lead);
        log.info("Создана новая заявка: ID={}, пользователь={}, продукт={}",
                saved.getId(), request.getUserId(), request.getProductId());

        return saved.getId();
    }

    public List<LeadDto> getUserLeads(UUID userId) {
        List<Lead> leads = leadRepository.findByUserIdOrderByCreatedAtDesc(userId);

        UserProfile userProfile = userProfileService.getUserProfile(userId);

        return leads.stream()
                .map(lead -> convertToDto(lead, userProfile))
                .collect(Collectors.toList());
    }

    @Transactional
    public List<LeadDto> pullBankLeads(UUID bankId, String status, Integer limit) {
        int actualLimit = (limit != null && limit > 0) ? limit : 50;
        Pageable pageable = PageRequest.of(0, actualLimit);

        List<Lead> leads = leadRepository.findBankLeadsWithLimit(bankId, status, pageable);

        if (!leads.isEmpty()) {
            Map<UUID, UserProfile> userProfiles = new HashMap<>();
            for (Lead lead : leads) {
                UUID userId = lead.getUserId();
                if (!userProfiles.containsKey(userId)) {
                    UserProfile profile = userProfileService.getUserProfile(userId);
                    if (profile != null) {
                        userProfiles.put(userId, profile);
                    }
                }
            }

            List<UUID> leadIds = leads.stream()
                    .map(Lead::getId)
                    .collect(Collectors.toList());

            int updatedCount = leadRepository.markAsDelivered(leadIds, Instant.now());
            log.info("Обновлено {} заявок как доставленные", updatedCount);

            log.info("Банк {} получил {} заявок со статусом {}", bankId, leads.size(), status);

            return leads.stream()
                    .map(lead -> convertToDto(lead, userProfiles.get(lead.getUserId())))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    @Transactional
    public void updateLeadStatus(UUID leadId, UpdateLeadStatusRequest request) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Заявка не найдена"));

        if (!isValidStatus(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Недопустимый статус: " + request.getStatus());
        }

        lead.setStatus(request.getStatus());
        lead.setUpdatedAt(Instant.now());

        leadRepository.save(lead);
        log.info("Обновлен статус заявки {} на {}", leadId, request.getStatus());
    }


    private LeadDto convertToDto(Lead entity, UserProfile userProfile) {
        LeadDto dto = new LeadDto();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setProductId(entity.getProductId());
        dto.setBankId(entity.getBankId());

        if (userProfile != null) {
            dto.setUserFullName(userProfile.getFullName());
            dto.setUserPhone(userProfile.getPhone());
            dto.setUserEmail(userProfile.getEmail());
            dto.setUserBankClientId(userProfile.getBankClientId());
            dto.setUserVerified(userProfile.isVerified());
            dto.setUserVerificationStatus(userProfile.getVerificationStatus());
        }

        dto.setPayload(entity.getPayload());

        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setExpiresAt(entity.getExpiresAt());
        dto.setDeliveredAt(entity.getDeliveredAt());
        return dto;
    }

    private boolean isValidStatus(String status) {
        return List.of("NEW", "RECEIVED", "APPROVED", "REJECTED", "EXPIRED").contains(status);
    }
}