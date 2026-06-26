package com.ecm.core.controller;

import com.ecm.core.scheduler.SchedulerObservabilityService;
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

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SchedulerAdminController.class)
@ContextConfiguration(classes = {
    SchedulerAdminController.class,
    RestExceptionHandler.class,
    SchedulerAdminControllerSecurityTest.TestSecurityConfig.class
})
class SchedulerAdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SchedulerObservabilityService schedulerObservabilityService;

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
    @DisplayName("unauthenticated GET /admin/schedulers returns 401")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/schedulers"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot read schedulers")
    void userCannotRead() throws Exception {
        mockMvc.perform(get("/api/v1/admin/schedulers"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ROLE_ADMIN can read schedulers")
    void adminCanRead() throws Exception {
        when(schedulerObservabilityService.getSnapshot()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/admin/schedulers"))
            .andExpect(status().isOk());
    }
}
