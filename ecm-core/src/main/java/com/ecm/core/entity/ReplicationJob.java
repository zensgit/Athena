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

    @Column(name = "copied_node_id")
    private UUID copiedNodeId;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReplicationJobStatus status = ReplicationJobStatus.PENDING;

    @Column(name = "last_message", columnDefinition = "TEXT")
    private String lastMessage;

    @Column(name = "error_log", columnDefinition = "TEXT")
    private String errorLog;

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
}
