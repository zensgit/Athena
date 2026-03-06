package com.ecm.core.controller;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.preview.PreviewQueueService;
import com.ecm.core.repository.DocumentRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private PreviewQueueService previewQueueService;

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

        mockMvc.perform(post("/api/v1/preview/diagnostics/failures/queue-batch")
                .contentType("application/json")
                .content("{\"documentIds\":[],\"force\":false}"))
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
    @DisplayName("Admin can queue preview failures in batch and get aggregated outcome")
    void diagnosticsBatchQueueAggregatesOutcome() throws Exception {
        UUID queuedId = UUID.randomUUID();
        UUID skippedId = UUID.randomUUID();
        UUID failedId = UUID.randomUUID();

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
            .andExpect(jsonPath("$.results", hasSize(3)));
    }
}
