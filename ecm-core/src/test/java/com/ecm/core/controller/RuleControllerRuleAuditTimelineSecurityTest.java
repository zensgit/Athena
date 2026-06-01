package com.ecm.core.controller;

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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RuleController.class)
@ContextConfiguration(classes = {
    RuleController.class,
    RuleControllerRuleAuditTimelineSecurityTest.TestSecurityConfig.class
})
class RuleControllerRuleAuditTimelineSecurityTest {

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
        when(auditLogRepository.findRuleAuditTimeline(any(), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(auditLogRepository.findRuleAuditTimelineNoNodeId(any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
    }

    @Test
    @DisplayName("Rule audit timeline requires authentication")
    void ruleAuditTimelineRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/rules/executions/audit"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Rule audit timeline forbids regular user")
    void ruleAuditTimelineForbidsRegularUser() throws Exception {
        mockMvc.perform(get("/api/v1/rules/executions/audit"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    @DisplayName("Rule audit timeline allows editor")
    void ruleAuditTimelineAllowsEditor() throws Exception {
        mockMvc.perform(get("/api/v1/rules/executions/audit"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Rule audit timeline export forbids regular user")
    void ruleAuditTimelineExportForbidsRegularUser() throws Exception {
        mockMvc.perform(get("/api/v1/rules/executions/audit/export"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    @DisplayName("Rule audit timeline export allows editor")
    void ruleAuditTimelineExportAllowsEditor() throws Exception {
        mockMvc.perform(get("/api/v1/rules/executions/audit/export"))
            .andExpect(status().isOk());
    }
}
