package com.ecm.core.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.entity.AuditLog;
import com.ecm.core.preview.PreviewDeadLetterRegistry;
import com.ecm.core.preview.PreviewQueueService;
import com.ecm.core.repository.AuditLogRepository;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.RenditionResourceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OpsRecoveryController.class)
@ContextConfiguration(classes = {
    OpsRecoveryController.class,
    OpsRecoveryControllerSecurityTest.TestSecurityConfig.class
})
class OpsRecoveryControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private PreviewQueueService previewQueueService;

    @MockBean
    private PreviewDeadLetterRegistry previewDeadLetterRegistry;

    @MockBean
    private AuditLogRepository auditLogRepository;

    @MockBean
    private AuditService auditService;

    @MockBean
    private RenditionResourceService renditionResourceService;

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll()
                )
                .httpBasic(basic -> {});
            return http.build();
        }
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Ops recovery endpoints require admin role")
    void requiresAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/ops/recovery/queue-by-reason")
                .contentType("application/json")
                .content("{\"reason\":\"gateway timeout\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ops/recovery/queue-by-window")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ops/recovery/replay-batch")
                .contentType("application/json")
                .content("{\"documentIds\":[]}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ops/recovery/clear-batch")
                .contentType("application/json")
                .content("{\"entryKeys\":[]}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ops/recovery/clear-by-filter")
                .contentType("application/json")
                .content("{\"reason\":\"timeout\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ops/recovery/replay-by-filter")
                .contentType("application/json")
                .content("{\"reason\":\"timeout\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ops/recovery/dry-run")
                .contentType("application/json")
                .content("{\"mode\":\"QUEUE_BY_WINDOW\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ops/recovery/dry-run/export")
                .contentType("application/json")
                .content("{\"mode\":\"QUEUE_BY_WINDOW\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ops/recovery/history"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary/compare"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary/compare/breakdown"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary/compare/actors"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary/compare/actors/export"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary/compare/breakdown/export"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary/compare/export"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary/trend"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary/trend/export"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary/export"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ops/recovery/history/export"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ops/recovery/history/export-async")
                .contentType("application/json")
                .content("{\"exportType\":\"HISTORY\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ops/recovery/history/export-async"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ops/recovery/history/export-async/non-existent-task"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ops/recovery/history/export-async/non-existent-task/cancel"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ops/recovery/history/export-async/non-existent-task/retry"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ops/recovery/history/export-async/non-existent-task/download"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ops/recovery/history/export-async/summary"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ops/recovery/history/export-async/cleanup"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ops/recovery/history/export-async/cancel-active"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ops/recovery/history/export-async/retry-terminal")
                .param("status", "FAILED")
                .param("limit", "20"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ops/recovery/history/export-async/retry-terminal/by-task-ids")
                .contentType("application/json")
                .content("{\"sourceTaskIds\":[\"task-1\"]}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can start/list ops recovery async export tasks")
    void historyExportAsyncTaskEndpointsForAdmin() throws Exception {
        AuditLog logEntry = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_QUEUE_BY_REASON")
            .username("admin")
            .eventTime(LocalDateTime.of(2026, 3, 7, 1, 0, 0))
            .details("domain=PREVIEW mode=QUEUE_BY_REASON queued=1 skipped=0 failed=0")
            .build();

        Mockito.when(auditLogRepository.findByEventTypePrefixSince(eq("OPS_RECOVERY_"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(logEntry), PageRequest.of(0, 5), 1));

        mockMvc.perform(post("/api/v1/ops/recovery/history/export-async")
                .contentType("application/json")
                .content("""
                    {
                      "exportType": "HISTORY",
                      "limit": 5,
                      "days": 7
                    }
                    """))
            .andExpect(status().isAccepted())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.taskId").isNotEmpty())
            .andExpect(jsonPath("$.exportType", is("HISTORY")))
            .andExpect(jsonPath("$.status").isNotEmpty())
            .andExpect(jsonPath("$.request.exportType", is("HISTORY")))
            .andExpect(jsonPath("$.request.limit", is(5)))
            .andExpect(jsonPath("$.request.days", is(7)));

        mockMvc.perform(get("/api/v1/ops/recovery/history/export-async")
                .param("maxItems", "20")
                .param("skipCount", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$.paging.skipCount", is(0)))
            .andExpect(jsonPath("$.paging.maxItems", is(20)))
            .andExpect(jsonPath("$.paging.totalItems", org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.paging.hasMoreItems", org.hamcrest.Matchers.notNullValue()))
            .andExpect(jsonPath("$.items[0].request.exportType").isNotEmpty())
            .andExpect(jsonPath("$.items[0].request.limit").isNumber())
            .andExpect(jsonPath("$.items[0].request.days").isNumber());

        mockMvc.perform(get("/api/v1/ops/recovery/history/export-async")
                .param("limit", "20")
                .param("exportType", "HISTORY"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.items[*].exportType").value(org.hamcrest.Matchers.everyItem(is("HISTORY"))));

        mockMvc.perform(get("/api/v1/ops/recovery/history/export-async")
                .param("limit", "20")
                .param("status", "completed"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/ops/recovery/history/export-async")
                .param("limit", "20")
                .param("status", "invalid"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/ops/recovery/history/export-async/non-existent-task"))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/ops/recovery/history/export-async/non-existent-task/cancel"))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/ops/recovery/history/export-async/non-existent-task/download"))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can summarize and cleanup ops recovery async export tasks")
    void historyExportAsyncSummaryAndCleanupForAdmin() throws Exception {
        AuditLog logEntry = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_QUEUE_BY_WINDOW")
            .username("admin")
            .eventTime(LocalDateTime.of(2026, 3, 8, 2, 0, 0))
            .details("domain=PREVIEW mode=QUEUE_BY_WINDOW queued=1 skipped=0 failed=0")
            .build();

        Mockito.when(auditLogRepository.findByEventTypePrefixSince(eq("OPS_RECOVERY_"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(logEntry), PageRequest.of(0, 5), 1));

        mockMvc.perform(post("/api/v1/ops/recovery/history/export-async")
                .contentType("application/json")
                .content("""
                    {
                      "exportType": "HISTORY",
                      "limit": 5,
                      "days": 7
                    }
                    """))
            .andExpect(status().isAccepted())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.taskId").isNotEmpty())
            .andExpect(jsonPath("$.exportType", is("HISTORY")));

        mockMvc.perform(get("/api/v1/ops/recovery/history/export-async/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.queuedCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)))
            .andExpect(jsonPath("$.runningCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)))
            .andExpect(jsonPath("$.completedCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)))
            .andExpect(jsonPath("$.cancelledCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)))
            .andExpect(jsonPath("$.failedCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)))
            .andExpect(jsonPath("$.activeCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)))
            .andExpect(jsonPath("$.terminalCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)));

        mockMvc.perform(get("/api/v1/ops/recovery/history/export-async/summary")
                .param("status", "completed"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/ops/recovery/history/export-async/summary")
                .param("status", "invalid"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/ops/recovery/history/export-async/summary")
                .param("exportType", "HISTORY_SUMMARY"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ops/recovery/history/export-async/cleanup"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletedCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)))
            .andExpect(jsonPath("$.remainingCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)))
            .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(post("/api/v1/ops/recovery/history/export-async/cleanup")
                .param("status", "running"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("QUEUED or RUNNING")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can cancel active ops recovery async export tasks")
    void historyExportAsyncCancelActiveForAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/ops/recovery/history/export-async/cancel-active"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cancelledCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)))
            .andExpect(jsonPath("$.remainingActiveCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)))
            .andExpect(jsonPath("$.statusFilter", is("ACTIVE")));

        mockMvc.perform(post("/api/v1/ops/recovery/history/export-async/cancel-active")
                .param("status", "completed"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("QUEUED or RUNNING")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Ops recovery async start deduplicates to active task with same snapshot")
    void historyExportAsyncStartDeduplicatesToActiveTask() throws Exception {
        AuditLog logEntry = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_QUEUE_BY_REASON")
            .username("admin")
            .eventTime(LocalDateTime.of(2026, 3, 9, 1, 0, 0))
            .details("domain=PREVIEW mode=QUEUE_BY_REASON queued=1 skipped=0 failed=0")
            .build();

        CountDownLatch enteredRepository = new CountDownLatch(1);
        CountDownLatch releaseRepository = new CountDownLatch(1);
        Mockito.when(auditLogRepository.findByEventTypePrefixSince(eq("OPS_RECOVERY_"), any(), any()))
            .thenAnswer(invocation -> {
                enteredRepository.countDown();
                releaseRepository.await(3, TimeUnit.SECONDS);
                return new PageImpl<>(List.of(logEntry), PageRequest.of(0, 5), 1);
            });

        try {
            MvcResult firstStart = mockMvc.perform(post("/api/v1/ops/recovery/history/export-async")
                    .contentType("application/json")
                    .content("""
                        {
                          "exportType": "HISTORY",
                          "limit": 5,
                          "days": 7
                        }
                        """))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.taskId").isNotEmpty())
                .andExpect(jsonPath("$.deduplicated", is(false)))
                .andExpect(jsonPath("$.deduplicatedFromTaskId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.request.exportType", is("HISTORY")))
                .andExpect(jsonPath("$.request.limit", is(5)))
                .andExpect(jsonPath("$.request.days", is(7)))
                .andReturn();
            String runningTaskId = readTaskId(firstStart);
            assertFalse(runningTaskId.isBlank());
            assertTrue(enteredRepository.await(3, TimeUnit.SECONDS));

            mockMvc.perform(post("/api/v1/ops/recovery/history/export-async")
                    .contentType("application/json")
                    .content("""
                        {
                          "exportType": "HISTORY",
                          "limit": 5,
                          "days": 7
                        }
                        """))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.taskId", is(runningTaskId)))
                .andExpect(jsonPath("$.deduplicated", is(true)))
                .andExpect(jsonPath("$.deduplicatedFromTaskId", is(runningTaskId)))
                .andExpect(jsonPath("$.request.exportType", is("HISTORY")))
                .andExpect(jsonPath("$.request.limit", is(5)))
                .andExpect(jsonPath("$.request.days", is(7)))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Reused active async export task")));
        } finally {
            releaseRepository.countDown();
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can retry terminal ops recovery async export tasks")
    void historyExportAsyncRetryForAdmin() throws Exception {
        AuditLog logEntry = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_QUEUE_BY_REASON")
            .username("admin")
            .eventTime(LocalDateTime.of(2026, 3, 9, 1, 0, 0))
            .details("domain=PREVIEW mode=QUEUE_BY_REASON queued=1 skipped=0 failed=0")
            .build();

        Mockito.when(auditLogRepository.findByEventTypePrefixSince(eq("OPS_RECOVERY_"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(logEntry), PageRequest.of(0, 5), 1));

        String completedTaskId = startHistoryExportAsyncTask();
        assertEquals("COMPLETED", awaitHistoryExportAsyncTaskTerminalStatus(completedTaskId));

        MvcResult retryResult = mockMvc.perform(post("/api/v1/ops/recovery/history/export-async/{taskId}/retry", completedTaskId))
            .andExpect(status().isAccepted())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.taskId").isNotEmpty())
            .andExpect(jsonPath("$.exportType", is("HISTORY")))
            .andExpect(jsonPath("$.status").isNotEmpty())
            .andExpect(jsonPath("$.deduplicated", is(false)))
            .andExpect(jsonPath("$.deduplicatedFromTaskId").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.request.exportType", is("HISTORY")))
            .andExpect(jsonPath("$.request.limit", is(5)))
            .andExpect(jsonPath("$.request.days", is(7)))
            .andReturn();

        String retryTaskId = readTaskId(retryResult);
        assertFalse(retryTaskId.isBlank());
        assertFalse(completedTaskId.equals(retryTaskId));
        assertEquals("COMPLETED", awaitHistoryExportAsyncTaskTerminalStatus(retryTaskId));

        mockMvc.perform(post("/api/v1/ops/recovery/history/export-async/non-existent-task/retry"))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can bulk retry terminal ops recovery async export tasks")
    void historyExportAsyncRetryTerminalBulkForAdmin() throws Exception {
        AuditLog logEntry = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_QUEUE_BY_REASON")
            .username("admin")
            .eventTime(LocalDateTime.of(2026, 3, 9, 1, 0, 0))
            .details("domain=PREVIEW mode=QUEUE_BY_REASON queued=1 skipped=0 failed=0")
            .build();

        Mockito.when(auditLogRepository.findByEventTypePrefixSince(eq("OPS_RECOVERY_"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(logEntry), PageRequest.of(0, 5), 1));

        String completedTaskId = startHistoryExportAsyncTask();
        assertEquals("COMPLETED", awaitHistoryExportAsyncTaskTerminalStatus(completedTaskId));

        mockMvc.perform(post("/api/v1/ops/recovery/history/export-async/retry-terminal")
                .param("exportType", "HISTORY")
                .param("limit", "20"))
            .andExpect(status().isAccepted())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.requested", org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.retried", org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.results", hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(1))));

        mockMvc.perform(post("/api/v1/ops/recovery/history/export-async/retry-terminal/by-task-ids")
                .param("exportType", "HISTORY")
                .contentType("application/json")
                .content("{\"sourceTaskIds\":[\"" + completedTaskId + "\"]}"))
            .andExpect(status().isAccepted())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.requested", is(1)))
            .andExpect(jsonPath("$.retried", org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].sourceTaskId", is(completedTaskId)))
            .andExpect(jsonPath("$.results[0].outcome", org.hamcrest.Matchers.anyOf(is("RETRIED"), is("REUSED"))));

        mockMvc.perform(post("/api/v1/ops/recovery/history/export-async/retry-terminal/dry-run")
                .param("exportType", "HISTORY")
                .param("status", "COMPLETED")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limit", is(20)))
            .andExpect(jsonPath("$.exportTypeFilter", is("HISTORY")))
            .andExpect(jsonPath("$.statusFilter", is("COMPLETED")))
            .andExpect(jsonPath("$.requested").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.retryable").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)))
            .andExpect(jsonPath("$.results", org.hamcrest.Matchers.notNullValue()));

        mockMvc.perform(get("/api/v1/ops/recovery/history/export-async/retry-terminal/dry-run/export")
                .param("exportType", "HISTORY")
                .param("status", "COMPLETED")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Ops-Recovery-Async-Retry-Dry-Run-Count"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("text/csv;charset=UTF-8"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                org.hamcrest.Matchers.containsString("sourceTaskId,exportType,sourceStatus,outcome,reasonCode,message")));

        mockMvc.perform(post("/api/v1/ops/recovery/history/export-async/retry-terminal")
                .param("status", "RUNNING"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("terminal states")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Ops recovery async retry deduplicates to active task with same snapshot")
    void historyExportAsyncRetryDeduplicatesToActiveTask() throws Exception {
        AuditLog logEntry = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_QUEUE_BY_REASON")
            .username("admin")
            .eventTime(LocalDateTime.of(2026, 3, 9, 1, 0, 0))
            .details("domain=PREVIEW mode=QUEUE_BY_REASON queued=1 skipped=0 failed=0")
            .build();

        Mockito.when(auditLogRepository.findByEventTypePrefixSince(eq("OPS_RECOVERY_"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(logEntry), PageRequest.of(0, 5), 1));

        String completedTaskId = startHistoryExportAsyncTask();
        assertEquals("COMPLETED", awaitHistoryExportAsyncTaskTerminalStatus(completedTaskId));

        CountDownLatch enteredRepository = new CountDownLatch(1);
        CountDownLatch releaseRepository = new CountDownLatch(1);
        Mockito.when(auditLogRepository.findByEventTypePrefixSince(eq("OPS_RECOVERY_"), any(), any()))
            .thenAnswer(invocation -> {
                enteredRepository.countDown();
                releaseRepository.await(3, TimeUnit.SECONDS);
                return new PageImpl<>(List.of(logEntry), PageRequest.of(0, 5), 1);
            });

        String runningTaskId;
        try {
            runningTaskId = startHistoryExportAsyncTask();
            assertTrue(enteredRepository.await(3, TimeUnit.SECONDS));

            mockMvc.perform(post("/api/v1/ops/recovery/history/export-async/{taskId}/retry", completedTaskId))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.taskId", is(runningTaskId)))
                .andExpect(jsonPath("$.deduplicated", is(true)))
                .andExpect(jsonPath("$.deduplicatedFromTaskId", is(runningTaskId)))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Reused active async export task")));
        } finally {
            releaseRepository.countDown();
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Retry rejects non-terminal ops recovery async export task")
    void historyExportAsyncRetryRejectsNonTerminalTask() throws Exception {
        AuditLog logEntry = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_QUEUE_BY_REASON")
            .username("admin")
            .eventTime(LocalDateTime.of(2026, 3, 10, 1, 0, 0))
            .details("domain=PREVIEW mode=QUEUE_BY_REASON queued=1 skipped=0 failed=0")
            .build();
        CountDownLatch enteredRepository = new CountDownLatch(1);
        CountDownLatch releaseRepository = new CountDownLatch(1);

        Mockito.when(auditLogRepository.findByEventTypePrefixSince(eq("OPS_RECOVERY_"), any(), any()))
            .thenAnswer(invocation -> {
                enteredRepository.countDown();
                releaseRepository.await(3, TimeUnit.SECONDS);
                return new PageImpl<>(List.of(logEntry), PageRequest.of(0, 5), 1);
            });

        String runningTaskId;
        try {
            runningTaskId = startHistoryExportAsyncTask();
            assertTrue(enteredRepository.await(3, TimeUnit.SECONDS));

            mockMvc.perform(post("/api/v1/ops/recovery/history/export-async/{taskId}/retry", runningTaskId))
                .andExpect(status().isConflict());
        } finally {
            releaseRepository.countDown();
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can queue by reason and receive unified recovery payload")
    void queueByReasonForAdmin() throws Exception {
        UUID docId = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(docId);
        doc.setName("timeout.pdf");
        doc.setPath("/Root/Documents/timeout.pdf");
        doc.setMimeType("application/pdf");
        doc.setPreviewStatus(PreviewStatus.FAILED);
        doc.setPreviewFailureReason("gateway timeout");
        doc.setPreviewLastUpdated(LocalDateTime.now());

        Mockito.when(documentRepository.countPreviewFailuresByReasonAndWindow(anyList(), any(), eq("gateway timeout")))
            .thenReturn(1L);
        Mockito.when(documentRepository.findPreviewFailuresByReasonAndWindow(anyList(), any(), eq("gateway timeout"), any()))
            .thenReturn(new PageImpl<>(List.of(doc), PageRequest.of(0, 1), 1));
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(doc));
        Mockito.when(previewQueueService.enqueue(eq(docId), eq(false)))
            .thenReturn(new PreviewQueueService.PreviewQueueStatus(
                docId,
                PreviewStatus.FAILED,
                true,
                1,
                Instant.parse("2026-03-06T00:00:00Z"),
                "Preview queued"
            ));

        mockMvc.perform(post("/api/v1/ops/recovery/queue-by-reason")
                .contentType("application/json")
                .content("""
                    {
                      "domain": "PREVIEW",
                      "reason": "gateway timeout",
                      "category": "TEMPORARY",
                      "retryable": true,
                      "maxDocuments": 20,
                      "days": 7,
                      "force": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domain", is("PREVIEW")))
            .andExpect(jsonPath("$.mode", is("QUEUE_BY_REASON")))
            .andExpect(jsonPath("$.queued", is(1)))
            .andExpect(jsonPath("$.failed", is(0)))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].documentId", is(docId.toString())))
            .andExpect(jsonPath("$.results[0].jobState", is("QUEUED")))
            .andExpect(jsonPath("$.results[0].failureCategory", is("TEMPORARY")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin queue-by-window prefers rendition summary for unsupported failures in ops recovery")
    void queueByWindowPrefersRenditionSummary() throws Exception {
        UUID docId = UUID.randomUUID();
        LocalDateTime updated = LocalDateTime.of(2026, 3, 28, 18, 0, 0);

        Document doc = new Document();
        doc.setId(docId);
        doc.setName("unsupported.bin");
        doc.setPath("/Root/Documents/unsupported.bin");
        doc.setMimeType("application/octet-stream");
        doc.setPreviewStatus(PreviewStatus.FAILED);
        doc.setPreviewFailureReason("Preview generation failed");

        Mockito.when(documentRepository.countPreviewFailuresByWindow(anyList(), any()))
            .thenReturn(1L);
        Mockito.when(documentRepository.findRecentPreviewFailuresByWindow(anyList(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(doc), PageRequest.of(0, 1), 1));
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(doc));
        Mockito.when(renditionResourceService.summarizeDocument(doc)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                docId,
                true,
                "UNSUPPORTED",
                false,
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                updated,
                null
            )
        );
        Mockito.when(previewQueueService.enqueue(eq(docId), eq(false)))
            .thenReturn(new PreviewQueueService.PreviewQueueStatus(
                docId,
                null,
                true,
                0,
                null,
                "Preview queued"
            ));

        mockMvc.perform(post("/api/v1/ops/recovery/queue-by-window")
                .contentType("application/json")
                .content("""
                    {
                      "domain": "PREVIEW",
                      "category": "UNSUPPORTED",
                      "retryable": false,
                      "maxDocuments": 20,
                      "days": 7,
                      "force": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queued", is(1)))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].documentId", is(docId.toString())))
            .andExpect(jsonPath("$.results[0].previewStatus", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.results[0].failureCategory", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.results[0].previewFailureReason", is("Preview definition is not registered for generic binary sources")))
            .andExpect(jsonPath("$.results[0].previewFailureCategory", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.results[0].previewLastUpdated", is("2026-03-28T18:00:00")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Dry run estimates skipped documents for permanent failures without force")
    void dryRunPredictsSkippedForPermanentFailures() throws Exception {
        UUID docId = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(docId);
        doc.setName("broken.pdf");
        doc.setPath("/Root/Documents/broken.pdf");
        doc.setMimeType("application/pdf");
        doc.setPreviewStatus(PreviewStatus.FAILED);
        doc.setPreviewFailureReason("Missing root object specification in trailer.");

        Mockito.when(documentRepository.countPreviewFailuresByWindow(anyList(), any()))
            .thenReturn(1L);
        Mockito.when(documentRepository.findRecentPreviewFailuresByWindow(anyList(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(doc), PageRequest.of(0, 1), 1));

        mockMvc.perform(post("/api/v1/ops/recovery/dry-run")
                .contentType("application/json")
                .content("""
                    {
                      "domain": "PREVIEW",
                      "mode": "QUEUE_BY_WINDOW",
                      "days": 7,
                      "maxDocuments": 10,
                      "force": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode", is("QUEUE_BY_WINDOW")))
            .andExpect(jsonPath("$.matched", is(1)))
            .andExpect(jsonPath("$.estimatedQueued", is(0)))
            .andExpect(jsonPath("$.estimatedSkipped", is(1)))
            .andExpect(jsonPath("$.samples", hasSize(1)))
            .andExpect(jsonPath("$.samples[0].predictedState", is("SKIPPED")))
            .andExpect(jsonPath("$.samples[0].failureCategory", is("PERMANENT")));

        Mockito.verify(previewQueueService, Mockito.never()).enqueue(any(UUID.class), anyBoolean());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Dry run prefers rendition summary for unsupported failures in ops recovery")
    void dryRunPrefersRenditionSummaryForUnsupportedFailures() throws Exception {
        UUID docId = UUID.randomUUID();
        LocalDateTime updated = LocalDateTime.of(2026, 3, 28, 18, 30, 0);

        Document doc = new Document();
        doc.setId(docId);
        doc.setName("unsupported.bin");
        doc.setPath("/Root/Documents/unsupported.bin");
        doc.setMimeType("application/octet-stream");
        doc.setPreviewStatus(PreviewStatus.FAILED);
        doc.setPreviewFailureReason("Preview generation failed");

        Mockito.when(documentRepository.countPreviewFailuresByWindow(anyList(), any()))
            .thenReturn(1L);
        Mockito.when(documentRepository.findRecentPreviewFailuresByWindow(anyList(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(doc), PageRequest.of(0, 1), 1));
        Mockito.when(renditionResourceService.summarizeDocument(doc)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                docId,
                true,
                "UNSUPPORTED",
                false,
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                updated,
                null
            )
        );

        mockMvc.perform(post("/api/v1/ops/recovery/dry-run")
                .contentType("application/json")
                .content("""
                    {
                      "domain": "PREVIEW",
                      "mode": "QUEUE_BY_WINDOW",
                      "category": "UNSUPPORTED",
                      "retryable": false,
                      "days": 7,
                      "maxDocuments": 10,
                      "force": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matched", is(1)))
            .andExpect(jsonPath("$.estimatedQueued", is(0)))
            .andExpect(jsonPath("$.estimatedSkipped", is(1)))
            .andExpect(jsonPath("$.samples", hasSize(1)))
            .andExpect(jsonPath("$.samples[0].previewStatus", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.samples[0].failureCategory", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.samples[0].previewFailureReason", is("Preview definition is not registered for generic binary sources")))
            .andExpect(jsonPath("$.samples[0].previewFailureCategory", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.samples[0].previewLastUpdated", is("2026-03-28T18:30:00")))
            .andExpect(jsonPath("$.samples[0].predictedState", is("SKIPPED")));

        Mockito.verify(previewQueueService, Mockito.never()).enqueue(any(UUID.class), anyBoolean());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Dry run replay-by-filter prefers rendition summary for unsupported failures in ops recovery")
    void dryRunReplayByFilterPrefersRenditionSummary() throws Exception {
        UUID docId = UUID.randomUUID();
        String entryKey = PreviewDeadLetterRegistry.buildEntryKey(docId, "preview");
        LocalDateTime updated = LocalDateTime.of(2026, 3, 28, 19, 15, 0);

        Document doc = new Document();
        doc.setId(docId);
        doc.setName("unsupported.bin");
        doc.setPath("/Root/Documents/unsupported.bin");
        doc.setMimeType("application/octet-stream");
        doc.setPreviewStatus(PreviewStatus.FAILED);
        doc.setPreviewFailureReason("Preview generation failed");

        Mockito.when(previewDeadLetterRegistry.list(500)).thenReturn(List.of(
            new PreviewDeadLetterRegistry.DeadLetterEntry(
                entryKey,
                docId,
                "preview",
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                "default",
                "UNSUPPORTED",
                Instant.now(),
                1,
                1,
                null,
                0
            )
        ));
        Mockito.when(previewDeadLetterRegistry.getItemCount()).thenReturn(1);
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(doc));
        Mockito.when(renditionResourceService.summarizeDocument(doc)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                docId,
                true,
                "UNSUPPORTED",
                false,
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                updated,
                null
            )
        );

        mockMvc.perform(post("/api/v1/ops/recovery/dry-run")
                .contentType("application/json")
                .content("""
                    {
                      "domain": "PREVIEW",
                      "mode": "REPLAY_BY_FILTER",
                      "reason": "Preview definition is not registered for generic binary sources",
                      "category": "UNSUPPORTED",
                      "retryable": false,
                      "maxDocuments": 100,
                      "days": 7,
                      "force": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matched", is(1)))
            .andExpect(jsonPath("$.estimatedQueued", is(0)))
            .andExpect(jsonPath("$.estimatedSkipped", is(1)))
            .andExpect(jsonPath("$.samples", hasSize(1)))
            .andExpect(jsonPath("$.samples[0].documentId", is(docId.toString())))
            .andExpect(jsonPath("$.samples[0].previewStatus", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.samples[0].failureCategory", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.samples[0].previewFailureReason", is("Preview definition is not registered for generic binary sources")))
            .andExpect(jsonPath("$.samples[0].previewFailureCategory", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.samples[0].previewLastUpdated", is("2026-03-28T19:15:00")))
            .andExpect(jsonPath("$.samples[0].predictedState", is("SKIPPED")))
            .andExpect(jsonPath("$.samples[0].predictedOutcome", is("SKIPPED")));

        Mockito.verify(previewQueueService, Mockito.never()).enqueue(any(UUID.class), anyBoolean());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Dry run export includes effective preview summary fields in ops recovery")
    void exportDryRunPrefersRenditionSummary() throws Exception {
        UUID docId = UUID.randomUUID();
        LocalDateTime updated = LocalDateTime.of(2026, 3, 28, 20, 0, 0);

        Document doc = new Document();
        doc.setId(docId);
        doc.setName("unsupported.bin");
        doc.setPath("/Root/Documents/unsupported.bin");
        doc.setMimeType("application/octet-stream");
        doc.setPreviewStatus(PreviewStatus.FAILED);
        doc.setPreviewFailureReason("Preview generation failed");

        Mockito.when(documentRepository.countPreviewFailuresByWindow(anyList(), any()))
            .thenReturn(1L);
        Mockito.when(documentRepository.findRecentPreviewFailuresByWindow(anyList(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(doc), PageRequest.of(0, 1), 1));
        Mockito.when(renditionResourceService.summarizeDocument(doc)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                docId,
                true,
                "UNSUPPORTED",
                false,
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                updated,
                null
            )
        );

        mockMvc.perform(post("/api/v1/ops/recovery/dry-run/export")
                .contentType("application/json")
                .content("""
                    {
                      "domain": "PREVIEW",
                      "mode": "QUEUE_BY_WINDOW",
                      "category": "UNSUPPORTED",
                      "retryable": false,
                      "days": 7,
                      "maxDocuments": 10,
                      "force": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Ops-Recovery-Dry-Run-Count", "1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                org.hamcrest.Matchers.containsString("previewFailureReason,previewFailureCategory,previewLastUpdated")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                org.hamcrest.Matchers.containsString("Preview definition is not registered for generic binary sources")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                org.hamcrest.Matchers.containsString("UNSUPPORTED")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                org.hamcrest.Matchers.containsString("2026-03-28T20:00")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can clear dead-letter entries in ops recovery control-plane")
    void clearBatchForAdmin() throws Exception {
        UUID docId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        String entryKey = PreviewDeadLetterRegistry.buildEntryKey(docId, "preview");
        LocalDateTime updated = LocalDateTime.of(2026, 3, 28, 19, 30, 0);

        Document doc = new Document();
        doc.setId(docId);
        doc.setName("dead-letter.bin");
        doc.setPath("/Root/Documents/dead-letter.bin");
        doc.setMimeType("application/octet-stream");
        doc.setPreviewStatus(PreviewStatus.UNSUPPORTED);
        doc.setPreviewFailureReason("Preview not supported for mime type application/octet-stream");

        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(doc));
        Mockito.when(previewDeadLetterRegistry.findByEntryKey(entryKey)).thenReturn(
            new PreviewDeadLetterRegistry.DeadLetterEntry(
                entryKey,
                docId,
                "preview",
                "Preview not supported for mime type application/octet-stream",
                "UNSUPPORTED",
                "default",
                "QUEUE_RETRY_EXHAUSTED",
                Instant.parse("2026-03-09T00:00:00Z"),
                3,
                1,
                null,
                0
            )
        );
        Mockito.when(renditionResourceService.summarizeDocument(doc)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                docId,
                true,
                "UNSUPPORTED",
                false,
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                updated,
                null
            )
        );

        mockMvc.perform(post("/api/v1/ops/recovery/clear-batch")
                .contentType("application/json")
                .content("""
                    {
                      "domain": "PREVIEW",
                      "entryKeys": ["%s"]
                    }
                    """.formatted(entryKey)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domain", is("PREVIEW")))
            .andExpect(jsonPath("$.mode", is("CLEAR_BATCH")))
            .andExpect(jsonPath("$.requested", is(1)))
            .andExpect(jsonPath("$.queued", is(1)))
            .andExpect(jsonPath("$.skipped", is(0)))
            .andExpect(jsonPath("$.failed", is(0)))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].documentId", is(docId.toString())))
            .andExpect(jsonPath("$.results[0].jobState", is("CLEARED")))
            .andExpect(jsonPath("$.results[0].outcome", is("CLEARED")))
            .andExpect(jsonPath("$.results[0].previewStatus", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.results[0].failureCategory", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.results[0].previewFailureReason", is("Preview definition is not registered for generic binary sources")))
            .andExpect(jsonPath("$.results[0].previewFailureCategory", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.results[0].previewLastUpdated", is("2026-03-28T19:30:00")));

        Mockito.verify(previewDeadLetterRegistry, Mockito.atLeastOnce()).remove(docId, "preview");
        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("OPS_RECOVERY_CLEAR_BATCH"),
            eq(null),
            eq("OPS_RECOVERY"),
            eq("admin"),
            Mockito.contains("mode=CLEAR_BATCH")
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can clear dead-letter entries by filter in ops recovery")
    void clearByFilterForAdmin() throws Exception {
        UUID tempDocId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID permDocId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        String tempEntryKey = PreviewDeadLetterRegistry.buildEntryKey(tempDocId, "preview");
        String permEntryKey = PreviewDeadLetterRegistry.buildEntryKey(permDocId, "preview");

        Document tempDoc = new Document();
        tempDoc.setId(tempDocId);
        tempDoc.setName("temporary.pdf");
        tempDoc.setPath("/Root/Documents/temporary.pdf");
        tempDoc.setMimeType("application/pdf");
        tempDoc.setPreviewStatus(PreviewStatus.FAILED);
        tempDoc.setPreviewFailureReason("Timeout contacting preview service");

        Document permDoc = new Document();
        permDoc.setId(permDocId);
        permDoc.setName("permanent.pdf");
        permDoc.setPath("/Root/Documents/permanent.pdf");
        permDoc.setMimeType("application/pdf");
        permDoc.setPreviewStatus(PreviewStatus.FAILED);
        permDoc.setPreviewFailureReason("Missing root object specification in trailer.");

        Mockito.when(previewDeadLetterRegistry.list(500)).thenReturn(List.of(
            new PreviewDeadLetterRegistry.DeadLetterEntry(
                tempEntryKey,
                tempDocId,
                "preview",
                "Timeout contacting preview service",
                "TEMPORARY",
                "default",
                "QUEUE_RETRY_EXHAUSTED",
                Instant.now(),
                3,
                1,
                null,
                0
            ),
            new PreviewDeadLetterRegistry.DeadLetterEntry(
                permEntryKey,
                permDocId,
                "preview",
                "Missing root object specification in trailer.",
                "PERMANENT",
                "default",
                "QUEUE_RETRY_EXHAUSTED",
                Instant.now(),
                3,
                1,
                null,
                0
            )
        ));
        Mockito.when(previewDeadLetterRegistry.getItemCount()).thenReturn(2);
        Mockito.when(previewDeadLetterRegistry.findByEntryKey(tempEntryKey)).thenReturn(
            new PreviewDeadLetterRegistry.DeadLetterEntry(
                tempEntryKey,
                tempDocId,
                "preview",
                "Timeout contacting preview service",
                "TEMPORARY",
                "default",
                "QUEUE_RETRY_EXHAUSTED",
                Instant.now(),
                3,
                1,
                null,
                0
            )
        );
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(tempDoc, permDoc));

        mockMvc.perform(post("/api/v1/ops/recovery/clear-by-filter")
                .contentType("application/json")
                .content("""
                    {
                      "domain": "PREVIEW",
                      "reason": "Timeout contacting preview service",
                      "category": "TEMPORARY",
                      "retryable": true,
                      "maxDocuments": 100,
                      "days": 7
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domain", is("PREVIEW")))
            .andExpect(jsonPath("$.mode", is("CLEAR_BY_FILTER")))
            .andExpect(jsonPath("$.totalCandidates", is(1)))
            .andExpect(jsonPath("$.matched", is(1)))
            .andExpect(jsonPath("$.queued", is(1)))
            .andExpect(jsonPath("$.failed", is(0)))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].documentId", is(tempDocId.toString())))
            .andExpect(jsonPath("$.results[0].jobState", is("CLEARED")));

        Mockito.verify(previewDeadLetterRegistry, Mockito.atLeastOnce()).remove(tempDocId, "preview");
        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("OPS_RECOVERY_CLEAR_BY_FILTER"),
            eq(null),
            eq("OPS_RECOVERY"),
            eq("admin"),
            Mockito.contains("reason=Timeout contacting preview service")
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can clear unsupported dead-letter entries by non-retryable filter in ops recovery")
    void clearByFilterForUnsupportedNonRetryable() throws Exception {
        UUID unsupportedDocId = UUID.fromString("c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1");
        String unsupportedEntryKey = PreviewDeadLetterRegistry.buildEntryKey(unsupportedDocId, "preview");

        Document unsupportedDoc = new Document();
        unsupportedDoc.setId(unsupportedDocId);
        unsupportedDoc.setName("unsupported.bin");
        unsupportedDoc.setPath("/Root/Documents/unsupported.bin");
        unsupportedDoc.setMimeType("application/octet-stream");
        unsupportedDoc.setPreviewStatus(PreviewStatus.FAILED);
        unsupportedDoc.setPreviewFailureReason("unsupported mime type");

        PreviewDeadLetterRegistry.DeadLetterEntry unsupportedEntry = new PreviewDeadLetterRegistry.DeadLetterEntry(
            unsupportedEntryKey,
            unsupportedDocId,
            "preview",
            "unsupported mime type",
            "UNSUPPORTED",
            "default",
            "UNSUPPORTED",
            Instant.now(),
            1,
            1,
            null,
            0
        );

        Mockito.when(previewDeadLetterRegistry.list(500)).thenReturn(List.of(unsupportedEntry));
        Mockito.when(previewDeadLetterRegistry.getItemCount()).thenReturn(1);
        Mockito.when(previewDeadLetterRegistry.findByEntryKey(unsupportedEntryKey)).thenReturn(unsupportedEntry);
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(unsupportedDoc));

        mockMvc.perform(post("/api/v1/ops/recovery/clear-by-filter")
                .contentType("application/json")
                .content("""
                    {
                      "domain": "PREVIEW",
                      "reason": "unsupported mime type",
                      "category": "UNSUPPORTED",
                      "retryable": false,
                      "maxDocuments": 100,
                      "days": 7
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domain", is("PREVIEW")))
            .andExpect(jsonPath("$.mode", is("CLEAR_BY_FILTER")))
            .andExpect(jsonPath("$.totalCandidates", is(1)))
            .andExpect(jsonPath("$.matched", is(1)))
            .andExpect(jsonPath("$.queued", is(1)))
            .andExpect(jsonPath("$.failed", is(0)))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].documentId", is(unsupportedDocId.toString())))
            .andExpect(jsonPath("$.results[0].jobState", is("CLEARED")))
            .andExpect(jsonPath("$.results[0].failureCategory", is("UNSUPPORTED")));

        Mockito.verify(previewDeadLetterRegistry, Mockito.atLeastOnce()).remove(unsupportedDocId, "preview");
        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("OPS_RECOVERY_CLEAR_BY_FILTER"),
            eq(null),
            eq("OPS_RECOVERY"),
            eq("admin"),
            Mockito.contains("category=UNSUPPORTED")
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can replay dead-letter entries by filter in ops recovery")
    void replayByFilterForAdmin() throws Exception {
        UUID tempDocId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        String tempEntryKey = PreviewDeadLetterRegistry.buildEntryKey(tempDocId, "preview");

        Document tempDoc = new Document();
        tempDoc.setId(tempDocId);
        tempDoc.setName("temporary-replay.pdf");
        tempDoc.setPath("/Root/Documents/temporary-replay.pdf");
        tempDoc.setMimeType("application/pdf");
        tempDoc.setPreviewStatus(PreviewStatus.FAILED);
        tempDoc.setPreviewFailureReason("Timeout contacting preview service");

        Mockito.when(previewDeadLetterRegistry.list(500)).thenReturn(List.of(
            new PreviewDeadLetterRegistry.DeadLetterEntry(
                tempEntryKey,
                tempDocId,
                "preview",
                "Timeout contacting preview service",
                "TEMPORARY",
                "default",
                "QUEUE_RETRY_EXHAUSTED",
                Instant.now(),
                3,
                1,
                null,
                0
            )
        ));
        Mockito.when(previewDeadLetterRegistry.getItemCount()).thenReturn(1);
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(tempDoc));
        Mockito.when(previewQueueService.enqueue(tempDocId, true)).thenReturn(
            new PreviewQueueService.PreviewQueueStatus(
                tempDocId,
                PreviewStatus.FAILED,
                true,
                0,
                null,
                "Preview queued"
            )
        );

        mockMvc.perform(post("/api/v1/ops/recovery/replay-by-filter")
                .contentType("application/json")
                .content("""
                    {
                      "domain": "PREVIEW",
                      "reason": "Timeout contacting preview service",
                      "category": "TEMPORARY",
                      "retryable": true,
                      "maxDocuments": 100,
                      "days": 7,
                      "force": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domain", is("PREVIEW")))
            .andExpect(jsonPath("$.mode", is("REPLAY_BY_FILTER")))
            .andExpect(jsonPath("$.matched", is(1)))
            .andExpect(jsonPath("$.queued", is(1)))
            .andExpect(jsonPath("$.failed", is(0)))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].documentId", is(tempDocId.toString())))
            .andExpect(jsonPath("$.results[0].jobState", is("QUEUED")));

        Mockito.verify(previewDeadLetterRegistry, Mockito.atLeastOnce()).remove(tempDocId, "preview");
        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("OPS_RECOVERY_REPLAY_BY_FILTER"),
            eq(null),
            eq("OPS_RECOVERY"),
            eq("admin"),
            Mockito.contains("reason=Timeout contacting preview service")
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Replay-by-filter prefers rendition summary reason and preview status in ops recovery")
    void replayByFilterPrefersRenditionSummary() throws Exception {
        UUID docId = UUID.randomUUID();
        String entryKey = PreviewDeadLetterRegistry.buildEntryKey(docId, "preview");
        LocalDateTime updated = LocalDateTime.of(2026, 3, 28, 19, 0, 0);

        Document doc = new Document();
        doc.setId(docId);
        doc.setName("unsupported.bin");
        doc.setPath("/Root/Documents/unsupported.bin");
        doc.setMimeType("application/octet-stream");
        doc.setPreviewStatus(PreviewStatus.FAILED);
        doc.setPreviewFailureReason("Preview generation failed");

        PreviewDeadLetterRegistry.DeadLetterEntry entry = new PreviewDeadLetterRegistry.DeadLetterEntry(
            entryKey,
            docId,
            "preview",
            "Preview definition is not registered for generic binary sources",
            "UNSUPPORTED",
            "default",
            "UNSUPPORTED",
            Instant.now(),
            1,
            1,
            null,
            0
        );

        Mockito.when(previewDeadLetterRegistry.list(500)).thenReturn(List.of(entry));
        Mockito.when(previewDeadLetterRegistry.getItemCount()).thenReturn(1);
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(doc));
        Mockito.when(renditionResourceService.summarizeDocument(doc)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                docId,
                true,
                "UNSUPPORTED",
                false,
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                updated,
                null
            )
        );
        Mockito.when(previewQueueService.enqueue(docId, true)).thenReturn(
            new PreviewQueueService.PreviewQueueStatus(
                docId,
                null,
                true,
                0,
                null,
                "Preview queued"
            )
        );

        mockMvc.perform(post("/api/v1/ops/recovery/replay-by-filter")
                .contentType("application/json")
                .content("""
                    {
                      "domain": "PREVIEW",
                      "reason": "Preview definition is not registered for generic binary sources",
                      "category": "UNSUPPORTED",
                      "retryable": false,
                      "maxDocuments": 100,
                      "days": 7,
                      "force": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matched", is(1)))
            .andExpect(jsonPath("$.queued", is(1)))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].documentId", is(docId.toString())))
            .andExpect(jsonPath("$.results[0].previewStatus", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.results[0].failureCategory", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.results[0].previewFailureReason", is("Preview definition is not registered for generic binary sources")))
            .andExpect(jsonPath("$.results[0].previewFailureCategory", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.results[0].previewLastUpdated", is("2026-03-28T19:00:00")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Dry run supports clear-by-filter mode with dead-letter estimates")
    void dryRunSupportsClearByFilterMode() throws Exception {
        UUID tempDocId = UUID.fromString("abababab-abab-abab-abab-abababababab");
        String tempEntryKey = PreviewDeadLetterRegistry.buildEntryKey(tempDocId, "preview");

        Document tempDoc = new Document();
        tempDoc.setId(tempDocId);
        tempDoc.setName("dryrun-clear.pdf");
        tempDoc.setPath("/Root/Documents/dryrun-clear.pdf");
        tempDoc.setMimeType("application/pdf");
        tempDoc.setPreviewStatus(PreviewStatus.FAILED);
        tempDoc.setPreviewFailureReason("Timeout contacting preview service");

        PreviewDeadLetterRegistry.DeadLetterEntry deadLetterEntry = new PreviewDeadLetterRegistry.DeadLetterEntry(
            tempEntryKey,
            tempDocId,
            "preview",
            "Timeout contacting preview service",
            "TEMPORARY",
            "default",
            "QUEUE_RETRY_EXHAUSTED",
            Instant.now(),
            3,
            1,
            null,
            0
        );

        Mockito.when(previewDeadLetterRegistry.list(500)).thenReturn(List.of(deadLetterEntry));
        Mockito.when(previewDeadLetterRegistry.getItemCount()).thenReturn(1);
        Mockito.when(previewDeadLetterRegistry.findByEntryKey(tempEntryKey)).thenReturn(deadLetterEntry);
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(tempDoc));

        mockMvc.perform(post("/api/v1/ops/recovery/dry-run")
                .contentType("application/json")
                .content("""
                    {
                      "domain": "PREVIEW",
                      "mode": "CLEAR_BY_FILTER",
                      "reason": "Timeout contacting preview service",
                      "category": "TEMPORARY",
                      "retryable": true,
                      "maxDocuments": 100,
                      "days": 7
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domain", is("PREVIEW")))
            .andExpect(jsonPath("$.mode", is("CLEAR_BY_FILTER")))
            .andExpect(jsonPath("$.matched", is(1)))
            .andExpect(jsonPath("$.estimatedQueued", is(1)))
            .andExpect(jsonPath("$.estimatedSkipped", is(0)))
            .andExpect(jsonPath("$.estimatedFailed", is(0)))
            .andExpect(jsonPath("$.samples", hasSize(1)))
            .andExpect(jsonPath("$.samples[0].predictedState", is("CLEARED")))
            .andExpect(jsonPath("$.samples[0].predictedOutcome", is("CLEARED")));

        Mockito.verify(previewQueueService, Mockito.never()).enqueue(any(UUID.class), anyBoolean());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Dry run supports replay-by-filter mode with force replay estimates")
    void dryRunSupportsReplayByFilterMode() throws Exception {
        UUID tempDocId = UUID.fromString("cdcdcdcd-cdcd-cdcd-cdcd-cdcdcdcdcdcd");
        String tempEntryKey = PreviewDeadLetterRegistry.buildEntryKey(tempDocId, "preview");

        Document tempDoc = new Document();
        tempDoc.setId(tempDocId);
        tempDoc.setName("dryrun-replay.pdf");
        tempDoc.setPath("/Root/Documents/dryrun-replay.pdf");
        tempDoc.setMimeType("application/pdf");
        tempDoc.setPreviewStatus(PreviewStatus.FAILED);
        tempDoc.setPreviewFailureReason("Timeout contacting preview service");

        Mockito.when(previewDeadLetterRegistry.list(500)).thenReturn(List.of(
            new PreviewDeadLetterRegistry.DeadLetterEntry(
                tempEntryKey,
                tempDocId,
                "preview",
                "Timeout contacting preview service",
                "TEMPORARY",
                "default",
                "QUEUE_RETRY_EXHAUSTED",
                Instant.now(),
                3,
                1,
                null,
                0
            )
        ));
        Mockito.when(previewDeadLetterRegistry.getItemCount()).thenReturn(1);
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(tempDoc));

        mockMvc.perform(post("/api/v1/ops/recovery/dry-run")
                .contentType("application/json")
                .content("""
                    {
                      "domain": "PREVIEW",
                      "mode": "REPLAY_BY_FILTER",
                      "reason": "Timeout contacting preview service",
                      "category": "TEMPORARY",
                      "retryable": true,
                      "maxDocuments": 100,
                      "days": 7,
                      "force": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domain", is("PREVIEW")))
            .andExpect(jsonPath("$.mode", is("REPLAY_BY_FILTER")))
            .andExpect(jsonPath("$.matched", is(1)))
            .andExpect(jsonPath("$.estimatedQueued", is(1)))
            .andExpect(jsonPath("$.estimatedSkipped", is(0)))
            .andExpect(jsonPath("$.estimatedFailed", is(0)))
            .andExpect(jsonPath("$.samples", hasSize(1)))
            .andExpect(jsonPath("$.samples[0].predictedState", is("QUEUED")))
            .andExpect(jsonPath("$.samples[0].predictedOutcome", is("QUEUED")));

        Mockito.verify(previewQueueService, Mockito.never()).enqueue(any(UUID.class), anyBoolean());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Unsupported domain returns bad request")
    void unsupportedDomainReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/ops/recovery/queue-by-window")
                .contentType("application/json")
                .content("""
                    {
                      "domain": "SEARCH",
                      "days": 7
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("Unsupported recovery domain: SEARCH")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can list ops recovery history entries")
    void listHistoryForAdmin() throws Exception {
        UUID documentId = UUID.randomUUID();
        AuditLog logEntry = AuditLog.builder()
            .id(UUID.randomUUID())
            .nodeId(documentId)
            .nodeName("archive.bin")
            .eventType("OPS_RECOVERY_QUEUE_BY_REASON")
            .username("admin")
            .eventTime(LocalDateTime.of(2026, 3, 7, 1, 0, 0))
            .details("domain=PREVIEW mode=QUEUE_BY_REASON queued=1 skipped=0 failed=0")
            .build();

        Document document = new Document();
        document.setId(documentId);
        document.setName("archive.bin");
        document.setMimeType("application/octet-stream");
        document.setDeleted(false);

        Mockito.when(auditLogRepository.findByEventTypePrefix(eq("OPS_RECOVERY_"), any()))
            .thenReturn(new PageImpl<>(List.of(logEntry), PageRequest.of(0, 5), 1));
        Mockito.when(documentRepository.findById(documentId)).thenReturn(java.util.Optional.of(document));
        Mockito.when(renditionResourceService.resolveEffectivePreviewSnapshot(
            eq(document),
            eq(null),
            eq(null),
            eq(null),
            eq(null)
        )).thenReturn(
            new RenditionResourceService.EffectivePreviewSnapshot(
                "UNSUPPORTED",
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                LocalDateTime.of(2026, 3, 7, 1, 30, 0)
            )
        );

        mockMvc.perform(get("/api/v1/ops/recovery/history")
                .param("limit", "5")
                .param("page", "0")
                .param("days", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domain", is("PREVIEW")))
            .andExpect(jsonPath("$.windowDays", is(0)))
            .andExpect(jsonPath("$.limit", is(5)))
            .andExpect(jsonPath("$.page", is(0)))
            .andExpect(jsonPath("$.totalPages", is(1)))
            .andExpect(jsonPath("$.total", is(1)))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].nodeId", is(documentId.toString())))
            .andExpect(jsonPath("$.items[0].nodeName", is("archive.bin")))
            .andExpect(jsonPath("$.items[0].eventType", is("OPS_RECOVERY_QUEUE_BY_REASON")))
            .andExpect(jsonPath("$.items[0].mode", is("QUEUE_BY_REASON")))
            .andExpect(jsonPath("$.items[0].actor", is("admin")))
            .andExpect(jsonPath("$.items[0].previewStatus", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.items[0].previewFailureCategory", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.items[0].previewFailureReason", is("Preview definition is not registered for generic binary sources")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can get ops recovery history summary with filters")
    void summaryHistoryForAdmin() throws Exception {
        Mockito.when(auditLogRepository.countByEventTypePrefixWithFilters(
                eq("OPS_RECOVERY_"), any(), eq("admin"), eq("OPS_RECOVERY_DRY_RUN")))
            .thenReturn(List.<Object[]>of(new Object[] {"OPS_RECOVERY_DRY_RUN", 3L}));
        Mockito.when(auditLogRepository.countByUsernamePrefixWithFilters(
                eq("OPS_RECOVERY_"), any(), eq("admin"), eq("OPS_RECOVERY_DRY_RUN")))
            .thenReturn(List.<Object[]>of(new Object[] {"admin", 3L}));
        Mockito.when(auditLogRepository.countByUsernamePrefixWithFilters(
                eq("OPS_RECOVERY_"), any(), eq("admin"), eq("OPS_RECOVERY_DRY_RUN")))
            .thenReturn(List.<Object[]>of(new Object[] {"admin", 3L}));
        Mockito.when(auditLogRepository.countByUsernamePrefixWithFilters(
                eq("OPS_RECOVERY_"), any(), eq("admin"), eq("OPS_RECOVERY_DRY_RUN")))
            .thenReturn(List.<Object[]>of(new Object[] {"admin", 3L}));

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary")
                .param("days", "7")
                .param("mode", "DRY_RUN")
                .param("actor", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domain", is("PREVIEW")))
            .andExpect(jsonPath("$.windowDays", is(7)))
            .andExpect(jsonPath("$.modeFilter", is("DRY_RUN")))
            .andExpect(jsonPath("$.actorFilter", is("admin")))
            .andExpect(jsonPath("$.eventTypeFilter").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.total", is(3)))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].eventType", is("OPS_RECOVERY_DRY_RUN")))
            .andExpect(jsonPath("$.items[0].mode", is("DRY_RUN")))
            .andExpect(jsonPath("$.items[0].count", is(3)))
            .andExpect(jsonPath("$.actorItems", hasSize(1)))
            .andExpect(jsonPath("$.actorItems[0].actor", is("admin")))
            .andExpect(jsonPath("$.actorItems[0].count", is(3)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can compare current vs previous summary window")
    void compareSummaryHistoryForAdmin() throws Exception {
        Mockito.when(auditLogRepository.countByEventTypePrefixWithFilters(
                eq("OPS_RECOVERY_"), any(), eq("admin"), eq("OPS_RECOVERY_DRY_RUN")))
            .thenReturn(List.<Object[]>of(new Object[] {"OPS_RECOVERY_DRY_RUN", 3L}));

        AuditLog previousOne = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("admin")
            .eventTime(LocalDateTime.now().minusDays(10))
            .details("previous")
            .build();
        AuditLog previousTwo = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("admin")
            .eventTime(LocalDateTime.now().minusDays(8))
            .details("previous")
            .build();
        Mockito.when(auditLogRepository.findByEventTypeAndUsernameAndEventTimeGreaterThanEqualOrderByEventTimeDesc(
                eq("OPS_RECOVERY_DRY_RUN"), eq("admin"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(previousOne, previousTwo), PageRequest.of(0, 500), 2));

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary/compare")
                .param("days", "7")
                .param("mode", "DRY_RUN")
                .param("actor", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domain", is("PREVIEW")))
            .andExpect(jsonPath("$.windowDays", is(7)))
            .andExpect(jsonPath("$.previousWindowDays", is(7)))
            .andExpect(jsonPath("$.modeFilter", is("DRY_RUN")))
            .andExpect(jsonPath("$.actorFilter", is("admin")))
            .andExpect(jsonPath("$.eventTypeFilter").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.currentTotal", is(3)))
            .andExpect(jsonPath("$.previousTotal", is(2)))
            .andExpect(jsonPath("$.delta", is(1)))
            .andExpect(jsonPath("$.compareAvailable", is(true)))
            .andExpect(jsonPath("$.truncated", is(false)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can compare summary breakdown by mode")
    void compareSummaryHistoryBreakdownForAdmin() throws Exception {
        Mockito.when(auditLogRepository.countByEventTypePrefixWithFilters(
                eq("OPS_RECOVERY_"), any(), eq("admin"), eq("OPS_RECOVERY_DRY_RUN")))
            .thenReturn(List.<Object[]>of(new Object[] {"OPS_RECOVERY_DRY_RUN", 3L}));
        Mockito.when(auditLogRepository.countByUsernamePrefixWithFilters(
                eq("OPS_RECOVERY_"), any(), eq("admin"), eq("OPS_RECOVERY_DRY_RUN")))
            .thenReturn(List.<Object[]>of(new Object[] {"admin", 3L}));

        AuditLog previousOne = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("admin")
            .eventTime(LocalDateTime.now().minusDays(10))
            .details("previous one")
            .build();
        AuditLog previousTwo = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("admin")
            .eventTime(LocalDateTime.now().minusDays(8))
            .details("previous two")
            .build();
        Mockito.when(auditLogRepository.findByEventTypeAndUsernameAndEventTimeGreaterThanEqualOrderByEventTimeDesc(
                eq("OPS_RECOVERY_DRY_RUN"), eq("admin"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(previousOne, previousTwo), PageRequest.of(0, 500), 2));

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary/compare/breakdown")
                .param("days", "7")
                .param("mode", "DRY_RUN")
                .param("actor", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domain", is("PREVIEW")))
            .andExpect(jsonPath("$.windowDays", is(7)))
            .andExpect(jsonPath("$.previousWindowDays", is(7)))
            .andExpect(jsonPath("$.modeFilter", is("DRY_RUN")))
            .andExpect(jsonPath("$.actorFilter", is("admin")))
            .andExpect(jsonPath("$.compareAvailable", is(true)))
            .andExpect(jsonPath("$.truncated", is(false)))
            .andExpect(jsonPath("$.sortBy", is("DELTA_ABS_DESC")))
            .andExpect(jsonPath("$.requestedLimit", is(10)))
            .andExpect(jsonPath("$.totalItems", is(1)))
            .andExpect(jsonPath("$.limited", is(false)))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].eventType", is("OPS_RECOVERY_DRY_RUN")))
            .andExpect(jsonPath("$.items[0].mode", is("DRY_RUN")))
            .andExpect(jsonPath("$.items[0].currentCount", is(3)))
            .andExpect(jsonPath("$.items[0].previousCount", is(2)))
            .andExpect(jsonPath("$.items[0].delta", is(1)))
            .andExpect(jsonPath("$.items[0].deltaPercent", is(50.0)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can sort and limit summary compare breakdown results")
    void compareSummaryHistoryBreakdownSortAndLimitForAdmin() throws Exception {
        Mockito.when(auditLogRepository.countByEventTypePrefixWithFilters(
                eq("OPS_RECOVERY_"), any(), eq("admin"), eq((String) null)))
            .thenReturn(List.<Object[]>of(
                new Object[] {"OPS_RECOVERY_DRY_RUN", 5L},
                new Object[] {"OPS_RECOVERY_QUEUE_BY_WINDOW", 2L}
            ));
        Mockito.when(auditLogRepository.countByUsernamePrefixWithFilters(
                eq("OPS_RECOVERY_"), any(), eq("admin"), eq((String) null)))
            .thenReturn(List.<Object[]>of(new Object[] {"admin", 7L}));

        AuditLog previousDryRun = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("admin")
            .eventTime(LocalDateTime.now().minusDays(10))
            .details("dry")
            .build();
        AuditLog previousWindowOne = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_QUEUE_BY_WINDOW")
            .username("admin")
            .eventTime(LocalDateTime.now().minusDays(9))
            .details("window")
            .build();
        AuditLog previousWindowTwo = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_QUEUE_BY_WINDOW")
            .username("admin")
            .eventTime(LocalDateTime.now().minusDays(8))
            .details("window")
            .build();
        AuditLog previousWindowThree = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_QUEUE_BY_WINDOW")
            .username("admin")
            .eventTime(LocalDateTime.now().minusDays(8))
            .details("window")
            .build();
        Mockito.when(auditLogRepository.findByEventTypePrefixSinceAndUsername(
                eq("OPS_RECOVERY_"), any(), eq("admin"), any()))
            .thenReturn(new PageImpl<>(
                List.of(previousDryRun, previousWindowOne, previousWindowTwo, previousWindowThree),
                PageRequest.of(0, 500),
                4
            ));

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary/compare/breakdown")
                .param("days", "7")
                .param("actor", "admin")
                .param("sort", "DELTA_DESC")
                .param("limit", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sortBy", is("DELTA_DESC")))
            .andExpect(jsonPath("$.requestedLimit", is(1)))
            .andExpect(jsonPath("$.totalItems", is(2)))
            .andExpect(jsonPath("$.limited", is(true)))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].eventType", is("OPS_RECOVERY_DRY_RUN")))
            .andExpect(jsonPath("$.items[0].delta", is(4)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can compare summary by actors")
    void compareSummaryHistoryActorsForAdmin() throws Exception {
        Mockito.when(auditLogRepository.countByEventTypePrefixWithFilters(
                eq("OPS_RECOVERY_"), any(), eq("admin"), eq((String) null)))
            .thenReturn(List.<Object[]>of(new Object[] {"OPS_RECOVERY_DRY_RUN", 3L}));
        Mockito.when(auditLogRepository.countByUsernamePrefixWithFilters(
                eq("OPS_RECOVERY_"), any(), eq("admin"), eq((String) null)))
            .thenReturn(List.<Object[]>of(new Object[] {"admin", 3L}));

        AuditLog previousOne = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("admin")
            .eventTime(LocalDateTime.now().minusDays(10))
            .details("previous one")
            .build();
        AuditLog previousTwo = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("admin")
            .eventTime(LocalDateTime.now().minusDays(8))
            .details("previous two")
            .build();
        Mockito.when(auditLogRepository.findByEventTypePrefixSinceAndUsername(
                eq("OPS_RECOVERY_"), any(), eq("admin"), any()))
            .thenReturn(new PageImpl<>(List.of(previousOne, previousTwo), PageRequest.of(0, 500), 2));

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary/compare/actors")
                .param("days", "7")
                .param("actor", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domain", is("PREVIEW")))
            .andExpect(jsonPath("$.windowDays", is(7)))
            .andExpect(jsonPath("$.previousWindowDays", is(7)))
            .andExpect(jsonPath("$.actorFilter", is("admin")))
            .andExpect(jsonPath("$.compareAvailable", is(true)))
            .andExpect(jsonPath("$.sortBy", is("DELTA_ABS_DESC")))
            .andExpect(jsonPath("$.requestedLimit", is(10)))
            .andExpect(jsonPath("$.totalItems", is(1)))
            .andExpect(jsonPath("$.limited", is(false)))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].actor", is("admin")))
            .andExpect(jsonPath("$.items[0].currentCount", is(3)))
            .andExpect(jsonPath("$.items[0].previousCount", is(2)))
            .andExpect(jsonPath("$.items[0].delta", is(1)))
            .andExpect(jsonPath("$.items[0].deltaPercent", is(50.0)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can sort and limit actor compare results")
    void compareSummaryHistoryActorsSortAndLimitForAdmin() throws Exception {
        Mockito.when(auditLogRepository.countByEventTypePrefixWithFilters(
                eq("OPS_RECOVERY_"), any(), eq((String) null), eq((String) null)))
            .thenReturn(List.<Object[]>of(
                new Object[] {"OPS_RECOVERY_DRY_RUN", 7L}
            ));
        Mockito.when(auditLogRepository.countByUsernamePrefixWithFilters(
                eq("OPS_RECOVERY_"), any(), eq((String) null), eq((String) null)))
            .thenReturn(List.<Object[]>of(
                new Object[] {"admin", 5L},
                new Object[] {"ops-admin", 2L}
            ));

        AuditLog previousAdminOne = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("admin")
            .eventTime(LocalDateTime.now().minusDays(10))
            .details("admin")
            .build();
        AuditLog previousOpsOne = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("ops-admin")
            .eventTime(LocalDateTime.now().minusDays(9))
            .details("ops")
            .build();
        AuditLog previousOpsTwo = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("ops-admin")
            .eventTime(LocalDateTime.now().minusDays(8))
            .details("ops")
            .build();
        AuditLog previousOpsThree = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("ops-admin")
            .eventTime(LocalDateTime.now().minusDays(8))
            .details("ops")
            .build();
        Mockito.when(auditLogRepository.findByEventTypePrefixSince(
                eq("OPS_RECOVERY_"), any(), any()))
            .thenReturn(new PageImpl<>(
                List.of(previousAdminOne, previousOpsOne, previousOpsTwo, previousOpsThree),
                PageRequest.of(0, 500),
                4
            ));

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary/compare/actors")
                .param("days", "7")
                .param("sort", "DELTA_DESC")
                .param("limit", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sortBy", is("DELTA_DESC")))
            .andExpect(jsonPath("$.requestedLimit", is(1)))
            .andExpect(jsonPath("$.totalItems", is(2)))
            .andExpect(jsonPath("$.limited", is(true)))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].actor", is("admin")))
            .andExpect(jsonPath("$.items[0].delta", is(4)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can export summary compare actors as CSV")
    void exportCompareSummaryHistoryActorsForAdmin() throws Exception {
        Mockito.when(auditLogRepository.countByEventTypePrefixWithFilters(
                eq("OPS_RECOVERY_"), any(), eq((String) null), eq((String) null)))
            .thenReturn(List.<Object[]>of(new Object[] {"OPS_RECOVERY_DRY_RUN", 3L}));
        Mockito.when(auditLogRepository.countByUsernamePrefixWithFilters(
                eq("OPS_RECOVERY_"), any(), eq((String) null), eq((String) null)))
            .thenReturn(List.<Object[]>of(
                new Object[] {"admin", 2L},
                new Object[] {"ops-admin", 1L}
            ));

        AuditLog previousAdmin = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("admin")
            .eventTime(LocalDateTime.now().minusDays(10))
            .details("admin")
            .build();
        Mockito.when(auditLogRepository.findByEventTypePrefixSince(
                eq("OPS_RECOVERY_"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(previousAdmin), PageRequest.of(0, 500), 1));

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary/compare/actors/export")
                .param("days", "7")
                .param("sort", "DELTA_ASC")
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Ops-Recovery-Compare-Actors-Count", "2"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("sortBy,requestedLimit,totalItems,limited,actor")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("DELTA_ASC")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("admin")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("ops-admin")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can export summary compare breakdown as CSV")
    void exportCompareSummaryHistoryBreakdownForAdmin() throws Exception {
        Mockito.when(auditLogRepository.countByEventTypePrefixWithFilters(
                eq("OPS_RECOVERY_"), any(), eq("admin"), eq("OPS_RECOVERY_DRY_RUN")))
            .thenReturn(List.<Object[]>of(new Object[] {"OPS_RECOVERY_DRY_RUN", 3L}));
        Mockito.when(auditLogRepository.countByUsernamePrefixWithFilters(
                eq("OPS_RECOVERY_"), any(), eq("admin"), eq("OPS_RECOVERY_DRY_RUN")))
            .thenReturn(List.<Object[]>of(new Object[] {"admin", 3L}));

        AuditLog previousOne = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("admin")
            .eventTime(LocalDateTime.now().minusDays(10))
            .details("previous one")
            .build();
        AuditLog previousTwo = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("admin")
            .eventTime(LocalDateTime.now().minusDays(8))
            .details("previous two")
            .build();
        Mockito.when(auditLogRepository.findByEventTypeAndUsernameAndEventTimeGreaterThanEqualOrderByEventTimeDesc(
                eq("OPS_RECOVERY_DRY_RUN"), eq("admin"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(previousOne, previousTwo), PageRequest.of(0, 500), 2));

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary/compare/breakdown/export")
                .param("days", "7")
                .param("mode", "DRY_RUN")
                .param("actor", "admin")
                .param("sort", "DELTA_ASC")
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Ops-Recovery-Compare-Breakdown-Count", "1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("domain,windowDays,previousWindowDays")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("DELTA_ASC")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("OPS_RECOVERY_DRY_RUN")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("\"3\"")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("\"2\"")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("\"1\"")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can export current vs previous summary compare as CSV")
    void exportCompareSummaryHistoryForAdmin() throws Exception {
        Mockito.when(auditLogRepository.countByEventTypePrefixWithFilters(
                eq("OPS_RECOVERY_"), any(), eq("admin"), eq("OPS_RECOVERY_DRY_RUN")))
            .thenReturn(List.<Object[]>of(new Object[] {"OPS_RECOVERY_DRY_RUN", 3L}));
        Mockito.when(auditLogRepository.countByUsernamePrefixWithFilters(
                eq("OPS_RECOVERY_"), any(), eq("admin"), eq("OPS_RECOVERY_DRY_RUN")))
            .thenReturn(List.<Object[]>of(new Object[] {"admin", 3L}));

        AuditLog previousOne = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("admin")
            .eventTime(LocalDateTime.now().minusDays(10))
            .details("previous")
            .build();
        AuditLog previousTwo = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("admin")
            .eventTime(LocalDateTime.now().minusDays(8))
            .details("previous")
            .build();
        Mockito.when(auditLogRepository.findByEventTypeAndUsernameAndEventTimeGreaterThanEqualOrderByEventTimeDesc(
                eq("OPS_RECOVERY_DRY_RUN"), eq("admin"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(previousOne, previousTwo), PageRequest.of(0, 500), 2));

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary/compare/export")
                .param("days", "7")
                .param("mode", "DRY_RUN")
                .param("actor", "admin"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Ops-Recovery-Compare-Count", "1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("domain,windowDays,previousWindowDays")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("PREVIEW")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("\"3\"")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("\"2\"")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("\"1\"")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can get ops recovery history summary trend with filters")
    void summaryHistoryTrendForAdmin() throws Exception {
        AuditLog first = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("admin")
            .eventTime(LocalDateTime.of(2026, 3, 7, 10, 0, 0))
            .details("dry run item one")
            .build();
        AuditLog second = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("admin")
            .eventTime(LocalDateTime.of(2026, 3, 7, 11, 0, 0))
            .details("dry run item two")
            .build();
        Mockito.when(auditLogRepository.findByEventTypeAndUsernameAndEventTimeGreaterThanEqualOrderByEventTimeDesc(
                eq("OPS_RECOVERY_DRY_RUN"), eq("admin"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 500), 2));

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary/trend")
                .param("days", "7")
                .param("mode", "DRY_RUN")
                .param("actor", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domain", is("PREVIEW")))
            .andExpect(jsonPath("$.windowDays", is(7)))
            .andExpect(jsonPath("$.modeFilter", is("DRY_RUN")))
            .andExpect(jsonPath("$.actorFilter", is("admin")))
            .andExpect(jsonPath("$.eventTypeFilter").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.total", is(2)))
            .andExpect(jsonPath("$.truncated", is(false)))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].day", is("2026-03-07")))
            .andExpect(jsonPath("$.items[0].count", is(2)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can export ops recovery history summary trend as CSV")
    void exportHistorySummaryTrendCsvForAdmin() throws Exception {
        AuditLog first = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("admin")
            .eventTime(LocalDateTime.of(2026, 3, 7, 10, 0, 0))
            .details("dry run item one")
            .build();
        AuditLog second = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("admin")
            .eventTime(LocalDateTime.of(2026, 3, 7, 11, 0, 0))
            .details("dry run item two")
            .build();
        Mockito.when(auditLogRepository.findByEventTypeAndUsernameAndEventTimeGreaterThanEqualOrderByEventTimeDesc(
                eq("OPS_RECOVERY_DRY_RUN"), eq("admin"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 500), 2));

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary/trend/export")
                .param("days", "7")
                .param("mode", "DRY_RUN")
                .param("actor", "admin"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Ops-Recovery-Trend-Count", "1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("day,count")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("2026-03-07")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("\"2\"")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can filter ops recovery history by mode")
    void listHistoryByModeForAdmin() throws Exception {
        AuditLog logEntry = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("admin")
            .eventTime(LocalDateTime.of(2026, 3, 7, 1, 5, 0))
            .details("domain=PREVIEW mode=QUEUE_BY_WINDOW estimatedQueued=1")
            .build();

        Mockito.when(auditLogRepository.findByEventTypeAndEventTimeGreaterThanEqualOrderByEventTimeDesc(
                eq("OPS_RECOVERY_DRY_RUN"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(logEntry), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/ops/recovery/history")
                .param("limit", "10")
                .param("page", "0")
                .param("days", "7")
                .param("mode", "DRY_RUN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page", is(0)))
            .andExpect(jsonPath("$.modeFilter", is("DRY_RUN")))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].eventType", is("OPS_RECOVERY_DRY_RUN")))
            .andExpect(jsonPath("$.items[0].mode", is("DRY_RUN")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can paginate ops recovery history entries")
    void listHistoryPaginationForAdmin() throws Exception {
        AuditLog logEntry = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_REPLAY_BATCH")
            .username("admin")
            .eventTime(LocalDateTime.of(2026, 3, 7, 2, 30, 0))
            .details("domain=PREVIEW mode=REPLAY_BATCH queued=2 skipped=0 failed=0")
            .build();

        Mockito.when(auditLogRepository.findByEventTypePrefixSince(eq("OPS_RECOVERY_"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(logEntry), PageRequest.of(1, 2), 5));

        mockMvc.perform(get("/api/v1/ops/recovery/history")
                .param("limit", "2")
                .param("page", "1")
                .param("days", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limit", is(2)))
            .andExpect(jsonPath("$.page", is(1)))
            .andExpect(jsonPath("$.totalPages", is(3)))
            .andExpect(jsonPath("$.total", is(5)))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].mode", is("REPLAY_BATCH")));

        Mockito.verify(auditLogRepository).findByEventTypePrefixSince(
            eq("OPS_RECOVERY_"),
            any(),
            argThat(pageable -> pageable.getPageNumber() == 1 && pageable.getPageSize() == 2)
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can filter ops recovery history by actor")
    void listHistoryByActorForAdmin() throws Exception {
        AuditLog logEntry = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_QUEUE_BY_WINDOW")
            .username("ops-admin")
            .eventTime(LocalDateTime.of(2026, 3, 7, 3, 0, 0))
            .details("domain=PREVIEW mode=QUEUE_BY_WINDOW queued=3 skipped=0 failed=0")
            .build();

        Mockito.when(auditLogRepository.findByEventTypePrefixSinceAndUsername(
                eq("OPS_RECOVERY_"), any(), eq("ops-admin"), any()))
            .thenReturn(new PageImpl<>(List.of(logEntry), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/ops/recovery/history")
                .param("limit", "20")
                .param("page", "0")
                .param("days", "7")
                .param("actor", "ops-admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.actorFilter", is("ops-admin")))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].actor", is("ops-admin")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can filter ops recovery history by event type")
    void listHistoryByEventTypeForAdmin() throws Exception {
        AuditLog logEntry = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_HISTORY_EXPORT")
            .username("admin")
            .eventTime(LocalDateTime.of(2026, 3, 7, 3, 10, 0))
            .details("limit=500 days=7 mode=DRY_RUN actor=admin eventType=OPS_RECOVERY_DRY_RUN count=1")
            .build();

        Mockito.when(auditLogRepository.findByEventTypeAndEventTimeGreaterThanEqualOrderByEventTimeDesc(
                eq("OPS_RECOVERY_HISTORY_EXPORT"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(logEntry), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/ops/recovery/history")
                .param("limit", "20")
                .param("page", "0")
                .param("days", "7")
                .param("eventType", "OPS_RECOVERY_HISTORY_EXPORT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventTypeFilter", is("OPS_RECOVERY_HISTORY_EXPORT")))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].eventType", is("OPS_RECOVERY_HISTORY_EXPORT")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can export ops recovery history as CSV")
    void exportHistoryCsvForAdmin() throws Exception {
        UUID documentId = UUID.randomUUID();
        AuditLog logEntry = AuditLog.builder()
            .id(UUID.randomUUID())
            .nodeId(documentId)
            .nodeName("archive.bin")
            .eventType("OPS_RECOVERY_QUEUE_BY_WINDOW")
            .username("admin")
            .eventTime(LocalDateTime.of(2026, 3, 7, 2, 0, 0))
            .details("domain=PREVIEW mode=QUEUE_BY_WINDOW queued=1")
            .build();

        Document document = new Document();
        document.setId(documentId);
        document.setName("archive.bin");
        document.setMimeType("application/octet-stream");
        document.setDeleted(false);

        Mockito.when(auditLogRepository.findByEventTypeAndEventTimeGreaterThanEqualOrderByEventTimeDesc(
                eq("OPS_RECOVERY_QUEUE_BY_WINDOW"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(logEntry), PageRequest.of(0, 100), 1));
        Mockito.when(documentRepository.findById(documentId)).thenReturn(java.util.Optional.of(document));
        Mockito.when(renditionResourceService.summarizeDocument(document)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                documentId,
                true,
                "UNSUPPORTED",
                false,
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                LocalDateTime.of(2026, 3, 7, 2, 30, 0),
                null
            )
        );

        mockMvc.perform(get("/api/v1/ops/recovery/history/export")
                .param("limit", "100")
                .param("days", "7")
                .param("mode", "QUEUE_BY_WINDOW"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Ops-Recovery-Count", "1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("nodeId,nodeName")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("previewFailureReason,previewFailureCategory,previewLastUpdated")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("OPS_RECOVERY_QUEUE_BY_WINDOW")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("archive.bin")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Preview definition is not registered for generic binary sources")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can export ops recovery history summary as CSV")
    void exportHistorySummaryCsvForAdmin() throws Exception {
        Mockito.when(auditLogRepository.countByEventTypePrefixWithFilters(
                eq("OPS_RECOVERY_"), any(), eq("admin"), eq("OPS_RECOVERY_DRY_RUN")))
            .thenReturn(List.<Object[]>of(new Object[] {"OPS_RECOVERY_DRY_RUN", 3L}));
        Mockito.when(auditLogRepository.countByUsernamePrefixWithFilters(
                eq("OPS_RECOVERY_"), any(), eq("admin"), eq("OPS_RECOVERY_DRY_RUN")))
            .thenReturn(List.<Object[]>of(new Object[] {"admin", 3L}));

        mockMvc.perform(get("/api/v1/ops/recovery/history/summary/export")
                .param("days", "7")
                .param("mode", "DRY_RUN")
                .param("actor", "admin"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Ops-Recovery-Summary-Count", "2"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("section,key,mode,count")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("EVENT_TYPE")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("ACTOR")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("OPS_RECOVERY_DRY_RUN")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("admin")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can export ops recovery history CSV by actor filter")
    void exportHistoryCsvByActorForAdmin() throws Exception {
        AuditLog logEntry = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_DRY_RUN")
            .username("ops-admin")
            .eventTime(LocalDateTime.of(2026, 3, 7, 2, 5, 0))
            .details("domain=PREVIEW mode=QUEUE_BY_WINDOW estimatedQueued=3")
            .build();

        Mockito.when(auditLogRepository.findByEventTypePrefixSinceAndUsername(
                eq("OPS_RECOVERY_"), any(), eq("ops-admin"), any()))
            .thenReturn(new PageImpl<>(List.of(logEntry), PageRequest.of(0, 500), 1));

        mockMvc.perform(get("/api/v1/ops/recovery/history/export")
                .param("limit", "500")
                .param("days", "7")
                .param("actor", "ops-admin"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Ops-Recovery-Count", "1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("ops-admin")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("OPS_RECOVERY_DRY_RUN")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can export ops recovery history CSV by event type")
    void exportHistoryCsvByEventTypeForAdmin() throws Exception {
        AuditLog logEntry = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType("OPS_RECOVERY_HISTORY_EXPORT")
            .username("admin")
            .eventTime(LocalDateTime.of(2026, 3, 7, 3, 20, 0))
            .details("limit=500 days=7 mode=DRY_RUN actor=admin eventType=OPS_RECOVERY_DRY_RUN count=1")
            .build();

        Mockito.when(auditLogRepository.findByEventTypeAndEventTimeGreaterThanEqualOrderByEventTimeDesc(
                eq("OPS_RECOVERY_HISTORY_EXPORT"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(logEntry), PageRequest.of(0, 500), 1));

        mockMvc.perform(get("/api/v1/ops/recovery/history/export")
                .param("limit", "500")
                .param("days", "7")
                .param("eventType", "OPS_RECOVERY_HISTORY_EXPORT"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Ops-Recovery-Count", "1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("OPS_RECOVERY_HISTORY_EXPORT")));
    }

    private String startHistoryExportAsyncTask() throws Exception {
        MvcResult startResult = mockMvc.perform(post("/api/v1/ops/recovery/history/export-async")
                .contentType("application/json")
                .content("""
                    {
                      "exportType": "HISTORY",
                      "limit": 5,
                      "days": 7
                    }
                    """))
            .andExpect(status().isAccepted())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.taskId").isNotEmpty())
            .andReturn();
        return readTaskId(startResult);
    }

    private String readTaskId(MvcResult result) throws Exception {
        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        return payload.path("taskId").asText();
    }

    private String awaitHistoryExportAsyncTaskTerminalStatus(String taskId) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            MvcResult result = mockMvc.perform(get("/api/v1/ops/recovery/history/export-async/{taskId}", taskId))
                .andExpect(status().isOk())
                .andReturn();
            String statusValue = objectMapper.readTree(result.getResponse().getContentAsString()).path("status").asText();
            if ("COMPLETED".equals(statusValue)
                || "CANCELLED".equals(statusValue)
                || "FAILED".equals(statusValue)
                || "TIMED_OUT".equals(statusValue)
                || "EXPIRED".equals(statusValue)) {
                return statusValue;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        throw new AssertionError("Timed out waiting for async ops recovery history export task to reach terminal status");
    }
}
