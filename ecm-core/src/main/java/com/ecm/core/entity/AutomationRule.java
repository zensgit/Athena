package com.ecm.core.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Automation Rule Entity
 *
 * Represents an automated rule that can be triggered on document events.
 * Rules have conditions that must be met and actions that are executed.
 *
 * Example use cases:
 * - Auto-tag documents based on name patterns
 * - Move documents to folders based on MIME type
 * - Auto-classify documents based on content
 * - Send notifications when documents match criteria
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "automation_rules", indexes = {
    @Index(name = "idx_rule_trigger", columnList = "trigger_type"),
    @Index(name = "idx_rule_enabled", columnList = "enabled"),
    @Index(name = "idx_rule_owner", columnList = "owner"),
    @Index(name = "idx_rule_priority", columnList = "priority")
})
@EqualsAndHashCode(callSuper = true)
public class AutomationRule extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    /**
     * Event type that triggers this rule
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private TriggerType triggerType;

    /**
     * Rule conditions (JSON format)
     * Supports AND, OR, NOT combinations of simple conditions
     */
    @Type(JsonType.class)
    @Column(name = "conditions", columnDefinition = "jsonb")
    private RuleCondition condition;

    /**
     * List of actions to execute when conditions are met
     */
    @Type(JsonType.class)
    @Column(name = "actions", columnDefinition = "jsonb")
    @Builder.Default
    private List<RuleAction> actions = new ArrayList<>();

    /**
     * Priority for rule execution order (lower = higher priority)
     * Default is 100
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 100;

    /**
     * Whether this rule is enabled
     */
    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;

    /**
     * Owner of this rule (usually the creator)
     */
    @Column(name = "owner")
    private String owner;

    /**
     * Optional: Restrict rule to specific folder and its children
     */
    @Column(name = "scope_folder_id")
    private UUID scopeFolderId;

    /**
     * Optional: Restrict rule to specific MIME types (comma-separated)
     */
    @Column(name = "scope_mime_types", length = 500)
    private String scopeMimeTypes;

    /**
     * Counter for how many times this rule has been executed
     */
    @Column(name = "execution_count")
    @Builder.Default
    private Long executionCount = 0L;

    /**
     * Counter for how many times this rule execution failed
     */
    @Column(name = "failure_count")
    @Builder.Default
    private Long failureCount = 0L;

    /**
     * Whether to stop processing more rules after this one matches
     */
    @Column(name = "stop_on_match")
    @Builder.Default
    private Boolean stopOnMatch = false;

    // ========== Scheduled Rule Fields ==========

    /**
     * Cron expression for scheduled rules (e.g., "0 0 * * * *" for hourly)
     * Only applicable when triggerType = SCHEDULED
     */
    @Column(name = "cron_expression", length = 100)
    private String cronExpression;

    /**
     * Timezone for cron expression evaluation (default: UTC)
     */
    @Column(name = "timezone", length = 50)
    @Builder.Default
    private String timezone = "UTC";

    /**
     * Last time this scheduled rule was executed
     */
    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    /**
     * Next scheduled run time (computed from cron expression)
     */
    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    /**
     * Maximum number of items to process per scheduled run
     * Prevents runaway processing of large document sets
     */
    @Column(name = "max_items_per_run")
    @Builder.Default
    private Integer maxItemsPerRun = 200;

    /**
     * Event types that can trigger automation rules
     */
    public enum TriggerType {
        DOCUMENT_CREATED,      // When a new document is uploaded
        DOCUMENT_UPDATED,      // When a document is modified
        DOCUMENT_TAGGED,       // When tags are added/removed
        DOCUMENT_MOVED,        // When a document is moved to a different folder
        DOCUMENT_CATEGORIZED,  // When categories are changed
        VERSION_CREATED,       // When a new version is created
        COMMENT_ADDED,         // When a comment is added
        SCHEDULED              // For scheduled/periodic rules
    }

    /**
     * Check if a MIME type is within the scope of this rule
     */
    public boolean isMimeTypeInScope(String mimeType) {
        if (scopeMimeTypes == null || scopeMimeTypes.isBlank()) {
            return true; // No restriction
        }
        String[] allowed = scopeMimeTypes.split(",");
        for (String pattern : allowed) {
            String trimmed = pattern.trim();
            if (trimmed.endsWith("/*")) {
                // Wildcard match like "image/*"
                String prefix = trimmed.substring(0, trimmed.length() - 1);
                if (mimeType.startsWith(prefix)) {
                    return true;
                }
            } else if (trimmed.equals(mimeType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Increment the execution counter
     */
    public void incrementExecutionCount() {
        this.executionCount = (this.executionCount == null ? 0 : this.executionCount) + 1;
    }

    /**
     * Increment the failure counter
     */
    public void incrementFailureCount() {
        this.failureCount = (this.failureCount == null ? 0 : this.failureCount) + 1;
    }

    /**
     * Check if this is a scheduled rule with a valid cron expression
     */
    public boolean isScheduledRule() {
        return triggerType == TriggerType.SCHEDULED
            && cronExpression != null
            && !cronExpression.isBlank();
    }

    /**
     * Check if this scheduled rule is due for execution
     */
    public boolean isDueForExecution() {
        if (!isScheduledRule() || !Boolean.TRUE.equals(enabled)) {
            return false;
        }
        if (nextRunAt == null) {
            return true; // Never run, should run now
        }
        return LocalDateTime.now().isAfter(nextRunAt) || LocalDateTime.now().isEqual(nextRunAt);
    }

    /**
     * Update the run timestamps after execution
     */
    public void markExecuted(LocalDateTime nextRun) {
        this.lastRunAt = LocalDateTime.now();
        this.nextRunAt = nextRun;
    }
}
