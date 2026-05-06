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
@Table(name = "property_encryption_rewrap_jobs", indexes = {
    @Index(name = "idx_prop_enc_rewrap_job_status", columnList = "status"),
    @Index(name = "idx_prop_enc_rewrap_job_requested", columnList = "requested_at")
})
public class PropertyEncryptionRewrapJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RewrapJobStatus status = RewrapJobStatus.PLANNED;

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

    @Column(name = "candidate_node_count", nullable = false)
    private long candidateNodeCount;

    @Column(name = "encrypted_property_value_count", nullable = false)
    private long encryptedPropertyValueCount;

    @Column(name = "values_already_on_target_key_count", nullable = false)
    private long valuesAlreadyOnTargetKeyCount;

    @Column(name = "values_requiring_rewrap_count", nullable = false)
    private long valuesRequiringRewrapCount;

    @Column(name = "unversioned_or_malformed_value_count", nullable = false)
    private long unversionedOrMalformedValueCount;

    @Column(name = "processed_value_count", nullable = false)
    private long processedValueCount;

    @Column(name = "rewrapped_value_count", nullable = false)
    private long rewrappedValueCount;

    @Column(name = "skipped_value_count", nullable = false)
    private long skippedValueCount;

    @Column(name = "failed_value_count", nullable = false)
    private long failedValueCount;

    @Type(JsonType.class)
    @Column(name = "key_version_counts", columnDefinition = "jsonb")
    private List<RewrapKeyVersionCountSnapshot> keyVersionCounts = new ArrayList<>();

    @Type(JsonType.class)
    @Column(name = "missing_source_key_versions", columnDefinition = "jsonb")
    private List<String> missingSourceKeyVersions = new ArrayList<>();

    @Type(JsonType.class)
    @Column(name = "warnings", columnDefinition = "jsonb")
    private List<String> warnings = new ArrayList<>();

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

    public enum RewrapJobStatus {
        PLANNED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCEL_REQUESTED,
        CANCELLED
    }

    public record RewrapKeyVersionCountSnapshot(
        String keyVersion,
        long encryptedPropertyValueCount
    ) {
    }
}
