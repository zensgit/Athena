package com.ecm.core.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Tag(name = "Workflow Management", description = "APIs for managing workflows and processes")
public class WorkflowController {
    
    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    
    @GetMapping("/definitions")
    @Operation(summary = "List workflow definitions", description = "Get all deployed workflow definitions")
    public ResponseEntity<List<ProcessDefinition>> getProcessDefinitions() {
        List<ProcessDefinition> definitions = repositoryService.createProcessDefinitionQuery()
            .latestVersion()
            .list();
        return ResponseEntity.ok(definitions);
    }
    
    @GetMapping("/definitions/{definitionId}")
    @Operation(summary = "Get workflow definition", description = "Get a specific workflow definition")
    public ResponseEntity<ProcessDefinition> getProcessDefinition(
            @Parameter(description = "Definition ID") @PathVariable String definitionId) {
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionId(definitionId)
            .singleResult();
        return ResponseEntity.ok(definition);
    }
    
    @PostMapping("/instances")
    @Operation(summary = "Start workflow", description = "Start a new workflow instance")
    public ResponseEntity<ProcessInstance> startWorkflow(
            @Parameter(description = "Process definition key") @RequestParam String processKey,
            @Parameter(description = "Business key") @RequestParam(required = false) String businessKey,
            @RequestBody(required = false) Map<String, Object> variables) {
        
        if (variables == null) {
            variables = new HashMap<>();
        }
        
        ProcessInstance instance;
        if (businessKey != null) {
            instance = runtimeService.startProcessInstanceByKey(processKey, businessKey, variables);
        } else {
            instance = runtimeService.startProcessInstanceByKey(processKey, variables);
        }
        
        return ResponseEntity.status(HttpStatus.CREATED).body(instance);
    }
    
    @GetMapping("/instances")
    @Operation(summary = "List workflow instances", description = "Get active workflow instances")
    public ResponseEntity<List<ProcessInstance>> getProcessInstances(
            @Parameter(description = "Process definition key") @RequestParam(required = false) String processKey,
            @Parameter(description = "Business key") @RequestParam(required = false) String businessKey) {
        
        var query = runtimeService.createProcessInstanceQuery();
        
        if (processKey != null) {
            query.processDefinitionKey(processKey);
        }
        if (businessKey != null) {
            query.processInstanceBusinessKey(businessKey);
        }
        
        List<ProcessInstance> instances = query.list();
        return ResponseEntity.ok(instances);
    }
    
    @GetMapping("/instances/{instanceId}")
    @Operation(summary = "Get workflow instance", description = "Get a specific workflow instance")
    public ResponseEntity<ProcessInstance> getProcessInstance(
            @Parameter(description = "Instance ID") @PathVariable String instanceId) {
        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(instanceId)
            .singleResult();
        return ResponseEntity.ok(instance);
    }
    
    @DeleteMapping("/instances/{instanceId}")
    @Operation(summary = "Cancel workflow", description = "Cancel a workflow instance")
    public ResponseEntity<Void> cancelWorkflow(
            @Parameter(description = "Instance ID") @PathVariable String instanceId,
            @Parameter(description = "Reason") @RequestParam(required = false) String reason) {
        
        runtimeService.deleteProcessInstance(instanceId, reason != null ? reason : "Cancelled by user");
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/tasks")
    @Operation(summary = "List tasks", description = "Get tasks for current user")
    public ResponseEntity<List<Task>> getTasks(
            @Parameter(description = "Assignee") @RequestParam(required = false) String assignee,
            @Parameter(description = "Process instance ID") @RequestParam(required = false) String processInstanceId) {
        
        var query = taskService.createTaskQuery();
        
        if (assignee != null) {
            query.taskAssignee(assignee);
        }
        if (processInstanceId != null) {
            query.processInstanceId(processInstanceId);
        }
        
        List<Task> tasks = query.list();
        return ResponseEntity.ok(tasks);
    }
    
    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "Get task", description = "Get a specific task")
    public ResponseEntity<Task> getTask(
            @Parameter(description = "Task ID") @PathVariable String taskId) {
        Task task = taskService.createTaskQuery()
            .taskId(taskId)
            .singleResult();
        return ResponseEntity.ok(task);
    }
    
    @PostMapping("/tasks/{taskId}/complete")
    @Operation(summary = "Complete task", description = "Complete a workflow task")
    public ResponseEntity<Void> completeTask(
            @Parameter(description = "Task ID") @PathVariable String taskId,
            @RequestBody(required = false) Map<String, Object> variables) {
        
        if (variables != null) {
            taskService.complete(taskId, variables);
        } else {
            taskService.complete(taskId);
        }
        
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/tasks/{taskId}/claim")
    @Operation(summary = "Claim task", description = "Claim a task for the current user")
    public ResponseEntity<Void> claimTask(
            @Parameter(description = "Task ID") @PathVariable String taskId,
            @Parameter(description = "User ID") @RequestParam String userId) {
        
        taskService.claim(taskId, userId);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/tasks/{taskId}/unclaim")
    @Operation(summary = "Unclaim task", description = "Release a claimed task")
    public ResponseEntity<Void> unclaimTask(
            @Parameter(description = "Task ID") @PathVariable String taskId) {
        
        taskService.unclaim(taskId);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/tasks/{taskId}/delegate")
    @Operation(summary = "Delegate task", description = "Delegate a task to another user")
    public ResponseEntity<Void> delegateTask(
            @Parameter(description = "Task ID") @PathVariable String taskId,
            @Parameter(description = "User ID") @RequestParam String userId) {
        
        taskService.delegateTask(taskId, userId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/history/instances")
    @Operation(summary = "Get workflow history", description = "Get completed workflow instances")
    public ResponseEntity<List<HistoricProcessInstance>> getProcessHistory(
            @Parameter(description = "Process definition key") @RequestParam(required = false) String processKey,
            @Parameter(description = "Finished only") @RequestParam(defaultValue = "true") boolean finishedOnly) {
        
        var query = historyService.createHistoricProcessInstanceQuery();
        
        if (processKey != null) {
            query.processDefinitionKey(processKey);
        }
        if (finishedOnly) {
            query.finished();
        }
        
        List<HistoricProcessInstance> history = query.list();
        return ResponseEntity.ok(history);
    }
    
    @PostMapping("/document/{documentId}/start-approval")
    @Operation(summary = "Start document approval", description = "Start an approval workflow for a document")
    public ResponseEntity<ProcessInstance> startDocumentApproval(
            @Parameter(description = "Document ID") @PathVariable UUID documentId,
            @Parameter(description = "Approvers") @RequestBody List<String> approvers) {
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("documentId", documentId.toString());
        variables.put("approvers", approvers);
        variables.put("initiator", getCurrentUser());
        
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "documentApproval", 
            documentId.toString(), 
            variables
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(instance);
    }
    
    private String getCurrentUser() {
        // This should get from SecurityContext
        return "currentUser";
    }
}