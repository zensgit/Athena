package com.ecm.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
    name = "rendition_resources",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_rendition_resources_document_key",
            columnNames = {"document_id", "rendition_key"}
        )
    },
    indexes = {
        @Index(name = "idx_rendition_resources_document", columnList = "document_id"),
        @Index(name = "idx_rendition_resources_state", columnList = "state")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenditionResource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "rendition_key", nullable = false, length = 64)
    private String renditionKey;

    @Column(name = "label", nullable = false, length = 128)
    private String label;

    @Column(name = "mime_type", nullable = false, length = 191)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private RenditionState state;

    @Column(name = "available", nullable = false)
    private boolean available;

    @Column(name = "downloadable", nullable = false)
    private boolean downloadable;

    @Column(name = "applicable", nullable = false)
    private boolean applicable;

    @Column(name = "applicability_reason", length = 256)
    private String applicabilityReason;

    @Column(name = "generation_mode", length = 64)
    private String generationMode;

    @Column(name = "dependency_rendition_key", length = 64)
    private String dependencyRenditionKey;

    @Column(name = "content_url", length = 512)
    private String contentUrl;

    @Column(name = "error_reason", columnDefinition = "TEXT")
    private String errorReason;

    @Column(name = "error_category", length = 128)
    private String errorCategory;

    @Column(name = "source_status", length = 64)
    private String sourceStatus;

    @Column(name = "version_label", length = 64)
    private String versionLabel;

    @Column(name = "source_updated_at")
    private LocalDateTime sourceUpdatedAt;

    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @PrePersist
    @PreUpdate
    protected void touchSyncTime() {
        if (lastSyncedAt == null) {
            lastSyncedAt = LocalDateTime.now();
        }
    }
}
