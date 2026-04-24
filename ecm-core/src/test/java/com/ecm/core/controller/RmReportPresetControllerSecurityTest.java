package com.ecm.core.controller;

import com.ecm.core.service.RmReportPresetDeliveryService;
import com.ecm.core.service.RmReportPresetService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RmReportPresetController.class)
@ContextConfiguration(classes = {
    RmReportPresetController.class,
    RestExceptionHandler.class,
    RmReportPresetControllerSecurityTest.TestSecurityConfig.class
})
class RmReportPresetControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RmReportPresetService presetService;

    @MockBean
    private RmReportPresetDeliveryService deliveryService;

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
    @DisplayName("report preset ops trigger requires authentication")
    void reportPresetOpsTriggerRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/records/report-presets/run-scheduled-deliveries"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("non-admin users cannot run report preset ops trigger")
    void nonAdminUsersCannotRunReportPresetOpsTrigger() throws Exception {
        mockMvc.perform(post("/api/v1/records/report-presets/run-scheduled-deliveries"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admins can run report preset ops trigger")
    void adminsCanRunReportPresetOpsTrigger() throws Exception {
        when(deliveryService.runScheduledDeliveriesNow())
            .thenReturn(new RmReportPresetDeliveryService.ScheduledRunResultDto(
                0,
                LocalDateTime.of(2026, 4, 24, 10, 30)
            ));

        mockMvc.perform(post("/api/v1/records/report-presets/run-scheduled-deliveries"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.processedCount").value(0))
            .andExpect(jsonPath("$.generatedAt").exists());
    }
}
