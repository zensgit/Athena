package com.ecm.core.service;

import com.ecm.core.dto.CreateGroupRequest;
import com.ecm.core.dto.CreateUserRequest;
import com.ecm.core.dto.GroupDto;
import com.ecm.core.dto.UpdateUserRequest;
import com.ecm.core.dto.UserDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@ConditionalOnProperty(name = "ecm.identity.provider", havingValue = "keycloak")
public class KeycloakUserGroupBackend implements UserGroupBackend {

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int TOKEN_SAFETY_WINDOW_SECONDS = 30;

    private final RestClient restClient;
    private final String realm;
    private final String adminRealm;
    private final String adminClientId;
    private final String adminUsername;
    private final String adminPassword;

    private volatile CachedToken cachedToken;

    public KeycloakUserGroupBackend(
        RestClient.Builder restClientBuilder,
        @Value("${ecm.keycloak.url:http://keycloak:8080}") String keycloakUrl,
        @Value("${ecm.keycloak.realm:ecm}") String realm,
        @Value("${ecm.keycloak.admin-realm:master}") String adminRealm,
        @Value("${ecm.keycloak.admin-client-id:admin-cli}") String adminClientId,
        @Value("${ecm.keycloak.admin-username:}") String adminUsername,
        @Value("${ecm.keycloak.admin-password:}") String adminPassword
    ) {
        this.restClient = restClientBuilder.baseUrl(keycloakUrl).build();
        this.realm = realm;
        this.adminRealm = adminRealm;
        this.adminClientId = adminClientId;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public Page<UserDto> searchUsers(String query, Pageable pageable) {
        int first = Math.toIntExact(pageable.getOffset());
        int max = pageable.getPageSize() > 0 ? pageable.getPageSize() : DEFAULT_PAGE_SIZE;

        List<KeycloakUser> users = restClient.get()
            .uri(uriBuilder -> buildUsersUri(uriBuilder, query, first, max))
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(new ParameterizedTypeReference<List<KeycloakUser>>() {});

        if (users == null) {
            users = List.of();
        }

        Integer total = restClient.get()
            .uri(uriBuilder -> buildUsersCountUri(uriBuilder, query))
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(Integer.class);

        long totalElements = total != null ? total : users.size();
        List<UserDto> content = users.stream().map(this::toDto).toList();
        return new PageImpl<>(content, pageable, totalElements);
    }

    @Override
    public UserDto getUser(String username) {
        String userId = findUserId(username);
        KeycloakUser user = restClient.get()
            .uri("/admin/realms/{realm}/users/{id}", realm, userId)
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(KeycloakUser.class);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + username);
        }
        return toDto(user);
    }

    @Override
    public UserDto createUser(CreateUserRequest user) {
        if (adminUsername == null || adminUsername.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("Keycloak admin credentials are not configured for user management");
        }

        Map<String, Object> safePayload = new HashMap<>();
        safePayload.put("username", user.username());
        safePayload.put("email", user.email());
        safePayload.put("enabled", user.enabled() == null || user.enabled());
        if (user.firstName() != null && !user.firstName().isBlank()) {
            safePayload.put("firstName", user.firstName());
        }
        if (user.lastName() != null && !user.lastName().isBlank()) {
            safePayload.put("lastName", user.lastName());
        }
        safePayload.values().removeIf(Objects::isNull);

        ResponseEntity<Void> resp = restClient.post()
            .uri("/admin/realms/{realm}/users", realm)
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .body(safePayload)
            .retrieve()
            .toBodilessEntity();

        String userId = extractId(resp.getHeaders().getLocation());
        if (userId == null) {
            userId = findUserId(user.username());
        }

        if (user.password() != null && !user.password().isBlank()) {
            restClient.put()
                .uri("/admin/realms/{realm}/users/{id}/reset-password", realm, userId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "password", "value", user.password(), "temporary", false))
                .retrieve()
                .toBodilessEntity();
        }

        // Assign default realm role "user" (best-effort).
        try {
            KeycloakRole role = restClient.get()
                .uri("/admin/realms/{realm}/roles/{roleName}", realm, "user")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(KeycloakRole.class);

            if (role != null) {
                restClient.post()
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId)
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(role))
                    .retrieve()
                    .toBodilessEntity();
            }
        } catch (Exception ex) {
            log.debug("Failed to assign default Keycloak role to user {}: {}", user.username(), ex.getMessage());
        }

        return getUser(user.username());
    }

    @Override
    public UserDto updateUser(String username, UpdateUserRequest updates) {
        String userId = findUserId(username);

        Map<String, Object> existing = restClient.get()
            .uri("/admin/realms/{realm}/users/{id}", realm, userId)
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (existing == null) {
            throw new IllegalArgumentException("User not found: " + username);
        }

        if (updates.email() != null) {
            existing.put("email", updates.email());
        }
        if (updates.firstName() != null) {
            existing.put("firstName", updates.firstName());
        }
        if (updates.lastName() != null) {
            existing.put("lastName", updates.lastName());
        }
        if (updates.enabled() != null) {
            existing.put("enabled", updates.enabled());
        }

        restClient.put()
            .uri("/admin/realms/{realm}/users/{id}", realm, userId)
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .body(existing)
            .retrieve()
            .toBodilessEntity();

        return getUser(username);
    }

    @Override
    public Page<GroupDto> getGroups(Pageable pageable) {
        int first = Math.toIntExact(pageable.getOffset());
        int max = pageable.getPageSize() > 0 ? pageable.getPageSize() : DEFAULT_PAGE_SIZE;

        List<KeycloakGroup> groups = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/admin/realms/{realm}/groups")
                .queryParam("first", first)
                .queryParam("max", max)
                .queryParam("briefRepresentation", false)
                .build(realm))
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(new ParameterizedTypeReference<List<KeycloakGroup>>() {});

        if (groups == null) {
            groups = List.of();
        }

        List<GroupDto> content = groups.stream().map(this::toDto).toList();
        return new PageImpl<>(content, pageable, content.size());
    }

    @Override
    public GroupDto createGroup(CreateGroupRequest request) {
        Map<String, Object> payload = Map.of(
            "name", request.name(),
            "attributes", request.displayName() != null
                ? Map.of("displayName", List.of(request.displayName()))
                : Collections.emptyMap()
        );

        ResponseEntity<Void> resp = restClient.post()
            .uri("/admin/realms/{realm}/groups", realm)
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .toBodilessEntity();

        String groupId = extractId(resp.getHeaders().getLocation());
        if (groupId == null) {
            groupId = findGroupId(request.name());
        }

        KeycloakGroup group = restClient.get()
            .uri("/admin/realms/{realm}/groups/{id}", realm, groupId)
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(KeycloakGroup.class);

        if (group == null) {
            throw new IllegalStateException("Failed to create group: " + request.name());
        }

        return toDto(group);
    }

    @Override
    public void deleteGroup(String name) {
        String groupId = findGroupId(name);
        restClient.delete()
            .uri("/admin/realms/{realm}/groups/{id}", realm, groupId)
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .toBodilessEntity();
    }

    @Override
    public void addUserToGroup(String username, String groupName) {
        String userId = findUserId(username);
        String groupId = findGroupId(groupName);
        restClient.put()
            .uri("/admin/realms/{realm}/users/{userId}/groups/{groupId}", realm, userId, groupId)
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .toBodilessEntity();
    }

    @Override
    public void removeUserFromGroup(String username, String groupName) {
        String userId = findUserId(username);
        String groupId = findGroupId(groupName);
        restClient.delete()
            .uri("/admin/realms/{realm}/users/{userId}/groups/{groupId}", realm, userId, groupId)
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .toBodilessEntity();
    }

    private URI buildUsersUri(UriBuilder uriBuilder, String query, int first, int max) {
        UriBuilder b = uriBuilder
            .path("/admin/realms/{realm}/users")
            .queryParam("first", first)
            .queryParam("max", max);
        if (query != null && !query.isBlank()) {
            b = b.queryParam("search", query);
        }
        return b.build(realm);
    }

    private URI buildUsersCountUri(UriBuilder uriBuilder, String query) {
        UriBuilder b = uriBuilder.path("/admin/realms/{realm}/users/count");
        if (query != null && !query.isBlank()) {
            b = b.queryParam("search", query);
        }
        return b.build(realm);
    }

    private String findUserId(String username) {
        List<KeycloakUser> users = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/admin/realms/{realm}/users")
                .queryParam("username", username)
                .queryParam("max", 5)
                .build(realm))
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(new ParameterizedTypeReference<List<KeycloakUser>>() {});

        if (users == null || users.isEmpty()) {
            // Fallback to search
            users = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/admin/realms/{realm}/users")
                    .queryParam("search", username)
                    .queryParam("max", 20)
                    .build(realm))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(new ParameterizedTypeReference<List<KeycloakUser>>() {});
        }

        if (users == null) {
            users = List.of();
        }

        return users.stream()
            .filter(u -> u.username() != null && u.username().equalsIgnoreCase(username))
            .map(KeycloakUser::id)
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }

    private String findGroupId(String name) {
        List<KeycloakGroup> groups = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/admin/realms/{realm}/groups")
                .queryParam("search", name)
                .queryParam("max", 50)
                .build(realm))
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(new ParameterizedTypeReference<List<KeycloakGroup>>() {});

        if (groups == null) {
            groups = List.of();
        }

        return groups.stream()
            .filter(g -> g.name() != null && g.name().equalsIgnoreCase(name))
            .map(KeycloakGroup::id)
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Group not found: " + name));
    }

    private UserDto toDto(KeycloakUser user) {
        List<String> roles = getRoleNames(user.id());
        return new UserDto(
            user.id(),
            user.username(),
            user.email(),
            roles,
            user.firstName(),
            user.lastName(),
            user.enabled(),
            false
        );
    }

    private List<String> getRoleNames(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }

        try {
            KeycloakRoleMappings mappings = restClient.get()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings", realm, userId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(KeycloakRoleMappings.class);

            if (mappings == null) {
                return List.of();
            }

            Map<String, Boolean> unique = new HashMap<>();
            if (mappings.realmMappings() != null) {
                for (KeycloakRole role : mappings.realmMappings()) {
                    if (role != null && role.name() != null && !role.name().isBlank()) {
                        unique.put(role.name(), Boolean.TRUE);
                    }
                }
            }

            if (mappings.clientMappings() != null) {
                for (KeycloakClientRoleMappings clientMapping : mappings.clientMappings().values()) {
                    if (clientMapping == null || clientMapping.mappings() == null) {
                        continue;
                    }
                    for (KeycloakRole role : clientMapping.mappings()) {
                        if (role != null && role.name() != null && !role.name().isBlank()) {
                            unique.put(role.name(), Boolean.TRUE);
                        }
                    }
                }
            }

            return unique.keySet().stream().sorted().toList();
        } catch (Exception ex) {
            log.debug("Failed to fetch Keycloak role mappings for user {}: {}", userId, ex.getMessage());
            return List.of();
        }
    }

    private GroupDto toDto(KeycloakGroup group) {
        String displayName = null;
        if (group.attributes() != null) {
            List<String> names = group.attributes().get("displayName");
            if (names != null && !names.isEmpty()) {
                displayName = names.get(0);
            }
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = group.name();
        }
        return new GroupDto(
            group.id(),
            group.name(),
            displayName,
            null,
            null,
            true,
            null,
            null
        );
    }

    private String bearer() {
        return "Bearer " + adminAccessToken();
    }

    private String adminAccessToken() {
        CachedToken token = cachedToken;
        Instant now = Instant.now();
        if (token != null && token.expiresAt().isAfter(now.plusSeconds(TOKEN_SAFETY_WINDOW_SECONDS))) {
            return token.value();
        }
        synchronized (this) {
            token = cachedToken;
            if (token != null && token.expiresAt().isAfter(Instant.now().plusSeconds(TOKEN_SAFETY_WINDOW_SECONDS))) {
                return token.value();
            }
            cachedToken = fetchAdminToken();
            return cachedToken.value();
        }
    }

    private CachedToken fetchAdminToken() {
        if (adminUsername == null || adminUsername.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("Keycloak admin credentials are not configured (ecm.keycloak.admin-username/admin-password)");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", adminClientId);
        form.add("username", adminUsername);
        form.add("password", adminPassword);

        TokenResponse resp = restClient.post()
            .uri("/realms/{realm}/protocol/openid-connect/token", adminRealm)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(TokenResponse.class);

        if (resp == null || resp.accessToken() == null || resp.accessToken().isBlank()) {
            throw new IllegalStateException("Failed to obtain Keycloak admin access token");
        }

        Instant expiresAt = Instant.now().plusSeconds(Math.max(30, resp.expiresIn()));
        return new CachedToken(resp.accessToken(), expiresAt);
    }

    private String extractId(URI location) {
        if (location == null) {
            return null;
        }
        String path = location.getPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        int idx = path.lastIndexOf('/');
        if (idx < 0 || idx == path.length() - 1) {
            return null;
        }
        return path.substring(idx + 1);
    }

    private record CachedToken(String value, Instant expiresAt) {}

    private record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") long expiresIn
    ) {}

    private record KeycloakUser(
        String id,
        String username,
        String email,
        Boolean enabled,
        String firstName,
        String lastName
    ) {}

    private record KeycloakGroup(
        String id,
        String name,
        Map<String, List<String>> attributes
    ) {}

    private record KeycloakRoleMappings(
        @JsonProperty("realmMappings") List<KeycloakRole> realmMappings,
        @JsonProperty("clientMappings") Map<String, KeycloakClientRoleMappings> clientMappings
    ) {}

    private record KeycloakClientRoleMappings(
        String id,
        String client,
        List<KeycloakRole> mappings
    ) {}

    private record KeycloakRole(String id, String name) {}
}
