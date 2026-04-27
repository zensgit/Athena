package com.ecm.core.controller;

import com.ecm.core.entity.AutomationRule;
import com.ecm.core.repository.AuditLogRepository;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.RuleEngineService;
import com.ecm.core.service.ScheduledRuleRunner;
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
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-only test for {@link RuleController}.
 *
 * RuleController has three distinct authorization tiers:
 *
 *   - "open" reads: no @PreAuthorize, only the global isAuthenticated() gate.
 *     E.g. GET /rules, GET /rules/{id}, GET /rules/templates, POST /rules/validate.
 *   - ADMIN-or-EDITOR writes: @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')").
 *     E.g. POST /rules, PUT /rules/{id}, DELETE /rules/{id}, PATCH enable/disable,
 *     execution ledger reads, dry-run, reorder, test.
 *   - ADMIN-only: @PreAuthorize("hasRole('ADMIN')").
 *     E.g. POST /rules/{id}/trigger (manually trigger a scheduled rule).
 *
 * This test verifies all three tiers fire correctly. Endpoints sharing a tier
 * are sampled, not exhaustively enumerated.
 */
@WebMvcTest(controllers = RuleController.class)
@ContextConfiguration(classes = {
    RuleController.class,
    RestExceptionHandler.class,
    RuleControllerSecurityTest.TestSecurityConfig.class
})
class RuleControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RuleEngineService ruleEngineService;

    @MockBean
    private SecurityService securityService;

    @MockBean
    private ScheduledRuleRunner scheduledRuleRunner;

    @MockBean
    private AuditService auditService;

    @MockBean
    private AuditLogRepository auditLogRepository;

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

    // ====== Tier 1: open reads (isAuthenticated only) ======

    @Test
    @DisplayName("unauthenticated GET /rules returns 401")
    void unauthenticatedListRulesReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/rules"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /rules/templates returns 401")
    void unauthenticatedListTemplatesReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/rules/templates"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER can list rules (open read)")
    void userCanListRules() throws Exception {
        Page<AutomationRule> empty = new PageImpl<>(List.of());
        when(ruleEngineService.getAllRules(any())).thenReturn(empty);

        mockMvc.perform(get("/api/v1/rules"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER can read templates (open read)")
    void userCanReadTemplates() throws Exception {
        mockMvc.perform(get("/api/v1/rules/templates"))
            .andExpect(status().isOk());
    }

    // ====== Tier 2: ADMIN-or-EDITOR writes ======

    @Test
    @DisplayName("unauthenticated POST /rules returns 401")
    void unauthenticatedCreateRuleReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated DELETE /rules/{id} returns 401")
    void unauthenticatedDeleteRuleReturns401() throws Exception {
        mockMvc.perform(delete("/api/v1/rules/{id}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated PATCH /rules/{id}/enable returns 401")
    void unauthenticatedEnableRuleReturns401() throws Exception {
        mockMvc.perform(patch("/api/v1/rules/{id}/enable", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot create rule (hasAnyRole ADMIN/EDITOR)")
    void userCannotCreateRule() throws Exception {
        mockMvc.perform(post("/api/v1/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot update rule")
    void userCannotUpdateRule() throws Exception {
        mockMvc.perform(put("/api/v1/rules/{id}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot delete rule")
    void userCannotDeleteRule() throws Exception {
        mockMvc.perform(delete("/api/v1/rules/{id}", UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot enable rule")
    void userCannotEnableRule() throws Exception {
        mockMvc.perform(patch("/api/v1/rules/{id}/enable", UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot list manual executions (admin-or-editor gate)")
    void userCannotListManualExecutions() throws Exception {
        mockMvc.perform(get("/api/v1/rules/executions"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    @DisplayName("ROLE_EDITOR can list manual executions (admin-or-editor gate)")
    void editorCanListManualExecutions() throws Exception {
        when(ruleEngineService.listRuleRuns(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/rules/executions"))
            .andExpect(status().isOk());
    }

    // ====== Tier 3: ADMIN-only ======

    @Test
    @DisplayName("unauthenticated POST /rules/{id}/trigger returns 401")
    void unauthenticatedTriggerScheduledRuleReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/rules/{id}/trigger", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot trigger scheduled rule (admin-only)")
    void userCannotTriggerScheduledRule() throws Exception {
        mockMvc.perform(post("/api/v1/rules/{id}/trigger", UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    @DisplayName("ROLE_EDITOR cannot trigger scheduled rule (admin-only — editor not enough)")
    void editorCannotTriggerScheduledRule() throws Exception {
        mockMvc.perform(post("/api/v1/rules/{id}/trigger", UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }
}
