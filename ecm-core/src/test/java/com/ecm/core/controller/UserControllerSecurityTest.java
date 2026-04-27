package com.ecm.core.controller;

import com.ecm.core.service.UserGroupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-only test for {@link UserController}.
 *
 * Two authorization tiers:
 *   - reads (GET /users, GET /users/{username}): isAuthenticated() only
 *   - writes (POST /users, PUT /users/{username}): @PreAuthorize hasRole('ADMIN')
 */
@WebMvcTest(controllers = UserController.class)
@ContextConfiguration(classes = {
    UserController.class,
    RestExceptionHandler.class,
    UserControllerSecurityTest.TestSecurityConfig.class
})
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserGroupService userGroupService;

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
    @DisplayName("unauthenticated GET /users returns 401")
    void unauthenticatedSearchUsersReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /users/{username} returns 401")
    void unauthenticatedGetUserReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/{username}", "alice"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /users returns 401")
    void unauthenticatedCreateUserReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated PUT /users/{username} returns 401")
    void unauthenticatedUpdateUserReturns401() throws Exception {
        mockMvc.perform(put("/api/v1/users/{username}", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER can search users (read tier is open)")
    void userCanSearchUsers() throws Exception {
        Page empty = new PageImpl<>(List.of());
        when(userGroupService.searchUsers(any(), any())).thenReturn(empty);

        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot create user (admin-only write)")
    void userCannotCreateUser() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot update user (admin-only write)")
    void userCannotUpdateUser() throws Exception {
        mockMvc.perform(put("/api/v1/users/{username}", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    @DisplayName("ROLE_EDITOR cannot create user (only ADMIN allowed, EDITOR is not enough)")
    void editorCannotCreateUser() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }
}
