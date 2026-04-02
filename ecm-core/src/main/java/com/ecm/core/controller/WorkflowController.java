package com.ecm.core.controller;

import com.ecm.core.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
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

    @GetMapping("/definitions/{definitionId}")
    @Operation(summary = "Workflow definition detail", description = "Get workflow definition metadata and deployment resources")
    public ResponseEntity<ProcessDefinitionDetailResponse> getDefinitionDetail(
        @Parameter(description = "Process definition ID") @PathVariable String definitionId
    ) {
        return ResponseEntity.ok(ProcessDefinitionDetailResponse.from(
            workflowService.getDefinitionDetail(definitionId)
        ));
    }

    @GetMapping("/definitions/{definitionId}/model")
    @Operation(summary = "Workflow definition model", description = "Get workflow BPMN XML model")
    public ResponseEntity<ProcessDefinitionModelResponse> getDefinitionModel(
        @Parameter(description = "Process definition ID") @PathVariable String definitionId
    ) {
        return ResponseEntity.ok(ProcessDefinitionModelResponse.from(
            workflowService.getDefinitionModel(definitionId)
        ));
    }

    @GetMapping("/definitions/{definitionId}/diagram")
    @Operation(summary = "Workflow definition diagram", description = "Get workflow diagram/image binary")
    public ResponseEntity<ByteArrayResource> getDefinitionDiagram(
        @Parameter(description = "Process definition ID") @PathVariable String definitionId
    ) {
        return toBinaryResponse(workflowService.getDefinitionDiagram(definitionId));
    }

    @GetMapping("/definitions/{definitionId}/start-form-model")
    @Operation(summary = "Workflow definition start form model", description = "Get the form model fields used to start a workflow definition")
    public ResponseEntity<List<FormModelElementResponse>> getStartFormModel(
        @Parameter(description = "Process definition ID") @PathVariable String definitionId
    ) {
        return ResponseEntity.ok(
            workflowService.getStartFormModel(definitionId).stream()
                .map(FormModelElementResponse::from)
                .collect(Collectors.toList())
        );
    }

    @GetMapping("/processes/{processId}/diagram")
    @Operation(summary = "Workflow process diagram", description = "Get workflow process diagram/image binary")
    public ResponseEntity<ByteArrayResource> getProcessDiagram(
        @Parameter(description = "Process instance ID") @PathVariable String processId
    ) {
        return toBinaryResponse(workflowService.getProcessDiagram(processId));
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

    @PostMapping("/document/{documentId}/approval/form-submit")
    @Operation(summary = "Submit approval start form", description = "Start document approval workflow using the published start form model fields")
    public ResponseEntity<ProcessInstanceResponse> submitApprovalStartForm(
        @PathVariable UUID documentId,
        @RequestBody FormSubmissionRequest request
    ) {
        ProcessInstance instance = workflowService.submitStartForm(documentId, request.values());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ProcessInstanceResponse.from(instance));
    }

    @PostMapping("/processes")
    @Operation(summary = "Start process instance", description = "Start a workflow process using a definition id/key, business key, variables and attached items")
    public ResponseEntity<ProcessInstanceResponse> startProcess(
        @RequestBody StartProcessRequest request
    ) {
        ProcessInstance instance = workflowService.startProcess(
            request.processDefinitionId(),
            request.processDefinitionKey(),
            request.businessKey(),
            request.variables(),
            request.items() == null ? List.of() : request.items().stream().map(StartProcessItemRequest::id).toList()
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

    @GetMapping({"/tasks", "/tasks/inbox"})
    @Operation(summary = "Workflow task inbox", description = "List workflow tasks for the current inbox scope with optional search filters")
    public ResponseEntity<List<TaskInboxItemResponse>> listTasks(
        @RequestParam(required = false) String scope,
        @RequestParam(required = false) String query,
        @RequestParam(required = false, name = "q") String legacyQuery,
        @RequestParam(required = false) String businessKey,
        @RequestParam(required = false) String assignee,
        @RequestParam(required = false) String processId,
        @RequestParam(required = false) String owner,
        @RequestParam(required = false) String candidateUser,
        @RequestParam(required = false) String candidateGroup
    ) {
        String effectiveQuery = query != null ? query : legacyQuery;
        return ResponseEntity.ok(
            workflowService.listTasks(scope, effectiveQuery, businessKey, assignee, processId, owner, candidateUser, candidateGroup).stream()
                .map(TaskInboxItemResponse::from)
                .collect(Collectors.toList())
        );
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "Task detail", description = "Get workflow task detail including process definition and variables")
    public ResponseEntity<TaskDetailResponse> getTaskDetail(
        @Parameter(description = "Task ID") @PathVariable String taskId
    ) {
        return ResponseEntity.ok(TaskDetailResponse.from(workflowService.getTaskDetail(taskId)));
    }

    @GetMapping("/tasks/{taskId}/task-form-model")
    @Operation(summary = "Workflow task form model", description = "Get the form model fields used to complete a workflow task")
    public ResponseEntity<List<FormModelElementResponse>> getTaskFormModel(
        @Parameter(description = "Task ID") @PathVariable String taskId
    ) {
        return ResponseEntity.ok(
            workflowService.getTaskFormModel(taskId).stream()
                .map(FormModelElementResponse::from)
                .collect(Collectors.toList())
        );
    }

    @GetMapping("/processes/{processId}")
    @Operation(summary = "Process detail", description = "Get workflow process detail including variables and lifecycle metadata")
    public ResponseEntity<ProcessDetailResponse> getProcessDetail(
        @Parameter(description = "Process instance ID") @PathVariable String processId
    ) {
        return ResponseEntity.ok(ProcessDetailResponse.from(workflowService.getProcessDetail(processId)));
    }

    @GetMapping({"/processes", "/processes/browser"})
    @Operation(summary = "Workflow process browser", description = "List workflow process instances with status, starter, business key and paging filters")
    public ResponseEntity<ProcessBrowserListResponse> listProcesses(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String businessKey,
        @RequestParam(required = false) String startedBy,
        @RequestParam(required = false) String definitionKey,
        @RequestParam(required = false) String query,
        @RequestParam(required = false, name = "q") String legacyQuery,
        @RequestParam(defaultValue = "0") int skipCount,
        @RequestParam(defaultValue = "20") int maxItems
    ) {
        String effectiveQuery = query != null ? query : legacyQuery;
        return ResponseEntity.ok(
            ProcessBrowserListResponse.from(
                workflowService.listProcesses(status, businessKey, startedBy, definitionKey, effectiveQuery, skipCount, maxItems)
            )
        );
    }

    @GetMapping("/processes/{processId}/tasks")
    @Operation(summary = "Process tasks", description = "Get active tasks for a workflow process instance")
    public ResponseEntity<List<ProcessTaskResponse>> getProcessTasks(
        @Parameter(description = "Process instance ID") @PathVariable String processId
    ) {
        return ResponseEntity.ok(
            workflowService.getProcessTaskDetails(processId).stream()
                .map(ProcessTaskResponse::from)
                .collect(Collectors.toList())
        );
    }

    @GetMapping("/processes/{processId}/task-history")
    @Operation(summary = "Process task history", description = "Get completed workflow tasks for a process instance")
    public ResponseEntity<List<HistoricTaskResponse>> getProcessTaskHistory(
        @Parameter(description = "Process instance ID") @PathVariable String processId,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) String assignee,
        @RequestParam(required = false) String taskDefinitionKey
    ) {
        return ResponseEntity.ok(
            workflowService.getProcessTaskHistory(processId, query, assignee, taskDefinitionKey).stream()
                .map(HistoricTaskResponse::from)
                .collect(Collectors.toList())
        );
    }

    @GetMapping("/processes/{processId}/activities")
    @Operation(summary = "Process activities", description = "Get workflow activity timeline for a process instance")
    public ResponseEntity<List<ProcessActivityResponse>> getProcessActivities(
        @Parameter(description = "Process instance ID") @PathVariable String processId,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) String assignee,
        @RequestParam(required = false) String activityType
    ) {
        return ResponseEntity.ok(
            workflowService.getProcessActivities(processId, query, assignee, activityType).stream()
                .map(ProcessActivityResponse::from)
                .collect(Collectors.toList())
        );
    }

    @GetMapping("/processes/{processId}/involved")
    @Operation(summary = "Process involved actors", description = "Get people and groups involved in a workflow process instance")
    public ResponseEntity<List<InvolvedActorResponse>> getProcessInvolvedActors(
        @Parameter(description = "Process instance ID") @PathVariable String processId
    ) {
        return ResponseEntity.ok(
            workflowService.getProcessInvolvedActors(processId).stream()
                .map(InvolvedActorResponse::from)
                .collect(Collectors.toList())
        );
    }

    @GetMapping("/processes/{processId}/variables")
    @Operation(summary = "Process variables", description = "Get workflow process variables as a stable resource list")
    public ResponseEntity<List<WorkflowVariableResponse>> getProcessVariables(
        @Parameter(description = "Process instance ID") @PathVariable String processId
    ) {
        return ResponseEntity.ok(
            workflowService.getProcessVariables(processId).stream()
                .map(WorkflowVariableResponse::from)
                .collect(Collectors.toList())
        );
    }

    @PutMapping("/processes/{processId}/variables/{variableName}")
    @Operation(summary = "Upsert process variable", description = "Create or replace a workflow process variable for an active process instance")
    public ResponseEntity<Void> upsertProcessVariable(
        @Parameter(description = "Process instance ID") @PathVariable String processId,
        @Parameter(description = "Variable name") @PathVariable String variableName,
        @RequestBody(required = false) VariableValueWriteRequest request
    ) {
        workflowService.upsertProcessVariable(processId, variableName, request != null ? request.value() : null);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/processes/{processId}/variables/{variableName}")
    @Operation(summary = "Delete process variable", description = "Delete a workflow process variable from an active process instance")
    public ResponseEntity<Void> deleteProcessVariable(
        @Parameter(description = "Process instance ID") @PathVariable String processId,
        @Parameter(description = "Variable name") @PathVariable String variableName
    ) {
        workflowService.deleteProcessVariable(processId, variableName);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tasks/{taskId}/variables")
    @Operation(summary = "Task variables", description = "Get workflow task variables as a stable resource list")
    public ResponseEntity<List<WorkflowVariableResponse>> getTaskVariables(
        @Parameter(description = "Task ID") @PathVariable String taskId
    ) {
        return ResponseEntity.ok(
            workflowService.getTaskVariables(taskId).stream()
                .map(WorkflowVariableResponse::from)
                .collect(Collectors.toList())
        );
    }

    @GetMapping("/processes/{processId}/items")
    @Operation(summary = "Process items", description = "Get attached business items for a workflow process instance")
    public ResponseEntity<List<WorkflowBusinessItemResponse>> getProcessItems(
        @Parameter(description = "Process instance ID") @PathVariable String processId
    ) {
        return ResponseEntity.ok(
            workflowService.getProcessItems(processId).stream()
                .map(WorkflowBusinessItemResponse::from)
                .collect(Collectors.toList())
        );
    }

    @GetMapping("/tasks/{taskId}/items")
    @Operation(summary = "Task items", description = "Get attached business items for a workflow task")
    public ResponseEntity<List<WorkflowBusinessItemResponse>> getTaskItems(
        @Parameter(description = "Task ID") @PathVariable String taskId
    ) {
        return ResponseEntity.ok(
            workflowService.getTaskItems(taskId).stream()
                .map(WorkflowBusinessItemResponse::from)
                .collect(Collectors.toList())
        );
    }

    @GetMapping("/tasks/{taskId}/candidates")
    @Operation(summary = "Task candidates", description = "Get candidate users and groups for a workflow task")
    public ResponseEntity<List<TaskCandidateResponse>> getTaskCandidates(
        @Parameter(description = "Task ID") @PathVariable String taskId
    ) {
        return ResponseEntity.ok(
            workflowService.getTaskCandidates(taskId).stream()
                .map(TaskCandidateResponse::from)
                .collect(Collectors.toList())
        );
    }

    @GetMapping("/tasks/{taskId}/involved")
    @Operation(summary = "Task involved actors", description = "Get people and groups involved in a workflow task")
    public ResponseEntity<List<InvolvedActorResponse>> getTaskInvolvedActors(
        @Parameter(description = "Task ID") @PathVariable String taskId
    ) {
        return ResponseEntity.ok(
            workflowService.getTaskInvolvedActors(taskId).stream()
                .map(InvolvedActorResponse::from)
                .collect(Collectors.toList())
        );
    }

    @PostMapping("/tasks/{taskId}/complete")
    @Operation(summary = "Complete task", description = "Complete a workflow task with variables (e.g. approved=true)")
    public ResponseEntity<Void> completeTask(
            @PathVariable String taskId,
            @RequestBody Map<String, Object> variables) {
        
        workflowService.completeTask(taskId, variables);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tasks/{taskId}/task-form-submit")
    @Operation(summary = "Submit workflow task form", description = "Complete a workflow task using the published task form model fields")
    public ResponseEntity<Void> submitTaskForm(
        @PathVariable String taskId,
        @RequestBody FormSubmissionRequest request
    ) {
        workflowService.submitTaskForm(taskId, request.values());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tasks/{taskId}/claim")
    @Operation(summary = "Claim workflow task", description = "Claim an unassigned workflow task for the current user")
    public ResponseEntity<Void> claimTask(@PathVariable String taskId) {
        workflowService.claimTask(taskId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tasks/{taskId}/unclaim")
    @Operation(summary = "Release workflow task", description = "Release a claimed workflow task back to the shared queue")
    public ResponseEntity<Void> unclaimTask(@PathVariable String taskId) {
        workflowService.unclaimTask(taskId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tasks/{taskId}/assign")
    @Operation(summary = "Assign workflow task", description = "Assign or reassign a workflow task to a user")
    public ResponseEntity<Void> assignTask(
        @PathVariable String taskId,
        @RequestBody AssignTaskRequest request
    ) {
        workflowService.assignTask(taskId, request.assignee());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/tasks/{taskId}")
    @Operation(summary = "Update workflow task state", description = "Apply an Alfresco-style task state transition such as claimed, unclaimed, assigned, delegated, resolved or completed")
    public ResponseEntity<Void> updateTask(
        @PathVariable String taskId,
        @RequestBody TaskTransitionRequest request
    ) {
        workflowService.updateTask(taskId, request.state(), request.assignee(), request.values());
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

    @DeleteMapping("/processes/{processId}")
    @Operation(summary = "Delete process instance", description = "Delete a workflow process instance")
    public ResponseEntity<Void> deleteProcess(
        @Parameter(description = "Process instance ID") @PathVariable String processId
    ) {
        workflowService.deleteProcess(processId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/processes/{processId}/cancel")
    @Operation(summary = "Cancel process instance", description = "Cancel a running workflow process instance with an optional reason")
    public ResponseEntity<Void> cancelProcess(
        @Parameter(description = "Process instance ID") @PathVariable String processId,
        @RequestBody(required = false) CancelProcessRequest request
    ) {
        workflowService.cancelProcess(processId, request != null ? request.reason() : null);
        return ResponseEntity.ok().build();
    }

    // ==================== DTOs ====================

    public record StartApprovalRequest(List<String> approvers, String comment) {}

    public record FormSubmissionRequest(Map<String, Object> values) {}

    public record AssignTaskRequest(String assignee) {}

    public record StartProcessItemRequest(String id) {}

    public record StartProcessRequest(
        String processDefinitionId,
        String processDefinitionKey,
        String businessKey,
        Map<String, Object> variables,
        List<StartProcessItemRequest> items
    ) {}

    public record TaskTransitionRequest(
        String state,
        String assignee,
        Map<String, Object> values
    ) {}

    public record VariableValueWriteRequest(Object value) {}

    public record CancelProcessRequest(String reason) {}

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

    public record TaskResponse(
        String id,
        String name,
        String assignee,
        String description,
        java.util.Date createTime,
        String processInstanceId,
        String processDefinitionId
    ) {
        static TaskResponse from(Task t) {
            return new TaskResponse(
                t.getId(),
                t.getName(),
                t.getAssignee(),
                t.getDescription(),
                t.getCreateTime(),
                t.getProcessInstanceId(),
                t.getProcessDefinitionId()
            );
        }
    }

    public record TaskInboxItemResponse(
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
        java.util.Date completedAt
    ) {
        static TaskInboxItemResponse from(WorkflowService.WorkflowTaskSummary summary) {
            return new TaskInboxItemResponse(
                summary.id(),
                summary.name(),
                summary.assignee(),
                summary.owner(),
                summary.delegationState(),
                summary.description(),
                summary.createTime(),
                summary.dueDate(),
                summary.processInstanceId(),
                summary.processDefinitionId(),
                summary.taskDefinitionKey(),
                summary.processDefinitionKey(),
                summary.processDefinitionName(),
                summary.processDefinitionVersion(),
                summary.businessKey(),
                summary.startedBy(),
                summary.claimable(),
                summary.status(),
                summary.completedAt()
            );
        }
    }

    public record TaskDetailResponse(
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
        WorkflowSubmissionSummaryResponse submissionSummary
    ) {
        static TaskDetailResponse from(WorkflowService.WorkflowTaskDetail detail) {
            return new TaskDetailResponse(
                detail.id(),
                detail.name(),
                detail.description(),
                detail.assignee(),
                detail.owner(),
                detail.delegationState(),
                detail.createTime(),
                detail.dueDate(),
                detail.processInstanceId(),
                detail.processDefinitionId(),
                detail.taskDefinitionKey(),
                detail.processDefinitionKey(),
                detail.processDefinitionName(),
                detail.processDefinitionVersion(),
                detail.businessKey(),
                detail.processVariables(),
                detail.processDefinitionSuspended(),
                WorkflowSubmissionSummaryResponse.from(detail.submissionSummary())
            );
        }
    }

    public record ProcessDetailResponse(
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
        WorkflowSubmissionSummaryResponse submissionSummary
    ) {
        static ProcessDetailResponse from(WorkflowService.WorkflowProcessDetail detail) {
            return new ProcessDetailResponse(
                detail.id(),
                detail.processDefinitionId(),
                detail.processDefinitionKey(),
                detail.processDefinitionName(),
                detail.processDefinitionVersion(),
                detail.businessKey(),
                detail.startedBy(),
                detail.startTime(),
                detail.endTime(),
                detail.suspended(),
                detail.ended(),
                detail.variables(),
                WorkflowSubmissionSummaryResponse.from(detail.submissionSummary())
            );
        }
    }

    public record ProcessBrowserItemResponse(
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
        WorkflowSubmissionSummaryResponse submissionSummary
    ) {
        static ProcessBrowserItemResponse from(WorkflowService.WorkflowProcessBrowserItem item) {
            return new ProcessBrowserItemResponse(
                item.id(),
                item.processDefinitionId(),
                item.processDefinitionKey(),
                item.processDefinitionName(),
                item.processDefinitionVersion(),
                item.businessKey(),
                item.startedBy(),
                item.startTime(),
                item.endTime(),
                item.ended(),
                WorkflowSubmissionSummaryResponse.from(item.submissionSummary())
            );
        }
    }

    public record ProcessBrowserListResponse(
        List<ProcessBrowserItemResponse> items,
        PagingResponse paging
    ) {
        static ProcessBrowserListResponse from(WorkflowService.WorkflowProcessBrowserPage page) {
            return new ProcessBrowserListResponse(
                page.items().stream().map(ProcessBrowserItemResponse::from).toList(),
                new PagingResponse(page.skipCount(), page.maxItems(), page.totalItems(), page.hasMoreItems())
            );
        }
    }

    public record PagingResponse(
        long skipCount,
        long maxItems,
        long totalItems,
        boolean hasMoreItems
    ) {}

    public record ProcessTaskResponse(
        String id,
        String name,
        String assignee,
        String owner,
        String description,
        java.util.Date createTime,
        java.util.Date dueDate,
        String taskDefinitionKey,
        String delegationState
    ) {
        static ProcessTaskResponse from(WorkflowService.WorkflowProcessTask detail) {
            return new ProcessTaskResponse(
                detail.id(),
                detail.name(),
                detail.assignee(),
                detail.owner(),
                detail.description(),
                detail.createTime(),
                detail.dueDate(),
                detail.taskDefinitionKey(),
                detail.delegationState()
            );
        }
    }

    public record HistoricTaskResponse(
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
    ) {
        static HistoricTaskResponse from(WorkflowService.WorkflowHistoricTaskItem task) {
            return new HistoricTaskResponse(
                task.id(),
                task.name(),
                task.assignee(),
                task.owner(),
                task.description(),
                task.taskDefinitionKey(),
                task.startTime(),
                task.endTime(),
                task.durationInMillis(),
                task.deleteReason()
            );
        }
    }

    public record ProcessActivityResponse(
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
    ) {
        static ProcessActivityResponse from(WorkflowService.WorkflowProcessActivity item) {
            return new ProcessActivityResponse(
                item.id(),
                item.activityId(),
                item.activityName(),
                item.activityType(),
                item.executionId(),
                item.taskId(),
                item.assignee(),
                item.startTime(),
                item.endTime(),
                item.durationInMillis()
            );
        }
    }

    public record WorkflowVariableResponse(
        String name,
        String type,
        Object value,
        String scope
    ) {
        static WorkflowVariableResponse from(WorkflowService.WorkflowVariableItem item) {
            return new WorkflowVariableResponse(
                item.name(),
                item.type(),
                item.value(),
                item.scope()
            );
        }
    }

    public record TaskCandidateResponse(
        String userId,
        String groupId,
        String type
    ) {
        static TaskCandidateResponse from(WorkflowService.WorkflowTaskCandidate item) {
            return new TaskCandidateResponse(
                item.userId(),
                item.groupId(),
                item.type()
            );
        }
    }

    public record InvolvedActorResponse(
        String userId,
        String groupId,
        String displayName,
        List<String> roles
    ) {
        static InvolvedActorResponse from(WorkflowService.WorkflowInvolvedActor item) {
            return new InvolvedActorResponse(
                item.userId(),
                item.groupId(),
                item.displayName(),
                item.roles()
            );
        }
    }

    public record WorkflowBusinessItemResponse(
        String id,
        String name,
        String nodeType,
        String path,
        String businessKey,
        String source
    ) {
        static WorkflowBusinessItemResponse from(WorkflowService.WorkflowBusinessItem item) {
            return new WorkflowBusinessItemResponse(
                item.id(),
                item.name(),
                item.nodeType(),
                item.path(),
                item.businessKey(),
                item.source()
            );
        }
    }

    public record ProcessDefinitionDetailResponse(
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
    ) {
        static ProcessDefinitionDetailResponse from(WorkflowService.WorkflowDefinitionDetail detail) {
            return new ProcessDefinitionDetailResponse(
                detail.id(),
                detail.key(),
                detail.name(),
                detail.description(),
                detail.version(),
                detail.category(),
                detail.deploymentId(),
                detail.tenantId(),
                detail.suspended(),
                detail.hasStartFormKey(),
                detail.hasGraphicalNotation(),
                detail.modelResourceName(),
                detail.diagramResourceName(),
                detail.bpmnXmlAvailable(),
                detail.diagramAvailable(),
                detail.resourceNames()
            );
        }
    }

    public record ProcessDefinitionModelResponse(
        String definitionId,
        String resourceName,
        String xml
    ) {
        static ProcessDefinitionModelResponse from(WorkflowService.WorkflowDefinitionModel detail) {
            return new ProcessDefinitionModelResponse(
                detail.definitionId(),
                detail.resourceName(),
                detail.xml()
            );
        }
    }

    public record FormModelElementResponse(
        String id,
        String name,
        String title,
        String type,
        boolean required,
        boolean readOnly,
        boolean repeated,
        String placeholder,
        Object defaultValue,
        List<FormModelOptionResponse> options,
        String scope
    ) {
        static FormModelElementResponse from(WorkflowService.WorkflowFormModelElement element) {
            return new FormModelElementResponse(
                element.id(),
                element.name(),
                element.title(),
                element.type(),
                element.required(),
                element.readOnly(),
                element.repeated(),
                element.placeholder(),
                element.defaultValue(),
                element.options().stream()
                    .map(FormModelOptionResponse::from)
                    .collect(Collectors.toList()),
                element.scope()
            );
        }
    }

    public record FormModelOptionResponse(
        String label,
        String value
    ) {
        static FormModelOptionResponse from(WorkflowService.WorkflowFormOption option) {
            return new FormModelOptionResponse(option.label(), option.value());
        }
    }

    public record HistoricProcessInstanceResponse(
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
    ) {
        static HistoricProcessInstanceResponse from(WorkflowService.WorkflowDocumentHistoryItem item) {
            return new HistoricProcessInstanceResponse(
                item.id(),
                item.businessKey(),
                item.processDefinitionKey(),
                item.processDefinitionName(),
                item.startTime(),
                item.endTime(),
                item.startedBy(),
                item.startComment(),
                item.approvers(),
                item.decision(),
                item.decisionLabel(),
                item.reviewedBy(),
                item.reviewedAt(),
                item.comment(),
            item.ended()
            );
        }
    }

    public record WorkflowSubmissionSummaryResponse(
        List<String> approvers,
        String startComment,
        String startFormSubmittedBy,
        String startFormSubmittedAt,
        String decision,
        String decisionLabel,
        String reviewedBy,
        String reviewedAt,
        String comment
    ) {
        static WorkflowSubmissionSummaryResponse from(WorkflowService.WorkflowSubmissionSummary summary) {
            if (summary == null) {
                return null;
            }
            return new WorkflowSubmissionSummaryResponse(
                summary.approvers(),
                summary.startComment(),
                summary.startFormSubmittedBy(),
                summary.startFormSubmittedAt(),
                summary.decision(),
                summary.decisionLabel(),
                summary.reviewedBy(),
                summary.reviewedAt(),
                summary.comment()
            );
        }
    }

    private ResponseEntity<ByteArrayResource> toBinaryResponse(WorkflowService.WorkflowDefinitionDiagram diagram) {
        MediaType mediaType = MediaTypeFactory.getMediaType(diagram.resourceName())
            .orElse(MediaType.APPLICATION_OCTET_STREAM);
        ByteArrayResource resource = new ByteArrayResource(diagram.bytes());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                .filename(diagram.resourceName(), StandardCharsets.UTF_8)
                .build()
                .toString())
            .contentType(mediaType)
            .contentLength(diagram.bytes().length)
            .body(resource);
    }
}
