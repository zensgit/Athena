package com.ecm.core.controller;

import com.ecm.core.entity.AuditLog;
import com.ecm.core.preview.PreviewDeadLetterRegistry;
import com.ecm.core.preview.PreviewQueueService;
import com.ecm.core.repository.AuditLogRepository;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.RenditionResourceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OpsRecoveryController.class)
@ContextConfiguration(classes = {
    OpsRecoveryController.class,
    OpsRecoveryControllerResponseContractTest.TestSecurityConfig.class
})
class OpsRecoveryControllerResponseContractTest {

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
    @WithMockUser(username = "ops-admin", roles = "ADMIN")
    @DisplayName("Ops recovery async export endpoints lock create, status, list, and summary DTO contracts")
    void historyExportAsyncEndpointsLockResponseContracts() throws Exception {
        AuditLog logEntry = AuditLog.builder()
            .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
            .eventType("OPS_RECOVERY_QUEUE_BY_REASON")
            .username("ops-admin")
            .eventTime(LocalDateTime.of(2026, 5, 23, 9, 30, 0))
            .details("domain=PREVIEW mode=QUEUE_BY_REASON queued=1 skipped=0 failed=0")
            .build();

        CountDownLatch enteredRepository = new CountDownLatch(1);
        CountDownLatch releaseRepository = new CountDownLatch(1);
        when(auditLogRepository.findByEventTypePrefixSince(eq("OPS_RECOVERY_"), any(), any()))
            .thenAnswer(invocation -> {
                enteredRepository.countDown();
                releaseRepository.await(3, TimeUnit.SECONDS);
                return new PageImpl<>(List.of(logEntry), PageRequest.of(0, 5), 1);
            });

        try {
            MvcResult createResult = mockMvc.perform(post("/api/v1/ops/recovery/history/export-async")
                    .contentType("application/json")
                    .content("""
                        {
                          "exportType": "HISTORY",
                          "limit": 5,
                          "days": 7,
                          "mode": "QUEUE_BY_REASON",
                          "actor": "ops-admin",
                          "eventType": "OPS_RECOVERY_QUEUE_BY_REASON"
                        }
                        """))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.taskId").isNotEmpty())
                .andExpect(jsonPath("$.exportType", is("HISTORY")))
                .andExpect(jsonPath("$.status", is("QUEUED")))
                .andExpect(jsonPath("$.request.exportType", is("HISTORY")))
                .andExpect(jsonPath("$.request.limit", is(5)))
                .andExpect(jsonPath("$.request.days", is(7)))
                .andExpect(jsonPath("$.request.mode", is("QUEUE_BY_REASON")))
                .andExpect(jsonPath("$.request.actor", is("ops-admin")))
                .andExpect(jsonPath("$.request.eventType", is("OPS_RECOVERY_QUEUE_BY_REASON")))
                .andExpect(jsonPath("$.deduplicated", is(false)))
                .andExpect(jsonPath("$.deduplicatedFromTaskId", nullValue()))
                .andExpect(jsonPath("$.message", is("Started async export task")))
                .andReturn();

            JsonNode createRoot = objectMapper.readTree(createResult.getResponse().getContentAsString());
            assertEquals(createResponseFieldNames(), fieldNames(createRoot));
            assertEquals(requestSnapshotFieldNames(), fieldNames(createRoot.get("request")));

            String taskId = createRoot.path("taskId").asText();
            assertFalse(taskId.isBlank());
            assertTrue(enteredRepository.await(3, TimeUnit.SECONDS));

            MvcResult statusResult = mockMvc.perform(get("/api/v1/ops/recovery/history/export-async/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId", is(taskId)))
                .andExpect(jsonPath("$.exportType", is("HISTORY")))
                .andExpect(jsonPath("$.status", is("RUNNING")))
                .andExpect(jsonPath("$.error", nullValue()))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.finishedAt", nullValue()))
                .andExpect(jsonPath("$.filename", nullValue()))
                .andExpect(jsonPath("$.createdBy", is("ops-admin")))
                .andExpect(jsonPath("$.updatedBy", is("ops-admin")))
                .andReturn();

            JsonNode statusRoot = objectMapper.readTree(statusResult.getResponse().getContentAsString());
            assertEquals(statusResponseFieldNames(), fieldNames(statusRoot));
            assertEquals(requestSnapshotFieldNames(), fieldNames(statusRoot.get("request")));

            MvcResult listResult = mockMvc.perform(get("/api/v1/ops/recovery/history/export-async")
                    .param("limit", "10")
                    .param("skipCount", "0")
                    .param("exportType", "HISTORY")
                    .param("status", "RUNNING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(1)))
                .andExpect(jsonPath("$.paging.skipCount", is(0)))
                .andExpect(jsonPath("$.paging.maxItems", is(10)))
                .andExpect(jsonPath("$.paging.totalItems", is(1)))
                .andExpect(jsonPath("$.paging.hasMoreItems", is(false)))
                .andExpect(jsonPath("$.items[0].taskId", is(taskId)))
                .andExpect(jsonPath("$.items[0].error", nullValue()))
                .andExpect(jsonPath("$.items[0].finishedAt", nullValue()))
                .andExpect(jsonPath("$.items[0].filename", nullValue()))
                .andReturn();

            JsonNode listRoot = objectMapper.readTree(listResult.getResponse().getContentAsString());
            assertEquals(listResponseFieldNames(), fieldNames(listRoot));
            assertEquals(pagingFieldNames(), fieldNames(listRoot.get("paging")));
            assertEquals(statusResponseFieldNames(), fieldNames(listRoot.get("items").get(0)));

            MvcResult summaryResult = mockMvc.perform(get("/api/v1/ops/recovery/history/export-async/summary")
                    .param("exportType", "HISTORY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.queuedCount", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.runningCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.completedCount", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.cancelledCount", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.failedCount", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.timedOutCount", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.expiredCount", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.activeCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.terminalCount", greaterThanOrEqualTo(0)))
                .andReturn();

            JsonNode summaryRoot = objectMapper.readTree(summaryResult.getResponse().getContentAsString());
            assertEquals(summaryFieldNames(), fieldNames(summaryRoot));
        } finally {
            releaseRepository.countDown();
        }
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> createResponseFieldNames() {
        return List.of(
            "taskId",
            "exportType",
            "status",
            "request",
            "createdAt",
            "timeoutAt",
            "expiresAt",
            "createdBy",
            "updatedBy",
            "deduplicated",
            "deduplicatedFromTaskId",
            "message"
        );
    }

    private static List<String> statusResponseFieldNames() {
        return List.of(
            "taskId",
            "exportType",
            "status",
            "error",
            "request",
            "createdAt",
            "startedAt",
            "updatedAt",
            "timeoutAt",
            "expiresAt",
            "finishedAt",
            "filename",
            "createdBy",
            "updatedBy"
        );
    }

    private static List<String> requestSnapshotFieldNames() {
        return List.of(
            "exportType",
            "limit",
            "days",
            "mode",
            "actor",
            "eventType",
            "compareBreakdownLimit",
            "compareBreakdownSort",
            "compareActorLimit",
            "compareActorSort"
        );
    }

    private static List<String> listResponseFieldNames() {
        return List.of("count", "paging", "items");
    }

    private static List<String> pagingFieldNames() {
        return List.of("skipCount", "maxItems", "totalItems", "hasMoreItems");
    }

    private static List<String> summaryFieldNames() {
        return List.of(
            "totalCount",
            "queuedCount",
            "runningCount",
            "completedCount",
            "cancelledCount",
            "failedCount",
            "timedOutCount",
            "expiredCount",
            "activeCount",
            "terminalCount"
        );
    }
}
