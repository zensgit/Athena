package com.ecm.core.controller;

import com.ecm.core.security.mfa.MfaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
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

@WebMvcTest(controllers = MfaController.class)
@ContextConfiguration(classes = {
    MfaController.class,
    RestExceptionHandler.class,
    MfaControllerSecurityTest.TestSecurityConfig.class
})
class MfaControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MfaService mfaService;

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
    @DisplayName("unauthenticated GET /mfa/status returns 401")
    void unauthenticatedGetStatusReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/mfa/status"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /mfa/enroll returns 401")
    void unauthenticatedEnrollReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/mfa/enroll"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /mfa/verify returns 401")
    void unauthenticatedVerifyReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/mfa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"123456\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /mfa/disable returns 401")
    void unauthenticatedDisableReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/mfa/disable")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"123456\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /mfa/recovery-codes returns 401")
    void unauthenticatedRegenerateRecoveryCodesReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/mfa/recovery-codes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"123456\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("authenticated user can read MFA status (isAuthenticated() gate)")
    void authenticatedUserCanReadStatus() throws Exception {
        mockMvc.perform(get("/api/v1/mfa/status"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("authenticated user can enroll for MFA")
    void authenticatedUserCanEnroll() throws Exception {
        mockMvc.perform(post("/api/v1/mfa/enroll"))
            .andExpect(status().isOk());
    }
}
