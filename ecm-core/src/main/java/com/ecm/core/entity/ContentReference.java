package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Authoritative binary ownership ledger. Every content binary must be tracked here;
 * physical deletion only occurs when zero active references remain.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "content_references",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_content_ref_owner",
            columnNames = {"content_id", "owner_type", "owner_id"})
    },
    indexes = {
        @Index(name = "idx_content_ref_content_active", columnList = "content_id, active"),
        @Index(name = "idx_content_ref_owner", columnList = "owner_type, owner_id")
    })
public class ContentReference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "content_id", nullable = false)
    private String contentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 32)
    private OwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum OwnerType {
        DOCUMENT,
        VERSION,
        WORKING_COPY,
        RENDITION
    }
}
