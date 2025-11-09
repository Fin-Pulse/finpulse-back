package com.example.notificationservice.repository;

import com.example.notificationservice.model.UserForecast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserForecastRepository extends JpaRepository<UserForecast, UUID> {

    @Query("SELECT uf FROM UserForecast uf WHERE uf.userId = :userId ORDER BY uf.forecastWeekStart DESC, uf.updatedAt DESC LIMIT 1")
    Optional<UserForecast> findLatestByUserId(@Param("userId") UUID userId);

    @Query(value = """
        SELECT * FROM user_forecasts uf 
        WHERE uf.user_id = :userId 
        ORDER BY uf.forecast_week_start DESC, uf.updated_at DESC 
        LIMIT 1
        """, nativeQuery = true)
    Optional<UserForecast> findLatestByUserIdNative(@Param("userId") UUID userId);
}