package com.ecm.core.controller;

import com.ecm.core.entity.Notification;
import com.ecm.core.service.NotificationInboxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class)
@ContextConfiguration(classes = {
    NotificationController.class,
    RestExceptionHandler.class,
    NotificationControllerSecurityTest.TestSecurityConfig.class
})
class NotificationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationInboxService inboxService;

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
    @DisplayName("unauthenticated GET inbox returns 401")
    void unauthenticatedGetInboxReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET unread returns 401")
    void unauthenticatedGetUnreadReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/unread"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET unread-count returns 401")
    void unauthenticatedGetUnreadCountReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/unread-count"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST mark-all-read returns 401")
    void unauthenticatedMarkAllReadReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/mark-all-read"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated PATCH mark-read returns 401")
    void unauthenticatedMarkReadReturns401() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated DELETE notification returns 401")
    void unauthenticatedDeleteReturns401() throws Exception {
        mockMvc.perform(delete("/api/v1/notifications/{id}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("authenticated user can list inbox (isAuthenticated() gate)")
    void authenticatedUserCanListInbox() throws Exception {
        Page<Notification> empty = new PageImpl<>(List.of());
        when(inboxService.getInbox(any())).thenReturn(empty);

        mockMvc.perform(get("/api/v1/notifications"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("authenticated user can read unread count")
    void authenticatedUserCanReadUnreadCount() throws Exception {
        when(inboxService.getUnreadCount()).thenReturn(5L);

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("authenticated user can mark all as read")
    void authenticatedUserCanMarkAllRead() throws Exception {
        when(inboxService.markAllRead()).thenReturn(3);

        mockMvc.perform(post("/api/v1/notifications/mark-all-read"))
            .andExpect(status().isOk());
    }
}
