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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RuleController.class)
@ContextConfiguration(classes = {
    RuleController.class,
    RuleControllerFolderScopeSecurityTest.TestSecurityConfig.class
})
class RuleControllerFolderScopeSecurityTest {

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
        when(ruleEngineService.getRulesByScopeFolder(any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(ruleEngineService.reorderRulesByScopeFolder(any(), any()))
            .thenReturn(List.of());
        when(ruleEngineService.dryRunRulesByScopeFolder(any(), any()))
            .thenReturn(new RuleEngineService.FolderRuleDryRunResult(
                UUID.randomUUID(),
                AutomationRule.TriggerType.DOCUMENT_CREATED,
                0,
                0,
                0,
                0,
                0,
                0,
                Map.of(),
                List.of()
            ));
    }

    @Test
    @DisplayName("Folder scoped list requires authentication")
    void folderScopedListRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/rules/folders/{folderId}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Authenticated user can read folder scoped list")
    void folderScopedListAllowsAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/rules/folders/{folderId}", UUID.randomUUID()))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Folder scoped reorder forbids regular user")
    void folderScopedReorderForbidsRegularUser() throws Exception {
        mockMvc.perform(post("/api/v1/rules/folders/{folderId}/reorder", UUID.randomUUID())
                .contentType("application/json")
                .content("{\"ruleIds\":[]}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    @DisplayName("Folder scoped reorder allows editor role")
    void folderScopedReorderAllowsEditor() throws Exception {
        mockMvc.perform(post("/api/v1/rules/folders/{folderId}/reorder", UUID.randomUUID())
                .contentType("application/json")
                .content("{\"ruleIds\":[],\"basePriority\":100,\"step\":10}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Folder scoped dry-run forbids regular user")
    void folderScopedDryRunForbidsRegularUser() throws Exception {
        mockMvc.perform(post("/api/v1/rules/folders/{folderId}/dry-run", UUID.randomUUID())
                .contentType("application/json")
                .content("{\"triggerType\":\"DOCUMENT_CREATED\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    @DisplayName("Folder scoped dry-run allows editor role")
    void folderScopedDryRunAllowsEditor() throws Exception {
        mockMvc.perform(post("/api/v1/rules/folders/{folderId}/dry-run", UUID.randomUUID())
                .contentType("application/json")
                .content("{\"triggerType\":\"DOCUMENT_CREATED\",\"testData\":{}}"))
            .andExpect(status().isOk());
    }
}
