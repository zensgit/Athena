package com.ecm.core.controller;

import com.ecm.core.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Tag(name = "Workflow Management", description = "APIs for business processes and approvals")
public class WorkflowController {
    
    private final WorkflowService workflowService;
    
    @GetMapping("/definitions")
    @Operation(summary = "List definitions", description = "Get available workflow definitions")
    public ResponseEntity<List<ProcessDefinitionResponse>> getDefinitions() {
        List<ProcessDefinitionResponse> definitions = workflowService.getProcessDefinitions().stream()
            .map(ProcessDefinitionResponse::from)
            .collect(Collectors.toList());
        return ResponseEntity.ok(definitions);
    }
    
    @PostMapping("/document/{documentId}/approval")
    @Operation(summary = "Start approval", description = "Start document approval workflow")
    public ResponseEntity<ProcessInstanceResponse> startApproval(
            @PathVariable UUID documentId,
            @RequestBody StartApprovalRequest request) {
        
        ProcessInstance instance = workflowService.startDocumentApproval(
            documentId, 
            request.approvers(), 
            request.comment()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ProcessInstanceResponse.from(instance));
    }
    
    @GetMapping("/tasks/my")
    @Operation(summary = "My tasks", description = "Get tasks assigned to current user")
    public ResponseEntity<List<TaskResponse>> getMyTasks() {
        List<TaskResponse> tasks = workflowService.getMyTasks().stream()
            .map(TaskResponse::from)
            .collect(Collectors.toList());
        return ResponseEntity.ok(tasks);
    }
    
    @PostMapping("/tasks/{taskId}/complete")
    @Operation(summary = "Complete task", description = "Complete a workflow task with variables (e.g. approved=true)")
    public ResponseEntity<Void> completeTask(
            @PathVariable String taskId,
            @RequestBody Map<String, Object> variables) {
        
        workflowService.completeTask(taskId, variables);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/document/{documentId}/instances")
    @Operation(summary = "Document workflows", description = "Get active workflows for a document")
    public ResponseEntity<List<ProcessInstanceResponse>> getDocumentWorkflows(@PathVariable UUID documentId) {
        List<ProcessInstanceResponse> instances = workflowService.getDocumentWorkflows(documentId).stream()
            .map(ProcessInstanceResponse::from)
            .collect(Collectors.toList());
        return ResponseEntity.ok(instances);
    }
    
    @GetMapping("/document/{documentId}/history")
    @Operation(summary = "Document history", description = "Get completed workflows for a document")
    public ResponseEntity<List<HistoricProcessInstanceResponse>> getDocumentHistory(@PathVariable UUID documentId) {
        List<HistoricProcessInstanceResponse> history = workflowService.getDocumentHistory(documentId).stream()
            .map(HistoricProcessInstanceResponse::from)
            .collect(Collectors.toList());
        return ResponseEntity.ok(history);
    }

    // ==================== DTOs ====================

    public record StartApprovalRequest(List<String> approvers, String comment) {}

    public record ProcessDefinitionResponse(String id, String key, String name, int version) {
        static ProcessDefinitionResponse from(ProcessDefinition pd) {
            return new ProcessDefinitionResponse(pd.getId(), pd.getKey(), pd.getName(), pd.getVersion());
        }
    }

    public record ProcessInstanceResponse(String id, String definitionKey, String businessKey, boolean ended) {
        static ProcessInstanceResponse from(ProcessInstance pi) {
            return new ProcessInstanceResponse(pi.getId(), pi.getProcessDefinitionKey(), pi.getBusinessKey(), pi.isEnded());
        }
    }

    public record TaskResponse(String id, String name, String assignee, String description, java.util.Date createTime) {
        static TaskResponse from(Task t) {
            return new TaskResponse(t.getId(), t.getName(), t.getAssignee(), t.getDescription(), t.getCreateTime());
        }
    }
    
    public record HistoricProcessInstanceResponse(String id, String businessKey, java.util.Date startTime, java.util.Date endTime) {
        static HistoricProcessInstanceResponse from(HistoricProcessInstance hpi) {
            return new HistoricProcessInstanceResponse(hpi.getId(), hpi.getBusinessKey(), hpi.getStartTime(), hpi.getEndTime());
        }
    }
}