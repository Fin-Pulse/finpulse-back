package com.example.notificationservice.repository;

import com.example.notificationservice.model.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

    @Query("SELECT r FROM Recommendation r WHERE r.userId = :userId ORDER BY r.createdAt DESC")
    List<Recommendation> findAllByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    default Optional<Recommendation> findLatestByUserId(UUID userId) {
        return findAllByUserIdOrderByCreatedAtDesc(userId).stream().findFirst();
    }
}

