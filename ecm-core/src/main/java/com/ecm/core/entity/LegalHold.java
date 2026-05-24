package com.ecm.core.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "legal_holds", indexes = {
    @Index(name = "idx_legal_hold_status", columnList = "status")
})
@EqualsAndHashCode(callSuper = true, exclude = "items")
@ToString(callSuper = true, exclude = "items")
public class LegalHold extends BaseEntity {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HoldStatus status = HoldStatus.ACTIVE;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "released_by", length = 255)
    private String releasedBy;

    @Column(name = "release_comment", columnDefinition = "TEXT")
    private String releaseComment;

    /**
     * Structured release reason added 2026-05-24 (migration 094). Nullable so
     * RELEASED rows predating the migration remain legal-valid without
     * backfill — frontend renders a "Legacy release" chip for
     * {@code status == RELEASED && releaseReason == null}. New releases via
     * {@code LegalHoldService.releaseHold(...)} require a non-null reason
     * (HTTP 400 otherwise).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "release_reason", length = 32)
    private HoldReleaseReason releaseReason;

    @OneToMany(mappedBy = "hold", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<LegalHoldItem> items = new LinkedHashSet<>();

    public enum HoldStatus {
        ACTIVE,
        RELEASED
    }

    public enum HoldReleaseReason {
        LITIGATION_ENDED,
        SCHEDULED_DISPOSITION,
        REQUEST_BY_REQUESTOR,
        OTHER
    }
}
