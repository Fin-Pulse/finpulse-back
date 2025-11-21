package com.example.notificationservice.service;

import com.example.notificationservice.dto.RecommendationsDto;
import com.example.notificationservice.model.Recommendation;
import com.example.notificationservice.repository.RecommendationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationRepository repository;
    private final ObjectMapper objectMapper;
    public RecommendationsDto getLatestRecommendations(UUID userId) {
        return repository.findLatestByUserId(userId)
                .map(r -> {
                    try {
                        List<Map<String, Object>> list = objectMapper.readValue(
                                r.getRecommendations(),
                                new TypeReference<List<Map<String, Object>>>() {}
                        );
                        return new RecommendationsDto(list);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .orElse(null);
    }
}

