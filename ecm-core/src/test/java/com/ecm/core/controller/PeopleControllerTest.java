package com.ecm.core.controller;

import com.ecm.core.dto.UserDto;
import com.ecm.core.entity.Favorite;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Group;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.User;
import com.ecm.core.model.Comment;
import com.ecm.core.repository.FavoriteRepository;
import com.ecm.core.repository.CommentRepository;
import com.ecm.core.repository.UserRepository;
import com.ecm.core.service.CommentService;
import com.ecm.core.service.FavoriteService;
import com.ecm.core.service.PreferenceService;
import com.ecm.core.service.SecurityService;
import com.ecm.core.service.UserGroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PeopleControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserGroupService userGroupService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FavoriteService favoriteService;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CommentService commentService;

    @Mock
    private SecurityService securityService;

    @Mock
    private PreferenceService preferenceService;

    @InjectMocks
    private PeopleController peopleController;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(peopleController)
            .setControllerAdvice(new RestExceptionHandler())
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @Test
    @DisplayName("Search people returns a pageable user list")
    void searchPeopleReturnsPage() throws Exception {
        UserDto alice = new UserDto(
            UUID.randomUUID().toString(),
            "alice",
            "alice@example.com",
            List.of("ROLE_USER"),
            "Alice",
            "Lee",
            true,
            false
        );
        Pageable pageable = PageRequest.of(0, 10);
        Mockito.when(userGroupService.searchUsers("ali", pageable))
            .thenReturn(new PageImpl<>(List.of(alice), pageable, 1));

        mockMvc.perform(get("/api/v1/people")
                .param("query", "ali")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].username").value("alice"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Get person returns the requested profile")
    void getPersonReturnsProfile() throws Exception {
        UserDto alice = new UserDto(
            UUID.randomUUID().toString(),
            "alice",
            "alice@example.com",
            List.of("ROLE_USER"),
            "Alice",
            "Lee",
            true,
            false
        );
        Mockito.when(userGroupService.getUser("alice")).thenReturn(alice);

        mockMvc.perform(get("/api/v1/people/alice"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    @DisplayName("Missing person returns 404")
    void getPersonReturnsNotFound() throws Exception {
        Mockito.when(userGroupService.getUser("missing"))
            .thenThrow(new IllegalArgumentException("User not found: missing"));

        mockMvc.perform(get("/api/v1/people/missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value(containsString("User not found: missing")));
    }

    @Test
    @DisplayName("Person groups are returned in name order")
    void getPersonGroupsReturnsSortedGroups() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");

        Group zeta = new Group();
        zeta.setId(UUID.randomUUID());
        zeta.setName("zeta");
        zeta.setDisplayName("Zeta Team");

        Group alpha = new Group();
        alpha.setId(UUID.randomUUID());
        alpha.setName("alpha");
        alpha.setDisplayName("Alpha Team");

        user.getGroups().add(zeta);
        user.getGroups().add(alpha);
        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/v1/people/alice/groups"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("alpha"))
            .andExpect(jsonPath("$[1].name").value("zeta"));
    }

    @Test
    @DisplayName("Current user favorites use FavoriteService")
    void getPersonFavoritesUsesFavoriteServiceForCurrentUser() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");

        UUID nodeId = UUID.randomUUID();
        Favorite favorite = buildFavorite(UUID.randomUUID(), nodeId, "Quarterly Report", Node.NodeType.DOCUMENT);
        Pageable pageable = PageRequest.of(0, 5);

        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Mockito.when(favoriteService.getFavoritesForUser("alice", pageable))
            .thenReturn(new PageImpl<>(List.of(favorite), pageable, 1));

        mockMvc.perform(get("/api/v1/people/alice/favorites")
                .param("page", "0")
                .param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].nodeName").value("Quarterly Report"))
            .andExpect(jsonPath("$.content[0].nodeType").value("DOCUMENT"));

        Mockito.verify(favoriteService).getFavoritesForUser("alice", pageable);
        Mockito.verify(favoriteRepository, Mockito.never()).findByUserIdOrderByCreatedAtDesc(Mockito.anyString(), Mockito.any());
    }

    @Test
    @DisplayName("Other user favorites also use FavoriteService")
    void getPersonFavoritesUsesFavoriteServiceForOtherUser() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("bob");
        user.setEmail("bob@example.com");

        UUID nodeId = UUID.randomUUID();
        Favorite favorite = buildFavorite(UUID.randomUUID(), nodeId, "Specs", Node.NodeType.DOCUMENT);
        Pageable pageable = PageRequest.of(0, 10);

        Mockito.when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        Mockito.when(favoriteService.getFavoritesForUser("bob", pageable))
            .thenReturn(new PageImpl<>(List.of(favorite), pageable, 1));

        mockMvc.perform(get("/api/v1/people/bob/favorites")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].nodeId").value(nodeId.toString()))
            .andExpect(jsonPath("$.content[0].nodeName").value("Specs"));

        Mockito.verify(favoriteService).getFavoritesForUser("bob", pageable);
    }

    @Test
    @DisplayName("Person preferences return profile metadata and raw preference map")
    void getPersonPreferencesReturnsProfileMetadata() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setFirstName("Alice");
        user.setLastName("Lee");
        user.setDisplayName("Alice Lee");
        user.setPhone("+1-555-0100");
        user.setDepartment("Finance");
        user.setJobTitle("Director");
        user.setAvatarUrl("https://example.com/alice.png");
        user.setLocale("en_US");
        user.setTimezone("UTC");
        user.setLastLoginDate(LocalDateTime.of(2026, 3, 18, 9, 30));
        user.setLastPasswordChangeDate(LocalDateTime.of(2026, 3, 17, 8, 0));
        Map<String, Object> preferences = new HashMap<>();
        preferences.put("theme", "dark");
        preferences.put("compactMode", true);
        user.setPreferences(preferences);

        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/v1/people/alice/preferences"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.displayName").value("Alice Lee"))
            .andExpect(jsonPath("$.firstName").value("Alice"))
            .andExpect(jsonPath("$.lastName").value("Lee"))
            .andExpect(jsonPath("$.locale").value("en_US"))
            .andExpect(jsonPath("$.preferences.theme").value("dark"))
            .andExpect(jsonPath("$.preferences.compactMode").value(true));
    }

    @Test
    @DisplayName("Person preferences can be filtered by namespace prefix")
    void getPersonPreferencesReturnsFilteredNamespaceEntries() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setDisplayName("Alice Lee");
        user.setPreferences(new HashMap<>(Map.of(
            "ui.theme", "dark",
            "org.athena.locale", "en_US"
        )));

        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Mockito.when(preferenceService.getPreferences("alice", "ui."))
            .thenReturn(Map.of("ui.theme", "dark"));

        mockMvc.perform(get("/api/v1/people/alice/preferences")
                .param("filter", "ui."))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.preferences['ui.theme']").value("dark"))
            .andExpect(jsonPath("$.preferences['org.athena.locale']").doesNotExist());
    }

    @Test
    @DisplayName("Preference namespaces endpoint returns distinct namespace prefixes")
    void listPreferenceNamespacesReturnsServiceResult() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");

        Mockito.when(preferenceService.listNamespaces("alice"))
            .thenReturn(List.of("org", "ui"));

        mockMvc.perform(get("/api/v1/people/alice/preferences/namespaces"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").value("org"))
            .andExpect(jsonPath("$[1]").value("ui"));
    }

    @Test
    @DisplayName("Preference export returns the raw map for download")
    void exportPersonPreferencesReturnsRawPreferenceMap() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPreferences(new HashMap<>(Map.of("ui.theme", "dark", "compactMode", true)));

        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Mockito.when(preferenceService.exportPreferences("alice"))
            .thenReturn(Map.of("ui.theme", "dark", "compactMode", true));

        mockMvc.perform(get("/api/v1/people/alice/preferences/export"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$['ui.theme']").value("dark"))
            .andExpect(jsonPath("$.compactMode").value(true));
    }

    @Test
    @DisplayName("Preference import replaces the full raw map")
    void importPersonPreferencesReturnsImportedPreferenceMap() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("bob");
        user.setEmail("bob@example.com");
        user.setPreferences(new HashMap<>(Map.of("theme", "light")));

        Mockito.when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        Mockito.when(securityService.getCurrentUser()).thenReturn("bob");
        Mockito.when(preferenceService.importPreferences("bob", Map.of("ui.theme", "dark", "compactMode", true)))
            .thenReturn(Map.of("ui.theme", "dark", "compactMode", true));

        mockMvc.perform(post("/api/v1/people/bob/preferences/import")
                .contentType("application/json")
                .content("""
                    {
                      "preferences": {
                        "ui.theme": "dark",
                        "compactMode": true
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.preferences['ui.theme']").value("dark"))
            .andExpect(jsonPath("$.preferences.compactMode").value(true));
    }

    @Test
    @DisplayName("Current user can update writable profile fields")
    void updatePersonProfileForCurrentUserReturnsUpdatedPreferences() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setDisplayName("Alice");
        user.setLocale("en_US");
        user.setTimezone("UTC");

        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Mockito.when(securityService.getCurrentUser()).thenReturn("alice");
        Mockito.when(userRepository.save(Mockito.any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/v1/people/alice/profile")
                .contentType("application/json")
                .content("""
                    {
                      "displayName": "Alice Lee",
                      "firstName": "Alice",
                      "lastName": "Lee",
                      "phone": "+1-555-0100",
                      "department": "Finance",
                      "jobTitle": "Director",
                      "avatarUrl": "https://example.com/alice.png",
                      "locale": "en_GB",
                      "timezone": "Europe/London"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("Alice Lee"))
            .andExpect(jsonPath("$.firstName").value("Alice"))
            .andExpect(jsonPath("$.lastName").value("Lee"))
            .andExpect(jsonPath("$.phone").value("+1-555-0100"))
            .andExpect(jsonPath("$.department").value("Finance"))
            .andExpect(jsonPath("$.jobTitle").value("Director"))
            .andExpect(jsonPath("$.avatarUrl").value("https://example.com/alice.png"))
            .andExpect(jsonPath("$.locale").value("en_GB"))
            .andExpect(jsonPath("$.timezone").value("Europe/London"));
    }

    @Test
    @DisplayName("Admins can replace another user's preference map")
    void updatePersonPreferencesForAdminReturnsUpdatedPreferences() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("bob");
        user.setEmail("bob@example.com");
        user.setPreferences(new HashMap<>(Map.of("theme", "light")));

        Mockito.when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        Mockito.when(preferenceService.replaceAll("bob", Map.of("theme", "dark", "compactMode", true)))
            .thenReturn(Map.of("theme", "dark", "compactMode", true));

        mockMvc.perform(put("/api/v1/people/bob/preferences")
                .contentType("application/json")
                .content("""
                    {
                      "preferences": {
                        "theme": "dark",
                        "compactMode": true
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.preferences.theme").value("dark"))
            .andExpect(jsonPath("$.preferences.compactMode").value(true));
    }

    @Test
    @DisplayName("Current user can upsert a single preference entry")
    void updateSinglePersonPreferenceReturnsUpdatedPreferences() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPreferences(new HashMap<>(Map.of("theme", "light")));

        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Mockito.when(preferenceService.setPreference("alice", "theme", "dark"))
            .thenReturn(Map.of("theme", "dark"));

        mockMvc.perform(put("/api/v1/people/alice/preferences/theme")
                .contentType("application/json")
                .content("""
                    {
                      "value": "dark"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.preferences.theme").value("dark"));
    }

    @Test
    @DisplayName("Current user can delete a single preference entry")
    void deleteSinglePersonPreferenceRemovesEntry() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPreferences(new HashMap<>(Map.of("theme", "dark", "compactMode", true)));

        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Mockito.when(preferenceService.deletePreference("alice", "theme"))
            .thenReturn(Map.of("compactMode", true));

        mockMvc.perform(delete("/api/v1/people/alice/preferences/theme"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.preferences.theme").doesNotExist())
            .andExpect(jsonPath("$.preferences.compactMode").value(true));
    }

    @Test
    @DisplayName("Current user can clear all preferences")
    void clearPersonPreferencesReturnsEmptyPreferenceMap() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPreferences(new HashMap<>(Map.of("theme", "dark", "compactMode", true)));

        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        mockMvc.perform(delete("/api/v1/people/alice/preferences"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.preferences").isMap())
            .andExpect(jsonPath("$.preferences.theme").doesNotExist())
            .andExpect(jsonPath("$.preferences.compactMode").doesNotExist());

        Mockito.verify(preferenceService).clearAll("alice");
    }

    @Test
    @DisplayName("Non-admin users cannot update another user's profile")
    void updatePersonProfileForDifferentUserWithoutAdminReturnsForbidden() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("bob");
        user.setEmail("bob@example.com");

        Mockito.when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        Mockito.when(securityService.getCurrentUser()).thenReturn("alice");
        Mockito.when(securityService.isAdmin("alice")).thenReturn(false);

        mockMvc.perform(put("/api/v1/people/bob/profile")
                .contentType("application/json")
                .content("""
                    {
                      "displayName": "Bob"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value(containsString("not allowed")));
    }

    @Test
    @DisplayName("Single person preference returns keyed entry")
    void getPersonPreferenceReturnsKeyedEntry() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPreferences(new HashMap<>(Map.of("theme", "dark")));

        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/v1/people/alice/preferences/theme"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key").value("theme"))
            .andExpect(jsonPath("$.value").value("dark"));
    }

    @Test
    @DisplayName("Person activities combine login, comments, favorites, and group membership")
    void getPersonActivitiesReturnsTimeline() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setLastLoginDate(LocalDateTime.of(2026, 3, 18, 9, 30));
        user.setLastPasswordChangeDate(LocalDateTime.of(2026, 3, 17, 8, 0));

        Group group = new Group();
        group.setId(UUID.randomUUID());
        group.setName("finance");
        group.setDisplayName("Finance");
        group.setCreatedDate(LocalDateTime.of(2026, 3, 18, 11, 0));
        user.getGroups().add(group);

        Favorite favorite = buildFavorite(UUID.randomUUID(), UUID.randomUUID(), "Quarterly Report", Node.NodeType.DOCUMENT);
        favorite.setCreatedAt(LocalDateTime.of(2026, 3, 18, 10, 15));

        Comment comment = new Comment();
        comment.setId(UUID.randomUUID());
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("board-pack.pdf");
        document.setMimeType("application/pdf");
        comment.setNode(document);
        comment.setContent("Looks good");
        comment.setAuthor("alice");
        comment.setCreated(Date.from(Instant.parse("2026-03-18T10:30:00Z")));

        Pageable pageable = PageRequest.of(0, 10);
        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Mockito.when(favoriteService.getFavoritesForUser("alice", pageable))
            .thenReturn(new PageImpl<>(List.of(favorite), pageable, 1));
        Mockito.when(commentService.getUserComments("alice", PageRequest.of(0, 10)))
            .thenReturn(new PageImpl<>(List.of(comment), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/people/alice/activities"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].type", hasItems("SITE_MEMBERSHIP", "COMMENT", "FAVORITE", "PROFILE")));
    }

    @Test
    @DisplayName("Person sites are mapped from group memberships")
    void getPersonSitesReturnsGroupBackedSites() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");

        Group finance = new Group();
        finance.setId(UUID.randomUUID());
        finance.setName("finance");
        finance.setDisplayName("Finance");
        finance.setDescription("Finance workspace");
        finance.setGroupType(Group.GroupType.PROJECT);
        finance.setCreatedDate(LocalDateTime.of(2026, 3, 18, 11, 0));

        user.getGroups().add(finance);
        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/v1/people/alice/sites"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].siteId").value("finance"))
            .andExpect(jsonPath("$[0].title").value("Finance"))
            .andExpect(jsonPath("$[0].role").value("PROJECT"));
    }

    @Test
    @DisplayName("Favorite sites derive from folder favorites")
    void getPersonFavoriteSitesReturnsFolderFavorites() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");

        UUID folderId = UUID.randomUUID();
        Favorite folderFavorite = buildFolderFavorite(UUID.randomUUID(), folderId, "Team Workspace", Folder.FolderType.WORKSPACE);
        Favorite documentFavorite = buildFavorite(UUID.randomUUID(), UUID.randomUUID(), "Report", Node.NodeType.DOCUMENT);
        Pageable pageable = PageRequest.of(0, 10);

        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Mockito.when(favoriteService.getFavoritesForUser("alice", pageable))
            .thenReturn(new PageImpl<>(List.of(folderFavorite, documentFavorite), pageable, 2));

        mockMvc.perform(get("/api/v1/people/alice/favorite-sites"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].siteId").value(folderId.toString()))
            .andExpect(jsonPath("$[0].folderType").value("WORKSPACE"));
    }

    @Test
    @DisplayName("Person favorite relation can fetch a single favorite")
    void getPersonFavoriteReturnsSingleFavorite() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");

        UUID nodeId = UUID.randomUUID();
        Favorite favorite = buildFavorite(UUID.randomUUID(), nodeId, "Quarterly Report", Node.NodeType.DOCUMENT);

        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Mockito.when(favoriteService.getFavoriteForUser("alice", nodeId)).thenReturn(favorite);

        mockMvc.perform(get("/api/v1/people/alice/favorites/" + nodeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodeId").value(nodeId.toString()))
            .andExpect(jsonPath("$.nodeName").value("Quarterly Report"));
    }

    @Test
    @DisplayName("Current user can create a favorite through people relation")
    void createPersonFavoriteCreatesFavorite() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");

        UUID nodeId = UUID.randomUUID();
        Favorite favorite = buildFavorite(UUID.randomUUID(), nodeId, "Design Spec", Node.NodeType.DOCUMENT);

        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Mockito.when(securityService.getCurrentUser()).thenReturn("alice");
        Mockito.when(favoriteService.addFavoriteForUser("alice", nodeId)).thenReturn(favorite);

        mockMvc.perform(post("/api/v1/people/alice/favorites")
                .contentType("application/json")
                .content("""
                    {
                      "nodeId": "%s"
                    }
                    """.formatted(nodeId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nodeId").value(nodeId.toString()))
            .andExpect(jsonPath("$.nodeName").value("Design Spec"));
    }

    @Test
    @DisplayName("Current user can delete a favorite through people relation")
    void deletePersonFavoriteRemovesFavorite() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");

        UUID nodeId = UUID.randomUUID();

        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Mockito.when(securityService.getCurrentUser()).thenReturn("alice");

        mockMvc.perform(delete("/api/v1/people/alice/favorites/" + nodeId))
            .andExpect(status().isNoContent());

        Mockito.verify(favoriteService).removeFavoriteForUser("alice", nodeId);
    }

    @Test
    @DisplayName("Current user can create a favorite site through people relation")
    void createPersonFavoriteSiteCreatesWorkspaceFavorite() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");

        UUID nodeId = UUID.randomUUID();
        Favorite favorite = buildFolderFavorite(UUID.randomUUID(), nodeId, "Finance Workspace", Folder.FolderType.WORKSPACE);

        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Mockito.when(securityService.getCurrentUser()).thenReturn("alice");
        Mockito.when(favoriteService.addFavoriteSiteForUser("alice", nodeId)).thenReturn(favorite);

        mockMvc.perform(post("/api/v1/people/alice/favorite-sites")
                .contentType("application/json")
                .content("""
                    {
                      "nodeId": "%s"
                    }
                    """.formatted(nodeId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.siteId").value(nodeId.toString()))
            .andExpect(jsonPath("$.title").value("Finance Workspace"))
            .andExpect(jsonPath("$.folderType").value("WORKSPACE"));
    }

    @Test
    @DisplayName("Current user can delete a favorite site through people relation")
    void deletePersonFavoriteSiteRemovesWorkspaceFavorite() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");

        UUID nodeId = UUID.randomUUID();
        Favorite favorite = buildFolderFavorite(UUID.randomUUID(), nodeId, "Finance Workspace", Folder.FolderType.WORKSPACE);

        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Mockito.when(securityService.getCurrentUser()).thenReturn("alice");
        Mockito.when(favoriteService.getFavoriteForUser("alice", nodeId)).thenReturn(favorite);

        mockMvc.perform(delete("/api/v1/people/alice/favorite-sites/" + nodeId))
            .andExpect(status().isNoContent());

        Mockito.verify(favoriteService).removeFavoriteForUser("alice", nodeId);
    }

    @Test
    @DisplayName("Site membership requests are read from user preferences")
    void getPersonSiteMembershipRequestsReturnsPreferences() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        Map<String, Object> request = new HashMap<>();
        request.put("siteId", "finance");
        request.put("siteTitle", "Finance");
        request.put("role", "Contributor");
        request.put("status", "PENDING");
        request.put("message", "Need access for quarterly planning");
        request.put("requestedAt", "2026-03-18T11:45:00");
        user.setPreferences(new HashMap<>(Map.of("siteMembershipRequests", List.of(request))));

        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/v1/people/alice/site-membership-requests"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].siteId").value("finance"))
            .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("Admins can list visible site membership requests with filters")
    void listVisibleSiteMembershipRequestsReturnsPagedResults() throws Exception {
        User alice = new User();
        alice.setId(UUID.randomUUID());
        alice.setUsername("alice");
        alice.setEmail("alice@example.com");
        alice.setPreferences(new HashMap<>(Map.of(
            "siteMembershipRequests",
            List.of(
                buildMembershipRequest("finance", "Finance", "Contributor", "PENDING", "Need access", "2026-03-18T11:45:00"),
                buildMembershipRequest("legal", "Legal", "Viewer", "REJECTED", "Already have access", "2026-03-18T12:10:00")
            )
        )));

        User bob = new User();
        bob.setId(UUID.randomUUID());
        bob.setUsername("bob");
        bob.setEmail("bob@example.com");
        bob.setPreferences(new HashMap<>(Map.of(
            "siteMembershipRequests",
            List.of(buildMembershipRequest("finance", "Finance", "Collaborator", "APPROVED", "Approved", "2026-03-17T09:30:00"))
        )));

        Mockito.when(userRepository.findAll()).thenReturn(List.of(alice, bob));
        Mockito.when(securityService.getCurrentUser()).thenReturn("admin");
        Mockito.when(securityService.isAdmin("admin")).thenReturn(true);

        mockMvc.perform(get("/api/v1/people/site-membership-requests")
                .param("siteId", "finance")
                .param("status", "PENDING")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].username").value("alice"))
            .andExpect(jsonPath("$.content[0].siteId").value("finance"))
            .andExpect(jsonPath("$.content[0].status").value("PENDING"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Admins can approve a site membership request and persist decision metadata")
    void approvePersonSiteMembershipRequestUpdatesDecisionMetadata() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPreferences(new HashMap<>(Map.of(
            "siteMembershipRequests",
            List.of(buildMembershipRequest("finance", "Finance Workspace", "Contributor", "PENDING", "Need access", "2026-03-18T11:45:00"))
        )));

        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Mockito.when(securityService.getCurrentUser()).thenReturn("admin");
        Mockito.when(securityService.isAdmin("admin")).thenReturn(true);
        Mockito.when(userRepository.save(Mockito.any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/v1/people/alice/site-membership-requests/finance/approve")
                .contentType("application/json")
                .content("""
                    {
                      "decisionComment": "Looks good"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.decisionBy").value("admin"))
            .andExpect(jsonPath("$.decisionComment").value("Looks good"))
            .andExpect(jsonPath("$.decisionAt").exists());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> requests = (List<Map<String, Object>>) user.getPreferences().get("siteMembershipRequests");
        org.junit.jupiter.api.Assertions.assertEquals("APPROVED", requests.get(0).get("status"));
        org.junit.jupiter.api.Assertions.assertEquals("admin", requests.get(0).get("decisionBy"));
        org.junit.jupiter.api.Assertions.assertEquals("Looks good", requests.get(0).get("decisionComment"));
    }

    @Test
    @DisplayName("Admins can reject a site membership request and persist decision metadata")
    void rejectPersonSiteMembershipRequestUpdatesDecisionMetadata() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPreferences(new HashMap<>(Map.of(
            "siteMembershipRequests",
            List.of(buildMembershipRequest("finance", "Finance Workspace", "Contributor", "PENDING", "Need access", "2026-03-18T11:45:00"))
        )));

        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Mockito.when(securityService.getCurrentUser()).thenReturn("admin");
        Mockito.when(securityService.isAdmin("admin")).thenReturn(true);
        Mockito.when(userRepository.save(Mockito.any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/v1/people/alice/site-membership-requests/finance/reject")
                .contentType("application/json")
                .content("""
                    {
                      "decisionComment": "Not moderated yet"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.decisionBy").value("admin"))
            .andExpect(jsonPath("$.decisionComment").value("Not moderated yet"));
    }

    @Test
    @DisplayName("Current user can create a site membership request")
    void createPersonSiteMembershipRequestAddsEntryToPreferences() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPreferences(new HashMap<>());

        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Mockito.when(securityService.getCurrentUser()).thenReturn("alice");
        Mockito.when(userRepository.save(Mockito.any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/v1/people/alice/site-membership-requests")
                .contentType("application/json")
                .content("""
                    {
                      "siteId": "finance",
                      "siteTitle": "Finance Workspace",
                      "role": "Contributor",
                      "message": "Need access for quarterly planning"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.siteId").value("finance"))
            .andExpect(jsonPath("$.siteTitle").value("Finance Workspace"))
            .andExpect(jsonPath("$.role").value("Contributor"))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.message").value("Need access for quarterly planning"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> requests = (List<Map<String, Object>>) user.getPreferences().get("siteMembershipRequests");
        org.junit.jupiter.api.Assertions.assertEquals(1, requests.size());
        org.junit.jupiter.api.Assertions.assertEquals("finance", requests.get(0).get("siteId"));
    }

    @Test
    @DisplayName("Current user can update an existing site membership request")
    void updatePersonSiteMembershipRequestReplacesEntry() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        Map<String, Object> request = new HashMap<>();
        request.put("siteId", "finance");
        request.put("siteTitle", "Finance Workspace");
        request.put("role", "Contributor");
        request.put("status", "PENDING");
        request.put("message", "Need access for quarterly planning");
        request.put("requestedAt", "2026-03-18T11:45:00");
        user.setPreferences(new HashMap<>(Map.of("siteMembershipRequests", List.of(request))));

        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Mockito.when(securityService.getCurrentUser()).thenReturn("alice");
        Mockito.when(userRepository.save(Mockito.any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/v1/people/alice/site-membership-requests/finance")
                .contentType("application/json")
                .content("""
                    {
                      "siteTitle": "Finance Workspace",
                      "role": "Collaborator",
                      "message": "Updated access request"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.siteId").value("finance"))
            .andExpect(jsonPath("$.role").value("Collaborator"))
            .andExpect(jsonPath("$.message").value("Updated access request"))
            .andExpect(jsonPath("$.status").value("PENDING"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> requests = (List<Map<String, Object>>) user.getPreferences().get("siteMembershipRequests");
        org.junit.jupiter.api.Assertions.assertEquals(1, requests.size());
        org.junit.jupiter.api.Assertions.assertEquals("Collaborator", requests.get(0).get("role"));
        org.junit.jupiter.api.Assertions.assertEquals("Updated access request", requests.get(0).get("message"));
    }

    @Test
    @DisplayName("Current user can withdraw a site membership request")
    void withdrawPersonSiteMembershipRequestRemovesPreferenceEntry() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        Map<String, Object> financeRequest = new HashMap<>();
        financeRequest.put("siteId", "finance");
        financeRequest.put("siteTitle", "Finance");
        financeRequest.put("status", "PENDING");
        Map<String, Object> legalRequest = new HashMap<>();
        legalRequest.put("siteId", "legal");
        legalRequest.put("siteTitle", "Legal");
        legalRequest.put("status", "PENDING");
        user.setPreferences(new HashMap<>(Map.of("siteMembershipRequests", List.of(financeRequest, legalRequest))));

        Mockito.when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Mockito.when(securityService.getCurrentUser()).thenReturn("alice");
        Mockito.when(userRepository.save(Mockito.any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(delete("/api/v1/people/alice/site-membership-requests/finance"))
            .andExpect(status().isNoContent());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> remaining = (List<Map<String, Object>>) user.getPreferences().get("siteMembershipRequests");
        org.junit.jupiter.api.Assertions.assertEquals(1, remaining.size());
        org.junit.jupiter.api.Assertions.assertEquals("legal", remaining.get(0).get("siteId"));
    }

    private Favorite buildFavorite(UUID favoriteId, UUID nodeId, String nodeName, Node.NodeType nodeType) {
        Favorite favorite = Mockito.mock(Favorite.class);
        Node node = Mockito.mock(Node.class);
        Mockito.lenient().when(favorite.getId()).thenReturn(favoriteId);
        Mockito.lenient().when(favorite.getNode()).thenReturn(node);
        Mockito.lenient().when(node.getId()).thenReturn(nodeId);
        Mockito.lenient().when(node.getName()).thenReturn(nodeName);
        Mockito.lenient().when(node.getNodeType()).thenReturn(nodeType);
        Mockito.lenient().when(favorite.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 3, 18, 12, 0));
        return favorite;
    }

    private Favorite buildFolderFavorite(UUID favoriteId, UUID nodeId, String nodeName, Folder.FolderType folderType) {
        Favorite favorite = Mockito.mock(Favorite.class);
        Folder folder = Mockito.mock(Folder.class);
        Mockito.lenient().when(favorite.getId()).thenReturn(favoriteId);
        Mockito.lenient().when(favorite.getNode()).thenReturn(folder);
        Mockito.lenient().when(folder.getId()).thenReturn(nodeId);
        Mockito.lenient().when(folder.getName()).thenReturn(nodeName);
        Mockito.lenient().when(folder.getFolderType()).thenReturn(folderType);
        Mockito.lenient().when(folder.getNodeType()).thenReturn(Node.NodeType.FOLDER);
        Mockito.lenient().when(folder.getPath()).thenReturn("/Sites/" + nodeName.replace(' ', '_'));
        Mockito.lenient().when(favorite.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 3, 18, 12, 0));
        return favorite;
    }

    private Map<String, Object> buildMembershipRequest(
        String siteId,
        String siteTitle,
        String role,
        String status,
        String message,
        String requestedAt
    ) {
        Map<String, Object> request = new HashMap<>();
        request.put("siteId", siteId);
        request.put("siteTitle", siteTitle);
        request.put("role", role);
        request.put("status", status);
        request.put("message", message);
        request.put("requestedAt", requestedAt);
        return request;
    }
}
