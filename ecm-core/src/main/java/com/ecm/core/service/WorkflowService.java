package com.ecm.core.service;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.Node.NodeStatus;
import com.ecm.core.entity.User;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.DelegationState;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Workflow Service
 *
 * Manages business processes and integrates Flowable engine with ECM entities.
 */
@Slf4j
@Service("workflowService") // Explicitly named for BPMN expressions
@RequiredArgsConstructor
public class WorkflowService {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final RepositoryService repositoryService;
    private final HistoryService historyService;
    private final NodeRepository nodeRepository;
    private final UserRepository userRepository;
    private final SecurityService securityService;
    private final AuditService auditService;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;

    /**
     * Start document approval workflow
     */
    @Transactional
    public ProcessInstance startDocumentApproval(UUID documentId, List<String> approvers, String comment) {
        assertNodeVisible(documentId, "Node not found: " + documentId);
        Node node = nodeRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Node not found: " + documentId));

        if (approvers == null || approvers.isEmpty()) {
            throw new IllegalArgumentException("At least one approver is required");
        }

        String currentUser = securityService.getCurrentUser();
        String normalizedComment = normalizeOptionalString(comment);
        Map<String, Object> variables = new HashMap<>();
        variables.put("documentId", documentId.toString());
        variables.put("documentName", node.getName());
        variables.put("approver", approvers.get(0)); // Simple: assign to first approver
        variables.put("approvers", approvers);
        variables.put("initiator", currentUser);
        variables.put("startComment", normalizedComment);
        variables.put("startFormSubmittedBy", currentUser);
        variables.put("startFormSubmittedAt", Instant.now().toString());

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "documentApproval",
            documentId.toString(),
            variables
        );

        auditService.logEvent("WORKFLOW_STARTED", node.getId(), node.getName(), 
            currentUser, "Started approval workflow: " + instance.getId());

