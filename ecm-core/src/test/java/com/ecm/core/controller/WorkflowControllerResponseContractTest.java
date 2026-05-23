package com.ecm.core.controller;

import com.ecm.core.service.WorkflowService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkflowControllerResponseContractTest {

    @Mock
    private WorkflowService workflowService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkflowController(workflowService))
            .setControllerAdvice(new RestExceptionHandler())
            .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("GET /workflows/definitions locks process definition summary contract")
    void definitionsLockProcessDefinitionSummaryContract() throws Exception {
        ProcessDefinition definition = Mockito.mock(ProcessDefinition.class);
        Mockito.when(definition.getId()).thenReturn("def-1");
        Mockito.when(definition.getKey()).thenReturn("documentApproval");
        Mockito.when(definition.getName()).thenReturn("Document Approval Workflow");
        Mockito.when(definition.getVersion()).thenReturn(3);
        Mockito.when(workflowService.getProcessDefinitions()).thenReturn(List.of(definition));

        MvcResult result = mockMvc.perform(get("/api/v1/workflows/definitions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value("def-1"))
            .andExpect(jsonPath("$[0].key").value("documentApproval"))
            .andExpect(jsonPath("$[0].name").value("Document Approval Workflow"))
            .andExpect(jsonPath("$[0].version").value(3))
            .andReturn();

        JsonNode item = objectMapper.readTree(result.getResponse().getContentAsString()).get(0);
        assertEquals(processDefinitionFieldNames(), fieldNames(item));
    }

    @Test
    @DisplayName("GET /workflows/tasks/inbox locks task inbox item contract and nullable fields")
    void taskInboxLocksSummaryContract() throws Exception {
        WorkflowService.WorkflowTaskSummary summary = new WorkflowService.WorkflowTaskSummary(
            "task-1",
            "Approve document",
            null,
            null,
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
            true,
            "CLAIMABLE",
            null,
            new Date(1_700_000_000_000L)
        );
        Mockito.when(workflowService.listTasks("claimable", "approve", "doc-1", null, "pi-1", null, null, "sales"))
            .thenReturn(List.of(summary));

        MvcResult result = mockMvc.perform(get("/api/v1/workflows/tasks/inbox")
                .param("scope", "claimable")
                .param("query", "approve")
                .param("businessKey", "doc-1")
                .param("processId", "pi-1")
                .param("candidateGroup", "sales"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value("task-1"))
            .andExpect(jsonPath("$[0].assignee", nullValue()))
            .andExpect(jsonPath("$[0].owner", nullValue()))
            .andExpect(jsonPath("$[0].delegationState", nullValue()))
            .andExpect(jsonPath("$[0].dueDate", nullValue()))
            .andExpect(jsonPath("$[0].completedAt", nullValue()))
            .andExpect(jsonPath("$[0].claimable").value(true))
            .andExpect(jsonPath("$[0].status").value("CLAIMABLE"))
            .andReturn();

        JsonNode item = objectMapper.readTree(result.getResponse().getContentAsString()).get(0);
        assertEquals(taskInboxItemFieldNames(), fieldNames(item));
    }

    @Test
    @DisplayName("GET /workflows/tasks/{taskId} locks task detail and submission summary contracts")
    void taskDetailLocksTaskDetailContract() throws Exception {
        WorkflowService.WorkflowTaskDetail detail = new WorkflowService.WorkflowTaskDetail(
            "task-1",
            "Approve document",
            "Review the submission",
            "bob",
            null,
            null,
            new Date(1_700_000_000_000L),
            null,
            "pi-1",
            "def-1",
            "approvalTask",
            "documentApproval",
            "Document Approval Workflow",
            3,
            "doc-1",
            Map.of("documentName", "Report.pdf"),
            null,
            submissionSummaryWithOpenDecision()
        );
        Mockito.when(workflowService.getTaskDetail("task-1")).thenReturn(detail);

        MvcResult result = mockMvc.perform(get("/api/v1/workflows/tasks/{taskId}", "task-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("task-1"))
            .andExpect(jsonPath("$.owner", nullValue()))
            .andExpect(jsonPath("$.delegationState", nullValue()))
            .andExpect(jsonPath("$.dueDate", nullValue()))
            .andExpect(jsonPath("$.processDefinitionSuspended", nullValue()))
            .andExpect(jsonPath("$.processVariables.documentName").value("Report.pdf"))
            .andExpect(jsonPath("$.submissionSummary.decision", nullValue()))
            .andExpect(jsonPath("$.submissionSummary.decisionLabel", nullValue()))
            .andExpect(jsonPath("$.submissionSummary.reviewedBy", nullValue()))
            .andExpect(jsonPath("$.submissionSummary.reviewedAt", nullValue()))
            .andExpect(jsonPath("$.submissionSummary.comment", nullValue()))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(taskDetailFieldNames(), fieldNames(root));
        assertEquals(submissionSummaryFieldNames(), fieldNames(root.get("submissionSummary")));
    }

    @Test
    @DisplayName("GET /workflows/processes/browser locks process browser envelope and item contracts")
    void processBrowserLocksEnvelopeAndItemContracts() throws Exception {
        WorkflowService.WorkflowProcessBrowserItem item = new WorkflowService.WorkflowProcessBrowserItem(
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
            submissionSummaryWithOpenDecision()
        );
        WorkflowService.WorkflowProcessBrowserPage page = new WorkflowService.WorkflowProcessBrowserPage(
            List.of(item),
            5,
            10,
            11,
            true
        );
        Mockito.when(workflowService.listProcesses("active", "doc-1", "alice", "documentApproval", "review", 5, 10))
            .thenReturn(page);

        MvcResult result = mockMvc.perform(get("/api/v1/workflows/processes/browser")
                .param("status", "active")
                .param("businessKey", "doc-1")
                .param("startedBy", "alice")
                .param("definitionKey", "documentApproval")
                .param("query", "review")
                .param("skipCount", "5")
                .param("maxItems", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].id").value("pi-1"))
            .andExpect(jsonPath("$.items[0].endTime", nullValue()))
            .andExpect(jsonPath("$.items[0].ended").value(false))
            .andExpect(jsonPath("$.items[0].submissionSummary.decision", nullValue()))
            .andExpect(jsonPath("$.paging.skipCount").value(5))
            .andExpect(jsonPath("$.paging.maxItems").value(10))
            .andExpect(jsonPath("$.paging.totalItems").value(11))
            .andExpect(jsonPath("$.paging.hasMoreItems").value(true))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(processBrowserListFieldNames(), fieldNames(root));
        assertEquals(processBrowserItemFieldNames(), fieldNames(root.get("items").get(0)));
        assertEquals(submissionSummaryFieldNames(), fieldNames(root.get("items").get(0).get("submissionSummary")));
        assertEquals(pagingFieldNames(), fieldNames(root.get("paging")));
    }

    @Test
    @DisplayName("GET /workflows/processes/{processId} locks process detail contract")
    void processDetailLocksProcessDetailContract() throws Exception {
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
            null
        );
        Mockito.when(workflowService.getProcessDetail("pi-1")).thenReturn(detail);

        MvcResult result = mockMvc.perform(get("/api/v1/workflows/processes/{processId}", "pi-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("pi-1"))
            .andExpect(jsonPath("$.endTime", nullValue()))
            .andExpect(jsonPath("$.suspended").value(false))
            .andExpect(jsonPath("$.ended").value(false))
            .andExpect(jsonPath("$.variables.documentName").value("Report.pdf"))
            .andExpect(jsonPath("$.submissionSummary", nullValue()))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(processDetailFieldNames(), fieldNames(root));
    }

    private WorkflowService.WorkflowSubmissionSummary submissionSummaryWithOpenDecision() {
        return new WorkflowService.WorkflowSubmissionSummary(
            List.of("bob", "carol"),
            "Please review",
            "alice",
            "2026-03-19T01:02:03Z",
            null,
            null,
            null,
            null,
            null
        );
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> processDefinitionFieldNames() {
        return List.of("id", "key", "name", "version");
    }

    private static List<String> taskInboxItemFieldNames() {
        return List.of(
            "id",
            "name",
            "assignee",
            "owner",
            "delegationState",
            "description",
            "createTime",
            "dueDate",
            "processInstanceId",
            "processDefinitionId",
            "taskDefinitionKey",
            "processDefinitionKey",
            "processDefinitionName",
            "processDefinitionVersion",
            "businessKey",
            "startedBy",
            "claimable",
            "status",
            "completedAt"
        );
    }

    private static List<String> taskDetailFieldNames() {
        return List.of(
            "id",
            "name",
            "description",
            "assignee",
            "owner",
            "delegationState",
            "createTime",
            "dueDate",
            "processInstanceId",
            "processDefinitionId",
            "taskDefinitionKey",
            "processDefinitionKey",
            "processDefinitionName",
            "processDefinitionVersion",
            "businessKey",
            "processVariables",
            "processDefinitionSuspended",
            "submissionSummary"
        );
    }

    private static List<String> processBrowserListFieldNames() {
        return List.of("items", "paging");
    }

    private static List<String> processBrowserItemFieldNames() {
        return List.of(
            "id",
            "processDefinitionId",
            "processDefinitionKey",
            "processDefinitionName",
            "processDefinitionVersion",
            "businessKey",
            "startedBy",
            "startTime",
            "endTime",
            "ended",
            "submissionSummary"
        );
    }

    private static List<String> pagingFieldNames() {
        return List.of("skipCount", "maxItems", "totalItems", "hasMoreItems");
    }

    private static List<String> processDetailFieldNames() {
        return List.of(
            "id",
            "processDefinitionId",
            "processDefinitionKey",
            "processDefinitionName",
            "processDefinitionVersion",
            "businessKey",
            "startedBy",
            "startTime",
            "endTime",
            "suspended",
            "ended",
            "variables",
            "submissionSummary"
        );
    }

    private static List<String> submissionSummaryFieldNames() {
        return List.of(
            "approvers",
            "startComment",
            "startFormSubmittedBy",
            "startFormSubmittedAt",
            "decision",
            "decisionLabel",
            "reviewedBy",
            "reviewedAt",
            "comment"
        );
    }
}
