package com.ecm.core.controller;

import com.ecm.core.asynctask.AsyncTaskAcknowledgementService;
import com.ecm.core.asynctask.AsyncTaskGovernanceOverviewSnapshot;
import com.ecm.core.asynctask.AsyncTaskGovernanceRiskLevel;
import com.ecm.core.asynctask.AsyncTaskGovernanceService;
import com.ecm.core.asynctask.AsyncTaskGovernanceStatus;
import com.ecm.core.asynctask.AsyncTaskLifecycleListSnapshot;
import com.ecm.core.asynctask.AsyncTaskLifecycleService;
import com.ecm.core.asynctask.AsyncTaskSummarySnapshot;
import com.ecm.core.service.AnalyticsService;
import com.ecm.core.service.AuditExportAsyncTaskRegistry;
import com.ecm.core.service.AuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@ContextConfiguration(classes = {
    AnalyticsController.class,
    AnalyticsControllerSecurityTest.TestSecurityConfig.class
})
class AnalyticsControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private AuditExportAsyncTaskRegistry auditExportAsyncTaskRegistry;

    @MockBean
    private AsyncTaskGovernanceService asyncTaskGovernanceService;

    @MockBean
    private AsyncTaskLifecycleService asyncTaskLifecycleService;

    @MockBean
    private AsyncTaskAcknowledgementService asyncTaskAcknowledgementService;

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
    @DisplayName("Async governance overview requires authentication")
    void asyncGovernanceOverviewRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/async-governance/overview"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Async governance overview requires admin role")
    void asyncGovernanceOverviewRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/async-governance/overview"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can access async governance overview")
    void asyncGovernanceOverviewAllowsAdmin() throws Exception {
        Mockito.when(asyncTaskGovernanceService.buildOverview())
            .thenReturn(new AsyncTaskGovernanceOverviewSnapshot(
                java.time.LocalDateTime.of(2026, 3, 21, 13, 0),
                AsyncTaskGovernanceStatus.HEALTHY,
                AsyncTaskGovernanceRiskLevel.LOW,
                0,
                0,
                AsyncTaskSummarySnapshot.ofBreakdown(0, 0, 0, 0, 0, 0, 0),
                java.util.List.of()
            ));

        mockMvc.perform(get("/api/v1/analytics/async-governance/overview"))
            .andExpect(status().isOk());

        Mockito.verify(asyncTaskGovernanceService).buildOverview();
    }

    @Test
    @DisplayName("Async lifecycle task list requires authentication")
    void asyncLifecycleTaskListRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/async-governance/tasks"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Async lifecycle task list requires admin role")
    void asyncLifecycleTaskListRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/async-governance/tasks"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can access async lifecycle task list")
    void asyncLifecycleTaskListAllowsAdmin() throws Exception {
        Mockito.when(asyncTaskLifecycleService.listRecentTasks(null, null, null, null, false))
            .thenReturn(new AsyncTaskLifecycleListSnapshot(
                java.time.Instant.parse("2026-03-21T13:10:00Z"),
                null,
                null,
                0,
                0,
                0,
                20,
                false,
                java.util.List.of()
            ));

        mockMvc.perform(get("/api/v1/analytics/async-governance/tasks"))
            .andExpect(status().isOk());

        Mockito.verify(asyncTaskLifecycleService).listRecentTasks(null, null, null, null, false);
    }

    @Test
    @DisplayName("Async lifecycle acknowledge requires authentication")
    void asyncLifecycleAcknowledgeRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/analytics/async-governance/tasks/acknowledge")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"domainKey\":\"preview\",\"taskId\":\"task-1\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Async lifecycle acknowledge requires admin role")
    void asyncLifecycleAcknowledgeRequiresAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/analytics/async-governance/tasks/acknowledge")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"domainKey\":\"preview\",\"taskId\":\"task-1\"}"))
            .andExpect(status().isForbidden());
    }
}
