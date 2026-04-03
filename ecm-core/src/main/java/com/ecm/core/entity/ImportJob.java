package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "import_jobs", indexes = {
    @Index(name = "idx_import_job_user_id", columnList = "user_id"),
    @Index(name = "idx_import_job_status", columnList = "status"),
    @Index(name = "idx_import_job_created_at", columnList = "created_at")
})
public class ImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ImportJobStatus status = ImportJobStatus.PENDING;

    @Column(name = "target_folder_id")
    private UUID targetFolderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "conflict_policy", nullable = false, length = 20)
    private ConflictPolicy conflictPolicy = ConflictPolicy.SKIP;

    @Column(name = "total_files", nullable = false)
    private int totalFiles;

    @Column(name = "processed_files", nullable = false)
    private int processedFiles;

    @Column(name = "imported_files", nullable = false)
    private int importedFiles;

    @Column(name = "skipped_files", nullable = false)
    private int skippedFiles;

    @Column(name = "failed_files", nullable = false)
    private int failedFiles;

    @Column(name = "current_item_path", columnDefinition = "TEXT")
    private String currentItemPath;

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

    public enum ImportJobStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELED
    }

    public enum ConflictPolicy {
        SKIP,
        RENAME,
        OVERWRITE
    }
}
