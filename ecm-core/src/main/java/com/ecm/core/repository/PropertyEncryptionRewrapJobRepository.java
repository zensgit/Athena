package com.ecm.core.repository;

import com.ecm.core.entity.PropertyEncryptionRewrapJob;
import com.ecm.core.entity.PropertyEncryptionRewrapJob.RewrapJobStatus;
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
public interface PropertyEncryptionRewrapJobRepository extends JpaRepository<PropertyEncryptionRewrapJob, UUID> {

    List<PropertyEncryptionRewrapJob> findAllByOrderByRequestedAtDesc(Pageable pageable);

    boolean existsByIdAndStatus(UUID id, RewrapJobStatus status);

    default int claimPlannedJob(UUID jobId, LocalDateTime startedAt) {
        return claimJobWithStatus(jobId, startedAt, RewrapJobStatus.RUNNING, RewrapJobStatus.PLANNED);
    }

    default int markRewrapJobStartFailed(UUID jobId, LocalDateTime finishedAt, String lastError) {
        return markTerminalIfStatus(
            jobId,
            RewrapJobStatus.FAILED,
            finishedAt,
            0,
            0,
            0,
            0,
            lastError,
            RewrapJobStatus.RUNNING
        );
    }

    default int requestRewrapJobCancel(UUID jobId, LocalDateTime requestedAt) {
        int cancelled = cancelJobWithStatus(
            jobId,
            requestedAt,
            RewrapJobStatus.CANCELLED,
            RewrapJobStatus.PLANNED
        );
        if (cancelled == 1) {
            return 1;
        }
        return requestJobCancelWithStatus(
            jobId,
            requestedAt,
            RewrapJobStatus.CANCEL_REQUESTED,
            RewrapJobStatus.RUNNING
        );
    }

    default int markTerminalIfRunningOrCancelRequested(
        UUID jobId,
        RewrapJobStatus status,
        LocalDateTime finishedAt,
        long processedValueCount,
        long rewrappedValueCount,
        long skippedValueCount,
        long failedValueCount,
        String lastError
    ) {
        int updated = markTerminalIfStatus(
            jobId,
            status,
            finishedAt,
            processedValueCount,
            rewrappedValueCount,
            skippedValueCount,
            failedValueCount,
            lastError,
            RewrapJobStatus.RUNNING
        );
        if (updated == 1) {
            return 1;
        }
        return markTerminalIfStatus(
            jobId,
            status,
            finishedAt,
            processedValueCount,
            rewrappedValueCount,
            skippedValueCount,
            failedValueCount,
            lastError,
            RewrapJobStatus.CANCEL_REQUESTED
        );
    }

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PropertyEncryptionRewrapJob j
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
        @Param("nextStatus") RewrapJobStatus nextStatus,
        @Param("expectedStatus") RewrapJobStatus expectedStatus
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PropertyEncryptionRewrapJob j
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
        @Param("nextStatus") RewrapJobStatus nextStatus,
        @Param("expectedStatus") RewrapJobStatus expectedStatus
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PropertyEncryptionRewrapJob j
           set j.status = :nextStatus,
               j.updatedAt = :requestedAt,
               j.version = j.version + 1
         where j.id = :jobId
           and j.status = :expectedStatus
    """)
    int requestJobCancelWithStatus(
        @Param("jobId") UUID jobId,
        @Param("requestedAt") LocalDateTime requestedAt,
        @Param("nextStatus") RewrapJobStatus nextStatus,
        @Param("expectedStatus") RewrapJobStatus expectedStatus
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PropertyEncryptionRewrapJob j
           set j.status = :status,
               j.finishedAt = :finishedAt,
               j.updatedAt = :finishedAt,
               j.processedValueCount = :processedValueCount,
               j.rewrappedValueCount = :rewrappedValueCount,
               j.skippedValueCount = :skippedValueCount,
               j.failedValueCount = :failedValueCount,
               j.lastError = :lastError,
               j.version = j.version + 1
         where j.id = :jobId
           and j.status = :expectedStatus
    """)
    int markTerminalIfStatus(
        @Param("jobId") UUID jobId,
        @Param("status") RewrapJobStatus status,
        @Param("finishedAt") LocalDateTime finishedAt,
        @Param("processedValueCount") long processedValueCount,
        @Param("rewrappedValueCount") long rewrappedValueCount,
        @Param("skippedValueCount") long skippedValueCount,
        @Param("failedValueCount") long failedValueCount,
        @Param("lastError") String lastError,
        @Param("expectedStatus") RewrapJobStatus expectedStatus
    );
}
