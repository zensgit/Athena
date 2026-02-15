package com.ecm.core.controller;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

        Mockito.when(documentRepository.findRecentPreviewFailures(anyList(), any(Pageable.class)))
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
    @DisplayName("Limit is clamped to protect the API")
    void limitIsClamped() throws Exception {
        Mockito.when(documentRepository.findRecentPreviewFailures(anyList(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 200), 0));

        mockMvc.perform(get("/api/v1/preview/diagnostics/failures?limit=9999"))
            .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        Mockito.verify(documentRepository).findRecentPreviewFailures(eq(List.of(PreviewStatus.FAILED, PreviewStatus.UNSUPPORTED)), captor.capture());
        Pageable pageable = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(200, pageable.getPageSize());
        org.junit.jupiter.api.Assertions.assertEquals(0, pageable.getPageNumber());
    }
}

