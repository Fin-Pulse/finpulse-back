package com.example.productservice.repository;

import com.example.productservice.model.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeadRepository extends JpaRepository<Lead, UUID> {

    // Найти все лиды пользователя
    List<Lead> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // Найти лиды банка с определенным статусом и без delivered_at
    List<Lead> findByBankIdAndStatusAndDeliveredAtIsNullOrderByCreatedAtAsc(
            UUID bankId, String status);

    // Ограничить количество результатов
    @Query("SELECT l FROM Lead l WHERE l.bankId = :bankId AND l.status = :status AND l.deliveredAt IS NULL ORDER BY l.createdAt ASC")
    List<Lead> findBankLeadsWithLimit(@Param("bankId") UUID bankId,
                                      @Param("status") String status,
                                      org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("UPDATE Lead l SET l.deliveredAt = :deliveredAt, l.status = 'RECEIVED', l.updatedAt = :deliveredAt WHERE l.id IN :leadIds")
    int markAsDelivered(@Param("leadIds") List<UUID> leadIds,
                        @Param("deliveredAt") Instant deliveredAt);

    // Основная проверка на дублирование заявок
    boolean existsByUserIdAndProductId(UUID userId, String productId);

    // Найти по ID с проверкой банка
    Optional<Lead> findByIdAndBankId(UUID id, UUID bankId);
}