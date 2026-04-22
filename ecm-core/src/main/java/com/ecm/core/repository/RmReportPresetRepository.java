package com.ecm.core.repository;

import com.ecm.core.entity.RmReportPreset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

@Repository
public interface RmReportPresetRepository extends JpaRepository<RmReportPreset, UUID> {

    List<RmReportPreset> findByOwnerAndDeletedFalseOrderByName(String owner);

    Optional<RmReportPreset> findByIdAndDeletedFalse(UUID id);

    boolean existsByOwnerAndNameAndDeletedFalse(String owner, String name);

    List<RmReportPreset> findByScheduleEnabledTrueAndDeletedFalseAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
        LocalDateTime now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update RmReportPreset p
           set p.nextRunAt = :nextRunAt,
               p.entityVersion = p.entityVersion + 1
         where p.id = :presetId
           and p.deleted = false
           and p.scheduleEnabled = true
           and p.nextRunAt = :expectedNextRunAt
    """)
    int claimScheduledRun(
        @Param("presetId") UUID presetId,
        @Param("expectedNextRunAt") LocalDateTime expectedNextRunAt,
        @Param("nextRunAt") LocalDateTime nextRunAt
    );

    long countByOwnerAndScheduleEnabledTrueAndDeletedFalse(String owner);

    long countByOwnerAndScheduleEnabledTrueAndDeletedFalseAndNextRunAtLessThanEqual(
        String owner,
        LocalDateTime now
    );
}
