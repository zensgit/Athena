package com.ecm.core.controller;

import com.ecm.core.service.ShareLinkService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShareLinkController.class)
@ContextConfiguration(classes = {
    ShareLinkController.class,
    ShareLinkControllerValidationTest.TestSecurityConfig.class
})
@Import(RestExceptionHandler.class)
class ShareLinkControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShareLinkService shareLinkService;

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
    @WithMockUser
    @DisplayName("Invalid allowedIps returns bad request")
    void createShareLinkRejectsInvalidAllowedIps() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Mockito.when(shareLinkService.createShareLink(Mockito.eq(nodeId), Mockito.any()))
            .thenThrow(new IllegalArgumentException("Invalid allowedIps entry: 192.168.1.0/33"));

        mockMvc.perform(post("/api/v1/share/nodes/{nodeId}", nodeId)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"share\",\"allowedIps\":\"192.168.1.0/33\"}"))
            .andExpect(status().isBadRequest());
    }
}
