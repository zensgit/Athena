package com.ecm.core.controller;

import com.ecm.core.queuebacklog.QueueBacklogObservabilityService;
import com.ecm.core.queuebacklog.QueueBacklogSummaryDto;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = QueueBacklogAdminController.class)
@ContextConfiguration(classes = {
    QueueBacklogAdminController.class,
    RestExceptionHandler.class,
    QueueBacklogAdminControllerSecurityTest.TestSecurityConfig.class
})
class QueueBacklogAdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QueueBacklogObservabilityService queueBacklogObservabilityService;

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

    private static QueueBacklogSummaryDto sampleSummary() {
        return new QueueBacklogSummaryDto(
            new QueueBacklogSummaryDto.OcrBacklog(true, 0L, null),
            new QueueBacklogSummaryDto.MailBacklog(true, null, 0.0d, 0L, "OK"),
            new QueueBacklogSummaryDto.TransferBacklog(true, 0L, 0L, 0L, null, 0L, 60L));
    }

    @Test
    @DisplayName("unauthenticated GET /admin/queue-backlog returns 401")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/queue-backlog"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot read the queue backlog")
    void userCannotRead() throws Exception {
        mockMvc.perform(get("/api/v1/admin/queue-backlog"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ROLE_ADMIN can read the queue backlog")
    void adminCanRead() throws Exception {
        when(queueBacklogObservabilityService.getSummary()).thenReturn(sampleSummary());
        mockMvc.perform(get("/api/v1/admin/queue-backlog"))
            .andExpect(status().isOk());
    }
}
