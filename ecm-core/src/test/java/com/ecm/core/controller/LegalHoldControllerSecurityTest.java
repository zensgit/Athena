package com.ecm.core.controller;

import com.ecm.core.service.LegalHoldService;
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

@WebMvcTest(controllers = LegalHoldController.class)
@ContextConfiguration(classes = {
    LegalHoldController.class,
    RestExceptionHandler.class,
    LegalHoldControllerSecurityTest.TestSecurityConfig.class
})
class LegalHoldControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LegalHoldService legalHoldService;

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
    @DisplayName("legal hold endpoints require authentication")
    void legalHoldEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/legal-holds"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("non-admin users cannot access legal hold endpoints")
    void nonAdminUsersCannotAccessLegalHoldEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/legal-holds"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admins can access legal hold endpoints")
    void adminsCanAccessLegalHoldEndpoints() throws Exception {
        when(legalHoldService.listHolds()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/legal-holds"))
            .andExpect(status().isOk());
    }
}
