package com.ecm.core.controller;

import com.ecm.core.service.BatchDownloadAsyncTaskRegistry;
import com.ecm.core.service.BatchDownloadAsyncTaskRegistry.BatchDownloadAsyncStatus;
import com.ecm.core.service.BatchDownloadAsyncTaskRegistry.BatchDownloadAsyncTask;
import com.ecm.core.service.BatchDownloadService;
import com.ecm.core.service.SecurityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipOutputStream;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BatchDownloadControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private BatchDownloadService batchDownloadService;

    @Mock
    private SecurityService securityService;

    private final BatchDownloadAsyncTaskRegistry batchDownloadAsyncTaskRegistry = new BatchDownloadAsyncTaskRegistry();

    private UUID nodeId;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BatchDownloadController(
                batchDownloadService,
                batchDownloadAsyncTaskRegistry,
                securityService))
            .setControllerAdvice(new RestExceptionHandler())
            .build();
        nodeId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Async batch download supports start list status and artifact download")
    void asyncBatchDownloadLifecycleWorks() throws Exception {
        when(batchDownloadService.inspectNodesPreflight(List.of(nodeId)))
            .thenReturn(new BatchDownloadService.BatchDownloadPreflightSummary(
                1,
                1,
                0,
                List.of(nodeId),
                1,
                1,
                2L,
                0,
                0,
                0,
                0,
                0,
                true,
                BatchDownloadService.BatchDownloadPreflightDecision.READY,
                BatchDownloadService.BatchDownloadPreflightPrimaryReason.NONE,
                "Ready to download 1 file(s) from 1 node(s)",
                List.of(),
                List.of(new BatchDownloadService.BatchDownloadPreflightItem(
                    nodeId,
                    "sample.txt",
                    "DOCUMENT",
                    BatchDownloadService.BatchDownloadPreflightOutcome.INCLUDED,
                    1,
                    2L,
                    "Included document"
                ))
            ));
        when(securityService.getCurrentUser()).thenReturn("alice");
        doAnswer(invocation -> {
            BatchDownloadService.BatchDownloadProgressListener listener = invocation.getArgument(2);
            listener.onFileAdded(nodeId, "sample.txt", 2L, 1, 2L);
            ZipOutputStream zipOut = invocation.getArgument(1);
            zipOut.putNextEntry(new java.util.zip.ZipEntry("sample.txt"));
            zipOut.write("ok".getBytes(StandardCharsets.UTF_8));
            zipOut.closeEntry();
            return new BatchDownloadService.BatchDownloadArchiveSummary(1, 2L, false);
        }).when(batchDownloadService).writeNodesAsZip(anyList(), any(ZipOutputStream.class), any(BatchDownloadService.BatchDownloadProgressListener.class));

        MvcResult startResult = mockMvc.perform(post("/api/v1/nodes/download/batch-async")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nodeIds\":[\"" + nodeId + "\"],\"name\":\"selection\"}"))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", containsString("/api/v1/nodes/download/batch-async/")))
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andExpect(jsonPath("$.createdBy").value("alice"))
            .andReturn();

        JsonNode startPayload = objectMapper.readTree(startResult.getResponse().getContentAsString());
        String taskId = startPayload.path("taskId").asText();

        JsonNode taskPayload = null;
        for (int i = 0; i < 30; i++) {
            MvcResult taskResult = mockMvc.perform(get("/api/v1/nodes/download/batch-async/{taskId}", taskId))
                .andExpect(status().isOk())
                .andReturn();
            taskPayload = objectMapper.readTree(taskResult.getResponse().getContentAsString());
            if ("COMPLETED".equals(taskPayload.path("status").asText())) {
                break;
            }
            Thread.sleep(20L);
        }

        org.junit.jupiter.api.Assertions.assertNotNull(taskPayload);
        org.junit.jupiter.api.Assertions.assertEquals("COMPLETED", taskPayload.path("status").asText());
        org.junit.jupiter.api.Assertions.assertEquals("alice", taskPayload.path("createdBy").asText());
        org.junit.jupiter.api.Assertions.assertTrue(taskPayload.path("downloadReady").asBoolean());
        org.junit.jupiter.api.Assertions.assertEquals(1, taskPayload.path("filesAdded").asInt());
        org.junit.jupiter.api.Assertions.assertTrue(taskPayload.path("cleanupEligible").asBoolean());
        org.junit.jupiter.api.Assertions.assertTrue(taskPayload.path("artifactPresent").asBoolean());
        org.junit.jupiter.api.Assertions.assertTrue(taskPayload.path("archiveSizeBytes").asLong() > 0L);
        org.junit.jupiter.api.Assertions.assertTrue(taskPayload.path("cleanupUrl").asText().endsWith("/cleanup"));
        org.junit.jupiter.api.Assertions.assertNotNull(taskPayload.path("retentionExpiresAt").asText());

        mockMvc.perform(get("/api/v1/nodes/download/batch-async")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].taskId").value(taskId))
            .andExpect(jsonPath("$.items[0].createdBy").value("alice"))
            .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$.paging.maxItems").value(10))
            .andExpect(jsonPath("$.paging.skipCount").value(0))
            .andExpect(jsonPath("$.paging.totalItems").value(1))
            .andExpect(jsonPath("$.paging.hasMoreItems").value(false));

        mockMvc.perform(get("/api/v1/nodes/download/batch-async/{taskId}/download", taskId))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("selection_")));

        mockMvc.perform(post("/api/v1/nodes/download/batch-async/{taskId}/cleanup", taskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").value(taskId))
            .andExpect(jsonPath("$.deletedCount").value(1))
            .andExpect(jsonPath("$.remainingCount").value(0));

        org.junit.jupiter.api.Assertions.assertNull(batchDownloadAsyncTaskRegistry.get(taskId));
    }

    @Test
    @DisplayName("Async batch download rejects empty node ids")
    void asyncBatchDownloadRejectsEmptyNodeIds() throws Exception {
        mockMvc.perform(post("/api/v1/nodes/download/batch-async")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nodeIds\":[],\"name\":\"selection\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("At least one nodeId is required"));
    }

    @Test
    @DisplayName("Async batch download preflight returns structured warnings")
    void asyncBatchDownloadPreflightReturnsStructuredWarnings() throws Exception {
        UUID missingNodeId = UUID.randomUUID();
        when(batchDownloadService.inspectNodesPreflight(List.of(nodeId, missingNodeId, nodeId)))
            .thenReturn(new BatchDownloadService.BatchDownloadPreflightSummary(
                3,
                2,
                1,
                List.of(nodeId),
                1,
                2,
                64L,
                1,
                0,
                0,
                0,
                2,
                true,
                BatchDownloadService.BatchDownloadPreflightDecision.PARTIAL,
                BatchDownloadService.BatchDownloadPreflightPrimaryReason.MISSING_NODES,
                "Ready to download 2 file(s) from 1 node(s); skipped 2 item(s) during preflight",
                List.of(
                    "Skipped 1 duplicate node reference(s)",
                    "1 node(s) were not found"
                ),
                List.of(
                    new BatchDownloadService.BatchDownloadPreflightItem(
                        nodeId,
                        "selection",
                        "FOLDER",
                        BatchDownloadService.BatchDownloadPreflightOutcome.INCLUDED,
                        2,
                        64L,
                        "Included 2 readable file(s) from folder"
                    ),
                    new BatchDownloadService.BatchDownloadPreflightItem(
                        missingNodeId,
                        null,
                        null,
                        BatchDownloadService.BatchDownloadPreflightOutcome.MISSING,
                        0,
                        0L,
                        "Node not found"
                    )
                )
            ));

        mockMvc.perform(post("/api/v1/nodes/download/batch-async/preflight")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nodeIds\":[\"" + nodeId + "\",\"" + missingNodeId + "\",\"" + nodeId + "\"],\"name\":\"selection\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestedCount").value(3))
            .andExpect(jsonPath("$.distinctCount").value(2))
            .andExpect(jsonPath("$.duplicateCount").value(1))
            .andExpect(jsonPath("$.includedNodeIds[0]").value(nodeId.toString()))
            .andExpect(jsonPath("$.includedFileCount").value(2))
            .andExpect(jsonPath("$.missingCount").value(1))
            .andExpect(jsonPath("$.skippedCount").value(2))
            .andExpect(jsonPath("$.executable").value(true))
            .andExpect(jsonPath("$.decision").value("PARTIAL"))
            .andExpect(jsonPath("$.primaryReason").value("MISSING_NODES"))
            .andExpect(jsonPath("$.warnings[0]").value("Skipped 1 duplicate node reference(s)"))
            .andExpect(jsonPath("$.items[1].outcome").value("MISSING"));
    }

    @Test
    @DisplayName("Async batch download start rejects non-executable preflight")
    void asyncBatchDownloadStartRejectsNonExecutablePreflight() throws Exception {
        when(batchDownloadService.inspectNodesPreflight(List.of(nodeId)))
            .thenReturn(new BatchDownloadService.BatchDownloadPreflightSummary(
                1,
                1,
                0,
                List.of(),
                0,
                0,
                0L,
                0,
                0,
                1,
                0,
                1,
                false,
                BatchDownloadService.BatchDownloadPreflightDecision.BLOCKED,
                BatchDownloadService.BatchDownloadPreflightPrimaryReason.FORBIDDEN_NODES,
                "No readable files available for batch download",
                List.of("1 node(s) are not readable"),
                List.of(new BatchDownloadService.BatchDownloadPreflightItem(
                    nodeId,
                    "secret.txt",
                    "DOCUMENT",
                    BatchDownloadService.BatchDownloadPreflightOutcome.FORBIDDEN,
                    0,
                    0L,
                    "Read permission required"
                ))
            ));

        mockMvc.perform(post("/api/v1/nodes/download/batch-async")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nodeIds\":[\"" + nodeId + "\"],\"name\":\"selection\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("No readable files available for batch download"));
    }

    @Test
    @DisplayName("Async batch download summary returns lifecycle counts")
    void asyncBatchDownloadSummaryReturnsCounts() throws Exception {
        batchDownloadAsyncTaskRegistry.register(buildTask("task-queued", BatchDownloadAsyncStatus.QUEUED, null));
        batchDownloadAsyncTaskRegistry.register(buildTask("task-running", BatchDownloadAsyncStatus.RUNNING, null));
        batchDownloadAsyncTaskRegistry.register(buildTask("task-cancel-requested", BatchDownloadAsyncStatus.CANCEL_REQUESTED, null));
        batchDownloadAsyncTaskRegistry.register(buildTask("task-cancelled", BatchDownloadAsyncStatus.CANCELLED, null));
        batchDownloadAsyncTaskRegistry.register(buildTask("task-completed", BatchDownloadAsyncStatus.COMPLETED, null));
        batchDownloadAsyncTaskRegistry.register(buildTask("task-failed", BatchDownloadAsyncStatus.FAILED, null));

        mockMvc.perform(get("/api/v1/nodes/download/batch-async/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCount").value(6))
            .andExpect(jsonPath("$.activeCount").value(3))
            .andExpect(jsonPath("$.terminalCount").value(3))
            .andExpect(jsonPath("$.queuedCount").value(1))
            .andExpect(jsonPath("$.runningCount").value(1))
            .andExpect(jsonPath("$.cancelRequestedCount").value(1))
            .andExpect(jsonPath("$.cancelledCount").value(1))
            .andExpect(jsonPath("$.completedCount").value(1))
            .andExpect(jsonPath("$.failedCount").value(1));
    }

    @Test
    @DisplayName("Async batch download list supports skipCount and maxItems paging")
    void asyncBatchDownloadListSupportsPaging() throws Exception {
        batchDownloadAsyncTaskRegistry.register(buildTask("task-1", BatchDownloadAsyncStatus.COMPLETED, null));
        batchDownloadAsyncTaskRegistry.register(buildTask("task-2", BatchDownloadAsyncStatus.COMPLETED, null));
        batchDownloadAsyncTaskRegistry.register(buildTask("task-3", BatchDownloadAsyncStatus.COMPLETED, null));

        mockMvc.perform(get("/api/v1/nodes/download/batch-async")
                .param("maxItems", "1")
                .param("skipCount", "1")
                .param("status", "COMPLETED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].taskId").value("task-2"))
            .andExpect(jsonPath("$.paging.maxItems").value(1))
            .andExpect(jsonPath("$.paging.skipCount").value(1))
            .andExpect(jsonPath("$.paging.totalItems").value(3))
            .andExpect(jsonPath("$.paging.hasMoreItems").value(true));
    }

    @Test
    @DisplayName("Async batch download list supports owner filtering")
    void asyncBatchDownloadListSupportsOwnerFiltering() throws Exception {
        batchDownloadAsyncTaskRegistry.register(buildTask("task-alice", BatchDownloadAsyncStatus.COMPLETED, null, "alice"));
        batchDownloadAsyncTaskRegistry.register(buildTask("task-bob", BatchDownloadAsyncStatus.COMPLETED, null, "bob"));

        mockMvc.perform(get("/api/v1/nodes/download/batch-async")
                .param("maxItems", "10")
                .param("owner", "alice"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$.items[0].taskId").value("task-alice"))
            .andExpect(jsonPath("$.items[0].createdBy").value("alice"))
            .andExpect(jsonPath("$.paging.totalItems").value(1));
    }

    @Test
    @DisplayName("Async batch download list supports status and query filtering")
    void asyncBatchDownloadListSupportsStatusAndQueryFiltering() throws Exception {
        batchDownloadAsyncTaskRegistry.register(new BatchDownloadAsyncTask(
            "task-finance-1",
            List.of(UUID.randomUUID()),
            "finance-export",
            "alice",
            "finance-report.zip",
            BatchDownloadAsyncStatus.COMPLETED,
            1,
            1,
            100L,
            100L,
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            null,
            null
        ));
        batchDownloadAsyncTaskRegistry.register(new BatchDownloadAsyncTask(
            "task-ops-2",
            List.of(UUID.randomUUID()),
            "ops-export",
            "bob",
            "ops-report.zip",
            BatchDownloadAsyncStatus.COMPLETED,
            1,
            1,
            100L,
            100L,
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            null,
            null
        ));
        batchDownloadAsyncTaskRegistry.register(buildTask("task-finance-running", BatchDownloadAsyncStatus.RUNNING, null));

        mockMvc.perform(get("/api/v1/nodes/download/batch-async")
                .param("maxItems", "10")
                .param("status", "COMPLETED")
                .param("q", "finance"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$.items[0].taskId").value("task-finance-1"))
            .andExpect(jsonPath("$.paging.totalItems").value(1))
            .andExpect(jsonPath("$.paging.hasMoreItems").value(false));
    }

    @Test
    @DisplayName("Async batch download cleanup removes terminal tasks and artifacts")
    void asyncBatchDownloadCleanupRemovesTerminalTasks() throws Exception {
        Path archive = Files.createTempFile("athena-batch-download-test-", ".zip");
        batchDownloadAsyncTaskRegistry.register(buildTask("task-terminal", BatchDownloadAsyncStatus.COMPLETED, archive));
        batchDownloadAsyncTaskRegistry.register(buildTask("task-active", BatchDownloadAsyncStatus.RUNNING, null));

        mockMvc.perform(post("/api/v1/nodes/download/batch-async/cleanup"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletedCount").value(1))
            .andExpect(jsonPath("$.remainingCount").value(1));

        org.junit.jupiter.api.Assertions.assertNull(batchDownloadAsyncTaskRegistry.get("task-terminal"));
        org.junit.jupiter.api.Assertions.assertTrue(Files.notExists(archive));
        org.junit.jupiter.api.Assertions.assertEquals(BatchDownloadAsyncStatus.RUNNING, batchDownloadAsyncTaskRegistry.get("task-active").status());
    }

    @Test
    @DisplayName("Async batch download per-task cleanup rejects active tasks")
    void asyncBatchDownloadCleanupRejectsActiveTasks() throws Exception {
        batchDownloadAsyncTaskRegistry.register(buildTask("task-running", BatchDownloadAsyncStatus.RUNNING, null));

        mockMvc.perform(post("/api/v1/nodes/download/batch-async/{taskId}/cleanup", "task-running"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Cleanup is only available for terminal batch download tasks"));
    }

    @Test
    @DisplayName("Async batch download cancel-active updates queued and running tasks")
    void asyncBatchDownloadCancelActiveCancelsTasks() throws Exception {
        batchDownloadAsyncTaskRegistry.register(buildTask("task-queued", BatchDownloadAsyncStatus.QUEUED, null));
        batchDownloadAsyncTaskRegistry.register(buildTask("task-running", BatchDownloadAsyncStatus.RUNNING, null));

        mockMvc.perform(post("/api/v1/nodes/download/batch-async/cancel-active"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cancelledCount").value(2))
            .andExpect(jsonPath("$.remainingActiveCount").value(1));

        org.junit.jupiter.api.Assertions.assertEquals(BatchDownloadAsyncStatus.CANCELLED, batchDownloadAsyncTaskRegistry.get("task-queued").status());
        org.junit.jupiter.api.Assertions.assertEquals(BatchDownloadAsyncStatus.CANCEL_REQUESTED, batchDownloadAsyncTaskRegistry.get("task-running").status());
    }

    private BatchDownloadAsyncTask buildTask(
        String taskId,
        BatchDownloadAsyncStatus status,
        Path archivePath
    ) {
        return buildTask(taskId, status, archivePath, "alice");
    }

    private BatchDownloadAsyncTask buildTask(
        String taskId,
        BatchDownloadAsyncStatus status,
        Path archivePath,
        String createdBy
    ) {
        return new BatchDownloadAsyncTask(
            taskId,
            List.of(nodeId),
            "selection",
            createdBy,
            "selection.zip",
            status,
            1,
            1,
            100L,
            100L,
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().minusMinutes(50),
            status.isTerminal() ? LocalDateTime.now().minusMinutes(10) : null,
            null,
            archivePath
        );
    }
}
