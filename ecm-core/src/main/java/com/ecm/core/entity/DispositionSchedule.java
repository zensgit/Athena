package com.ecm.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "disposition_schedules", indexes = {
    @Index(name = "idx_disposition_schedule_folder", columnList = "folder_id", unique = true),
    @Index(name = "idx_disposition_schedule_enabled", columnList = "enabled")
})
@EqualsAndHashCode(callSuper = true, exclude = "folder")
@ToString(callSuper = true, exclude = "folder")
public class DispositionSchedule extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "folder_id", nullable = false, unique = true)
    private Folder folder;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "include_subfolders", nullable = false)
    private boolean includeSubfolders = true;

    @Column(name = "cutoff_after_days", nullable = false)
    private Integer cutoffAfterDays = 90;

    @Column(name = "archive_after_cutoff_days")
    private Integer archiveAfterCutoffDays;

    @Column(name = "destroy_after_archive_days")
    private Integer destroyAfterArchiveDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "archive_storage_tier", nullable = false, length = 20)
    private Node.ArchiveStoreTier archiveStorageTier = Node.ArchiveStoreTier.COLD;

    @Column(name = "max_candidates_per_action", nullable = false)
    private Integer maxCandidatesPerAction = 100;

    @Column(name = "last_dry_run_at")
    private LocalDateTime lastDryRunAt;

    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;
}
