package com.ecm.core.controller;

import com.ecm.core.entity.*;
import com.ecm.core.entity.AutomationRule.TriggerType;
import com.ecm.core.service.RuleEngineService;
import com.ecm.core.service.RuleEngineService.*;
import com.ecm.core.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.ecm.core.service.ScheduledRuleRunner;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Automation Rules
 *
 * Provides endpoints for:
 * - CRUD operations on automation rules
 * - Rule testing and validation
 * - Rule execution statistics
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rules")
@RequiredArgsConstructor
@Tag(name = "Automation Rules", description = "Automation rule management APIs")
public class RuleController {

    private final RuleEngineService ruleEngineService;
    private final SecurityService securityService;
    private final ScheduledRuleRunner scheduledRuleRunner;

    // ==================== Rule CRUD ====================

    @PostMapping
    @Operation(summary = "Create automation rule",
               description = "Create a new automation rule")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<RuleResponse> createRule(@RequestBody CreateRuleRequestDto request) {
        CreateRuleRequest serviceRequest = CreateRuleRequest.builder()
            .name(request.name())
            .description(request.description())
            .triggerType(request.triggerType())
            .condition(request.condition())
            .actions(request.actions())
            .priority(request.priority())
            .enabled(request.enabled())
            .owner(securityService.getCurrentUser())
            .scopeFolderId(request.scopeFolderId())
            .scopeMimeTypes(request.scopeMimeTypes())
            .stopOnMatch(request.stopOnMatch())
            // Scheduled rule fields
            .cronExpression(request.cronExpression())
            .timezone(request.timezone())
            .maxItemsPerRun(request.maxItemsPerRun())
            .manualBackfillMinutes(request.manualBackfillMinutes())
            .build();

        AutomationRule rule = ruleEngineService.createRule(serviceRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(RuleResponse.from(rule));
    }

    @GetMapping("/{ruleId}")
    @Operation(summary = "Get rule by ID",
               description = "Get details of a specific automation rule")
    public ResponseEntity<RuleResponse> getRule(@PathVariable UUID ruleId) {
        AutomationRule rule = ruleEngineService.getRule(ruleId);
        return ResponseEntity.ok(RuleResponse.from(rule));
    }

    @GetMapping
    @Operation(summary = "Get all rules",
               description = "Get all automation rules with pagination")
    public ResponseEntity<Page<RuleResponse>> getAllRules(Pageable pageable) {
        Page<AutomationRule> rules = ruleEngineService.getAllRules(pageable);
        return ResponseEntity.ok(rules.map(RuleResponse::from));
    }

    @GetMapping("/my")
    @Operation(summary = "Get my rules",
               description = "Get automation rules owned by current user")
    public ResponseEntity<Page<RuleResponse>> getMyRules(Pageable pageable) {
        String currentUser = securityService.getCurrentUser();
        Page<AutomationRule> rules = ruleEngineService.getRulesByOwner(currentUser, pageable);
        return ResponseEntity.ok(rules.map(RuleResponse::from));
    }

    @GetMapping("/search")
    @Operation(summary = "Search rules",
               description = "Search rules by name or description")
    public ResponseEntity<Page<RuleResponse>> searchRules(
            @Parameter(description = "Search query")
            @RequestParam String q,
            Pageable pageable) {
        Page<AutomationRule> rules = ruleEngineService.searchRules(q, pageable);
        return ResponseEntity.ok(rules.map(RuleResponse::from));
    }

    @PutMapping("/{ruleId}")
    @Operation(summary = "Update rule",
               description = "Update an existing automation rule")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<RuleResponse> updateRule(
            @PathVariable UUID ruleId,
            @RequestBody UpdateRuleRequestDto request) {

        UpdateRuleRequest serviceRequest = UpdateRuleRequest.builder()
            .name(request.name())
            .description(request.description())
            .triggerType(request.triggerType())
            .condition(request.condition())
            .actions(request.actions())
            .priority(request.priority())
            .enabled(request.enabled())
            .scopeFolderId(request.scopeFolderId())
            .scopeMimeTypes(request.scopeMimeTypes())
            .stopOnMatch(request.stopOnMatch())
            // Scheduled rule fields
            .cronExpression(request.cronExpression())
            .timezone(request.timezone())
            .maxItemsPerRun(request.maxItemsPerRun())
            .manualBackfillMinutes(request.manualBackfillMinutes())
            .build();

        AutomationRule rule = ruleEngineService.updateRule(ruleId, serviceRequest);
        return ResponseEntity.ok(RuleResponse.from(rule));
    }

    @DeleteMapping("/{ruleId}")
    @Operation(summary = "Delete rule",
               description = "Delete an automation rule")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID ruleId) {
        ruleEngineService.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    // ==================== Rule Enable/Disable ====================

    @PatchMapping("/{ruleId}/enable")
    @Operation(summary = "Enable rule",
               description = "Enable an automation rule")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<RuleResponse> enableRule(@PathVariable UUID ruleId) {
        AutomationRule rule = ruleEngineService.setRuleEnabled(ruleId, true);
        return ResponseEntity.ok(RuleResponse.from(rule));
    }

    @PatchMapping("/{ruleId}/disable")
    @Operation(summary = "Disable rule",
               description = "Disable an automation rule")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<RuleResponse> disableRule(@PathVariable UUID ruleId) {
        AutomationRule rule = ruleEngineService.setRuleEnabled(ruleId, false);
        return ResponseEntity.ok(RuleResponse.from(rule));
    }

    // ==================== Rule Testing ====================

    @PostMapping("/{ruleId}/test")
    @Operation(summary = "Test rule",
               description = "Test a rule against a specific document without executing actions")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<RuleTestResult> testRule(
            @PathVariable UUID ruleId,
            @RequestBody TestRuleRequest request) {

        AutomationRule rule = ruleEngineService.getRule(ruleId);

        // Build a mock document for testing
        Document testDoc = createTestDocument(request);

        // Evaluate condition only
        boolean matches = ruleEngineService.evaluateCondition(rule.getCondition(), testDoc);

        return ResponseEntity.ok(new RuleTestResult(
            ruleId,
            rule.getName(),
            matches,
            matches ? "Condition matched" : "Condition not matched",
            request.testData()
        ));
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate rule condition",
               description = "Validate a rule condition syntax")
    public ResponseEntity<ValidationResult> validateCondition(@RequestBody RuleCondition condition) {
        try {
            // Try to validate the condition structure
            validateConditionStructure(condition);
            return ResponseEntity.ok(new ValidationResult(true, "Condition is valid", null));
        } catch (Exception e) {
            return ResponseEntity.ok(new ValidationResult(false, "Invalid condition", e.getMessage()));
        }
    }

    @PostMapping("/validate-cron")
    @Operation(summary = "Validate cron expression",
               description = "Validate a cron expression and return next execution times")
    public ResponseEntity<CronValidationResult> validateCronExpression(
            @RequestBody CronValidationRequest request) {
        try {
            List<LocalDateTime> nextExecutions = scheduledRuleRunner.validateCronExpression(
                request.cronExpression(),
                request.timezone()
            );
            List<String> formattedTimes = nextExecutions.stream()
                .map(LocalDateTime::toString)
                .toList();
            return ResponseEntity.ok(new CronValidationResult(true, formattedTimes, null));
        } catch (Exception e) {
            return ResponseEntity.ok(new CronValidationResult(false, null, e.getMessage()));
        }
    }

    @PostMapping("/{ruleId}/trigger")
    @Operation(summary = "Trigger scheduled rule",
               description = "Manually trigger a scheduled rule execution")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> triggerScheduledRule(@PathVariable UUID ruleId) {
        AutomationRule rule = ruleEngineService.getRule(ruleId);
        if (!rule.isScheduledRule()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Rule is not a scheduled rule"));
        }
        scheduledRuleRunner.triggerRule(rule);
        return ResponseEntity.ok(Map.of(
            "message", "Scheduled rule triggered successfully",
            "ruleId", ruleId.toString(),
            "ruleName", rule.getName()
        ));
    }

    // ==================== Statistics ====================

    @GetMapping("/stats")
    @Operation(summary = "Get rule statistics",
               description = "Get overall automation rule statistics")
    public ResponseEntity<Map<String, Object>> getRuleStats() {
        Page<AutomationRule> allRules = ruleEngineService.getAllRules(Pageable.unpaged());

        long totalRules = allRules.getTotalElements();
        long enabledRules = allRules.stream().filter(r -> Boolean.TRUE.equals(r.getEnabled())).count();
        long totalExecutions = allRules.stream().mapToLong(r -> r.getExecutionCount() != null ? r.getExecutionCount() : 0).sum();
        long totalFailures = allRules.stream().mapToLong(r -> r.getFailureCount() != null ? r.getFailureCount() : 0).sum();

        Map<String, Long> byTriggerType = allRules.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                r -> r.getTriggerType().name(),
                java.util.stream.Collectors.counting()));

