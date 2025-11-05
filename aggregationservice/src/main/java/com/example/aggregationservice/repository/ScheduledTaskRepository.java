package com.example.aggregationservice.repository;

import com.example.aggregationservice.model.ScheduledTask;
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
public interface ScheduledTaskRepository extends JpaRepository<ScheduledTask, UUID> {

    @Query("SELECT st FROM ScheduledTask st WHERE st.scheduledTime <= :now AND st.status = 'PENDING' ORDER BY st.priority DESC, st.scheduledTime ASC")
    List<ScheduledTask> findDueTasks(@Param("now") Instant now);

    @Modifying
    @Query("UPDATE ScheduledTask st SET st.status = 'PROCESSING', st.lockedBy = :lockedBy, st.lockedAt = :now WHERE st.id = :id AND st.status = 'PENDING'")
    int lockTask(@Param("id") UUID id, @Param("lockedBy") String lockedBy, @Param("now") Instant now);

    @Query("SELECT st FROM ScheduledTask st WHERE st.taskType = :taskType AND st.status = 'PENDING'")
    List<ScheduledTask> findByTypeAndStatus(@Param("taskType") String taskType, @Param("status") String status);
}