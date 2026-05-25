package com.ecm.core.controller;

import com.ecm.core.entity.AuditLog;
import com.ecm.core.repository.AuditLogRepository;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.BulkMetadataService;
import com.ecm.core.service.BulkOperationService;
import com.ecm.core.service.BulkShareLinkService;
import com.ecm.core.service.SecurityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-only test for {@link BulkOperationController}.
 *
 * Every endpoint is gated by @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')").
 * This is a high-blast-radius admin surface: a single POST /bulk/delete can
 * touch many nodes at once, so missing/widened gates are amplified.
 *
 * The test asserts:
 *   - unauth → 401 on representative endpoints (write + read)
 *   - ROLE_USER → 403 on representative writes (proves @PreAuthorize fires)
 *   - ROLE_EDITOR → 200 on a representative endpoint (proves the gate admits
 *     editors, not just admins)
 */
@WebMvcTest(controllers = BulkOperationController.class)
@ContextConfiguration(classes = {
    BulkOperationController.class,
    RestExceptionHandler.class,
    BulkOperationControllerSecurityTest.TestSecurityConfig.class
})
class BulkOperationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BulkOperationService bulkService;

    @MockBean
    private BulkMetadataService bulkMetadataService;

    @MockBean
    private BulkShareLinkService bulkShareLinkService;

    @MockBean
    private AuditLogRepository auditLogRepository;

    @MockBean
    private AuditService auditService;

    @MockBean
    private SecurityService securityService;

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

    // ====== unauth → 401 ======

    @Test
    @DisplayName("unauthenticated POST /bulk/move returns 401")
    void unauthenticatedMoveReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/bulk/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /bulk/delete returns 401 — load-bearing high-blast endpoint")
    void unauthenticatedBulkDeleteReturns401() throws Exception {
        // POST /bulk/delete can hard-delete many nodes in one call. The unauth-401
        // case is load-bearing: a missing gate means anyone can mass-delete.
        mockMvc.perform(post("/api/v1/bulk/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /bulk/restore returns 401")
    void unauthenticatedRestoreReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/bulk/restore")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /bulk/metadata returns 401")
    void unauthenticatedMetadataReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/bulk/metadata")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /bulk/history returns 401")
    void unauthenticatedHistoryReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/bulk/history"))
            .andExpect(status().isUnauthorized());
    }

    // ====== ROLE_USER → 403 ======

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot bulk move (admin-or-editor gate)")
    void userCannotBulkMove() throws Exception {
        mockMvc.perform(post("/api/v1/bulk/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot bulk delete (admin-or-editor gate)")
    void userCannotBulkDelete() throws Exception {
        mockMvc.perform(post("/api/v1/bulk/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot read bulk history (admin-or-editor gate even on read)")
    void userCannotReadBulkHistory() throws Exception {
        mockMvc.perform(get("/api/v1/bulk/history"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot read bulk history summary")
    void userCannotReadBulkHistorySummary() throws Exception {
        mockMvc.perform(get("/api/v1/bulk/history/summary"))
            .andExpect(status().isForbidden());
    }

    // ====== ROLE_EDITOR / ADMIN → 200 ======

    @Test
    @WithMockUser(roles = "EDITOR")
    @DisplayName("ROLE_EDITOR can read bulk history (admin-or-editor gate admits editor)")
    void editorCanReadBulkHistory() throws Exception {
        Page<AuditLog> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(auditLogRepository.findBulkOperationTimelineNoNodeId(
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                any()))
            .thenReturn(empty);

        mockMvc.perform(get("/api/v1/bulk/history"))
            .andExpect(status().isOk());
    }

    // ====== bulk share-links: same admin-or-editor gate ======

    private static final String SHARE_BODY =
        "{\"nodeIds\":[\"11111111-1111-4111-8111-111111111111\"],\"permissionLevel\":\"VIEW\"}";

    @Test
    @DisplayName("unauthenticated POST /bulk/share-links returns 401")
    void unauthBulkShareLinks() throws Exception {
        mockMvc.perform(post("/api/v1/bulk/share-links")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SHARE_BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot bulk-create share links (admin-or-editor gate)")
    void userCannotBulkShareLinks() throws Exception {
        mockMvc.perform(post("/api/v1/bulk/share-links")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SHARE_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    @DisplayName("ROLE_EDITOR can bulk-create share links (gate admits editor)")
    void editorCanBulkShareLinks() throws Exception {
        when(bulkShareLinkService.createShareLinksBulk(any(), any())).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/bulk/share-links")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SHARE_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bulkShareLinkCreateResults.rows").isArray());
    }
}