        return ResponseEntity.ok(Map.of(
            "totalRules", totalRules,
            "enabledRules", enabledRules,
            "disabledRules", totalRules - enabledRules,
            "totalExecutions", totalExecutions,
            "totalFailures", totalFailures,
            "successRate", totalExecutions > 0 ? (double)(totalExecutions - totalFailures) / totalExecutions * 100 : 0,
            "byTriggerType", byTriggerType
        ));
    }

    @GetMapping("/{ruleId}/stats")
    @Operation(summary = "Get rule execution statistics",
               description = "Get execution statistics for a specific rule")
    public ResponseEntity<Map<String, Object>> getRuleExecutionStats(@PathVariable UUID ruleId) {
        AutomationRule rule = ruleEngineService.getRule(ruleId);

        long executions = rule.getExecutionCount() != null ? rule.getExecutionCount() : 0;
        long failures = rule.getFailureCount() != null ? rule.getFailureCount() : 0;

        return ResponseEntity.ok(Map.of(
            "ruleId", ruleId,
            "ruleName", rule.getName(),
            "enabled", rule.getEnabled(),
            "executions", executions,
            "failures", failures,
            "successes", executions - failures,
            "successRate", executions > 0 ? (double)(executions - failures) / executions * 100 : 0,
            "createdDate", rule.getCreatedDate(),
            "lastModifiedDate", rule.getLastModifiedDate()
        ));
    }

    // ==================== Template Rules ====================

    @GetMapping("/templates")
    @Operation(summary = "Get rule templates",
               description = "Get predefined rule templates for common use cases")
    public ResponseEntity<List<RuleTemplate>> getRuleTemplates() {
        return ResponseEntity.ok(List.of(
            new RuleTemplate(
                "auto-tag-pdf",
                "Auto-tag PDF Documents",
                "Automatically add 'pdf' tag to all PDF documents",
                TriggerType.DOCUMENT_CREATED,
                RuleCondition.simple("mimeType", "equals", "application/pdf"),
                List.of(RuleAction.addTag("pdf"))
            ),
            new RuleTemplate(
                "auto-categorize-invoice",
                "Auto-categorize Invoices",
                "Automatically categorize documents with 'invoice' in name as Finance",
                TriggerType.DOCUMENT_CREATED,
                RuleCondition.simple("name", "contains", "invoice"),
                List.of(RuleAction.setCategory("Finance"))
            ),
            new RuleTemplate(
                "large-file-notification",
                "Large File Notification",
                "Send notification when files larger than 100MB are uploaded",
                TriggerType.DOCUMENT_CREATED,
                RuleCondition.simple("size", "gt", 104857600),
                List.of(RuleAction.sendNotification("admin", "Large file uploaded: {documentName}"))
            ),
            new RuleTemplate(
                "archive-old-docs",
                "Archive Old Documents",
                "Set status to ARCHIVED for documents moved to Archive folder",
                TriggerType.DOCUMENT_MOVED,
                RuleCondition.simple("path", "startsWith", "/Archive"),
                List.of(RuleAction.builder()
                    .type(RuleAction.ActionType.SET_STATUS)
                    .params(Map.of("status", "ARCHIVED"))
                    .build())
            )
        ));
    }

    // ==================== Helper Methods ====================

    private Document createTestDocument(TestRuleRequest request) {
        Document doc = new Document();
        doc.setId(UUID.randomUUID());

        Map<String, Object> testData = request.testData();
        if (testData != null) {
            doc.setName((String) testData.getOrDefault("name", "test.pdf"));
            doc.setDescription((String) testData.get("description"));
            doc.setMimeType((String) testData.getOrDefault("mimeType", "application/pdf"));

            if (testData.get("size") != null) {
                doc.setFileSize(((Number) testData.get("size")).longValue());
            }
        }

        return doc;
    }

    private void validateConditionStructure(RuleCondition condition) {
        if (condition == null) return;

        if (condition.getType() == RuleCondition.ConditionType.SIMPLE) {
            if (condition.getField() == null || condition.getField().isBlank()) {
                throw new IllegalArgumentException("Field is required for SIMPLE condition");
            }
            if (condition.getOperator() == null || condition.getOperator().isBlank()) {
                throw new IllegalArgumentException("Operator is required for SIMPLE condition");
            }
        } else if (condition.getType() == RuleCondition.ConditionType.AND ||
                   condition.getType() == RuleCondition.ConditionType.OR) {
            if (condition.getChildren() == null || condition.getChildren().isEmpty()) {
                throw new IllegalArgumentException("Children are required for " + condition.getType() + " condition");
            }
            for (RuleCondition child : condition.getChildren()) {
                validateConditionStructure(child);
            }
        } else if (condition.getType() == RuleCondition.ConditionType.NOT) {
            if (condition.getChildren() == null || condition.getChildren().isEmpty()) {
                throw new IllegalArgumentException("Child condition is required for NOT condition");
            }
            validateConditionStructure(condition.getChildren().get(0));
        }
    }

    // ==================== DTOs ====================

    public record CreateRuleRequestDto(
        String name,
        String description,
        TriggerType triggerType,
        RuleCondition condition,
        List<RuleAction> actions,
        Integer priority,
        Boolean enabled,
        UUID scopeFolderId,
        String scopeMimeTypes,
        Boolean stopOnMatch,
        // Scheduled rule fields
        String cronExpression,
        String timezone,
        Integer maxItemsPerRun,
        Integer manualBackfillMinutes
    ) {}

    public record UpdateRuleRequestDto(
        String name,
        String description,
        TriggerType triggerType,
        RuleCondition condition,
        List<RuleAction> actions,
        Integer priority,
        Boolean enabled,
        UUID scopeFolderId,
        String scopeMimeTypes,
        Boolean stopOnMatch,
        // Scheduled rule fields
        String cronExpression,
        String timezone,
        Integer maxItemsPerRun,
        Integer manualBackfillMinutes
    ) {}

    public record CronValidationRequest(
        String cronExpression,
        String timezone
    ) {}

    public record CronValidationResult(
        boolean valid,
        List<String> nextExecutions,
        String error
    ) {}

    public record TestRuleRequest(
        Map<String, Object> testData
    ) {}

    public record RuleTestResult(
        UUID ruleId,
        String ruleName,
        boolean matched,
        String message,
        Map<String, Object> testData
    ) {}

    public record ValidationResult(
        boolean valid,
        String message,
        String error
    ) {}

    public record RuleTemplate(
        String id,
        String name,
        String description,
        TriggerType triggerType,
        RuleCondition condition,
        List<RuleAction> actions
    ) {}

    public record RuleResponse(
        UUID id,
        String name,
        String description,
        TriggerType triggerType,
        RuleCondition condition,
        List<RuleAction> actions,
        Integer priority,
        Boolean enabled,
        String owner,
        UUID scopeFolderId,
        String scopeMimeTypes,
        Boolean stopOnMatch,
        Long executionCount,
        Long failureCount,
        LocalDateTime createdDate,
        String createdBy,
        LocalDateTime lastModifiedDate,
        String lastModifiedBy,
        // Scheduled rule fields
        String cronExpression,
        String timezone,
        LocalDateTime lastRunAt,
        LocalDateTime nextRunAt,
        Integer maxItemsPerRun,
        Integer manualBackfillMinutes
    ) {
        public static RuleResponse from(AutomationRule rule) {
            return new RuleResponse(
                rule.getId(),
                rule.getName(),
                rule.getDescription(),
                rule.getTriggerType(),
                rule.getCondition(),
                rule.getActions(),
                rule.getPriority(),
                rule.getEnabled(),
                rule.getOwner(),
                rule.getScopeFolderId(),
                rule.getScopeMimeTypes(),
                rule.getStopOnMatch(),
                rule.getExecutionCount(),
                rule.getFailureCount(),
                rule.getCreatedDate(),
                rule.getCreatedBy(),
                rule.getLastModifiedDate(),
                rule.getLastModifiedBy(),
                // Scheduled rule fields
                rule.getCronExpression(),
                rule.getTimezone(),
                rule.getLastRunAt(),
                rule.getNextRunAt(),
                rule.getMaxItemsPerRun(),
                rule.getManualBackfillMinutes()
            );
        }
    }
}
