package com.ecm.core.controller;

import com.ecm.core.license.LicenseService;
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

/**
 * Security-only test for {@link LicenseController}.
 *
 * Single endpoint, admin-only:
 *   GET /api/v1/system/license → @PreAuthorize("hasRole('ADMIN')")
 */
@WebMvcTest(controllers = LicenseController.class)
@ContextConfiguration(classes = {
    LicenseController.class,
    RestExceptionHandler.class,
    LicenseControllerSecurityTest.TestSecurityConfig.class
})
class LicenseControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LicenseService licenseService;

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
    @DisplayName("unauthenticated GET /system/license returns 401")
    void unauthenticatedGetLicenseReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/system/license"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot read license info (admin-only)")
    void userCannotReadLicense() throws Exception {
        mockMvc.perform(get("/api/v1/system/license"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    @DisplayName("ROLE_EDITOR cannot read license info — admin-only, EDITOR not enough")
    void editorCannotReadLicense() throws Exception {
        // Load-bearing: catches a future widening to hasAnyRole('ADMIN','EDITOR')
        mockMvc.perform(get("/api/v1/system/license"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ROLE_ADMIN can read license info")
    void adminCanReadLicense() throws Exception {
        mockMvc.perform(get("/api/v1/system/license"))
            .andExpect(status().isOk());
    }
}
