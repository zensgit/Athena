package com.ecm.core.service;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.Node.NodeStatus;
import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final SecurityService securityService;
    private final AuditService auditService;

    /**
     * Start document approval workflow
     */
    @Transactional
    public ProcessInstance startDocumentApproval(UUID documentId, List<String> approvers, String comment) {
        Node node = nodeRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Node not found: " + documentId));

        if (approvers == null || approvers.isEmpty()) {
            throw new IllegalArgumentException("At least one approver is required");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("documentId", documentId.toString());
        variables.put("documentName", node.getName());
        variables.put("approver", approvers.get(0)); // Simple: assign to first approver
        variables.put("approvers", approvers);
        variables.put("initiator", securityService.getCurrentUser());
        variables.put("startComment", comment);

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "documentApproval",
            documentId.toString(),
            variables
        );

        auditService.logEvent("WORKFLOW_STARTED", node.getId(), node.getName(), 
            securityService.getCurrentUser(), "Started approval workflow: " + instance.getId());

        log.info("Started workflow {} for document {}", instance.getId(), documentId);
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
            .list();
    }

    /**
     * Get tasks for a specific process instance
     */
    public List<Task> getProcessTasks(String processInstanceId) {
        return taskService.createTaskQuery()
            .processInstanceId(processInstanceId)
            .list();
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

        // Verify assignee (optional, but good practice)
        String currentUser = securityService.getCurrentUser();
        if (task.getAssignee() != null && !task.getAssignee().equals(currentUser)) {
            // In real app, check for admin or delegate permissions
            // For now, allow completion if user is admin or assignee
            if (!securityService.isAdmin(currentUser)) {
                throw new SecurityException("You are not assigned to this task");
            }
        }

        taskService.complete(taskId, variables);
        log.info("Completed task {} by {}", taskId, currentUser);
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
        return runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(documentId.toString())
            .list();
    }

    /**
     * Get workflow history for a document
     */
    public List<HistoricProcessInstance> getDocumentHistory(UUID documentId) {
        return historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(documentId.toString())
            .finished()
            .orderByProcessInstanceEndTime().desc()
            .list();
    }
}
