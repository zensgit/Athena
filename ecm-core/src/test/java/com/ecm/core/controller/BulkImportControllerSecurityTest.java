package com.ecm.core.controller;

import com.ecm.core.service.BulkImportService;
import com.ecm.core.service.TenantQuotaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-only test for {@link BulkImportController}.
 *
 * BulkImportController has NO @PreAuthorize annotations — every endpoint
 * relies on the global filter chain's isAuthenticated() check. Quota and
 * tenant-scope checks are enforced inside the service (TenantQuotaService).
 *
 * This is a privilege amplifier: one POST can ingest hundreds of files,
 * so the unauth-401 case matters even though no role-specific gate exists
 * at the controller level.
 */
@WebMvcTest(controllers = BulkImportController.class)
@ContextConfiguration(classes = {
    BulkImportController.class,
    RestExceptionHandler.class,
    BulkImportControllerSecurityTest.TestSecurityConfig.class
})
class BulkImportControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BulkImportService bulkImportService;

    @MockBean
    private TenantQuotaService tenantQuotaService;

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

    private MockMultipartFile sampleFile() {
        return new MockMultipartFile(
            "files",
            "doc.txt",
            "text/plain",
            "hello".getBytes()
        );
    }

    @Test
    @DisplayName("unauthenticated POST /bulk-import (multipart) returns 401 — privilege-amplifier endpoint")
    void unauthenticatedStartImportReturns401() throws Exception {
        mockMvc.perform(multipart("/api/v1/bulk-import")
                .file(sampleFile())
                .contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /bulk-import/{jobId} returns 401")
    void unauthenticatedGetJobReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/bulk-import/{jobId}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /bulk-import (list jobs) returns 401")
    void unauthenticatedListJobsReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/bulk-import"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated DELETE /bulk-import/{jobId} returns 401")
    void unauthenticatedCancelJobReturns401() throws Exception {
        mockMvc.perform(delete("/api/v1/bulk-import/{jobId}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER can list import jobs (gate is isAuthenticated only)")
    void userCanListJobs() throws Exception {
        Page empty = new PageImpl<>(List.of());
        when(bulkImportService.listJobs(any())).thenReturn(empty);

        mockMvc.perform(get("/api/v1/bulk-import"))
            .andExpect(status().isOk());
    }
}
