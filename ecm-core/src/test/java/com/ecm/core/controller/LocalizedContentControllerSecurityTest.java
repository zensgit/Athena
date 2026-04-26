package com.ecm.core.controller;

import com.ecm.core.service.LocalizedContentService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LocalizedContentController.class)
@ContextConfiguration(classes = {
    LocalizedContentController.class,
    RestExceptionHandler.class,
    LocalizedContentControllerSecurityTest.TestSecurityConfig.class
})
class LocalizedContentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LocalizedContentService localizedContentService;

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
    @DisplayName("unauthenticated GET localizations returns 401")
    void unauthenticatedGetLocalizationsReturns401() throws Exception {
        UUID nodeId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/nodes/" + nodeId + "/localizations"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("authenticated user can list localizations (isAuthenticated() gate)")
    void authenticatedUserCanListLocalizations() throws Exception {
        UUID nodeId = UUID.randomUUID();
        when(localizedContentService.listForNode(nodeId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/nodes/" + nodeId + "/localizations"))
            .andExpect(status().isOk());
    }
}
