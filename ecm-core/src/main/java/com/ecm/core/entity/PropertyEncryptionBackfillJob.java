package com.ecm.core.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Table(name = "property_encryption_backfill_jobs", indexes = {
    @Index(name = "idx_prop_enc_backfill_job_status", columnList = "status"),
    @Index(name = "idx_prop_enc_backfill_job_requested", columnList = "requested_at")
})
public class PropertyEncryptionBackfillJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private BackfillJobStatus status = BackfillJobStatus.PLANNED;

    @Column(name = "target_key_version", length = 100)
    private String targetKeyVersion;

    @Column(name = "requested_by", nullable = false, length = 255)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "encrypted_property_definition_count", nullable = false)
    private long encryptedPropertyDefinitionCount;

    @Column(name = "plaintext_value_count", nullable = false)
    private long plaintextValueCount;

    @Column(name = "already_encrypted_value_count", nullable = false)
    private long alreadyEncryptedValueCount;

    @Column(name = "dual_storage_conflict_value_count", nullable = false)
    private long dualStorageConflictValueCount;

    @Column(name = "ready_value_count", nullable = false)
    private long readyValueCount;

    @Column(name = "orphan_encrypted_value_count", nullable = false)
    private long orphanEncryptedValueCount;

    @Column(name = "processed_value_count", nullable = false)
    private long processedValueCount;

    @Column(name = "migrated_value_count", nullable = false)
    private long migratedValueCount;

    @Column(name = "skipped_value_count", nullable = false)
    private long skippedValueCount;

    @Column(name = "failed_value_count", nullable = false)
    private long failedValueCount;

    @Type(JsonType.class)
    @Column(name = "warnings", columnDefinition = "jsonb")
    private List<String> warnings = new ArrayList<>();

    @Type(JsonType.class)
    @Column(name = "definition_counts", columnDefinition = "jsonb")
    private List<BackfillDefinitionCountSnapshot> definitionCounts = new ArrayList<>();

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (requestedAt == null) {
            requestedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum BackfillJobStatus {
        PLANNED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCEL_REQUESTED,
        CANCELLED
    }

    public record BackfillDefinitionCountSnapshot(
        String qualifiedName,
        String ownerKind,
        String ownerQName,
        long plaintextValueCount,
        long alreadyEncryptedValueCount,
        long dualStorageConflictValueCount,
        long readyValueCount
    ) {
    }
}
