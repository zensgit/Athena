package com.ecm.core.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Type;

import java.util.HashMap;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * P5 PR-83: RM Saved Report Preset Foundation
 *
 * A user-saved configuration that captures the kind + parameters for one of
 * the shipped RM report endpoints (activity family / event-type / contributor /
 * contributor-family). Does NOT execute the report — just persists the intent
 * so an admin can recall the same date range + filters quickly, and so future
 * "RM delivery workflow" slices can schedule against it.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {})
@ToString(callSuper = true, exclude = {})
@Table(name = "rm_report_presets",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_rm_report_preset_owner_name",
            columnNames = {"owner", "name"})
    },
    indexes = {
        @Index(name = "idx_rm_report_preset_owner", columnList = "owner"),
        @Index(name = "idx_rm_report_preset_kind", columnList = "kind")
    })
public class RmReportPreset extends BaseEntity {

    @Column(name = "owner", nullable = false, length = 255)
    private String owner;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 64)
    private Kind kind;

    @Type(JsonType.class)
    @Column(name = "params", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> params = new HashMap<>();

    @Builder.Default
    @Column(name = "schedule_enabled", nullable = false)
    private boolean scheduleEnabled = false;

    @Column(name = "cron_expression", length = 100)
    private String cronExpression;

    @Column(name = "schedule_timezone", length = 64)
    @Builder.Default
    private String scheduleTimezone = "UTC";

    @Column(name = "delivery_folder_id")
    private UUID deliveryFolderId;

    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    public enum Kind {
        ACTIVITY_FAMILY_REPORT,
        ACTIVITY_FAMILY_HIGHLIGHTS,
        ACTIVITY_FAMILY_MIX,
        ACTIVITY_EVENT_TYPE_REPORT,
        ACTIVITY_CONTRIBUTOR_REPORT,
        ACTIVITY_CONTRIBUTOR_FAMILY_REPORT,
        ACTIVITY_CONTRIBUTOR_EVENT_TYPE_REPORT
    }
}