        log.info("Started workflow {} for document {}", instance.getId(), documentId);
        return instance;
    }

    @Transactional
    public ProcessInstance submitStartForm(UUID documentId, Map<String, Object> formValues) {
        WorkflowStartFormSubmission submission = normalizeStartFormSubmission(formValues);
        return startDocumentApproval(documentId, submission.approvers(), submission.comment());
    }

    @Transactional
    public ProcessInstance startProcess(
        String processDefinitionId,
        String processDefinitionKey,
        String businessKey,
        Map<String, Object> variables,
        List<String> itemIds
    ) {
        ProcessDefinition definition = resolveStartProcessDefinition(processDefinitionId, processDefinitionKey);
        List<Node> items = resolveStartProcessItems(itemIds);
        String currentUser = securityService.getCurrentUser();

        Map<String, Object> normalizedVariables = new LinkedHashMap<>();
        if (variables != null && !variables.isEmpty()) {
            normalizedVariables.putAll(variables);
        }
        normalizedVariables.putIfAbsent("initiator", currentUser);
        normalizedVariables.putIfAbsent("startFormSubmittedBy", currentUser);
        normalizedVariables.putIfAbsent("startFormSubmittedAt", Instant.now().toString());

        if (!items.isEmpty()) {
            List<String> attachedItemIds = items.stream()
                .map(node -> node.getId().toString())
                .toList();
            List<String> attachedItemNames = items.stream()
                .map(Node::getName)
                .toList();
            normalizedVariables.putIfAbsent("attachedItemIds", attachedItemIds);
            normalizedVariables.putIfAbsent("attachedItemNames", attachedItemNames);

            if (items.size() == 1) {
                Node item = items.get(0);
                normalizedVariables.putIfAbsent("nodeId", item.getId().toString());
                normalizedVariables.putIfAbsent("documentId", item.getId().toString());
                normalizedVariables.putIfAbsent("documentName", item.getName());
            }
        }

        String normalizedBusinessKey = normalizeOptionalString(businessKey);
        if (normalizedBusinessKey == null && items.size() == 1) {
            normalizedBusinessKey = items.get(0).getId().toString();
        }
        assertWorkflowStartVisible(normalizedBusinessKey, normalizedVariables, items);

        ProcessInstance instance = processDefinitionId != null && !processDefinitionId.isBlank()
            ? runtimeService.startProcessInstanceById(definition.getId(), normalizedBusinessKey, normalizedVariables)
            : runtimeService.startProcessInstanceByKey(definition.getKey(), normalizedBusinessKey, normalizedVariables);

        if (!items.isEmpty()) {
            Node auditNode = items.get(0);
            auditService.logEvent(
                "WORKFLOW_STARTED",
                auditNode.getId(),
                auditNode.getName(),
                currentUser,
                "Started workflow " + definition.getKey() + ": " + instance.getId()
            );
        }

        log.info("Started workflow {} using {}", instance.getId(), definition.getKey());
        return instance;
    }

    /**
     * Get tasks assigned to current user
     */
    public List<Task> getMyTasks() {
        String currentUser = securityService.getCurrentUser();
        return taskService.createTaskQuery()
            .taskAssignee(currentUser)
            .orderByTaskCreateTime().desc()
            .list()
            .stream()
            .filter(this::isTaskVisible)
            .toList();
    }

    public List<WorkflowTaskSummary> listTasks(
        String scope,
        String query,
        String businessKey,
        String assigneeOverride,
        String processId,
        String owner,
        String candidateUser,
        String candidateGroup
    ) {
        String currentUser = securityService.getCurrentUser();
        String normalizedScope = normalizeTaskScope(scope);
        String normalizedQuery = normalizeOptionalString(query);
        String normalizedBusinessKey = normalizeOptionalString(businessKey);
        String effectiveAssignee = normalizeOptionalString(assigneeOverride);
        String normalizedProcessId = normalizeOptionalString(processId);
        String normalizedOwner = normalizeOptionalString(owner);
        String normalizedCandidateUser = normalizeOptionalString(candidateUser);
        String normalizedCandidateGroup = normalizeOptionalString(candidateGroup);
        if (effectiveAssignee == null) {
            effectiveAssignee = currentUser;
        }
        final String resolvedAssignee = effectiveAssignee;
        Set<String> candidateKeys = resolveTaskCandidateKeys(resolvedAssignee);
        validateCandidateScope(normalizedScope, normalizedCandidateUser, normalizedCandidateGroup);

        List<WorkflowTaskSummary> summaries = new ArrayList<>();
        switch (normalizedScope) {
            case "ASSIGNED" -> summaries.addAll(taskService.createTaskQuery()
                .taskAssignee(resolvedAssignee)
                .list()
                .stream()
                .filter(task -> matchesOwner(task, normalizedOwner))
                .filter(task -> matchesCandidateUser(task, normalizedCandidateUser))
                .filter(task -> matchesCandidateGroup(task, normalizedCandidateGroup))
                .filter(task -> matchesProcessId(task, normalizedProcessId))
                .map(task -> buildTaskSummary(task, resolvedAssignee, candidateKeys))
                .toList());
            case "CLAIMABLE" -> summaries.addAll(listClaimableTaskSummaries(resolvedAssignee, candidateKeys).stream()
                .filter(summary -> matchesOwner(summary, normalizedOwner))
                .filter(summary -> matchesProcessId(summary, normalizedProcessId))
                .filter(summary -> matchesCandidateUser(summary.id(), normalizedCandidateUser))
                .filter(summary -> matchesCandidateGroup(summary.id(), normalizedCandidateGroup))
                .toList());
            case "INVOLVED" -> summaries.addAll(taskService.createTaskQuery()
                .list()
                .stream()
                .filter(task -> matchesOwner(task, normalizedOwner))
                .filter(task -> matchesProcessId(task, normalizedProcessId))
                .filter(task -> matchesCandidateUser(task, normalizedCandidateUser))
                .filter(task -> matchesCandidateGroup(task, normalizedCandidateGroup))
                .filter(task -> isTaskInvolvedFor(task, resolvedAssignee, candidateKeys))
                .map(task -> buildTaskSummary(task, resolvedAssignee, candidateKeys))
                .toList());
            case "COMPLETED" -> summaries.addAll(historyService.createHistoricTaskInstanceQuery()
                .finished()
                .taskAssignee(resolvedAssignee)
                .list()
                .stream()
                .map(this::buildTaskSummary)
                .filter(summary -> matchesOwner(summary, normalizedOwner))
                .filter(summary -> matchesCandidateUser(summary.id(), normalizedCandidateUser))
                .filter(summary -> matchesProcessId(summary, normalizedProcessId))
                .toList());
            case "ALL_ACTIVE" -> {
                summaries.addAll(taskService.createTaskQuery()
                    .taskAssignee(resolvedAssignee)
                    .list()
                    .stream()
                    .filter(task -> matchesOwner(task, normalizedOwner))
                    .filter(task -> matchesProcessId(task, normalizedProcessId))
                    .filter(task -> matchesCandidateUser(task, normalizedCandidateUser))
                    .filter(task -> matchesCandidateGroup(task, normalizedCandidateGroup))
                    .map(task -> buildTaskSummary(task, resolvedAssignee, candidateKeys))
                    .toList());
                summaries.addAll(taskService.createTaskQuery()
                    .taskUnassigned()
                    .list()
                    .stream()
                    .filter(task -> matchesOwner(task, normalizedOwner))
                    .filter(task -> matchesProcessId(task, normalizedProcessId))
                    .filter(task -> matchesCandidateUser(task, normalizedCandidateUser))
                    .filter(task -> matchesCandidateGroup(task, normalizedCandidateGroup))
                    .map(task -> buildTaskSummary(task, resolvedAssignee, candidateKeys))
                    .toList());
            }
            case "ALL" -> {
                summaries.addAll(taskService.createTaskQuery()
                    .taskAssignee(resolvedAssignee)
                    .list()
                    .stream()
                    .filter(task -> matchesOwner(task, normalizedOwner))
                    .filter(task -> matchesProcessId(task, normalizedProcessId))
                    .filter(task -> matchesCandidateUser(task, normalizedCandidateUser))
                    .filter(task -> matchesCandidateGroup(task, normalizedCandidateGroup))
                    .map(task -> buildTaskSummary(task, resolvedAssignee, candidateKeys))
                    .toList());
                summaries.addAll(taskService.createTaskQuery()
                    .taskUnassigned()
                    .list()
                    .stream()
                    .filter(task -> matchesOwner(task, normalizedOwner))
                    .filter(task -> matchesProcessId(task, normalizedProcessId))
                    .filter(task -> matchesCandidateUser(task, normalizedCandidateUser))
                    .filter(task -> matchesCandidateGroup(task, normalizedCandidateGroup))
                    .map(task -> buildTaskSummary(task, resolvedAssignee, candidateKeys))
                    .toList());
                summaries.addAll(historyService.createHistoricTaskInstanceQuery()
                    .finished()
                    .taskAssignee(resolvedAssignee)
                    .list()
                    .stream()
                    .map(this::buildTaskSummary)
                    .filter(summary -> matchesOwner(summary, normalizedOwner))
                    .filter(summary -> matchesCandidateUser(summary.id(), normalizedCandidateUser))
                    .filter(summary -> matchesProcessId(summary, normalizedProcessId))
                    .toList());
            }
            case "SHARED" -> summaries.addAll(taskService.createTaskQuery()
                .taskUnassigned()
                .list()
                .stream()
                .filter(task -> matchesOwner(task, normalizedOwner))
                .filter(task -> matchesProcessId(task, normalizedProcessId))
                .filter(task -> matchesCandidateUser(task, normalizedCandidateUser))
                .filter(task -> matchesCandidateGroup(task, normalizedCandidateGroup))
                .map(task -> buildTaskSummary(task, resolvedAssignee, candidateKeys))
                .toList());
            default -> throw new IllegalArgumentException("Unsupported workflow task scope: " + normalizedScope);
        }

        return summaries.stream()
            .filter(summary -> isProcessVisible(summary.processInstanceId(), summary.businessKey()))
            .filter(summary -> matchesTaskSummary(summary, normalizedQuery, normalizedBusinessKey, normalizedOwner))
            .sorted(Comparator.comparing(WorkflowTaskSummary::sortTime, Comparator.nullsLast(Date::compareTo)).reversed())
            .toList();
    }

    /**
     * Get tasks for a specific process instance
     */
    public List<Task> getProcessTasks(String processInstanceId) {
        assertProcessVisible(processInstanceId);
        return taskService.createTaskQuery()
            .processInstanceId(processInstanceId)
            .list();
    }

    @Transactional
    public void updateTask(String taskId, String state, String assignee, Map<String, Object> values) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }
        assertTaskVisible(task);
        switch (normalizeTaskTransition(state)) {
            case "COMPLETED" -> completeTask(taskId, values);
            case "CLAIMED" -> claimTask(taskId);
            case "UNCLAIMED" -> unclaimTask(taskId);
            case "ASSIGNED" -> assignTask(taskId, assignee);
            case "DELEGATED" -> delegateTask(taskId, assignee);
            case "RESOLVED" -> resolveTask(taskId, values);
            default -> throw new IllegalArgumentException("Unsupported workflow task state transition: " + state);
        }
    }

    /**
     * Complete a user task
     */
    @Transactional
    public void completeTask(String taskId, Map<String, Object> variables) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        assertTaskVisible(task);

        // Verify assignee (optional, but good practice)
        String currentUser = securityService.getCurrentUser();
        if (task.getAssignee() != null && !task.getAssignee().equals(currentUser)) {
            // In real app, check for admin or delegate permissions
            // For now, allow completion if user is admin or assignee
            if (!securityService.isAdmin(currentUser)) {
                throw new SecurityException("You are not assigned to this task");
            }
        }

        taskService.complete(taskId, normalizeCompletionVariables(task, variables, currentUser));
        log.info("Completed task {} by {}", taskId, currentUser);
    }

    @Transactional
    public void submitTaskForm(String taskId, Map<String, Object> formValues) {
        completeTask(taskId, formValues);
    }

    @Transactional
    public void claimTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }
        assertTaskVisible(task);

        String currentUser = securityService.getCurrentUser();
        if (task.getAssignee() != null && !task.getAssignee().isBlank()) {
            if (task.getAssignee().equals(currentUser)) {
                return;
            }
            throw new IllegalStateException("Task is already assigned to " + task.getAssignee());
        }
        if (!securityService.isAdmin(currentUser) && !isTaskClaimableFor(task, currentUser, resolveTaskCandidateKeys(currentUser))) {
            throw new SecurityException("You are not a candidate for this task");
        }

        taskService.claim(taskId, currentUser);
        log.info("Claimed task {} by {}", taskId, currentUser);
    }

    @Transactional
    public void unclaimTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }
        assertTaskVisible(task);

        String currentUser = securityService.getCurrentUser();
        String assignee = normalizeOptionalString(task.getAssignee());
        if (assignee == null) {
            return;
        }
        if (!assignee.equals(currentUser) && !securityService.isAdmin(currentUser)) {
            throw new SecurityException("You are not allowed to release this task");
        }

        taskService.unclaim(taskId);
        log.info("Released task {} by {}", taskId, currentUser);
    }

    @Transactional
    public void assignTask(String taskId, String assignee) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }
        assertTaskVisible(task);

        String normalizedAssignee = normalizeOptionalString(assignee);
        if (normalizedAssignee == null) {
            throw new IllegalArgumentException("Assignee is required");
        }
        if (!userRepository.existsByUsername(normalizedAssignee)) {
            throw new ResourceNotFoundException("User not found: " + normalizedAssignee);
        }

        String currentUser = securityService.getCurrentUser();
        String currentAssignee = normalizeOptionalString(task.getAssignee());
        boolean isAdmin = securityService.isAdmin(currentUser);

        if (currentAssignee == null) {
            if (!isAdmin && !normalizedAssignee.equals(currentUser)) {
                throw new SecurityException("Only admins can assign an unclaimed task to another user");
            }
        } else if (!currentAssignee.equals(currentUser) && !isAdmin) {
            throw new SecurityException("You are not allowed to reassign this task");
        }

        if (normalizedAssignee.equals(currentAssignee)) {
            return;
        }

        taskService.setAssignee(taskId, normalizedAssignee);
        log.info("Assigned task {} to {} by {}", taskId, normalizedAssignee, currentUser);
    }

    @Transactional
    public void delegateTask(String taskId, String assignee) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }
        assertTaskVisible(task);

        String normalizedAssignee = normalizeOptionalString(assignee);
        if (normalizedAssignee == null) {
            throw new IllegalArgumentException("Delegate assignee is required");
        }
        if (!userRepository.existsByUsername(normalizedAssignee)) {
            throw new ResourceNotFoundException("User not found: " + normalizedAssignee);
        }

        String currentUser = securityService.getCurrentUser();
        String currentAssignee = normalizeOptionalString(task.getAssignee());
        boolean isAdmin = securityService.isAdmin(currentUser);
        if (currentAssignee == null) {
            throw new IllegalStateException("Task must be assigned before it can be delegated");
        }
        if (!isAdmin && !currentAssignee.equals(currentUser)) {
            throw new SecurityException("You are not allowed to delegate this task");
        }
        if (normalizedAssignee.equals(currentAssignee)) {
            throw new IllegalArgumentException("Delegated assignee must be different from the current assignee");
        }

        taskService.delegateTask(taskId, normalizedAssignee);
        log.info("Delegated task {} to {} by {}", taskId, normalizedAssignee, currentUser);
    }

    @Transactional
    public void resolveTask(String taskId, Map<String, Object> values) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }
        assertTaskVisible(task);

        String currentUser = securityService.getCurrentUser();
        String currentAssignee = normalizeOptionalString(task.getAssignee());
        boolean isAdmin = securityService.isAdmin(currentUser);
        if (!isAdmin && (currentAssignee == null || !currentAssignee.equals(currentUser))) {
            throw new SecurityException("You are not allowed to resolve this task");
        }
        if (task.getDelegationState() != DelegationState.PENDING) {
            throw new IllegalStateException("Task is not currently delegated");
        }

        Map<String, Object> normalizedValues = new LinkedHashMap<>();
        if (values != null && !values.isEmpty()) {
            normalizedValues.putAll(values);
        }
        normalizedValues.put("resolvedBy", currentUser);
        normalizedValues.put("resolvedAt", Instant.now().toString());
        taskService.resolveTask(taskId, normalizedValues);
        log.info("Resolved delegated task {} by {}", taskId, currentUser);
    }

    /**
     * Update document status (Called by BPMN Service Task expression)
     * e.g., ${workflowService.updateDocumentStatus(documentId, 'APPROVED')}
     */
    @Transactional
    public void updateDocumentStatus(String documentIdStr, String statusStr) {
        try {
            UUID documentId = UUID.fromString(documentIdStr);
            NodeStatus status = NodeStatus.valueOf(statusStr);
            
            Node node = nodeRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + documentId));
            
            NodeStatus oldStatus = node.getStatus();
            node.setStatus(status);
            nodeRepository.save(node);
            
            log.info("Workflow updated document {} status: {} -> {}", documentId, oldStatus, status);
            auditService.logEvent("STATUS_CHANGED", node.getId(), node.getName(), 
                "system", "Workflow changed status from " + oldStatus + " to " + status);
                
        } catch (Exception e) {
            log.error("Failed to update document status from workflow", e);
            // Don't throw exception to avoid failing the workflow step? 
            // Better to fail so incident is created in Flowable
            throw new RuntimeException("Workflow status update failed", e);
        }
    }

    /**
     * Get all process definitions
     */
    public List<ProcessDefinition> getProcessDefinitions() {
        return repositoryService.createProcessDefinitionQuery()
            .latestVersion()
            .orderByProcessDefinitionName().asc()
            .list();
    }

    /**
     * Get active process instances for a document
     */
    public List<ProcessInstance> getDocumentWorkflows(UUID documentId) {
        assertNodeVisible(documentId, "Node not found: " + documentId);
        return runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(documentId.toString())
            .list();
    }

    /**
     * Get workflow history for a document
     */
    public List<WorkflowDocumentHistoryItem> getDocumentHistory(UUID documentId) {
        assertNodeVisible(documentId, "Node not found: " + documentId);
        return historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(documentId.toString())
            .finished()
            .orderByProcessInstanceEndTime().desc()
            .list()
            .stream()
            .map(this::buildDocumentHistoryItem)
            .toList();
    }

    public WorkflowProcessBrowserPage listProcesses(
        String status,
        String businessKey,
        String startedBy,
        String definitionKey,
        String query,
        int skipCount,
        int maxItems
    ) {
        String normalizedStatus = normalizeProcessStatus(status);
        String normalizedBusinessKey = normalizeOptionalString(businessKey);
        String normalizedStartedBy = normalizeOptionalString(startedBy);
        String normalizedDefinitionKey = normalizeOptionalString(definitionKey);
        String normalizedQuery = normalizeOptionalString(query);
        int safeSkipCount = Math.max(0, skipCount);
        int safeMaxItems = Math.max(1, Math.min(100, maxItems));

        var historicQuery = historyService.createHistoricProcessInstanceQuery();
        if ("ACTIVE".equals(normalizedStatus)) {
            historicQuery.unfinished();
        } else if ("COMPLETED".equals(normalizedStatus)) {
            historicQuery.finished();
        }
        if (normalizedBusinessKey != null) {
            historicQuery.processInstanceBusinessKey(normalizedBusinessKey);
        }
        if (normalizedStartedBy != null) {
            historicQuery.startedBy(normalizedStartedBy);
        }

        List<WorkflowProcessBrowserItem> filtered = historicQuery
            .orderByProcessInstanceStartTime().desc()
            .list()
            .stream()
            .filter(this::isHistoricProcessVisible)
            .map(this::buildProcessBrowserItem)
            .filter(item -> matchesProcessBrowserItem(
                item,
                normalizedBusinessKey,
                normalizedStartedBy,
                normalizedDefinitionKey,
                normalizedQuery
            ))
            .toList();
        long totalCount = filtered.size();
        int fromIndex = Math.min(safeSkipCount, filtered.size());
        int toIndex = Math.min(fromIndex + safeMaxItems, filtered.size());
        List<WorkflowProcessBrowserItem> items = filtered.subList(fromIndex, toIndex);

        return new WorkflowProcessBrowserPage(
            items,
            safeSkipCount,
            safeMaxItems,
            totalCount,
            safeSkipCount + items.size() < totalCount
        );
    }

    public WorkflowProcessDetail getProcessDetail(String processInstanceId) {
        ProcessInstance runtimeInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();

        if (runtimeInstance == null && historicInstance == null) {
            throw new ResourceNotFoundException("Process instance not found: " + processInstanceId);
        }

        String processDefinitionId = runtimeInstance != null
            ? runtimeInstance.getProcessDefinitionId()
            : historicInstance.getProcessDefinitionId();

        ProcessDefinition processDefinition = processDefinitionId == null
            ? null
            : repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();

        Map<String, Object> variables = runtimeInstance != null
            ? runtimeService.getVariables(processInstanceId)
            : getHistoricProcessVariables(processInstanceId);
        assertProcessVisible(processInstanceId, runtimeInstance != null ? runtimeInstance.getBusinessKey() : historicInstance.getBusinessKey(), variables);
        WorkflowSubmissionSummary submissionSummary = buildSubmissionSummary(
            variables,
            historicInstance != null ? normalizeOptionalString(historicInstance.getStartUserId()) : null,
            historicInstance != null ? historicInstance.getStartTime() : null,
            historicInstance != null ? historicInstance.getEndTime() : null
        );

        return new WorkflowProcessDetail(
            processInstanceId,
            processDefinitionId,
            processDefinition != null ? processDefinition.getKey() : null,
            processDefinition != null ? processDefinition.getName() : null,
            processDefinition != null ? processDefinition.getVersion() : null,
            runtimeInstance != null ? runtimeInstance.getBusinessKey() : historicInstance.getBusinessKey(),
            historicInstance != null ? historicInstance.getStartUserId() : null,
            historicInstance != null ? historicInstance.getStartTime() : null,
            historicInstance != null ? historicInstance.getEndTime() : null,
            processDefinition != null && processDefinition.isSuspended(),
            historicInstance != null && historicInstance.getEndTime() != null,
            variables,
            submissionSummary
        );
    }

    public List<WorkflowProcessTask> getProcessTaskDetails(String processInstanceId) {
        assertProcessVisible(processInstanceId);
        ProcessInstance runtimeInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        if (runtimeInstance == null) {
            HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
            if (historicInstance == null) {
                throw new ResourceNotFoundException("Process instance not found: " + processInstanceId);
            }
        }

        return getProcessTasks(processInstanceId).stream()
            .map(task -> new WorkflowProcessTask(
                task.getId(),
                task.getName(),
                task.getAssignee(),
                normalizeOptionalString(task.getOwner()),
                task.getDescription(),
                task.getCreateTime(),
                task.getDueDate(),
                task.getTaskDefinitionKey(),
                toDelegationState(task)
            ))
            .toList();
    }

    public List<WorkflowHistoricTaskItem> getProcessTaskHistory(String processInstanceId) {
        return getProcessTaskHistory(processInstanceId, null, null, null);
    }

    public List<WorkflowHistoricTaskItem> getProcessTaskHistory(
        String processInstanceId,
        String query,
        String assignee,
        String taskDefinitionKey
    ) {
        assertProcessVisible(processInstanceId);
        ProcessInstance runtimeInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        if (runtimeInstance == null && historicInstance == null) {
            throw new ResourceNotFoundException("Process instance not found: " + processInstanceId);
        }

        String normalizedQuery = normalizeOptionalString(query);
        String normalizedAssignee = normalizeOptionalString(assignee);
        String normalizedTaskDefinitionKey = normalizeOptionalString(taskDefinitionKey);

        return historyService.createHistoricTaskInstanceQuery()
            .processInstanceId(processInstanceId)
            .list()
            .stream()
            .filter(task -> task.getEndTime() != null)
            .map(this::buildHistoricTaskItem)
            .filter(task -> matchesHistoricTaskItem(task, normalizedQuery, normalizedAssignee, normalizedTaskDefinitionKey))
            .sorted(Comparator
                .comparing(WorkflowHistoricTaskItem::endTime, Comparator.nullsLast(Date::compareTo))
                .thenComparing(WorkflowHistoricTaskItem::startTime, Comparator.nullsLast(Date::compareTo)))
            .toList();
    }

    public List<WorkflowProcessActivity> getProcessActivities(String processInstanceId) {
        return getProcessActivities(processInstanceId, null, null, null);
    }

    public List<WorkflowProcessActivity> getProcessActivities(
        String processInstanceId,
        String query,
        String assignee,
        String activityType
    ) {
        assertProcessVisible(processInstanceId);
        ProcessInstance runtimeInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        if (runtimeInstance == null && historicInstance == null) {
            throw new ResourceNotFoundException("Process instance not found: " + processInstanceId);
        }

        String normalizedQuery = normalizeOptionalString(query);
        String normalizedAssignee = normalizeOptionalString(assignee);
        String normalizedActivityType = normalizeOptionalString(activityType);

        return historyService.createHistoricActivityInstanceQuery()
            .processInstanceId(processInstanceId)
            .orderByHistoricActivityInstanceStartTime().asc()
            .list()
            .stream()
            .map(this::buildProcessActivity)
            .filter(activity -> matchesProcessActivity(activity, normalizedQuery, normalizedAssignee, normalizedActivityType))
            .toList();
    }

    public List<WorkflowInvolvedActor> getProcessInvolvedActors(String processInstanceId) {
        assertProcessVisible(processInstanceId);
        ProcessInstance runtimeInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        if (runtimeInstance == null && historicInstance == null) {
            throw new ResourceNotFoundException("Process instance not found: " + processInstanceId);
        }

        Map<String, Object> variables = runtimeInstance != null
            ? runtimeService.getVariables(processInstanceId)
            : getHistoricProcessVariables(processInstanceId);

        Map<String, WorkflowActorAccumulator> actors = new LinkedHashMap<>();
        registerUserActor(actors, historicInstance != null ? historicInstance.getStartUserId() : null, "starter");
        registerUserActor(actors, normalizeOptionalString(variables.get("startFormSubmittedBy")), "submittedBy");
        normalizeStringList(variables.get("approvers")).forEach(approver -> registerUserActor(actors, approver, "approver"));
        registerUserActor(actors, normalizeOptionalString(variables.get("reviewedBy")), "reviewer");

        getProcessTasks(processInstanceId).forEach(task -> {
            registerUserActor(actors, task.getAssignee(), "currentAssignee");
            taskService.getIdentityLinksForTask(task.getId()).forEach(link -> registerIdentityLinkActor(actors, link));
        });

        historyService.createHistoricActivityInstanceQuery()
            .processInstanceId(processInstanceId)
            .list()
            .forEach(activity -> registerUserActor(actors, activity.getAssignee(), "activityAssignee"));

        return toInvolvedActors(actors);
    }

    public List<WorkflowTaskCandidate> getTaskCandidates(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }
        assertTaskVisible(task);

        return taskService.getIdentityLinksForTask(taskId).stream()
            .filter(link -> "candidate".equalsIgnoreCase(link.getType()))
            .map(this::buildTaskCandidate)
            .distinct()
            .toList();
    }

    public List<WorkflowInvolvedActor> getTaskInvolvedActors(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }
        assertTaskVisible(task);

        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(task.getProcessInstanceId())
            .singleResult();
        Map<String, Object> variables = runtimeService.createProcessInstanceQuery()
            .processInstanceId(task.getProcessInstanceId())
            .singleResult() != null
                ? runtimeService.getVariables(task.getProcessInstanceId())
                : getHistoricProcessVariables(task.getProcessInstanceId());

        Map<String, WorkflowActorAccumulator> actors = new LinkedHashMap<>();
        registerUserActor(actors, task.getAssignee(), "assignee");
        registerUserActor(actors, task.getOwner(), "owner");
        registerUserActor(actors, historicInstance != null ? historicInstance.getStartUserId() : null, "starter");
        registerUserActor(actors, normalizeOptionalString(variables.get("startFormSubmittedBy")), "submittedBy");
        normalizeStringList(variables.get("approvers")).forEach(approver -> registerUserActor(actors, approver, "approver"));
        registerUserActor(actors, normalizeOptionalString(variables.get("reviewedBy")), "reviewer");
        taskService.getIdentityLinksForTask(taskId).forEach(link -> registerIdentityLinkActor(actors, link));

        return toInvolvedActors(actors);
    }

    public WorkflowTaskDetail getTaskDetail(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        assertTaskVisible(task);

        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(task.getProcessInstanceId())
            .singleResult();
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(task.getProcessInstanceId())
            .singleResult();
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionId(task.getProcessDefinitionId())
            .singleResult();
        Map<String, Object> processVariables = processInstance != null
            ? processInstance.getProcessVariables()
            : getHistoricProcessVariables(task.getProcessInstanceId());

        return new WorkflowTaskDetail(
            task.getId(),
            task.getName(),
            task.getDescription(),
            task.getAssignee(),
            task.getOwner(),
            toDelegationState(task),
            task.getCreateTime(),
            task.getDueDate(),
            task.getProcessInstanceId(),
            task.getProcessDefinitionId(),
            task.getTaskDefinitionKey(),
            processDefinition != null ? processDefinition.getKey() : null,
            processDefinition != null ? processDefinition.getName() : null,
            processDefinition != null ? processDefinition.getVersion() : null,
            processInstance != null ? processInstance.getBusinessKey() : null,
            processVariables,
            processDefinition != null ? processDefinition.isSuspended() : null,
            buildSubmissionSummary(
                processVariables,
                historicInstance != null ? normalizeOptionalString(historicInstance.getStartUserId()) : null,
                historicInstance != null ? historicInstance.getStartTime() : null,
                historicInstance != null ? historicInstance.getEndTime() : null
            )
        );
    }

    public List<WorkflowVariableItem> getProcessVariables(String processInstanceId) {
        return toWorkflowVariableItems(getProcessDetail(processInstanceId).variables(), "process");
    }

    @Transactional
    public void upsertProcessVariable(String processInstanceId, String variableName, Object value) {
        assertProcessVisible(processInstanceId);
        ProcessInstance runtimeInstance = requireRuntimeProcessInstance(processInstanceId);
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        assertProcessVariableWriteAccess(processInstanceId, historicInstance);

        String normalizedVariableName = normalizeOptionalString(variableName);
        if (normalizedVariableName == null) {
            throw new IllegalArgumentException("Variable name is required");
        }

        runtimeService.setVariable(runtimeInstance.getId(), normalizedVariableName, value);
        log.info("Updated workflow variable {} on process {} by {}", normalizedVariableName, processInstanceId, securityService.getCurrentUser());
    }

    @Transactional
    public void deleteProcessVariable(String processInstanceId, String variableName) {
        assertProcessVisible(processInstanceId);
        ProcessInstance runtimeInstance = requireRuntimeProcessInstance(processInstanceId);
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        assertProcessVariableWriteAccess(processInstanceId, historicInstance);

        String normalizedVariableName = normalizeOptionalString(variableName);
        if (normalizedVariableName == null) {
            throw new IllegalArgumentException("Variable name is required");
        }

        runtimeService.removeVariable(runtimeInstance.getId(), normalizedVariableName);
        log.info("Deleted workflow variable {} on process {} by {}", normalizedVariableName, processInstanceId, securityService.getCurrentUser());
    }

    public List<WorkflowVariableItem> getTaskVariables(String taskId) {
        WorkflowTaskDetail detail = getTaskDetail(taskId);
        return toWorkflowVariableItems(detail.processVariables(), "task");
    }

    public List<WorkflowBusinessItem> getProcessItems(String processInstanceId) {
        WorkflowProcessDetail detail = getProcessDetail(processInstanceId);
        return resolveBusinessItems(detail.businessKey(), detail.variables(), "process");
    }

    public List<WorkflowBusinessItem> getTaskItems(String taskId) {
        WorkflowTaskDetail detail = getTaskDetail(taskId);
        return resolveBusinessItems(detail.businessKey(), detail.processVariables(), "task");
    }

    @Transactional
    public void deleteProcess(String processInstanceId) {
        assertProcessVisible(processInstanceId);
        ProcessInstance runtimeInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        if (runtimeInstance == null) {
            throw new ResourceNotFoundException("Process instance not found: " + processInstanceId);
        }

        runtimeService.deleteProcessInstance(processInstanceId, "Deleted via Athena workflow API");
        log.info("Deleted workflow process instance {}", processInstanceId);
    }

    @Transactional
    public void cancelProcess(String processInstanceId, String reason) {
        assertProcessVisible(processInstanceId);
        ProcessInstance runtimeInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        if (runtimeInstance == null) {
            throw new ResourceNotFoundException("Process instance not found: " + processInstanceId);
        }

        String cancelReason = normalizeOptionalString(reason);
        String effectiveReason = cancelReason != null
            ? "Cancelled via Athena workflow API: " + cancelReason
            : "Cancelled via Athena workflow API";
        runtimeService.deleteProcessInstance(processInstanceId, effectiveReason);
        log.info("Cancelled workflow process instance {} with reason {}", processInstanceId, cancelReason);
    }

    public WorkflowDefinitionDetail getDefinitionDetail(String definitionId) {
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionId(definitionId)
            .singleResult();
        if (definition == null) {
            throw new IllegalArgumentException("Process definition not found: " + definitionId);
        }

        List<String> resourceNames = repositoryService.getDeploymentResourceNames(definition.getDeploymentId());
        String modelResourceName = definition.getResourceName();
        String diagramResourceName = definition.getDiagramResourceName();
        boolean hasBpmnXml = modelResourceName != null && !modelResourceName.isBlank();
        boolean hasDiagram = diagramResourceName != null && !diagramResourceName.isBlank();

        return new WorkflowDefinitionDetail(
            definition.getId(),
            definition.getKey(),
            definition.getName(),
            definition.getDescription(),
            definition.getVersion(),
            definition.getCategory(),
            definition.getDeploymentId(),
            definition.getTenantId(),
            definition.isSuspended(),
            definition.hasStartFormKey(),
            definition.hasGraphicalNotation(),
            modelResourceName,
            diagramResourceName,
            hasBpmnXml,
            hasDiagram,
            resourceNames
        );
    }

    public WorkflowDefinitionModel getDefinitionModel(String definitionId) {
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionId(definitionId)
            .singleResult();
        if (definition == null) {
            throw new IllegalArgumentException("Process definition not found: " + definitionId);
        }

        try (InputStream inputStream = repositoryService.getProcessModel(definitionId)) {
            if (inputStream == null) {
                return new WorkflowDefinitionModel(
                    definition.getId(),
                    definition.getResourceName(),
                    null
                );
            }
            String xml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return new WorkflowDefinitionModel(
                definition.getId(),
                definition.getResourceName(),
                xml
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to load workflow model: " + definitionId, e);
        }
    }

    public WorkflowDefinitionDiagram getDefinitionDiagram(String definitionId) {
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionId(definitionId)
            .singleResult();
        if (definition == null) {
            throw new ResourceNotFoundException("Process definition not found: " + definitionId);
        }
        String diagramResourceName = definition.getDiagramResourceName();
        if (diagramResourceName == null || diagramResourceName.isBlank()) {
            throw new ResourceNotFoundException("Workflow definition diagram not found: " + definitionId);
        }

        try (InputStream inputStream = repositoryService.getResourceAsStream(definition.getDeploymentId(), diagramResourceName)) {
            if (inputStream == null) {
                throw new ResourceNotFoundException("Workflow definition diagram not found: " + definitionId);
            }
            return new WorkflowDefinitionDiagram(
                definition.getId(),
                diagramResourceName,
                inputStream.readAllBytes()
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to load workflow diagram: " + definitionId, e);
        }
    }

    public WorkflowDefinitionDiagram getProcessDiagram(String processInstanceId) {
        WorkflowProcessDetail detail = getProcessDetail(processInstanceId);
        if (detail.processDefinitionId() == null || detail.processDefinitionId().isBlank()) {
            throw new ResourceNotFoundException("Process definition not found for process instance: " + processInstanceId);
        }

        WorkflowDefinitionDiagram definitionDiagram = getDefinitionDiagram(detail.processDefinitionId());
        return new WorkflowDefinitionDiagram(
            processInstanceId,
            definitionDiagram.resourceName(),
            definitionDiagram.bytes()
        );
    }

    public List<WorkflowFormModelElement> getStartFormModel(String definitionId) {
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionId(definitionId)
            .singleResult();
        if (definition == null) {
            throw new IllegalArgumentException("Process definition not found: " + definitionId);
        }
        return buildStartFormModel(definition);
    }

    public List<WorkflowFormModelElement> getTaskFormModel(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }

        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionId(task.getProcessDefinitionId())
            .singleResult();
        if (definition == null) {
            throw new ResourceNotFoundException("Process definition not found for task: " + taskId);
        }

        return buildTaskFormModel(definition, task);
    }

    private WorkflowStartFormSubmission normalizeStartFormSubmission(Map<String, Object> formValues) {
        if (formValues == null || formValues.isEmpty()) {
            throw new IllegalArgumentException("Workflow start form values are required");
        }

        List<String> approvers = normalizeStringList(formValues.get("approvers"));
        if (approvers.isEmpty()) {
            throw new IllegalArgumentException("At least one approver is required");
        }

        return new WorkflowStartFormSubmission(
            approvers,
            normalizeOptionalString(formValues.get("comment"))
        );
    }

    private Map<String, Object> normalizeCompletionVariables(Task task, Map<String, Object> variables, String currentUser) {
        Map<String, Object> safeVariables = variables == null ? Map.of() : variables;
        if (!isDocumentApprovalTask(task)) {
            return new LinkedHashMap<>(safeVariables);
        }

        Boolean approved = normalizeRequiredBoolean(safeVariables.get("approved"), "Workflow decision is required");
        String comment = normalizeOptionalString(safeVariables.get("comment"));

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("approved", approved);
        normalized.put("decision", approved ? "APPROVED" : "REJECTED");
        normalized.put("decisionLabel", approved ? "Approve" : "Reject");
        normalized.put("reviewedBy", currentUser);
        normalized.put("reviewedAt", Instant.now().toString());
        if (comment != null) {
            normalized.put("comment", comment);
        }
        return normalized;
    }

    private boolean isDocumentApprovalTask(Task task) {
        if (task == null || task.getProcessDefinitionId() == null) {
            return false;
        }
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionId(task.getProcessDefinitionId())
            .singleResult();
        return definition != null
            && "documentApproval".equals(definition.getKey())
            && "approvalTask".equals(task.getTaskDefinitionKey());
    }

    private List<String> normalizeStringList(Object value) {
        if (value instanceof List<?> listValue) {
            return listValue.stream()
                .map(this::normalizeOptionalString)
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .toList();
        }
        String singleValue = normalizeOptionalString(value);
        if (singleValue == null) {
            return List.of();
        }
        return List.of(singleValue);
    }

    private String normalizeOptionalString(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Boolean normalizeRequiredBoolean(Object value, String errorMessage) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim().toLowerCase();
            if ("true".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized)) {
                return false;
            }
        }
        throw new IllegalArgumentException(errorMessage);
    }

    private Boolean normalizeOptionalBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim().toLowerCase();
            if ("true".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized)) {
                return false;
            }
        }
        return null;
    }

    private String formatDate(java.util.Date date) {
        if (date == null) {
            return null;
        }
        return Instant.ofEpochMilli(date.getTime()).toString();
    }

    private WorkflowSubmissionSummary buildSubmissionSummary(
        Map<String, Object> variables,
        String startFormSubmittedByFallback,
        java.util.Date startFormSubmittedAtFallback,
        java.util.Date reviewedAtFallback
    ) {
        Map<String, Object> safeVariables = variables == null ? Map.of() : variables;
        String decisionValue = normalizeOptionalString(safeVariables.get("decision"));
        Boolean approvedValue = normalizeOptionalBoolean(safeVariables.get("approved"));
        String decisionLabel = normalizeOptionalString(safeVariables.get("decisionLabel"));
        if (decisionLabel == null) {
            if ("APPROVED".equalsIgnoreCase(decisionValue) || Boolean.TRUE.equals(approvedValue)) {
                decisionLabel = "Approve";
            } else if ("REJECTED".equalsIgnoreCase(decisionValue) || Boolean.FALSE.equals(approvedValue)) {
                decisionLabel = "Reject";
            }
        }

        String submittedBy = normalizeOptionalString(safeVariables.get("startFormSubmittedBy"));
        if (submittedBy == null) {
            submittedBy = normalizeOptionalString(safeVariables.get("initiator"));
        }
        if (submittedBy == null) {
            submittedBy = startFormSubmittedByFallback;
        }

        String submittedAt = normalizeOptionalString(safeVariables.get("startFormSubmittedAt"));
        if (submittedAt == null) {
            submittedAt = formatDate(startFormSubmittedAtFallback);
        }

        String reviewedAt = normalizeOptionalString(safeVariables.get("reviewedAt"));
        if (reviewedAt == null) {
            reviewedAt = formatDate(reviewedAtFallback);
        }

        return new WorkflowSubmissionSummary(
            normalizeStringList(safeVariables.get("approvers")),
            normalizeOptionalString(safeVariables.get("startComment")),
            submittedBy,
            submittedAt,
            decisionValue,
            decisionLabel,
            normalizeOptionalString(safeVariables.get("reviewedBy")),
            reviewedAt,
            normalizeOptionalString(safeVariables.get("comment"))
        );
    }

    private void assertNodeVisible(UUID nodeId, String notFoundMessage) {
        if (!tenantWorkspaceScopeService.isNodeVisible(nodeId)) {
            throw new ResourceNotFoundException(notFoundMessage);
        }
    }

    private void assertTaskVisible(Task task) {
        if (task == null || !isProcessVisible(task.getProcessInstanceId(), null)) {
            throw new ResourceNotFoundException("Task not found: " + (task != null ? task.getId() : null));
        }
    }

    private void assertProcessVisible(String processInstanceId) {
        ProcessInstance runtimeInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        if (runtimeInstance == null && historicInstance == null) {
            throw new ResourceNotFoundException("Process instance not found: " + processInstanceId);
        }

        Map<String, Object> variables = runtimeInstance != null
            ? runtimeService.getVariables(processInstanceId)
            : getHistoricProcessVariables(processInstanceId);
        String businessKey = runtimeInstance != null
            ? runtimeInstance.getBusinessKey()
            : historicInstance.getBusinessKey();
        assertProcessVisible(processInstanceId, businessKey, variables);
    }

    private void assertProcessVisible(String processInstanceId, String businessKey, Map<String, Object> variables) {
        if (!isProcessVisible(processInstanceId, businessKey, variables)) {
            throw new ResourceNotFoundException("Process instance not found: " + processInstanceId);
        }
    }

    private boolean isTaskVisible(Task task) {
        return task != null && isProcessVisible(task.getProcessInstanceId(), null);
    }

    private boolean isHistoricProcessVisible(HistoricProcessInstance historicInstance) {
        if (historicInstance == null) {
            return false;
        }
        return isProcessVisible(historicInstance.getId(), historicInstance.getBusinessKey(), getHistoricProcessVariables(historicInstance.getId()));
    }

    private boolean isProcessVisible(String processInstanceId, String businessKey) {
        Map<String, Object> variables = processInstanceId != null ? resolveProcessVariables(processInstanceId) : Map.of();
        return isProcessVisible(processInstanceId, businessKey, variables);
    }

    private boolean isProcessVisible(String processInstanceId, String businessKey, Map<String, Object> variables) {
        if (!tenantWorkspaceScopeService.hasScopedTenantWorkspace()) {
            return true;
        }

        String tenantRootPath = tenantWorkspaceScopeService.resolveCurrentTenantRootPath();
        if (tenantRootPath == null) {
            return true;
        }
        if (tenantRootPath.isBlank()) {
            return false;
        }

        Set<UUID> candidateNodeIds = resolveWorkflowBusinessNodeIds(businessKey, variables);
        if (candidateNodeIds.isEmpty()) {
            return false;
        }

        return candidateNodeIds.stream()
            .anyMatch(nodeId -> tenantWorkspaceScopeService.isNodeVisible(nodeId, tenantRootPath));
    }

    private void assertWorkflowStartVisible(String businessKey, Map<String, Object> variables, List<Node> items) {
        if (!tenantWorkspaceScopeService.hasScopedTenantWorkspace()) {
            return;
        }

        String tenantRootPath = tenantWorkspaceScopeService.resolveCurrentTenantRootPath();
        if (tenantRootPath == null) {
            return;
        }
        if (tenantRootPath.isBlank()) {
            throw new ResourceNotFoundException("Workflow start payload is outside current tenant workspace");
        }

        if (items != null && !items.isEmpty()) {
            for (Node item : items) {
                if (item == null || item.getId() == null || !tenantWorkspaceScopeService.isNodeVisible(item.getId(), tenantRootPath)) {
                    throw new ResourceNotFoundException("Workflow item not found: " + (item != null ? item.getId() : null));
                }
            }
            return;
        }

        if (!isProcessVisible(null, businessKey, variables)) {
            throw new ResourceNotFoundException("Workflow start payload is outside current tenant workspace");
        }
    }

    private Map<String, Object> resolveProcessVariables(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return Map.of();
        }
        ProcessInstance runtimeInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        return runtimeInstance != null
            ? runtimeService.getVariables(processInstanceId)
            : getHistoricProcessVariables(processInstanceId);
    }

    private Set<UUID> resolveWorkflowBusinessNodeIds(String businessKey, Map<String, Object> variables) {
        LinkedHashSet<UUID> candidateIds = new LinkedHashSet<>();
        addWorkflowBusinessNodeId(candidateIds, businessKey);
        if (variables == null || variables.isEmpty()) {
            return candidateIds;
        }
        addWorkflowBusinessNodeId(candidateIds, variables.get("documentId"));
        addWorkflowBusinessNodeId(candidateIds, variables.get("nodeId"));
        Object attachedItemIds = variables.get("attachedItemIds");
        if (attachedItemIds instanceof List<?> listValue) {
            listValue.forEach(itemId -> addWorkflowBusinessNodeId(candidateIds, itemId));
        } else {
            addWorkflowBusinessNodeId(candidateIds, attachedItemIds);
        }
        return candidateIds;
    }

    private void addWorkflowBusinessNodeId(Set<UUID> candidateIds, Object candidate) {
        String normalized = normalizeOptionalString(candidate);
        if (normalized == null) {
            return;
        }
        try {
            candidateIds.add(UUID.fromString(normalized));
        } catch (IllegalArgumentException ignored) {
            // Ignore non-node business keys in tenant scoped process visibility checks.
        }
    }

    private WorkflowProcessActivity buildProcessActivity(HistoricActivityInstance activity) {
        return new WorkflowProcessActivity(
            activity.getId(),
            activity.getActivityId(),
            activity.getActivityName(),
            activity.getActivityType(),
            activity.getExecutionId(),
            activity.getTaskId(),
            activity.getAssignee(),
            activity.getStartTime(),
            activity.getEndTime(),
            activity.getDurationInMillis()
        );
    }

    private WorkflowHistoricTaskItem buildHistoricTaskItem(HistoricTaskInstance task) {
        return new WorkflowHistoricTaskItem(
            task.getId(),
            task.getName(),
            normalizeOptionalString(task.getAssignee()),
            normalizeOptionalString(task.getOwner()),
            normalizeOptionalString(task.getDescription()),
            normalizeOptionalString(task.getTaskDefinitionKey()),
            task.getStartTime(),
            task.getEndTime(),
            task.getDurationInMillis(),
            normalizeOptionalString(task.getDeleteReason())
        );
    }

    private WorkflowTaskCandidate buildTaskCandidate(IdentityLink link) {
        return new WorkflowTaskCandidate(
            normalizeOptionalString(link.getUserId()),
            normalizeOptionalString(link.getGroupId()),
            normalizeOptionalString(link.getType())
        );
    }

    private List<WorkflowTaskSummary> listClaimableTaskSummaries(String username, Set<String> candidateKeys) {
        return taskService.createTaskQuery()
            .taskUnassigned()
            .list()
            .stream()
            .filter(task -> isTaskClaimableFor(task, username, candidateKeys))
            .map(task -> buildTaskSummary(task, username, candidateKeys))
            .toList();
    }

    private boolean matchesHistoricTaskItem(
        WorkflowHistoricTaskItem task,
        String normalizedQuery,
        String normalizedAssignee,
        String normalizedTaskDefinitionKey
    ) {
        if (normalizedAssignee != null && !normalizedAssignee.equalsIgnoreCase(normalizeOptionalString(task.assignee()))) {
            return false;
        }
        if (normalizedTaskDefinitionKey != null
            && !normalizedTaskDefinitionKey.equalsIgnoreCase(normalizeOptionalString(task.taskDefinitionKey()))) {
            return false;
        }
        if (normalizedQuery == null) {
            return true;
        }
        return containsIgnoreCase(task.name(), normalizedQuery)
            || containsIgnoreCase(task.assignee(), normalizedQuery)
            || containsIgnoreCase(task.owner(), normalizedQuery)
            || containsIgnoreCase(task.description(), normalizedQuery)
            || containsIgnoreCase(task.taskDefinitionKey(), normalizedQuery)
            || containsIgnoreCase(task.deleteReason(), normalizedQuery);
    }

    private boolean matchesProcessActivity(
        WorkflowProcessActivity activity,
        String normalizedQuery,
        String normalizedAssignee,
        String normalizedActivityType
    ) {
        if (normalizedAssignee != null && !normalizedAssignee.equalsIgnoreCase(normalizeOptionalString(activity.assignee()))) {
            return false;
        }
        if (normalizedActivityType != null
            && !normalizedActivityType.equalsIgnoreCase(normalizeOptionalString(activity.activityType()))) {
            return false;
        }
        if (normalizedQuery == null) {
            return true;
        }
        return containsIgnoreCase(activity.activityName(), normalizedQuery)
            || containsIgnoreCase(activity.activityId(), normalizedQuery)
            || containsIgnoreCase(activity.activityType(), normalizedQuery)
            || containsIgnoreCase(activity.executionId(), normalizedQuery)
            || containsIgnoreCase(activity.taskId(), normalizedQuery)
            || containsIgnoreCase(activity.assignee(), normalizedQuery);
    }

    private void validateCandidateScope(String normalizedScope, String normalizedCandidateUser, String normalizedCandidateGroup) {
        if (normalizedCandidateUser == null && normalizedCandidateGroup == null) {
            return;
        }
        if (!Set.of("CLAIMABLE", "SHARED", "ALL_ACTIVE").contains(normalizedScope)) {
            throw new IllegalArgumentException("Filtering on candidateUser or candidateGroup is only allowed for active shared or claimable scopes");
        }
    }

    private boolean matchesOwner(Task task, String normalizedOwner) {
        if (normalizedOwner == null) {
            return true;
        }
        return task != null && normalizedOwner.equalsIgnoreCase(normalizeOptionalString(task.getOwner()));
    }

    private boolean matchesOwner(WorkflowTaskSummary summary, String normalizedOwner) {
        if (normalizedOwner == null) {
            return true;
        }
        return normalizedOwner.equalsIgnoreCase(normalizeOptionalString(summary.owner()));
    }

    private boolean matchesCandidateUser(Task task, String normalizedCandidateUser) {
        if (normalizedCandidateUser == null) {
            return true;
        }
        if (task == null || normalizeOptionalString(task.getAssignee()) != null) {
            return false;
        }
        return taskService.getIdentityLinksForTask(task.getId()).stream()
            .filter(link -> "candidate".equalsIgnoreCase(link.getType()))
            .map(IdentityLink::getUserId)
            .map(this::normalizeOptionalString)
            .anyMatch(userId -> userId != null && userId.equalsIgnoreCase(normalizedCandidateUser));
    }

    private boolean matchesCandidateUser(String taskId, String normalizedCandidateUser) {
        if (normalizedCandidateUser == null) {
            return true;
        }
        if (normalizeOptionalString(taskId) == null) {
            return false;
        }
        Task task = taskService.createTaskQuery()
            .taskId(taskId)
            .singleResult();
        return task != null && matchesCandidateUser(task, normalizedCandidateUser);
    }

    private boolean matchesCandidateGroup(Task task, String normalizedCandidateGroup) {
        if (normalizedCandidateGroup == null) {
            return true;
        }
        if (task == null || normalizeOptionalString(task.getAssignee()) != null) {
            return false;
        }
        return taskService.getIdentityLinksForTask(task.getId()).stream()
            .filter(link -> "candidate".equalsIgnoreCase(link.getType()))
            .map(IdentityLink::getGroupId)
            .map(this::normalizeOptionalString)
            .anyMatch(groupId -> groupId != null && groupId.equalsIgnoreCase(normalizedCandidateGroup));
    }

    private boolean matchesCandidateGroup(String taskId, String normalizedCandidateGroup) {
        if (normalizedCandidateGroup == null) {
            return true;
        }
        if (normalizeOptionalString(taskId) == null) {
            return false;
        }
        Task task = taskService.createTaskQuery()
            .taskId(taskId)
            .singleResult();
        return task != null && matchesCandidateGroup(task, normalizedCandidateGroup);
    }

    private boolean matchesProcessId(Task task, String normalizedProcessId) {
        if (normalizedProcessId == null) {
            return true;
        }
        return task != null && normalizedProcessId.equalsIgnoreCase(normalizeOptionalString(task.getProcessInstanceId()));
    }

    private boolean matchesProcessId(WorkflowTaskSummary summary, String normalizedProcessId) {
        if (normalizedProcessId == null) {
            return true;
        }
        return normalizedProcessId.equalsIgnoreCase(normalizeOptionalString(summary.processInstanceId()));
    }

    private Set<String> resolveTaskCandidateKeys(String username) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        String normalizedUsername = normalizeOptionalString(username);
        if (normalizedUsername == null) {
            return keys;
        }
        keys.add(normalizedUsername);

        userRepository.findByUsername(normalizedUsername).ifPresent(user -> user.getGroups().forEach(group -> {
            String groupName = normalizeOptionalString(group.getName());
            if (groupName != null) {
                keys.add(groupName);
            }
            if (group.getId() != null) {
                keys.add(group.getId().toString());
            }
        }));
        return keys;
    }

    private boolean isTaskClaimableFor(Task task, String username, Set<String> candidateKeys) {
        String normalizedUsername = normalizeOptionalString(username);
        if (task == null || normalizedUsername == null || normalizeOptionalString(task.getAssignee()) != null) {
            return false;
        }

        List<IdentityLink> candidateLinks = taskService.getIdentityLinksForTask(task.getId()).stream()
            .filter(link -> "candidate".equalsIgnoreCase(link.getType()))
            .toList();
        if (candidateLinks.isEmpty()) {
            return false;
        }

        for (IdentityLink link : candidateLinks) {
            String candidateUser = normalizeOptionalString(link.getUserId());
            if (candidateUser != null && candidateUser.equalsIgnoreCase(normalizedUsername)) {
                return true;
            }
            String candidateGroup = normalizeOptionalString(link.getGroupId());
            if (candidateGroup != null && candidateKeys.contains(candidateGroup)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTaskInvolvedFor(Task task, String username, Set<String> candidateKeys) {
        String normalizedUsername = normalizeOptionalString(username);
        if (task == null || normalizedUsername == null) {
            return false;
        }
        if (normalizedUsername.equalsIgnoreCase(normalizeOptionalString(task.getAssignee()))
            || normalizedUsername.equalsIgnoreCase(normalizeOptionalString(task.getOwner()))) {
            return true;
        }

        return getTaskInvolvedActors(task.getId()).stream().anyMatch(actor -> {
            String userId = normalizeOptionalString(actor.userId());
            if (userId != null && normalizedUsername.equalsIgnoreCase(userId)) {
                return true;
            }
            String groupId = normalizeOptionalString(actor.groupId());
            return groupId != null && candidateKeys.contains(groupId);
        });
    }

    private List<WorkflowVariableItem> toWorkflowVariableItems(Map<String, Object> variables, String scope) {
        if (variables == null || variables.isEmpty()) {
            return List.of();
        }

        return variables.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new WorkflowVariableItem(
                entry.getKey(),
                inferVariableType(entry.getValue()),
                entry.getValue(),
                scope
            ))
            .toList();
    }

    private List<WorkflowBusinessItem> resolveBusinessItems(String businessKey, Map<String, Object> variables, String source) {
        LinkedHashSet<String> candidateIds = new LinkedHashSet<>();
        addBusinessKeyCandidate(candidateIds, businessKey);
        if (variables != null) {
            addBusinessKeyCandidate(candidateIds, variables.get("documentId"));
            addBusinessKeyCandidate(candidateIds, variables.get("nodeId"));
        }

        List<WorkflowBusinessItem> items = new java.util.ArrayList<>();
        for (String candidateId : candidateIds) {
            try {
                UUID nodeId = UUID.fromString(candidateId);
                nodeRepository.findById(nodeId).ifPresent(node -> items.add(WorkflowBusinessItem.from(node, businessKey, source)));
            } catch (IllegalArgumentException ignored) {
                // Ignore non-UUID business keys and keep the resource additive.
            }
        }
        return items;
    }

    private void addBusinessKeyCandidate(LinkedHashSet<String> candidateIds, Object value) {
        String normalized = normalizeOptionalString(value);
        if (normalized != null) {
            candidateIds.add(normalized);
        }
    }

    private String inferVariableType(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        if (value instanceof List<?>) {
            return "array";
        }
        return value.getClass().getSimpleName();
    }

    private List<WorkflowFormModelElement> buildStartFormModel(ProcessDefinition definition) {
        if (!"documentApproval".equals(definition.getKey())) {
            return List.of();
        }

        return List.of(
            new WorkflowFormModelElement(
                "start-approvers",
                "approvers",
                "Approvers",
                "people-multi",
                true,
                false,
                true,
                "Select one or more approvers",
                null,
                List.of(),
                "start"
            ),
            new WorkflowFormModelElement(
                "start-comment",
                "comment",
                "Comment / Instructions",
                "multiline-text",
                false,
                false,
                false,
                "Add optional instructions for approvers",
                null,
                List.of(),
                "start"
            )
        );
    }

    private List<WorkflowFormModelElement> buildTaskFormModel(ProcessDefinition definition, Task task) {
        if (!"documentApproval".equals(definition.getKey()) || !"approvalTask".equals(task.getTaskDefinitionKey())) {
            return List.of();
        }

        return List.of(
            new WorkflowFormModelElement(
                "task-approved",
                "approved",
                "Decision",
                "boolean",
                true,
                false,
                false,
                "Approve or reject this document",
                null,
                List.of(
                    new WorkflowFormOption("Approve", "true"),
                    new WorkflowFormOption("Reject", "false")
                ),
                "task"
            ),
            new WorkflowFormModelElement(
                "task-comment",
                "comment",
                "Reviewer Comment",
                "multiline-text",
                false,
                false,
                false,
                "Add optional review notes",
                null,
                List.of(),
                "task"
            )
        );
    }

    private Map<String, Object> getHistoricProcessVariables(String processInstanceId) {
        List<HistoricVariableInstance> variables = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(processInstanceId)
            .list();
        if (variables == null || variables.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (HistoricVariableInstance variable : variables) {
            result.put(variable.getVariableName(), variable.getValue());
        }
        return result;
    }

    private WorkflowDocumentHistoryItem buildDocumentHistoryItem(HistoricProcessInstance historicInstance) {
        Map<String, Object> variables = getHistoricProcessVariables(historicInstance.getId());
        ProcessDefinition processDefinition = historicInstance.getProcessDefinitionId() == null
            ? null
            : repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(historicInstance.getProcessDefinitionId())
                .singleResult();

        String decisionValue = normalizeOptionalString(variables.get("decision"));
        Boolean approvedValue = normalizeOptionalBoolean(variables.get("approved"));
        String decisionLabel = normalizeOptionalString(variables.get("decisionLabel"));
        if (decisionLabel == null) {
            if ("APPROVED".equalsIgnoreCase(decisionValue) || Boolean.TRUE.equals(approvedValue)) {
                decisionLabel = "Approve";
            } else if ("REJECTED".equalsIgnoreCase(decisionValue) || Boolean.FALSE.equals(approvedValue)) {
                decisionLabel = "Reject";
            }
        }

        return new WorkflowDocumentHistoryItem(
            historicInstance.getId(),
            historicInstance.getBusinessKey(),
            processDefinition != null ? processDefinition.getKey() : null,
            processDefinition != null ? processDefinition.getName() : null,
            historicInstance.getStartTime(),
            historicInstance.getEndTime(),
            normalizeOptionalString(historicInstance.getStartUserId()),
            normalizeOptionalString(variables.get("startComment")),
            normalizeStringList(variables.get("approvers")),
            decisionValue,
            decisionLabel,
            normalizeOptionalString(variables.get("reviewedBy")),
            normalizeOptionalString(variables.get("reviewedAt")),
            normalizeOptionalString(variables.get("comment")),
            historicInstance.getEndTime() != null
        );
    }

    private WorkflowTaskSummary buildTaskSummary(Task task, String evaluationUser, Set<String> candidateKeys) {
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(task.getProcessInstanceId())
            .singleResult();
        ProcessDefinition processDefinition = task.getProcessDefinitionId() == null
            ? null
            : repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(task.getProcessDefinitionId())
                .singleResult();
        String businessKey = historicInstance != null
            ? normalizeOptionalString(historicInstance.getBusinessKey())
            : null;
        String assignee = normalizeOptionalString(task.getAssignee());
        boolean claimable = isTaskClaimableFor(task, evaluationUser, candidateKeys);
        String delegationState = toDelegationState(task);
        return new WorkflowTaskSummary(
            task.getId(),
            task.getName(),
            assignee,
            normalizeOptionalString(task.getOwner()),
            delegationState,
            task.getDescription(),
            task.getCreateTime(),
            task.getDueDate(),
            task.getProcessInstanceId(),
            task.getProcessDefinitionId(),
            task.getTaskDefinitionKey(),
            processDefinition != null ? processDefinition.getKey() : null,
            processDefinition != null ? processDefinition.getName() : null,
            processDefinition != null ? processDefinition.getVersion() : null,
            businessKey,
            historicInstance != null ? normalizeOptionalString(historicInstance.getStartUserId()) : null,
            claimable,
            deriveActiveTaskStatus(task, claimable, delegationState),
            task.getCreateTime(),
            null
        );
    }

    private WorkflowTaskSummary buildTaskSummary(org.flowable.task.api.history.HistoricTaskInstance task) {
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(task.getProcessInstanceId())
            .singleResult();
        ProcessDefinition processDefinition = task.getProcessDefinitionId() == null
            ? null
            : repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(task.getProcessDefinitionId())
                .singleResult();
        String businessKey = historicInstance != null
            ? normalizeOptionalString(historicInstance.getBusinessKey())
            : null;
        return new WorkflowTaskSummary(
            task.getId(),
            task.getName(),
            task.getAssignee(),
            normalizeOptionalString(task.getOwner()),
            null,
            task.getDescription(),
            task.getCreateTime(),
            task.getDueDate(),
            task.getProcessInstanceId(),
            task.getProcessDefinitionId(),
            task.getTaskDefinitionKey(),
            processDefinition != null ? processDefinition.getKey() : null,
            processDefinition != null ? processDefinition.getName() : null,
            processDefinition != null ? processDefinition.getVersion() : null,
            businessKey,
            historicInstance != null ? normalizeOptionalString(historicInstance.getStartUserId()) : null,
            false,
            "COMPLETED",
            task.getEndTime(),
            task.getEndTime()
        );
    }

    private boolean matchesTaskSummary(WorkflowTaskSummary summary, String normalizedQuery, String normalizedBusinessKey, String normalizedOwner) {
        if (normalizedBusinessKey != null && !containsIgnoreCase(summary.businessKey(), normalizedBusinessKey)) {
            return false;
        }
        if (!matchesOwner(summary, normalizedOwner)) {
            return false;
        }
        if (normalizedQuery == null) {
            return true;
        }
        return containsIgnoreCase(summary.name(), normalizedQuery)
            || containsIgnoreCase(summary.description(), normalizedQuery)
            || containsIgnoreCase(summary.assignee(), normalizedQuery)
            || containsIgnoreCase(summary.owner(), normalizedQuery)
            || containsIgnoreCase(summary.taskDefinitionKey(), normalizedQuery)
            || containsIgnoreCase(summary.processDefinitionKey(), normalizedQuery)
            || containsIgnoreCase(summary.processDefinitionName(), normalizedQuery)
            || containsIgnoreCase(summary.businessKey(), normalizedQuery)
            || containsIgnoreCase(summary.startedBy(), normalizedQuery)
            || containsIgnoreCase(summary.delegationState(), normalizedQuery)
            || containsIgnoreCase(summary.status(), normalizedQuery);
    }

    private boolean matchesProcessBrowserItem(
        WorkflowProcessBrowserItem item,
        String normalizedBusinessKey,
        String normalizedStartedBy,
        String normalizedDefinitionKey,
        String normalizedQuery
    ) {
        if (normalizedBusinessKey != null && !containsIgnoreCase(item.businessKey(), normalizedBusinessKey)) {
            return false;
        }
        if (normalizedStartedBy != null && !containsIgnoreCase(item.startedBy(), normalizedStartedBy)) {
            return false;
        }
        if (normalizedDefinitionKey != null && !containsIgnoreCase(item.processDefinitionKey(), normalizedDefinitionKey)) {
            return false;
        }
        if (normalizedQuery == null) {
            return true;
        }
        return containsIgnoreCase(item.processDefinitionKey(), normalizedQuery)
            || containsIgnoreCase(item.processDefinitionName(), normalizedQuery)
            || containsIgnoreCase(item.businessKey(), normalizedQuery)
            || containsIgnoreCase(item.startedBy(), normalizedQuery)
            || containsIgnoreCase(item.submissionSummary() != null ? item.submissionSummary().decision() : null, normalizedQuery)
            || containsIgnoreCase(item.submissionSummary() != null ? item.submissionSummary().decisionLabel() : null, normalizedQuery)
            || containsIgnoreCase(item.ended() ? "completed" : "active", normalizedQuery);
    }

    private WorkflowProcessBrowserItem buildProcessBrowserItem(HistoricProcessInstance historicInstance) {
        ProcessDefinition processDefinition = historicInstance.getProcessDefinitionId() == null
            ? null
            : repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(historicInstance.getProcessDefinitionId())
                .singleResult();
        Map<String, Object> variables = getHistoricProcessVariables(historicInstance.getId());
        return new WorkflowProcessBrowserItem(
            historicInstance.getId(),
            historicInstance.getProcessDefinitionId(),
            processDefinition != null ? processDefinition.getKey() : null,
            processDefinition != null ? processDefinition.getName() : null,
            processDefinition != null ? processDefinition.getVersion() : null,
            normalizeOptionalString(historicInstance.getBusinessKey()),
            normalizeOptionalString(historicInstance.getStartUserId()),
            historicInstance.getStartTime(),
            historicInstance.getEndTime(),
            historicInstance.getEndTime() != null,
            buildSubmissionSummary(
                variables,
                normalizeOptionalString(historicInstance.getStartUserId()),
                historicInstance.getStartTime(),
                historicInstance.getEndTime()
            )
        );
    }

    private String normalizeTaskScope(String scope) {
        String normalizedScope = normalizeOptionalString(scope);
        if (normalizedScope == null) {
            return "ASSIGNED";
        }
        String upper = normalizedScope.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "ASSIGNED", "CLAIMABLE", "COMPLETED", "ALL", "SHARED", "ALL_ACTIVE", "INVOLVED" -> upper;
            default -> throw new IllegalArgumentException("Unsupported workflow task scope: " + scope);
        };
    }

    private String normalizeProcessStatus(String status) {
        String normalizedStatus = normalizeOptionalString(status);
        if (normalizedStatus == null) {
            return "ACTIVE";
        }
        String upper = normalizedStatus.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "ACTIVE", "COMPLETED", "ALL" -> upper;
            default -> throw new IllegalArgumentException("Unsupported workflow process status: " + status);
        };
    }

    private String normalizeTaskTransition(String state) {
        String normalizedState = normalizeOptionalString(state);
        if (normalizedState == null) {
            throw new IllegalArgumentException("Workflow task state transition is required");
        }
        String upper = normalizedState.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "COMPLETED", "CLAIMED", "UNCLAIMED", "ASSIGNED", "DELEGATED", "RESOLVED" -> upper;
            default -> throw new IllegalArgumentException("Unsupported workflow task state transition: " + state);
        };
    }

    private ProcessInstance requireRuntimeProcessInstance(String processInstanceId) {
        ProcessInstance runtimeInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        if (runtimeInstance == null) {
            HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
            if (historicInstance != null && historicInstance.getEndTime() != null) {
                throw new IllegalStateException("Workflow process is already completed: " + processInstanceId);
            }
            throw new ResourceNotFoundException("Process instance not found: " + processInstanceId);
        }
        return runtimeInstance;
    }

    private void assertProcessVariableWriteAccess(String processInstanceId, HistoricProcessInstance historicInstance) {
        String currentUser = securityService.getCurrentUser();
        if (securityService.isAdmin(currentUser)) {
            return;
        }
        String startedBy = historicInstance != null ? normalizeOptionalString(historicInstance.getStartUserId()) : null;
        if (startedBy != null && startedBy.equalsIgnoreCase(currentUser)) {
            return;
        }
        throw new SecurityException("You are not allowed to edit workflow variables for process " + processInstanceId);
    }

    private boolean containsIgnoreCase(String source, String needle) {
        if (source == null || needle == null) {
            return false;
        }
        return source.toLowerCase().contains(needle.toLowerCase());
    }

    private ProcessDefinition resolveStartProcessDefinition(String processDefinitionId, String processDefinitionKey) {
        String normalizedDefinitionId = normalizeOptionalString(processDefinitionId);
        String normalizedDefinitionKey = normalizeOptionalString(processDefinitionKey);
        if (normalizedDefinitionId == null && normalizedDefinitionKey == null) {
            throw new IllegalArgumentException("processDefinitionId or processDefinitionKey is required");
        }
        if (normalizedDefinitionId != null && normalizedDefinitionKey != null) {
            throw new IllegalArgumentException("Provide either processDefinitionId or processDefinitionKey, not both");
        }

        ProcessDefinition definition;
        if (normalizedDefinitionId != null) {
            definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(normalizedDefinitionId)
                .singleResult();
            if (definition == null) {
                throw new ResourceNotFoundException("Process definition not found: " + normalizedDefinitionId);
            }
            return definition;
        }

        definition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(normalizedDefinitionKey)
            .latestVersion()
            .singleResult();
        if (definition == null) {
            throw new ResourceNotFoundException("Process definition not found: " + normalizedDefinitionKey);
        }
        return definition;
    }

    private List<Node> resolveStartProcessItems(List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }

        List<Node> items = new ArrayList<>();
        for (String itemId : itemIds) {
            String normalizedItemId = normalizeOptionalString(itemId);
            if (normalizedItemId == null) {
                continue;
            }
            UUID nodeId;
            try {
                nodeId = UUID.fromString(normalizedItemId);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid workflow item id: " + normalizedItemId);
            }
            Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow item not found: " + normalizedItemId));
            items.add(node);
        }
        return items;
    }

    private String toDelegationState(Task task) {
        if (task == null || task.getDelegationState() == null) {
            return null;
        }
        return task.getDelegationState().name();
    }

    private String deriveActiveTaskStatus(Task task, boolean claimable, String delegationState) {
        if ("PENDING".equalsIgnoreCase(delegationState)) {
            return "DELEGATED";
        }
        String assignee = normalizeOptionalString(task != null ? task.getAssignee() : null);
        if (assignee == null) {
            return claimable ? "CLAIMABLE" : "SHARED";
        }
        return "ASSIGNED";
    }

    private void registerIdentityLinkActor(Map<String, WorkflowActorAccumulator> actors, IdentityLink link) {
        if (link == null) {
            return;
        }
        String type = normalizeOptionalString(link.getType());
        if (type == null) {
            return;
        }
        String userId = normalizeOptionalString(link.getUserId());
        if (userId != null) {
            registerUserActor(actors, userId, type);
        }
        String groupId = normalizeOptionalString(link.getGroupId());
        if (groupId != null) {
            registerGroupActor(actors, groupId, type);
        }
    }

    private void registerUserActor(Map<String, WorkflowActorAccumulator> actors, String username, String role) {
        String normalizedUsername = normalizeOptionalString(username);
        String normalizedRole = normalizeOptionalString(role);
        if (normalizedUsername == null || normalizedRole == null) {
            return;
        }
        WorkflowActorAccumulator actor = actors.computeIfAbsent(
            "user:" + normalizedUsername,
            key -> new WorkflowActorAccumulator(normalizedUsername, null, resolveUserDisplayName(normalizedUsername))
        );
        actor.roles().add(normalizedRole);
    }

    private void registerGroupActor(Map<String, WorkflowActorAccumulator> actors, String groupId, String role) {
        String normalizedGroupId = normalizeOptionalString(groupId);
        String normalizedRole = normalizeOptionalString(role);
        if (normalizedGroupId == null || normalizedRole == null) {
            return;
        }
        WorkflowActorAccumulator actor = actors.computeIfAbsent(
            "group:" + normalizedGroupId,
            key -> new WorkflowActorAccumulator(null, normalizedGroupId, normalizedGroupId)
        );
        actor.roles().add(normalizedRole);
    }

    private List<WorkflowInvolvedActor> toInvolvedActors(Map<String, WorkflowActorAccumulator> actors) {
        return actors.values().stream()
            .map(actor -> new WorkflowInvolvedActor(
                actor.userId(),
                actor.groupId(),
                actor.displayName(),
                List.copyOf(actor.roles())
            ))
            .toList();
    }

    private String resolveUserDisplayName(String username) {
        return userRepository.findByUsername(username)
            .map(this::resolveUserDisplayName)
            .orElse(username);
    }

    private String resolveUserDisplayName(User user) {
        String displayName = normalizeOptionalString(user.getDisplayName());
        if (displayName != null) {
            return displayName;
        }
        String fullName = List.of(
                normalizeOptionalString(user.getFirstName()),
                normalizeOptionalString(user.getLastName())
            ).stream()
            .filter(java.util.Objects::nonNull)
            .reduce((left, right) -> left + " " + right)
            .orElse(null);
        return fullName != null ? fullName : user.getUsername();
    }

    private record WorkflowActorAccumulator(
        String userId,
        String groupId,
        String displayName,
        LinkedHashSet<String> roles
    ) {
        private WorkflowActorAccumulator(String userId, String groupId, String displayName) {
            this(userId, groupId, displayName, new LinkedHashSet<>());
        }
    }

    public record WorkflowProcessDetail(
        String id,
        String processDefinitionId,
        String processDefinitionKey,
        String processDefinitionName,
        Integer processDefinitionVersion,
        String businessKey,
        String startedBy,
        java.util.Date startTime,
        java.util.Date endTime,
        boolean suspended,
        boolean ended,
        Map<String, Object> variables,
        WorkflowSubmissionSummary submissionSummary
    ) {}

    public record WorkflowProcessTask(
        String id,
        String name,
        String assignee,
        String owner,
        String description,
        java.util.Date createTime,
        java.util.Date dueDate,
        String taskDefinitionKey,
        String delegationState
    ) {}

    public record WorkflowProcessActivity(
        String id,
        String activityId,
        String activityName,
        String activityType,
        String executionId,
        String taskId,
        String assignee,
        java.util.Date startTime,
        java.util.Date endTime,
        Long durationInMillis
    ) {}

    public record WorkflowHistoricTaskItem(
        String id,
        String name,
        String assignee,
        String owner,
        String description,
        String taskDefinitionKey,
        java.util.Date startTime,
        java.util.Date endTime,
        Long durationInMillis,
        String deleteReason
    ) {}

    public record WorkflowTaskDetail(
        String id,
        String name,
        String description,
        String assignee,
        String owner,
        String delegationState,
        java.util.Date createTime,
        java.util.Date dueDate,
        String processInstanceId,
        String processDefinitionId,
        String taskDefinitionKey,
        String processDefinitionKey,
        String processDefinitionName,
        Integer processDefinitionVersion,
        String businessKey,
        Map<String, Object> processVariables,
        Boolean processDefinitionSuspended,
        WorkflowSubmissionSummary submissionSummary
    ) {}

    public record WorkflowTaskCandidate(
        String userId,
        String groupId,
        String type
    ) {}

    public record WorkflowInvolvedActor(
        String userId,
        String groupId,
        String displayName,
        List<String> roles
    ) {}

    public record WorkflowTaskSummary(
        String id,
        String name,
        String assignee,
        String owner,
        String delegationState,
        String description,
        java.util.Date createTime,
        java.util.Date dueDate,
        String processInstanceId,
        String processDefinitionId,
        String taskDefinitionKey,
        String processDefinitionKey,
        String processDefinitionName,
        Integer processDefinitionVersion,
        String businessKey,
        String startedBy,
        boolean claimable,
        String status,
        java.util.Date completedAt,
        java.util.Date sortTime
    ) {}

    public record WorkflowProcessBrowserItem(
        String id,
        String processDefinitionId,
        String processDefinitionKey,
        String processDefinitionName,
        Integer processDefinitionVersion,
        String businessKey,
        String startedBy,
        java.util.Date startTime,
        java.util.Date endTime,
        boolean ended,
        WorkflowSubmissionSummary submissionSummary
    ) {}

    public record WorkflowProcessBrowserPage(
        List<WorkflowProcessBrowserItem> items,
        int skipCount,
        int maxItems,
        long totalItems,
        boolean hasMoreItems
    ) {}

    public record WorkflowDefinitionDetail(
        String id,
        String key,
        String name,
        String description,
        Integer version,
        String category,
        String deploymentId,
        String tenantId,
        boolean suspended,
        boolean hasStartFormKey,
        boolean hasGraphicalNotation,
        String modelResourceName,
        String diagramResourceName,
        boolean bpmnXmlAvailable,
        boolean diagramAvailable,
        List<String> resourceNames
    ) {}

    public record WorkflowDefinitionModel(
        String definitionId,
        String resourceName,
        String xml
    ) {}

    public record WorkflowDefinitionDiagram(
        String definitionId,
        String resourceName,
        byte[] bytes
    ) {}

    public record WorkflowStartFormSubmission(
        List<String> approvers,
        String comment
    ) {}

    public record WorkflowSubmissionSummary(
        List<String> approvers,
        String startComment,
        String startFormSubmittedBy,
        String startFormSubmittedAt,
        String decision,
        String decisionLabel,
        String reviewedBy,
        String reviewedAt,
        String comment
    ) {}

    public record WorkflowVariableItem(
        String name,
        String type,
        Object value,
        String scope
    ) {}

    public record WorkflowBusinessItem(
        String id,
        String name,
        String nodeType,
        String path,
        String businessKey,
        String source
    ) {
        static WorkflowBusinessItem from(Node node, String businessKey, String source) {
            return new WorkflowBusinessItem(
                node.getId().toString(),
                node.getName(),
                node.getNodeType().name(),
                node.getPath(),
                businessKey,
                source
            );
        }
    }

    public record WorkflowDocumentHistoryItem(
        String id,
        String businessKey,
        String processDefinitionKey,
        String processDefinitionName,
        java.util.Date startTime,
        java.util.Date endTime,
        String startedBy,
        String startComment,
        List<String> approvers,
        String decision,
        String decisionLabel,
        String reviewedBy,
        String reviewedAt,
        String comment,
        boolean ended
    ) {}

    public record WorkflowFormModelElement(
        String id,
        String name,
        String title,
        String type,
        boolean required,
        boolean readOnly,
        boolean repeated,
        String placeholder,
        Object defaultValue,
        List<WorkflowFormOption> options,
        String scope
    ) {}

    public record WorkflowFormOption(
        String label,
        String value
    ) {}
}
