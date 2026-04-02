package com.ecm.core.controller;

import com.ecm.core.service.CommentService;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CommentController.class, UserCommentController.class})
@ContextConfiguration(classes = {
    CommentController.class,
    UserCommentController.class,
    CommentControllerSecurityTest.TestSecurityConfig.class
})
class CommentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommentService commentService;

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
    @DisplayName("Comment endpoints require authentication")
    void commentEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/nodes/{nodeId}/comments", java.util.UUID.randomUUID()))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/comments", java.util.UUID.randomUUID())
                .contentType("application/json")
                .content("{\"content\":\"hello\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("Authenticated users can access comment endpoints")
    void authenticatedUsersCanAccessCommentEndpoints() throws Exception {
        org.mockito.Mockito.when(commentService.getUserComments(
            "alice",
            PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(java.util.List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/users/{username}/comments", "alice"))
            .andExpect(status().isOk());
    }
}
