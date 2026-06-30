package com.ecm.core.controller;

import com.ecm.core.failureinventory.FailureInventoryService;
import com.ecm.core.failureinventory.FailureInventorySummaryDto;
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

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security calibration for {@link FailureInventoryAdminController} (CLAUDE.md *SecurityTest convention).
 * Mirrors {@code QueueBacklogAdminControllerSecurityTest}: TestSecurityConfig reproduces the production
 * rule that {@code /api/**} requires authentication, and {@code @EnableMethodSecurity} makes the
 * {@code @PreAuthorize("hasRole('ADMIN')")} gate fire. Denied-role (USER) + admitted-role (ADMIN) cases
 * prove the gate both rejects and admits the right role.
 */
@WebMvcTest(controllers = FailureInventoryAdminController.class)
@ContextConfiguration(classes = {
    FailureInventoryAdminController.class,
    RestExceptionHandler.class,
    FailureInventoryAdminControllerSecurityTest.TestSecurityConfig.class
})
class FailureInventoryAdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FailureInventoryService failureInventoryService;

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

    private static FailureInventorySummaryDto sampleSummary() {
        return new FailureInventorySummaryDto(
            new FailureInventorySummaryDto.PreviewDeadLetter(true, 0L, Map.of(), null),
            new FailureInventorySummaryDto.TransferFailures(true, 0L),
            new FailureInventorySummaryDto.MailFetchErrors(true, 0L),
            new FailureInventorySummaryDto.OcrFailures(true, 0L, 0L),
            new FailureInventorySummaryDto.MailProcessedErrors(true, 0L));
    }

    @Test
    @DisplayName("unauthenticated GET /admin/failure-inventory returns 401")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/failure-inventory"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot read the failure inventory")
    void userCannotRead() throws Exception {
        mockMvc.perform(get("/api/v1/admin/failure-inventory"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ROLE_ADMIN can read the failure inventory")
    void adminCanRead() throws Exception {
        when(failureInventoryService.getSummary()).thenReturn(sampleSummary());
        mockMvc.perform(get("/api/v1/admin/failure-inventory"))
            .andExpect(status().isOk());
    }
}
