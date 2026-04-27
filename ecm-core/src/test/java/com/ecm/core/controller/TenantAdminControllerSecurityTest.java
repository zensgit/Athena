package com.ecm.core.controller;

import com.ecm.core.service.TenantMetricsService;
import com.ecm.core.service.TenantService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TenantAdminController.class)
@ContextConfiguration(classes = {
    TenantAdminController.class,
    RestExceptionHandler.class,
    TenantAdminControllerSecurityTest.TestSecurityConfig.class
})
class TenantAdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantService tenantService;

    @MockBean
    private TenantMetricsService tenantMetricsService;

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
    @DisplayName("unauthenticated GET /admin/tenants returns 401")
    void unauthenticatedListTenantsReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tenants"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /admin/tenants/current returns 401")
    void unauthenticatedGetCurrentTenantReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tenants/current"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /admin/tenants returns 401")
    void unauthenticatedCreateTenantReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated PUT /admin/tenants/{domain} returns 401")
    void unauthenticatedUpdateTenantReturns401() throws Exception {
        mockMvc.perform(put("/api/v1/admin/tenants/{domain}", "acme")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated DELETE /admin/tenants/{domain} returns 401")
    void unauthenticatedDeleteTenantReturns401() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/tenants/{domain}", "acme"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /admin/tenants/{domain}/metrics returns 401")
    void unauthenticatedGetMetricsReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tenants/{domain}/metrics", "acme"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("authenticated user can list tenants (isAuthenticated() gate; admin enforcement is service-side)")
    void authenticatedUserCanListTenants() throws Exception {
        when(tenantService.listTenants()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/tenants"))
            .andExpect(status().isOk());
    }
}
