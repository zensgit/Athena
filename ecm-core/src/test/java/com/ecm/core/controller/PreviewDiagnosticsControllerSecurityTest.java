package com.ecm.core.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
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

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PreviewDiagnosticsController.class)
@ContextConfiguration(classes = {
    PreviewDiagnosticsController.class,
    PreviewDiagnosticsControllerSecurityTest.TestSecurityConfig.class
})
class PreviewDiagnosticsControllerSecurityTest {

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
    @WithMockUser(roles = "USER")
    @DisplayName("Preview diagnostics requires admin role")
    void diagnosticsRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/preview/diagnostics/failures"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/failures/summary"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/failures/ledger"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/failures/ledger/11111111-1111-1111-1111-111111111111/reset"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/failures/ledger/reset-batch")
                .contentType("application/json")
                .content("{\"documentIds\":[]}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/failures/ledger/reset-by-filter")
                .contentType("application/json")
                .content("{\"reason\":\"Timeout contacting preview service\",\"category\":\"TEMPORARY\",\"retryable\":true}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/failures/ledger/export"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/summary"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/resources"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/resources/export"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async")
                .contentType("application/json")
                .content("{\"days\":7,\"limit\":500}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/resources/export-async"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/resources/export-async/summary"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/cleanup"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/cancel-active"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/retry-terminal")
                .param("status", "FAILED")
                .param("limit", "20"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/retry-terminal/by-task-ids")
                .contentType("application/json")
                .content("{\"sourceTaskIds\":[\"task-1\"]}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/resources/export-async/non-existent-task"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/non-existent-task/cancel"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/non-existent-task/retry"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/resources/export-async/non-existent-task/download"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/cad-failover"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/traces"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/policies"))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/preview/diagnostics/policies/default")
                .contentType("application/json")
                .content("{\"maxAttempts\":3}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/failures/queue-batch")
                .contentType("application/json")
                .content("{\"documentIds\":[],\"force\":false}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/failures/queue-by-reason")
                .contentType("application/json")
                .content("{\"reason\":\"Timeout contacting preview service\",\"category\":\"TEMPORARY\",\"retryable\":true}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/summary"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/summary/export"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/cancel-active"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/export"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/export-async")
                .contentType("application/json")
                .content("{\"limit\":500,\"category\":\"QUIET_PERIOD\",\"forceRequired\":\"NO\",\"query\":\"quiet\",\"windowHours\":24}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/export-async"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/export-async/summary"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/export-async/cleanup"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/export-async/cancel-active"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/export-async/non-existent-task"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/export-async/non-existent-task/cancel"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/export-async/non-existent-task/retry"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/export-async/retry-terminal")
                .param("status", "FAILED")
                .param("limit", "20"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/export-async/retry-terminal/dry-run")
                .param("status", "FAILED")
                .param("limit", "20"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/export-async/retry-terminal/dry-run/export")
                .param("status", "FAILED")
                .param("limit", "20"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/export-async/retry-terminal/by-task-ids")
                .contentType("application/json")
                .content("{\"sourceTaskIds\":[\"task-1\"]}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/export-async/non-existent-task/download"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async")
                .contentType("application/json")
                .content("{\"limit\":200,\"category\":\"QUIET_PERIOD\",\"forceRequired\":\"NO\",\"query\":\"quiet\",\"windowHours\":24,\"force\":false}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/summary"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/cleanup"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/cancel-active"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/non-existent-task"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/non-existent-task/cancel"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/non-existent-task/retry"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/retry-terminal")
                .param("status", "FAILED")
                .param("limit", "20"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/retry-terminal/dry-run")
                .param("status", "FAILED")
                .param("limit", "20"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/retry-terminal/dry-run/export")
                .param("status", "FAILED")
                .param("limit", "20"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/retry-terminal/by-task-ids")
                .contentType("application/json")
                .content("{\"sourceTaskIds\":[\"task-1\"]}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/non-existent-task/download"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/clear"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/prevention/blocked"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/prevention/11111111-1111-1111-1111-111111111111/unblock"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/prevention/11111111-1111-1111-1111-111111111111/unblock-requeue"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/prevention/unblock-batch")
                .contentType("application/json")
                .content("{\"documentIds\":[]}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/prevention/unblock-requeue-batch")
                .contentType("application/json")
                .content("{\"documentIds\":[],\"force\":true}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/dead-letter"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/preview/diagnostics/dead-letter/export"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/dead-letter/replay-batch")
                .contentType("application/json")
                .content("{\"documentIds\":[],\"force\":true}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preview/diagnostics/dead-letter/clear-batch")
                .contentType("application/json")
                .content("{\"entryKeys\":[]}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can access preview diagnostics and categories are derived")
    void diagnosticsAllowsAdmin() throws Exception {
        UUID docId = UUID.randomUUID();
        LocalDateTime updated = LocalDateTime.of(2026, 2, 13, 12, 34, 56);

        Document document = new Document();
        document.setId(docId);
        document.setName("broken.pdf");
        document.setPath("/Root/Documents/broken.pdf");
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("Error generating preview: Missing root object specification in trailer.");
        document.setPreviewLastUpdated(updated);

        Mockito.when(documentRepository.findRecentPreviewFailuresByWindow(anyList(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 50), 1));

        mockMvc.perform(get("/api/v1/preview/diagnostics/failures?limit=50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id", is(docId.toString())))
            .andExpect(jsonPath("$[0].name", is("broken.pdf")))
            .andExpect(jsonPath("$[0].path", is("/Root/Documents/broken.pdf")))
            .andExpect(jsonPath("$[0].mimeType", is("application/pdf")))
            .andExpect(jsonPath("$[0].previewStatus", is("FAILED")))
            .andExpect(jsonPath("$[0].previewFailureCategory", is("PERMANENT")))
            .andExpect(jsonPath("$[0].previewFailureReason", is("Error generating preview: Missing root object specification in trailer.")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Recent preview failures prefer rendition-backed effective semantics")
    void diagnosticsRecentFailuresPreferRenditionSummary() throws Exception {
        UUID docId = UUID.randomUUID();
        LocalDateTime updated = LocalDateTime.of(2026, 3, 28, 10, 20, 30);

        Document document = new Document();
        document.setId(docId);
        document.setName("unsupported.bin");
        document.setPath("/Root/Documents/unsupported.bin");
        document.setMimeType("application/octet-stream");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("Preview generation failed");
        document.setPreviewLastUpdated(updated);

        Mockito.when(documentRepository.findRecentPreviewFailuresByWindow(anyList(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 50), 1));
        Mockito.when(renditionResourceService.summarizeDocument(document)).thenReturn(
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

        mockMvc.perform(get("/api/v1/preview/diagnostics/failures?limit=50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].previewStatus", is("UNSUPPORTED")))
            .andExpect(jsonPath("$[0].previewFailureCategory", is("UNSUPPORTED")))
            .andExpect(jsonPath("$[0].previewFailureReason", is("Preview definition is not registered for generic binary sources")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can access preview failure ledger diagnostics")
    void diagnosticsFailureLedgerAllowsAdmin() throws Exception {
        UUID docId = UUID.randomUUID();
        LocalDateTime failedAt = LocalDateTime.of(2026, 3, 9, 10, 11, 12);

        Document document = new Document();
        document.setId(docId);
        document.setName("ledger-failed.pdf");
        document.setPath("/Root/Documents/ledger-failed.pdf");
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureCount(3);
        document.setPreviewFailedAt(failedAt);
        document.setPreviewLastFailureReason("Timeout contacting preview service");
        document.setPreviewFailureContentHash("abc123");
        document.setContentHash("abc123");
        document.setPreviewLastUpdated(failedAt);

        Mockito.when(documentRepository.countPreviewFailureLedgerEntries(any()))
            .thenReturn(1L);
        Mockito.when(documentRepository.findPreviewFailureLedgerEntries(any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 100), 1));

        mockMvc.perform(get("/api/v1/preview/diagnostics/failures/ledger?limit=100&days=30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalEntries", is(1)))
            .andExpect(jsonPath("$.sampledEntries", is(1)))
            .andExpect(jsonPath("$.windowDays", is(30)))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].documentId", is(docId.toString())))
            .andExpect(jsonPath("$.items[0].name", is("ledger-failed.pdf")))
            .andExpect(jsonPath("$.items[0].failureCount", is(3)))
            .andExpect(jsonPath("$.items[0].lastReason", is("Timeout contacting preview service")))
            .andExpect(jsonPath("$.items[0].category", is("TEMPORARY")))
            .andExpect(jsonPath("$.items[0].retryable", is(true)))
            .andExpect(jsonPath("$.items[0].staleByContentChange", is(false)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Preview failure ledger exposes effective unsupported preview semantics")
    void diagnosticsFailureLedgerUsesEffectiveUnsupportedPreviewSemantics() throws Exception {
        UUID docId = UUID.randomUUID();
        LocalDateTime failedAt = LocalDateTime.of(2026, 3, 11, 9, 10, 11);

        Document document = new Document();
        document.setId(docId);
        document.setName("ledger-unsupported.bin");
        document.setPath("/Root/Documents/ledger-unsupported.bin");
        document.setMimeType("application/octet-stream");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureCount(1);
        document.setPreviewFailedAt(failedAt);
        document.setPreviewLastFailureReason("Preview not supported for mime type");
        document.setPreviewFailureContentHash("deadbeef");
        document.setContentHash("deadbeef");
        document.setPreviewLastUpdated(failedAt);

        Mockito.when(documentRepository.countPreviewFailureLedgerEntries(any()))
            .thenReturn(1L);
        Mockito.when(documentRepository.findPreviewFailureLedgerEntries(any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 100), 1));

        mockMvc.perform(get("/api/v1/preview/diagnostics/failures/ledger?limit=100&days=30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].previewStatus", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.items[0].lastReason", is("Preview not supported for mime type")))
            .andExpect(jsonPath("$.items[0].category", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.items[0].retryable", is(false)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can access preview queue diagnostics summary")
    void diagnosticsQueueSummaryAllowsAdmin() throws Exception {
        UUID runningDocumentId = UUID.randomUUID();
        UUID queuedDocumentId = UUID.randomUUID();
        Instant nextAttemptAt = Instant.parse("2026-03-10T12:34:56Z");
        PreviewQueueService.PreviewQueueDiagnosticsSnapshot snapshot = new PreviewQueueService.PreviewQueueDiagnosticsSnapshot(
            "MEMORY",
            true,
            2L,
            2L,
            1L,
            true,
            1L,
            20,
            false,
            List.of(
                new PreviewQueueService.PreviewQueueDiagnosticsItem(
                    runningDocumentId,
                    runningDocumentId + "|preview|hash-a",
                    1,
                    nextAttemptAt,
                    true,
                    false
                ),
                new PreviewQueueService.PreviewQueueDiagnosticsItem(
                    queuedDocumentId,
                    queuedDocumentId + "|preview|hash-b",
                    0,
                    nextAttemptAt.plusSeconds(30),
                    false,
                    true
                )
            )
        );
        Document runningDocument = new Document();
        runningDocument.setId(runningDocumentId);
        runningDocument.setName("queue-running.pdf");
        runningDocument.setPath("/Root/Documents/queue-running.pdf");
        runningDocument.setMimeType("application/pdf");
        runningDocument.setPreviewStatus(PreviewStatus.PROCESSING);

        Document queuedDocument = new Document();
        queuedDocument.setId(queuedDocumentId);
        queuedDocument.setName("queue-pending.pdf");
        queuedDocument.setPath("/Root/Documents/queue-pending.pdf");
        queuedDocument.setMimeType("application/pdf");
        queuedDocument.setPreviewStatus(PreviewStatus.FAILED);

        Mockito.when(previewQueueService.diagnosticsSnapshot(20)).thenReturn(snapshot);
        Mockito.when(previewQueueService.diagnosticsSnapshot(200)).thenReturn(snapshot);
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(runningDocument, queuedDocument));

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/summary?limit=20&state=RUNNING&query=running"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.backend", is("MEMORY")))
            .andExpect(jsonPath("$.queueEnabled", is(true)))
            .andExpect(jsonPath("$.scheduledCount", is(2)))
            .andExpect(jsonPath("$.governanceCount", is(2)))
            .andExpect(jsonPath("$.runningCount", is(1)))
            .andExpect(jsonPath("$.runningCountAccurate", is(true)))
            .andExpect(jsonPath("$.cancellationRequestedCount", is(1)))
            .andExpect(jsonPath("$.sampleLimit", is(20)))
            .andExpect(jsonPath("$.sampleTruncated", is(false)))
            .andExpect(jsonPath("$.stateFilter", is("RUNNING")))
            .andExpect(jsonPath("$.queryFilter", is("running")))
            .andExpect(jsonPath("$.totalSampledItems", is(2)))
            .andExpect(jsonPath("$.filteredSampledItems", is(1)))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].documentId", is(runningDocumentId.toString())))
            .andExpect(jsonPath("$.items[0].name", is("queue-running.pdf")))
            .andExpect(jsonPath("$.items[0].path", is("/Root/Documents/queue-running.pdf")))
            .andExpect(jsonPath("$.items[0].mimeType", is("application/pdf")))
            .andExpect(jsonPath("$.items[0].previewStatus", is("PROCESSING")))
            .andExpect(jsonPath("$.items[0].queueState", is("RUNNING")))
            .andExpect(jsonPath("$.items[0].attempts", is(1)))
            .andExpect(jsonPath("$.items[0].running", is(true)))
            .andExpect(jsonPath("$.items[0].cancelRequested", is(false)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Queue diagnostics summary prefers rendition-backed preview status")
    void diagnosticsQueueSummaryPrefersRenditionSummary() throws Exception {
        UUID documentId = UUID.randomUUID();
        Instant nextAttemptAt = Instant.parse("2026-03-10T12:34:56Z");
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
            List.of(
                new PreviewQueueService.PreviewQueueDiagnosticsItem(
                    documentId,
                    documentId + "|preview|hash-a",
                    1,
                    nextAttemptAt,
                    true,
                    false
                )
            )
        );

        Document document = new Document();
        document.setId(documentId);
        document.setName("queue-running.bin");
        document.setPath("/Root/Documents/queue-running.bin");
        document.setMimeType("application/octet-stream");
        document.setPreviewStatus(PreviewStatus.FAILED);

        Mockito.when(previewQueueService.diagnosticsSnapshot(20)).thenReturn(snapshot);
        Mockito.when(previewQueueService.diagnosticsSnapshot(200)).thenReturn(snapshot);
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(document));
        Mockito.when(renditionResourceService.summarizeDocument(document)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                documentId,
                true,
                "UNSUPPORTED",
                false,
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                LocalDateTime.of(2026, 3, 28, 12, 0, 0),
                null
            )
        );

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/summary?limit=20&state=RUNNING&query=running"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].previewStatus", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.items[0].previewFailureReason", is("Preview definition is not registered for generic binary sources")))
            .andExpect(jsonPath("$.items[0].previewFailureCategory", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.items[0].previewLastUpdated", is("2026-03-28T12:00:00")));

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/summary/export?limit=200&state=RUNNING"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("previewFailureReason,previewFailureCategory,previewLastUpdated")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("Preview definition is not registered for generic binary sources")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("UNSUPPORTED")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can export preview queue diagnostics summary CSV")
    void diagnosticsQueueSummaryExportAllowsAdmin() throws Exception {
        UUID queuedDocumentId = UUID.randomUUID();
        UUID runningDocumentId = UUID.randomUUID();
        PreviewQueueService.PreviewQueueDiagnosticsSnapshot snapshot = new PreviewQueueService.PreviewQueueDiagnosticsSnapshot(
            "MEMORY",
            true,
            2L,
            2L,
            1L,
            true,
            1L,
            200,
            false,
            List.of(
                new PreviewQueueService.PreviewQueueDiagnosticsItem(
                    queuedDocumentId,
                    queuedDocumentId + "|preview|hash-export",
                    0,
                    Instant.parse("2026-03-10T00:00:00Z"),
                    false,
                    true
                ),
                new PreviewQueueService.PreviewQueueDiagnosticsItem(
                    runningDocumentId,
                    runningDocumentId + "|preview|hash-running",
                    1,
                    Instant.parse("2026-03-10T00:01:00Z"),
                    true,
                    false
                )
            )
        );
        Document queuedDocument = new Document();
        queuedDocument.setId(queuedDocumentId);
        queuedDocument.setName("queue-export.bin");
        queuedDocument.setPath("/Root/Documents/queue-export.bin");
        queuedDocument.setMimeType("application/octet-stream");
        queuedDocument.setPreviewStatus(PreviewStatus.FAILED);

        Document runningDocument = new Document();
        runningDocument.setId(runningDocumentId);
        runningDocument.setName("queue-running.pdf");
        runningDocument.setPath("/Root/Documents/queue-running.pdf");
        runningDocument.setMimeType("application/pdf");
        runningDocument.setPreviewStatus(PreviewStatus.PROCESSING);

        Mockito.when(previewQueueService.diagnosticsSnapshot(200)).thenReturn(snapshot);
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(queuedDocument, runningDocument));

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/summary/export?limit=200&state=CANCEL_REQUESTED&query=queue-export"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Preview-Queue-Item-Count", "1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("text/csv;charset=UTF-8"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString(queuedDocumentId.toString())))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("queue-export.bin")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("CANCEL_REQUESTED")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("hash-export")));

        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_DIAGNOSTICS_EXPORTED"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            eq("user"),
            Mockito.contains("exported=1")
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can cancel active preview queue tasks by filter")
    void diagnosticsQueueCancelActiveAllowsAdmin() throws Exception {
        UUID runningDocumentId = UUID.randomUUID();
        UUID queuedDocumentId = UUID.randomUUID();
        Instant nextAttemptAt = Instant.parse("2026-03-10T12:34:56Z");
        PreviewQueueService.PreviewQueueDiagnosticsSnapshot snapshot = new PreviewQueueService.PreviewQueueDiagnosticsSnapshot(
            "MEMORY",
            true,
            2L,
            2L,
            1L,
            true,
            1L,
            200,
            false,
            List.of(
                new PreviewQueueService.PreviewQueueDiagnosticsItem(
                    runningDocumentId,
                    runningDocumentId + "|preview|hash-running",
                    1,
                    nextAttemptAt,
                    true,
                    false
                ),
                new PreviewQueueService.PreviewQueueDiagnosticsItem(
                    queuedDocumentId,
                    queuedDocumentId + "|preview|hash-queued",
                    0,
                    nextAttemptAt.plusSeconds(30),
                    false,
                    false
                )
            )
        );
        Document runningDocument = new Document();
        runningDocument.setId(runningDocumentId);
        runningDocument.setName("queue-running.pdf");
        runningDocument.setPath("/Root/Documents/queue-running.pdf");
        runningDocument.setMimeType("application/pdf");
        runningDocument.setPreviewStatus(PreviewStatus.PROCESSING);

        Document queuedDocument = new Document();
        queuedDocument.setId(queuedDocumentId);
        queuedDocument.setName("queue-queued.pdf");
        queuedDocument.setPath("/Root/Documents/queue-queued.pdf");
        queuedDocument.setMimeType("application/pdf");
        queuedDocument.setPreviewStatus(PreviewStatus.FAILED);

        Mockito.when(previewQueueService.diagnosticsSnapshot(200)).thenReturn(snapshot);
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(runningDocument, queuedDocument));
        Mockito.when(renditionResourceService.summarizeDocument(runningDocument)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                runningDocumentId,
                true,
                "PROCESSING",
                false,
                null,
                null,
                LocalDateTime.of(2026, 3, 29, 10, 15, 0),
                null
            )
        );
        Mockito.when(previewQueueService.cancel(runningDocumentId)).thenReturn(
            new PreviewQueueService.PreviewQueueCancellationStatus(
                runningDocumentId,
                "CANCEL_REQUESTED",
                true,
                true,
                true,
                "Cancellation requested for running preview task"
            )
        );

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/cancel-active?state=RUNNING&query=running&limit=200"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stateFilter", is("RUNNING")))
            .andExpect(jsonPath("$.queryFilter", is("running")))
            .andExpect(jsonPath("$.limit", is(200)))
            .andExpect(jsonPath("$.requested", is(1)))
            .andExpect(jsonPath("$.cancelled", is(1)))
            .andExpect(jsonPath("$.skipped", is(0)))
            .andExpect(jsonPath("$.failed", is(0)))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].documentId", is(runningDocumentId.toString())))
            .andExpect(jsonPath("$.results[0].previewStatus", is("PROCESSING")))
            .andExpect(jsonPath("$.results[0].previewFailureReason").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.results[0].previewFailureCategory").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.results[0].previewLastUpdated", is("2026-03-29T10:15:00")))
            .andExpect(jsonPath("$.results[0].queueState", is("CANCEL_REQUESTED")))
            .andExpect(jsonPath("$.results[0].outcome", is("CANCELLED")));

        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_CANCEL_ACTIVE"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            eq("user"),
            Mockito.contains("cancelled=1")
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can access and export declined preview queue diagnostics")
    void diagnosticsQueueDeclinedAllowsAdmin() throws Exception {
        UUID quietPeriodDocumentId = UUID.randomUUID();
        UUID permanentDocumentId = UUID.randomUUID();
        Instant now = Instant.now().minusSeconds(2L * 3600L);

        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = new PreviewQueueService.PreviewQueueDeclinedSnapshot(
            true,
            2L,
            50,
            false,
            List.of(
                new PreviewQueueService.PreviewQueueDeclinedItem(
                    quietPeriodDocumentId,
                    quietPeriodDocumentId + "|preview|hash-quiet",
                    PreviewStatus.FAILED,
                    "Within quiet period for policy: default",
                    "QUIET_PERIOD",
                    now,
                    now.plusSeconds(120),
                    false
                ),
                new PreviewQueueService.PreviewQueueDeclinedItem(
                    permanentDocumentId,
                    permanentDocumentId + "|preview|hash-permanent",
                    PreviewStatus.FAILED,
                    "Preview failed permanently; use force=true to rebuild",
                    "PERMANENT_FAILURE",
                    now.minusSeconds(26L * 3600L),
                    null,
                    true
                )
            )
        );

        Document quietPeriodDocument = new Document();
        quietPeriodDocument.setId(quietPeriodDocumentId);
        quietPeriodDocument.setName("queue-declined-quiet.pdf");
        quietPeriodDocument.setPath("/Root/Documents/queue-declined-quiet.pdf");
        quietPeriodDocument.setMimeType("application/pdf");
        quietPeriodDocument.setPreviewStatus(PreviewStatus.FAILED);

        Document permanentDocument = new Document();
        permanentDocument.setId(permanentDocumentId);
        permanentDocument.setName("queue-declined-permanent.pdf");
        permanentDocument.setPath("/Root/Documents/queue-declined-permanent.pdf");
        permanentDocument.setMimeType("application/pdf");
        permanentDocument.setPreviewStatus(PreviewStatus.FAILED);

        Mockito.when(previewQueueService.declinedSnapshot(50)).thenReturn(snapshot);
        Mockito.when(previewQueueService.declinedSnapshot(500)).thenReturn(snapshot);
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(quietPeriodDocument, permanentDocument));

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined?limit=50&category=QUIET_PERIOD&query=quiet&windowHours=24"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queueEnabled", is(true)))
            .andExpect(jsonPath("$.totalDeclined", is(2)))
            .andExpect(jsonPath("$.sampleLimit", is(50)))
            .andExpect(jsonPath("$.sampleTruncated", is(false)))
            .andExpect(jsonPath("$.categoryFilter", is("QUIET_PERIOD")))
            .andExpect(jsonPath("$.forceRequiredFilter", is("ANY")))
            .andExpect(jsonPath("$.queryFilter", is("quiet")))
            .andExpect(jsonPath("$.windowHoursFilter", is(24)))
            .andExpect(jsonPath("$.totalSampledItems", is(2)))
            .andExpect(jsonPath("$.filteredSampledItems", is(1)))
            .andExpect(jsonPath("$.forceRequiredCount", is(0)))
            .andExpect(jsonPath("$.categoryCounts", hasSize(1)))
            .andExpect(jsonPath("$.categoryCounts[0].category", is("QUIET_PERIOD")))
            .andExpect(jsonPath("$.categoryCounts[0].count", is(1)))
            .andExpect(jsonPath("$.categoryCounts[0].forceRequiredCount", is(0)))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].documentId", is(quietPeriodDocumentId.toString())))
            .andExpect(jsonPath("$.items[0].name", is("queue-declined-quiet.pdf")))
            .andExpect(jsonPath("$.items[0].category", is("QUIET_PERIOD")))
            .andExpect(jsonPath("$.items[0].forceRequired", is(false)));

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined?limit=50&forceRequired=NO&windowHours=9999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoryFilter", is("ANY")))
            .andExpect(jsonPath("$.forceRequiredFilter", is("NO")))
            .andExpect(jsonPath("$.windowHoursFilter", is(720)))
            .andExpect(jsonPath("$.totalSampledItems", is(2)))
            .andExpect(jsonPath("$.filteredSampledItems", is(1)))
            .andExpect(jsonPath("$.forceRequiredCount", is(0)))
            .andExpect(jsonPath("$.categoryCounts", hasSize(1)))
            .andExpect(jsonPath("$.categoryCounts[0].category", is("QUIET_PERIOD")))
            .andExpect(jsonPath("$.categoryCounts[0].count", is(1)))
            .andExpect(jsonPath("$.categoryCounts[0].forceRequiredCount", is(0)))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].documentId", is(quietPeriodDocumentId.toString())))
            .andExpect(jsonPath("$.items[0].name", is("queue-declined-quiet.pdf")))
            .andExpect(jsonPath("$.items[0].category", is("QUIET_PERIOD")))
            .andExpect(jsonPath("$.items[0].forceRequired", is(false)));

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined?limit=50&category=PERMANENT_FAILURE&windowHours=24"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowHoursFilter", is(24)))
            .andExpect(jsonPath("$.filteredSampledItems", is(0)))
            .andExpect(jsonPath("$.items", hasSize(0)));

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/export?limit=500&category=PERMANENT_FAILURE&forceRequired=YES&query=permanent&windowHours=48"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Preview-Queue-Declined-Count", "1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("text/csv;charset=UTF-8"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("categoryFilter,forceRequiredFilter,queryFilter,windowHoursFilter")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("\"PERMANENT_FAILURE\",\"YES\",\"permanent\",\"48\"")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString(permanentDocumentId.toString())))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("PERMANENT_FAILURE")));

        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_DECLINED_EXPORTED"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            eq("user"),
            Mockito.contains("exported=1")
        );
        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_DECLINED_EXPORTED"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            eq("user"),
            Mockito.contains("windowHours=48")
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Queue declined summary and export prefer rendition-backed preview status")
    void diagnosticsQueueDeclinedPrefersRenditionSummary() throws Exception {
        UUID documentId = UUID.randomUUID();
        Instant now = Instant.parse("2026-03-28T12:00:00Z");

        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = new PreviewQueueService.PreviewQueueDeclinedSnapshot(
            true,
            1L,
            50,
            false,
            List.of(
                new PreviewQueueService.PreviewQueueDeclinedItem(
                    documentId,
                    documentId + "|preview|hash-binary",
                    PreviewStatus.FAILED,
                    "Preview definition is not registered for generic binary sources",
                    "UNSUPPORTED_SOURCE",
                    now,
                    now.plusSeconds(120),
                    false
                )
            )
        );

        Document document = new Document();
        document.setId(documentId);
        document.setName("queue-declined.bin");
        document.setPath("/Root/Documents/queue-declined.bin");
        document.setMimeType("application/octet-stream");
        document.setPreviewStatus(PreviewStatus.FAILED);

        Mockito.when(previewQueueService.declinedSnapshot(50)).thenReturn(snapshot);
        Mockito.when(previewQueueService.declinedSnapshot(500)).thenReturn(
            new PreviewQueueService.PreviewQueueDeclinedSnapshot(
                true,
                1L,
                500,
                false,
                snapshot.items()
            )
        );
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(document));
        Mockito.when(renditionResourceService.summarizeDocument(document)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                documentId,
                true,
                "UNSUPPORTED",
                false,
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                LocalDateTime.of(2026, 3, 28, 12, 0, 0),
                null
            )
        );

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined?limit=50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].previewStatus", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.items[0].previewFailureReason", is("Preview definition is not registered for generic binary sources")))
            .andExpect(jsonPath("$.items[0].previewFailureCategory", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.items[0].previewLastUpdated", is("2026-03-28T12:00:00")));

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/export?limit=500"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("previewFailureReason,previewFailureCategory,previewLastUpdated")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("Preview definition is not registered for generic binary sources")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("UNSUPPORTED")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can manage declined queue async export tasks")
    void diagnosticsQueueDeclinedAsyncExportTaskCenterForAdmin() throws Exception {
        UUID documentId = UUID.randomUUID();
        Instant declinedAt = Instant.now().minusSeconds(90L * 60L);
        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = new PreviewQueueService.PreviewQueueDeclinedSnapshot(
            true,
            1L,
            500,
            false,
            List.of(
                new PreviewQueueService.PreviewQueueDeclinedItem(
                    documentId,
                    documentId + "|preview|hash-quiet",
                    PreviewStatus.FAILED,
                    "Within quiet period for policy: default",
                    "QUIET_PERIOD",
                    declinedAt,
                    declinedAt.plusSeconds(120),
                    false
                )
            )
        );

        Document document = new Document();
        document.setId(documentId);
        document.setName("queue-declined-async.pdf");
        document.setPath("/Root/Documents/queue-declined-async.pdf");
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);

        CountDownLatch enteredSnapshot = new CountDownLatch(1);
        CountDownLatch releaseSnapshot = new CountDownLatch(1);
        Mockito.when(previewQueueService.declinedSnapshot(500))
            .thenAnswer(invocation -> {
                enteredSnapshot.countDown();
                releaseSnapshot.await(3, TimeUnit.SECONDS);
                return snapshot;
            })
            .thenReturn(snapshot)
            .thenThrow(new RuntimeException("simulated async export failure"));
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(document));

        String cancelledTaskId;
        try {
            MvcResult startResult = mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/export-async")
                    .contentType("application/json")
                    .content("{\"limit\":500,\"category\":\"QUIET_PERIOD\",\"forceRequired\":\"NO\",\"query\":\"quiet\",\"windowHours\":24}"))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.taskId", notNullValue()))
                .andExpect(jsonPath("$.status", is("QUEUED")))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.deduplicated", is(false)))
                .andReturn();

            JsonNode startJson = objectMapper.readTree(startResult.getResponse().getContentAsString());
            cancelledTaskId = startJson.get("taskId").asText();

            assertTrue(
                enteredSnapshot.await(3, TimeUnit.SECONDS),
                "Async queue declined export task should reach declined snapshot query"
            );

            mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/export-async")
                    .contentType("application/json")
                    .content("{\"limit\":500,\"category\":\"QUIET_PERIOD\",\"forceRequired\":\"NO\",\"query\":\"quiet\",\"windowHours\":24}"))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.taskId", is(cancelledTaskId)))
                .andExpect(jsonPath("$.deduplicated", is(true)))
                .andExpect(jsonPath("$.deduplicatedFromTaskId", is(cancelledTaskId)))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Reused active queue declined async export task")));

            mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/export-async?limit=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(1))));

            mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/export-async/{taskId}", cancelledTaskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId", is(cancelledTaskId)))
                .andExpect(jsonPath("$.status", notNullValue()));

            mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/export-async/{taskId}/download", cancelledTaskId))
                .andExpect(status().isConflict());

            mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/export-async/{taskId}/cancel", cancelledTaskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId", is(cancelledTaskId)))
                .andExpect(jsonPath("$.status", is("CANCELLED")));
        } finally {
            releaseSnapshot.countDown();
        }

        String completedTaskId = startQueueDeclinedAsyncExportTask();
        assertEquals("COMPLETED", awaitQueueDeclinedAsyncTaskTerminalStatus(completedTaskId));
        String failedTaskId = startQueueDeclinedAsyncExportTask();
        assertEquals("FAILED", awaitQueueDeclinedAsyncTaskTerminalStatus(failedTaskId));

        CountDownLatch enteredRetrySnapshot = new CountDownLatch(1);
        CountDownLatch releaseRetrySnapshot = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
                enteredRetrySnapshot.countDown();
                releaseRetrySnapshot.await(3, TimeUnit.SECONDS);
                return snapshot;
            })
            .doReturn(snapshot)
            .when(previewQueueService)
            .declinedSnapshot(500);

        String retryTaskId;
        try {
            MvcResult retryResult = mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/export-async/{taskId}/retry", completedTaskId))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.taskId", notNullValue()))
                .andExpect(jsonPath("$.status", is("QUEUED")))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.deduplicated", is(false)))
                .andReturn();
            retryTaskId = objectMapper.readTree(retryResult.getResponse().getContentAsString()).path("taskId").asText();
            assertFalse(retryTaskId.isBlank());
            assertTrue(enteredRetrySnapshot.await(3, TimeUnit.SECONDS));

            mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/export-async/{taskId}/retry", completedTaskId))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.taskId", is(retryTaskId)))
                .andExpect(jsonPath("$.status", notNullValue()))
                .andExpect(jsonPath("$.deduplicated", is(true)))
                .andExpect(jsonPath("$.deduplicatedFromTaskId", is(retryTaskId)));
        } finally {
            releaseRetrySnapshot.countDown();
        }

        assertEquals("COMPLETED", awaitQueueDeclinedAsyncTaskTerminalStatus(retryTaskId));
        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/export-async/{taskId}", retryTaskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId", is(retryTaskId)))
            .andExpect(jsonPath("$.status", notNullValue()));

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/export-async/retry-terminal/dry-run")
                .param("status", "FAILED")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limit", is(20)))
            .andExpect(jsonPath("$.statusFilter", is("FAILED")))
            .andExpect(jsonPath("$.requested", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.retryable", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.reasonBreakdown", notNullValue()))
            .andExpect(jsonPath("$.reasonBreakdown[0].reasonCode", notNullValue()))
            .andExpect(jsonPath("$.reasonBreakdown[0].outcome", notNullValue()))
            .andExpect(jsonPath("$.reasonBreakdown[0].count", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.results", notNullValue()));

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/export-async/retry-terminal/dry-run/export")
                .param("status", "FAILED")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("X-Preview-Queue-Declined-Retry-Dry-Run-Count", notNullValue()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .contentType("text/csv;charset=UTF-8"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("statusFilter,limit,requested,retryable,skipped,sourceTaskId,sourceStatus,outcome,reasonCode,message")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("reasonCode,outcome,count")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("TERMINAL_TASK_RETRYABLE")));

        CountDownLatch enteredSelectedRetrySnapshot = new CountDownLatch(1);
        CountDownLatch releaseSelectedRetrySnapshot = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
                enteredSelectedRetrySnapshot.countDown();
                releaseSelectedRetrySnapshot.await(3, TimeUnit.SECONDS);
                return snapshot;
            })
            .doReturn(snapshot)
            .when(previewQueueService)
            .declinedSnapshot(500);

        MvcResult selectedBulkRetryResult = mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/export-async/retry-terminal/by-task-ids")
                .contentType("application/json")
                .content("{\"sourceTaskIds\":[\"" + failedTaskId + "\",\"" + retryTaskId + "\"]}"))
            .andExpect(status().isAccepted())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.statusFilter", is("BY_TASK_IDS")))
            .andExpect(jsonPath("$.requested", is(2)))
            .andExpect(jsonPath("$.retried", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.reused", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.results", notNullValue()))
            .andReturn();
        JsonNode selectedBulkRetryJson = objectMapper.readTree(selectedBulkRetryResult.getResponse().getContentAsString());
        JsonNode selectedBulkRetryResults = selectedBulkRetryJson.path("results");
        assertTrue(enteredSelectedRetrySnapshot.await(3, TimeUnit.SECONDS));
        assertTrue(selectedBulkRetryResults.isArray(), "Selected bulk retry should return results array");
        boolean selectedContainsReused = false;
        String selectedBulkRetryTaskId = "";
        for (JsonNode result : selectedBulkRetryResults) {
            if ("REUSED".equalsIgnoreCase(result.path("outcome").asText())) {
                selectedContainsReused = true;
            }
            if (selectedBulkRetryTaskId.isBlank()) {
                String candidateTaskId = result.path("newTaskId").asText("");
                if (!candidateTaskId.isBlank()) {
                    selectedBulkRetryTaskId = candidateTaskId;
                }
            }
        }
        releaseSelectedRetrySnapshot.countDown();
        assertTrue(selectedContainsReused, "Selected bulk retry should include reused outcome when request-equivalent active task exists");
        assertFalse(selectedBulkRetryTaskId.isBlank(), "Selected bulk retry should return a new task id");
        assertEquals("COMPLETED", awaitQueueDeclinedAsyncTaskTerminalStatus(selectedBulkRetryTaskId));

        CountDownLatch enteredBulkRetrySnapshot = new CountDownLatch(1);
        CountDownLatch releaseBulkRetrySnapshot = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
                enteredBulkRetrySnapshot.countDown();
                releaseBulkRetrySnapshot.await(3, TimeUnit.SECONDS);
                return snapshot;
            })
            .doReturn(snapshot)
            .when(previewQueueService)
            .declinedSnapshot(500);
        MvcResult bulkRetryResult = mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/export-async/retry-terminal")
                .param("status", "COMPLETED")
                .param("limit", "20"))
            .andExpect(status().isAccepted())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.limit", is(20)))
            .andExpect(jsonPath("$.statusFilter", is("COMPLETED")))
            .andExpect(jsonPath("$.requested", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.retried", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.reused", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.results", notNullValue()))
            .andReturn();
        JsonNode bulkRetryJson = objectMapper.readTree(bulkRetryResult.getResponse().getContentAsString());
        JsonNode bulkRetryResults = bulkRetryJson.path("results");
        assertTrue(enteredBulkRetrySnapshot.await(3, TimeUnit.SECONDS));
        assertTrue(bulkRetryResults.isArray(), "Bulk retry should return results array");
        boolean bulkContainsReused = false;
        String bulkRetryTaskId = "";
        for (JsonNode result : bulkRetryResults) {
            if ("REUSED".equalsIgnoreCase(result.path("outcome").asText())) {
                bulkContainsReused = true;
            }
            if (bulkRetryTaskId.isBlank()) {
                String candidateTaskId = result.path("newTaskId").asText("");
                if (!candidateTaskId.isBlank()) {
                    bulkRetryTaskId = candidateTaskId;
                }
            }
        }
        releaseBulkRetrySnapshot.countDown();
        assertTrue(bulkContainsReused, "Bulk retry should include reused outcome when equivalent active task exists");
        assertFalse(bulkRetryTaskId.isBlank(), "Bulk retry should return a new task id");
        assertEquals("COMPLETED", awaitQueueDeclinedAsyncTaskTerminalStatus(bulkRetryTaskId));

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/export-async/{taskId}/download", completedTaskId))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("text/csv;charset=UTF-8"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("queueEnabled,totalDeclined,sampleLimit")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString(documentId.toString())));

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/export-async/cancel-active"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusFilter", nullValue()));

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/export-async/cleanup"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletedCount", greaterThanOrEqualTo(1)));

        Mockito.verify(auditService, Mockito.timeout(3000).atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_COMPLETED"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            anyString(),
            Mockito.contains("status=COMPLETED")
        );
        Mockito.verify(auditService, Mockito.timeout(3000).atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_FAILED"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            anyString(),
            Mockito.contains("status=FAILED")
        );

        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eventCaptor.capture(),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            anyString(),
            messageCaptor.capture()
        );

        List<String> events = eventCaptor.getAllValues();
        assertTrue(events.contains("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_STARTED"));
        assertTrue(events.contains("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_START_DEDUP_HIT"));
        assertTrue(events.contains("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_CANCEL_SINGLE"));
        assertTrue(events.contains("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_CANCEL_ACTIVE"));
        assertTrue(events.contains("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_CLEANUP"));
        assertTrue(events.contains("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_DOWNLOADED"));
        assertTrue(events.contains("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_COMPLETED"));
        assertTrue(events.contains("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_FAILED"));
        assertTrue(events.contains("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY"));
        assertTrue(events.contains("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK_DRY_RUN"));
        assertTrue(events.contains("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK_DRY_RUN_EXPORTED"));
        assertTrue(events.contains("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK_SELECTED"));
        assertTrue(events.contains("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK"));

        boolean startedAuditContainsContext = false;
        boolean startedDedupAuditContainsContext = false;
        boolean retryAuditContainsContext = false;
        boolean bulkRetryDryRunAuditContainsContext = false;
        boolean bulkRetryDryRunExportAuditContainsContext = false;
        boolean bulkRetrySelectedAuditContainsContext = false;
        boolean bulkRetryAuditContainsContext = false;
        List<String> messages = messageCaptor.getAllValues();
        for (int i = 0; i < events.size(); i++) {
            String event = events.get(i);
            String message = messages.get(i);
            if ("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_STARTED".equals(event)) {
                if (message.contains("limit=500")
                    && message.contains("category=QUIET_PERIOD")
                    && message.contains("forceRequired=NO")
                    && message.contains("query=quiet")
                    && message.contains("windowHours=24")
                    && message.contains("taskId=")
                    && message.contains("status=QUEUED")) {
                    startedAuditContainsContext = true;
                }
            }
            if ("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_START_DEDUP_HIT".equals(event)) {
                if (message.contains("limit=500")
                    && message.contains("category=QUIET_PERIOD")
                    && message.contains("forceRequired=NO")
                    && message.contains("query=quiet")
                    && message.contains("windowHours=24")
                    && message.contains("status=")
                    && message.contains("reusedTaskId=" + cancelledTaskId)) {
                    startedDedupAuditContainsContext = true;
                }
            }
            if ("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY".equals(event)) {
                if (message.contains("sourceTaskId=" + completedTaskId)
                    && message.contains("newTaskId=" + retryTaskId)
                    && message.contains("status=QUEUED")
                    && message.contains("limit=500")
                    && message.contains("category=QUIET_PERIOD")
                    && message.contains("forceRequired=NO")
                    && message.contains("query=quiet")
                    && message.contains("windowHours=24")) {
                    retryAuditContainsContext = true;
                }
            }
            if ("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK_DRY_RUN".equals(event)) {
                if (message.contains("statusFilter=FAILED")
                    && message.contains("limit=20")
                    && message.contains("requested=")
                    && message.contains("retryable=")
                    && message.contains("skipped=")
                    && message.contains("sourceTaskIds=")
                    && message.contains("reasonBreakdown=")) {
                    bulkRetryDryRunAuditContainsContext = true;
                }
            }
            if ("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK_DRY_RUN_EXPORTED".equals(event)) {
                if (message.contains("statusFilter=FAILED")
                    && message.contains("limit=20")
                    && message.contains("requested=")
                    && message.contains("retryable=")
                    && message.contains("skipped=")
                    && message.contains("reasonBreakdown=")) {
                    bulkRetryDryRunExportAuditContainsContext = true;
                }
            }
            if ("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK_SELECTED".equals(event)) {
                if (message.contains("statusFilter=BY_TASK_IDS")
                    && message.contains("requested=")
                    && message.contains("retried=")
                    && message.contains("reused=")
                    && message.contains("skipped=")
                    && message.contains("failed=")
                    && message.contains("sourceTaskIds=")
                    && message.contains("newTaskIds=")) {
                    bulkRetrySelectedAuditContainsContext = true;
                }
            }
            if ("PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK".equals(event)) {
                if (message.contains("statusFilter=COMPLETED")
                    && message.contains("limit=20")
                    && message.contains("requested=")
                    && message.contains("retried=")
                    && message.contains("reused=")
                    && message.contains("skipped=")
                    && message.contains("failed=")
                    && message.contains("newTaskIds=")) {
                    bulkRetryAuditContainsContext = true;
                }
            }
        }
        assertTrue(startedAuditContainsContext, "Started async export audit should include filters and task status context");
        assertTrue(
            startedDedupAuditContainsContext,
            "Start dedup async export audit should include filters and reused task context"
        );
        assertTrue(retryAuditContainsContext, "Retry async export audit should include source/new task ids and filter context");
        assertTrue(bulkRetryDryRunAuditContainsContext, "Bulk retry dry-run async export audit should include retry governance context");
        assertTrue(
            bulkRetryDryRunExportAuditContainsContext,
            "Bulk retry dry-run export async export audit should include retry governance context"
        );
        assertTrue(bulkRetrySelectedAuditContainsContext, "Bulk retry selected async export audit should include retry governance context");
        assertTrue(bulkRetryAuditContainsContext, "Bulk retry async export audit should include retry governance context");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can requeue and clear declined preview queue diagnostics by filter")
    void diagnosticsQueueDeclinedRequeueAndClearAllowsAdmin() throws Exception {
        UUID quietPeriodDocumentId = UUID.randomUUID();
        Instant now = Instant.now().minusSeconds(90L * 60L);
        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = new PreviewQueueService.PreviewQueueDeclinedSnapshot(
            true,
            1L,
            200,
            false,
            List.of(
                new PreviewQueueService.PreviewQueueDeclinedItem(
                    quietPeriodDocumentId,
                    quietPeriodDocumentId + "|preview|hash-quiet",
                    PreviewStatus.FAILED,
                    "Within quiet period for policy: default",
                    "QUIET_PERIOD",
                    now,
                    now.plusSeconds(90),
                    false
                )
            )
        );

        Document quietPeriodDocument = new Document();
        quietPeriodDocument.setId(quietPeriodDocumentId);
        quietPeriodDocument.setName("queue-declined-quiet.pdf");
        quietPeriodDocument.setPath("/Root/Documents/queue-declined-quiet.pdf");
        quietPeriodDocument.setMimeType("application/pdf");
        quietPeriodDocument.setPreviewStatus(PreviewStatus.FAILED);

        CountDownLatch enteredSnapshot = new CountDownLatch(1);
        CountDownLatch releaseSnapshot = new CountDownLatch(1);
        Mockito.when(previewQueueService.declinedSnapshot(200))
            .thenAnswer(invocation -> {
                enteredSnapshot.countDown();
                releaseSnapshot.await(3, TimeUnit.SECONDS);
                return snapshot;
            })
            .thenReturn(snapshot);
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(quietPeriodDocument));
        Mockito.when(previewQueueService.enqueue(quietPeriodDocumentId, true)).thenReturn(
            new PreviewQueueService.PreviewQueueStatus(
                quietPeriodDocumentId,
                PreviewStatus.FAILED,
                true,
                0,
                Instant.parse("2026-03-10T14:05:00Z"),
                "Preview queued"
            )
        );
        Mockito.when(previewQueueService.clearDeclined(quietPeriodDocumentId)).thenReturn(true);

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue?category=QUIET_PERIOD&forceRequired=NO&query=quiet&windowHours=24&limit=200&force=true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoryFilter", is("QUIET_PERIOD")))
            .andExpect(jsonPath("$.forceRequiredFilter", is("NO")))
            .andExpect(jsonPath("$.queryFilter", is("quiet")))
            .andExpect(jsonPath("$.windowHoursFilter", is(24)))
            .andExpect(jsonPath("$.limit", is(200)))
            .andExpect(jsonPath("$.force", is(true)))
            .andExpect(jsonPath("$.requested", is(1)))
            .andExpect(jsonPath("$.queued", is(1)))
            .andExpect(jsonPath("$.skipped", is(0)))
            .andExpect(jsonPath("$.failed", is(0)))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].documentId", is(quietPeriodDocumentId.toString())))
            .andExpect(jsonPath("$.results[0].outcome", is("QUEUED")));

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/clear?category=QUIET_PERIOD&forceRequired=NO&query=quiet&windowHours=24&limit=200"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoryFilter", is("QUIET_PERIOD")))
            .andExpect(jsonPath("$.forceRequiredFilter", is("NO")))
            .andExpect(jsonPath("$.queryFilter", is("quiet")))
            .andExpect(jsonPath("$.windowHoursFilter", is(24)))
            .andExpect(jsonPath("$.requested", is(1)))
            .andExpect(jsonPath("$.cleared", is(1)))
            .andExpect(jsonPath("$.skipped", is(0)))
            .andExpect(jsonPath("$.failed", is(0)))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].documentId", is(quietPeriodDocumentId.toString())))
            .andExpect(jsonPath("$.results[0].outcome", is("CLEARED")));

        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_DECLINED_REQUEUE"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            eq("user"),
            Mockito.contains("queued=1")
        );
        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_DECLINED_REQUEUE"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            eq("user"),
            Mockito.contains("windowHours=24")
        );
        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_DECLINED_CLEAR"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            eq("user"),
            Mockito.contains("cleared=1")
        );
        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_DECLINED_CLEAR"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            eq("user"),
            Mockito.contains("windowHours=24")
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can dry-run requeue for declined preview queue diagnostics")
    void diagnosticsQueueDeclinedRequeueDryRunAllowsAdmin() throws Exception {
        UUID quietPeriodDocumentId = UUID.randomUUID();
        Instant now = Instant.now().minusSeconds(75L * 60L);
        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = new PreviewQueueService.PreviewQueueDeclinedSnapshot(
            true,
            1L,
            200,
            false,
            List.of(
                new PreviewQueueService.PreviewQueueDeclinedItem(
                    quietPeriodDocumentId,
                    quietPeriodDocumentId + "|preview|hash-quiet",
                    PreviewStatus.FAILED,
                    "Within quiet period for policy: default",
                    "QUIET_PERIOD",
                    now,
                    now.plusSeconds(90),
                    false
                )
            )
        );

        Document quietPeriodDocument = new Document();
        quietPeriodDocument.setId(quietPeriodDocumentId);
        quietPeriodDocument.setName("queue-declined-quiet.pdf");
        quietPeriodDocument.setPath("/Root/Documents/queue-declined-quiet.pdf");
        quietPeriodDocument.setMimeType("application/pdf");
        quietPeriodDocument.setPreviewStatus(PreviewStatus.FAILED);

        CountDownLatch enteredSnapshot = new CountDownLatch(1);
        CountDownLatch releaseSnapshot = new CountDownLatch(1);
        Mockito.when(previewQueueService.declinedSnapshot(200))
            .thenAnswer(invocation -> {
                enteredSnapshot.countDown();
                releaseSnapshot.await(3, TimeUnit.SECONDS);
                return snapshot;
            })
            .thenReturn(snapshot);
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(quietPeriodDocument));
        Mockito.when(previewPreflightResolver.evaluateDocument(any(Document.class))).thenReturn(
            new PreviewPreflightResolver.PreflightDecision(
                quietPeriodDocumentId,
                "pdf",
                "ACCEPTED",
                null,
                "Eligible for preview queue",
                "default",
                1024L,
                268435456L,
                List.of("local-pdfbox")
            )
        );
        Mockito.when(previewQueueService.evaluateEnqueue(quietPeriodDocumentId, false)).thenReturn(
            new PreviewQueueService.PreviewQueueStatus(
                quietPeriodDocumentId,
                PreviewStatus.FAILED,
                false,
                0,
                Instant.parse("2026-03-10T14:31:30Z"),
                "Within quiet period for policy: default"
            )
        );

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run?category=QUIET_PERIOD&forceRequired=NO&query=quiet&windowHours=24&limit=200&force=false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoryFilter", is("QUIET_PERIOD")))
            .andExpect(jsonPath("$.forceRequiredFilter", is("NO")))
            .andExpect(jsonPath("$.queryFilter", is("quiet")))
            .andExpect(jsonPath("$.windowHoursFilter", is(24)))
            .andExpect(jsonPath("$.limit", is(200)))
            .andExpect(jsonPath("$.force", is(false)))
            .andExpect(jsonPath("$.requested", is(1)))
            .andExpect(jsonPath("$.estimatedQueued", is(0)))
            .andExpect(jsonPath("$.estimatedSkipped", is(1)))
            .andExpect(jsonPath("$.estimatedFailed", is(0)))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].documentId", is(quietPeriodDocumentId.toString())))
            .andExpect(jsonPath("$.results[0].outcome", is("SKIPPED")))
            .andExpect(jsonPath("$.results[0].reasonCode", is("QUIET_PERIOD_ACTIVE")))
            .andExpect(jsonPath("$.results[0].nextAttemptAt", notNullValue()))
            .andExpect(jsonPath("$.results[0].preflightStatus", is("ACCEPTED")))
            .andExpect(jsonPath("$.results[0].preflightSkipReason", nullValue()))
            .andExpect(jsonPath("$.results[0].preflightRoute", is("pdf")))
            .andExpect(jsonPath("$.results[0].preflightPolicyProfile", is("default")))
            .andExpect(jsonPath("$.results[0].preflightPipeline", is("local-pdfbox")))
            .andExpect(jsonPath("$.reasonBreakdown", hasSize(1)))
            .andExpect(jsonPath("$.reasonBreakdown[0].reasonCode", is("QUIET_PERIOD_ACTIVE")))
            .andExpect(jsonPath("$.reasonBreakdown[0].outcome", is("SKIPPED")))
            .andExpect(jsonPath("$.reasonBreakdown[0].count", is(1)));

        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            eq("user"),
            Mockito.contains("estimatedSkipped=1")
        );
        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            eq("user"),
            Mockito.contains("windowHours=24")
        );
        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            eq("user"),
            Mockito.contains("reasonBreakdown=[QUIET_PERIOD_ACTIVE:SKIPPED=1]")
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Queue declined requeue and dry-run prefer shared preview snapshot fallback")
    void diagnosticsQueueDeclinedRequeueActionsPreferSharedPreviewSnapshot() throws Exception {
        UUID documentId = UUID.randomUUID();
        Instant now = Instant.parse("2026-03-28T12:10:00Z");

        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = new PreviewQueueService.PreviewQueueDeclinedSnapshot(
            true,
            1L,
            200,
            false,
            List.of(
                new PreviewQueueService.PreviewQueueDeclinedItem(
                    documentId,
                    documentId + "|preview|hash-binary",
                    PreviewStatus.FAILED,
                    "Preview definition is not registered for generic binary sources",
                    "UNSUPPORTED_SOURCE",
                    now,
                    now.plusSeconds(90),
                    false
                )
            )
        );

        Document document = new Document();
        document.setId(documentId);
        document.setName("queue-declined-requeue.bin");
        document.setPath("/Root/Documents/queue-declined-requeue.bin");
        document.setMimeType("application/octet-stream");
        document.setPreviewStatus(PreviewStatus.FAILED);

        Mockito.when(previewQueueService.declinedSnapshot(200)).thenReturn(snapshot);
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(document));
        Mockito.when(renditionResourceService.resolveEffectivePreviewSnapshot(
            eq(document),
            any(),
            any(),
            any(),
            any()
        )).thenReturn(
            new RenditionResourceService.EffectivePreviewSnapshot(
                "UNSUPPORTED",
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                LocalDateTime.of(2026, 3, 28, 12, 10, 0)
            )
        );
        Mockito.when(previewQueueService.enqueue(documentId, true)).thenReturn(
            new PreviewQueueService.PreviewQueueStatus(
                documentId,
                null,
                false,
                0,
                now.plusSeconds(300),
                "Preview queueing skipped by policy"
            )
        );
        Mockito.when(previewPreflightResolver.evaluateDocument(any(Document.class))).thenReturn(
            new PreviewPreflightResolver.PreflightDecision(
                documentId,
                "generic-binary",
                "ACCEPTED",
                null,
                "Eligible for preview queue",
                "default",
                512L,
                268435456L,
                List.of("local-pdfbox")
            )
        );
        Mockito.when(previewQueueService.evaluateEnqueue(documentId, false)).thenReturn(
            new PreviewQueueService.PreviewQueueStatus(
                documentId,
                null,
                false,
                0,
                now.plusSeconds(300),
                "Preview queueing skipped by policy"
            )
        );

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue?limit=200&force=true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].previewStatus", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.results[0].previewFailureReason", is("Preview definition is not registered for generic binary sources")))
            .andExpect(jsonPath("$.results[0].previewFailureCategory", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.results[0].previewLastUpdated", is("2026-03-28T12:10:00")));

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run?limit=200&force=false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].previewStatus", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.results[0].previewFailureReason", is("Preview definition is not registered for generic binary sources")))
            .andExpect(jsonPath("$.results[0].previewFailureCategory", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.results[0].previewLastUpdated", is("2026-03-28T12:10:00")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can export queue declined requeue dry-run CSV with reason breakdown")
    void diagnosticsQueueDeclinedRequeueDryRunExportAllowsAdmin() throws Exception {
        UUID quietPeriodDocumentId = UUID.randomUUID();
        Instant now = Instant.now().minusSeconds(75L * 60L);
        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = new PreviewQueueService.PreviewQueueDeclinedSnapshot(
            true,
            1L,
            200,
            false,
            List.of(
                new PreviewQueueService.PreviewQueueDeclinedItem(
                    quietPeriodDocumentId,
                    quietPeriodDocumentId + "|preview|hash-quiet",
                    PreviewStatus.FAILED,
                    "Within quiet period for policy: default",
                    "QUIET_PERIOD",
                    now,
                    now.plusSeconds(90),
                    false
                )
            )
        );

        Document quietPeriodDocument = new Document();
        quietPeriodDocument.setId(quietPeriodDocumentId);
        quietPeriodDocument.setName("queue-declined-quiet-export.pdf");
        quietPeriodDocument.setPath("/Root/Documents/queue-declined-quiet-export.pdf");
        quietPeriodDocument.setMimeType("application/pdf");
        quietPeriodDocument.setPreviewStatus(PreviewStatus.FAILED);

        CountDownLatch enteredSnapshot = new CountDownLatch(1);
        CountDownLatch releaseSnapshot = new CountDownLatch(1);
        Mockito.when(previewQueueService.declinedSnapshot(200))
            .thenAnswer(invocation -> {
                enteredSnapshot.countDown();
                releaseSnapshot.await(3, TimeUnit.SECONDS);
                return snapshot;
            })
            .thenReturn(snapshot);
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(quietPeriodDocument));
        Mockito.when(previewPreflightResolver.evaluateDocument(any(Document.class))).thenReturn(
            new PreviewPreflightResolver.PreflightDecision(
                quietPeriodDocumentId,
                "pdf",
                "ACCEPTED",
                null,
                "Eligible for preview queue",
                "default",
                1024L,
                268435456L,
                List.of("local-pdfbox")
            )
        );
        Mockito.when(previewQueueService.evaluateEnqueue(quietPeriodDocumentId, false)).thenReturn(
            new PreviewQueueService.PreviewQueueStatus(
                quietPeriodDocumentId,
                PreviewStatus.FAILED,
                false,
                0,
                Instant.parse("2026-03-10T14:31:30Z"),
                "Within quiet period for policy: default"
            )
        );

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export")
                .param("category", "QUIET_PERIOD")
                .param("forceRequired", "NO")
                .param("query", "quiet")
                .param("windowHours", "24")
                .param("limit", "200")
                .param("force", "false"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("X-Preview-Queue-Declined-Requeue-Dry-Run-Count", "1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .contentType("text/csv;charset=UTF-8"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("categoryFilter,forceRequiredFilter,queryFilter,windowHoursFilter,limit,force,requested,estimatedQueued,estimatedSkipped,estimatedFailed,documentId,category,outcome,reasonCode,message,previewStatus,previewFailureReason,previewFailureCategory,previewLastUpdated,nextAttemptAt,preflightStatus,preflightSkipReason,preflightRoute,preflightPolicyProfile,preflightPipeline")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("reasonCode,outcome,count")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("\"QUIET_PERIOD_ACTIVE\",\"SKIPPED\",1")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("\"ACCEPTED\",,\"pdf\",\"default\",\"local-pdfbox\"")));

        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORTED"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            eq("user"),
                Mockito.contains("reasonBreakdown=[QUIET_PERIOD_ACTIVE:SKIPPED=1]")
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can manage declined requeue dry-run async export tasks")
    void diagnosticsQueueDeclinedRequeueDryRunAsyncExportTaskCenterForAdmin() throws Exception {
        UUID documentId = UUID.randomUUID();
        Instant declinedAt = Instant.now().minusSeconds(70L * 60L);
        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = new PreviewQueueService.PreviewQueueDeclinedSnapshot(
            true,
            1L,
            200,
            false,
            List.of(
                new PreviewQueueService.PreviewQueueDeclinedItem(
                    documentId,
                    documentId + "|preview|hash-quiet",
                    PreviewStatus.FAILED,
                    "Within quiet period for policy: default",
                    "QUIET_PERIOD",
                    declinedAt,
                    declinedAt.plusSeconds(90),
                    false
                )
            )
        );

        Document document = new Document();
        document.setId(documentId);
        document.setName("queue-declined-requeue-dry-run-async.pdf");
        document.setPath("/Root/Documents/queue-declined-requeue-dry-run-async.pdf");
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);

        CountDownLatch enteredSnapshot = new CountDownLatch(1);
        CountDownLatch releaseSnapshot = new CountDownLatch(1);
        Mockito.when(previewQueueService.declinedSnapshot(200))
            .thenAnswer(invocation -> {
                enteredSnapshot.countDown();
                releaseSnapshot.await(3, TimeUnit.SECONDS);
                return snapshot;
            })
            .thenReturn(snapshot);
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(document));
        Mockito.when(previewPreflightResolver.evaluateDocument(any(Document.class))).thenReturn(
            new PreviewPreflightResolver.PreflightDecision(
                documentId,
                "pdf",
                "ACCEPTED",
                null,
                "Eligible for preview queue",
                "default",
                2048L,
                268435456L,
                List.of("local-pdfbox")
            )
        );
        Mockito.when(previewQueueService.evaluateEnqueue(documentId, false)).thenReturn(
            new PreviewQueueService.PreviewQueueStatus(
                documentId,
                PreviewStatus.FAILED,
                false,
                0,
                declinedAt.plusSeconds(90),
                "Within quiet period for policy: default"
            )
        );

        String taskId;
        try {
            MvcResult startResult = mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async")
                    .contentType("application/json")
                    .content("{\"limit\":200,\"category\":\"QUIET_PERIOD\",\"forceRequired\":\"NO\",\"query\":\"quiet\",\"windowHours\":24,\"force\":false}"))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.taskId", notNullValue()))
                .andExpect(jsonPath("$.status", is("QUEUED")))
                .andExpect(jsonPath("$.deduplicated", is(false)))
                .andReturn();
            taskId = objectMapper.readTree(startResult.getResponse().getContentAsString()).path("taskId").asText();
            assertTrue(enteredSnapshot.await(3, TimeUnit.SECONDS));

            mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async")
                    .contentType("application/json")
                    .content("{\"limit\":200,\"category\":\"QUIET_PERIOD\",\"forceRequired\":\"NO\",\"query\":\"quiet\",\"windowHours\":24,\"force\":false}"))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.taskId", is(taskId)))
                .andExpect(jsonPath("$.deduplicated", is(true)))
                .andExpect(jsonPath("$.deduplicatedFromTaskId", is(taskId)));
        } finally {
            releaseSnapshot.countDown();
        }

        assertEquals("COMPLETED", awaitQueueDeclinedRequeueDryRunAsyncTaskTerminalStatus(taskId));

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async?limit=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCount", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.completedCount", greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/{taskId}", taskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId", is(taskId)))
            .andExpect(jsonPath("$.status", is("COMPLETED")))
            .andExpect(jsonPath("$.force", is(false)))
            .andExpect(jsonPath("$.windowHoursFilter", is(24)));

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/{taskId}/download", taskId))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("text/csv;charset=UTF-8"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("categoryFilter,forceRequiredFilter,queryFilter,windowHoursFilter")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("reasonCode,outcome,count")));

        CountDownLatch enteredRetrySnapshot = new CountDownLatch(1);
        CountDownLatch releaseRetrySnapshot = new CountDownLatch(1);
        Mockito.when(previewQueueService.declinedSnapshot(200))
            .thenAnswer(invocation -> {
                enteredRetrySnapshot.countDown();
                releaseRetrySnapshot.await(3, TimeUnit.SECONDS);
                return snapshot;
            })
            .thenReturn(snapshot);

        String retryTaskId;
        try {
            MvcResult retryResult = mockMvc.perform(post(
                    "/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/{taskId}/retry",
                    taskId
                ))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.taskId", notNullValue()))
                .andExpect(jsonPath("$.status", is("QUEUED")))
                .andExpect(jsonPath("$.deduplicated", is(false)))
                .andReturn();
            retryTaskId = objectMapper.readTree(retryResult.getResponse().getContentAsString()).path("taskId").asText();
            assertFalse(retryTaskId.isBlank());
            assertTrue(enteredRetrySnapshot.await(3, TimeUnit.SECONDS));

            mockMvc.perform(post(
                    "/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/{taskId}/retry",
                    taskId
                ))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.taskId", is(retryTaskId)))
                .andExpect(jsonPath("$.status", notNullValue()))
                .andExpect(jsonPath("$.deduplicated", is(true)))
                .andExpect(jsonPath("$.deduplicatedFromTaskId", is(retryTaskId)));
        } finally {
            releaseRetrySnapshot.countDown();
        }
        assertEquals("COMPLETED", awaitQueueDeclinedRequeueDryRunAsyncTaskTerminalStatus(retryTaskId));

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/retry-terminal/dry-run")
                .param("status", "COMPLETED")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requested", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.retryable", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.statusFilter", is("COMPLETED")))
            .andExpect(jsonPath("$.reasonBreakdown", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/retry-terminal/dry-run/export")
                .param("status", "COMPLETED")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("statusFilter,limit,requested,retryable,skipped,sourceTaskId,sourceStatus,outcome,reasonCode,message")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("reasonCode,outcome,count")));

        CountDownLatch enteredSelectedRetrySnapshot = new CountDownLatch(1);
        CountDownLatch releaseSelectedRetrySnapshot = new CountDownLatch(1);
        Mockito.when(previewQueueService.declinedSnapshot(200))
            .thenAnswer(invocation -> {
                enteredSelectedRetrySnapshot.countDown();
                releaseSelectedRetrySnapshot.await(3, TimeUnit.SECONDS);
                return snapshot;
            })
            .thenReturn(snapshot);

        MvcResult selectedRetryResult = mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/retry-terminal/by-task-ids")
                .contentType("application/json")
                .content("{\"sourceTaskIds\":[\"" + taskId + "\",\"" + retryTaskId + "\"]}"))
            .andExpect(status().isAccepted())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.requested", is(2)))
            .andExpect(jsonPath("$.retried", is(2)))
            .andExpect(jsonPath("$.reused", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.failed", is(0)))
            .andExpect(jsonPath("$.results", hasSize(2)))
            .andReturn();
        JsonNode selectedRetryResults = objectMapper.readTree(selectedRetryResult.getResponse().getContentAsString()).path("results");
        assertTrue(enteredSelectedRetrySnapshot.await(3, TimeUnit.SECONDS));
        boolean selectedContainsReused = false;
        String selectedRetryTaskId = "";
        for (JsonNode result : selectedRetryResults) {
            if ("REUSED".equalsIgnoreCase(result.path("outcome").asText())) {
                selectedContainsReused = true;
            }
            if (selectedRetryTaskId.isBlank()) {
                String candidateTaskId = result.path("newTaskId").asText("");
                if (!candidateTaskId.isBlank()) {
                    selectedRetryTaskId = candidateTaskId;
                }
            }
        }
        releaseSelectedRetrySnapshot.countDown();
        assertTrue(selectedContainsReused, "Selected retry should include reused outcome when request-equivalent active task exists");
        assertFalse(selectedRetryTaskId.isBlank());
        assertEquals("COMPLETED", awaitQueueDeclinedRequeueDryRunAsyncTaskTerminalStatus(selectedRetryTaskId));

        CountDownLatch enteredBulkRetrySnapshot = new CountDownLatch(1);
        CountDownLatch releaseBulkRetrySnapshot = new CountDownLatch(1);
        Mockito.when(previewQueueService.declinedSnapshot(200))
            .thenAnswer(invocation -> {
                enteredBulkRetrySnapshot.countDown();
                releaseBulkRetrySnapshot.await(3, TimeUnit.SECONDS);
                return snapshot;
            })
            .thenReturn(snapshot);

        MvcResult bulkRetryResult = mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/retry-terminal")
                .param("status", "COMPLETED")
                .param("limit", "20"))
            .andExpect(status().isAccepted())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.requested", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.retried", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.reused", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.failed", is(0)))
            .andExpect(jsonPath("$.statusFilter", is("COMPLETED")))
            .andReturn();
        JsonNode bulkRetryResults = objectMapper.readTree(bulkRetryResult.getResponse().getContentAsString()).path("results");
        assertTrue(enteredBulkRetrySnapshot.await(3, TimeUnit.SECONDS));
        assertTrue(bulkRetryResults.isArray(), "Bulk retry should return results array");
        assertTrue(bulkRetryResults.size() > 0, "Bulk retry should include at least one result");
        boolean bulkContainsReused = false;
        String bulkRetryTaskId = "";
        for (JsonNode result : bulkRetryResults) {
            if ("REUSED".equalsIgnoreCase(result.path("outcome").asText())) {
                bulkContainsReused = true;
            }
            if (bulkRetryTaskId.isBlank()) {
                String candidateTaskId = result.path("newTaskId").asText("");
                if (!candidateTaskId.isBlank()) {
                    bulkRetryTaskId = candidateTaskId;
                }
            }
        }
        releaseBulkRetrySnapshot.countDown();
        assertTrue(bulkContainsReused, "Bulk retry should include reused outcome when equivalent active task exists");
        assertFalse(bulkRetryTaskId.isBlank());
        assertEquals("COMPLETED", awaitQueueDeclinedRequeueDryRunAsyncTaskTerminalStatus(bulkRetryTaskId));

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/cancel-active"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusFilter", nullValue()));

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/cleanup"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletedCount", greaterThanOrEqualTo(1)));

        Mockito.verify(auditService, Mockito.timeout(3000).atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_STARTED"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            anyString(),
            Mockito.contains("taskId=")
        );
        Mockito.verify(auditService, Mockito.timeout(3000).atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_COMPLETED"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            anyString(),
            Mockito.contains("status=COMPLETED")
        );
        Mockito.verify(auditService, Mockito.timeout(3000).atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_RETRY"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            anyString(),
            Mockito.contains("newTaskId=")
        );
        Mockito.verify(auditService, Mockito.timeout(3000).atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_RETRY_BULK_DRY_RUN"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            anyString(),
            Mockito.contains("retryable=")
        );
        Mockito.verify(auditService, Mockito.timeout(3000).atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_RETRY_BULK_DRY_RUN_EXPORTED"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            anyString(),
            Mockito.contains("reasonBreakdown=")
        );
        Mockito.verify(auditService, Mockito.timeout(3000).atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_RETRY_BULK_SELECTED"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            anyString(),
            Mockito.contains("reused=")
        );
        Mockito.verify(auditService, Mockito.timeout(3000).atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_RETRY_BULK"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            anyString(),
            Mockito.contains("reused=")
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Queue declined dry-run surfaces preflight skip reason codes")
    void diagnosticsQueueDeclinedRequeueDryRunSurfacesPreflightSkipReason() throws Exception {
        UUID unsupportedDocumentId = UUID.randomUUID();
        Instant now = Instant.now().minusSeconds(60L * 60L);
        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = new PreviewQueueService.PreviewQueueDeclinedSnapshot(
            true,
            1L,
            200,
            false,
            List.of(
                new PreviewQueueService.PreviewQueueDeclinedItem(
                    unsupportedDocumentId,
                    unsupportedDocumentId + "|preview|hash-unsupported",
                    PreviewStatus.FAILED,
                    "Preview pipeline not available for this MIME type",
                    "PREFLIGHT",
                    now,
                    now.plusSeconds(30),
                    false
                )
            )
        );
        Document unsupportedDocument = new Document();
        unsupportedDocument.setId(unsupportedDocumentId);
        unsupportedDocument.setName("unsupported.bin");
        unsupportedDocument.setPath("/Root/Documents/unsupported.bin");
        unsupportedDocument.setMimeType("application/octet-stream");
        unsupportedDocument.setPreviewStatus(PreviewStatus.FAILED);
        unsupportedDocument.setFileSize(12L);

        Mockito.when(previewQueueService.declinedSnapshot(200)).thenReturn(snapshot);
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(unsupportedDocument));
        Mockito.when(previewPreflightResolver.evaluateDocument(any(Document.class))).thenReturn(
            new PreviewPreflightResolver.PreflightDecision(
                unsupportedDocumentId,
                "unsupported",
                "DECLINED",
                "MIME_UNSUPPORTED",
                "Preview pipeline not available for this MIME type",
                "default",
                12L,
                268435456L,
                List.of()
            )
        );

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run?category=ANY&forceRequired=ANY&query=unsupported&windowHours=24&limit=200&force=true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requested", is(1)))
            .andExpect(jsonPath("$.estimatedQueued", is(0)))
            .andExpect(jsonPath("$.estimatedSkipped", is(1)))
            .andExpect(jsonPath("$.estimatedFailed", is(0)))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].documentId", is(unsupportedDocumentId.toString())))
            .andExpect(jsonPath("$.results[0].outcome", is("SKIPPED")))
            .andExpect(jsonPath("$.results[0].reasonCode", is("PREFLIGHT_MIME_UNSUPPORTED")))
            .andExpect(jsonPath("$.results[0].preflightStatus", is("DECLINED")))
            .andExpect(jsonPath("$.results[0].preflightSkipReason", is("MIME_UNSUPPORTED")))
            .andExpect(jsonPath("$.results[0].preflightRoute", is("unsupported")))
            .andExpect(jsonPath("$.results[0].preflightPolicyProfile", is("default")))
            .andExpect(jsonPath("$.reasonBreakdown[0].reasonCode", is("PREFLIGHT_MIME_UNSUPPORTED")))
            .andExpect(jsonPath("$.reasonBreakdown[0].outcome", is("SKIPPED")))
            .andExpect(jsonPath("$.reasonBreakdown[0].count", is(1)));

        Mockito.verify(previewQueueService, Mockito.never()).evaluateEnqueue(any(UUID.class), eq(true));
        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            eq("user"),
            Mockito.contains("reasonBreakdown=[PREFLIGHT_MIME_UNSUPPORTED:SKIPPED=1]")
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Queue declined async export list supports skipCount and maxItems paging")
    void diagnosticsQueueDeclinedAsyncListSupportsPaging() throws Exception {
        UUID documentId = UUID.randomUUID();
        Instant declinedAt = Instant.now().minusSeconds(50L * 60L);
        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = new PreviewQueueService.PreviewQueueDeclinedSnapshot(
            true,
            1L,
            500,
            false,
            List.of(
                new PreviewQueueService.PreviewQueueDeclinedItem(
                    documentId,
                    documentId + "|preview|hash-paging",
                    PreviewStatus.FAILED,
                    "Within quiet period for policy: default",
                    "QUIET_PERIOD",
                    declinedAt,
                    declinedAt.plusSeconds(60),
                    false
                )
            )
        );

        Document document = new Document();
        document.setId(documentId);
        document.setName("queue-declined-paging.pdf");
        document.setPath("/Root/Documents/queue-declined-paging.pdf");
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);

        Mockito.when(previewQueueService.declinedSnapshot(500)).thenReturn(snapshot);
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(document));

        String taskId1 = startQueueDeclinedAsyncExportTaskWithQuery("paging-1-" + UUID.randomUUID());
        String taskId2 = startQueueDeclinedAsyncExportTaskWithQuery("paging-2-" + UUID.randomUUID());
        String taskId3 = startQueueDeclinedAsyncExportTaskWithQuery("paging-3-" + UUID.randomUUID());
        assertEquals("COMPLETED", awaitQueueDeclinedAsyncTaskTerminalStatus(taskId1));
        assertEquals("COMPLETED", awaitQueueDeclinedAsyncTaskTerminalStatus(taskId2));
        assertEquals("COMPLETED", awaitQueueDeclinedAsyncTaskTerminalStatus(taskId3));

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/export-async")
                .param("status", "completed")
                .param("maxItems", "1")
                .param("skipCount", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count", is(1)))
            .andExpect(jsonPath("$.paging.skipCount", is(1)))
            .andExpect(jsonPath("$.paging.maxItems", is(1)))
            .andExpect(jsonPath("$.paging.totalItems", greaterThanOrEqualTo(3)))
            .andExpect(jsonPath("$.paging.hasMoreItems", is(true)))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].status", is("COMPLETED")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Queue declined requeue dry-run async export list supports skipCount and maxItems paging")
    void diagnosticsQueueDeclinedRequeueDryRunAsyncListSupportsPaging() throws Exception {
        UUID documentId = UUID.randomUUID();
        Instant declinedAt = Instant.now().minusSeconds(45L * 60L);
        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = new PreviewQueueService.PreviewQueueDeclinedSnapshot(
            true,
            1L,
            200,
            false,
            List.of(
                new PreviewQueueService.PreviewQueueDeclinedItem(
                    documentId,
                    documentId + "|preview|hash-paging-dry-run",
                    PreviewStatus.FAILED,
                    "Within quiet period for policy: default",
                    "QUIET_PERIOD",
                    declinedAt,
                    declinedAt.plusSeconds(60),
                    false
                )
            )
        );

        Document document = new Document();
        document.setId(documentId);
        document.setName("queue-declined-requeue-dry-run-paging.pdf");
        document.setPath("/Root/Documents/queue-declined-requeue-dry-run-paging.pdf");
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);

        Mockito.when(previewQueueService.declinedSnapshot(200)).thenReturn(snapshot);
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(document));
        Mockito.when(previewPreflightResolver.evaluateDocument(any(Document.class))).thenReturn(
            new PreviewPreflightResolver.PreflightDecision(
                documentId,
                "application/pdf",
                "ACCEPTED",
                null,
                "Eligible for preview queue",
                "default",
                2048L,
                268435456L,
                List.of("local-pdfbox")
            )
        );
        Mockito.when(previewQueueService.evaluateEnqueue(documentId, false)).thenReturn(
            new PreviewQueueService.PreviewQueueStatus(
                documentId,
                PreviewStatus.FAILED,
                false,
                0,
                declinedAt.plusSeconds(90),
                "Within quiet period for policy: default"
            )
        );

        String taskId1 = startQueueDeclinedRequeueDryRunAsyncExportTaskWithQuery("dry-run-paging-1-" + UUID.randomUUID(), false);
        String taskId2 = startQueueDeclinedRequeueDryRunAsyncExportTaskWithQuery("dry-run-paging-2-" + UUID.randomUUID(), false);
        String taskId3 = startQueueDeclinedRequeueDryRunAsyncExportTaskWithQuery("dry-run-paging-3-" + UUID.randomUUID(), false);
        assertEquals("COMPLETED", awaitQueueDeclinedRequeueDryRunAsyncTaskTerminalStatus(taskId1));
        assertEquals("COMPLETED", awaitQueueDeclinedRequeueDryRunAsyncTaskTerminalStatus(taskId2));
        assertEquals("COMPLETED", awaitQueueDeclinedRequeueDryRunAsyncTaskTerminalStatus(taskId3));

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async")
                .param("status", "completed")
                .param("limit", "1")
                .param("skipCount", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count", is(1)))
            .andExpect(jsonPath("$.paging.skipCount", is(1)))
            .andExpect(jsonPath("$.paging.maxItems", is(1)))
            .andExpect(jsonPath("$.paging.totalItems", greaterThanOrEqualTo(3)))
            .andExpect(jsonPath("$.paging.hasMoreItems", is(true)))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].status", is("COMPLETED")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can reset preview failure ledger in batch")
    void diagnosticsFailureLedgerResetBatchAllowsAdmin() throws Exception {
        UUID docId = UUID.randomUUID();

        Document document = new Document();
        document.setId(docId);
        document.setName("ledger-reset.pdf");
        document.setPreviewFailureCount(5);
        document.setPreviewFailedAt(LocalDateTime.of(2026, 3, 10, 1, 2, 3));
        document.setPreviewLastFailureReason("gateway timeout");
        document.setPreviewFailureContentHash("oldhash");

        Mockito.when(documentRepository.findById(docId)).thenReturn(java.util.Optional.of(document));
        Mockito.when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/v1/preview/diagnostics/failures/ledger/reset-batch")
                .contentType("application/json")
                .content("{\"documentIds\":[\"" + docId + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requested", is(1)))
            .andExpect(jsonPath("$.deduplicated", is(1)))
            .andExpect(jsonPath("$.reset", is(1)))
            .andExpect(jsonPath("$.failed", is(0)))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].documentId", is(docId.toString())))
            .andExpect(jsonPath("$.results[0].outcome", is("RESET")));

        assertEquals(0, document.getPreviewFailureCount());
        assertNull(document.getPreviewFailedAt());
        assertNull(document.getPreviewLastFailureReason());
        assertNull(document.getPreviewFailureContentHash());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can reset preview failure ledger by filter")
    void diagnosticsFailureLedgerResetByFilterAllowsAdmin() throws Exception {
        UUID matchedId = UUID.randomUUID();
        UUID unmatchedId = UUID.randomUUID();

        Document matched = new Document();
        matched.setId(matchedId);
        matched.setName("ledger-filter-match.pdf");
        matched.setMimeType("application/pdf");
        matched.setPreviewStatus(PreviewStatus.FAILED);
        matched.setPreviewFailureCount(2);
        matched.setPreviewFailedAt(LocalDateTime.of(2026, 3, 10, 11, 0, 0));
        matched.setPreviewLastFailureReason("Timeout contacting preview service");
        matched.setPreviewFailureContentHash("hash-old");

        Document unmatched = new Document();
        unmatched.setId(unmatchedId);
        unmatched.setName("ledger-filter-unmatched.bin");
        unmatched.setMimeType("application/octet-stream");
        unmatched.setPreviewStatus(PreviewStatus.UNSUPPORTED);
        unmatched.setPreviewFailureCount(1);
        unmatched.setPreviewFailedAt(LocalDateTime.of(2026, 3, 10, 10, 0, 0));
        unmatched.setPreviewLastFailureReason("Preview not supported for mime type application/octet-stream");
        unmatched.setPreviewFailureContentHash("hash-bin");

        Mockito.when(documentRepository.countPreviewFailureLedgerEntries(any()))
            .thenReturn(2L);
        Mockito.when(documentRepository.findPreviewFailureLedgerEntries(any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(matched, unmatched), PageRequest.of(0, 100), 2));
        Mockito.when(documentRepository.findById(matchedId)).thenReturn(java.util.Optional.of(matched));
        Mockito.when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/v1/preview/diagnostics/failures/ledger/reset-by-filter")
                .contentType("application/json")
                .content("{\"reason\":\"Timeout contacting preview service\",\"category\":\"TEMPORARY\",\"retryable\":true,\"maxDocuments\":100,\"days\":30}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reason", is("Timeout contacting preview service")))
            .andExpect(jsonPath("$.category", is("TEMPORARY")))
            .andExpect(jsonPath("$.retryable", is(true)))
            .andExpect(jsonPath("$.windowDays", is(30)))
            .andExpect(jsonPath("$.totalCandidates", is(2)))
            .andExpect(jsonPath("$.scanned", is(2)))
            .andExpect(jsonPath("$.matched", is(1)))
            .andExpect(jsonPath("$.truncated", is(false)))
            .andExpect(jsonPath("$.reset", is(1)))
            .andExpect(jsonPath("$.skipped", is(0)))
            .andExpect(jsonPath("$.failed", is(0)))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].documentId", is(matchedId.toString())))
            .andExpect(jsonPath("$.results[0].outcome", is("RESET")));

        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("PREVIEW_FAILURE_LEDGER_RESET_BY_FILTER"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            eq("user"),
            Mockito.contains("matched=1")
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can export preview failure ledger CSV")
    void diagnosticsFailureLedgerExportAllowsAdmin() throws Exception {
        UUID docId = UUID.randomUUID();
        Document document = new Document();
        document.setId(docId);
        document.setName("ledger-export.pdf");
        document.setPath("/Root/Documents/ledger-export.pdf");
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureCount(4);
        document.setPreviewFailedAt(LocalDateTime.of(2026, 3, 10, 12, 0, 0));
        document.setPreviewLastFailureReason("Timeout contacting preview service");
        document.setPreviewFailureContentHash("hash-ledger");
        document.setContentHash("hash-ledger");
        document.setPreviewLastUpdated(LocalDateTime.of(2026, 3, 10, 12, 1, 0));

        Mockito.when(documentRepository.findPreviewFailureLedgerEntries(any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 500), 1));

        mockMvc.perform(get("/api/v1/preview/diagnostics/failures/ledger/export?days=30&limit=500"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Preview-Failure-Ledger-Count", "1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("text/csv;charset=UTF-8"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("ledger-export.pdf")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString(docId.toString())));

        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("PREVIEW_FAILURE_LEDGER_EXPORTED"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            eq("user"),
            Mockito.contains("exported=1")
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can access rendition summary diagnostics")
    void diagnosticsRenditionSummaryAllowsAdmin() throws Exception {
        Document created = new Document();
        created.setId(UUID.randomUUID());
        created.setPreviewStatus(PreviewStatus.READY);

        Document stale = new Document();
        stale.setId(UUID.randomUUID());
        stale.setPreviewStatus(PreviewStatus.FAILED);
        stale.setMimeType("application/pdf");
        stale.setPreviewFailureReason("gateway timeout");

        Mockito.when(documentRepository.count(any(org.springframework.data.jpa.domain.Specification.class)))
            .thenReturn(2L);
        Mockito.when(documentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(created, stale), PageRequest.of(0, 500), 2));

        mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/summary?days=7&sampleLimit=500"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalResources", is(2)))
            .andExpect(jsonPath("$.sampledResources", is(2)))
            .andExpect(jsonPath("$.statusCounts", hasSize(6)))
            .andExpect(jsonPath("$.statusCounts[0].status", is("CREATED")))
            .andExpect(jsonPath("$.statusCounts[0].count", is(1)))
            .andExpect(jsonPath("$.statusCounts[2].status", is("STALE")))
            .andExpect(jsonPath("$.statusCounts[2].count", is(1)))
            .andExpect(jsonPath("$.topReasons", hasSize(1)))
            .andExpect(jsonPath("$.topReasons[0].reason", is("gateway timeout")))
            .andExpect(jsonPath("$.topReasons[0].count", is(1)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can access rendition resource diagnostics")
    void diagnosticsRenditionResourcesAllowsAdmin() throws Exception {
        UUID staleId = UUID.randomUUID();
        UUID unsupportedId = UUID.randomUUID();
        LocalDateTime staleUpdated = LocalDateTime.of(2026, 3, 6, 14, 22, 33);

        Document stale = new Document();
        stale.setId(staleId);
        stale.setName("stale.pdf");
        stale.setPath("/Root/Documents/stale.pdf");
        stale.setMimeType("Application/PDF; charset=UTF-8");
        stale.setPreviewStatus(PreviewStatus.FAILED);
        stale.setPreviewFailureReason("  Timeout   contacting preview   service  ");
        stale.setPreviewLastUpdated(staleUpdated);

        Document unsupported = new Document();
        unsupported.setId(unsupportedId);
        unsupported.setName("unsupported.bin");
        unsupported.setPath("/Root/Documents/unsupported.bin");
        unsupported.setMimeType("application/octet-stream");
        unsupported.setPreviewLastUpdated(LocalDateTime.of(2026, 3, 6, 9, 15, 0));

        Document created = new Document();
        created.setId(UUID.randomUUID());
        created.setName("ready.png");
        created.setPath("/Root/Documents/ready.png");
        created.setMimeType("image/png");
        created.setPreviewStatus(PreviewStatus.READY);
        created.setPreviewLastUpdated(LocalDateTime.of(2026, 3, 5, 10, 0, 0));

        Mockito.when(documentRepository.count(any(org.springframework.data.jpa.domain.Specification.class)))
            .thenReturn(3L);
        Mockito.when(documentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(stale, unsupported, created), PageRequest.of(0, 100), 3));

        mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/resources"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalResources", is(3)))
            .andExpect(jsonPath("$.sampledResources", is(3)))
            .andExpect(jsonPath("$.limit", is(100)))
            .andExpect(jsonPath("$.windowDays", is(7)))
            .andExpect(jsonPath("$.windowStart", notNullValue()))
            .andExpect(jsonPath("$.sampleTruncated", is(false)))
            .andExpect(jsonPath("$.items", hasSize(3)))
            .andExpect(jsonPath("$.items[0].documentId", is(staleId.toString())))
            .andExpect(jsonPath("$.items[0].name", is("stale.pdf")))
            .andExpect(jsonPath("$.items[0].path", is("/Root/Documents/stale.pdf")))
            .andExpect(jsonPath("$.items[0].mimeType", is("application/pdf")))
            .andExpect(jsonPath("$.items[0].previewStatus", is("FAILED")))
            .andExpect(jsonPath("$.items[0].renditionStatus", is("STALE")))
            .andExpect(jsonPath("$.items[0].previewFailureReason", is("Timeout contacting preview service")))
            .andExpect(jsonPath("$.items[0].previewFailureCategory", is("TEMPORARY")))
            .andExpect(jsonPath("$.items[0].previewLastUpdated", is("2026-03-06T14:22:33")))
            .andExpect(jsonPath("$.items[1].documentId", is(unsupportedId.toString())))
            .andExpect(jsonPath("$.items[1].previewStatus", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.items[1].renditionStatus", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.items[1].previewFailureReason", is("Preview definition is not registered for generic binary sources")))
            .andExpect(jsonPath("$.items[1].previewFailureCategory", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.items[2].renditionStatus", is("CREATED")))
            .andExpect(jsonPath("$.items[2].previewFailureReason", nullValue()))
            .andExpect(jsonPath("$.items[2].previewFailureCategory", nullValue()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can export rendition resource diagnostics CSV")
    void diagnosticsRenditionResourcesExportForAdmin() throws Exception {
        UUID staleId = UUID.randomUUID();

        Document stale = new Document();
        stale.setId(staleId);
        stale.setName("stale-export.pdf");
        stale.setPath("/Root/Documents/stale-export.pdf");
        stale.setMimeType("Application/PDF; charset=UTF-8");
        stale.setPreviewStatus(PreviewStatus.FAILED);
        stale.setPreviewFailureReason("Timeout contacting preview service");
        stale.setPreviewLastUpdated(LocalDateTime.of(2026, 3, 8, 10, 20, 30));

        Mockito.when(documentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(stale), PageRequest.of(0, 500), 1));

        mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/resources/export?days=30&limit=500"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Preview-Rendition-Resource-Count", "1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("text/csv;charset=UTF-8"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("stale-export.pdf")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString(staleId.toString())));

        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("PREVIEW_RENDITION_RESOURCES_EXPORTED"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            eq("user"),
            Mockito.contains("exported=1")
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can manage rendition resources async export tasks")
    void diagnosticsRenditionResourcesAsyncExportTaskCenterForAdmin() throws Exception {
        CountDownLatch enteredRepository = new CountDownLatch(1);
        CountDownLatch releaseRepository = new CountDownLatch(1);

        Mockito.when(documentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
            .thenAnswer(invocation -> {
                enteredRepository.countDown();
                releaseRepository.await(3, TimeUnit.SECONDS);
                return new PageImpl<>(List.of(), PageRequest.of(0, 500), 0);
            });

        try {
            MvcResult startResult = mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async")
                    .contentType("application/json")
                    .content("{\"days\":7,\"limit\":500}"))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.taskId", notNullValue()))
                .andExpect(jsonPath("$.status", notNullValue()))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andReturn();

            JsonNode startJson = objectMapper.readTree(startResult.getResponse().getContentAsString());
            String taskId = startJson.get("taskId").asText();

            org.junit.jupiter.api.Assertions.assertTrue(
                enteredRepository.await(3, TimeUnit.SECONDS),
                "Async export task should reach repository query"
            );

            mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/resources/export-async")
                    .param("maxItems", "20")
                    .param("skipCount", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.paging.skipCount", is(0)))
                .andExpect(jsonPath("$.paging.maxItems", is(20)))
                .andExpect(jsonPath("$.paging.totalItems", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.paging.hasMoreItems", notNullValue()));

            mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/resources/export-async/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId", is(taskId)))
                .andExpect(jsonPath("$.status", notNullValue()));

            mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/resources/export-async/{taskId}/download", taskId))
                .andExpect(status().isConflict());

            mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/{taskId}/cancel", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId", is(taskId)))
                .andExpect(jsonPath("$.status", is("CANCELLED")));
        } finally {
            releaseRepository.countDown();
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can filter summary and cleanup rendition resources async export tasks")
    void diagnosticsRenditionResourcesAsyncExportGovernanceForAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/cleanup"))
            .andExpect(status().isOk());

        Mockito.when(documentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 500), 0))
            .thenThrow(new RuntimeException("simulated export failure"));

        String completedTaskId = startRenditionResourcesAsyncExportTask();
        assertEquals("COMPLETED", awaitRenditionResourcesAsyncTaskTerminalStatus(completedTaskId));

        String failedTaskId = startRenditionResourcesAsyncExportTask();
        assertEquals("FAILED", awaitRenditionResourcesAsyncTaskTerminalStatus(failedTaskId));

        mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/resources/export-async")
                .param("status", "completed"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count", is(1)))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].taskId", is(completedTaskId)))
            .andExpect(jsonPath("$.items[0].status", is("COMPLETED")));

        MvcResult summaryResult = mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/resources/export-async/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completedCount", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.failedCount", greaterThanOrEqualTo(1)))
            .andReturn();

        JsonNode summaryPayload = objectMapper.readTree(summaryResult.getResponse().getContentAsString());
        long queuedCount = summaryPayload.path("queuedCount").asLong();
        long runningCount = summaryPayload.path("runningCount").asLong();
        long completedCount = summaryPayload.path("completedCount").asLong();
        long cancelledCount = summaryPayload.path("cancelledCount").asLong();
        long failedCount = summaryPayload.path("failedCount").asLong();
        long totalCount = summaryPayload.path("totalCount").asLong();
        long activeCount = summaryPayload.path("activeCount").asLong();
        long terminalCount = summaryPayload.path("terminalCount").asLong();

        assertEquals(totalCount, queuedCount + runningCount + completedCount + cancelledCount + failedCount);
        assertEquals(activeCount, queuedCount + runningCount);
        assertEquals(terminalCount, completedCount + cancelledCount + failedCount);

        mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/resources/export-async/summary")
                .param("status", "completed"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCount", is(1)))
            .andExpect(jsonPath("$.queuedCount", is(0)))
            .andExpect(jsonPath("$.runningCount", is(0)))
            .andExpect(jsonPath("$.completedCount", is(1)))
            .andExpect(jsonPath("$.cancelledCount", is(0)))
            .andExpect(jsonPath("$.failedCount", is(0)))
            .andExpect(jsonPath("$.activeCount", is(0)))
            .andExpect(jsonPath("$.terminalCount", is(1)));

        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/cleanup")
                .param("status", "completed"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletedCount", is(1)))
            .andExpect(jsonPath("$.statusFilter", is("COMPLETED")));

        mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/resources/export-async/{taskId}", completedTaskId))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/resources/export-async/{taskId}", failedTaskId))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/cleanup"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletedCount", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("terminal")));

        mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/resources/export-async/{taskId}", failedTaskId))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Rendition resources async start deduplicates to active task with same filters")
    void diagnosticsRenditionResourcesAsyncStartDeduplicatesToActiveTask() throws Exception {
        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/cleanup"))
            .andExpect(status().isOk());

        CountDownLatch enteredRepository = new CountDownLatch(1);
        CountDownLatch releaseRepository = new CountDownLatch(1);
        Mockito.when(documentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
            .thenAnswer(invocation -> {
                enteredRepository.countDown();
                releaseRepository.await(3, TimeUnit.SECONDS);
                return new PageImpl<>(List.of(), PageRequest.of(0, 500), 0);
            });

        try {
            MvcResult firstStart = mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async")
                    .contentType("application/json")
                    .content("{\"days\":7,\"limit\":500}"))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.taskId", notNullValue()))
                .andExpect(jsonPath("$.deduplicated", is(false)))
                .andExpect(jsonPath("$.deduplicatedFromTaskId", nullValue()))
                .andReturn();
            String runningTaskId = objectMapper.readTree(firstStart.getResponse().getContentAsString()).path("taskId").asText();
            assertFalse(runningTaskId.isBlank());
            assertTrue(enteredRepository.await(3, TimeUnit.SECONDS));

            mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async")
                    .contentType("application/json")
                    .content("{\"days\":7,\"limit\":500}"))
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
    @DisplayName("Admin can retry terminal rendition resources async export tasks")
    void diagnosticsRenditionResourcesAsyncRetryForAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/cleanup"))
            .andExpect(status().isOk());

        Mockito.when(documentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 500), 0));

        String completedTaskId = startRenditionResourcesAsyncExportTask();
        assertEquals("COMPLETED", awaitRenditionResourcesAsyncTaskTerminalStatus(completedTaskId));

        MvcResult retryResult = mockMvc.perform(post(
                "/api/v1/preview/diagnostics/renditions/resources/export-async/{taskId}/retry",
                completedTaskId))
            .andExpect(status().isAccepted())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.taskId", notNullValue()))
            .andExpect(jsonPath("$.status", notNullValue()))
            .andExpect(jsonPath("$.deduplicated", is(false)))
            .andExpect(jsonPath("$.deduplicatedFromTaskId", nullValue()))
            .andReturn();
        String retryTaskId = objectMapper.readTree(retryResult.getResponse().getContentAsString()).path("taskId").asText();

        assertFalse(retryTaskId.isBlank());
        assertFalse(completedTaskId.equals(retryTaskId));
        assertEquals("COMPLETED", awaitRenditionResourcesAsyncTaskTerminalStatus(retryTaskId));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Rendition resources async retry deduplicates to active task with same filters")
    void diagnosticsRenditionResourcesAsyncRetryDeduplicatesToActiveTask() throws Exception {
        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/cleanup"))
            .andExpect(status().isOk());

        Mockito.when(documentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 500), 0));

        String completedTaskId = startRenditionResourcesAsyncExportTask();
        assertEquals("COMPLETED", awaitRenditionResourcesAsyncTaskTerminalStatus(completedTaskId));

        CountDownLatch enteredRepository = new CountDownLatch(1);
        CountDownLatch releaseRepository = new CountDownLatch(1);
        Mockito.when(documentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
            .thenAnswer(invocation -> {
                enteredRepository.countDown();
                releaseRepository.await(3, TimeUnit.SECONDS);
                return new PageImpl<>(List.of(), PageRequest.of(0, 500), 0);
            });

        String runningTaskId;
        try {
            runningTaskId = startRenditionResourcesAsyncExportTask();
            assertTrue(enteredRepository.await(3, TimeUnit.SECONDS));

            mockMvc.perform(post(
                    "/api/v1/preview/diagnostics/renditions/resources/export-async/{taskId}/retry",
                    completedTaskId))
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
    @DisplayName("Admin can bulk retry terminal rendition resources async export tasks")
    void diagnosticsRenditionResourcesAsyncRetryTerminalBulkForAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/cleanup"))
            .andExpect(status().isOk());

        Mockito.when(documentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 500), 0));

        String completedTaskId = startRenditionResourcesAsyncExportTask();
        assertEquals("COMPLETED", awaitRenditionResourcesAsyncTaskTerminalStatus(completedTaskId));

        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/retry-terminal")
                .param("limit", "20"))
            .andExpect(status().isAccepted())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.requested", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.retried", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.results", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/retry-terminal/by-task-ids")
                .contentType("application/json")
                .content("{\"sourceTaskIds\":[\"" + completedTaskId + "\"]}"))
            .andExpect(status().isAccepted())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.requested", is(1)))
            .andExpect(jsonPath("$.retried", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].sourceTaskId", is(completedTaskId)))
            .andExpect(jsonPath("$.results[0].outcome", anyOf(is("RETRIED"), is("REUSED"))));

        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/retry-terminal/dry-run")
                .param("status", "COMPLETED")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limit", is(20)))
            .andExpect(jsonPath("$.statusFilter", is("COMPLETED")))
            .andExpect(jsonPath("$.requested", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.retryable", greaterThanOrEqualTo(0)))
            .andExpect(jsonPath("$.results", notNullValue()));

        mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/resources/export-async/retry-terminal/dry-run/export")
                .param("status", "COMPLETED")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Preview-Rendition-Resources-Retry-Dry-Run-Count"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("text/csv;charset=UTF-8"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                org.hamcrest.Matchers.containsString("sourceTaskId,sourceStatus,outcome,reasonCode,message")));

        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/retry-terminal")
                .param("status", "RUNNING"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can cancel active rendition resources async export tasks")
    void diagnosticsRenditionResourcesAsyncCancelActiveForAdmin() throws Exception {
        CountDownLatch enteredRepository = new CountDownLatch(1);
        CountDownLatch releaseRepository = new CountDownLatch(1);

        Mockito.when(documentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
            .thenAnswer(invocation -> {
                enteredRepository.countDown();
                releaseRepository.await(3, TimeUnit.SECONDS);
                return new PageImpl<>(List.of(), PageRequest.of(0, 500), 0);
            });

        try {
            startRenditionResourcesAsyncExportTask();
            assertTrue(enteredRepository.await(3, TimeUnit.SECONDS));

            mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/cancel-active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.remainingActiveCount", is(0)))
                .andExpect(jsonPath("$.statusFilter", nullValue()))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Cancelled")));
        } finally {
            releaseRepository.countDown();
        }

        CountDownLatch enteredRepositoryByFilter = new CountDownLatch(1);
        CountDownLatch releaseRepositoryByFilter = new CountDownLatch(1);
        Mockito.when(documentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
            .thenAnswer(invocation -> {
                enteredRepositoryByFilter.countDown();
                releaseRepositoryByFilter.await(3, TimeUnit.SECONDS);
                return new PageImpl<>(List.of(), PageRequest.of(0, 500), 0);
            });
        try {
            startRenditionResourcesAsyncExportTask();
            assertTrue(enteredRepositoryByFilter.await(3, TimeUnit.SECONDS));

            mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/cancel-active")
                    .param("status", "running"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.remainingActiveCount", is(0)))
                .andExpect(jsonPath("$.statusFilter", is("RUNNING")))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("RUNNING")));
        } finally {
            releaseRepositoryByFilter.countDown();
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Invalid rendition resources async status filter returns bad request")
    void diagnosticsRenditionResourcesAsyncInvalidStatusFilterReturnsBadRequest() throws Exception {
        String invalidStatus = "NOT_A_VALID_STATUS";
        String expectedMessage = "Unknown async export status: " + invalidStatus;

        mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/resources/export-async")
                .param("status", invalidStatus))
            .andExpect(status().isBadRequest())
            .andExpect(status().reason(is(expectedMessage)));

        mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/resources/export-async/summary")
                .param("status", invalidStatus))
            .andExpect(status().isBadRequest())
            .andExpect(status().reason(is(expectedMessage)));

        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/cleanup")
                .param("status", invalidStatus))
            .andExpect(status().isBadRequest())
            .andExpect(status().reason(is(expectedMessage)));

        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/cancel-active")
                .param("status", invalidStatus))
            .andExpect(status().isBadRequest())
            .andExpect(status().reason(is(expectedMessage)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Cleanup rejects non-terminal rendition resources async status filter")
    void diagnosticsRenditionResourcesAsyncCleanupRejectsNonTerminalStatusFilter() throws Exception {
        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/cleanup")
                .param("status", "running"))
            .andExpect(status().isBadRequest())
            .andExpect(status().reason(is("status filter only supports terminal states: COMPLETED, CANCELLED, FAILED, TIMED_OUT, EXPIRED")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Cancel active rejects terminal rendition resources async status filter")
    void diagnosticsRenditionResourcesAsyncCancelActiveRejectsTerminalStatusFilter() throws Exception {
        mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async/cancel-active")
                .param("status", "completed"))
            .andExpect(status().isBadRequest())
            .andExpect(status().reason(is("status filter only supports active states: QUEUED, RUNNING")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Invalid declined requeue dry-run async status filter returns bad request")
    void diagnosticsQueueDeclinedRequeueDryRunAsyncInvalidStatusFilterReturnsBadRequest() throws Exception {
        String invalidStatus = "NOT_A_VALID_STATUS";
        String expectedMessage = "Unknown async requeue dry-run export status: " + invalidStatus;

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async")
                .param("status", invalidStatus))
            .andExpect(status().isBadRequest())
            .andExpect(status().reason(is(expectedMessage)));

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/summary")
                .param("status", invalidStatus))
            .andExpect(status().isBadRequest())
            .andExpect(status().reason(is(expectedMessage)));

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/cleanup")
                .param("status", invalidStatus))
            .andExpect(status().isBadRequest())
            .andExpect(status().reason(is(expectedMessage)));

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/cancel-active")
                .param("status", invalidStatus))
            .andExpect(status().isBadRequest())
            .andExpect(status().reason(is(expectedMessage)));

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/retry-terminal")
                .param("status", invalidStatus))
            .andExpect(status().isBadRequest())
            .andExpect(status().reason(is(expectedMessage)));

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/retry-terminal/dry-run")
                .param("status", invalidStatus))
            .andExpect(status().isBadRequest())
            .andExpect(status().reason(is(expectedMessage)));

        mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/retry-terminal/dry-run/export")
                .param("status", invalidStatus))
            .andExpect(status().isBadRequest())
            .andExpect(status().reason(is(expectedMessage)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Retry terminal rejects active declined requeue dry-run async status filter")
    void diagnosticsQueueDeclinedRequeueDryRunAsyncRetryTerminalRejectsActiveStatusFilter() throws Exception {
        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/retry-terminal")
                .param("status", "running"))
            .andExpect(status().isBadRequest())
            .andExpect(status().reason(is("status filter only supports terminal states: COMPLETED, CANCELLED, FAILED, TIMED_OUT, EXPIRED")));

        mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/retry-terminal/dry-run")
                .param("status", "queued"))
            .andExpect(status().isBadRequest())
            .andExpect(status().reason(is("status filter only supports terminal states: COMPLETED, CANCELLED, FAILED, TIMED_OUT, EXPIRED")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin summary includes confidence and reason/category aggregation")
    void diagnosticsSummaryIncludesConfidenceAndAggregations() throws Exception {
        Document temporary = new Document();
        temporary.setId(UUID.randomUUID());
        temporary.setName("temporary.pdf");
        temporary.setPath("/Root/Documents/temporary.pdf");
        temporary.setMimeType("application/pdf");
        temporary.setPreviewStatus(PreviewStatus.FAILED);
        temporary.setPreviewFailureReason("gateway timeout");

        Document unsupported = new Document();
        unsupported.setId(UUID.randomUUID());
        unsupported.setName("unsupported.bin");
        unsupported.setPath("/Root/Documents/unsupported.bin");
        unsupported.setMimeType("application/octet-stream");
        unsupported.setPreviewStatus(PreviewStatus.UNSUPPORTED);
        unsupported.setPreviewFailureReason("Preview not supported for mime type: application/octet-stream");

        Document permanent = new Document();
        permanent.setId(UUID.randomUUID());
        permanent.setName("broken.pdf");
        permanent.setPath("/Root/Documents/broken.pdf");
        permanent.setMimeType("application/pdf");
        permanent.setPreviewStatus(PreviewStatus.FAILED);
        permanent.setPreviewFailureReason("Missing root object specification in trailer.");

        Mockito.when(documentRepository.countPreviewFailuresByWindow(anyList(), any()))
            .thenReturn(10L);
        Mockito.when(documentRepository.findRecentPreviewFailuresByWindow(anyList(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(temporary, unsupported, permanent), PageRequest.of(0, 3), 3));

        mockMvc.perform(get("/api/v1/preview/diagnostics/failures/summary?sampleLimit=3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalFailures", is(10)))
            .andExpect(jsonPath("$.sampledFailures", is(3)))
            .andExpect(jsonPath("$.sampleLimit", is(3)))
            .andExpect(jsonPath("$.windowDays", is(7)))
            .andExpect(jsonPath("$.windowStart", notNullValue()))
            .andExpect(jsonPath("$.sampleTruncated", is(true)))
            .andExpect(jsonPath("$.confidenceLevel", is("LOW")))
            .andExpect(jsonPath("$.confidenceReason", is("sample_truncated")))
            .andExpect(jsonPath("$.statusCounts", hasSize(2)))
            .andExpect(jsonPath("$.statusCounts[0].status", is("FAILED")))
            .andExpect(jsonPath("$.statusCounts[0].count", is(2)))
            .andExpect(jsonPath("$.statusCounts[1].status", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.statusCounts[1].count", is(1)))
            .andExpect(jsonPath("$.categoryCounts", hasSize(3)))
            .andExpect(jsonPath("$.topReasons", hasSize(3)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Failure summary prefers rendition-backed effective statuses")
    void diagnosticsSummaryPrefersRenditionSummary() throws Exception {
        UUID docId = UUID.randomUUID();
        LocalDateTime updated = LocalDateTime.of(2026, 3, 28, 11, 12, 13);

        Document document = new Document();
        document.setId(docId);
        document.setName("unsupported.bin");
        document.setPath("/Root/Documents/unsupported.bin");
        document.setMimeType("application/octet-stream");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("Preview generation failed");
        document.setPreviewLastUpdated(updated);

        Mockito.when(documentRepository.countPreviewFailuresByWindow(anyList(), any()))
            .thenReturn(1L);
        Mockito.when(documentRepository.findRecentPreviewFailuresByWindow(anyList(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 1), 1));
        Mockito.when(renditionResourceService.summarizeDocument(document)).thenReturn(
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

        mockMvc.perform(get("/api/v1/preview/diagnostics/failures/summary?sampleLimit=1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sampledFailures", is(1)))
            .andExpect(jsonPath("$.statusCounts", hasSize(1)))
            .andExpect(jsonPath("$.statusCounts[0].status", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.statusCounts[0].count", is(1)))
            .andExpect(jsonPath("$.categoryCounts", hasSize(1)))
            .andExpect(jsonPath("$.categoryCounts[0].category", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.categoryCounts[0].retryable", is(false)))
            .andExpect(jsonPath("$.topReasons", hasSize(1)))
            .andExpect(jsonPath("$.topReasons[0].reason", is("Preview definition is not registered for generic binary sources")))
            .andExpect(jsonPath("$.topReasons[0].category", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.topReasons[0].retryable", is(false)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Limit is clamped to protect the API")
    void limitIsClamped() throws Exception {
        Mockito.when(documentRepository.findRecentPreviewFailuresByWindow(anyList(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 200), 0));

        mockMvc.perform(get("/api/v1/preview/diagnostics/failures?limit=9999"))
            .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        Mockito.verify(documentRepository).findRecentPreviewFailuresByWindow(
            eq(List.of(PreviewStatus.FAILED, PreviewStatus.UNSUPPORTED)),
            any(),
            captor.capture()
        );
        Pageable pageable = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(200, pageable.getPageSize());
        org.junit.jupiter.api.Assertions.assertEquals(0, pageable.getPageNumber());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Summary sample limit is clamped to protect the API")
    void summaryLimitIsClamped() throws Exception {
        Mockito.when(documentRepository.countPreviewFailuresByWindow(anyList(), any()))
            .thenReturn(0L);
        Mockito.when(documentRepository.findRecentPreviewFailuresByWindow(anyList(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 2000), 0));

        mockMvc.perform(get("/api/v1/preview/diagnostics/failures/summary?sampleLimit=99999"))
            .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        Mockito.verify(documentRepository).findRecentPreviewFailuresByWindow(
            eq(List.of(PreviewStatus.FAILED, PreviewStatus.UNSUPPORTED)),
            any(),
            captor.capture()
        );
        Pageable pageable = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(2000, pageable.getPageSize());
        org.junit.jupiter.api.Assertions.assertEquals(0, pageable.getPageNumber());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Days=0 disables window filtering and returns HIGH confidence when sample is complete")
    void summarySupportsAllWindow() throws Exception {
        Mockito.when(documentRepository.countPreviewFailuresByWindow(anyList(), eq(null)))
            .thenReturn(0L);
        Mockito.when(documentRepository.findRecentPreviewFailuresByWindow(anyList(), eq(null), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 500), 0));

        mockMvc.perform(get("/api/v1/preview/diagnostics/failures/summary?sampleLimit=500&days=0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowDays", is(0)))
            .andExpect(jsonPath("$.windowStart", nullValue()))
            .andExpect(jsonPath("$.confidenceLevel", is("HIGH")))
            .andExpect(jsonPath("$.confidenceReason", is("sample_complete")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can inspect CAD failover diagnostics")
    void diagnosticsCadFailoverForAdmin() throws Exception {
        Instant lastSuccess = Instant.parse("2026-03-06T06:00:00Z");
        Instant lastFailure = Instant.parse("2026-03-06T06:05:00Z");
        List<String> endpoints = List.of(
            "http://cad-render-primary.local/convert",
            "http://cad-render-fallback.local/convert"
        );

        Mockito.when(cadRenderEndpointRegistry.isCadPreviewEnabled()).thenReturn(true);
        Mockito.when(cadRenderEndpointRegistry.resolveEndpoints()).thenReturn(endpoints);
        Mockito.when(cadRenderFailoverTracker.snapshot(endpoints)).thenReturn(List.of(
            new CadRenderFailoverTracker.EndpointStats(
                "http://cad-render-primary.local/convert",
                5,
                2,
                lastSuccess,
                lastFailure,
                "connection refused",
                2,
                "OPEN",
                Instant.parse("2026-03-06T06:06:00Z"),
                Instant.parse("2026-03-06T06:05:00Z"),
                false
            ),
            new CadRenderFailoverTracker.EndpointStats(
                "http://cad-render-fallback.local/convert",
                3,
                0,
                lastSuccess,
                null,
                null,
                0,
                "CLOSED",
                null,
                null,
                false
            )
        ));
        Mockito.when(cadRenderFailoverTracker.isCircuitBreakerEnabled()).thenReturn(true);
        Mockito.when(cadRenderFailoverTracker.getCircuitFailureThreshold()).thenReturn(3);
        Mockito.when(cadRenderFailoverTracker.getCircuitOpenMs()).thenReturn(120000L);
        Mockito.when(cadRenderFailoverTracker.getHalfOpenTrialTimeoutMs()).thenReturn(30000L);

        mockMvc.perform(get("/api/v1/preview/diagnostics/cad-failover"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cadPreviewEnabled", is(true)))
            .andExpect(jsonPath("$.configured", is(true)))
            .andExpect(jsonPath("$.circuitBreakerEnabled", is(true)))
            .andExpect(jsonPath("$.circuitFailureThreshold", is(3)))
            .andExpect(jsonPath("$.circuitOpenMs", is(120000)))
            .andExpect(jsonPath("$.halfOpenTrialTimeoutMs", is(30000)))
            .andExpect(jsonPath("$.endpoints", hasSize(2)))
            .andExpect(jsonPath("$.endpointStats", hasSize(2)))
            .andExpect(jsonPath("$.endpointStats[0].endpoint", is("http://cad-render-primary.local/convert")))
            .andExpect(jsonPath("$.endpointStats[0].successCount", is(5)))
            .andExpect(jsonPath("$.endpointStats[0].failureCount", is(2)))
            .andExpect(jsonPath("$.endpointStats[0].lastSuccessAt", is("2026-03-06T06:00:00Z")))
            .andExpect(jsonPath("$.endpointStats[0].lastFailureAt", is("2026-03-06T06:05:00Z")))
            .andExpect(jsonPath("$.endpointStats[0].lastFailureReason", is("connection refused")))
            .andExpect(jsonPath("$.endpointStats[0].consecutiveFailureCount", is(2)))
            .andExpect(jsonPath("$.endpointStats[0].circuitState", is("OPEN")))
            .andExpect(jsonPath("$.endpointStats[0].circuitOpenUntil", is("2026-03-06T06:06:00Z")))
            .andExpect(jsonPath("$.endpointStats[0].lastCircuitOpenedAt", is("2026-03-06T06:05:00Z")))
            .andExpect(jsonPath("$.endpointStats[0].halfOpenInFlight", is(false)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can inspect preview transform traces")
    void diagnosticsTransformTracesForAdmin() throws Exception {
        UUID documentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant startedAt = Instant.parse("2026-03-06T07:00:00Z");
        Instant finishedAt = Instant.parse("2026-03-06T07:00:03Z");

        Mockito.when(previewTransformTraceBuffer.snapshot(eq(20), eq("pv-1")))
            .thenReturn(List.of(
                new PreviewTransformTraceBuffer.TraceSnapshot(
                    "pv-1",
                    documentId,
                    "application/pdf",
                    "preview",
                    startedAt,
                    finishedAt,
                    "READY",
                    false,
                    null,
                    "Preview completed",
                    List.of(
                        new PreviewTransformTraceBuffer.TraceEventSnapshot(
                            Instant.parse("2026-03-06T07:00:01Z"),
                            "ROUTE",
                            "pdf"
                        )
                    )
                )
            ));

        mockMvc.perform(get("/api/v1/preview/diagnostics/traces?limit=20&requestId=pv-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].requestId", is("pv-1")))
            .andExpect(jsonPath("$[0].documentId", is(documentId.toString())))
            .andExpect(jsonPath("$[0].mimeType", is("application/pdf")))
            .andExpect(jsonPath("$[0].source", is("preview")))
            .andExpect(jsonPath("$[0].startedAt", is("2026-03-06T07:00:00Z")))
            .andExpect(jsonPath("$[0].finishedAt", is("2026-03-06T07:00:03Z")))
            .andExpect(jsonPath("$[0].status", is("READY")))
            .andExpect(jsonPath("$[0].retryNeeded", is(false)))
            .andExpect(jsonPath("$[0].latestMessage", is("Preview completed")))
            .andExpect(jsonPath("$[0].events", hasSize(1)))
            .andExpect(jsonPath("$[0].events[0].stage", is("ROUTE")))
            .andExpect(jsonPath("$[0].events[0].message", is("pdf")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can inspect and replay preview dead-letter entries")
    void diagnosticsDeadLetterForAdmin() throws Exception {
        UUID documentId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        Instant failedAt = Instant.parse("2026-03-06T08:00:00Z");

        Document document = new Document();
        document.setId(documentId);
        document.setName("dead-letter.pdf");
        document.setPath("/Root/Documents/dead-letter.pdf");
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);

        Mockito.when(previewDeadLetterRegistry.list(50)).thenReturn(List.of(
            new PreviewDeadLetterRegistry.DeadLetterEntry(
                PreviewDeadLetterRegistry.buildEntryKey(documentId, "preview"),
                documentId,
                "preview",
                "Timeout contacting preview service",
                "TEMPORARY",
                "cad",
                "QUEUE_RETRY_EXHAUSTED",
                failedAt,
                5,
                2,
                null,
                0
            )
        ));
        Mockito.when(previewDeadLetterRegistry.isEnabled()).thenReturn(true);
        Mockito.when(previewDeadLetterRegistry.isRedisEnabled()).thenReturn(true);
        Mockito.when(previewDeadLetterRegistry.isRedisActive()).thenReturn(true);
        Mockito.when(previewDeadLetterRegistry.getTtlMs()).thenReturn(604800000L);
        Mockito.when(previewDeadLetterRegistry.getItemCount()).thenReturn(1);
        Mockito.when(previewDeadLetterRegistry.getMaxEntries()).thenReturn(5000);
        Mockito.when(documentRepository.findAllById(any(Iterable.class))).thenReturn(List.of(document));

        mockMvc.perform(get("/api/v1/preview/diagnostics/dead-letter?limit=50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled", is(true)))
            .andExpect(jsonPath("$.redisEnabled", is(true)))
            .andExpect(jsonPath("$.backendMode", is("REDIS")))
            .andExpect(jsonPath("$.ttlMs", is(604800000)))
            .andExpect(jsonPath("$.itemCount", is(1)))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].entryKey", is(PreviewDeadLetterRegistry.buildEntryKey(documentId, "preview"))))
            .andExpect(jsonPath("$.items[0].documentId", is(documentId.toString())))
            .andExpect(jsonPath("$.items[0].renditionKey", is("preview")))
            .andExpect(jsonPath("$.items[0].policyKey", is("cad")))
            .andExpect(jsonPath("$.items[0].sourceStage", is("QUEUE_RETRY_EXHAUSTED")))
            .andExpect(jsonPath("$.items[0].occurrences", is(2)));

        LocalDateTime replayUpdatedAt = LocalDateTime.of(2026, 3, 29, 12, 45);
        Mockito.when(previewQueueService.enqueue(documentId, true)).thenReturn(
            new PreviewQueueService.PreviewQueueStatus(
                documentId,
                PreviewStatus.PROCESSING,
                true,
                0,
                null,
                "Preview queued",
                null,
                null,
                replayUpdatedAt
            )
        );

        mockMvc.perform(post("/api/v1/preview/diagnostics/dead-letter/replay-batch")
                .contentType("application/json")
                .content("{\"documentIds\":[\"99999999-9999-9999-9999-999999999999\"],\"force\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requested", is(1)))
            .andExpect(jsonPath("$.queued", is(1)))
            .andExpect(jsonPath("$.failed", is(0)))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].outcome", is("QUEUED")))
            .andExpect(jsonPath("$.results[0].previewStatus", is("PROCESSING")))
            .andExpect(jsonPath("$.results[0].previewFailureReason", nullValue()))
            .andExpect(jsonPath("$.results[0].previewFailureCategory", nullValue()))
            .andExpect(jsonPath("$.results[0].previewLastUpdated", is("2026-03-29T12:45:00")));

        Mockito.verify(previewDeadLetterRegistry, Mockito.atLeastOnce()).remove(documentId, "preview");
        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("PREVIEW_DEAD_LETTER_REPLAY"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            eq("user"),
            Mockito.contains("queued=1")
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Dead-letter replay batch prefers shared effective preview snapshot when queue status is sparse")
    void diagnosticsDeadLetterReplayBatchPrefersSharedEffectivePreviewSnapshot() throws Exception {
        UUID documentId = UUID.randomUUID();
        String entryKey = PreviewDeadLetterRegistry.buildEntryKey(documentId, "preview");
        Instant failedAt = Instant.parse("2026-03-29T12:10:00Z");
        Instant nextAttemptAt = Instant.parse("2026-03-29T12:20:00Z");
        LocalDateTime updatedAt = LocalDateTime.of(2026, 3, 29, 12, 25);

        Document document = new Document();
        document.setId(documentId);
        document.setName("dead-letter.bin");
        document.setPath("/Root/Documents/dead-letter.bin");
        document.setMimeType("application/octet-stream");

        Mockito.when(documentRepository.findAllById(any(Iterable.class))).thenReturn(List.of(document));
        Mockito.when(previewDeadLetterRegistry.findByEntryKey(entryKey)).thenReturn(
            new PreviewDeadLetterRegistry.DeadLetterEntry(
                entryKey,
                documentId,
                "preview",
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                "default",
                "QUEUE_RETRY_EXHAUSTED",
                failedAt,
                2,
                0,
                null,
                0
            )
        );
        Mockito.when(previewQueueService.enqueue(eq(documentId), eq(true))).thenReturn(
            new PreviewQueueService.PreviewQueueStatus(
                documentId,
                null,
                true,
                3,
                nextAttemptAt,
                "Preview queued"
            )
        );
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
                updatedAt
            )
        );

        mockMvc.perform(post("/api/v1/preview/diagnostics/dead-letter/replay-batch")
                .contentType("application/json")
                .content("""
                    {
                      "entryKeys": ["%s"],
                      "force": true
                    }
                    """.formatted(entryKey)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queued", is(1)))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].previewStatus", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.results[0].previewFailureReason", is("Preview definition is not registered for generic binary sources")))
            .andExpect(jsonPath("$.results[0].previewFailureCategory", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.results[0].previewLastUpdated", is("2026-03-29T12:25:00")))
            .andExpect(jsonPath("$.results[0].attempts", is(3)))
            .andExpect(jsonPath("$.results[0].nextAttemptAt", is("2026-03-29T12:20:00Z")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Dead-letter diagnostics prefer rendition-backed preview status")
    void diagnosticsDeadLetterPrefersRenditionSummary() throws Exception {
        UUID documentId = UUID.randomUUID();
        Instant failedAt = Instant.parse("2026-03-06T08:00:00Z");

        Document document = new Document();
        document.setId(documentId);
        document.setName("dead-letter.bin");
        document.setPath("/Root/Documents/dead-letter.bin");
        document.setMimeType("application/octet-stream");
        document.setPreviewStatus(PreviewStatus.FAILED);

        Mockito.when(previewDeadLetterRegistry.list(50)).thenReturn(List.of(
            new PreviewDeadLetterRegistry.DeadLetterEntry(
                PreviewDeadLetterRegistry.buildEntryKey(documentId, "preview"),
                documentId,
                "preview",
                "Preview generation failed",
                "TEMPORARY",
                "cad",
                "QUEUE_RETRY_EXHAUSTED",
                failedAt,
                1,
                1,
                null,
                0
            )
        ));
        Mockito.when(previewDeadLetterRegistry.isEnabled()).thenReturn(true);
        Mockito.when(previewDeadLetterRegistry.isRedisEnabled()).thenReturn(true);
        Mockito.when(previewDeadLetterRegistry.isRedisActive()).thenReturn(true);
        Mockito.when(previewDeadLetterRegistry.getTtlMs()).thenReturn(604800000L);
        Mockito.when(previewDeadLetterRegistry.getItemCount()).thenReturn(1);
        Mockito.when(previewDeadLetterRegistry.getMaxEntries()).thenReturn(5000);
        Mockito.when(documentRepository.findAllById(any(Iterable.class))).thenReturn(List.of(document));
        Mockito.when(renditionResourceService.summarizeDocument(document)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                documentId,
                true,
                "UNSUPPORTED",
                false,
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                LocalDateTime.of(2026, 3, 28, 12, 5, 0),
                null
            )
        );

        mockMvc.perform(get("/api/v1/preview/diagnostics/dead-letter?limit=50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].previewStatus", is("UNSUPPORTED")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can clear preview dead-letter entries")
    void diagnosticsDeadLetterClearForAdmin() throws Exception {
        UUID documentId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        String entryKey = PreviewDeadLetterRegistry.buildEntryKey(documentId, "preview");
        Instant failedAt = Instant.parse("2026-03-06T08:20:00Z");

        Mockito.when(previewDeadLetterRegistry.findByEntryKey(entryKey)).thenReturn(
            new PreviewDeadLetterRegistry.DeadLetterEntry(
                entryKey,
                documentId,
                "preview",
                "Preview not supported for mime type application/octet-stream",
                "UNSUPPORTED",
                "default",
                "QUEUE_RETRY_EXHAUSTED",
                failedAt,
                3,
                1,
                null,
                0
            )
        );

        mockMvc.perform(post("/api/v1/preview/diagnostics/dead-letter/clear-batch")
                .contentType("application/json")
                .content("""
                    {
                      "entryKeys": ["%s"]
                    }
                    """.formatted(entryKey)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requested", is(1)))
            .andExpect(jsonPath("$.deduplicated", is(1)))
            .andExpect(jsonPath("$.cleared", is(1)))
            .andExpect(jsonPath("$.failed", is(0)))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].documentId", is(documentId.toString())))
            .andExpect(jsonPath("$.results[0].entryKey", is(entryKey)))
            .andExpect(jsonPath("$.results[0].outcome", is("CLEARED")));

        Mockito.verify(previewDeadLetterRegistry, Mockito.atLeastOnce()).remove(documentId, "preview");
        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("PREVIEW_DEAD_LETTER_CLEARED"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            eq("user"),
            Mockito.contains("cleared=1")
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can export dead-letter CSV")
    void diagnosticsDeadLetterExportForAdmin() throws Exception {
        UUID documentId = UUID.fromString("88888888-8888-8888-8888-888888888888");
        Instant failedAt = Instant.parse("2026-03-06T08:10:00Z");

        Document document = new Document();
        document.setId(documentId);
        document.setName("dead-letter-export.pdf");
        document.setPath("/Root/Documents/dead-letter-export.pdf");
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);

        Mockito.when(previewDeadLetterRegistry.list(500)).thenReturn(List.of(
            new PreviewDeadLetterRegistry.DeadLetterEntry(
                PreviewDeadLetterRegistry.buildEntryKey(documentId, "preview"),
                documentId,
                "preview",
                "timeout",
                "TEMPORARY",
                "pdf",
                "QUEUE_RETRY_EXHAUSTED",
                failedAt,
                3,
                2,
                Instant.parse("2026-03-06T09:00:00Z"),
                1
            )
        ));
        Mockito.when(previewDeadLetterRegistry.isRedisActive()).thenReturn(true);
        Mockito.when(documentRepository.findAllById(any(Iterable.class))).thenReturn(List.of(document));

        mockMvc.perform(get("/api/v1/preview/diagnostics/dead-letter/export?limit=500"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Preview-Dead-Letter-Count", "1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("text/csv;charset=UTF-8"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("dead-letter-export.pdf")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString(documentId.toString())));

        Mockito.verify(auditService, Mockito.atLeastOnce()).logEvent(
            eq("PREVIEW_DEAD_LETTER_EXPORTED"),
            eq(null),
            eq("PREVIEW_DIAGNOSTICS"),
            eq("user"),
            Mockito.contains("exported=1")
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Dead-letter export prefers rendition-backed preview status")
    void diagnosticsDeadLetterExportPrefersRenditionSummary() throws Exception {
        UUID documentId = UUID.randomUUID();
        Instant failedAt = Instant.parse("2026-03-06T08:10:00Z");

        Document document = new Document();
        document.setId(documentId);
        document.setName("dead-letter-export.bin");
        document.setPath("/Root/Documents/dead-letter-export.bin");
        document.setMimeType("application/octet-stream");
        document.setPreviewStatus(PreviewStatus.FAILED);

        Mockito.when(previewDeadLetterRegistry.list(500)).thenReturn(List.of(
            new PreviewDeadLetterRegistry.DeadLetterEntry(
                PreviewDeadLetterRegistry.buildEntryKey(documentId, "preview"),
                documentId,
                "preview",
                "Preview generation failed",
                "TEMPORARY",
                "cad",
                "QUEUE_RETRY_EXHAUSTED",
                failedAt,
                1,
                1,
                null,
                0
            )
        ));
        Mockito.when(previewDeadLetterRegistry.isRedisActive()).thenReturn(true);
        Mockito.when(documentRepository.findAllById(any(Iterable.class))).thenReturn(List.of(document));
        Mockito.when(renditionResourceService.summarizeDocument(document)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                documentId,
                true,
                "UNSUPPORTED",
                false,
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                LocalDateTime.of(2026, 3, 28, 12, 15, 0),
                null
            )
        );

        mockMvc.perform(get("/api/v1/preview/diagnostics/dead-letter/export?limit=500"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("UNSUPPORTED")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can inspect and update failure policy profiles")
    void diagnosticsPolicyProfilesForAdmin() throws Exception {
        Mockito.when(previewFailurePolicyRegistry.listPolicies()).thenReturn(List.of(
            new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 3, 60000L, 1.6d, 0L, true),
            new PreviewFailurePolicyRegistry.PreviewFailurePolicy("cad", "CAD", 5, 60000L, 2.0d, 120000L, true)
        ));

        mockMvc.perform(get("/api/v1/preview/diagnostics/policies"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].key", is("default")))
            .andExpect(jsonPath("$[1].key", is("cad")))
            .andExpect(jsonPath("$[1].maxAttempts", is(5)));

        Mockito.when(previewFailurePolicyRegistry.upsert(eq("cad"), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("cad", "CAD", 6, 70000L, 2.2d, 180000L, true));

        mockMvc.perform(put("/api/v1/preview/diagnostics/policies/cad")
                .contentType("application/json")
                .content("{\"maxAttempts\":6,\"retryDelayMs\":70000,\"backoffMultiplier\":2.2,\"quietPeriodMs\":180000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key", is("cad")))
            .andExpect(jsonPath("$.maxAttempts", is(6)))
            .andExpect(jsonPath("$.retryDelayMs", is(70000)))
            .andExpect(jsonPath("$.backoffMultiplier", is(2.2)))
            .andExpect(jsonPath("$.quietPeriodMs", is(180000)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can inspect and operate rendition prevention entries")
    void diagnosticsPreventionForAdmin() throws Exception {
        UUID blockedId = UUID.randomUUID();
        Instant blockedAt = Instant.parse("2026-03-06T08:00:00Z");
        Instant lastHitAt = Instant.parse("2026-03-06T08:30:00Z");

        Document blockedDocument = new Document();
        blockedDocument.setId(blockedId);
        blockedDocument.setName("blocked.bin");
        blockedDocument.setPath("/Root/Documents/blocked.bin");
        blockedDocument.setMimeType("application/octet-stream");
        blockedDocument.setPreviewStatus(PreviewStatus.FAILED);

        Mockito.when(previewRenditionPreventionRegistry.isEnabled()).thenReturn(true);
        Mockito.when(previewRenditionPreventionRegistry.getBlockedCount()).thenReturn(1);
        Mockito.when(previewRenditionPreventionRegistry.getMaxBlocked()).thenReturn(5000);
        Mockito.when(previewRenditionPreventionRegistry.listAutoBlockCategories())
            .thenReturn(List.of("PERMANENT", "UNSUPPORTED"));
        Mockito.when(previewRenditionPreventionRegistry.list(eq(50)))
            .thenReturn(List.of(
                new PreviewRenditionPreventionRegistry.BlockedEntry(
                    blockedId,
                    "Preview not supported for mime type application/octet-stream",
                    "UNSUPPORTED",
                    blockedAt,
                    lastHitAt,
                    7
                )
            ));
        Mockito.when(documentRepository.findAllById(any()))
            .thenReturn(List.of(blockedDocument));

        mockMvc.perform(get("/api/v1/preview/diagnostics/prevention/blocked?limit=50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled", is(true)))
            .andExpect(jsonPath("$.blockedCount", is(1)))
            .andExpect(jsonPath("$.maxBlocked", is(5000)))
            .andExpect(jsonPath("$.autoBlockCategories", hasSize(2)))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].documentId", is(blockedId.toString())))
            .andExpect(jsonPath("$.items[0].name", is("blocked.bin")))
            .andExpect(jsonPath("$.items[0].mimeType", is("application/octet-stream")))
            .andExpect(jsonPath("$.items[0].category", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.items[0].hitCount", is(7)));

        mockMvc.perform(post("/api/v1/preview/diagnostics/prevention/%s/unblock".formatted(blockedId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentId", is(blockedId.toString())))
            .andExpect(jsonPath("$.unblocked", is(true)))
            .andExpect(jsonPath("$.queued", is(false)));

        Mockito.when(previewQueueService.enqueue(eq(blockedId), eq(true)))
            .thenReturn(new PreviewQueueService.PreviewQueueStatus(
                blockedId,
                PreviewStatus.FAILED,
                true,
                0,
                null,
                "Preview queued"
            ));

        mockMvc.perform(post("/api/v1/preview/diagnostics/prevention/%s/unblock-requeue?force=true".formatted(blockedId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentId", is(blockedId.toString())))
            .andExpect(jsonPath("$.unblocked", is(true)))
            .andExpect(jsonPath("$.queued", is(true)))
            .andExpect(jsonPath("$.message", is("Preview queued")))
            .andExpect(jsonPath("$.previewStatus", is("FAILED")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Prevention diagnostics prefer rendition-backed preview status")
    void diagnosticsPreventionPrefersRenditionSummary() throws Exception {
        UUID blockedId = UUID.randomUUID();
        Instant blockedAt = Instant.parse("2026-03-06T08:00:00Z");
        Instant lastHitAt = Instant.parse("2026-03-06T08:30:00Z");

        Document blockedDocument = new Document();
        blockedDocument.setId(blockedId);
        blockedDocument.setName("blocked.bin");
        blockedDocument.setPath("/Root/Documents/blocked.bin");
        blockedDocument.setMimeType("application/octet-stream");
        blockedDocument.setPreviewStatus(PreviewStatus.FAILED);

        Mockito.when(previewRenditionPreventionRegistry.isEnabled()).thenReturn(true);
        Mockito.when(previewRenditionPreventionRegistry.getBlockedCount()).thenReturn(1);
        Mockito.when(previewRenditionPreventionRegistry.getMaxBlocked()).thenReturn(5000);
        Mockito.when(previewRenditionPreventionRegistry.listAutoBlockCategories()).thenReturn(List.of("UNSUPPORTED"));
        Mockito.when(previewRenditionPreventionRegistry.list(eq(50))).thenReturn(List.of(
            new PreviewRenditionPreventionRegistry.BlockedEntry(
                blockedId,
                "Preview generation failed",
                "TEMPORARY",
                blockedAt,
                lastHitAt,
                1
            )
        ));
        Mockito.when(documentRepository.findAllById(any())).thenReturn(List.of(blockedDocument));
        Mockito.when(renditionResourceService.summarizeDocument(blockedDocument)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                blockedId,
                true,
                "UNSUPPORTED",
                false,
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                LocalDateTime.of(2026, 3, 28, 12, 10, 0),
                null
            )
        );

        mockMvc.perform(get("/api/v1/preview/diagnostics/prevention/blocked?limit=50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].previewStatus", is("UNSUPPORTED")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can run prevention batch actions")
    void diagnosticsPreventionBatchForAdmin() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        Mockito.when(previewQueueService.enqueue(eq(first), eq(true)))
            .thenReturn(new PreviewQueueService.PreviewQueueStatus(
                first,
                PreviewStatus.FAILED,
                true,
                0,
                null,
                "Preview queued"
            ));
        Mockito.when(previewQueueService.enqueue(eq(second), eq(true)))
            .thenThrow(new IllegalArgumentException("Document not found"));

        String payload = """
            {
              "documentIds": ["%s", "%s"],
              "force": true
            }
            """.formatted(first, second);

        mockMvc.perform(post("/api/v1/preview/diagnostics/prevention/unblock-batch")
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requested", is(2)))
            .andExpect(jsonPath("$.deduplicated", is(2)))
            .andExpect(jsonPath("$.unblocked", is(2)))
            .andExpect(jsonPath("$.queued", is(0)))
            .andExpect(jsonPath("$.failed", is(0)))
            .andExpect(jsonPath("$.results", hasSize(2)));

        mockMvc.perform(post("/api/v1/preview/diagnostics/prevention/unblock-requeue-batch")
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requested", is(2)))
            .andExpect(jsonPath("$.deduplicated", is(2)))
            .andExpect(jsonPath("$.unblocked", is(1)))
            .andExpect(jsonPath("$.queued", is(1)))
            .andExpect(jsonPath("$.failed", is(1)))
            .andExpect(jsonPath("$.results", hasSize(2)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can queue preview failures in batch and get aggregated outcome")
    void diagnosticsBatchQueueAggregatesOutcome() throws Exception {
        UUID queuedId = UUID.randomUUID();
        UUID skippedId = UUID.randomUUID();
        UUID failedId = UUID.randomUUID();
        LocalDateTime queuedUpdatedAt = LocalDateTime.of(2026, 3, 29, 11, 15);
        LocalDateTime skippedUpdatedAt = LocalDateTime.of(2026, 3, 29, 11, 20);

        Mockito.when(previewQueueService.enqueue(eq(queuedId), eq(false)))
            .thenReturn(new PreviewQueueService.PreviewQueueStatus(
                queuedId,
                PreviewStatus.PROCESSING,
                true,
                0,
                null,
                "Preview queued",
                null,
                null,
                queuedUpdatedAt
            ));
        Mockito.when(previewQueueService.enqueue(eq(skippedId), eq(false)))
            .thenReturn(new PreviewQueueService.PreviewQueueStatus(
                skippedId,
                PreviewStatus.UNSUPPORTED,
                false,
                0,
                null,
                "Preview unsupported",
                "Preview not supported for application/octet-stream",
                "UNSUPPORTED",
                skippedUpdatedAt
            ));
        Mockito.when(previewQueueService.enqueue(eq(failedId), eq(false)))
            .thenThrow(new IllegalArgumentException("Document not found"));

        String payload = """
            {
              "documentIds": ["%s", "%s", "%s"],
              "force": false
            }
            """.formatted(queuedId, skippedId, failedId);

        mockMvc.perform(post("/api/v1/preview/diagnostics/failures/queue-batch")
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requested", is(3)))
            .andExpect(jsonPath("$.deduplicated", is(3)))
            .andExpect(jsonPath("$.queued", is(1)))
            .andExpect(jsonPath("$.skipped", is(1)))
            .andExpect(jsonPath("$.failed", is(1)))
            .andExpect(jsonPath("$.results", hasSize(3)))
            .andExpect(jsonPath("$.results[0].previewStatus", is("PROCESSING")))
            .andExpect(jsonPath("$.results[0].previewFailureReason", nullValue()))
            .andExpect(jsonPath("$.results[0].previewFailureCategory", nullValue()))
            .andExpect(jsonPath("$.results[0].previewLastUpdated", is("2026-03-29T11:15:00")))
            .andExpect(jsonPath("$.results[1].previewStatus", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.results[1].previewFailureReason", is("Preview not supported for application/octet-stream")))
            .andExpect(jsonPath("$.results[1].previewFailureCategory", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.results[1].previewLastUpdated", is("2026-03-29T11:20:00")))
            .andExpect(jsonPath("$.results[2].previewFailureReason", nullValue()))
            .andExpect(jsonPath("$.results[2].previewFailureCategory", nullValue()))
            .andExpect(jsonPath("$.results[2].previewLastUpdated", nullValue()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Batch queue prefers shared effective preview snapshot when queue status is sparse")
    void diagnosticsBatchQueuePrefersSharedEffectivePreviewSnapshot() throws Exception {
        UUID documentId = UUID.randomUUID();
        Instant nextAttemptAt = Instant.parse("2026-03-29T12:00:00Z");
        LocalDateTime updatedAt = LocalDateTime.of(2026, 3, 29, 12, 5);

        Document document = new Document();
        document.setId(documentId);
        document.setName("unsupported.bin");
        document.setPath("/Root/Documents/unsupported.bin");
        document.setMimeType("application/octet-stream");

        Mockito.when(documentRepository.findAllById(any(Iterable.class))).thenReturn(List.of(document));
        Mockito.when(previewQueueService.enqueue(eq(documentId), eq(false)))
            .thenReturn(new PreviewQueueService.PreviewQueueStatus(
                documentId,
                null,
                true,
                2,
                nextAttemptAt,
                "Preview queued"
            ));
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
                updatedAt
            )
        );

        mockMvc.perform(post("/api/v1/preview/diagnostics/failures/queue-batch")
                .contentType("application/json")
                .content("""
                    {
                      "documentIds": ["%s"],
                      "force": false
                    }
                    """.formatted(documentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queued", is(1)))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].previewStatus", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.results[0].previewFailureReason", is("Preview definition is not registered for generic binary sources")))
            .andExpect(jsonPath("$.results[0].previewFailureCategory", is("UNSUPPORTED")))
            .andExpect(jsonPath("$.results[0].previewLastUpdated", is("2026-03-29T12:05:00")))
            .andExpect(jsonPath("$.results[0].attempts", is(2)))
            .andExpect(jsonPath("$.results[0].nextAttemptAt", is("2026-03-29T12:00:00Z")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can queue preview failures by reason across diagnostics window")
    void diagnosticsQueueByReasonAggregatesOutcome() throws Exception {
        UUID queuedId = UUID.randomUUID();
        UUID skippedId = UUID.randomUUID();

        Document queued = new Document();
        queued.setId(queuedId);
        queued.setMimeType("application/pdf");
        queued.setPreviewStatus(PreviewStatus.FAILED);
        queued.setPreviewFailureReason("Timeout contacting preview service");

        Document skipped = new Document();
        skipped.setId(skippedId);
        skipped.setMimeType("application/pdf");
        skipped.setPreviewStatus(PreviewStatus.FAILED);
        skipped.setPreviewFailureReason("Timeout contacting preview service");

        Mockito.when(documentRepository.countPreviewFailuresByReasonAndWindow(anyList(), any(), eq("Timeout contacting preview service")))
            .thenReturn(2L);
        Mockito.when(documentRepository.findPreviewFailuresByReasonAndWindow(anyList(), any(), eq("Timeout contacting preview service"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(queued, skipped), PageRequest.of(0, 100), 2));

        Mockito.when(previewQueueService.enqueue(eq(queuedId), eq(false)))
            .thenReturn(new PreviewQueueService.PreviewQueueStatus(
                queuedId,
                PreviewStatus.FAILED,
                true,
                0,
                null,
                "Preview queued"
            ));
        Mockito.when(previewQueueService.enqueue(eq(skippedId), eq(false)))
            .thenReturn(new PreviewQueueService.PreviewQueueStatus(
                skippedId,
                PreviewStatus.UNSUPPORTED,
                false,
                0,
                null,
                "Preview unsupported"
            ));

        String payload = """
            {
              "reason": "Timeout contacting preview service",
              "category": "TEMPORARY",
              "retryable": true,
              "maxDocuments": 100,
              "days": 7,
              "force": false
            }
            """;

        mockMvc.perform(post("/api/v1/preview/diagnostics/failures/queue-by-reason")
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reason", is("Timeout contacting preview service")))
            .andExpect(jsonPath("$.category", is("TEMPORARY")))
            .andExpect(jsonPath("$.retryable", is(true)))
            .andExpect(jsonPath("$.totalByReason", is(2)))
            .andExpect(jsonPath("$.matched", is(2)))
            .andExpect(jsonPath("$.queued", is(1)))
            .andExpect(jsonPath("$.skipped", is(1)))
            .andExpect(jsonPath("$.failed", is(0)))
            .andExpect(jsonPath("$.results", hasSize(2)));
    }

    private String startQueueDeclinedAsyncExportTask() throws Exception {
        return startQueueDeclinedAsyncExportTaskWithQuery("quiet");
    }

    private String startQueueDeclinedAsyncExportTaskWithQuery(String query) throws Exception {
        MvcResult startResult = mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/export-async")
                .contentType("application/json")
                .content("""
                    {"limit":500,"category":"QUIET_PERIOD","forceRequired":"NO","query":"%s","windowHours":24}
                    """.formatted(query)))
            .andExpect(status().isAccepted())
            .andExpect(header().exists("Location"))
            .andReturn();
        JsonNode payload = objectMapper.readTree(startResult.getResponse().getContentAsString());
        return payload.path("taskId").asText();
    }

    private String startQueueDeclinedRequeueDryRunAsyncExportTaskWithQuery(String query, boolean force) throws Exception {
        MvcResult startResult = mockMvc.perform(post("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async")
                .contentType("application/json")
                .content("""
                    {"limit":200,"category":"QUIET_PERIOD","forceRequired":"NO","query":"%s","windowHours":24,"force":%s}
                    """.formatted(query, force)))
            .andExpect(status().isAccepted())
            .andExpect(header().exists("Location"))
            .andReturn();
        JsonNode payload = objectMapper.readTree(startResult.getResponse().getContentAsString());
        return payload.path("taskId").asText();
    }

    private String awaitQueueDeclinedAsyncTaskTerminalStatus(String taskId) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            MvcResult result = mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/export-async/{taskId}", taskId))
                .andExpect(status().isOk())
                .andReturn();
            String statusValue = objectMapper.readTree(result.getResponse().getContentAsString()).path("status").asText();
            if ("COMPLETED".equals(statusValue) || "CANCELLED".equals(statusValue) || "FAILED".equals(statusValue)) {
                return statusValue;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        throw new AssertionError("Timed out waiting for async queue declined export task to reach terminal status");
    }

    private String awaitQueueDeclinedRequeueDryRunAsyncTaskTerminalStatus(String taskId) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            MvcResult result = mockMvc.perform(get("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/{taskId}", taskId))
                .andExpect(status().isOk())
                .andReturn();
            String statusValue = objectMapper.readTree(result.getResponse().getContentAsString()).path("status").asText();
            if ("COMPLETED".equals(statusValue) || "CANCELLED".equals(statusValue) || "FAILED".equals(statusValue)) {
                return statusValue;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        throw new AssertionError("Timed out waiting for async queue declined requeue dry-run export task to reach terminal status");
    }

    private String startRenditionResourcesAsyncExportTask() throws Exception {
        MvcResult startResult = mockMvc.perform(post("/api/v1/preview/diagnostics/renditions/resources/export-async")
                .contentType("application/json")
                .content("{\"days\":7,\"limit\":500}"))
            .andExpect(status().isAccepted())
            .andExpect(header().exists("Location"))
            .andReturn();
        JsonNode payload = objectMapper.readTree(startResult.getResponse().getContentAsString());
        return payload.path("taskId").asText();
    }

    private String awaitRenditionResourcesAsyncTaskTerminalStatus(String taskId) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            MvcResult result = mockMvc.perform(get("/api/v1/preview/diagnostics/renditions/resources/export-async/{taskId}", taskId))
                .andExpect(status().isOk())
                .andReturn();
            String statusValue = objectMapper.readTree(result.getResponse().getContentAsString()).path("status").asText();
            if ("COMPLETED".equals(statusValue) || "CANCELLED".equals(statusValue) || "FAILED".equals(statusValue)) {
                return statusValue;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        throw new AssertionError("Timed out waiting for async rendition resources export task to reach terminal status");
    }
}
