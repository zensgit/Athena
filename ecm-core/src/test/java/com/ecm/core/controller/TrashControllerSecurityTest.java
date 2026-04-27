package com.ecm.core.controller;

import com.ecm.core.service.TrashService;
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
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-only test for {@link TrashController}.
 *
 * TrashController has NO @PreAuthorize annotations — every endpoint relies on
 * the global filter chain's isAuthenticated() check. Real authorization
 * (whose trash you can see, whose trash you can empty, who can hard-delete)
 * is enforced inside TrashService.
 *
 * This is a high-blast-radius surface: a single DELETE /api/v1/trash/empty
 * call hard-deletes EVERY trashed node it has visibility over, and a missing
 * filter-chain rule would mean any unauthenticated caller can wipe trash.
 */
@WebMvcTest(controllers = TrashController.class)
@ContextConfiguration(classes = {
    TrashController.class,
    RestExceptionHandler.class,
    TrashControllerSecurityTest.TestSecurityConfig.class
})
class TrashControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TrashService trashService;

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
    @DisplayName("unauthenticated POST /trash/nodes/{nodeId} (soft-delete) returns 401")
    void unauthenticatedMoveToTrashReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/trash/nodes/{nodeId}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /trash/{nodeId}/restore returns 401")
    void unauthenticatedRestoreReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/trash/{nodeId}/restore", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated DELETE /trash/{nodeId} (hard-delete) returns 401")
    void unauthenticatedPermanentDeleteReturns401() throws Exception {
        mockMvc.perform(delete("/api/v1/trash/{nodeId}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /trash returns 401")
    void unauthenticatedGetTrashItemsReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/trash"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /trash/user/{username} returns 401")
    void unauthenticatedGetTrashForUserReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/trash/user/{username}", "alice"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated DELETE /trash/empty returns 401 — high-blast-radius endpoint")
    void unauthenticatedEmptyTrashReturns401() throws Exception {
        // This is the load-bearing 401: a missing rule means ANY caller can wipe trash
        // for the entire repository. Spring Security must reject before the controller
        // body runs.
        mockMvc.perform(delete("/api/v1/trash/empty"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /trash/stats returns 401")
    void unauthenticatedGetTrashStatsReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/trash/stats"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /trash/nearing-purge returns 401")
    void unauthenticatedGetNearingPurgeReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/trash/nearing-purge"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER can list trash items (gate is isAuthenticated only)")
    void userCanListTrashItems() throws Exception {
        when(trashService.getTrashItems()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/trash"))
            .andExpect(status().isOk());
    }
}
