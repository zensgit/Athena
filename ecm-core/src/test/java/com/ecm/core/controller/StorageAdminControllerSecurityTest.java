package com.ecm.core.controller;

import com.ecm.core.dto.StorageCapacityStatusDto;
import com.ecm.core.service.StorageCapacityService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StorageAdminController.class)
@ContextConfiguration(classes = {
    StorageAdminController.class,
    RestExceptionHandler.class,
    StorageAdminControllerSecurityTest.TestSecurityConfig.class
})
class StorageAdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StorageCapacityService storageCapacityService;

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
    @DisplayName("unauthenticated GET /admin/storage/capacity returns 401")
    void unauthenticatedCapacityReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/storage/capacity"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot read storage capacity")
    void userCannotReadCapacity() throws Exception {
        mockMvc.perform(get("/api/v1/admin/storage/capacity"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ROLE_ADMIN can read storage capacity")
    void adminCanReadCapacity() throws Exception {
        when(storageCapacityService.getStatus()).thenReturn(new StorageCapacityStatusDto(
            "filesystem",
            "OK",
            1000L,
            900L,
            100L,
            10.0d,
            80,
            95,
            104857600L,
            "/var/ecm/content",
            null
        ));

        mockMvc.perform(get("/api/v1/admin/storage/capacity"))
            .andExpect(status().isOk());
    }
}
