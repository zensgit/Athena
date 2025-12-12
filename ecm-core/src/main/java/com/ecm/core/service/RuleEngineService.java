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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
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

    private final AutomationRuleRepository ruleRepository;
    private final NodeRepository nodeRepository;
    private final TagRepository tagRepository;
    private final CategoryRepository categoryRepository;
    private final FolderRepository folderRepository;

    @Autowired
    private TagService tagService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

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

        AutomationRule rule = AutomationRule.builder()
            .name(request.getName())
            .description(request.getDescription())
            .triggerType(request.getTriggerType())
            .condition(request.getCondition())
            .actions(request.getActions())
            .priority(request.getPriority() != null ? request.getPriority() : 100)
            .enabled(request.getEnabled() != null ? request.getEnabled() : true)
            .owner(request.getOwner())
            .scopeFolderId(request.getScopeFolderId())
            .scopeMimeTypes(request.getScopeMimeTypes())
            .stopOnMatch(request.getStopOnMatch() != null ? request.getStopOnMatch() : false)
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
        AutomationRule rule = ruleRepository.findById(ruleId)
            .orElseThrow(() -> new ResourceNotFoundException("Rule not found: " + ruleId));

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
            rule.setActions(request.getActions());
        }
        if (request.getPriority() != null) {
            rule.setPriority(request.getPriority());
        }
        if (request.getEnabled() != null) {
            rule.setEnabled(request.getEnabled());
        }
        if (request.getScopeFolderId() != null) {
            rule.setScopeFolderId(request.getScopeFolderId());
        }
        if (request.getScopeMimeTypes() != null) {
            rule.setScopeMimeTypes(request.getScopeMimeTypes());
        }
        if (request.getStopOnMatch() != null) {
            rule.setStopOnMatch(request.getStopOnMatch());
        }

        return ruleRepository.save(rule);
    }

    /**
     * Delete a rule
     */
    @Transactional
    public void deleteRule(UUID ruleId) {
        AutomationRule rule = ruleRepository.findById(ruleId)
            .orElseThrow(() -> new ResourceNotFoundException("Rule not found: " + ruleId));

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
        ruleRepository.updateEnabled(ruleId, enabled);
        return ruleRepository.findById(ruleId)
            .orElseThrow(() -> new ResourceNotFoundException("Rule not found: " + ruleId));
    }

    /**
     * Get a rule by ID
     */
    public AutomationRule getRule(UUID ruleId) {
        return ruleRepository.findById(ruleId)
            .orElseThrow(() -> new ResourceNotFoundException("Rule not found: " + ruleId));
    }

    /**
     * Get all rules (paged)
     */
    public Page<AutomationRule> getAllRules(Pageable pageable) {
        return ruleRepository.findAllActive(pageable);
    }

    /**
     * Get rules by owner
     */
    public Page<AutomationRule> getRulesByOwner(String owner, Pageable pageable) {
        return ruleRepository.findByOwner(owner, pageable);
    }

    /**
     * Search rules
     */
    public Page<AutomationRule> searchRules(String query, Pageable pageable) {
        return ruleRepository.searchRules(query, pageable);
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

        // Sort by priority
        rules.sort(Comparator.comparingInt(AutomationRule::getPriority));

        List<RuleExecutionResult> results = new ArrayList<>();

        for (AutomationRule rule : rules) {
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

            return result;

        } catch (Exception e) {
            log.error("Rule '{}' execution failed for document {}",
                rule.getName(), document.getId(), e);

            ruleRepository.incrementExecutionCount(rule.getId());
            ruleRepository.incrementFailureCount(rule.getId());

            return RuleExecutionResult.failed(rule, document, e.getMessage());
        }
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
                default -> throw new UnsupportedOperationException("Action type not supported: " + action.getType());
            }

            return ActionExecutionResult.builder()
                .actionType(action.getType())
                .success(true)
                .durationMs(System.currentTimeMillis() - startTime)
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

        if (recipient == null || message == null) {
            throw new IllegalArgumentException("Recipient and message are required for SEND_NOTIFICATION action");
        }

        // Replace placeholders in message
        message = message.replace("{documentName}", document.getName());
        message = message.replace("{documentId}", document.getId().toString());

        // TODO: Integrate with notification service
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

        // TODO: Integrate with workflow engine
        log.info("Start workflow {} for document {}", workflowKey, document.getId());
    }

    // ==================== DTO Classes ====================

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
    }
}
