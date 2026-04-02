package com.ecm.core.controller;

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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RuleController.class)
@ContextConfiguration(classes = {
    RuleController.class,
    RuleControllerActionDefinitionsSecurityTest.TestSecurityConfig.class
})
class RuleControllerActionDefinitionsSecurityTest {

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

    @Test
    @DisplayName("Rule action definitions require authentication")
    void actionDefinitionsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/rules/actions/definitions"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Authenticated user can read rule action definitions")
    void actionDefinitionsAllowsAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/rules/actions/definitions"))
            .andExpect(status().isOk());
    }
}
