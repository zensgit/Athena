package com.ecm.core.controller;

import com.ecm.core.service.WorkflowService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkflowControllerTest {

    private MockMvc mockMvc;

    @Mock
    private WorkflowService workflowService;

    @InjectMocks
    private WorkflowController workflowController;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(workflowController)
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("Workflow definitions return details and model metadata")
    void workflowDefinitionDetailReturnsMetadata() throws Exception {
        WorkflowService.WorkflowDefinitionDetail detail = new WorkflowService.WorkflowDefinitionDetail(
            "def-1",
            "documentApproval",
            "Document Approval Workflow",
            "Approval flow",
            3,
            "workflow",
            "deploy-1",
            "tenant-a",
            false,
            true,
            true,
            "document-approval.bpmn20.xml",
            "document-approval.png",
            true,
            true,
            List.of("document-approval.bpmn20.xml", "document-approval.png")
        );
        WorkflowService.WorkflowDefinitionModel model = new WorkflowService.WorkflowDefinitionModel(
            "def-1",
            "document-approval.bpmn20.xml",
            "<definitions id=\"documentApproval\"/>"
        );

        Mockito.when(workflowService.getDefinitionDetail("def-1")).thenReturn(detail);
        Mockito.when(workflowService.getDefinitionModel("def-1")).thenReturn(model);

        mockMvc.perform(get("/api/v1/workflows/definitions/{definitionId}", "def-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("def-1"))
            .andExpect(jsonPath("$.diagramAvailable").value(true))
            .andExpect(jsonPath("$.resourceNames", hasSize(2)));

        mockMvc.perform(get("/api/v1/workflows/definitions/{definitionId}/model", "def-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.definitionId").value("def-1"))
            .andExpect(jsonPath("$.xml").value("<definitions id=\"documentApproval\"/>"));
    }

    @Test
    @DisplayName("Workflow definition diagram returns binary resource")
    void workflowDefinitionDiagramReturnsBinary() throws Exception {
        WorkflowService.WorkflowDefinitionDiagram diagram = new WorkflowService.WorkflowDefinitionDiagram(
            "def-1",
            "document-approval.png",
            "png-bytes".getBytes(StandardCharsets.UTF_8)
        );
        Mockito.when(workflowService.getDefinitionDiagram("def-1")).thenReturn(diagram);

        mockMvc.perform(get("/api/v1/workflows/definitions/{definitionId}/diagram", "def-1"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("document-approval.png")))
            .andExpect(content().bytes("png-bytes".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Workflow definition start form model returns derived form fields")
    void workflowDefinitionStartFormModelReturnsFields() throws Exception {
        Mockito.when(workflowService.getStartFormModel("def-1")).thenReturn(List.of(
            new WorkflowService.WorkflowFormModelElement(
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
            )
        ));

        mockMvc.perform(get("/api/v1/workflows/definitions/{definitionId}/start-form-model", "def-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("approvers"))
            .andExpect(jsonPath("$[0].scope").value("start"));
    }

    @Test
    @DisplayName("Workflow task detail returns task and process metadata")
    void workflowTaskDetailReturnsMetadata() throws Exception {
        WorkflowService.WorkflowTaskDetail detail = new WorkflowService.WorkflowTaskDetail(
            "task-1",
            "Approve document",
            "Review the submission",
            "alice",
            "workflow-admin",
            "PENDING",
            new Date(1_700_000_000_000L),
            new Date(1_700_100_000_000L),
            "pi-1",
            "def-1",
            "userTask",
            "documentApproval",
            "Document Approval Workflow",
            3,
            "doc-1",
            Map.of("documentName", "Report.pdf"),
            false,
            submissionSummary(
                List.of("bob", "carol"),
                "Please review",
                "alice",
                "2026-03-19T01:02:03Z",
                "APPROVED",
                "Approve",
                "reviewer",
                "2026-03-19T04:05:06Z",
                "Looks good"
            )
        );

        Mockito.when(workflowService.getTaskDetail("task-1")).thenReturn(detail);

        mockMvc.perform(get("/api/v1/workflows/tasks/{taskId}", "task-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("task-1"))
            .andExpect(jsonPath("$.processInstanceId").value("pi-1"))
            .andExpect(jsonPath("$.delegationState").value("PENDING"))
            .andExpect(jsonPath("$.businessKey").value("doc-1"))
            .andExpect(jsonPath("$.processVariables.documentName").value("Report.pdf"))
            .andExpect(jsonPath("$.submissionSummary.approvers", hasSize(2)))
            .andExpect(jsonPath("$.submissionSummary.startComment").value("Please review"))
            .andExpect(jsonPath("$.submissionSummary.startFormSubmittedBy").value("alice"))
            .andExpect(jsonPath("$.submissionSummary.startFormSubmittedAt").value("2026-03-19T01:02:03Z"))
            .andExpect(jsonPath("$.submissionSummary.decision").value("APPROVED"))
            .andExpect(jsonPath("$.submissionSummary.decisionLabel").value("Approve"))
            .andExpect(jsonPath("$.submissionSummary.reviewedBy").value("reviewer"))
            .andExpect(jsonPath("$.submissionSummary.reviewedAt").value("2026-03-19T04:05:06Z"))
            .andExpect(jsonPath("$.submissionSummary.comment").value("Looks good"));
    }

    @Test
    @DisplayName("Workflow task form model returns review fields")
    void workflowTaskFormModelReturnsFields() throws Exception {
        Mockito.when(workflowService.getTaskFormModel("task-1")).thenReturn(List.of(
            new WorkflowService.WorkflowFormModelElement(
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
                    new WorkflowService.WorkflowFormOption("Approve", "true"),
                    new WorkflowService.WorkflowFormOption("Reject", "false")
                ),
                "task"
            )
        ));

        mockMvc.perform(get("/api/v1/workflows/tasks/{taskId}/task-form-model", "task-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("approved"))
            .andExpect(jsonPath("$[0].options", hasSize(2)))
            .andExpect(jsonPath("$[0].scope").value("task"));
    }

    @Test
    @DisplayName("Workflow process detail and tasks return process-centric metadata")
    void workflowProcessDetailAndTasksReturnMetadata() throws Exception {
        WorkflowService.WorkflowProcessDetail detail = new WorkflowService.WorkflowProcessDetail(
            "pi-1",
            "def-1",
            "documentApproval",
            "Document Approval Workflow",
            3,
            "doc-1",
            "alice",
            new Date(1_700_000_000_000L),
            null,
            false,
            false,
            Map.of("documentName", "Report.pdf"),
            submissionSummary(
                List.of("bob", "carol"),
                "Please review",
                "alice",
                "2026-03-19T01:02:03Z",
                "APPROVED",
                "Approve",
                "reviewer",
                "2026-03-19T04:05:06Z",
                "Looks good"
            )
        );
        WorkflowService.WorkflowProcessTask processTask = new WorkflowService.WorkflowProcessTask(
            "task-1",
            "Approve document",
            "alice",
            "workflow-admin",
            "Review the submission",
            new Date(1_700_000_000_000L),
            new Date(1_700_100_000_000L),
            "userTask",
            "PENDING"
        );

        Mockito.when(workflowService.getProcessDetail("pi-1")).thenReturn(detail);
        Mockito.when(workflowService.getProcessTaskDetails("pi-1")).thenReturn(List.of(processTask));
        Mockito.when(workflowService.getProcessVariables("pi-1")).thenReturn(List.of(
            new WorkflowService.WorkflowVariableItem("documentName", "String", "Report.pdf", "process")
        ));
        Mockito.when(workflowService.getProcessItems("pi-1")).thenReturn(List.of(
            new WorkflowService.WorkflowBusinessItem("doc-1", "Report.pdf", "DOCUMENT", "/Root/Documents/Report.pdf", "doc-1", "businessKey")
        ));
        Mockito.when(workflowService.getTaskVariables("task-1")).thenReturn(List.of(
            new WorkflowService.WorkflowVariableItem("approved", "boolean", true, "task")
        ));
        Mockito.when(workflowService.getTaskItems("task-1")).thenReturn(List.of(
            new WorkflowService.WorkflowBusinessItem("doc-1", "Report.pdf", "DOCUMENT", "/Root/Documents/Report.pdf", "doc-1", "businessKey")
        ));

        mockMvc.perform(get("/api/v1/workflows/processes/{processId}", "pi-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("pi-1"))
            .andExpect(jsonPath("$.processDefinitionKey").value("documentApproval"))
            .andExpect(jsonPath("$.variables.documentName").value("Report.pdf"))
            .andExpect(jsonPath("$.submissionSummary.approvers", hasSize(2)))
            .andExpect(jsonPath("$.submissionSummary.startFormSubmittedBy").value("alice"))
            .andExpect(jsonPath("$.submissionSummary.startFormSubmittedAt").value("2026-03-19T01:02:03Z"))
            .andExpect(jsonPath("$.submissionSummary.decisionLabel").value("Approve"))
            .andExpect(jsonPath("$.submissionSummary.reviewedBy").value("reviewer"))
            .andExpect(jsonPath("$.submissionSummary.comment").value("Looks good"));

        mockMvc.perform(get("/api/v1/workflows/processes/{processId}/tasks", "pi-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].taskDefinitionKey").value("userTask"))
            .andExpect(jsonPath("$[0].delegationState").value("PENDING"));

        mockMvc.perform(get("/api/v1/workflows/processes/{processId}/variables", "pi-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("documentName"))
            .andExpect(jsonPath("$[0].scope").value("process"));

        mockMvc.perform(get("/api/v1/workflows/processes/{processId}/items", "pi-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value("doc-1"))
            .andExpect(jsonPath("$[0].source").value("businessKey"));

        mockMvc.perform(get("/api/v1/workflows/tasks/{taskId}/variables", "task-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("approved"))
            .andExpect(jsonPath("$[0].scope").value("task"));

        mockMvc.perform(get("/api/v1/workflows/tasks/{taskId}/items", "task-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("Report.pdf"));
    }

    @Test
    @DisplayName("Workflow process activities and task candidates return lifecycle resources")
    void workflowProcessActivitiesAndTaskCandidatesReturnResources() throws Exception {
        Mockito.when(workflowService.getProcessActivities("pi-1", null, null, null)).thenReturn(List.of(
            new WorkflowService.WorkflowProcessActivity(
                "activity-1",
                "approvalTask",
                "Approve document",
                "userTask",
                "exec-1",
                "task-1",
                "alice",
                new Date(1_700_000_000_000L),
                new Date(1_700_000_100_000L),
                100_000L
            )
        ));
        Mockito.when(workflowService.getTaskCandidates("task-1")).thenReturn(List.of(
            new WorkflowService.WorkflowTaskCandidate("alice", null, "candidate"),
            new WorkflowService.WorkflowTaskCandidate(null, "GROUP_REVIEWERS", "candidate")
        ));

        mockMvc.perform(get("/api/v1/workflows/processes/{processId}/activities", "pi-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].activityId").value("approvalTask"))
            .andExpect(jsonPath("$[0].activityType").value("userTask"))
            .andExpect(jsonPath("$[0].durationInMillis").value(100000));

        mockMvc.perform(get("/api/v1/workflows/tasks/{taskId}/candidates", "task-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].userId").value("alice"))
            .andExpect(jsonPath("$[1].groupId").value("GROUP_REVIEWERS"));
    }

    @Test
    @DisplayName("Workflow process activities forward server-side filter parameters")
    void workflowProcessActivitiesForwardServerSideFilterParameters() throws Exception {
        Mockito.when(workflowService.getProcessActivities("pi-1", "approve", "alice", "userTask")).thenReturn(List.of(
            new WorkflowService.WorkflowProcessActivity(
                "activity-1",
                "approvalTask",
                "Approve document",
                "userTask",
                "exec-1",
                "task-1",
                "alice",
                new Date(1_700_000_000_000L),
                null,
                null
            )
        ));

        mockMvc.perform(get("/api/v1/workflows/processes/{processId}/activities", "pi-1")
                .param("query", "approve")
                .param("assignee", "alice")
                .param("activityType", "userTask"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].assignee").value("alice"))
            .andExpect(jsonPath("$[0].activityType").value("userTask"));

        Mockito.verify(workflowService).getProcessActivities("pi-1", "approve", "alice", "userTask");
    }

    @Test
    @DisplayName("Workflow process task history returns completed task resources")
    void workflowProcessTaskHistoryReturnsCompletedTaskResources() throws Exception {
        Mockito.when(workflowService.getProcessTaskHistory("pi-1", "review", "alice", "approvalTask")).thenReturn(List.of(
            new WorkflowService.WorkflowHistoricTaskItem(
                "task-h-1",
                "Review completed",
                "alice",
                "workflow-admin",
                "Completed review",
                "approvalTask",
                new Date(1_700_000_000_000L),
                new Date(1_700_000_120_000L),
                120_000L,
                "completed"
            )
        ));

        mockMvc.perform(get("/api/v1/workflows/processes/{processId}/task-history", "pi-1")
                .param("query", "review")
                .param("assignee", "alice")
                .param("taskDefinitionKey", "approvalTask"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value("task-h-1"))
            .andExpect(jsonPath("$[0].taskDefinitionKey").value("approvalTask"))
            .andExpect(jsonPath("$[0].durationInMillis").value(120000))
            .andExpect(jsonPath("$[0].deleteReason").value("completed"));

        Mockito.verify(workflowService).getProcessTaskHistory("pi-1", "review", "alice", "approvalTask");
    }

    @Test
    @DisplayName("Workflow process variable write endpoints delegate to service")
    void workflowProcessVariableWriteEndpointsDelegateToService() throws Exception {
        mockMvc.perform(put("/api/v1/workflows/processes/{processId}/variables/{variableName}", "pi-1", "priority")
                .contentType("application/json")
                .content("{\"value\":\"high\"}"))
            .andExpect(status().isOk());

        Mockito.verify(workflowService).upsertProcessVariable("pi-1", "priority", "high");

        mockMvc.perform(delete("/api/v1/workflows/processes/{processId}/variables/{variableName}", "pi-1", "priority"))
            .andExpect(status().isNoContent());

        Mockito.verify(workflowService).deleteProcessVariable("pi-1", "priority");
    }

    @Test
    @DisplayName("Workflow involved actor resources return users and groups with roles")
    void workflowInvolvedActorResourcesReturnUsersAndGroups() throws Exception {
        Mockito.when(workflowService.getProcessInvolvedActors("pi-1")).thenReturn(List.of(
            new WorkflowService.WorkflowInvolvedActor("alice", null, "Alice Lee", List.of("starter", "submittedBy")),
            new WorkflowService.WorkflowInvolvedActor(null, "GROUP_REVIEWERS", "GROUP_REVIEWERS", List.of("candidate"))
        ));
        Mockito.when(workflowService.getTaskInvolvedActors("task-1")).thenReturn(List.of(
            new WorkflowService.WorkflowInvolvedActor("bob", null, "Bob Chen", List.of("assignee")),
            new WorkflowService.WorkflowInvolvedActor(null, "GROUP_REVIEWERS", "GROUP_REVIEWERS", List.of("candidate"))
        ));

        mockMvc.perform(get("/api/v1/workflows/processes/{processId}/involved", "pi-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].userId").value("alice"))
            .andExpect(jsonPath("$[0].displayName").value("Alice Lee"))
            .andExpect(jsonPath("$[0].roles[0]").value("starter"));

        mockMvc.perform(get("/api/v1/workflows/tasks/{taskId}/involved", "task-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].userId").value("bob"))
            .andExpect(jsonPath("$[1].groupId").value("GROUP_REVIEWERS"));
    }

    @Test
    @DisplayName("Workflow process diagram returns binary resource")
    void workflowProcessDiagramReturnsBinary() throws Exception {
        WorkflowService.WorkflowDefinitionDiagram diagram = new WorkflowService.WorkflowDefinitionDiagram(
            "pi-1",
            "document-approval.png",
            "process-png".getBytes(StandardCharsets.UTF_8)
        );
        Mockito.when(workflowService.getProcessDiagram("pi-1")).thenReturn(diagram);

        mockMvc.perform(get("/api/v1/workflows/processes/{processId}/diagram", "pi-1"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("document-approval.png")))
            .andExpect(content().bytes("process-png".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Workflow task list maps existing fields")
    void workflowTaskListMapsFields() throws Exception {
        Task task = Mockito.mock(Task.class);
        Mockito.when(task.getId()).thenReturn("task-1");
        Mockito.when(task.getName()).thenReturn("Approve document");
        Mockito.when(task.getAssignee()).thenReturn("alice");
        Mockito.when(task.getDescription()).thenReturn("Review the submission");
        Mockito.when(task.getCreateTime()).thenReturn(new Date(1_700_000_000_000L));

        Mockito.when(workflowService.getMyTasks()).thenReturn(List.of(task));

        mockMvc.perform(get("/api/v1/workflows/tasks/my"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value("task-1"))
            .andExpect(jsonPath("$[0].name").value("Approve document"));
    }

    @Test
    @DisplayName("Workflow task inbox supports scope, search and assignee override")
    void workflowTaskInboxSupportsFilters() throws Exception {
        WorkflowService.WorkflowTaskSummary summary = new WorkflowService.WorkflowTaskSummary(
            "task-1",
            "Approve document",
            "bob",
            "workflow-admin",
            null,
            "Review the submission",
            new Date(1_700_000_000_000L),
            new Date(1_700_100_000_000L),
            "pi-1",
            "def-1",
            "approvalTask",
            "documentApproval",
            "Document Approval Workflow",
            3,
            "doc-1",
            "alice",
            false,
            "COMPLETED",
            new Date(1_700_200_000_000L),
            new Date(1_700_200_000_000L)
        );

        Mockito.when(workflowService.listTasks("completed", "review", "doc-1", "bob", "pi-1", "workflow-admin", "candidate.alice", null))
            .thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/workflows/tasks/inbox")
                .param("scope", "completed")
                .param("query", "review")
                .param("businessKey", "doc-1")
                .param("assignee", "bob")
                .param("processId", "pi-1")
                .param("owner", "workflow-admin")
                .param("candidateUser", "candidate.alice"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value("task-1"))
            .andExpect(jsonPath("$[0].owner").value("workflow-admin"))
            .andExpect(jsonPath("$[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$[0].completedAt").exists());

        Mockito.verify(workflowService).listTasks("completed", "review", "doc-1", "bob", "pi-1", "workflow-admin", "candidate.alice", null);
    }

    @Test
    @DisplayName("Workflow task inbox supports involved scope")
    void workflowTaskInboxSupportsInvolvedScope() throws Exception {
        Mockito.when(workflowService.listTasks("involved", "starter", null, null, "pi-1", null, null, null)).thenReturn(List.of(
            new WorkflowService.WorkflowTaskSummary(
                "task-1",
                "Approve document",
                "bob",
                "workflow-admin",
                null,
                "Review the submission",
                new Date(1_700_000_000_000L),
                null,
                "pi-1",
                "def-1",
                "approvalTask",
                "documentApproval",
                "Document Approval Workflow",
                3,
                "doc-1",
                "alice",
                false,
                "ASSIGNED",
                null,
                new Date(1_700_000_000_000L)
            )
        ));

        mockMvc.perform(get("/api/v1/workflows/tasks/inbox")
                .param("scope", "involved")
                .param("query", "starter")
                .param("processId", "pi-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value("task-1"))
            .andExpect(jsonPath("$[0].status").value("ASSIGNED"));

        Mockito.verify(workflowService).listTasks("involved", "starter", null, null, "pi-1", null, null, null);
    }

    @Test
    @DisplayName("Workflow task inbox supports candidate group and legacy query")
    void workflowTaskInboxSupportsCandidateGroupAndLegacyQuery() throws Exception {
        WorkflowService.WorkflowTaskSummary summary = new WorkflowService.WorkflowTaskSummary(
            "task-claimable-1",
            "Review pooled request",
            null,
            "workflow-admin",
            null,
            "Candidate group review",
            new Date(1_700_000_000_000L),
            null,
            "pi-shared-1",
            "def-1",
            "approvalTask",
            "documentApproval",
            "Document Approval Workflow",
            3,
            "doc-claimable",
            "alice",
            true,
            "CLAIMABLE",
            null,
            new Date(1_700_000_000_000L)
        );

        Mockito.when(workflowService.listTasks("claimable", "pooled", null, null, "pi-shared-1", "workflow-admin", null, "sales"))
            .thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/workflows/tasks/inbox")
                .param("scope", "claimable")
                .param("q", "pooled")
                .param("processId", "pi-shared-1")
                .param("owner", "workflow-admin")
                .param("candidateGroup", "sales"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value("task-claimable-1"))
            .andExpect(jsonPath("$[0].owner").value("workflow-admin"))
            .andExpect(jsonPath("$[0].claimable").value(true));

        Mockito.verify(workflowService).listTasks("claimable", "pooled", null, null, "pi-shared-1", "workflow-admin", null, "sales");
    }

    @Test
    @DisplayName("Workflow process browser supports status filter and paging")
    void workflowProcessBrowserSupportsPaging() throws Exception {
        WorkflowService.WorkflowProcessBrowserItem item = new WorkflowService.WorkflowProcessBrowserItem(
            "pi-1",
            "def-1",
            "documentApproval",
            "Document Approval Workflow",
            3,
            "doc-1",
            "alice",
            new Date(1_700_000_000_000L),
            new Date(1_700_100_000_000L),
            true,
            submissionSummary(
                List.of("bob", "carol"),
                "Please review",
                "alice",
                "2026-03-19T01:02:03Z",
                "APPROVED",
                "Approve",
                "reviewer",
                "2026-03-19T04:05:06Z",
                "Looks good"
            )
        );
        WorkflowService.WorkflowProcessBrowserPage page = new WorkflowService.WorkflowProcessBrowserPage(
            List.of(item),
            5,
            10,
            1L,
            false
        );

        Mockito.when(workflowService.listProcesses("all", "doc-1", "alice", "documentApproval", "review", 5, 10)).thenReturn(page);

        mockMvc.perform(get("/api/v1/workflows/processes/browser")
                .param("status", "all")
                .param("businessKey", "doc-1")
                .param("startedBy", "alice")
                .param("definitionKey", "documentApproval")
                .param("query", "review")
                .param("skipCount", "5")
                .param("maxItems", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].id").value("pi-1"))
            .andExpect(jsonPath("$.paging.totalItems").value(1))
            .andExpect(jsonPath("$.paging.hasMoreItems").value(false));

        Mockito.verify(workflowService).listProcesses("all", "doc-1", "alice", "documentApproval", "review", 5, 10);
    }

    @Test
    @DisplayName("Workflow task inbox returns scoped summaries")
    void workflowTaskInboxReturnsScopedSummaries() throws Exception {
        Mockito.when(workflowService.listTasks("SHARED", "approve", "doc-1", null, null, null, null, null)).thenReturn(List.of(
            new WorkflowService.WorkflowTaskSummary(
                "task-1",
                "Approve document",
                null,
                null,
                null,
                "Shared queue item",
                new Date(1_700_000_000_000L),
                null,
                "pi-1",
                "def-1",
                "approvalTask",
                "documentApproval",
                "Document Approval Workflow",
                3,
                "doc-1",
                "alice",
                true,
                "CLAIMABLE",
                null,
                new Date(1_700_000_000_000L)
            )
        ));

        mockMvc.perform(get("/api/v1/workflows/tasks")
                .param("scope", "SHARED")
                .param("q", "approve")
                .param("businessKey", "doc-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].businessKey").value("doc-1"))
            .andExpect(jsonPath("$[0].processDefinitionName").value("Document Approval Workflow"))
            .andExpect(jsonPath("$[0].claimable").value(true));

        Mockito.verify(workflowService).listTasks("SHARED", "approve", "doc-1", null, null, null, null, null);
    }

    @Test
    @DisplayName("Workflow process browser returns paged summaries")
    void workflowProcessBrowserReturnsPagedSummaries() throws Exception {
        Mockito.when(workflowService.listProcesses("ACTIVE", "doc-1", "alice", "documentApproval", "review", 0, 20)).thenReturn(
            new WorkflowService.WorkflowProcessBrowserPage(
                List.of(
                    new WorkflowService.WorkflowProcessBrowserItem(
                        "pi-1",
                        "def-1",
                        "documentApproval",
                        "Document Approval Workflow",
                        3,
                        "doc-1",
                        "alice",
                        new Date(1_700_000_000_000L),
                        null,
                        false,
                        submissionSummary(
                            List.of("bob"),
                            "Please review",
                            "alice",
                            "2026-03-19T01:02:03Z",
                            null,
                            null,
                            null,
                            null,
                            null
                        )
                    )
                ),
                0,
                20,
                1,
                false
            )
        );

        mockMvc.perform(get("/api/v1/workflows/processes")
                .param("status", "ACTIVE")
                .param("businessKey", "doc-1")
                .param("startedBy", "alice")
                .param("definitionKey", "documentApproval")
                .param("q", "review")
                .param("skipCount", "0")
                .param("maxItems", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].id").value("pi-1"))
            .andExpect(jsonPath("$.items[0].businessKey").value("doc-1"))
            .andExpect(jsonPath("$.paging.totalItems").value(1))
            .andExpect(jsonPath("$.paging.hasMoreItems").value(false));
    }

    @Test
    @DisplayName("Workflow process delete delegates to service")
    void workflowProcessDeleteDelegatesToService() throws Exception {
        Mockito.doNothing().when(workflowService).deleteProcess("pi-1");

        mockMvc.perform(delete("/api/v1/workflows/processes/{processId}", "pi-1"))
            .andExpect(status().isNoContent());

        Mockito.verify(workflowService).deleteProcess("pi-1");
    }

    @Test
    @DisplayName("Workflow process cancel delegates to service")
    void workflowProcessCancelDelegatesToService() throws Exception {
        Mockito.doNothing().when(workflowService).cancelProcess("pi-1", "User cancelled");

        mockMvc.perform(post("/api/v1/workflows/processes/{processId}/cancel", "pi-1")
                .contentType("application/json")
                .content("{\"reason\":\"User cancelled\"}"))
            .andExpect(status().isOk());

        Mockito.verify(workflowService).cancelProcess("pi-1", "User cancelled");
    }

    @Test
    @DisplayName("Workflow document history returns mapped history")
    void workflowDocumentHistoryReturnsMappedHistory() throws Exception {
        WorkflowService.WorkflowDocumentHistoryItem history = new WorkflowService.WorkflowDocumentHistoryItem(
            "hist-1",
            "doc-1",
            "documentApproval",
            "Document Approval Workflow",
            new Date(1_700_000_000_000L),
            new Date(1_700_100_000_000L),
            "alice",
            "Please review",
            List.of("bob", "carol"),
            "APPROVED",
            "Approve",
            "bob",
            "2026-03-19T10:00:00Z",
            "Looks good",
            true
        );
        Mockito.when(workflowService.getDocumentHistory(Mockito.any())).thenReturn(List.of(history));

        mockMvc.perform(get("/api/v1/workflows/document/{documentId}/history", java.util.UUID.randomUUID()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value("hist-1"))
            .andExpect(jsonPath("$[0].businessKey").value("doc-1"))
            .andExpect(jsonPath("$[0].processDefinitionKey").value("documentApproval"))
            .andExpect(jsonPath("$[0].processDefinitionName").value("Document Approval Workflow"))
            .andExpect(jsonPath("$[0].startedBy").value("alice"))
            .andExpect(jsonPath("$[0].startComment").value("Please review"))
            .andExpect(jsonPath("$[0].approvers", hasSize(2)))
            .andExpect(jsonPath("$[0].decision").value("APPROVED"))
            .andExpect(jsonPath("$[0].decisionLabel").value("Approve"))
            .andExpect(jsonPath("$[0].reviewedBy").value("bob"))
            .andExpect(jsonPath("$[0].reviewedAt").value("2026-03-19T10:00:00Z"))
            .andExpect(jsonPath("$[0].comment").value("Looks good"))
            .andExpect(jsonPath("$[0].ended").value(true));
    }

    @Test
    @DisplayName("Start approval returns created process instance")
    void startApprovalReturnsProcessInstance() throws Exception {
        ProcessInstance instance = Mockito.mock(ProcessInstance.class);
        Mockito.when(instance.getId()).thenReturn("pi-1");
        Mockito.when(instance.getProcessDefinitionKey()).thenReturn("documentApproval");
        Mockito.when(instance.getBusinessKey()).thenReturn("doc-1");
        Mockito.when(instance.isEnded()).thenReturn(false);
        Mockito.when(workflowService.startDocumentApproval(Mockito.any(), Mockito.anyList(), Mockito.any())).thenReturn(instance);

        mockMvc.perform(post("/api/v1/workflows/document/{documentId}/approval", java.util.UUID.randomUUID())
                .contentType("application/json")
                .content("{\"approvers\":[\"alice\"],\"comment\":\"ok\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.businessKey").value("doc-1"));
    }

    @Test
    @DisplayName("Approval start form submit returns created process instance")
    void approvalStartFormSubmitReturnsProcessInstance() throws Exception {
        ProcessInstance instance = Mockito.mock(ProcessInstance.class);
        Mockito.when(instance.getId()).thenReturn("pi-2");
        Mockito.when(instance.getProcessDefinitionKey()).thenReturn("documentApproval");
        Mockito.when(instance.getBusinessKey()).thenReturn("doc-2");
        Mockito.when(instance.isEnded()).thenReturn(false);
        Mockito.when(workflowService.submitStartForm(Mockito.any(), Mockito.anyMap())).thenReturn(instance);

        mockMvc.perform(post("/api/v1/workflows/document/{documentId}/approval/form-submit", java.util.UUID.randomUUID())
                .contentType("application/json")
                .content("{\"values\":{\"approvers\":[\"alice\"],\"comment\":\"review\"}}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("pi-2"))
            .andExpect(jsonPath("$.businessKey").value("doc-2"));
    }

    @Test
    @DisplayName("Generic process start delegates to service")
    void genericProcessStartDelegatesToService() throws Exception {
        ProcessInstance instance = Mockito.mock(ProcessInstance.class);
        Mockito.when(instance.getId()).thenReturn("pi-3");
        Mockito.when(instance.getProcessDefinitionKey()).thenReturn("invoiceApproval");
        Mockito.when(instance.getBusinessKey()).thenReturn("invoice-42");
        Mockito.when(instance.isEnded()).thenReturn(false);
        Mockito.when(workflowService.startProcess(
            Mockito.eq("def-2"),
            Mockito.isNull(),
            Mockito.eq("invoice-42"),
            Mockito.anyMap(),
            Mockito.eq(List.of("node-1"))
        )).thenReturn(instance);

        mockMvc.perform(post("/api/v1/workflows/processes")
                .contentType("application/json")
                .content("""
                    {
                      "processDefinitionId":"def-2",
                      "businessKey":"invoice-42",
                      "variables":{"approvalType":"finance"},
                      "items":[{"id":"node-1"}]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("pi-3"))
            .andExpect(jsonPath("$.definitionKey").value("invoiceApproval"));
    }

    private WorkflowService.WorkflowSubmissionSummary submissionSummary(
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
        return new WorkflowService.WorkflowSubmissionSummary(
            approvers,
            startComment,
            startFormSubmittedBy,
            startFormSubmittedAt,
            decision,
            decisionLabel,
            reviewedBy,
            reviewedAt,
            comment
        );
    }

    @Test
    @DisplayName("Task form submit delegates completion")
    void taskFormSubmitCompletesTask() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/tasks/{taskId}/task-form-submit", "task-1")
                .contentType("application/json")
                .content("{\"values\":{\"approved\":true,\"comment\":\"looks good\"}}"))
            .andExpect(status().isOk());

        Mockito.verify(workflowService).submitTaskForm(
            Mockito.eq("task-1"),
            Mockito.argThat((Map<String, Object> values) ->
                Boolean.TRUE.equals(values.get("approved")) && "looks good".equals(values.get("comment"))
            )
        );
    }

    @Test
    @DisplayName("Task claim delegates to service")
    void taskClaimDelegatesToService() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/tasks/{taskId}/claim", "task-1"))
            .andExpect(status().isOk());

        Mockito.verify(workflowService).claimTask("task-1");
    }

    @Test
    @DisplayName("Task unclaim delegates to service")
    void taskUnclaimDelegatesToService() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/tasks/{taskId}/unclaim", "task-1"))
            .andExpect(status().isOk());

        Mockito.verify(workflowService).unclaimTask("task-1");
    }

    @Test
    @DisplayName("Workflow task assign delegates to service")
    void taskAssignDelegatesToService() throws Exception {
        Mockito.doNothing().when(workflowService).assignTask("task-1", "bob");

        mockMvc.perform(post("/api/v1/workflows/tasks/{taskId}/assign", "task-1")
                .contentType("application/json")
                .content("""
                    {"assignee":"bob"}
                    """))
            .andExpect(status().isOk());

        Mockito.verify(workflowService).assignTask("task-1", "bob");
    }

    @Test
    @DisplayName("Workflow task update delegates transition to service")
    void taskUpdateDelegatesTransitionToService() throws Exception {
        mockMvc.perform(put("/api/v1/workflows/tasks/{taskId}", "task-1")
                .contentType("application/json")
                .content("""
                    {"state":"delegated","assignee":"bob","values":{"comment":"please cover"}}
                    """))
            .andExpect(status().isOk());

        Mockito.verify(workflowService).updateTask(
            Mockito.eq("task-1"),
            Mockito.eq("delegated"),
            Mockito.eq("bob"),
            Mockito.argThat((Map<String, Object> values) -> "please cover".equals(values.get("comment")))
        );
    }
}
