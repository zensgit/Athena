package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "replication_jobs", indexes = {
    @Index(name = "idx_replication_job_definition_id", columnList = "definition_id"),
    @Index(name = "idx_replication_job_target_id", columnList = "transfer_target_id"),
    @Index(name = "idx_replication_job_retry_of_job_id", columnList = "retry_of_job_id"),
    @Index(name = "idx_replication_job_status", columnList = "status"),
    @Index(name = "idx_replication_job_created_at", columnList = "created_at")
})
public class ReplicationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "definition_id", nullable = false)
    private UUID definitionId;

    @Column(name = "transfer_target_id", nullable = false)
    private UUID transferTargetId;

    @Column(name = "source_node_id", nullable = false)
    private UUID sourceNodeId;

    @Column(name = "retry_of_job_id")
    private UUID retryOfJobId;

    @Column(name = "copied_node_id")
    private UUID copiedNodeId;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber = 1;

    @Column(name = "scheduled_for")
    private LocalDateTime scheduledFor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReplicationJobStatus status = ReplicationJobStatus.PENDING;

    @Column(name = "last_message", columnDefinition = "TEXT")
    private String lastMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_status", nullable = false, length = 20)
    private TransportStatus transportStatus = TransportStatus.NEVER_RUN;

    @Column(name = "transport_message", columnDefinition = "TEXT")
    private String transportMessage;

    @Column(name = "error_log", columnDefinition = "TEXT")
    private String errorLog;

    @Column(name = "last_attempted_at")
    private LocalDateTime lastAttemptedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum ReplicationJobStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELED
    }

    public enum TransportStatus {
        NEVER_RUN,
        RUNNING,
        SUCCESS,
        FAILED
    }
}
