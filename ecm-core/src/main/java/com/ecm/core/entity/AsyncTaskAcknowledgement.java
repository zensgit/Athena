package com.ecm.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "async_task_acknowledgements",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_async_task_acknowledgements_user_fingerprint",
            columnNames = {"user_id", "task_fingerprint"}
        )
    },
    indexes = {
        @Index(name = "idx_async_task_ack_user", columnList = "user_id"),
        @Index(name = "idx_async_task_ack_domain_task", columnList = "user_id, domain_key, task_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncTaskAcknowledgement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "domain_key", nullable = false, length = 64)
    private String domainKey;

    @Column(name = "task_id", nullable = false, length = 191)
    private String taskId;

    @Column(name = "task_status", nullable = false, length = 64)
    private String taskStatus;

    @Column(name = "task_fingerprint", nullable = false, length = 512)
    private String taskFingerprint;

    @Column(name = "task_timestamp")
    private LocalDateTime taskTimestamp;

    @Column(name = "acknowledged_at", nullable = false)
    private LocalDateTime acknowledgedAt;

    @PrePersist
    protected void onCreate() {
        if (acknowledgedAt == null) {
            acknowledgedAt = LocalDateTime.now();
        }
    }
}
