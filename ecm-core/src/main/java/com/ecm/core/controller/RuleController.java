package com.ecm.core.controller;

import com.ecm.core.entity.*;
import com.ecm.core.entity.AutomationRule.TriggerType;
import com.ecm.core.repository.AuditLogRepository;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.RuleEngineService;
import com.ecm.core.service.RuleEngineService.*;
import com.ecm.core.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.ecm.core.service.ScheduledRuleRunner;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
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
    private final AuditService auditService;
    private final AuditLogRepository auditLogRepository;

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

    @GetMapping("/folders/{folderId}")
    @Operation(
        summary = "Get rules by scoped folder",
        description = "Get automation rules scoped to a specific folder ordered by priority"
    )
    public ResponseEntity<Page<RuleResponse>> getRulesByScopeFolder(
            @PathVariable UUID folderId,
            Pageable pageable) {
        Page<AutomationRule> rules = ruleEngineService.getRulesByScopeFolder(folderId, pageable);
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

    @PostMapping("/folders/{folderId}/reorder")
    @Operation(
        summary = "Reorder folder scoped rules",
        description = "Reorder scoped rules by IDs and reassign priorities in sequence"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<FolderRuleReorderResponse> reorderScopeFolderRules(
            @PathVariable UUID folderId,
            @RequestBody FolderRuleReorderRequest request) {
        List<AutomationRule> reordered = ruleEngineService.reorderRulesByScopeFolder(
            folderId,
            new RuleEngineService.FolderRuleReorderRequest(
                request.ruleIds(),
                request.basePriority(),
                request.step()
            )
        );
        return ResponseEntity.ok(new FolderRuleReorderResponse(
            folderId,
            reordered.size(),
            reordered.stream().map(RuleResponse::from).toList()
        ));
    }

    @PostMapping("/folders/{folderId}/dry-run")
    @Operation(
        summary = "Dry-run folder scoped rules",
        description = "Evaluate folder scoped rules against synthetic input without executing actions"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<FolderRuleDryRunResponse> dryRunScopeFolderRules(
            @PathVariable UUID folderId,
            @RequestBody(required = false) FolderRuleDryRunRequestDto request) {
        FolderRuleDryRunRequestDto safeRequest = request != null
            ? request
            : new FolderRuleDryRunRequestDto(null, Map.of(), null);
        RuleEngineService.FolderRuleDryRunResult result = ruleEngineService.dryRunRulesByScopeFolder(
            folderId,
            new RuleEngineService.FolderRuleDryRunRequest(
                safeRequest.triggerType(),
                safeRequest.testData(),
                safeRequest.limit()
            )
        );
        return ResponseEntity.ok(new FolderRuleDryRunResponse(
            result.scopeFolderId(),
            result.triggerType(),
            result.found(),
            result.scanned(),
            result.matched(),
            result.processable(),
            result.skipped(),
            result.errors(),
            result.skipReasons(),
            result.results().stream()
                .map(item -> new FolderRuleDryRunItemResponse(
                    item.ruleId(),
                    item.ruleName(),
                    item.priority(),
                    item.matched(),
                    item.processable(),
                    item.skipReason(),
                    item.unsupportedActions(),
                    item.error()
                ))
                .toList()
        ));
    }

    // ==================== Manual Execution Ledger ====================

    @PostMapping("/{ruleId}/execute")
    @Operation(
        summary = "Execute rule manually",
        description = "Execute one rule against a document with optional idempotency key and persist a run ledger record"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<RuleExecutionCommandResponse> executeRuleManually(
            @PathVariable UUID ruleId,
            @RequestBody RuleExecutionCommandRequest request) {
        if (request.documentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documentId is required");
        }
        RuleEngineService.RuleExecutionCommandResult result = ruleEngineService.executeRuleManual(
            ruleId,
            request.documentId(),
            request.triggerType(),
            request.idempotencyKey()
        );
        return ResponseEntity.ok(new RuleExecutionCommandResponse(
            result.runId(),
            result.deduplicated(),
            result.deduplicatedFromRunId(),
            toRuleRunRecordResponse(result.run())
        ));
    }

    @GetMapping({"/executions", "/executions/timeline"})
    @Operation(
        summary = "List recent manual rule executions",
        description = "List manual rule execution ledger records with timeline filters"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<List<RuleRunRecordResponse>> listManualRuleExecutions(
            @RequestParam(required = false) UUID ruleId,
            @RequestParam(required = false) UUID documentId,
            @RequestParam(required = false) TriggerType triggerType,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "20") int limit) {
        RuleRunTimelineQuery query = new RuleRunTimelineQuery(
            ruleId,
            documentId,
            triggerType,
            success,
            actor,
            parseDateTimeFilter("from", from),
            parseDateTimeFilter("to", to),
            limit
        );
        return ResponseEntity.ok(
            ruleEngineService.listRuleRuns(query).stream()
                .map(this::toRuleRunRecordResponse)
                .toList()
        );
    }

    @GetMapping(value = {"/executions/export", "/executions/timeline/export"}, produces = "text/csv")
    @Operation(
        summary = "Export manual rule execution timeline",
        description = "Export filtered manual rule execution ledger records to CSV"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<byte[]> exportManualRuleExecutions(
            @RequestParam(required = false) UUID ruleId,
            @RequestParam(required = false) UUID documentId,
            @RequestParam(required = false) TriggerType triggerType,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "200") int limit) {
        RuleRunTimelineQuery query = new RuleRunTimelineQuery(
            ruleId,
            documentId,
            triggerType,
            success,
            actor,
            parseDateTimeFilter("from", from),
            parseDateTimeFilter("to", to),
            limit
        );
        List<RuleRunRecordResponse> rows = ruleEngineService.listRuleRuns(query).stream()
            .map(this::toRuleRunRecordResponse)
            .toList();

        String username = securityService.getCurrentUser();
        auditService.logEvent(
            "RULE_MANUAL_RUN_TIMELINE_EXPORTED",
            null,
            "rule-manual-executions",
            username != null ? username : "system",
            String.format(
                "Exported %d manual rule executions (ruleId=%s, documentId=%s, trigger=%s, success=%s, actor=%s)",
                rows.size(),
                ruleId,
                documentId,
                triggerType,
                success,
                actor
            )
        );

        byte[] csv = buildRuleExecutionCsv(rows).getBytes(StandardCharsets.UTF_8);
        String filename = "rule-executions-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(csv.length);
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    @GetMapping("/executions/{runId}")
    @Operation(
        summary = "Get manual rule execution run",
        description = "Get one manual rule execution ledger record by run id"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<RuleRunRecordResponse> getManualRuleExecution(
            @PathVariable UUID runId) {
        RuleEngineService.RuleRunLedgerRecord runRecord = ruleEngineService.getRuleRun(runId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule run not found: " + runId));
        return ResponseEntity.ok(toRuleRunRecordResponse(runRecord));
    }

    @GetMapping("/executions/audit")
    @Operation(
        summary = "List rule audit timeline",
        description = "List rule/scheduled-rule audit events with optional filters"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<List<RuleAuditTimelineItemResponse>> listRuleExecutionAuditTimeline(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) UUID nodeId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "50") int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 500));
        LocalDateTime fromAt = parseDateTimeFilter("from", from);
        LocalDateTime toAt = parseDateTimeFilter("to", to);
        return ResponseEntity.ok(
            auditLogRepository.findRuleAuditTimeline(
                eventType,
                actor,
                nodeId,
                fromAt,
                toAt,
                PageRequest.of(0, boundedLimit)
            ).getContent().stream()
                .map(this::toRuleAuditTimelineItemResponse)
                .toList()
        );
    }

    @GetMapping(value = "/executions/audit/export", produces = "text/csv")
    @Operation(
        summary = "Export rule audit timeline",
        description = "Export filtered rule/scheduled-rule audit events to CSV"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<byte[]> exportRuleExecutionAuditTimeline(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) UUID nodeId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "500") int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 2000));
        LocalDateTime fromAt = parseDateTimeFilter("from", from);
        LocalDateTime toAt = parseDateTimeFilter("to", to);
        List<RuleAuditTimelineItemResponse> rows = auditLogRepository.findRuleAuditTimeline(
            eventType,
            actor,
            nodeId,
            fromAt,
            toAt,
            PageRequest.of(0, boundedLimit)
        ).getContent().stream()
            .map(this::toRuleAuditTimelineItemResponse)
            .toList();

        String username = securityService.getCurrentUser();
        auditService.logEvent(
            "RULE_AUDIT_TIMELINE_EXPORTED",
            nodeId,
            "rule-audit-timeline",
            username != null ? username : "system",
            String.format(
                "Exported %d rule audit events (eventType=%s, actor=%s, nodeId=%s)",
                rows.size(),
                eventType,
                actor,
                nodeId
            )
        );

        byte[] csv = buildRuleAuditTimelineCsv(rows).getBytes(StandardCharsets.UTF_8);
        String filename = "rule-audit-timeline-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(csv.length);
        return ResponseEntity.ok().headers(headers).body(csv);
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
        try {
            scheduledRuleRunner.triggerRule(rule);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException | org.hibernate.StaleObjectStateException ex) {
            // Scheduled rules can race with async preview/status updates; treat as non-fatal for manual trigger.
            log.warn("Scheduled rule trigger hit concurrent update for rule {}: {}", ruleId, ex.getMessage());
            return ResponseEntity.ok(Map.of(
                "message", "Scheduled rule triggered with concurrent update warning",
                "ruleId", ruleId.toString(),
                "ruleName", rule.getName(),
                "warning", "optimistic_lock"
            ));
        }
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

    @GetMapping("/actions/definitions")
    @Operation(
        summary = "Get rule action definitions",
        description = "Get supported rule action types and parameter definitions for rule builder UIs."
    )
    public ResponseEntity<RuleActionDefinitionsResponse> getRuleActionDefinitions() {
        List<RuleActionDefinition> actions = Arrays.stream(RuleAction.ActionType.values())
            .map(this::toRuleActionDefinition)
            .toList();
        return ResponseEntity.ok(new RuleActionDefinitionsResponse(actions));
    }

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

    private RuleActionDefinition toRuleActionDefinition(RuleAction.ActionType actionType) {
        List<String> requiredParams = new ArrayList<>();
        List<String> optionalParams = new ArrayList<>();
        List<String> constraints = new ArrayList<>();

        switch (actionType) {
            case ADD_TAG, REMOVE_TAG -> requiredParams.add(RuleAction.ParamKeys.TAG_NAME);
            case SET_CATEGORY, REMOVE_CATEGORY -> requiredParams.add(RuleAction.ParamKeys.CATEGORY_NAME);
            case MOVE_TO_FOLDER -> requiredParams.add(RuleAction.ParamKeys.FOLDER_ID);
            case COPY_TO_FOLDER -> {
                requiredParams.add(RuleAction.ParamKeys.FOLDER_ID);
                optionalParams.add(RuleAction.ParamKeys.NEW_NAME);
            }
            case SET_METADATA -> {
                requiredParams.add(RuleAction.ParamKeys.KEY);
                optionalParams.add(RuleAction.ParamKeys.VALUE);
            }
            case REMOVE_METADATA -> requiredParams.add(RuleAction.ParamKeys.KEY);
            case RENAME -> {
                optionalParams.add(RuleAction.ParamKeys.NEW_NAME);
                optionalParams.add(RuleAction.ParamKeys.PATTERN);
                constraints.add("atLeastOneOf:newName,pattern");
            }
            case START_WORKFLOW -> {
                requiredParams.add(RuleAction.ParamKeys.WORKFLOW_KEY);
                optionalParams.add(RuleAction.ParamKeys.VARIABLES);
                constraints.add("workflowKey=documentApproval requires approvers");
            }
            case SEND_NOTIFICATION -> {
                requiredParams.add(RuleAction.ParamKeys.RECIPIENT);
                requiredParams.add(RuleAction.ParamKeys.MESSAGE);
                optionalParams.add(RuleAction.ParamKeys.NOTIFICATION_TYPE);
            }
            case WEBHOOK -> {
                requiredParams.add(RuleAction.ParamKeys.URL);
                optionalParams.add(RuleAction.ParamKeys.METHOD);
                optionalParams.add(RuleAction.ParamKeys.HEADERS);
                optionalParams.add(RuleAction.ParamKeys.BODY);
            }
            case SET_STATUS -> requiredParams.add(RuleAction.ParamKeys.STATUS);
            case LOCK_DOCUMENT -> {
                // no params
            }
            case EXECUTE_SCRIPT -> {
                requiredParams.add(RuleAction.ParamKeys.OUTPUT_PROPERTY);
                optionalParams.add(RuleAction.ParamKeys.SCRIPT_PATH);
                optionalParams.add(RuleAction.ParamKeys.SCRIPT);
                optionalParams.add(RuleAction.ParamKeys.TIMEOUT_MS);
                optionalParams.add(RuleAction.ParamKeys.LANGUAGE);
                constraints.add("atLeastOneOf:scriptPath,script");
                constraints.add("adminOnly");
            }
            case RENDER_TEMPLATE -> {
                requiredParams.add(RuleAction.ParamKeys.OUTPUT_PROPERTY);
                optionalParams.add(RuleAction.ParamKeys.TEMPLATE_PATH);
                optionalParams.add(RuleAction.ParamKeys.TEMPLATE);
                constraints.add("atLeastOneOf:templatePath,template");
                constraints.add("adminOnly");
            }
        }

        return new RuleActionDefinition(
            actionType.name(),
            true,
            requiredParams,
            optionalParams,
            constraints
        );
    }

    private LocalDateTime parseDateTimeFilter(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException ex) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid datetime for " + fieldName + ": " + value
                );
            }
        }
    }

    private String buildRuleExecutionCsv(List<RuleRunRecordResponse> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("runId,ruleId,ruleName,documentId,documentName,triggerType,executedBy,idempotencyKey,conditionMatched,success,successfulActions,failedActions,totalActions,errorMessage,startedAt,completedAt,durationMs").append('\n');
        for (RuleRunRecordResponse run : rows) {
            sb.append(csvEscape(run.runId())).append(',')
                .append(csvEscape(run.ruleId())).append(',')
                .append(csvEscape(run.ruleName())).append(',')
                .append(csvEscape(run.documentId())).append(',')
                .append(csvEscape(run.documentName())).append(',')
                .append(csvEscape(run.triggerType())).append(',')
                .append(csvEscape(run.executedBy())).append(',')
                .append(csvEscape(run.idempotencyKey())).append(',')
                .append(run.conditionMatched()).append(',')
                .append(run.success()).append(',')
                .append(run.successfulActions()).append(',')
                .append(run.failedActions()).append(',')
                .append(run.totalActions()).append(',')
                .append(csvEscape(run.errorMessage())).append(',')
                .append(csvEscape(run.startedAt())).append(',')
                .append(csvEscape(run.completedAt())).append(',')
                .append(run.durationMs() != null ? run.durationMs() : "")
                .append('\n');
        }
        return sb.toString();
    }

    private String buildRuleAuditTimelineCsv(List<RuleAuditTimelineItemResponse> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,eventType,nodeId,nodeName,username,eventTime,details").append('\n');
        for (RuleAuditTimelineItemResponse row : rows) {
            sb.append(csvEscape(row.id())).append(',')
                .append(csvEscape(row.eventType())).append(',')
                .append(csvEscape(row.nodeId())).append(',')
                .append(csvEscape(row.nodeName())).append(',')
                .append(csvEscape(row.username())).append(',')
                .append(csvEscape(row.eventTime())).append(',')
                .append(csvEscape(row.details()))
                .append('\n');
        }
        return sb.toString();
    }

    private String csvEscape(Object value) {
        if (value == null) {
            return "";
        }
        String raw = value.toString();
        if (raw.contains(",") || raw.contains("\"") || raw.contains("\n") || raw.contains("\r")) {
            return "\"" + raw.replace("\"", "\"\"") + "\"";
        }
        return raw;
    }

    private RuleRunRecordResponse toRuleRunRecordResponse(RuleEngineService.RuleRunLedgerRecord run) {
        return new RuleRunRecordResponse(
            run.runId(),
            run.ruleId(),
            run.ruleName(),
            run.documentId(),
            run.documentName(),
            run.triggerType(),
            run.idempotencyKey(),
            run.executedBy(),
            run.conditionMatched(),
            run.success(),
            run.successfulActions(),
            run.failedActions(),
            run.totalActions(),
            run.errorMessage(),
            run.startedAt(),
            run.completedAt(),
            run.durationMs(),
            run.actions().stream()
                .map(action -> new RuleRunActionRecordResponse(
                    action.actionType(),
                    action.success(),
                    action.errorMessage(),
                    action.durationMs(),
                    action.details()
                ))
                .toList()
        );
    }

    private RuleAuditTimelineItemResponse toRuleAuditTimelineItemResponse(AuditLog auditLog) {
        return new RuleAuditTimelineItemResponse(
            auditLog.getId(),
            auditLog.getEventType(),
            auditLog.getNodeId(),
            auditLog.getNodeName(),
            auditLog.getUsername(),
            auditLog.getEventTime(),
            auditLog.getDetails()
        );
    }

    // ==================== DTOs ====================

    public record RuleActionDefinitionsResponse(
        List<RuleActionDefinition> actions
    ) {}

    public record FolderRuleReorderRequest(
        List<UUID> ruleIds,
        Integer basePriority,
        Integer step
    ) {}

    public record FolderRuleReorderResponse(
        UUID scopeFolderId,
        int updated,
        List<RuleResponse> rules
    ) {}

    public record FolderRuleDryRunRequestDto(
        TriggerType triggerType,
        Map<String, Object> testData,
        Integer limit
    ) {}

    public record FolderRuleDryRunResponse(
        UUID scopeFolderId,
        TriggerType triggerType,
        int found,
        int scanned,
        int matched,
        int processable,
        int skipped,
        int errors,
        Map<String, Long> skipReasons,
        List<FolderRuleDryRunItemResponse> results
    ) {}

    public record FolderRuleDryRunItemResponse(
        UUID ruleId,
        String ruleName,
        Integer priority,
        boolean matched,
        boolean processable,
        String skipReason,
        List<String> unsupportedActions,
        String error
    ) {}

    public record RuleExecutionCommandRequest(
        UUID documentId,
        TriggerType triggerType,
        String idempotencyKey
    ) {}

    public record RuleExecutionCommandResponse(
        UUID runId,
        boolean deduplicated,
        UUID deduplicatedFromRunId,
        RuleRunRecordResponse run
    ) {}

    public record RuleRunRecordResponse(
        UUID runId,
        UUID ruleId,
        String ruleName,
        UUID documentId,
        String documentName,
        TriggerType triggerType,
        String idempotencyKey,
        String executedBy,
        boolean conditionMatched,
        boolean success,
        int successfulActions,
        int failedActions,
        int totalActions,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Long durationMs,
        List<RuleRunActionRecordResponse> actions
    ) {}

    public record RuleRunActionRecordResponse(
        String actionType,
        boolean success,
        String errorMessage,
        Long durationMs,
        String details
    ) {}

    public record RuleAuditTimelineItemResponse(
        UUID id,
        String eventType,
        UUID nodeId,
        String nodeName,
        String username,
        LocalDateTime eventTime,
        String details
    ) {}

    public record RuleActionDefinition(
        String type,
        boolean supported,
        List<String> requiredParams,
        List<String> optionalParams,
        List<String> constraints
    ) {}

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
