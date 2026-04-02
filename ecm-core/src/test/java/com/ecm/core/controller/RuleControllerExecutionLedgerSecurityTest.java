package com.ecm.core.controller;

import com.ecm.core.entity.AutomationRule;
import com.ecm.core.repository.AuditLogRepository;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.RuleEngineService;
import com.ecm.core.service.ScheduledRuleRunner;
import com.ecm.core.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RuleController.class)
@ContextConfiguration(classes = {
    RuleController.class,
    RuleControllerExecutionLedgerSecurityTest.TestSecurityConfig.class
})
class RuleControllerExecutionLedgerSecurityTest {

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

    @BeforeEach
    void setupDefaults() {
        UUID runId = UUID.randomUUID();
        RuleEngineService.RuleRunLedgerRecord run = new RuleEngineService.RuleRunLedgerRecord(
            runId,
            UUID.randomUUID(),
            "rule",
            UUID.randomUUID(),
            "doc.pdf",
            AutomationRule.TriggerType.DOCUMENT_UPDATED,
            null,
            "admin",
            true,
            true,
            1,
            0,
            1,
            null,
            LocalDateTime.now().minusSeconds(1),
            LocalDateTime.now(),
            100L,
            List.of()
        );
        when(ruleEngineService.executeRuleManual(any(), any(), any(), any()))
            .thenReturn(new RuleEngineService.RuleExecutionCommandResult(runId, false, null, run));
        when(ruleEngineService.listRuleRuns(any(RuleEngineService.RuleRunTimelineQuery.class))).thenReturn(List.of(run));
        when(ruleEngineService.getRuleRun(eq(runId))).thenReturn(Optional.of(run));
    }

    @Test
    @DisplayName("Manual execute requires authentication")
    void manualExecuteRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/rules/{ruleId}/execute", UUID.randomUUID())
                .contentType("application/json")
                .content("{\"documentId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Manual execute forbids regular user")
    void manualExecuteForbidsRegularUser() throws Exception {
        mockMvc.perform(post("/api/v1/rules/{ruleId}/execute", UUID.randomUUID())
                .contentType("application/json")
                .content("{\"documentId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    @DisplayName("Manual execute allows editor")
    void manualExecuteAllowsEditor() throws Exception {
        mockMvc.perform(post("/api/v1/rules/{ruleId}/execute", UUID.randomUUID())
                .contentType("application/json")
                .content("{\"documentId\":\"" + UUID.randomUUID() + "\",\"triggerType\":\"DOCUMENT_UPDATED\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Execution ledger list forbids regular user")
    void executionLedgerListForbidsRegularUser() throws Exception {
        mockMvc.perform(get("/api/v1/rules/executions"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    @DisplayName("Execution ledger list allows editor")
    void executionLedgerListAllowsEditor() throws Exception {
        mockMvc.perform(get("/api/v1/rules/executions"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Execution ledger export forbids regular user")
    void executionLedgerExportForbidsRegularUser() throws Exception {
        mockMvc.perform(get("/api/v1/rules/executions/export"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    @DisplayName("Execution ledger export allows editor")
    void executionLedgerExportAllowsEditor() throws Exception {
        mockMvc.perform(get("/api/v1/rules/executions/export"))
            .andExpect(status().isOk());
    }
}
