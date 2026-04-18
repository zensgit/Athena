package com.ecm.core.controller;

import com.ecm.core.integration.ldap.LdapConnectionStatus;
import com.ecm.core.integration.ldap.LdapSyncResult;
import com.ecm.core.integration.ldap.LdapSyncService;
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
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LdapSyncController.class, properties = "ecm.identity.provider=ldap")
@ContextConfiguration(classes = {
    LdapSyncController.class,
    RestExceptionHandler.class,
    LdapSyncControllerSecurityTest.TestSecurityConfig.class
})
class LdapSyncControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LdapSyncService ldapSyncService;

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
    @DisplayName("LDAP admin endpoints require authentication")
    void ldapAdminEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ldap/test-connection"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Non-admin users cannot run LDAP admin endpoints")
    void nonAdminUsersCannotRunLdapAdminEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ldap/sync"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admins can run LDAP admin endpoints")
    void adminsCanRunLdapAdminEndpoints() throws Exception {
        when(ldapSyncService.testConnection()).thenReturn(
            new LdapConnectionStatus(true, "ou=people", "ou=groups", "ok")
        );
        when(ldapSyncService.syncNow()).thenReturn(
            new LdapSyncResult("manual", LocalDateTime.now(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of())
        );

        mockMvc.perform(post("/api/v1/admin/ldap/test-connection"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/ldap/sync"))
            .andExpect(status().isOk());
    }
}
