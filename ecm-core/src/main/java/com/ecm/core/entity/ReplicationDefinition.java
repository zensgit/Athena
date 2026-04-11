package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "replication_definitions", indexes = {
    @Index(name = "idx_replication_definition_name", columnList = "name", unique = true),
    @Index(name = "idx_replication_definition_source", columnList = "source_node_id"),
    @Index(name = "idx_replication_definition_target", columnList = "transfer_target_id")
})
public class ReplicationDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200, unique = true)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "source_node_id", nullable = false)
    private UUID sourceNodeId;

    @Column(name = "transfer_target_id", nullable = false)
    private UUID transferTargetId;

    @Column(name = "include_children", nullable = false)
    private boolean includeChildren = true;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "cron_expression", length = 100)
    private String cronExpression;

    @Column(name = "schedule_timezone", length = 64)
    private String scheduleTimezone = "UTC";

    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    @Column(name = "auto_retry_enabled", nullable = false)
    private boolean autoRetryEnabled = false;

    @Column(name = "max_retry_attempts", nullable = false)
    private Integer maxRetryAttempts = 0;

    @Column(name = "retry_backoff_minutes", nullable = false)
    private Integer retryBackoffMinutes = 0;

    @Column(name = "job_retention_days", nullable = false)
    private Integer jobRetentionDays = 30;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
