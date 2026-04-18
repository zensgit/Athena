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
@Table(name = "disposition_action_executions", indexes = {
    @Index(name = "idx_disposition_exec_schedule_time", columnList = "schedule_id, executed_at"),
    @Index(name = "idx_disposition_exec_schedule_action_status", columnList = "schedule_id, action_type, status")
})
public class DispositionActionExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private DispositionSchedule schedule;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private ActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExecutionStatus status;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Column(name = "node_name", nullable = false, length = 255)
    private String nodeName;

    @Column(name = "node_type", nullable = false, length = 20)
    private String nodeType;

    @Column(name = "node_path", nullable = false, length = 2000)
    private String nodePath;

    @Column(name = "affected_node_count", nullable = false)
    private Integer affectedNodeCount = 1;

    @Column(name = "details", length = 2000)
    private String details;

    @Column(name = "actor", nullable = false, length = 255)
    private String actor;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    public enum ActionType {
        CUTOFF,
        ARCHIVE,
        DESTROY
    }

    public enum ExecutionStatus {
        SUCCESS,
        BLOCKED,
        FAILED
    }
}
