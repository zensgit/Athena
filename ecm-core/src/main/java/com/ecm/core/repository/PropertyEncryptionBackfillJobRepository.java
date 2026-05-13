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
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface PropertyEncryptionBackfillJobRepository extends JpaRepository<PropertyEncryptionBackfillJob, UUID> {

    List<PropertyEncryptionBackfillJob> findAllByOrderByRequestedAtDesc(Pageable pageable);

    List<PropertyEncryptionBackfillJob> findByStatusInOrderByRequestedAtDesc(
        Collection<BackfillJobStatus> statuses,
        Pageable pageable
    );

    long countByStatus(BackfillJobStatus status);

    boolean existsByIdAndStatus(UUID id, BackfillJobStatus status);

    default int markBackfillJobStartFailed(UUID jobId, LocalDateTime finishedAt, String lastError) {
        return markTerminalIfStatus(
            jobId,
            BackfillJobStatus.FAILED,
            finishedAt,
            0,
            0,
            0,
            0,
            lastError,
            BackfillJobStatus.RUNNING
        );
    }

    default int markStaleActiveJobsTerminal(LocalDateTime staleStartedBefore, LocalDateTime finishedAt) {
        int failedRunning = markStaleJobsTerminalWithStatus(
            staleStartedBefore,
            finishedAt,
            BackfillJobStatus.FAILED,
            "Backfill job recovery marked stale RUNNING job failed",
            BackfillJobStatus.RUNNING
        );
        int cancelledRequested = markStaleJobsTerminalWithStatus(
            staleStartedBefore,
            finishedAt,
            BackfillJobStatus.CANCELLED,
            null,
            BackfillJobStatus.CANCEL_REQUESTED
        );
        return failedRunning + cancelledRequested;
    }

    default int claimPlannedJob(UUID jobId, LocalDateTime startedAt) {
        return claimJobWithStatus(
            jobId,
            startedAt,
            BackfillJobStatus.RUNNING,
            BackfillJobStatus.PLANNED
        );
    }

    default int requestBackfillJobCancel(UUID jobId, LocalDateTime requestedAt) {
        int cancelled = cancelJobWithStatus(
            jobId,
            requestedAt,
            BackfillJobStatus.CANCELLED,
            BackfillJobStatus.PLANNED
        );
        if (cancelled == 1) {
            return 1;
        }
        return requestJobCancelWithStatus(
            jobId,
            requestedAt,
            BackfillJobStatus.CANCEL_REQUESTED,
            BackfillJobStatus.RUNNING
        );
    }

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PropertyEncryptionBackfillJob j
           set j.status = :nextStatus,
               j.finishedAt = :requestedAt,
               j.updatedAt = :requestedAt,
               j.lastError = null,
               j.version = j.version + 1
         where j.id = :jobId
           and j.status = :expectedStatus
    """)
    int cancelJobWithStatus(
        @Param("jobId") UUID jobId,
        @Param("requestedAt") LocalDateTime requestedAt,
        @Param("nextStatus") BackfillJobStatus nextStatus,
        @Param("expectedStatus") BackfillJobStatus expectedStatus
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PropertyEncryptionBackfillJob j
           set j.status = :nextStatus,
               j.updatedAt = :requestedAt,
               j.version = j.version + 1
         where j.id = :jobId
           and j.status = :expectedStatus
    """)
    int requestJobCancelWithStatus(
        @Param("jobId") UUID jobId,
        @Param("requestedAt") LocalDateTime requestedAt,
        @Param("nextStatus") BackfillJobStatus nextStatus,
        @Param("expectedStatus") BackfillJobStatus expectedStatus
    );

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

    default int markTerminalIfRunningOrCancelRequested(
        UUID jobId,
        BackfillJobStatus status,
        LocalDateTime finishedAt,
        long processedValueCount,
        long migratedValueCount,
        long skippedValueCount,
        long failedValueCount,
        String lastError
    ) {
        int updated = markTerminalIfStatus(
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
        if (updated == 1) {
            return 1;
        }
        return markTerminalIfStatus(
            jobId,
            status,
            finishedAt,
            processedValueCount,
            migratedValueCount,
            skippedValueCount,
            failedValueCount,
            lastError,
            BackfillJobStatus.CANCEL_REQUESTED
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

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PropertyEncryptionBackfillJob j
           set j.status = :terminalStatus,
               j.finishedAt = :finishedAt,
               j.updatedAt = :finishedAt,
               j.lastError = :lastError,
               j.version = j.version + 1
         where j.status = :expectedStatus
           and j.startedAt is not null
           and j.startedAt < :staleStartedBefore
    """)
    int markStaleJobsTerminalWithStatus(
        @Param("staleStartedBefore") LocalDateTime staleStartedBefore,
        @Param("finishedAt") LocalDateTime finishedAt,
        @Param("terminalStatus") BackfillJobStatus terminalStatus,
        @Param("lastError") String lastError,
        @Param("expectedStatus") BackfillJobStatus expectedStatus
    );
}
