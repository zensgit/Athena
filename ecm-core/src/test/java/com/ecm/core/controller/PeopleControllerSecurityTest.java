package com.ecm.core.controller;

import com.ecm.core.dto.UserDto;
import com.ecm.core.repository.CommentRepository;
import com.ecm.core.repository.FavoriteRepository;
import com.ecm.core.repository.UserRepository;
import com.ecm.core.service.CommentService;
import com.ecm.core.service.FavoriteService;
import com.ecm.core.service.PreferenceService;
import com.ecm.core.service.SecurityService;
import com.ecm.core.service.UserGroupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PeopleController.class)
@ContextConfiguration(classes = {
    PeopleController.class,
    RestExceptionHandler.class,
    PeopleControllerSecurityTest.TestSecurityConfig.class
})
class PeopleControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserGroupService userGroupService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private FavoriteService favoriteService;

    @MockBean
    private FavoriteRepository favoriteRepository;

    @MockBean
    private CommentRepository commentRepository;

    @MockBean
    private CommentService commentService;

    @MockBean
    private SecurityService securityService;

    @MockBean
    private PreferenceService preferenceService;

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
    @DisplayName("People endpoints require authentication")
    void peopleEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/people"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("Authenticated users can access people directory endpoints")
    void authenticatedUsersCanAccessPeopleDirectoryEndpoints() throws Exception {
        when(userGroupService.searchUsers(null, PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(
                new UserDto("1", "admin", "admin@example.com", List.of("ROLE_ADMIN"), "Admin", "User", true, false)
            ), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/people"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("Authenticated users can update their own people profile")
    void authenticatedUsersCanUpdateOwnPeopleProfile() throws Exception {
        com.ecm.core.entity.User user = new com.ecm.core.entity.User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.save(org.mockito.ArgumentMatchers.any(com.ecm.core.entity.User.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(securityService.getCurrentUser()).thenReturn("alice");

        mockMvc.perform(put("/api/v1/people/alice/profile")
                .contentType("application/json")
                .content("""
                    {
                      "displayName": "Alice"
                    }
                    """))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("Authenticated users can create their own favorite through people relation")
    void authenticatedUsersCanCreateOwnFavorite() throws Exception {
        com.ecm.core.entity.User user = new com.ecm.core.entity.User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");

        com.ecm.core.entity.Document node = new com.ecm.core.entity.Document();
        node.setId(UUID.randomUUID());
        node.setName("Quarterly Report");

        com.ecm.core.entity.Favorite favorite = new com.ecm.core.entity.Favorite();
        favorite.setId(UUID.randomUUID());
        favorite.setUserId("alice");
        favorite.setNode(node);
        favorite.setCreatedAt(java.time.LocalDateTime.now());

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(favoriteService.addFavoriteForUser("alice", node.getId())).thenReturn(favorite);

        mockMvc.perform(post("/api/v1/people/alice/favorites")
                .contentType("application/json")
                .content("""
                    {
                      "nodeId": "%s"
                    }
                    """.formatted(node.getId())))
            .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("Authenticated users can delete their own favorite through people relation")
    void authenticatedUsersCanDeleteOwnFavorite() throws Exception {
        com.ecm.core.entity.User user = new com.ecm.core.entity.User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(securityService.getCurrentUser()).thenReturn("alice");

        mockMvc.perform(delete("/api/v1/people/alice/favorites/" + UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("Authenticated users can create their own favorite site through people relation")
    void authenticatedUsersCanCreateOwnFavoriteSite() throws Exception {
        com.ecm.core.entity.User user = new com.ecm.core.entity.User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");

        com.ecm.core.entity.Folder folder = new com.ecm.core.entity.Folder();
        folder.setId(UUID.randomUUID());
        folder.setName("Workspace");
        folder.setFolderType(com.ecm.core.entity.Folder.FolderType.WORKSPACE);
        folder.setPath("/Workspace");

        com.ecm.core.entity.Favorite favorite = new com.ecm.core.entity.Favorite();
        favorite.setId(UUID.randomUUID());
        favorite.setUserId("alice");
        favorite.setNode(folder);
        favorite.setCreatedAt(java.time.LocalDateTime.now());

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(favoriteService.addFavoriteSiteForUser("alice", folder.getId())).thenReturn(favorite);

        mockMvc.perform(post("/api/v1/people/alice/favorite-sites")
                .contentType("application/json")
                .content("""
                    {
                      "nodeId": "%s"
                    }
                    """.formatted(folder.getId())))
            .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("Authenticated users can withdraw their own site membership request")
    void authenticatedUsersCanWithdrawOwnSiteMembershipRequest() throws Exception {
        com.ecm.core.entity.User user = new com.ecm.core.entity.User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPreferences(new java.util.HashMap<>(java.util.Map.of(
            "siteMembershipRequests",
            java.util.List.of(java.util.Map.of("siteId", "finance", "status", "PENDING"))
        )));

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.save(org.mockito.ArgumentMatchers.any(com.ecm.core.entity.User.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(securityService.getCurrentUser()).thenReturn("alice");

        mockMvc.perform(delete("/api/v1/people/alice/site-membership-requests/finance"))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("Authenticated non-admin users cannot access the moderation queue")
    void authenticatedUsersCannotAccessModerationQueue() throws Exception {
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(securityService.isAdmin("alice")).thenReturn(false);

        mockMvc.perform(get("/api/v1/people/site-membership-requests"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("Authenticated users can create their own site membership request")
    void authenticatedUsersCanCreateOwnSiteMembershipRequest() throws Exception {
        com.ecm.core.entity.User user = new com.ecm.core.entity.User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPreferences(new java.util.HashMap<>());

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.save(org.mockito.ArgumentMatchers.any(com.ecm.core.entity.User.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(securityService.getCurrentUser()).thenReturn("alice");

        mockMvc.perform(post("/api/v1/people/alice/site-membership-requests")
                .contentType("application/json")
                .content("""
                    {
                      "siteId": "finance",
                      "siteTitle": "Finance Workspace",
                      "message": "Need access"
                    }
                    """))
            .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("Authenticated non-admin users cannot approve site membership requests")
    void authenticatedUsersCannotApproveSiteMembershipRequests() throws Exception {
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(securityService.isAdmin("alice")).thenReturn(false);

        mockMvc.perform(post("/api/v1/people/alice/site-membership-requests/finance/approve"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("Authenticated users can update their own site membership request")
    void authenticatedUsersCanUpdateOwnSiteMembershipRequest() throws Exception {
        com.ecm.core.entity.User user = new com.ecm.core.entity.User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPreferences(new java.util.HashMap<>(java.util.Map.of(
            "siteMembershipRequests",
            java.util.List.of(java.util.Map.of("siteId", "finance", "siteTitle", "Finance Workspace", "status", "PENDING"))
        )));

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.save(org.mockito.ArgumentMatchers.any(com.ecm.core.entity.User.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(securityService.getCurrentUser()).thenReturn("alice");

        mockMvc.perform(put("/api/v1/people/alice/site-membership-requests/finance")
                .contentType("application/json")
                .content("""
                    {
                      "siteTitle": "Finance Workspace",
                      "role": "Contributor",
                      "message": "Updated access request"
                    }
                    """))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("Authenticated users can delete their own preference entry")
    void authenticatedUsersCanDeleteOwnPreferenceEntry() throws Exception {
        com.ecm.core.entity.User user = new com.ecm.core.entity.User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPreferences(new java.util.HashMap<>(java.util.Map.of("theme", "dark")));

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(preferenceService.deletePreference("alice", "theme"))
            .thenReturn(java.util.Map.of());

        mockMvc.perform(delete("/api/v1/people/alice/preferences/theme"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("Authenticated users cannot import another user's preferences")
    void authenticatedUsersCannotImportAnotherUsersPreferences() throws Exception {
        com.ecm.core.entity.User user = new com.ecm.core.entity.User();
        user.setId(UUID.randomUUID());
        user.setUsername("bob");
        user.setEmail("bob@example.com");
        user.setPreferences(new java.util.HashMap<>(java.util.Map.of("theme", "light")));

        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(securityService.isAdmin("alice")).thenReturn(false);

        mockMvc.perform(post("/api/v1/people/bob/preferences/import")
                .contentType("application/json")
                .content("""
                    {
                      "preferences": {
                        "ui.theme": "dark"
                      }
                    }
                    """))
            .andExpect(status().isForbidden());
    }
}
