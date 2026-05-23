package com.ecm.core.controller;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.preview.CadRenderEndpointRegistry;
import com.ecm.core.preview.CadRenderFailoverTracker;
import com.ecm.core.preview.PreviewDeadLetterRegistry;
import com.ecm.core.preview.PreviewFailurePolicyRegistry;
import com.ecm.core.preview.PreviewPreflightResolver;
import com.ecm.core.preview.PreviewQueueService;
import com.ecm.core.preview.PreviewRenditionPreventionRegistry;
import com.ecm.core.preview.PreviewTransformTraceBuffer;
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
import org.springframework.data.domain.Pageable;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PreviewDiagnosticsController.class)
@ContextConfiguration(classes = {
    PreviewDiagnosticsController.class,
    PreviewDiagnosticsControllerResponseContractTest.TestSecurityConfig.class
})
class PreviewDiagnosticsControllerResponseContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private PreviewQueueService previewQueueService;

    @MockBean
    private CadRenderEndpointRegistry cadRenderEndpointRegistry;

    @MockBean
    private CadRenderFailoverTracker cadRenderFailoverTracker;

    @MockBean
    private PreviewTransformTraceBuffer previewTransformTraceBuffer;

    @MockBean
    private PreviewFailurePolicyRegistry previewFailurePolicyRegistry;

    @MockBean
    private PreviewPreflightResolver previewPreflightResolver;

    @MockBean
    private PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry;

    @MockBean
    private PreviewDeadLetterRegistry previewDeadLetterRegistry;

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
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /preview/diagnostics/failures locks PreviewFailureSampleDto field set")
    void recentFailuresLocksSampleContract() throws Exception {
        UUID documentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Document document = document(
            documentId,
            "broken.pdf",
            "/Root/Documents/broken.pdf",
            "application/pdf",
            PreviewStatus.FAILED
        );
        document.setPreviewFailureReason(null);
        document.setPreviewLastUpdated(null);

        when(documentRepository.findRecentPreviewFailuresByWindow(anyList(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 25), 1));

        MvcResult result = mockMvc.perform(get("/api/v1/preview/diagnostics/failures")
                .param("limit", "25")
                .param("days", "14"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(documentId.toString()))
            .andExpect(jsonPath("$[0].previewFailureReason", nullValue()))
            .andExpect(jsonPath("$[0].previewLastUpdated", nullValue()))
            .andReturn();

        JsonNode item = objectMapper.readTree(result.getResponse().getContentAsString()).get(0);
        assertEquals(previewFailureSampleFieldNames(), fieldNames(item));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /preview/diagnostics/failures/summary locks summary and nested count DTOs")
    void failureSummaryLocksSummaryContract() throws Exception {
        UUID documentId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Document document = document(
            documentId,
            "timeout.pdf",
            "/Root/Documents/timeout.pdf",
            "application/pdf",
            PreviewStatus.FAILED
        );
        document.setPreviewFailureReason("Timeout contacting preview service");
        document.setPreviewLastUpdated(LocalDateTime.of(2026, 5, 22, 8, 30, 0));

        when(documentRepository.countPreviewFailuresByWindow(anyList(), any())).thenReturn(1L);
        when(documentRepository.findRecentPreviewFailuresByWindow(anyList(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 100), 1));

        MvcResult result = mockMvc.perform(get("/api/v1/preview/diagnostics/failures/summary")
                .param("sampleLimit", "100")
                .param("days", "30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalFailures").value(1))
            .andExpect(jsonPath("$.sampledFailures").value(1))
            .andExpect(jsonPath("$.statusCounts[0].status").value("FAILED"))
            .andExpect(jsonPath("$.categoryCounts[0].category").value("TEMPORARY"))
            .andExpect(jsonPath("$.topReasons[0].reason").value("Timeout contacting preview service"))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(previewFailureSummaryFieldNames(), fieldNames(root));
        assertEquals(previewFailureStatusCountFieldNames(), fieldNames(root.get("statusCounts").get(0)));
        assertEquals(previewFailureCategoryCountFieldNames(), fieldNames(root.get("categoryCounts").get(0)));
        assertEquals(previewFailureReasonCountFieldNames(), fieldNames(root.get("topReasons").get(0)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /preview/diagnostics/queue/summary locks queue summary and item DTOs")
    void queueSummaryLocksSummaryAndItemContract() throws Exception {
        UUID documentId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        Instant nextAttemptAt = Instant.parse("2026-05-22T16:00:00Z");
        PreviewQueueService.PreviewQueueDiagnosticsSnapshot snapshot = new PreviewQueueService.PreviewQueueDiagnosticsSnapshot(
            "MEMORY",
            true,
            3L,
            2L,
            1L,
            true,
            0L,
            20,
            false,
            List.of(new PreviewQueueService.PreviewQueueDiagnosticsItem(
                documentId,
                documentId + "|preview|hash",
                2,
                nextAttemptAt,
                true,
                false
            ))
        );
        Document document = document(documentId, "queued.pdf", "/Root/queued.pdf", "application/pdf", PreviewStatus.PROCESSING);

        when(previewQueueService.diagnosticsSnapshot(20)).thenReturn(snapshot);
        when(documentRepository.findAllById(any())).thenReturn(List.of(document));

        MvcResult result = mockMvc.perform(get("/api/v1/preview/diagnostics/queue/summary")
                .param("limit", "20")
                .param("state", "RUNNING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.backend").value("MEMORY"))
            .andExpect(jsonPath("$.items[0].documentId").value(documentId.toString()))
            .andExpect(jsonPath("$.items[0].nextAttemptAt").value("2026-05-22T16:00:00Z"))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(previewQueueDiagnosticsSummaryFieldNames(), fieldNames(root));
        assertEquals(previewQueueDiagnosticsItemFieldNames(), fieldNames(root.get("items").get(0)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /preview/diagnostics/queue/cancel-active locks cancel response and result DTOs")
    void queueCancelActiveLocksResponseAndItemContract() throws Exception {
        UUID documentId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        PreviewQueueService.PreviewQueueDiagnosticsSnapshot snapshot = new PreviewQueueService.PreviewQueueDiagnosticsSnapshot(
            "MEMORY",
            true,
            1L,
            1L,
            1L,
            true,
            0L,
            20,
            false,
            List.of(new PreviewQueueService.PreviewQueueDiagnosticsItem(
                documentId,
                documentId + "|preview|hash",
                1,
                Instant.parse("2026-05-22T16:15:00Z"),
                true,
                false
            ))
        );
        Document document = document(documentId, "running.pdf", "/Root/running.pdf", "application/pdf", PreviewStatus.PROCESSING);

        when(previewQueueService.diagnosticsSnapshot(20)).thenReturn(snapshot);
        when(documentRepository.findAllById(any())).thenReturn(List.of(document));
        when(previewQueueService.cancel(documentId)).thenReturn(new PreviewQueueService.PreviewQueueCancellationStatus(
            documentId,
            "CANCEL_REQUESTED",
            true,
            true,
            true,
            "Cancellation requested"
        ));

        MvcResult result = mockMvc.perform(post("/api/v1/preview/diagnostics/queue/cancel-active")
                .param("limit", "20")
                .param("state", "RUNNING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cancelled").value(1))
            .andExpect(jsonPath("$.results[0].outcome").value("CANCELLED"))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(previewQueueCancelActiveResponseFieldNames(), fieldNames(root));
        assertEquals(previewQueueCancelActiveItemFieldNames(), fieldNames(root.get("results").get(0)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /preview/diagnostics/queue/declined locks declined summary and item DTOs")
    void queueDeclinedLocksSummaryAndItemContract() throws Exception {
        UUID documentId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = new PreviewQueueService.PreviewQueueDeclinedSnapshot(
            true,
            1L,
            50,
            false,
            List.of(new PreviewQueueService.PreviewQueueDeclinedItem(
                documentId,
                documentId + "|preview|hash",
                PreviewStatus.FAILED,
                "Within quiet period",
                "QUIET_PERIOD",
                Instant.parse("2026-05-22T15:00:00Z"),
                null,
                false
            ))
        );
        Document document = document(documentId, "declined.pdf", "/Root/declined.pdf", "application/pdf", PreviewStatus.FAILED);

        when(previewQueueService.declinedSnapshot(50)).thenReturn(snapshot);
        when(documentRepository.findAllById(any())).thenReturn(List.of(document));

        MvcResult result = mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined")
                .param("limit", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoryCounts[0].category").value("QUIET_PERIOD"))
            .andExpect(jsonPath("$.items[0].documentId").value(documentId.toString()))
            .andExpect(jsonPath("$.items[0].nextEligibleAt", nullValue()))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(previewQueueDeclinedSummaryFieldNames(), fieldNames(root));
        assertEquals(previewQueueDeclinedCategoryCountFieldNames(), fieldNames(root.get("categoryCounts").get(0)));
        assertEquals(previewQueueDeclinedItemFieldNames(), fieldNames(root.get("items").get(0)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /preview/diagnostics/queue/declined/export-async locks create response DTO")
    void queueDeclinedExportAsyncCreateLocksResponseContract() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/export-async")
                .contentType("application/json")
                .content("{\"limit\":50,\"category\":\"QUIET_PERIOD\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.taskId").exists())
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andExpect(jsonPath("$.deduplicated").value(false))
            .andExpect(jsonPath("$.deduplicatedFromTaskId", nullValue()))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(previewQueueDeclinedExportAsyncCreateResponseFieldNames(), fieldNames(root));
    }

    private static Document document(UUID id, String name, String path, String mimeType, PreviewStatus status) {
        Document document = new Document();
        document.setId(id);
        document.setName(name);
        document.setPath(path);
        document.setMimeType(mimeType);
        document.setPreviewStatus(status);
        return document;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> previewFailureSampleFieldNames() {
        return List.of("id", "name", "path", "mimeType", "previewStatus", "previewFailureReason", "previewFailureCategory", "previewLastUpdated");
    }

    private static List<String> previewFailureSummaryFieldNames() {
        return List.of("totalFailures", "sampledFailures", "sampleLimit", "windowDays", "windowStart", "sampleTruncated", "confidenceLevel", "confidenceReason", "statusCounts", "categoryCounts", "topReasons");
    }

    private static List<String> previewFailureStatusCountFieldNames() {
        return List.of("status", "count");
    }

    private static List<String> previewFailureCategoryCountFieldNames() {
        return List.of("category", "retryable", "count");
    }

    private static List<String> previewFailureReasonCountFieldNames() {
        return List.of("reason", "category", "retryable", "count");
    }

    private static List<String> previewQueueDiagnosticsSummaryFieldNames() {
        return List.of("backend", "queueEnabled", "scheduledCount", "governanceCount", "runningCount", "runningCountAccurate", "cancellationRequestedCount", "sampleLimit", "sampleTruncated", "stateFilter", "queryFilter", "totalSampledItems", "filteredSampledItems", "items");
    }

    private static List<String> previewQueueDiagnosticsItemFieldNames() {
        return List.of("documentId", "name", "path", "mimeType", "previewStatus", "previewFailureReason", "previewFailureCategory", "previewLastUpdated", "queueState", "governanceKey", "attempts", "nextAttemptAt", "running", "cancelRequested");
    }

    private static List<String> previewQueueCancelActiveResponseFieldNames() {
        return List.of("stateFilter", "queryFilter", "limit", "requested", "cancelled", "skipped", "failed", "results");
    }

    private static List<String> previewQueueCancelActiveItemFieldNames() {
        return List.of("documentId", "previewStatus", "previewFailureReason", "previewFailureCategory", "previewLastUpdated", "queueState", "outcome", "message");
    }

    private static List<String> previewQueueDeclinedSummaryFieldNames() {
        return List.of("queueEnabled", "totalDeclined", "sampleLimit", "sampleTruncated", "categoryFilter", "forceRequiredFilter", "queryFilter", "windowHoursFilter", "totalSampledItems", "filteredSampledItems", "forceRequiredCount", "categoryCounts", "items");
    }

    private static List<String> previewQueueDeclinedCategoryCountFieldNames() {
        return List.of("category", "count", "forceRequiredCount");
    }

    private static List<String> previewQueueDeclinedItemFieldNames() {
        return List.of("documentId", "name", "path", "mimeType", "previewStatus", "previewFailureReason", "previewFailureCategory", "previewLastUpdated", "reason", "category", "governanceKey", "declinedAt", "nextEligibleAt", "forceRequired");
    }

    private static List<String> previewQueueDeclinedExportAsyncCreateResponseFieldNames() {
        return List.of("taskId", "status", "createdAt", "timeoutAt", "expiresAt", "createdBy", "updatedBy", "deduplicated", "deduplicatedFromTaskId", "message");
    }
}
