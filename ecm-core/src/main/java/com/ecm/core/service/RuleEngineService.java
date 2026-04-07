package com.ecm.core.service;

import com.ecm.core.entity.*;
import com.ecm.core.entity.AutomationRule.TriggerType;
import com.ecm.core.entity.RuleAction.ActionType;
import com.ecm.core.entity.RuleCondition.ConditionType;
import com.ecm.core.entity.RuleExecutionResult.ActionExecutionResult;
import com.ecm.core.model.Category;
import com.ecm.core.model.Tag;
import com.ecm.core.repository.*;
import com.ecm.core.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Rule Engine Service
 *
 * Core service for automation rule management and execution.
 *
 * Features:
 * - Evaluate rule conditions against documents
 * - Execute rule actions
 * - Support complex condition combinations (AND, OR, NOT)
 * - Graceful error handling for action failures
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngineService {

    private static final int MIN_MANUAL_BACKFILL_MINUTES = 1;
    private static final int MAX_MANUAL_BACKFILL_MINUTES = 1440;
    private static final int RULE_RUN_LEDGER_LIMIT = 1000;

    private final AutomationRuleRepository ruleRepository;
    private final NodeRepository nodeRepository;
    private final TagRepository tagRepository;
    private final CategoryRepository categoryRepository;
    private final FolderRepository folderRepository;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;

    @Autowired
    private TagService tagService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private org.flowable.engine.RuntimeService runtimeService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ScriptService scriptService;

    @Autowired
    private TemplateService templateService;

    private final Map<UUID, RuleRunLedgerRecord> ruleRunLedgerById = new ConcurrentHashMap<>();
    private final Deque<UUID> ruleRunLedgerOrder = new ConcurrentLinkedDeque<>();
    private final Map<String, UUID> ruleRunIdempotencyIndex = new ConcurrentHashMap<>();

    // ==================== Rule Management ====================

    /**
     * Create a new automation rule
     */
    @Transactional
    public AutomationRule createRule(CreateRuleRequest request) {
        // Check for duplicate name
        if (ruleRepository.findByName(request.getName()).isPresent()) {
            throw new DuplicateResourceException("Rule with name already exists: " + request.getName());
        }
        validateManualBackfillMinutes(request.getManualBackfillMinutes());
        validateActionAuthoringPermissions(request.getActions());

        AutomationRule rule = AutomationRule.builder()
            .name(request.getName())
            .description(request.getDescription())
            .triggerType(request.getTriggerType())
            .condition(request.getCondition())
            .actions(request.getActions())
            .priority(request.getPriority() != null ? request.getPriority() : 100)
            .enabled(request.getEnabled() != null ? request.getEnabled() : true)
            .owner(request.getOwner())
            .scopeFolderId(resolveRequestedScopeFolderId(request.getScopeFolderId()))
            .scopeMimeTypes(request.getScopeMimeTypes())
            .stopOnMatch(request.getStopOnMatch() != null ? request.getStopOnMatch() : false)
            // Scheduled rule fields
            .cronExpression(request.getCronExpression())
            .timezone(request.getTimezone() != null ? request.getTimezone() : "UTC")
            .maxItemsPerRun(request.getMaxItemsPerRun() != null ? request.getMaxItemsPerRun() : 200)
            .manualBackfillMinutes(request.getManualBackfillMinutes())
            .build();

        AutomationRule saved = ruleRepository.save(rule);
        log.info("Created automation rule: {} ({})", saved.getName(), saved.getId());

        return saved;
    }

    /**
     * Update an existing rule
     */
    @Transactional
    public AutomationRule updateRule(UUID ruleId, UpdateRuleRequest request) {
        AutomationRule rule = getVisibleRule(ruleId);

        // Check for duplicate name (excluding current rule)
        if (request.getName() != null && !request.getName().equals(rule.getName())) {
            if (ruleRepository.existsByNameExcludingId(request.getName(), ruleId)) {
                throw new DuplicateResourceException("Rule with name already exists: " + request.getName());
            }
            rule.setName(request.getName());
        }

        if (request.getDescription() != null) {
            rule.setDescription(request.getDescription());
        }
        if (request.getTriggerType() != null) {
            rule.setTriggerType(request.getTriggerType());
        }
        if (request.getCondition() != null) {
            rule.setCondition(request.getCondition());
        }
        if (request.getActions() != null) {
            validateActionAuthoringPermissions(request.getActions());
            rule.setActions(request.getActions());
        }
        if (request.getPriority() != null) {
            rule.setPriority(request.getPriority());
        }
        if (request.getEnabled() != null) {
            rule.setEnabled(request.getEnabled());
        }
        if (request.getScopeFolderId() != null) {
            rule.setScopeFolderId(resolveRequestedScopeFolderId(request.getScopeFolderId()));
        }
        if (request.getScopeMimeTypes() != null) {
            rule.setScopeMimeTypes(request.getScopeMimeTypes());
        }
        if (request.getStopOnMatch() != null) {
            rule.setStopOnMatch(request.getStopOnMatch());
        }
        // Scheduled rule fields
        if (request.getCronExpression() != null) {
            rule.setCronExpression(request.getCronExpression());
        }
        if (request.getTimezone() != null) {
            rule.setTimezone(request.getTimezone());
        }
        if (request.getMaxItemsPerRun() != null) {
            rule.setMaxItemsPerRun(request.getMaxItemsPerRun());
        }
        if (request.getManualBackfillMinutes() != null) {
            validateManualBackfillMinutes(request.getManualBackfillMinutes());
            rule.setManualBackfillMinutes(request.getManualBackfillMinutes());
        }

        return ruleRepository.save(rule);
    }

    /**
     * Delete a rule
     */
    @Transactional
    public void deleteRule(UUID ruleId) {
        AutomationRule rule = getVisibleRule(ruleId);

        rule.setDeleted(true);
        rule.setDeletedAt(LocalDateTime.now());
        ruleRepository.save(rule);

        log.info("Deleted automation rule: {} ({})", rule.getName(), ruleId);
    }

    /**
     * Enable or disable a rule
     */
    @Transactional
    public AutomationRule setRuleEnabled(UUID ruleId, boolean enabled) {
        getVisibleRule(ruleId);
        ruleRepository.updateEnabled(ruleId, enabled);
        return ruleRepository.findById(ruleId)
            .orElseThrow(() -> new ResourceNotFoundException("Rule not found: " + ruleId));
    }

    /**
     * Get a rule by ID
     */
    public AutomationRule getRule(UUID ruleId) {
        return getVisibleRule(ruleId);
    }

    /**
     * Get all rules (paged)
     */
    public Page<AutomationRule> getAllRules(Pageable pageable) {
        return filterVisibleRules(ruleRepository.findAllActive(Pageable.unpaged()), pageable);
    }

    /**
     * Get rules by owner
     */
    public Page<AutomationRule> getRulesByOwner(String owner, Pageable pageable) {
        return filterVisibleRules(ruleRepository.findByOwner(owner, Pageable.unpaged()), pageable);
    }

    /**
     * Search rules
     */
    public Page<AutomationRule> searchRules(String query, Pageable pageable) {
        return filterVisibleRules(ruleRepository.searchRules(query, Pageable.unpaged()), pageable);
    }

    /**
     * Get rules scoped to a specific folder.
     */
    public Page<AutomationRule> getRulesByScopeFolder(UUID scopeFolderId, Pageable pageable) {
        assertScopeFolderVisible(scopeFolderId);
        return ruleRepository.findByScopeFolderIdActive(scopeFolderId, pageable);
    }

    /**
     * Reorder rules scoped to a specific folder.
     * When request.ruleIds is partial, remaining rules keep their relative order and are appended.
     */
    @Transactional
    public List<AutomationRule> reorderRulesByScopeFolder(
        UUID scopeFolderId,
        FolderRuleReorderRequest request
    ) {
        assertScopeFolderVisible(scopeFolderId);
        List<AutomationRule> scopedRules = ruleRepository.findByScopeFolderIdActiveOrderByPriority(scopeFolderId);
        if (scopedRules.isEmpty()) {
            return List.of();
        }

        int step = request != null && request.step() != null ? request.step() : 10;
        int basePriority = request != null && request.basePriority() != null ? request.basePriority() : 100;
        if (step < 1) {
            throw new IllegalArgumentException("step must be >= 1");
        }

        Map<UUID, AutomationRule> scopedById = scopedRules.stream()
            .collect(Collectors.toMap(AutomationRule::getId, rule -> rule, (a, b) -> a, LinkedHashMap::new));

        List<UUID> requestedIds = request != null && request.ruleIds() != null ? request.ruleIds() : List.of();
        Set<UUID> seen = new HashSet<>();
        List<AutomationRule> ordered = new ArrayList<>();

        for (UUID ruleId : requestedIds) {
            if (ruleId == null || !seen.add(ruleId)) {
                continue;
            }
            AutomationRule scopedRule = scopedById.remove(ruleId);
            if (scopedRule == null) {
                throw new IllegalArgumentException("Rule does not belong to scope folder: " + ruleId);
            }
            ordered.add(scopedRule);
        }

        for (AutomationRule scopedRule : scopedRules) {
            if (scopedById.containsKey(scopedRule.getId())) {
                ordered.add(scopedRule);
            }
        }

        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setPriority(basePriority + (i * step));
        }

        ruleRepository.saveAll(ordered);
        return ordered;
    }

    /**
     * Dry-run rules scoped to a specific folder against synthetic test data.
     * Conditions are evaluated but actions are not executed.
     */
    public FolderRuleDryRunResult dryRunRulesByScopeFolder(
        UUID scopeFolderId,
        FolderRuleDryRunRequest request
    ) {
        assertScopeFolderVisible(scopeFolderId);
        TriggerType triggerType = request != null && request.triggerType() != null
            ? request.triggerType()
            : TriggerType.DOCUMENT_CREATED;
        int limit = request != null && request.limit() != null ? request.limit() : 200;
        if (limit < 1) {
            limit = 200;
        }
        if (limit > 1000) {
            limit = 1000;
        }

        List<AutomationRule> allScopedRules = ruleRepository.findByScopeFolderIdActiveOrderByPriority(scopeFolderId);
        List<AutomationRule> candidateRules = allScopedRules.stream()
            .filter(rule -> Boolean.TRUE.equals(rule.getEnabled()))
            .filter(rule -> rule.getTriggerType() == triggerType)
            .limit(limit)
            .toList();

        Document dryRunDocument = buildDryRunDocument(scopeFolderId, request != null ? request.testData() : null);

        int matched = 0;
        int processable = 0;
        int errors = 0;
        Map<String, Long> skipReasons = new LinkedHashMap<>();
        List<FolderRuleDryRunItem> results = new ArrayList<>();

        for (AutomationRule rule : candidateRules) {
            boolean mimeMatched = rule.isMimeTypeInScope(dryRunDocument.getMimeType());
            boolean conditionMatched = false;
            boolean evaluated = false;
            String evaluationError = null;

            if (mimeMatched) {
                try {
                    conditionMatched = evaluateCondition(rule.getCondition(), dryRunDocument);
                    evaluated = true;
                } catch (Exception ex) {
                    evaluationError = ex.getMessage();
                    errors++;
                }
            }

            List<String> unsupportedActions = rule.getActions() == null
                ? List.of()
                : rule.getActions().stream()
                    .map(RuleAction::getType)
                    .filter(Objects::nonNull)
                    .filter(this::isDryRunUnsupportedAction)
                    .map(Enum::name)
                    .distinct()
                    .toList();

            boolean isMatched = mimeMatched && evaluated && conditionMatched;
            boolean isProcessable = isMatched && unsupportedActions.isEmpty();
            if (isMatched) {
                matched++;
            }
            if (isProcessable) {
                processable++;
            }

            String skipReason = null;
            if (!isProcessable) {
                if (!mimeMatched) {
                    skipReason = "mime_out_of_scope";
                } else if (evaluationError != null) {
                    skipReason = "evaluation_error";
                } else if (!conditionMatched) {
                    skipReason = "condition_not_matched";
                } else if (!unsupportedActions.isEmpty()) {
                    skipReason = "contains_unsupported_action";
                } else {
                    skipReason = "not_processable";
                }
                skipReasons.merge(skipReason, 1L, Long::sum);
            }

            results.add(new FolderRuleDryRunItem(
                rule.getId(),
                rule.getName(),
                rule.getPriority(),
                isMatched,
                isProcessable,
                skipReason,
                unsupportedActions,
                evaluationError
            ));
        }

        int scanned = candidateRules.size();
        return new FolderRuleDryRunResult(
            scopeFolderId,
            triggerType,
            allScopedRules.size(),
            scanned,
            matched,
            processable,
            scanned - processable,
            errors,
            skipReasons,
            results
        );
    }

    /**
     * Execute a single rule manually with optional idempotency protection.
     */
    @Transactional
    public RuleExecutionCommandResult executeRuleManual(
        UUID ruleId,
        UUID documentId,
        TriggerType triggerType,
        String idempotencyKey
    ) {
        AutomationRule rule = getRule(ruleId);
        Node node = nodeService.getNode(documentId);
        if (!(node instanceof Document document)) {
            throw new IllegalArgumentException("Only document nodes can be used for manual rule execution");
        }

        TriggerType effectiveTrigger = triggerType != null ? triggerType : rule.getTriggerType();
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        String idempotencyIndexKey = buildIdempotencyIndexKey(ruleId, documentId, effectiveTrigger, normalizedIdempotencyKey);

        if (idempotencyIndexKey != null) {
            RuleExecutionCommandResult deduplicated = resolveDeduplicatedManualRun(idempotencyIndexKey);
            if (deduplicated != null) {
                return deduplicated;
            }
        }

        RuleExecutionResult result;
        if (!Boolean.TRUE.equals(rule.getEnabled())) {
            result = RuleExecutionResult.notMatched(rule, document, "Rule is disabled");
            result.setTriggerType(effectiveTrigger);
        } else if (rule.getTriggerType() != effectiveTrigger) {
            result = RuleExecutionResult.notMatched(rule, document, "Trigger type mismatch");
            result.setTriggerType(effectiveTrigger);
        } else if (!isDocumentInRuleScope(rule, document)) {
            result = RuleExecutionResult.notMatched(rule, document, "Document not in scoped folder");
            result.setTriggerType(effectiveTrigger);
        } else if (!rule.isMimeTypeInScope(document.getMimeType())) {
            result = RuleExecutionResult.notMatched(rule, document, "MIME type out of scope");
            result.setTriggerType(effectiveTrigger);
        } else {
            result = executeRule(rule, document, effectiveTrigger);
        }

        if (result.getExecutionId() == null) {
            result.setExecutionId(UUID.randomUUID());
        }
        if (result.getDurationMs() == null) {
            result.calculateDuration();
        }

        String username = securityService.getCurrentUser();
        String effectiveUsername = username != null ? username : "system";
        RuleRunLedgerRecord runRecord = toRuleRunLedgerRecord(result, normalizedIdempotencyKey, effectiveUsername);
        saveRuleRunLedger(runRecord, idempotencyIndexKey);

        auditService.logEvent(
            "RULE_MANUAL_RUN_EXECUTED",
            runRecord.documentId(),
            runRecord.documentName(),
            effectiveUsername,
            String.format(
                "Rule manual run %s for rule %s on trigger %s (matched=%s, success=%s, idempotencyKey=%s)",
                runRecord.runId(),
                runRecord.ruleName(),
                runRecord.triggerType(),
                runRecord.conditionMatched(),
                runRecord.success(),
                runRecord.idempotencyKey() != null ? runRecord.idempotencyKey() : "-"
            )
        );

        return new RuleExecutionCommandResult(
            runRecord.runId(),
            false,
            null,
            runRecord
        );
    }

    /**
     * List recent manual rule run ledger records.
     */
    public List<RuleRunLedgerRecord> listRuleRuns(UUID ruleId, int limit) {
        return listRuleRuns(new RuleRunTimelineQuery(
            ruleId,
            null,
            null,
            null,
            null,
            null,
            null,
            limit
        ));
    }

    /**
     * List recent manual rule run ledger records with timeline filters.
     */
    public List<RuleRunLedgerRecord> listRuleRuns(RuleRunTimelineQuery query) {
        RuleRunTimelineQuery safeQuery = query != null
            ? query
            : new RuleRunTimelineQuery(null, null, null, null, null, null, null, 20);
        int boundedLimit = Math.max(1, Math.min(safeQuery.limit(), 500));
        String normalizedActor = safeQuery.actor() != null ? safeQuery.actor().trim().toLowerCase(Locale.ROOT) : null;
        if (normalizedActor != null && normalizedActor.isEmpty()) {
            normalizedActor = null;
        }
        List<RuleRunLedgerRecord> results = new ArrayList<>();
        for (UUID runId : ruleRunLedgerOrder) {
            RuleRunLedgerRecord record = ruleRunLedgerById.get(runId);
            if (record == null) {
                continue;
            }
            if (!isRuleRunVisible(record)) {
                continue;
            }
            if (safeQuery.ruleId() != null && !safeQuery.ruleId().equals(record.ruleId())) {
                continue;
            }
            if (safeQuery.documentId() != null && !safeQuery.documentId().equals(record.documentId())) {
                continue;
            }
            if (safeQuery.triggerType() != null && safeQuery.triggerType() != record.triggerType()) {
                continue;
            }
            if (safeQuery.success() != null && safeQuery.success().booleanValue() != record.success()) {
                continue;
            }
            if (normalizedActor != null) {
                String executedBy = record.executedBy();
                if (executedBy == null || !executedBy.toLowerCase(Locale.ROOT).contains(normalizedActor)) {
                    continue;
                }
            }
            LocalDateTime anchorTime = record.startedAt() != null ? record.startedAt() : record.completedAt();
            if (safeQuery.from() != null && (anchorTime == null || anchorTime.isBefore(safeQuery.from()))) {
                continue;
            }
            if (safeQuery.to() != null && (anchorTime == null || anchorTime.isAfter(safeQuery.to()))) {
                continue;
            }
            results.add(record);
            if (results.size() >= boundedLimit) {
                break;
            }
        }
        return results;
    }

    /**
     * Get a single manual rule run ledger record.
     */
    public Optional<RuleRunLedgerRecord> getRuleRun(UUID runId) {
        RuleRunLedgerRecord record = ruleRunLedgerById.get(runId);
        if (record == null || !isRuleRunVisible(record)) {
            return Optional.empty();
        }
        return Optional.of(record);
    }

    // ==================== Rule Execution ====================

    /**
     * Evaluate and execute all applicable rules for a document
     */
    @Transactional
    public List<RuleExecutionResult> evaluateAndExecute(Document document, TriggerType trigger) {
        UUID folderId = document.getParent() != null ? document.getParent().getId() : null;

        // Get applicable rules
        List<AutomationRule> rules = ruleRepository.findByTriggerTypeAndEnabledTrueWithScope(trigger, folderId);

        return evaluateAndExecute(document, trigger, rules);
    }

    /**
     * Evaluate and execute specific rules for a document (used by scheduled runner)
     */
    @Transactional
    public List<RuleExecutionResult> evaluateAndExecute(Document document, TriggerType trigger, List<AutomationRule> rules) {
        List<AutomationRule> sortedRules = rules != null ? new ArrayList<>(rules) : new ArrayList<>();
        sortedRules.sort(Comparator.comparingInt(r -> r.getPriority() != null ? r.getPriority() : 100));

        List<RuleExecutionResult> results = new ArrayList<>();

        for (AutomationRule rule : sortedRules) {
            // Check MIME type scope
            if (!rule.isMimeTypeInScope(document.getMimeType())) {
                continue;
            }

            RuleExecutionResult result = executeRule(rule, document, trigger);
            results.add(result);

            // Stop processing if rule matched and stopOnMatch is true
            if (result.isConditionMatched() && Boolean.TRUE.equals(rule.getStopOnMatch())) {
                log.debug("Rule '{}' matched with stopOnMatch=true, stopping rule chain", rule.getName());
                break;
            }
        }

        return results;
    }

    /**
     * Execute a single rule on a document
     */
    @Transactional
    public RuleExecutionResult executeRule(AutomationRule rule, Document document, TriggerType trigger) {
        LocalDateTime startTime = LocalDateTime.now();

        try {
            // Evaluate condition
            boolean matches = evaluateCondition(rule.getCondition(), document);

            if (!matches) {
                return RuleExecutionResult.notMatched(rule, document, "Condition not satisfied");
            }

            log.info("Rule '{}' matched for document {} ({})",
                rule.getName(), document.getName(), document.getId());

            // Execute actions
            RuleExecutionResult result = RuleExecutionResult.builder()
                .executionId(UUID.randomUUID())
                .rule(rule)
                .documentId(document.getId())
                .documentName(document.getName())
                .conditionMatched(true)
                .triggerType(trigger)
                .startTime(startTime)
                .actionResults(new ArrayList<>())
                .build();

            boolean allActionsSucceeded = true;

            List<RuleAction> sortedActions = rule.getActions().stream()
                .sorted(Comparator.comparingInt(a -> a.getOrder() != null ? a.getOrder() : 0))
                .collect(Collectors.toList());

            for (RuleAction action : sortedActions) {
                ActionExecutionResult actionResult = executeAction(action, document);
                result.addActionResult(actionResult);

                if (!actionResult.isSuccess()) {
                    allActionsSucceeded = false;

                    // Stop if action should not continue on error
                    if (!Boolean.TRUE.equals(action.getContinueOnError())) {
                        result.setErrorMessage("Action " + action.getType() + " failed: " + actionResult.getErrorMessage());
                        break;
                    }
                }
            }

            result.setSuccess(allActionsSucceeded || result.getSuccessfulActionCount() > 0);
            result.setEndTime(LocalDateTime.now());
            result.calculateDuration();

            // Update rule statistics
            ruleRepository.incrementExecutionCount(rule.getId());
            if (!allActionsSucceeded) {
                ruleRepository.incrementFailureCount(rule.getId());
            }

            // Audit log: record rule execution summary (not per-action to avoid log explosion)
            try {
                String username = securityService.getCurrentUser();
                auditService.logRuleExecution(result, username != null ? username : "system");
            } catch (Exception e) {
                log.warn("Failed to write rule execution audit log: {}", e.getMessage());
            }

            return result;

        } catch (Exception e) {
            log.error("Rule '{}' execution failed for document {}",
                rule.getName(), document.getId(), e);

            ruleRepository.incrementExecutionCount(rule.getId());
            ruleRepository.incrementFailureCount(rule.getId());

            RuleExecutionResult failedResult = RuleExecutionResult.failed(rule, document, e.getMessage());

            // Audit log: record failed rule execution
            try {
                String username = securityService.getCurrentUser();
                auditService.logRuleExecution(failedResult, username != null ? username : "system");
            } catch (Exception ae) {
                log.warn("Failed to write rule execution audit log: {}", ae.getMessage());
            }

            return failedResult;
        }
    }

    private boolean isDryRunUnsupportedAction(ActionType actionType) {
        return false;
    }

    private void validateActionAuthoringPermissions(List<RuleAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        boolean containsAdminOnlyAction = actions.stream()
            .map(RuleAction::getType)
            .filter(Objects::nonNull)
            .anyMatch(this::isAdminOnlyAuthoringAction);
        if (containsAdminOnlyAction && !securityService.hasRole("ROLE_ADMIN")) {
            throw new AccessDeniedException("Only administrators can configure template or script rule actions");
        }
    }

    private boolean isAdminOnlyAuthoringAction(ActionType actionType) {
        return actionType == ActionType.EXECUTE_SCRIPT || actionType == ActionType.RENDER_TEMPLATE;
    }

    // ==================== Condition Evaluation ====================

    /**
     * Evaluate a rule condition against a document
     */
    public boolean evaluateCondition(RuleCondition condition, Document document) {
        if (condition == null) {
            return true; // No condition means always match
        }

        ConditionType type = condition.getType();
        if (type == null) {
            type = ConditionType.ALWAYS_TRUE;
        }

        return switch (type) {
            case SIMPLE -> evaluateSimpleCondition(condition, document);
            case AND -> evaluateAndCondition(condition, document);
            case OR -> evaluateOrCondition(condition, document);
            case NOT -> evaluateNotCondition(condition, document);
            case ALWAYS_TRUE -> true;
            case ALWAYS_FALSE -> false;
        };
    }

    private boolean evaluateAndCondition(RuleCondition condition, Document document) {
        if (condition.getChildren() == null || condition.getChildren().isEmpty()) {
            return true;
        }
        return condition.getChildren().stream()
            .allMatch(child -> evaluateCondition(child, document));
    }

    private boolean evaluateOrCondition(RuleCondition condition, Document document) {
        if (condition.getChildren() == null || condition.getChildren().isEmpty()) {
            return false;
        }
        return condition.getChildren().stream()
            .anyMatch(child -> evaluateCondition(child, document));
    }

    private boolean evaluateNotCondition(RuleCondition condition, Document document) {
        if (condition.getChildren() == null || condition.getChildren().isEmpty()) {
            return true;
        }
        return !evaluateCondition(condition.getChildren().get(0), document);
    }

    private boolean evaluateSimpleCondition(RuleCondition condition, Document document) {
        Object fieldValue = getFieldValue(document, condition.getField());
        Object targetValue = condition.getValue();
        String operator = condition.getOperator();
        boolean ignoreCase = Boolean.TRUE.equals(condition.getIgnoreCase());

        if (operator == null) {
            operator = "equals";
        }

        return switch (operator) {
            case "equals" -> compareEquals(fieldValue, targetValue, ignoreCase);
            case "notEquals" -> !compareEquals(fieldValue, targetValue, ignoreCase);
            case "contains" -> compareContains(fieldValue, targetValue, ignoreCase);
            case "notContains" -> !compareContains(fieldValue, targetValue, ignoreCase);
            case "startsWith" -> compareStartsWith(fieldValue, targetValue, ignoreCase);
            case "endsWith" -> compareEndsWith(fieldValue, targetValue, ignoreCase);
            case "regex" -> compareRegex(fieldValue, targetValue);
            case "gt" -> compareNumbers(fieldValue, targetValue) > 0;
            case "gte" -> compareNumbers(fieldValue, targetValue) >= 0;
            case "lt" -> compareNumbers(fieldValue, targetValue) < 0;
            case "lte" -> compareNumbers(fieldValue, targetValue) <= 0;
            case "in" -> compareIn(fieldValue, targetValue);
            case "notIn" -> !compareIn(fieldValue, targetValue);
            case "isNull" -> fieldValue == null;
            case "isNotNull" -> fieldValue != null;
            case "isEmpty" -> isEmpty(fieldValue);
            case "isNotEmpty" -> !isEmpty(fieldValue);
            default -> {
                log.warn("Unknown operator: {}", operator);
                yield false;
            }
        };
    }

    private Object getFieldValue(Document document, String field) {
        if (field == null) return null;

        // Handle metadata fields
        if (field.startsWith("metadata.")) {
            String metadataKey = field.substring(9);
            return document.getMetadata() != null ? document.getMetadata().get(metadataKey) : null;
        }

        return switch (field) {
            case "name" -> document.getName();
            case "description" -> document.getDescription();
            case "mimeType" -> document.getMimeType();
            case "size" -> document.getSize();
            case "content", "textContent" -> document.getTextContent();
            case "createdBy" -> document.getCreatedBy();
            case "path" -> document.getPath();
            case "parentId" -> document.getParent() != null ? document.getParent().getId().toString() : null;
            case "tags" -> document.getTags().stream().map(Tag::getName).collect(Collectors.toList());
            case "categories" -> document.getCategories().stream().map(Category::getName).collect(Collectors.toList());
            case "extension" -> getFileExtension(document.getName());
            default -> document.getMetadata() != null ? document.getMetadata().get(field) : null;
        };
    }

    private String getFileExtension(String filename) {
        if (filename == null) return null;
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }

    // ==================== Comparison Methods ====================

    private boolean compareEquals(Object fieldValue, Object targetValue, boolean ignoreCase) {
        if (fieldValue == null && targetValue == null) return true;
        if (fieldValue == null || targetValue == null) return false;

        if (ignoreCase && fieldValue instanceof String && targetValue instanceof String) {
            return ((String) fieldValue).equalsIgnoreCase((String) targetValue);
        }
        return Objects.equals(fieldValue, targetValue);
    }

    private boolean compareContains(Object fieldValue, Object targetValue, boolean ignoreCase) {
        if (fieldValue == null || targetValue == null) return false;

        String fieldStr = fieldValue.toString();
        String targetStr = targetValue.toString();

        if (ignoreCase) {
            return fieldStr.toLowerCase().contains(targetStr.toLowerCase());
        }
        return fieldStr.contains(targetStr);
    }

    private boolean compareStartsWith(Object fieldValue, Object targetValue, boolean ignoreCase) {
        if (fieldValue == null || targetValue == null) return false;

        String fieldStr = fieldValue.toString();
        String targetStr = targetValue.toString();

        if (ignoreCase) {
            return fieldStr.toLowerCase().startsWith(targetStr.toLowerCase());
        }
        return fieldStr.startsWith(targetStr);
    }

    private boolean compareEndsWith(Object fieldValue, Object targetValue, boolean ignoreCase) {
        if (fieldValue == null || targetValue == null) return false;

        String fieldStr = fieldValue.toString();
        String targetStr = targetValue.toString();

        if (ignoreCase) {
            return fieldStr.toLowerCase().endsWith(targetStr.toLowerCase());
        }
        return fieldStr.endsWith(targetStr);
    }

    private boolean compareRegex(Object fieldValue, Object targetValue) {
        if (fieldValue == null || targetValue == null) return false;

        try {
            Pattern pattern = Pattern.compile(targetValue.toString());
            return pattern.matcher(fieldValue.toString()).matches();
        } catch (PatternSyntaxException e) {
            log.warn("Invalid regex pattern: {}", targetValue);
            return false;
        }
    }

    private int compareNumbers(Object fieldValue, Object targetValue) {
        if (fieldValue == null || targetValue == null) return 0;

        try {
            double field = Double.parseDouble(fieldValue.toString());
            double target = Double.parseDouble(targetValue.toString());
            return Double.compare(field, target);
        } catch (NumberFormatException e) {
            log.warn("Cannot compare as numbers: {} vs {}", fieldValue, targetValue);
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean compareIn(Object fieldValue, Object targetValue) {
        if (fieldValue == null || targetValue == null) return false;

        Collection<?> targetList;
        if (targetValue instanceof Collection) {
            targetList = (Collection<?>) targetValue;
        } else if (targetValue instanceof String) {
            // Parse comma-separated values
            targetList = Arrays.asList(((String) targetValue).split(","));
        } else {
            targetList = List.of(targetValue);
        }

        // Handle collection field values (like tags)
        if (fieldValue instanceof Collection) {
            Collection<?> fieldCollection = (Collection<?>) fieldValue;
            return fieldCollection.stream()
                .anyMatch(item -> targetList.stream()
                    .anyMatch(target -> item.toString().equalsIgnoreCase(target.toString())));
        }

        return targetList.stream()
            .anyMatch(target -> fieldValue.toString().equalsIgnoreCase(target.toString()));
    }

    private boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String) return ((String) value).isEmpty();
        if (value instanceof Collection) return ((Collection<?>) value).isEmpty();
        return false;
    }

    // ==================== Action Execution ====================

    /**
     * Execute a single action
     */
    private ActionExecutionResult executeAction(RuleAction action, Document document) {
        long startTime = System.currentTimeMillis();

        try {
            String details = null;
            switch (action.getType()) {
                case ADD_TAG -> executeAddTag(action, document);
                case REMOVE_TAG -> executeRemoveTag(action, document);
                case SET_CATEGORY -> executeSetCategory(action, document);
                case REMOVE_CATEGORY -> executeRemoveCategory(action, document);
                case MOVE_TO_FOLDER -> executeMoveToFolder(action, document);
                case COPY_TO_FOLDER -> executeCopyToFolder(action, document);
                case SET_METADATA -> executeSetMetadata(action, document);
                case REMOVE_METADATA -> executeRemoveMetadata(action, document);
                case RENAME -> executeRename(action, document);
                case SET_STATUS -> executeSetStatus(action, document);
                case LOCK_DOCUMENT -> executeLockDocument(action, document);
                case SEND_NOTIFICATION -> executeSendNotification(action, document);
                case WEBHOOK -> executeWebhook(action, document);
                case START_WORKFLOW -> executeStartWorkflow(action, document);
                case EXECUTE_SCRIPT -> details = executeScriptAction(action, document);
                case RENDER_TEMPLATE -> details = executeTemplateAction(action, document);
                default -> throw new UnsupportedOperationException("Action type not supported: " + action.getType());
            }

            return ActionExecutionResult.builder()
                .actionType(action.getType())
                .success(true)
                .durationMs(System.currentTimeMillis() - startTime)
                .details(details)
                .build();

        } catch (Exception e) {
            log.error("Action {} failed for document {}: {}",
                action.getType(), document.getId(), e.getMessage());

            return ActionExecutionResult.builder()
                .actionType(action.getType())
                .success(false)
                .errorMessage(e.getMessage())
                .durationMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }

    private void executeAddTag(RuleAction action, Document document) {
        String tagName = action.getParam(RuleAction.ParamKeys.TAG_NAME);
        if (tagName == null) {
            throw new IllegalArgumentException("Tag name is required for ADD_TAG action");
        }
        tagService.addTagToNode(document.getId().toString(), tagName);
        log.debug("Added tag '{}' to document {}", tagName, document.getId());
    }

    private void executeRemoveTag(RuleAction action, Document document) {
        String tagName = action.getParam(RuleAction.ParamKeys.TAG_NAME);
        if (tagName == null) {
            throw new IllegalArgumentException("Tag name is required for REMOVE_TAG action");
        }
        try {
            tagService.removeTagFromNode(document.getId().toString(), tagName);
            log.debug("Removed tag '{}' from document {}", tagName, document.getId());
        } catch (ResourceNotFoundException e) {
            // Tag doesn't exist or isn't on document, ignore
            log.debug("Tag '{}' not found on document {}, skipping removal", tagName, document.getId());
        }
    }

    private void executeSetCategory(RuleAction action, Document document) {
        String categoryName = action.getParam(RuleAction.ParamKeys.CATEGORY_NAME);
        if (categoryName == null) {
            throw new IllegalArgumentException("Category name is required for SET_CATEGORY action");
        }

        // Find or create category
        Optional<Category> existing = categoryRepository.findByName(categoryName);
        Category category;
        if (existing.isPresent()) {
            category = existing.get();
        } else {
            category = categoryService.createCategory(categoryName, null, null);
        }

        document.getCategories().add(category);
        nodeRepository.save(document);
        log.debug("Set category '{}' on document {}", categoryName, document.getId());
    }

    private void executeRemoveCategory(RuleAction action, Document document) {
        String categoryName = action.getParam(RuleAction.ParamKeys.CATEGORY_NAME);
        if (categoryName == null) {
            throw new IllegalArgumentException("Category name is required for REMOVE_CATEGORY action");
        }

        document.getCategories().removeIf(c -> c.getName().equalsIgnoreCase(categoryName));
        nodeRepository.save(document);
        log.debug("Removed category '{}' from document {}", categoryName, document.getId());
    }

    private void executeMoveToFolder(RuleAction action, Document document) {
        String folderIdStr = action.getParam(RuleAction.ParamKeys.FOLDER_ID);
        if (folderIdStr == null) {
            throw new IllegalArgumentException("Folder ID is required for MOVE_TO_FOLDER action");
        }

        UUID folderId = UUID.fromString(folderIdStr);
        nodeService.moveNode(document.getId(), folderId);
        log.debug("Moved document {} to folder {}", document.getId(), folderId);
    }

    private void executeCopyToFolder(RuleAction action, Document document) {
        String folderIdStr = action.getParam(RuleAction.ParamKeys.FOLDER_ID);
        if (folderIdStr == null) {
            throw new IllegalArgumentException("Folder ID is required for COPY_TO_FOLDER action");
        }

        UUID folderId = UUID.fromString(folderIdStr);
        String newName = action.getParam(RuleAction.ParamKeys.NEW_NAME);

        nodeService.copyNode(document.getId(), folderId, newName, false);
        log.debug("Copied document {} to folder {}", document.getId(), folderId);
    }

    private void executeSetMetadata(RuleAction action, Document document) {
        String key = action.getParam(RuleAction.ParamKeys.KEY);
        Object value = action.getParam(RuleAction.ParamKeys.VALUE);

        if (key == null) {
            throw new IllegalArgumentException("Key is required for SET_METADATA action");
        }

        if (document.getMetadata() == null) {
            document.setMetadata(new HashMap<>());
        }
        document.getMetadata().put(key, value);
        nodeRepository.save(document);
        log.debug("Set metadata {}={} on document {}", key, value, document.getId());
    }

    private void executeRemoveMetadata(RuleAction action, Document document) {
        String key = action.getParam(RuleAction.ParamKeys.KEY);
        if (key == null) {
            throw new IllegalArgumentException("Key is required for REMOVE_METADATA action");
        }

        if (document.getMetadata() != null) {
            document.getMetadata().remove(key);
            nodeRepository.save(document);
        }
        log.debug("Removed metadata '{}' from document {}", key, document.getId());
    }

    private void executeRename(RuleAction action, Document document) {
        String newName = action.getParam(RuleAction.ParamKeys.NEW_NAME);
        String pattern = action.getParam(RuleAction.ParamKeys.PATTERN);

        if (newName == null && pattern == null) {
            throw new IllegalArgumentException("New name or pattern is required for RENAME action");
        }

        String finalName;
        if (pattern != null) {
            // Pattern-based rename (e.g., "{date}_{name}")
            finalName = applyNamingPattern(pattern, document);
        } else {
            finalName = newName;
        }

        document.setName(finalName);
        nodeRepository.save(document);
        log.debug("Renamed document {} to '{}'", document.getId(), finalName);
    }

    private String applyNamingPattern(String pattern, Document document) {
        String result = pattern;
        result = result.replace("{name}", document.getName());
        result = result.replace("{date}", LocalDateTime.now().toLocalDate().toString());
        result = result.replace("{datetime}", LocalDateTime.now().toString().replace(":", "-"));
        result = result.replace("{id}", document.getId().toString().substring(0, 8));
        result = result.replace("{ext}", getFileExtension(document.getName()));
        return result;
    }

    private void executeSetStatus(RuleAction action, Document document) {
        String statusStr = action.getParam(RuleAction.ParamKeys.STATUS);
        if (statusStr == null) {
            throw new IllegalArgumentException("Status is required for SET_STATUS action");
        }

        Node.NodeStatus status = Node.NodeStatus.valueOf(statusStr.toUpperCase());
        document.setStatus(status);
        nodeRepository.save(document);
        log.debug("Set status {} on document {}", status, document.getId());
    }

    private void executeLockDocument(RuleAction action, Document document) {
        document.setLocked(true);
        document.setLockedBy("system");
        document.setLockedDate(LocalDateTime.now());
        nodeRepository.save(document);
        log.debug("Locked document {}", document.getId());
    }

    private void executeSendNotification(RuleAction action, Document document) {
        String recipient = action.getParam(RuleAction.ParamKeys.RECIPIENT);
        String message = action.getParam(RuleAction.ParamKeys.MESSAGE);
        String type = action.getParam(RuleAction.ParamKeys.NOTIFICATION_TYPE);

        if (recipient == null || message == null) {
            throw new IllegalArgumentException("Recipient and message are required for SEND_NOTIFICATION action");
        }

        // Replace placeholders in message
        message = message.replace("{documentName}", document.getName());
        message = message.replace("{documentId}", document.getId().toString());

        String title = "Rule Notification";
        if (type != null && !type.isBlank()) {
            title = String.format("Rule Notification (%s)", type.trim());
        }

        notificationService.notifyUser(recipient, title, message);
        log.info("Notification to {}: {}", recipient, message);
    }

    @Autowired
    private com.ecm.core.integration.webhook.WebhookService webhookService;

    // ... (existing code)

    private void executeWebhook(RuleAction action, Document document) {
        String url = action.getParam(RuleAction.ParamKeys.URL);
        if (url == null) {
            throw new IllegalArgumentException("URL is required for WEBHOOK action");
        }
        
        String method = (String) action.getParams().getOrDefault("method", "POST");
        String body = (String) action.getParams().get("body");
        
        // Headers (stored as Map in params)
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) action.getParams().get("headers");

        webhookService.sendWebhook(url, method, body, headers, document);
        log.info("Webhook executed for document {}", document.getId());
    }

    private void executeStartWorkflow(RuleAction action, Document document) {
        String workflowKey = action.getParam(RuleAction.ParamKeys.WORKFLOW_KEY);
        if (workflowKey == null) {
            throw new IllegalArgumentException("Workflow key is required for START_WORKFLOW action");
        }

        try {
            // Special handling for documentApproval workflow
            if ("documentApproval".equals(workflowKey)) {
                // Get approvers from params (can be comma-separated string or list)
                Object approversParam = action.getParams().get("approvers");
                List<String> approvers = parseApprovers(approversParam);

                if (approvers.isEmpty()) {
                    throw new IllegalArgumentException("At least one approver is required for documentApproval workflow");
                }

                String comment = action.getParam("comment", "Auto-started by rule engine");
                workflowService.startDocumentApproval(document.getId(), approvers, comment);
                log.info("Started documentApproval workflow for document {} with approvers: {}",
                    document.getId(), approvers);
            } else {
                // Generic workflow start
                @SuppressWarnings("unchecked")
                Map<String, Object> variables = action.getParam(RuleAction.ParamKeys.VARIABLES, new HashMap<>());

                // Inject standard variables
                variables.put("documentId", document.getId().toString());
                variables.put("documentName", document.getName());
                variables.put("initiator", securityService.getCurrentUser());

                runtimeService.startProcessInstanceByKey(
                    workflowKey,
                    document.getId().toString(),
                    variables
                );
                log.info("Started workflow {} for document {} with variables: {}",
                    workflowKey, document.getId(), variables.keySet());
            }
        } catch (Exception e) {
            log.error("Failed to start workflow {} for document {}: {}",
                workflowKey, document.getId(), e.getMessage(), e);
            throw new RuntimeException("Workflow start failed: " + e.getMessage(), e);
        }
    }

    private String executeScriptAction(RuleAction action, Document document) {
        String outputProperty = normalizeOutputProperty(action.getParam(RuleAction.ParamKeys.OUTPUT_PROPERTY));
        ScriptService.ScriptExecutionResult execution = scriptService.executeScriptForAutomation(
            new ScriptService.ScriptExecutionRequest(
                normalizeOptionalText(action.getParam(RuleAction.ParamKeys.SCRIPT_PATH)),
                normalizeOptionalText(action.getParam(RuleAction.ParamKeys.SCRIPT)),
                buildAutomationModel(document, action),
                parseTimeoutMs(action.getParam(RuleAction.ParamKeys.TIMEOUT_MS))
            )
        );

        Map<String, Object> metadata = ensureMetadata(document);
        metadata.put(outputProperty, execution.result());
        if (execution.logs() != null && !execution.logs().isEmpty()) {
            metadata.put(outputProperty + "Logs", List.copyOf(execution.logs()));
        }
        nodeRepository.save(document);
        log.info("Executed rule script for document {} into metadata '{}'", document.getId(), outputProperty);

        return execution.storedScript()
            ? "Stored script " + execution.scriptPath() + " wrote metadata." + outputProperty
            : "Inline script wrote metadata." + outputProperty;
    }

    private String executeTemplateAction(RuleAction action, Document document) {
        String outputProperty = normalizeOutputProperty(action.getParam(RuleAction.ParamKeys.OUTPUT_PROPERTY));
        TemplateService.TemplateExecutionResult execution = templateService.executeTemplateForAutomation(
            new TemplateService.TemplateExecutionRequest(
                normalizeOptionalText(action.getParam(RuleAction.ParamKeys.TEMPLATE_PATH)),
                normalizeOptionalText(action.getParam(RuleAction.ParamKeys.TEMPLATE)),
                buildAutomationModel(document, action)
            )
        );

        Map<String, Object> metadata = ensureMetadata(document);
        metadata.put(outputProperty, execution.rendered());
        nodeRepository.save(document);
        log.info("Rendered rule template for document {} into metadata '{}'", document.getId(), outputProperty);

        return execution.storedTemplate()
            ? "Stored template " + execution.templatePath() + " wrote metadata." + outputProperty
            : "Inline template wrote metadata." + outputProperty;
    }

    private Map<String, Object> buildAutomationModel(Document document, RuleAction action) {
        Map<String, Object> model = new LinkedHashMap<>();
        Map<String, Object> documentModel = new LinkedHashMap<>();
        documentModel.put("id", document.getId() != null ? document.getId().toString() : null);
        documentModel.put("name", document.getName());
        documentModel.put("description", document.getDescription());
        documentModel.put("path", document.getPath());
        documentModel.put("mimeType", document.getMimeType());
        documentModel.put("size", document.getFileSize());
        documentModel.put("status", document.getStatus() != null ? document.getStatus().name() : null);
        documentModel.put("metadata", document.getMetadata() != null ? new LinkedHashMap<>(document.getMetadata()) : Map.of());
        documentModel.put("parentFolderId", document.getParent() != null && document.getParent().getId() != null
            ? document.getParent().getId().toString()
            : null);
        documentModel.put("parentPath", document.getParent() != null ? document.getParent().getPath() : null);

        model.put("document", documentModel);
        model.put("documentId", documentModel.get("id"));
        model.put("documentName", document.getName());
        model.put("documentPath", document.getPath());
        model.put("mimeType", document.getMimeType());
        model.put("metadata", documentModel.get("metadata"));
        model.put("actionParams", action.getParams() != null ? new LinkedHashMap<>(action.getParams()) : Map.of());
        model.put("executedAt", LocalDateTime.now().toString());
        return model;
    }

    private Map<String, Object> ensureMetadata(Document document) {
        if (document.getMetadata() == null) {
            document.setMetadata(new HashMap<>());
        }
        return document.getMetadata();
    }

    private String normalizeOutputProperty(String outputProperty) {
        if (outputProperty == null || outputProperty.isBlank()) {
            throw new IllegalArgumentException("Output property is required for template/script rule actions");
        }
        return outputProperty.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Long parseTimeoutMs(Object timeoutValue) {
        if (timeoutValue == null) {
            return null;
        }
        long timeoutMs;
        if (timeoutValue instanceof Number number) {
            timeoutMs = number.longValue();
        } else {
            try {
                timeoutMs = Long.parseLong(timeoutValue.toString().trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("timeoutMs must be a positive number");
            }
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be a positive number");
        }
        return timeoutMs;
    }

    /**
     * Parse approvers from various formats (string, list, comma-separated)
     */
    @SuppressWarnings("unchecked")
    private List<String> parseApprovers(Object approversParam) {
        if (approversParam == null) {
            return new ArrayList<>();
        }

        if (approversParam instanceof List) {
            return ((List<?>) approversParam).stream()
                .map(Object::toString)
                .collect(Collectors.toList());
        }

        if (approversParam instanceof String) {
            String str = (String) approversParam;
            if (str.contains(",")) {
                return Arrays.stream(str.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            } else {
                return str.isEmpty() ? new ArrayList<>() : List.of(str.trim());
            }
        }

        return new ArrayList<>();
    }

    private boolean isDocumentInRuleScope(AutomationRule rule, Document document) {
        UUID scopeFolderId = rule.getScopeFolderId();
        if (scopeFolderId == null) {
            return true;
        }
        if (document.getParent() == null) {
            return false;
        }
        return scopeFolderId.equals(document.getParent().getId());
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildIdempotencyIndexKey(
        UUID ruleId,
        UUID documentId,
        TriggerType triggerType,
        String normalizedIdempotencyKey
    ) {
        if (normalizedIdempotencyKey == null) {
            return null;
        }
        return ruleId + "|" + documentId + "|" + triggerType + "|" + normalizedIdempotencyKey;
    }

    private RuleExecutionCommandResult resolveDeduplicatedManualRun(String idempotencyIndexKey) {
        UUID existingRunId = ruleRunIdempotencyIndex.get(idempotencyIndexKey);
        if (existingRunId == null) {
            return null;
        }
        RuleRunLedgerRecord existing = ruleRunLedgerById.get(existingRunId);
        if (existing == null) {
            ruleRunIdempotencyIndex.remove(idempotencyIndexKey);
            return null;
        }
        String username = securityService.getCurrentUser();
        auditService.logEvent(
            "RULE_MANUAL_RUN_REUSED",
            existing.documentId(),
            existing.documentName(),
            username != null ? username : "system",
            String.format("Reused manual run %s by idempotency key", existing.runId())
        );
        return new RuleExecutionCommandResult(
            existing.runId(),
            true,
            existing.runId(),
            existing
        );
    }

    private void saveRuleRunLedger(RuleRunLedgerRecord record, String idempotencyIndexKey) {
        ruleRunLedgerById.put(record.runId(), record);
        ruleRunLedgerOrder.remove(record.runId());
        ruleRunLedgerOrder.addFirst(record.runId());
        if (idempotencyIndexKey != null) {
            ruleRunIdempotencyIndex.put(idempotencyIndexKey, record.runId());
        }

        while (ruleRunLedgerOrder.size() > RULE_RUN_LEDGER_LIMIT) {
            UUID tailRunId = ruleRunLedgerOrder.pollLast();
            if (tailRunId == null) {
                break;
            }
            ruleRunLedgerById.remove(tailRunId);
            ruleRunIdempotencyIndex.entrySet().removeIf(entry -> tailRunId.equals(entry.getValue()));
        }
    }

    private RuleRunLedgerRecord toRuleRunLedgerRecord(
        RuleExecutionResult result,
        String idempotencyKey,
        String executedBy
    ) {
        List<RuleRunActionRecord> actionRecords = result.getActionResults() == null
            ? List.of()
            : result.getActionResults().stream()
                .map(action -> new RuleRunActionRecord(
                    action.getActionType() != null ? action.getActionType().name() : null,
                    action.isSuccess(),
                    action.getErrorMessage(),
                    action.getDurationMs(),
                    action.getDetails()
                ))
                .toList();

        return new RuleRunLedgerRecord(
            result.getExecutionId(),
            result.getRule().getId(),
            result.getRule().getName(),
            result.getDocumentId(),
            result.getDocumentName(),
            result.getTriggerType(),
            idempotencyKey,
            executedBy,
            result.isConditionMatched(),
            result.isSuccess(),
            result.getSuccessfulActionCount(),
            result.getFailedActionCount(),
            result.getTotalActionCount(),
            result.getErrorMessage(),
            result.getStartTime(),
            result.getEndTime(),
            result.getDurationMs(),
            actionRecords
        );
    }

    private Document buildDryRunDocument(UUID scopeFolderId, Map<String, Object> testData) {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("dry-run-document");
        document.setMimeType("application/pdf");
        document.setFileSize(1024L);
        document.setPath("/dry-run-document");

        if (testData != null) {
            Object name = testData.get("name");
            if (name instanceof String value && !value.isBlank()) {
                document.setName(value);
            }
            Object mimeType = testData.get("mimeType");
            if (mimeType instanceof String value && !value.isBlank()) {
                document.setMimeType(value);
            }
            Object size = testData.get("size");
            if (size instanceof Number number) {
                document.setFileSize(number.longValue());
            }
            Object path = testData.get("path");
            if (path instanceof String value && !value.isBlank()) {
                document.setPath(value);
            }
        }

        if (scopeFolderId != null) {
            Folder parent = new Folder();
            parent.setId(scopeFolderId);
            document.setParent(parent);
        }
        return document;
    }

    private AutomationRule getVisibleRule(UUID ruleId) {
        AutomationRule rule = ruleRepository.findById(ruleId)
            .orElseThrow(() -> new ResourceNotFoundException("Rule not found: " + ruleId));
        if (!isRuleVisible(rule, tenantWorkspaceScopeService.resolveCurrentTenantRootPath())) {
            throw new ResourceNotFoundException("Rule not found: " + ruleId);
        }
        return rule;
    }

    private UUID resolveRequestedScopeFolderId(UUID requestedScopeFolderId) {
        if (!tenantWorkspaceScopeService.hasScopedTenantWorkspace()) {
            return requestedScopeFolderId;
        }
        UUID effectiveScopeFolderId = requestedScopeFolderId != null
            ? requestedScopeFolderId
            : tenantWorkspaceScopeService.resolveCurrentTenantRootNodeId();
        assertScopeFolderVisible(effectiveScopeFolderId);
        return effectiveScopeFolderId;
    }

    private void assertScopeFolderVisible(UUID scopeFolderId) {
        if (!tenantWorkspaceScopeService.hasScopedTenantWorkspace()) {
            return;
        }
        if (!tenantWorkspaceScopeService.isNodeVisible(scopeFolderId)) {
            throw new ResourceNotFoundException("Scope folder not found: " + scopeFolderId);
        }
    }

    private Page<AutomationRule> filterVisibleRules(Page<AutomationRule> source, Pageable pageable) {
        String tenantRootPath = tenantWorkspaceScopeService.resolveCurrentTenantRootPath();
        List<AutomationRule> visible = source.getContent().stream()
            .filter(rule -> isRuleVisible(rule, tenantRootPath))
            .toList();
        return sliceRules(visible, pageable);
    }

    private boolean isRuleVisible(AutomationRule rule, String tenantRootPath) {
        if (tenantRootPath == null) {
            return true;
        }
        if (tenantRootPath.isBlank() || rule == null || rule.getScopeFolderId() == null) {
            return false;
        }
        return tenantWorkspaceScopeService.isNodeVisible(rule.getScopeFolderId(), tenantRootPath);
    }

    private boolean isRuleRunVisible(RuleRunLedgerRecord record) {
        if (!tenantWorkspaceScopeService.hasScopedTenantWorkspace()) {
            return true;
        }
        if (record == null || record.documentId() == null || !tenantWorkspaceScopeService.isNodeVisible(record.documentId())) {
            return false;
        }
        if (record.ruleId() == null) {
            return true;
        }
        return ruleRepository.findById(record.ruleId())
            .map(rule -> isRuleVisible(rule, tenantWorkspaceScopeService.resolveCurrentTenantRootPath()))
            .orElse(false);
    }

    private Page<AutomationRule> sliceRules(List<AutomationRule> rules, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(rules);
        }
        int fromIndex = Math.toIntExact(pageable.getOffset());
        if (fromIndex >= rules.size()) {
            return new PageImpl<>(List.of(), pageable, rules.size());
        }
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), rules.size());
        return new PageImpl<>(rules.subList(fromIndex, toIndex), pageable, rules.size());
    }

    // ==================== DTO Classes ====================

    public record FolderRuleReorderRequest(
        List<UUID> ruleIds,
        Integer basePriority,
        Integer step
    ) {}

    public record FolderRuleDryRunRequest(
        TriggerType triggerType,
        Map<String, Object> testData,
        Integer limit
    ) {}

    public record FolderRuleDryRunResult(
        UUID scopeFolderId,
        TriggerType triggerType,
        int found,
        int scanned,
        int matched,
        int processable,
        int skipped,
        int errors,
        Map<String, Long> skipReasons,
        List<FolderRuleDryRunItem> results
    ) {}

    public record FolderRuleDryRunItem(
        UUID ruleId,
        String ruleName,
        Integer priority,
        boolean matched,
        boolean processable,
        String skipReason,
        List<String> unsupportedActions,
        String error
    ) {}

    public record RuleExecutionCommandResult(
        UUID runId,
        boolean deduplicated,
        UUID deduplicatedFromRunId,
        RuleRunLedgerRecord run
    ) {}

    public record RuleRunLedgerRecord(
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
        List<RuleRunActionRecord> actions
    ) {}

    public record RuleRunTimelineQuery(
        UUID ruleId,
        UUID documentId,
        TriggerType triggerType,
        Boolean success,
        String actor,
        LocalDateTime from,
        LocalDateTime to,
        int limit
    ) {}

    public record RuleRunActionRecord(
        String actionType,
        boolean success,
        String errorMessage,
        Long durationMs,
        String details
    ) {}

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreateRuleRequest {
        private String name;
        private String description;
        private TriggerType triggerType;
        private RuleCondition condition;
        private List<RuleAction> actions;
        private Integer priority;
        private Boolean enabled;
        private String owner;
        private UUID scopeFolderId;
        private String scopeMimeTypes;
        private Boolean stopOnMatch;
        // Scheduled rule fields
        private String cronExpression;
        private String timezone;
        private Integer maxItemsPerRun;
        private Integer manualBackfillMinutes;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UpdateRuleRequest {
        private String name;
        private String description;
        private TriggerType triggerType;
        private RuleCondition condition;
        private List<RuleAction> actions;
        private Integer priority;
        private Boolean enabled;
        private UUID scopeFolderId;
        private String scopeMimeTypes;
        private Boolean stopOnMatch;
        // Scheduled rule fields
        private String cronExpression;
        private String timezone;
        private Integer maxItemsPerRun;
        private Integer manualBackfillMinutes;
    }

    private void validateManualBackfillMinutes(Integer manualBackfillMinutes) {
        if (manualBackfillMinutes == null) {
            return;
        }
        if (manualBackfillMinutes < MIN_MANUAL_BACKFILL_MINUTES
            || manualBackfillMinutes > MAX_MANUAL_BACKFILL_MINUTES) {
            throw new IllegalArgumentException(
                "Manual backfill minutes must be between "
                    + MIN_MANUAL_BACKFILL_MINUTES
                    + " and "
                    + MAX_MANUAL_BACKFILL_MINUTES
            );
        }
    }
}
