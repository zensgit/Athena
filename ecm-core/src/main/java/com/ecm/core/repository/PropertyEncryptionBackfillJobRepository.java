package com.ecm.core.repository;

import com.ecm.core.entity.PropertyEncryptionBackfillJob;
import com.ecm.core.entity.PropertyEncryptionBackfillJob.BackfillJobStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PropertyEncryptionBackfillJobRepository extends JpaRepository<PropertyEncryptionBackfillJob, UUID> {

    List<PropertyEncryptionBackfillJob> findAllByOrderByRequestedAtDesc(Pageable pageable);

    long countByStatus(BackfillJobStatus status);

    default int claimPlannedJob(UUID jobId, LocalDateTime startedAt) {
        return claimJobWithStatus(
            jobId,
            startedAt,
            BackfillJobStatus.RUNNING,
            BackfillJobStatus.PLANNED
        );
    }

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PropertyEncryptionBackfillJob j
           set j.status = :nextStatus,
               j.startedAt = :startedAt,
               j.finishedAt = null,
               j.lastError = null,
               j.updatedAt = :startedAt,
               j.version = j.version + 1
         where j.id = :jobId
           and j.status = :expectedStatus
    """)
    int claimJobWithStatus(
        @Param("jobId") UUID jobId,
        @Param("startedAt") LocalDateTime startedAt,
        @Param("nextStatus") BackfillJobStatus nextStatus,
        @Param("expectedStatus") BackfillJobStatus expectedStatus
    );

    default int markTerminalIfRunning(
        UUID jobId,
        BackfillJobStatus status,
        LocalDateTime finishedAt,
        long processedValueCount,
        long migratedValueCount,
        long skippedValueCount,
        long failedValueCount,
        String lastError
    ) {
        return markTerminalIfStatus(
            jobId,
            status,
            finishedAt,
            processedValueCount,
            migratedValueCount,
            skippedValueCount,
            failedValueCount,
            lastError,
            BackfillJobStatus.RUNNING
        );
    }

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PropertyEncryptionBackfillJob j
           set j.status = :status,
               j.finishedAt = :finishedAt,
               j.updatedAt = :finishedAt,
               j.processedValueCount = :processedValueCount,
               j.migratedValueCount = :migratedValueCount,
               j.skippedValueCount = :skippedValueCount,
               j.failedValueCount = :failedValueCount,
               j.lastError = :lastError,
               j.version = j.version + 1
         where j.id = :jobId
           and j.status = :expectedStatus
    """)
    int markTerminalIfStatus(
        @Param("jobId") UUID jobId,
        @Param("status") BackfillJobStatus status,
        @Param("finishedAt") LocalDateTime finishedAt,
        @Param("processedValueCount") long processedValueCount,
        @Param("migratedValueCount") long migratedValueCount,
        @Param("skippedValueCount") long skippedValueCount,
        @Param("failedValueCount") long failedValueCount,
        @Param("lastError") String lastError,
        @Param("expectedStatus") BackfillJobStatus expectedStatus
    );
}
