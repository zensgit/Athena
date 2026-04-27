package com.ecm.core.controller;

import com.ecm.core.service.ScriptService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-only test for {@link ScriptController}.
 *
 * ScriptController has NO @PreAuthorize annotations — every endpoint relies
 * on the global filter chain's isAuthenticated() check. ScriptService is
 * responsible for any role-based enforcement at the service layer.
 *
 * The blast radius makes the unauthenticated-401 case load-bearing:
 *
 *   POST /scripts/execute runs arbitrary GraalJS code. A missing filter rule
 *   would let an anonymous caller execute server-side scripts with the JVM's
 *   permissions. This is the highest-blast-radius single endpoint in the repo
 *   that this backfill has touched.
 *
 * The test asserts the filter chain rejects unauthenticated requests on every
 * endpoint and that an authenticated USER reaches the controller body (so the
 * gate is isAuthenticated, not role-based; role enforcement is service-side).
 */
@WebMvcTest(controllers = ScriptController.class)
@ContextConfiguration(classes = {
    ScriptController.class,
    RestExceptionHandler.class,
    ScriptControllerSecurityTest.TestSecurityConfig.class
})
class ScriptControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScriptService scriptService;

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
    @DisplayName("unauthenticated GET /scripts returns 401")
    void unauthenticatedListScriptsReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/scripts"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /scripts/{id} returns 401")
    void unauthenticatedGetScriptReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/scripts/{id}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /scripts returns 401")
    void unauthenticatedCreateScriptReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated PUT /scripts/{id} returns 401")
    void unauthenticatedUpdateScriptReturns401() throws Exception {
        mockMvc.perform(put("/api/v1/scripts/{id}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated DELETE /scripts/{id} returns 401")
    void unauthenticatedDeleteScriptReturns401() throws Exception {
        mockMvc.perform(delete("/api/v1/scripts/{id}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /scripts/execute returns 401 — load-bearing arbitrary-code-execution endpoint")
    void unauthenticatedExecuteScriptReturns401() throws Exception {
        // Highest blast radius in this backfill: missing rule = anonymous arbitrary
        // GraalJS execution under the JVM's permissions.
        mockMvc.perform(post("/api/v1/scripts/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER can list scripts (gate is isAuthenticated only; role enforcement is service-side)")
    void userCanListScripts() throws Exception {
        when(scriptService.listScripts()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/scripts"))
            .andExpect(status().isOk());
    }
}
