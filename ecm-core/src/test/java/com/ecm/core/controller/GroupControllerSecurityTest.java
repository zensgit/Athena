package com.ecm.core.controller;

import com.ecm.core.dto.GroupDto;
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
import org.springframework.data.domain.PageRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-only test for {@link GroupController}.
 *
 * Two authorization tiers:
 *   - reads (GET /groups): isAuthenticated() only
 *   - writes (POST /groups, DELETE, member POST/DELETE): @PreAuthorize hasRole('ADMIN')
 */
@WebMvcTest(controllers = GroupController.class)
@ContextConfiguration(classes = {
    GroupController.class,
    RestExceptionHandler.class,
    GroupControllerSecurityTest.TestSecurityConfig.class
})
class GroupControllerSecurityTest {

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
    @DisplayName("unauthenticated GET /groups returns 401")
    void unauthenticatedListGroupsReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/groups"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /groups returns 401")
    void unauthenticatedCreateGroupReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated DELETE /groups/{name} returns 401")
    void unauthenticatedDeleteGroupReturns401() throws Exception {
        mockMvc.perform(delete("/api/v1/groups/{name}", "engineering"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /groups/{groupName}/members/{username} returns 401")
    void unauthenticatedAddMemberReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/groups/{groupName}/members/{username}", "engineering", "alice"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated DELETE /groups/{groupName}/members/{username} returns 401")
    void unauthenticatedRemoveMemberReturns401() throws Exception {
        mockMvc.perform(delete("/api/v1/groups/{groupName}/members/{username}", "engineering", "alice"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER can list groups (read tier is open)")
    void userCanListGroups() throws Exception {
        Page<GroupDto> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(userGroupService.getGroups(any())).thenReturn(empty);

        mockMvc.perform(get("/api/v1/groups"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot create group (admin-only write)")
    void userCannotCreateGroup() throws Exception {
        mockMvc.perform(post("/api/v1/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot delete group")
    void userCannotDeleteGroup() throws Exception {
        mockMvc.perform(delete("/api/v1/groups/{name}", "engineering"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot add member to group")
    void userCannotAddMember() throws Exception {
        mockMvc.perform(post("/api/v1/groups/{groupName}/members/{username}", "engineering", "alice"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER cannot remove member from group")
    void userCannotRemoveMember() throws Exception {
        mockMvc.perform(delete("/api/v1/groups/{groupName}/members/{username}", "engineering", "alice"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    @DisplayName("ROLE_EDITOR cannot create group (admin-only, EDITOR not enough)")
    void editorCannotCreateGroup() throws Exception {
        mockMvc.perform(post("/api/v1/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }
}
