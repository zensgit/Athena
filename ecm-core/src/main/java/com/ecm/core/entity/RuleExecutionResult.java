package com.ecm.core.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Rule Execution Result
 *
 * Records the result of executing an automation rule on a document.
 * Used for logging, debugging, and monitoring rule execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleExecutionResult {

    /**
     * Unique ID for this execution
     */
    private UUID executionId;

    /**
     * The rule that was executed
     */
    private AutomationRule rule;

    /**
     * The document that was processed
     */
    private UUID documentId;

    /**
     * Document name (for display purposes)
     */
    private String documentName;

    /**
     * Whether the execution was successful
     */
    private boolean success;

    /**
     * Overall error message if execution failed
     */
    private String errorMessage;

    /**
     * Whether the rule condition matched
     */
    private boolean conditionMatched;

    /**
     * Details about condition evaluation
     */
    private String conditionDetails;

    /**
     * Results for each action executed
     */
    @Builder.Default
    private List<ActionExecutionResult> actionResults = new ArrayList<>();

    /**
     * Execution start time
     */
    private LocalDateTime startTime;

    /**
     * Execution end time
     */
    private LocalDateTime endTime;

    /**
     * Total execution duration in milliseconds
     */
    private Long durationMs;

    /**
     * Trigger type that initiated this execution
     */
    private AutomationRule.TriggerType triggerType;

    /**
     * Result of executing a single action
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionExecutionResult {
        private RuleAction.ActionType actionType;
        private boolean success;
        private String errorMessage;
        private Long durationMs;
        private String details;
    }

    /**
     * Factory method for successful execution
     */
    public static RuleExecutionResult success(AutomationRule rule, Document document) {
        return RuleExecutionResult.builder()
            .executionId(UUID.randomUUID())
            .rule(rule)
            .documentId(document.getId())
            .documentName(document.getName())
            .success(true)
            .conditionMatched(true)
            .startTime(LocalDateTime.now())
            .endTime(LocalDateTime.now())
            .build();
    }

    /**
     * Factory method for failed execution
     */
    public static RuleExecutionResult failed(AutomationRule rule, Document document, String error) {
        return RuleExecutionResult.builder()
            .executionId(UUID.randomUUID())
            .rule(rule)
            .documentId(document.getId())
            .documentName(document.getName())
            .success(false)
            .conditionMatched(true)
            .errorMessage(error)
            .startTime(LocalDateTime.now())
            .endTime(LocalDateTime.now())
            .build();
    }

    /**
     * Factory method for condition not matched
     */
    public static RuleExecutionResult notMatched(AutomationRule rule, Document document, String details) {
        return RuleExecutionResult.builder()
            .executionId(UUID.randomUUID())
            .rule(rule)
            .documentId(document.getId())
            .documentName(document.getName())
            .success(true)
            .conditionMatched(false)
            .conditionDetails(details)
            .startTime(LocalDateTime.now())
            .endTime(LocalDateTime.now())
            .build();
    }

    /**
     * Add an action result
     */
    public void addActionResult(ActionExecutionResult result) {
        if (actionResults == null) {
            actionResults = new ArrayList<>();
        }
        actionResults.add(result);
    }

    /**
     * Calculate and set duration
     */
    public void calculateDuration() {
        if (startTime != null && endTime != null) {
            durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        }
    }

    /**
     * Get count of successful actions
     */
    public int getSuccessfulActionCount() {
        if (actionResults == null) return 0;
        return (int) actionResults.stream().filter(ActionExecutionResult::isSuccess).count();
    }

    /**
     * Get count of failed actions
     */
    public int getFailedActionCount() {
        if (actionResults == null) return 0;
        return (int) actionResults.stream().filter(r -> !r.isSuccess()).count();
    }

    /**
     * Get total count of actions executed
     */
    public int getTotalActionCount() {
        if (actionResults == null) return 0;
        return actionResults.size();
    }
}
