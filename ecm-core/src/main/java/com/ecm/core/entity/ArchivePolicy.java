package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "archive_policies", indexes = {
    @Index(name = "idx_archive_policy_folder", columnList = "folder_id", unique = true),
    @Index(name = "idx_archive_policy_enabled", columnList = "enabled")
})
@EqualsAndHashCode(callSuper = true, exclude = "folder")
@ToString(callSuper = true, exclude = "folder")
public class ArchivePolicy extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "folder_id", nullable = false, unique = true)
    private Folder folder;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "inactivity_days", nullable = false)
    private Integer inactivityDays = 90;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_tier", nullable = false, length = 20)
    private Node.ArchiveStoreTier storageTier = Node.ArchiveStoreTier.COLD;

    @Column(name = "include_subfolders", nullable = false)
    private boolean includeSubfolders = true;

    @Column(name = "max_candidates_per_run", nullable = false)
    private Integer maxCandidatesPerRun = 100;

    @Column(name = "last_dry_run_at")
    private LocalDateTime lastDryRunAt;

    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;

    @Column(name = "last_candidate_count")
    private Integer lastCandidateCount;

    @Column(name = "last_archived_node_count")
    private Integer lastArchivedNodeCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;
}
