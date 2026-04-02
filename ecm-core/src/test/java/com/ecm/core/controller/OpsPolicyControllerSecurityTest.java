package com.ecm.core.controller;

import com.ecm.core.preview.PreviewFailurePolicyRegistry;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.OpsPolicyService;
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

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OpsPolicyController.class)
@ContextConfiguration(classes = {
    OpsPolicyController.class,
    OpsPolicyControllerSecurityTest.TestSecurityConfig.class
})
class OpsPolicyControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OpsPolicyService opsPolicyService;

    @MockBean
    private AuditService auditService;

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
    @DisplayName("Ops policy endpoints require admin role")
    void requiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/ops/policies"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/ops/policies/PREVIEW/history"))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/ops/policies/PREVIEW")
                .contentType("application/json")
                .content("{\"profileKey\":\"default\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/ops/policies/PREVIEW/rollback")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can load policy version history")
    void adminCanLoadPolicyHistory() throws Exception {
        Mockito.when(opsPolicyService.getState("PREVIEW"))
            .thenReturn(new OpsPolicyService.DomainPolicyState(
                "PREVIEW",
                12L,
                Instant.parse("2026-03-06T10:00:00Z"),
                "admin",
                "policy_update:default",
                List.of()
            ));
        Mockito.when(opsPolicyService.listHistory("PREVIEW", 5))
            .thenReturn(List.of(
                new OpsPolicyService.DomainPolicyHistoryEntry(
                    12L,
                    Instant.parse("2026-03-06T10:00:00Z"),
                    "admin",
                    "policy_update:default"
                ),
                new OpsPolicyService.DomainPolicyHistoryEntry(
                    11L,
                    Instant.parse("2026-03-06T09:40:00Z"),
                    "admin",
                    "bootstrap"
                )
            ));

        mockMvc.perform(get("/api/v1/ops/policies/PREVIEW/history")
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domain", is("PREVIEW")))
            .andExpect(jsonPath("$.currentVersion", is(12)))
            .andExpect(jsonPath("$.history", hasSize(2)))
            .andExpect(jsonPath("$.history[0].version", is(12)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can load policy domain state")
    void adminCanLoadPolicyState() throws Exception {
        PreviewFailurePolicyRegistry.PreviewFailurePolicy defaultPolicy =
            new PreviewFailurePolicyRegistry.PreviewFailurePolicy(
                "default",
                "Default",
                3,
                60000L,
                1.6d,
                0L,
                true
            );
        Mockito.when(opsPolicyService.getState("PREVIEW"))
            .thenReturn(new OpsPolicyService.DomainPolicyState(
                "PREVIEW",
                12L,
                Instant.parse("2026-03-06T10:00:00Z"),
                "admin",
                "policy_update:default",
                List.of(defaultPolicy)
            ));

        mockMvc.perform(get("/api/v1/ops/policies")
                .param("domain", "PREVIEW"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domain", is("PREVIEW")))
            .andExpect(jsonPath("$.currentVersion", is(12)))
            .andExpect(jsonPath("$.policies", hasSize(1)))
            .andExpect(jsonPath("$.policies[0].key", is("default")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can update preview policy")
    void adminCanUpdatePolicy() throws Exception {
        PreviewFailurePolicyRegistry.PreviewFailurePolicy updatedPolicy =
            new PreviewFailurePolicyRegistry.PreviewFailurePolicy(
                "cad",
                "CAD",
                6,
                90000L,
                2.0d,
                180000L,
                true
            );
        Mockito.when(opsPolicyService.updatePolicy(
                Mockito.eq("PREVIEW"),
                Mockito.eq("cad"),
                Mockito.any(),
                Mockito.eq("user"),
                Mockito.eq("increase_cad_retries")
            ))
            .thenReturn(new OpsPolicyService.DomainPolicyUpdateResult(
                "PREVIEW",
                13L,
                Instant.parse("2026-03-06T10:30:00Z"),
                "user",
                "increase_cad_retries",
                updatedPolicy,
                List.of(updatedPolicy)
            ));

        mockMvc.perform(put("/api/v1/ops/policies/PREVIEW")
                .contentType("application/json")
                .content("""
                    {
                      "profileKey":"cad",
                      "maxAttempts":6,
                      "retryDelayMs":90000,
                      "backoffMultiplier":2.0,
                      "quietPeriodMs":180000,
                      "reason":"increase_cad_retries"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domain", is("PREVIEW")))
            .andExpect(jsonPath("$.currentVersion", is(13)))
            .andExpect(jsonPath("$.updatedPolicy.key", is("cad")))
            .andExpect(jsonPath("$.updatedPolicy.maxAttempts", is(6)));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("Admin can rollback preview policy")
    void adminCanRollbackPolicy() throws Exception {
        PreviewFailurePolicyRegistry.PreviewFailurePolicy defaultPolicy =
            new PreviewFailurePolicyRegistry.PreviewFailurePolicy(
                "default",
                "Default",
                3,
                60000L,
                1.6d,
                0L,
                true
            );
        Mockito.when(opsPolicyService.rollback(
                Mockito.eq("PREVIEW"),
                Mockito.eq(11L),
                Mockito.eq("admin"),
                Mockito.eq("rollback_test")
            ))
            .thenReturn(new OpsPolicyService.DomainPolicyRollbackResult(
                "PREVIEW",
                13L,
                11L,
                14L,
                Instant.parse("2026-03-06T11:00:00Z"),
                "admin",
                "rollback_test",
                List.of(defaultPolicy)
            ));

        mockMvc.perform(post("/api/v1/ops/policies/PREVIEW/rollback")
                .contentType("application/json")
                .content("""
                    {
                      "targetVersion":11,
                      "reason":"rollback_test"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.previousVersion", is(13)))
            .andExpect(jsonPath("$.rolledBackToVersion", is(11)))
            .andExpect(jsonPath("$.currentVersion", is(14)))
            .andExpect(jsonPath("$.policies[0].key", is("default")));
    }
}
