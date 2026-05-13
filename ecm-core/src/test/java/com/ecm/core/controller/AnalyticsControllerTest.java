package com.ecm.core.controller;

import com.ecm.core.asynctask.AsyncTaskAcknowledgementService;
import com.ecm.core.asynctask.AsyncTaskGovernanceDomainSnapshot;
import com.ecm.core.asynctask.AsyncTaskGovernanceOverviewSnapshot;
import com.ecm.core.asynctask.AsyncTaskGovernanceRiskLevel;
import com.ecm.core.asynctask.AsyncTaskGovernanceService;
import com.ecm.core.asynctask.AsyncTaskGovernanceStatus;
import com.ecm.core.asynctask.AsyncTaskLifecycleListSnapshot;
import com.ecm.core.asynctask.AsyncTaskLifecycleService;
import com.ecm.core.asynctask.AsyncTaskStatusSnapshot;
import com.ecm.core.asynctask.AsyncTaskActionSnapshot;
import com.ecm.core.asynctask.AsyncTaskSummarySnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ecm.core.entity.AsyncTaskAcknowledgement;
import com.ecm.core.entity.AuditCategory;
import com.ecm.core.entity.AuditCategorySetting;
import com.ecm.core.entity.AuditLog;
import com.ecm.core.service.AnalyticsService;
import com.ecm.core.service.AuditExportAsyncTaskRegistry;
import com.ecm.core.service.AuditService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private AuditService auditService;

    @Mock
    private AsyncTaskGovernanceService asyncTaskGovernanceService;

    @Mock
    private AsyncTaskLifecycleService asyncTaskLifecycleService;

    @Mock
    private AsyncTaskAcknowledgementService asyncTaskAcknowledgementService;

    private AuditExportAsyncTaskRegistry auditExportAsyncTaskRegistry;
    private AnalyticsController analyticsController;

    private TimeZone originalTimeZone;

    @BeforeEach
    void setup() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        auditExportAsyncTaskRegistry = new AuditExportAsyncTaskRegistry();
        analyticsController = new AnalyticsController(
            analyticsService,
            auditService,
            auditExportAsyncTaskRegistry,
            asyncTaskGovernanceService,
            asyncTaskLifecycleService,
            asyncTaskAcknowledgementService
        );
        ReflectionTestUtils.setField(analyticsController, "auditExportMaxRangeDays", 30);
        mockMvc = MockMvcBuilders.standaloneSetup(analyticsController).build();
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    @DisplayName("Audit export accepts ISO offset datetime")
    void exportAuditLogsAcceptsOffsetDatetime() throws Exception {
        Mockito.when(analyticsService.exportAuditLogsCsv(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
            .thenReturn(new AnalyticsService.AuditExportResult("header\n", 0));

        mockMvc.perform(get("/api/v1/analytics/audit/export")
                .param("from", "2026-01-05T10:15:30+08:00")
                .param("to", "2026-01-05T12:15:30+08:00"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")))
            .andExpect(header().string("X-Audit-Export-Count", "0"));

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        Mockito.verify(analyticsService).exportAuditLogsCsv(
            fromCaptor.capture(),
            toCaptor.capture(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull()
        );

        assertEquals(LocalDateTime.of(2026, 1, 5, 2, 15, 30), fromCaptor.getValue());
        assertEquals(LocalDateTime.of(2026, 1, 5, 4, 15, 30), toCaptor.getValue());
    }

    @Test
    @DisplayName("Audit categories endpoint returns toggles")
    void auditCategoriesList() throws Exception {
        Mockito.when(auditService.getCategorySettings())
            .thenReturn(List.of(AuditCategorySetting.builder()
                .category(AuditCategory.NODE)
                .enabled(true)
                .build()));

        mockMvc.perform(get("/api/v1/analytics/audit/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].category").value("NODE"))
            .andExpect(jsonPath("$[0].enabled").value(true));
    }

    @Test
    @DisplayName("Audit categories endpoint updates toggles")
    void auditCategoriesUpdate() throws Exception {
        Mockito.when(auditService.updateCategorySettings(Mockito.any()))
            .thenReturn(List.of(AuditCategorySetting.builder()
                .category(AuditCategory.MAIL)
                .enabled(false)
                .build()));

        mockMvc.perform(put("/api/v1/analytics/audit/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"category\":\"MAIL\",\"enabled\":false}]"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].category").value("MAIL"))
            .andExpect(jsonPath("$[0].enabled").value(false));
    }

    @Test
    @DisplayName("Audit export accepts local datetime without offset")
    void exportAuditLogsAcceptsLocalDatetime() throws Exception {
        Mockito.when(analyticsService.exportAuditLogsCsv(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
            .thenReturn(new AnalyticsService.AuditExportResult("header\n", 0));

        mockMvc.perform(get("/api/v1/analytics/audit/export")
                .param("from", "2026-01-05T10:15:30")
                .param("to", "2026-01-05T12:15:30"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")));

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        Mockito.verify(analyticsService).exportAuditLogsCsv(
            fromCaptor.capture(),
            toCaptor.capture(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull()
        );

        assertEquals(LocalDateTime.of(2026, 1, 5, 10, 15, 30), fromCaptor.getValue());
        assertEquals(LocalDateTime.of(2026, 1, 5, 12, 15, 30), toCaptor.getValue());
    }

    @Test
    @DisplayName("Audit export rejects invalid datetime")
    void exportAuditLogsRejectsInvalidDatetime() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/audit/export")
                .param("from", "not-a-date")
                .param("to", "2026-01-05T12:15:30"))
            .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("Audit export rejects reversed range")
    void exportAuditLogsRejectsReversedRange() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/audit/export")
                .param("from", "2026-01-05T12:15:30")
                .param("to", "2026-01-05T10:15:30"))
            .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("Audit export rejects range after offset normalization")
    void exportAuditLogsRejectsOffsetNormalizedRange() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/audit/export")
                .param("from", "2026-01-05T10:00:00+08:00")
                .param("to", "2026-01-05T01:00:00+00:00"))
            .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("Audit export rejects empty range")
    void exportAuditLogsRejectsEmptyRange() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/audit/export")
                .param("from", "2026-01-05T10:15:30")
                .param("to", "2026-01-05T10:15:30"))
            .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("Audit export rejects range exceeding max window")
    void exportAuditLogsRejectsRangeExceedingMax() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/audit/export")
                .param("from", "2026-01-01T00:00:00")
                .param("to", "2026-02-02T00:00:00"))
            .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("Audit export rejects blank parameters")
    void exportAuditLogsRejectsBlankParameters() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/audit/export")
                .param("from", " ")
                .param("to", "2026-01-05T12:15:30"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/analytics/audit/export")
                .param("from", "2026-01-05T10:15:30")
                .param("to", " "))
            .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("Audit export accepts range at max window")
    void exportAuditLogsAcceptsMaxWindow() throws Exception {
        Mockito.when(analyticsService.exportAuditLogsCsv(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
            .thenReturn(new AnalyticsService.AuditExportResult("header\nrow\n", 1));

        mockMvc.perform(get("/api/v1/analytics/audit/export")
                .param("from", "2026-01-01T00:00:00")
                .param("to", "2026-01-31T00:00:00"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Audit-Export-Count", "1"));

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        Mockito.verify(analyticsService).exportAuditLogsCsv(
            fromCaptor.capture(),
            toCaptor.capture(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull()
        );

        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0, 0), fromCaptor.getValue());
        assertEquals(LocalDateTime.of(2026, 1, 31, 0, 0, 0), toCaptor.getValue());
    }

    @Test
    @DisplayName("Audit export sets CSV headers and filename")
    void exportAuditLogsSetsCsvHeadersAndFilename() throws Exception {
        Mockito.when(analyticsService.exportAuditLogsCsv(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
            .thenReturn(new AnalyticsService.AuditExportResult("header\nrow\n", 2));

        mockMvc.perform(get("/api/v1/analytics/audit/export")
                .param("from", "2026-01-05T00:00:00")
                .param("to", "2026-01-06T00:00:00"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/csv; charset=UTF-8"))
            .andExpect(header().string("Content-Disposition",
                Matchers.containsString("audit_logs_20260105_to_20260106.csv")))
            .andExpect(header().string("X-Audit-Export-Count", "2"))
            .andExpect(content().string("header\nrow\n"));

        Mockito.verify(analyticsService).exportAuditLogsCsv(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("Audit export async start/list flow works")
    void auditExportAsyncStartAndListFlowWorks() throws Exception {
        Mockito.when(analyticsService.exportAuditLogsCsv(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
            .thenReturn(new AnalyticsService.AuditExportResult("header\nrow\n", 1));

        MvcResult startResult = mockMvc.perform(post("/api/v1/analytics/audit/export-async")
                .param("from", "2026-01-05T00:00:00")
                .param("to", "2026-01-06T00:00:00"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").isNotEmpty())
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andReturn();

        String taskId = objectMapper.readTree(startResult.getResponse().getContentAsString())
            .path("taskId")
            .asText();

        mockMvc.perform(get("/api/v1/analytics/audit/export-async")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.items[0].taskId").value(taskId))
            .andExpect(jsonPath("$.items[0].status").isNotEmpty())
            .andExpect(jsonPath("$.items[0].createdAt").isNotEmpty());

        awaitAuditExportAsyncTaskTerminalStatus(taskId);
    }

    @Test
    @DisplayName("Audit export async list supports COMPLETED status filter")
    void auditExportAsyncListFiltersByCompletedStatus() throws Exception {
        Mockito.when(analyticsService.exportAuditLogsCsv(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
            .thenReturn(new AnalyticsService.AuditExportResult("header\nrow\n", 1));

        MvcResult startResult = mockMvc.perform(post("/api/v1/analytics/audit/export-async")
                .param("from", "2026-01-05T00:00:00")
                .param("to", "2026-01-06T00:00:00"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").isNotEmpty())
            .andReturn();

        String taskId = objectMapper.readTree(startResult.getResponse().getContentAsString())
            .path("taskId")
            .asText();

        assertEquals("COMPLETED", awaitAuditExportAsyncTaskTerminalStatus(taskId));

        mockMvc.perform(get("/api/v1/analytics/audit/export-async")
                .param("limit", "10")
                .param("status", "COMPLETED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.items[0].taskId").value(taskId))
            .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$.items[*].status", Matchers.everyItem(Matchers.is("COMPLETED"))));
    }

    @Test
    @DisplayName("Audit export async summary returns aggregate counters")
    void auditExportAsyncSummaryReturnsAggregateCounters() throws Exception {
        Mockito.when(analyticsService.exportAuditLogsCsv(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
            .thenReturn(new AnalyticsService.AuditExportResult("header\nrow\n", 1));

        String taskId = startAuditExportAsyncTask();
        assertEquals("COMPLETED", awaitAuditExportAsyncTaskTerminalStatus(taskId));

        MvcResult summaryResult = mockMvc.perform(get("/api/v1/analytics/audit/export-async/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCount").isNumber())
            .andExpect(jsonPath("$.queuedCount").isNumber())
            .andExpect(jsonPath("$.runningCount").isNumber())
            .andExpect(jsonPath("$.completedCount").isNumber())
            .andExpect(jsonPath("$.cancelledCount").isNumber())
            .andExpect(jsonPath("$.failedCount").isNumber())
            .andExpect(jsonPath("$.activeCount").isNumber())
            .andExpect(jsonPath("$.terminalCount").isNumber())
            .andReturn();

        JsonNode payload = objectMapper.readTree(summaryResult.getResponse().getContentAsString());
        long queuedCount = payload.path("queuedCount").asLong();
        long runningCount = payload.path("runningCount").asLong();
        long completedCount = payload.path("completedCount").asLong();
        long cancelledCount = payload.path("cancelledCount").asLong();
        long failedCount = payload.path("failedCount").asLong();
        long totalCount = payload.path("totalCount").asLong();
        long activeCount = payload.path("activeCount").asLong();
        long terminalCount = payload.path("terminalCount").asLong();

        assertEquals(totalCount, queuedCount + runningCount + completedCount + cancelledCount + failedCount);
        assertEquals(activeCount, queuedCount + runningCount);
        assertEquals(terminalCount, completedCount + cancelledCount + failedCount);
        assertTrue(completedCount >= 1);
    }

    @Test
    @DisplayName("Audit export async summary supports COMPLETED status filter")
    void auditExportAsyncSummaryFiltersByCompletedStatus() throws Exception {
        Mockito.when(analyticsService.exportAuditLogsCsv(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
            .thenReturn(new AnalyticsService.AuditExportResult("header\nrow\n", 1));

        String taskId = startAuditExportAsyncTask();
        assertEquals("COMPLETED", awaitAuditExportAsyncTaskTerminalStatus(taskId));

        mockMvc.perform(get("/api/v1/analytics/audit/export-async/summary")
                .param("status", "completed"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCount").value(1))
            .andExpect(jsonPath("$.queuedCount").value(0))
            .andExpect(jsonPath("$.runningCount").value(0))
            .andExpect(jsonPath("$.completedCount").value(1))
            .andExpect(jsonPath("$.cancelledCount").value(0))
            .andExpect(jsonPath("$.failedCount").value(0))
            .andExpect(jsonPath("$.activeCount").value(0))
            .andExpect(jsonPath("$.terminalCount").value(1));
    }

    @Test
    @DisplayName("Audit export async summary rejects invalid status filter")
    void auditExportAsyncSummaryRejectsInvalidStatusFilter() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/audit/export-async/summary")
                .param("status", "NOT_A_VALID_STATUS"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Audit export async cancel-active without status cancels active tasks and updates summary")
    void auditExportAsyncCancelActiveWithoutStatusCancelsActiveTasksAndUpdatesSummary() throws Exception {
        CountDownLatch startedLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);

        Mockito.when(analyticsService.exportAuditLogsCsv(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
            .thenAnswer(invocation -> {
                startedLatch.countDown();
                if (!releaseLatch.await(2, TimeUnit.SECONDS)) {
                    throw new RuntimeException("simulated export timeout");
                }
                return new AnalyticsService.AuditExportResult("header\nrow\n", 1);
            });

        String taskId = startAuditExportAsyncTask();
        try {
            assertTrue(startedLatch.await(1, TimeUnit.SECONDS), "async export task did not start in time");
            awaitAuditExportAsyncActiveCountAtLeast(1);

            mockMvc.perform(post("/api/v1/analytics/audit/export-async/cancel-active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledCount").value(1))
                .andExpect(jsonPath("$.remainingActiveCount").value(0))
                .andExpect(jsonPath("$.message", Matchers.containsString("Cancelled")));

            mockMvc.perform(get("/api/v1/analytics/audit/export-async/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeCount").value(0))
                .andExpect(jsonPath("$.cancelledCount").value(1));
        } finally {
            releaseLatch.countDown();
            assertEquals("CANCELLED", awaitAuditExportAsyncTaskTerminalStatus(taskId));
        }
    }

    @Test
    @DisplayName("Audit export async cancel-active supports QUEUED status filter")
    void auditExportAsyncCancelActiveSupportsQueuedStatusFilter() throws Exception {
        mockMvc.perform(post("/api/v1/analytics/audit/export-async/cancel-active")
                .param("status", "queued"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cancelledCount").value(0))
            .andExpect(jsonPath("$.remainingActiveCount").value(0))
            .andExpect(jsonPath("$.statusFilter").value("QUEUED"));
    }

    @Test
    @DisplayName("Audit export async cancel-active rejects COMPLETED status filter")
    void auditExportAsyncCancelActiveRejectsCompletedStatusFilter() throws Exception {
        mockMvc.perform(post("/api/v1/analytics/audit/export-async/cancel-active")
                .param("status", "completed"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Audit export async cleanup without status removes all terminal tasks")
    void auditExportAsyncCleanupWithoutStatusRemovesTerminalTasks() throws Exception {
        Mockito.when(analyticsService.exportAuditLogsCsv(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
            .thenReturn(new AnalyticsService.AuditExportResult("header\nrow\n", 1))
            .thenThrow(new RuntimeException("simulated export failure"));

        String completedTaskId = startAuditExportAsyncTask();
        assertEquals("COMPLETED", awaitAuditExportAsyncTaskTerminalStatus(completedTaskId));

        String failedTaskId = startAuditExportAsyncTask();
        assertEquals("FAILED", awaitAuditExportAsyncTaskTerminalStatus(failedTaskId));

        mockMvc.perform(post("/api/v1/analytics/audit/export-async/cleanup"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletedCount").value(2))
            .andExpect(jsonPath("$.remainingCount").value(0))
            .andExpect(jsonPath("$.message", Matchers.containsString("terminal")));

        mockMvc.perform(get("/api/v1/analytics/audit/export-async/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCount").value(0))
            .andExpect(jsonPath("$.activeCount").value(0))
            .andExpect(jsonPath("$.terminalCount").value(0));
    }

    @Test
    @DisplayName("Audit export async cleanup with COMPLETED status only removes completed tasks")
    void auditExportAsyncCleanupWithCompletedStatusOnlyRemovesCompletedTasks() throws Exception {
        Mockito.when(analyticsService.exportAuditLogsCsv(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
            .thenReturn(new AnalyticsService.AuditExportResult("header\nrow\n", 1))
            .thenThrow(new RuntimeException("simulated export failure"));

        String completedTaskId = startAuditExportAsyncTask();
        assertEquals("COMPLETED", awaitAuditExportAsyncTaskTerminalStatus(completedTaskId));

        String failedTaskId = startAuditExportAsyncTask();
        assertEquals("FAILED", awaitAuditExportAsyncTaskTerminalStatus(failedTaskId));

        mockMvc.perform(post("/api/v1/analytics/audit/export-async/cleanup")
                .param("status", "completed"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletedCount").value(1))
            .andExpect(jsonPath("$.remainingCount").value(1))
            .andExpect(jsonPath("$.statusFilter").value("COMPLETED"))
            .andExpect(jsonPath("$.message", Matchers.containsString("COMPLETED")));

        mockMvc.perform(get("/api/v1/analytics/audit/export-async/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCount").value(1))
            .andExpect(jsonPath("$.completedCount").value(0))
            .andExpect(jsonPath("$.failedCount").value(1))
            .andExpect(jsonPath("$.terminalCount").value(1))
            .andExpect(jsonPath("$.activeCount").value(0));
    }

    @Test
    @DisplayName("Audit export async cleanup rejects RUNNING status filter")
    void auditExportAsyncCleanupRejectsRunningStatusFilter() throws Exception {
        mockMvc.perform(post("/api/v1/analytics/audit/export-async/cleanup")
                .param("status", "RUNNING"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Audit export async list rejects invalid status filter")
    void auditExportAsyncListRejectsInvalidStatusFilter() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/audit/export-async")
                .param("status", "NOT_A_VALID_STATUS"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Audit export async missing task status/cancel/download return 404")
    void auditExportAsyncMissingTaskEndpointsReturnNotFound() throws Exception {
        String missingTaskId = "missing-task";

        mockMvc.perform(get("/api/v1/analytics/audit/export-async/{taskId}", missingTaskId))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/analytics/audit/export-async/{taskId}/cancel", missingTaskId))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/analytics/audit/export-async/{taskId}/download", missingTaskId))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Audit retention info returns policy details")
    void auditRetentionInfoReturnsPolicyDetails() throws Exception {
        Mockito.when(analyticsService.getAuditRetentionDays()).thenReturn(120);
        Mockito.when(analyticsService.getExpiredAuditLogCount()).thenReturn(42L);

        mockMvc.perform(get("/api/v1/analytics/audit/retention"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.retentionDays").value(120))
            .andExpect(jsonPath("$.expiredLogCount").value(42))
            .andExpect(jsonPath("$.exportMaxRangeDays").value(30));
    }

    @Test
    @DisplayName("Audit cleanup returns deletion summary")
    void auditCleanupReturnsDeletionSummary() throws Exception {
        Mockito.when(analyticsService.manualCleanupExpiredAuditLogs()).thenReturn(7L);
        Mockito.when(analyticsService.getAuditRetentionDays()).thenReturn(120);

        mockMvc.perform(post("/api/v1/analytics/audit/cleanup"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletedCount").value(7))
            .andExpect(jsonPath("$.retentionDays").value(120))
            .andExpect(jsonPath("$.message").value("Deleted 7 expired audit logs"));
    }

    @Test
    @DisplayName("Audit cleanup returns no-op message when nothing deleted")
    void auditCleanupReturnsNoopMessage() throws Exception {
        Mockito.when(analyticsService.manualCleanupExpiredAuditLogs()).thenReturn(0L);
        Mockito.when(analyticsService.getAuditRetentionDays()).thenReturn(90);

        mockMvc.perform(post("/api/v1/analytics/audit/cleanup"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletedCount").value(0))
            .andExpect(jsonPath("$.retentionDays").value(90))
            .andExpect(jsonPath("$.message").value("No expired audit logs to delete"));
    }

    @Test
    @DisplayName("Recent audit defaults to limit 50")
    void recentAuditDefaultsToLimit() throws Exception {
        AuditLog log = new AuditLog();
        log.setEventType("LOGIN");
        log.setUsername("alice");
        log.setEventTime(LocalDateTime.of(2026, 1, 1, 0, 0));

        Mockito.when(analyticsService.getRecentActivity(50)).thenReturn(List.of(log));

        mockMvc.perform(get("/api/v1/analytics/audit/recent"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].eventType").value("LOGIN"))
            .andExpect(jsonPath("$[0].username").value("alice"));

        Mockito.verify(analyticsService).getRecentActivity(50);
    }

    @Test
    @DisplayName("Recent audit respects explicit limit parameter")
    void recentAuditRespectsLimitParameter() throws Exception {
        AuditLog log = new AuditLog();
        log.setEventType("VIEW");
        log.setUsername("bob");
        log.setEventTime(LocalDateTime.of(2026, 1, 2, 0, 0));

        Mockito.when(analyticsService.getRecentActivity(5)).thenReturn(List.of(log));

        mockMvc.perform(get("/api/v1/analytics/audit/recent")
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].eventType").value("VIEW"))
            .andExpect(jsonPath("$[0].username").value("bob"));

        Mockito.verify(analyticsService).getRecentActivity(5);
    }

    @Test
    @DisplayName("Recent rule activity defaults to limit 20")
    void recentRuleActivityDefaultsToLimit() throws Exception {
        AuditLog log = new AuditLog();
        log.setEventType("RULE_EXECUTED");
        log.setUsername("system");
        log.setEventTime(LocalDateTime.of(2026, 1, 3, 0, 0));

        Mockito.when(analyticsService.getRecentRuleActivity(20)).thenReturn(List.of(log));

        mockMvc.perform(get("/api/v1/analytics/rules/recent"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].eventType").value("RULE_EXECUTED"))
            .andExpect(jsonPath("$[0].username").value("system"));

        Mockito.verify(analyticsService).getRecentRuleActivity(20);
    }

    @Test
    @DisplayName("Recent rule activity respects explicit limit parameter")
    void recentRuleActivityRespectsLimitParameter() throws Exception {
        AuditLog log = new AuditLog();
        log.setEventType("RULE_EXECUTED");
        log.setUsername("automation");
        log.setEventTime(LocalDateTime.of(2026, 1, 4, 0, 0));

        Mockito.when(analyticsService.getRecentRuleActivity(3)).thenReturn(List.of(log));

        mockMvc.perform(get("/api/v1/analytics/rules/recent")
                .param("limit", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].eventType").value("RULE_EXECUTED"))
            .andExpect(jsonPath("$[0].username").value("automation"));

        Mockito.verify(analyticsService).getRecentRuleActivity(3);
    }

    @Test
    @DisplayName("Rule execution summary defaults to 7 days")
    void ruleExecutionSummaryDefaultsToWindow() throws Exception {
        AnalyticsService.RuleExecutionSummary summary = new AnalyticsService.RuleExecutionSummary(
            7,
            12,
            2,
            0.83,
            4,
            1,
            Map.of("RULE_EXECUTED", 12L)
        );

        Mockito.when(analyticsService.getRuleExecutionSummary(7)).thenReturn(summary);

        mockMvc.perform(get("/api/v1/analytics/rules/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowDays").value(7))
            .andExpect(jsonPath("$.executions").value(12))
            .andExpect(jsonPath("$.failures").value(2))
            .andExpect(jsonPath("$.scheduledBatches").value(4))
            .andExpect(jsonPath("$.scheduledFailures").value(1))
            .andExpect(jsonPath("$.countsByType.RULE_EXECUTED").value(12));

        Mockito.verify(analyticsService).getRuleExecutionSummary(7);
    }

    @Test
    @DisplayName("Rule execution summary respects explicit days parameter")
    void ruleExecutionSummaryRespectsDaysParameter() throws Exception {
        AnalyticsService.RuleExecutionSummary summary = new AnalyticsService.RuleExecutionSummary(
            14,
            20,
            0,
            1.0,
            5,
            0,
            Map.of("RULE_EXECUTED", 20L)
        );

        Mockito.when(analyticsService.getRuleExecutionSummary(14)).thenReturn(summary);

        mockMvc.perform(get("/api/v1/analytics/rules/summary")
                .param("days", "14"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowDays").value(14))
            .andExpect(jsonPath("$.executions").value(20))
            .andExpect(jsonPath("$.failures").value(0))
            .andExpect(jsonPath("$.scheduledBatches").value(5))
            .andExpect(jsonPath("$.scheduledFailures").value(0))
            .andExpect(jsonPath("$.countsByType.RULE_EXECUTED").value(20));

        Mockito.verify(analyticsService).getRuleExecutionSummary(14);
    }

    @Test
    @DisplayName("Daily activity defaults to 30 days")
    void dailyActivityDefaultsToWindow() throws Exception {
        List<AnalyticsService.DailyActivityStats> stats = List.of(
            new AnalyticsService.DailyActivityStats(LocalDate.of(2026, 1, 1), 4)
        );

        Mockito.when(analyticsService.getDailyActivity(30)).thenReturn(stats);

        mockMvc.perform(get("/api/v1/analytics/activity/daily"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].date[0]").value(2026))
            .andExpect(jsonPath("$[0].date[1]").value(1))
            .andExpect(jsonPath("$[0].date[2]").value(1))
            .andExpect(jsonPath("$[0].eventCount").value(4));

        Mockito.verify(analyticsService).getDailyActivity(30);
    }

    @Test
    @DisplayName("Daily activity respects explicit days parameter")
    void dailyActivityRespectsDaysParameter() throws Exception {
        List<AnalyticsService.DailyActivityStats> stats = List.of(
            new AnalyticsService.DailyActivityStats(LocalDate.of(2026, 1, 2), 2)
        );

        Mockito.when(analyticsService.getDailyActivity(7)).thenReturn(stats);

        mockMvc.perform(get("/api/v1/analytics/activity/daily")
                .param("days", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].date[0]").value(2026))
            .andExpect(jsonPath("$[0].date[1]").value(1))
            .andExpect(jsonPath("$[0].date[2]").value(2))
            .andExpect(jsonPath("$[0].eventCount").value(2));

        Mockito.verify(analyticsService).getDailyActivity(7);
    }

    @Test
    @DisplayName("System summary returns counts and size")
    void systemSummaryReturnsCounts() throws Exception {
        AnalyticsService.SystemSummaryStats summary = new AnalyticsService.SystemSummaryStats(12, 3, 4096);

        Mockito.when(analyticsService.getSystemSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/v1/analytics/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDocuments").value(12))
            .andExpect(jsonPath("$.totalFolders").value(3))
            .andExpect(jsonPath("$.totalSizeBytes").value(4096));

        Mockito.verify(analyticsService).getSystemSummary();
    }

    @Test
    @DisplayName("Storage by MIME type returns stats")
    void storageByMimeTypeReturnsStats() throws Exception {
        List<AnalyticsService.MimeTypeStats> stats = List.of(
            new AnalyticsService.MimeTypeStats("application/pdf", 2, 2048)
        );

        Mockito.when(analyticsService.getStorageByMimeType()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/analytics/storage/mimetype"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].mimeType").value("application/pdf"))
            .andExpect(jsonPath("$[0].count").value(2))
            .andExpect(jsonPath("$[0].sizeBytes").value(2048));

        Mockito.verify(analyticsService).getStorageByMimeType();
    }

    @Test
    @DisplayName("Top users defaults to limit 10")
    void topUsersDefaultsToLimit() throws Exception {
        List<AnalyticsService.UserActivityStats> stats = List.of(
            new AnalyticsService.UserActivityStats("alice", 3)
        );

        Mockito.when(analyticsService.getTopUsers(10)).thenReturn(stats);

        mockMvc.perform(get("/api/v1/analytics/users/top"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].username").value("alice"))
            .andExpect(jsonPath("$[0].activityCount").value(3));

        Mockito.verify(analyticsService).getTopUsers(10);
    }

    @Test
    @DisplayName("Top users respects explicit limit parameter")
    void topUsersRespectsLimitParameter() throws Exception {
        List<AnalyticsService.UserActivityStats> stats = List.of(
            new AnalyticsService.UserActivityStats("bob", 1)
        );

        Mockito.when(analyticsService.getTopUsers(5)).thenReturn(stats);

        mockMvc.perform(get("/api/v1/analytics/users/top")
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].username").value("bob"))
            .andExpect(jsonPath("$[0].activityCount").value(1));

        Mockito.verify(analyticsService).getTopUsers(5);
    }

    @Test
    @DisplayName("Dashboard aggregates summary, storage, activity, and top users")
    void dashboardAggregatesAnalytics() throws Exception {
        AnalyticsService.SystemSummaryStats summary = new AnalyticsService.SystemSummaryStats(10, 4, 2048);
        List<AnalyticsService.MimeTypeStats> storage = List.of(
            new AnalyticsService.MimeTypeStats("application/pdf", 2, 1024)
        );
        List<AnalyticsService.DailyActivityStats> activity = List.of(
            new AnalyticsService.DailyActivityStats(LocalDate.of(2026, 1, 5), 6)
        );
        List<AnalyticsService.UserActivityStats> topUsers = List.of(
            new AnalyticsService.UserActivityStats("carol", 8)
        );

        Mockito.when(analyticsService.getSystemSummary()).thenReturn(summary);
        Mockito.when(analyticsService.getStorageByMimeType()).thenReturn(storage);
        Mockito.when(analyticsService.getDailyActivity(14)).thenReturn(activity);
        Mockito.when(analyticsService.getTopUsers(5)).thenReturn(topUsers);

        mockMvc.perform(get("/api/v1/analytics/dashboard"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.totalDocuments").value(10))
            .andExpect(jsonPath("$.summary.totalFolders").value(4))
            .andExpect(jsonPath("$.summary.totalSizeBytes").value(2048))
            .andExpect(jsonPath("$.storage[0].mimeType").value("application/pdf"))
            .andExpect(jsonPath("$.activity[0].date[0]").value(2026))
            .andExpect(jsonPath("$.activity[0].date[2]").value(5))
            .andExpect(jsonPath("$.topUsers[0].username").value("carol"))
            .andExpect(jsonPath("$.topUsers[0].activityCount").value(8));

        Mockito.verify(analyticsService).getSystemSummary();
        Mockito.verify(analyticsService).getStorageByMimeType();
        Mockito.verify(analyticsService).getDailyActivity(14);
        Mockito.verify(analyticsService).getTopUsers(5);
    }

    @Test
    @DisplayName("Async governance overview aggregates cross-center summaries")
    void asyncGovernanceOverviewAggregatesCrossCenterSummaries() throws Exception {
        Mockito.when(asyncTaskGovernanceService.buildOverview())
            .thenReturn(new AsyncTaskGovernanceOverviewSnapshot(
                LocalDateTime.of(2026, 3, 21, 12, 0),
                AsyncTaskGovernanceStatus.HEALTHY,
                AsyncTaskGovernanceRiskLevel.HIGH,
                5,
                0,
                AsyncTaskSummarySnapshot.ofBreakdown(4, 3, 8, 3, 5, 1, 1),
                List.of(
                    new AsyncTaskGovernanceDomainSnapshot(
                        "audit",
                        "Audit",
                        AsyncTaskGovernanceStatus.HEALTHY,
                        AsyncTaskGovernanceRiskLevel.LOW,
                        null,
                        AsyncTaskSummarySnapshot.ofBreakdown(0, 0, 4, 0, 1, 0, 0)
                    ),
                    new AsyncTaskGovernanceDomainSnapshot(
                        "ops",
                        "Ops Recovery",
                        AsyncTaskGovernanceStatus.HEALTHY,
                        AsyncTaskGovernanceRiskLevel.HIGH,
                        null,
                        AsyncTaskSummarySnapshot.ofBreakdown(2, 1, 2, 1, 1, 0, 1)
                    ),
                    new AsyncTaskGovernanceDomainSnapshot(
                        "search",
                        "Search",
                        AsyncTaskGovernanceStatus.HEALTHY,
                        AsyncTaskGovernanceRiskLevel.MEDIUM,
                        null,
                        AsyncTaskSummarySnapshot.ofBreakdown(1, 0, 1, 1, 1, 0, 0)
                    ),
                    new AsyncTaskGovernanceDomainSnapshot(
                        "preview",
                        "Preview",
                        AsyncTaskGovernanceStatus.HEALTHY,
                        AsyncTaskGovernanceRiskLevel.HIGH,
                        null,
                        AsyncTaskSummarySnapshot.ofBreakdown(0, 1, 1, 0, 1, 1, 0)
                    ),
                    new AsyncTaskGovernanceDomainSnapshot(
                        "batchDownload",
                        "Batch Download",
                        AsyncTaskGovernanceStatus.HEALTHY,
                        AsyncTaskGovernanceRiskLevel.MEDIUM,
                        null,
                        AsyncTaskSummarySnapshot.ofBreakdown(1, 1, 0, 1, 1, 0, 0)
                    )
                )
            ));

        mockMvc.perform(get("/api/v1/analytics/async-governance/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.generatedAt[0]").value(2026))
            .andExpect(jsonPath("$.generatedAt[1]").value(3))
            .andExpect(jsonPath("$.generatedAt[2]").value(21))
            .andExpect(jsonPath("$.overallStatus").value("HEALTHY"))
            .andExpect(jsonPath("$.overallRiskLevel").value("HIGH"))
            .andExpect(jsonPath("$.totalDomains").value(5))
            .andExpect(jsonPath("$.degradedDomainCount").value(0))
            .andExpect(jsonPath("$.totalCount").value(25))
            .andExpect(jsonPath("$.activeCount").value(7))
            .andExpect(jsonPath("$.terminalCount").value(18))
            .andExpect(jsonPath("$.queuedCount").value(4))
            .andExpect(jsonPath("$.runningCount").value(3))
            .andExpect(jsonPath("$.completedCount").value(8))
            .andExpect(jsonPath("$.cancelledCount").value(3))
            .andExpect(jsonPath("$.failedCount").value(5))
            .andExpect(jsonPath("$.timedOutCount").value(1))
            .andExpect(jsonPath("$.expiredCount").value(1))
            .andExpect(jsonPath("$.domains.length()").value(5))
            .andExpect(jsonPath("$.domains[?(@.key=='ops')].riskLevel", Matchers.contains("HIGH")))
            .andExpect(jsonPath("$.domains[?(@.key=='preview')].riskLevel", Matchers.contains("HIGH")))
            .andExpect(jsonPath("$.domains[?(@.key=='search')].riskLevel", Matchers.contains("MEDIUM")))
            .andExpect(jsonPath("$.domains[?(@.key=='audit')].riskLevel", Matchers.contains("LOW")))
            .andExpect(jsonPath("$.domains[?(@.key=='batchDownload')].riskLevel", Matchers.contains("MEDIUM")));

        Mockito.verify(asyncTaskGovernanceService).buildOverview();
    }

    @Test
    @DisplayName("Async governance overview marks degraded domains and escalates overall risk")
    void asyncGovernanceOverviewMarksDegradedDomains() throws Exception {
        Mockito.when(asyncTaskGovernanceService.buildOverview())
            .thenReturn(new AsyncTaskGovernanceOverviewSnapshot(
                LocalDateTime.of(2026, 3, 21, 12, 30),
                AsyncTaskGovernanceStatus.DEGRADED,
                AsyncTaskGovernanceRiskLevel.CRITICAL,
                5,
                2,
                AsyncTaskSummarySnapshot.ofBreakdown(0, 0, 0, 0, 0, 0, 0),
                List.of(
                    new AsyncTaskGovernanceDomainSnapshot(
                        "audit",
                        "Audit",
                        AsyncTaskGovernanceStatus.HEALTHY,
                        AsyncTaskGovernanceRiskLevel.LOW,
                        null,
                        AsyncTaskSummarySnapshot.ofBreakdown(0, 0, 0, 0, 0, 0, 0)
                    ),
                    new AsyncTaskGovernanceDomainSnapshot(
                        "ops",
                        "Ops Recovery",
                        AsyncTaskGovernanceStatus.HEALTHY,
                        AsyncTaskGovernanceRiskLevel.LOW,
                        null,
                        AsyncTaskSummarySnapshot.ofBreakdown(0, 0, 0, 0, 0, 0, 0)
                    ),
                    new AsyncTaskGovernanceDomainSnapshot(
                        "search",
                        "Search",
                        AsyncTaskGovernanceStatus.DEGRADED,
                        AsyncTaskGovernanceRiskLevel.CRITICAL,
                        "search summary unavailable",
                        AsyncTaskSummarySnapshot.ofBreakdown(0, 0, 0, 0, 0, 0, 0)
                    ),
                    new AsyncTaskGovernanceDomainSnapshot(
                        "preview",
                        "Preview",
                        AsyncTaskGovernanceStatus.HEALTHY,
                        AsyncTaskGovernanceRiskLevel.LOW,
                        null,
                        AsyncTaskSummarySnapshot.ofBreakdown(0, 0, 0, 0, 0, 0, 0)
                    ),
                    new AsyncTaskGovernanceDomainSnapshot(
                        "batchDownload",
                        "Batch Download",
                        AsyncTaskGovernanceStatus.DEGRADED,
                        AsyncTaskGovernanceRiskLevel.CRITICAL,
                        "batch download summary unavailable",
                        AsyncTaskSummarySnapshot.ofBreakdown(0, 0, 0, 0, 0, 0, 0)
                    )
                )
            ));

        mockMvc.perform(get("/api/v1/analytics/async-governance/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overallStatus").value("DEGRADED"))
            .andExpect(jsonPath("$.overallRiskLevel").value("CRITICAL"))
            .andExpect(jsonPath("$.degradedDomainCount").value(2))
            .andExpect(jsonPath("$.domains[?(@.key=='search')].status", Matchers.contains("DEGRADED")))
            .andExpect(jsonPath("$.domains[?(@.key=='search')].riskLevel", Matchers.contains("CRITICAL")))
            .andExpect(jsonPath("$.domains[?(@.key=='search')].error", Matchers.contains("search summary unavailable")))
            .andExpect(jsonPath("$.domains[?(@.key=='batchDownload')].status", Matchers.contains("DEGRADED")))
            .andExpect(jsonPath("$.domains[?(@.key=='batchDownload')].riskLevel", Matchers.contains("CRITICAL")))
            .andExpect(jsonPath("$.domains[?(@.key=='batchDownload')].error", Matchers.contains("batch download summary unavailable")));

        Mockito.verify(asyncTaskGovernanceService).buildOverview();
    }

    @Test
    @DisplayName("Async lifecycle task list exposes shared action affordances")
    void asyncLifecycleTaskListExposesSharedActionAffordances() throws Exception {
        Mockito.when(asyncTaskLifecycleService.listRecentTasks(10, 5, "preview", "completed", false))
            .thenReturn(new AsyncTaskLifecycleListSnapshot(
                java.time.Instant.parse("2026-03-21T13:45:00Z"),
                "preview",
                "completed",
                1,
                3,
                5,
                10,
                false,
                List.of(
                    new AsyncTaskStatusSnapshot(
                        "preview",
                        "Preview",
                        "task-123",
                        "COMPLETED",
                        null,
                        java.time.Instant.parse("2026-03-21T13:00:00Z"),
                        java.time.Instant.parse("2026-03-21T13:01:00Z"),
                        java.time.Instant.parse("2026-03-21T13:05:00Z"),
                        java.time.Instant.parse("2026-03-21T13:10:00Z"),
                        java.time.Instant.parse("2026-03-22T13:10:00Z"),
                        java.time.Instant.parse("2026-03-21T13:04:00Z"),
                        "preview_rendition_resources.csv",
                        "admin",
                        "admin",
                        new AsyncTaskActionSnapshot(
                            null,
                            "/api/v1/preview/diagnostics/renditions/resources/export-async/task-123/download",
                            "/api/v1/preview/diagnostics/renditions/resources/export-async/cleanup",
                            false,
                            true,
                            true
                        )
                    ).withAcknowledgement("preview|task-123|COMPLETED|2026-03-21T13:05:00Z", false, null)
                )
            ));

        mockMvc.perform(get("/api/v1/analytics/async-governance/tasks")
                .param("maxItems", "10")
                .param("skipCount", "5")
                .param("domain", "preview")
                .param("status", "completed"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.generatedAt").value("2026-03-21T13:45:00Z"))
            .andExpect(jsonPath("$.domainFilter").value("preview"))
            .andExpect(jsonPath("$.statusFilter").value("completed"))
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.totalCount").value(3))
            .andExpect(jsonPath("$.paging.skipCount").value(5))
            .andExpect(jsonPath("$.paging.maxItems").value(10))
            .andExpect(jsonPath("$.paging.totalItems").value(3))
            .andExpect(jsonPath("$.paging.hasMoreItems").value(false))
            .andExpect(jsonPath("$.items[0].domainKey").value("preview"))
            .andExpect(jsonPath("$.items[0].taskId").value("task-123"))
            .andExpect(jsonPath("$.items[0].fingerprint").value("preview|task-123|COMPLETED|2026-03-21T13:05:00Z"))
            .andExpect(jsonPath("$.items[0].acknowledged").value(false))
            .andExpect(jsonPath("$.items[0].downloadUrl").value("/api/v1/preview/diagnostics/renditions/resources/export-async/task-123/download"))
            .andExpect(jsonPath("$.items[0].cleanupUrl").value("/api/v1/preview/diagnostics/renditions/resources/export-async/cleanup"))
            .andExpect(jsonPath("$.items[0].cancellable").value(false))
            .andExpect(jsonPath("$.items[0].cleanupEligible").value(true))
            .andExpect(jsonPath("$.items[0].downloadReady").value(true));

        Mockito.verify(asyncTaskLifecycleService).listRecentTasks(10, 5, "preview", "completed", false);
    }

    @Test
    @DisplayName("Async lifecycle task list exposes property encryption aliases and cancel URL")
    void asyncLifecycleTaskListExposesPropertyEncryptionAliasAndCancelUrl() throws Exception {
        String backfillJobId = "00000000-0000-0000-0000-000000000010";
        Mockito.when(asyncTaskLifecycleService.listRecentTasks(20, 0, "property-encryption", "running", true))
            .thenReturn(new AsyncTaskLifecycleListSnapshot(
                java.time.Instant.parse("2026-05-12T15:30:00Z"),
                "propertyencryption",
                "running",
                1,
                1,
                0,
                20,
                false,
                List.of(
                    new AsyncTaskStatusSnapshot(
                        "propertyEncryption",
                        "Property Encryption",
                        "backfill:" + backfillJobId,
                        "RUNNING",
                        null,
                        java.time.Instant.parse("2026-05-12T15:00:00Z"),
                        java.time.Instant.parse("2026-05-12T15:01:00Z"),
                        java.time.Instant.parse("2026-05-12T15:05:00Z"),
                        null,
                        null,
                        null,
                        null,
                        "admin",
                        null,
                        new AsyncTaskActionSnapshot(
                            "/api/v1/admin/property-encryption/backfill-jobs/" + backfillJobId + "/cancel",
                            null,
                            null,
                            true,
                            false,
                            false
                        )
                    ).withAcknowledgement(
                        "propertyEncryption|backfill:" + backfillJobId + "|RUNNING|2026-05-12T15:05:00Z",
                        false,
                        null
                    )
                )
            ));

        mockMvc.perform(get("/api/v1/analytics/async-governance/tasks")
                .param("maxItems", "20")
                .param("skipCount", "0")
                .param("domain", "property-encryption")
                .param("status", "running")
                .param("includeAcknowledged", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.generatedAt").value("2026-05-12T15:30:00Z"))
            .andExpect(jsonPath("$.domainFilter").value("propertyencryption"))
            .andExpect(jsonPath("$.statusFilter").value("running"))
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.totalCount").value(1))
            .andExpect(jsonPath("$.paging.skipCount").value(0))
            .andExpect(jsonPath("$.paging.maxItems").value(20))
            .andExpect(jsonPath("$.items[0].domainKey").value("propertyEncryption"))
            .andExpect(jsonPath("$.items[0].domainLabel").value("Property Encryption"))
            .andExpect(jsonPath("$.items[0].taskId").value("backfill:" + backfillJobId))
            .andExpect(jsonPath("$.items[0].status").value("RUNNING"))
            .andExpect(jsonPath("$.items[0].createdBy").value("admin"))
            .andExpect(jsonPath("$.items[0].fingerprint").value("propertyEncryption|backfill:" + backfillJobId + "|RUNNING|2026-05-12T15:05:00Z"))
            .andExpect(jsonPath("$.items[0].cancelUrl").value("/api/v1/admin/property-encryption/backfill-jobs/" + backfillJobId + "/cancel"))
            .andExpect(jsonPath("$.items[0].downloadUrl").doesNotExist())
            .andExpect(jsonPath("$.items[0].cleanupUrl").doesNotExist())
            .andExpect(jsonPath("$.items[0].cancellable").value(true))
            .andExpect(jsonPath("$.items[0].cleanupEligible").value(false))
            .andExpect(jsonPath("$.items[0].downloadReady").value(false))
            .andExpect(jsonPath("$.items[0].acknowledged").value(false));

        Mockito.verify(asyncTaskLifecycleService).listRecentTasks(20, 0, "property-encryption", "running", true);
    }

    @Test
    @DisplayName("Async lifecycle task acknowledge persists operator acknowledgement")
    void acknowledgeAsyncLifecycleTaskPersistsAcknowledgement() throws Exception {
        AsyncTaskStatusSnapshot task = new AsyncTaskStatusSnapshot(
            "preview",
            "Preview",
            "task-ack",
            "FAILED",
            "boom",
            java.time.Instant.parse("2026-03-21T13:00:00Z"),
            java.time.Instant.parse("2026-03-21T13:01:00Z"),
            java.time.Instant.parse("2026-03-21T13:05:00Z"),
            null,
            null,
            java.time.Instant.parse("2026-03-21T13:05:00Z"),
            "preview_failure.csv",
            "admin",
            "admin",
            new AsyncTaskActionSnapshot(
                null,
                null,
                "/api/v1/preview/diagnostics/renditions/resources/export-async/cleanup",
                false,
                true,
                false
            )
        ).withAcknowledgement("preview|task-ack|FAILED|2026-03-21T13:05:00Z", false, null);

        AsyncTaskAcknowledgement acknowledgement = AsyncTaskAcknowledgement.builder()
            .userId("admin")
            .domainKey("preview")
            .taskId("task-ack")
            .taskStatus("FAILED")
            .taskFingerprint("preview|task-ack|FAILED|2026-03-21T13:05:00Z")
            .acknowledgedAt(java.time.LocalDateTime.of(2026, 3, 21, 13, 7))
            .build();

        Mockito.when(asyncTaskLifecycleService.findRecentTask("preview", "task-ack", "preview|task-ack|FAILED|2026-03-21T13:05:00Z"))
            .thenReturn(task);
        Mockito.when(asyncTaskAcknowledgementService.acknowledge(task))
            .thenReturn(acknowledgement);
        Mockito.when(asyncTaskAcknowledgementService.applyAcknowledgement(task, acknowledgement))
            .thenReturn(task.withAcknowledgement(
                "preview|task-ack|FAILED|2026-03-21T13:05:00Z",
                true,
                java.time.Instant.parse("2026-03-21T13:07:00Z")
            ));

        mockMvc.perform(post("/api/v1/analytics/async-governance/tasks/acknowledge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "domainKey": "preview",
                      "taskId": "task-ack",
                      "fingerprint": "preview|task-ack|FAILED|2026-03-21T13:05:00Z"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domainKey").value("preview"))
            .andExpect(jsonPath("$.taskId").value("task-ack"))
            .andExpect(jsonPath("$.fingerprint").value("preview|task-ack|FAILED|2026-03-21T13:05:00Z"))
            .andExpect(jsonPath("$.acknowledged").value(true))
            .andExpect(jsonPath("$.acknowledgedAt").value("2026-03-21T13:07:00Z"))
            .andExpect(jsonPath("$.changed").value(true));

        Mockito.verify(asyncTaskLifecycleService).findRecentTask("preview", "task-ack", "preview|task-ack|FAILED|2026-03-21T13:05:00Z");
        Mockito.verify(asyncTaskAcknowledgementService).acknowledge(task);
    }

    @Test
    @DisplayName("Async lifecycle task unacknowledge restores acknowledged entry")
    void unacknowledgeAsyncLifecycleTaskRestoresAcknowledgement() throws Exception {
        Mockito.when(asyncTaskAcknowledgementService.unacknowledge("preview|task-ack|FAILED|2026-03-21T13:05:00Z"))
            .thenReturn(true);

        mockMvc.perform(post("/api/v1/analytics/async-governance/tasks/unacknowledge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "fingerprint": "preview|task-ack|FAILED|2026-03-21T13:05:00Z"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fingerprint").value("preview|task-ack|FAILED|2026-03-21T13:05:00Z"))
            .andExpect(jsonPath("$.acknowledged").value(false))
            .andExpect(jsonPath("$.acknowledgedAt").doesNotExist())
            .andExpect(jsonPath("$.changed").value(true));

        Mockito.verify(asyncTaskAcknowledgementService).unacknowledge("preview|task-ack|FAILED|2026-03-21T13:05:00Z");
    }

    private String awaitAuditExportAsyncTaskTerminalStatus(String taskId) throws Exception {
        for (int i = 0; i < 60; i++) {
            MvcResult statusResult = mockMvc.perform(get("/api/v1/analytics/audit/export-async/{taskId}", taskId))
                .andExpect(status().isOk())
                .andReturn();
            JsonNode statusPayload = objectMapper.readTree(statusResult.getResponse().getContentAsString());
            String statusValue = statusPayload.path("status").asText();
            if ("COMPLETED".equals(statusValue) || "FAILED".equals(statusValue) || "CANCELLED".equals(statusValue)) {
                return statusValue;
            }
            Thread.sleep(20L);
        }
        fail("async export task did not reach terminal state in time");
        return "";
    }

    private void awaitAuditExportAsyncActiveCountAtLeast(long minActiveCount) throws Exception {
        for (int i = 0; i < 60; i++) {
            MvcResult summaryResult = mockMvc.perform(get("/api/v1/analytics/audit/export-async/summary"))
                .andExpect(status().isOk())
                .andReturn();
            JsonNode payload = objectMapper.readTree(summaryResult.getResponse().getContentAsString());
            if (payload.path("activeCount").asLong() >= minActiveCount) {
                return;
            }
            Thread.sleep(20L);
        }
        fail("async export activeCount did not reach expected value in time");
    }

    private String startAuditExportAsyncTask() throws Exception {
        MvcResult startResult = mockMvc.perform(post("/api/v1/analytics/audit/export-async")
                .param("from", "2026-01-05T00:00:00")
                .param("to", "2026-01-06T00:00:00"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").isNotEmpty())
            .andReturn();
        return objectMapper.readTree(startResult.getResponse().getContentAsString())
            .path("taskId")
            .asText();
    }
}
