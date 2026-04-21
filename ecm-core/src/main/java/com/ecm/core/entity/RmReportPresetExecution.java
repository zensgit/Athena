package com.ecm.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "rm_report_preset_executions", indexes = {
    @Index(name = "idx_rm_report_preset_exec_preset_started", columnList = "preset_id, started_at"),
    @Index(name = "idx_rm_report_preset_exec_status", columnList = "status")
})
public class RmReportPresetExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "preset_id", nullable = false)
    private RmReportPreset preset;

    @Column(name = "owner", nullable = false, length = 255)
    private String owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExecutionStatus status;

    @Column(name = "filename", length = 255)
    private String filename;

    @Column(name = "target_folder_id")
    private UUID targetFolderId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "message", length = 2000)
    private String message;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at", nullable = false)
    private LocalDateTime finishedAt;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    public enum TriggerType {
        MANUAL,
        SCHEDULED
    }

    public enum ExecutionStatus {
        SUCCESS,
        FAILED
    }
}
