package com.ecm.core.controller;

import com.ecm.core.dto.GroupDto;
import com.ecm.core.dto.UserDto;
import com.ecm.core.entity.Favorite;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Group;
import com.ecm.core.entity.User;
import com.ecm.core.exception.AccessDeniedException;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.model.Comment;
import com.ecm.core.repository.FavoriteRepository;
import com.ecm.core.repository.CommentRepository;
import com.ecm.core.repository.UserRepository;
import com.ecm.core.service.FavoriteService;
import com.ecm.core.service.SecurityService;
import com.ecm.core.service.UserGroupService;
import com.ecm.core.service.PreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.data.domain.PageRequest;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/people")
@RequiredArgsConstructor
@Tag(name = "People", description = "Directory lookup for users, groups, and favorites")
@Transactional(readOnly = true)
public class PeopleController {

    private static final int MAX_RECENT_ACTIVITIES = 12;
    private static final int MAX_RECENT_FAVORITES = 10;
    private static final int MAX_RECENT_COMMENTS = 10;
    private static final DateTimeFormatter ACTIVITY_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String SITE_REQUESTS_KEY = "siteMembershipRequests";
    
    private final UserGroupService userGroupService;
    private final UserRepository userRepository;
    private final FavoriteService favoriteService;
    private final FavoriteRepository favoriteRepository;
    private final CommentRepository commentRepository;
    private final SecurityService securityService;
    private final PreferenceService preferenceService;

    @GetMapping
    @Operation(summary = "Search people", description = "Search users by username or email for mention and approver pickers")
    public ResponseEntity<Page<UserDto>> searchPeople(
            @RequestParam(required = false) String query,
            Pageable pageable) {
        return ResponseEntity.ok(userGroupService.searchUsers(query, pageable));
    }

    @GetMapping("/{username}")
    @Operation(summary = "Get person", description = "Get a user profile for directory lookups")
    public ResponseEntity<UserDto> getPerson(@PathVariable String username) {
        try {
            return ResponseEntity.ok(userGroupService.getUser(username));
        } catch (IllegalArgumentException ex) {
            throw new ResourceNotFoundException(ex.getMessage(), ex);
        }
    }

    @GetMapping("/{username}/groups")
    @Operation(summary = "List person groups", description = "Get the groups a user belongs to")
    public ResponseEntity<List<GroupDto>> getPersonGroups(@PathVariable String username) {
        User user = requireUser(username);
        List<GroupDto> groups = user.getGroups().stream()
            .sorted(Comparator.comparing(Group::getName, String.CASE_INSENSITIVE_ORDER))
            .map(this::toGroupDto)
            .toList();
        return ResponseEntity.ok(groups);
    }

    @GetMapping("/{username}/favorites")
    @Operation(summary = "List person favorites", description = "Get the favorite nodes for a user")
    public ResponseEntity<Page<FavoriteDto>> getPersonFavorites(@PathVariable String username, Pageable pageable) {
        requireUser(username);
        return ResponseEntity.ok(loadFavorites(username, pageable).map(FavoriteDto::from));
    }

    @GetMapping("/{username}/favorites/{nodeId}")
    @Operation(summary = "Get person favorite", description = "Get a specific favorite node relationship for a user")
    public ResponseEntity<FavoriteDto> getPersonFavorite(
        @PathVariable String username,
        @PathVariable String nodeId
    ) {
        requireUser(username);
        return ResponseEntity.ok(FavoriteDto.from(loadFavorite(username, nodeId)));
    }

    @PostMapping("/{username}/favorites")
    @Transactional
    @Operation(summary = "Create person favorite", description = "Create a favorite node relationship for the current user or an admin-managed profile")
    public ResponseEntity<FavoriteDto> createPersonFavorite(
        @PathVariable String username,
        @RequestBody PersonFavoriteWriteRequest request
    ) {
        User user = requireWritableUser(username);
        Favorite favorite = favoriteService.addFavoriteForUser(user.getUsername(), parseRequiredUuid(request.nodeId(), "nodeId"));
        return ResponseEntity.status(HttpStatus.CREATED).body(FavoriteDto.from(favorite));
    }

    @DeleteMapping("/{username}/favorites/{nodeId}")
    @Transactional
    @Operation(summary = "Delete person favorite", description = "Delete a favorite node relationship for the current user or an admin-managed profile")
    public ResponseEntity<Void> deletePersonFavorite(
        @PathVariable String username,
        @PathVariable String nodeId
    ) {
        User user = requireWritableUser(username);
        favoriteService.removeFavoriteForUser(user.getUsername(), parseRequiredUuid(nodeId, "nodeId"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{username}/preferences")
    @Operation(summary = "Get person preferences",
               description = "Get user preference values and profile metadata. Use ?filter=org.athena to filter by namespace prefix.")
    public ResponseEntity<PeoplePreferencesDto> getPersonPreferences(
            @PathVariable String username,
            @RequestParam(required = false) String filter) {
        User user = requireUser(username);
        if (filter != null && !filter.isBlank()) {
            Map<String, Object> filtered = preferenceService.getPreferences(username, filter);
            return ResponseEntity.ok(PeoplePreferencesDto.fromFiltered(user, filtered));
        }
        return ResponseEntity.ok(PeoplePreferencesDto.from(user));
    }

    @GetMapping("/{username}/preferences/export")
    @Operation(summary = "Export person preferences", description = "Export the full preference map as JSON for download or backup.")
    public ResponseEntity<Map<String, Object>> exportPersonPreferences(@PathVariable String username) {
        requireUser(username);
        return ResponseEntity.ok(preferenceService.exportPreferences(username));
    }

    @GetMapping("/{username}/preferences/namespaces")
    @Operation(summary = "List preference namespaces", description = "List distinct top-level namespace prefixes for a user's preferences")
    public ResponseEntity<List<String>> listPreferenceNamespaces(@PathVariable String username) {
        return ResponseEntity.ok(preferenceService.listNamespaces(username));
    }

    @PutMapping("/{username}/profile")
    @Transactional
    @Operation(summary = "Update person profile", description = "Update writable profile fields for the current user or an admin-managed profile")
    public ResponseEntity<PeoplePreferencesDto> updatePersonProfile(
        @PathVariable String username,
        @RequestBody PeopleProfileUpdateRequest request
    ) {
        User user = requireWritableUser(username);
        user.setFirstName(normalizeOptionalString(request.firstName()));
        user.setLastName(normalizeOptionalString(request.lastName()));
        user.setDisplayName(normalizeOptionalString(request.displayName()));
        user.setPhone(normalizeOptionalString(request.phone()));
        user.setDepartment(normalizeOptionalString(request.department()));
        user.setJobTitle(normalizeOptionalString(request.jobTitle()));
        user.setAvatarUrl(normalizeOptionalString(request.avatarUrl()));

        String locale = normalizeOptionalString(request.locale());
        user.setLocale(locale != null ? locale : "en_US");
        String timezone = normalizeOptionalString(request.timezone());
        user.setTimezone(timezone != null ? timezone : "UTC");

        return ResponseEntity.ok(PeoplePreferencesDto.from(userRepository.save(user)));
    }

    @GetMapping("/{username}/preferences/{preferenceName}")
    @Operation(summary = "Get person preference", description = "Get an individual preference entry for a user")
    public ResponseEntity<PreferenceEntryDto> getPersonPreference(
        @PathVariable String username,
        @PathVariable String preferenceName
    ) {
        User user = requireUser(username);
        Map<String, Object> preferences = user.getPreferences() == null
            ? Map.of()
            : new LinkedHashMap<>(user.getPreferences());
        if (!preferences.containsKey(preferenceName)) {
            throw new ResourceNotFoundException("Preference not found: " + preferenceName);
        }
        return ResponseEntity.ok(new PreferenceEntryDto(preferenceName, preferences.get(preferenceName)));
    }

    @PutMapping("/{username}/preferences")
    @Transactional
    @Operation(summary = "Update person preferences", description = "Replace the raw preference map for the current user or an admin-managed profile")
    public ResponseEntity<PeoplePreferencesDto> updatePersonPreferences(
        @PathVariable String username,
        @RequestBody PeoplePreferencesUpdateRequest request
    ) {
        Map<String, Object> updated = preferenceService.replaceAll(
            username,
            request != null ? request.preferences() : null
        );
        User user = requireUser(username);
        return ResponseEntity.ok(PeoplePreferencesDto.fromFiltered(user, updated));
    }

    @PostMapping("/{username}/preferences/import")
    @Transactional
    @Operation(summary = "Import person preferences", description = "Replace the stored preference map from an imported JSON payload after validation.")
    public ResponseEntity<PeoplePreferencesDto> importPersonPreferences(
        @PathVariable String username,
        @RequestBody PeoplePreferencesUpdateRequest request
    ) {
        requireWritableUser(username);
        Map<String, Object> updated = preferenceService.importPreferences(
            username,
            request != null ? request.preferences() : null
        );
        User user = requireUser(username);
        return ResponseEntity.ok(PeoplePreferencesDto.fromFiltered(user, updated));
    }

    @PutMapping("/{username}/preferences/{preferenceName}")
    @Transactional
    @Operation(summary = "Upsert person preference", description = "Set or replace a single preference value. Key is validated for format/length.")
    public ResponseEntity<PeoplePreferencesDto> updatePersonPreference(
        @PathVariable String username,
        @PathVariable String preferenceName,
        @RequestBody PreferenceValueUpdateRequest request
    ) {
        Map<String, Object> updated = preferenceService.setPreference(
            username, preferenceName, request != null ? request.value() : null);
        User user = requireUser(username);
        return ResponseEntity.ok(PeoplePreferencesDto.fromFiltered(user, updated));
    }

    @DeleteMapping("/{username}/preferences/{preferenceName}")
    @Transactional
    @Operation(summary = "Delete person preference", description = "Remove a single preference entry for the current user or an admin-managed profile")
    public ResponseEntity<PeoplePreferencesDto> deletePersonPreference(
        @PathVariable String username,
        @PathVariable String preferenceName
    ) {
        Map<String, Object> updated = preferenceService.deletePreference(username, preferenceName);
        User user = requireUser(username);
        return ResponseEntity.ok(PeoplePreferencesDto.fromFiltered(user, updated));
    }

    @DeleteMapping("/{username}/preferences")
    @Transactional
    @Operation(summary = "Clear person preferences", description = "Remove every stored preference entry for the current user or an admin-managed profile")
    public ResponseEntity<PeoplePreferencesDto> clearPersonPreferences(@PathVariable String username) {
        preferenceService.clearAll(username);
        User user = requireUser(username);
        return ResponseEntity.ok(PeoplePreferencesDto.fromFiltered(user, Map.of()));
    }

    @GetMapping("/{username}/activities")
    @Operation(summary = "Get person activities", description = "Get a lightweight activity timeline for the user")
    public ResponseEntity<List<PersonActivityDto>> getPersonActivities(@PathVariable String username) {
        User user = requireUser(username);
        List<PersonActivityDto> activities = new ArrayList<>();

        if (user.getLastLoginDate() != null) {
            activities.add(PersonActivityDto.fromProfile(
                "last-login",
                "Login",
                "Last login recorded",
                user.getLastLoginDate(),
                Map.of("field", "lastLoginDate", "value", user.getLastLoginDate().toString())
            ));
        }

        if (user.getLastPasswordChangeDate() != null) {
            activities.add(PersonActivityDto.fromProfile(
                "password-change",
                "Security",
                "Password changed",
                user.getLastPasswordChangeDate(),
                Map.of("field", "lastPasswordChangeDate", "value", user.getLastPasswordChangeDate().toString())
            ));
        }

        loadRecentFavoriteActivities(username).forEach(activities::add);
        loadRecentCommentActivities(username).forEach(activities::add);
        loadRecentGroupActivities(user).forEach(activities::add);

        activities.sort(Comparator.comparing(PersonActivityDto::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        if (activities.size() > MAX_RECENT_ACTIVITIES) {
            activities = activities.subList(0, MAX_RECENT_ACTIVITIES);
        }
        return ResponseEntity.ok(activities);
    }

    @GetMapping("/{username}/sites")
    @Operation(summary = "Get person sites", description = "Map user groups to collaboration site-style summaries")
    public ResponseEntity<List<PersonSiteDto>> getPersonSites(@PathVariable String username) {
        User user = requireUser(username);
        List<PersonSiteDto> sites = user.getGroups().stream()
            .sorted(Comparator.comparing(Group::getName, String.CASE_INSENSITIVE_ORDER))
            .map(PersonSiteDto::fromGroup)
            .toList();
        return ResponseEntity.ok(sites);
    }

    @GetMapping("/{username}/favorite-sites")
    @Operation(summary = "Get person favorite sites", description = "Get favorite collaboration spaces derived from folder favorites")
    public ResponseEntity<List<PersonFavoriteSiteDto>> getPersonFavoriteSites(@PathVariable String username) {
        requireUser(username);
        List<PersonFavoriteSiteDto> sites = loadFavorites(username, PageRequest.of(0, MAX_RECENT_FAVORITES)).stream()
            .filter(favorite -> favorite.getNode() instanceof Folder)
            .map(PersonFavoriteSiteDto::fromFavorite)
            .toList();
        return ResponseEntity.ok(sites);
    }

    @GetMapping("/{username}/favorite-sites/{siteId}")
    @Operation(summary = "Get person favorite site", description = "Get a specific favorite site-style workspace relationship for a user")
    public ResponseEntity<PersonFavoriteSiteDto> getPersonFavoriteSite(
        @PathVariable String username,
        @PathVariable String siteId
    ) {
        requireUser(username);
        Favorite favorite = loadFavorite(username, siteId);
        return ResponseEntity.ok(PersonFavoriteSiteDto.fromFavorite(requireFolderFavorite(favorite, siteId)));
    }

    @PostMapping("/{username}/favorite-sites")
    @Transactional
    @Operation(summary = "Create person favorite site", description = "Create a favorite site-style workspace relationship for the current user or an admin-managed profile")
    public ResponseEntity<PersonFavoriteSiteDto> createPersonFavoriteSite(
        @PathVariable String username,
        @RequestBody PersonFavoriteSiteWriteRequest request
    ) {
        User user = requireWritableUser(username);
        UUID nodeId = parseRequiredUuid(request.nodeId(), "nodeId");
        Favorite favorite = favoriteService.addFavoriteSiteForUser(user.getUsername(), nodeId);
        return ResponseEntity.status(HttpStatus.CREATED).body(PersonFavoriteSiteDto.fromFavorite(requireFolderFavorite(favorite, nodeId.toString())));
    }

    @DeleteMapping("/{username}/favorite-sites/{siteId}")
    @Transactional
    @Operation(summary = "Delete person favorite site", description = "Delete a favorite site-style workspace relationship for the current user or an admin-managed profile")
    public ResponseEntity<Void> deletePersonFavoriteSite(
        @PathVariable String username,
        @PathVariable String siteId
    ) {
        User user = requireWritableUser(username);
        Favorite favorite = loadFavorite(user.getUsername(), siteId);
        requireFolderFavorite(favorite, siteId);
        favoriteService.removeFavoriteForUser(user.getUsername(), favorite.getNode().getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{username}/site-membership-requests")
    @Operation(summary = "Get person site membership requests", description = "Get pending collaboration requests from the user's preference payload")
    public ResponseEntity<List<PersonSiteMembershipRequestDto>> getPersonSiteMembershipRequests(@PathVariable String username) {
        User user = requireUser(username);
        Map<String, Object> preferences = user.getPreferences() == null ? new LinkedHashMap<>() : user.getPreferences();
        return ResponseEntity.ok(PersonSiteMembershipRequestDto.fromPreferenceValue(username, preferences.get(SITE_REQUESTS_KEY)));
    }

    @GetMapping("/site-membership-requests")
    @Operation(
        summary = "List visible site membership requests",
        description = "List site membership requests across users for admins and moderators"
    )
    public ResponseEntity<Page<PersonSiteMembershipRequestDto>> getVisibleSiteMembershipRequests(
        @RequestParam(required = false) String siteId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String requester,
        Pageable pageable
    ) {
        requireModerationAccess();
        List<PersonSiteMembershipRequestDto> visibleRequests = userRepository.findAll().stream()
            .flatMap(user -> PersonSiteMembershipRequestDto.fromPreferenceValue(
                user.getUsername(),
                user.getPreferences() != null ? user.getPreferences().get(SITE_REQUESTS_KEY) : null
            ).stream())
            .filter(request -> matchesVisibleSiteMembershipRequest(request, siteId, status, requester))
            .sorted(
                Comparator.comparing(PersonSiteMembershipRequestDto::requestedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(PersonSiteMembershipRequestDto::username, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(PersonSiteMembershipRequestDto::siteId, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            )
            .toList();
        return ResponseEntity.ok(pageVisibleSiteMembershipRequests(visibleRequests, pageable));
    }

    @PostMapping("/{username}/site-membership-requests")
    @Transactional
    @Operation(summary = "Create person site membership request", description = "Create a pending collaboration request in the current user's preference payload")
    public ResponseEntity<PersonSiteMembershipRequestDto> createPersonSiteMembershipRequest(
        @PathVariable String username,
        @RequestBody PersonSiteMembershipRequestWriteRequest request
    ) {
        User user = requireWritableUser(username);
        Map<String, Object> preferences = editablePreferences(user);
        List<Map<String, Object>> requests = mutableSiteMembershipRequests(preferences);
        String siteId = normalizeOptionalString(request.siteId());
        if (siteId == null) {
            throw new IllegalArgumentException("Site ID is required");
        }
        if (findSiteMembershipRequestIndex(requests, siteId) >= 0) {
            throw new IllegalArgumentException("Site membership request already exists: " + siteId);
        }

        Map<String, Object> storedRequest = buildSiteMembershipRequest(siteId, request, LocalDateTime.now(), "PENDING", null);
        requests.add(storedRequest);
        preferences.put(SITE_REQUESTS_KEY, requests);
        user.setPreferences(preferences);
        userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(PersonSiteMembershipRequestDto.fromPreferenceValue(username, List.of(storedRequest)).get(0));
    }

    @PutMapping("/{username}/site-membership-requests/{siteId}")
    @Transactional
    @Operation(summary = "Update person site membership request", description = "Update a pending collaboration request in the current user's preference payload")
    public ResponseEntity<PersonSiteMembershipRequestDto> updatePersonSiteMembershipRequest(
        @PathVariable String username,
        @PathVariable String siteId,
        @RequestBody PersonSiteMembershipRequestWriteRequest request
    ) {
        User user = requireWritableUser(username);
        Map<String, Object> preferences = editablePreferences(user);
        List<Map<String, Object>> requests = mutableSiteMembershipRequests(preferences);
        String normalizedSiteId = normalizeOptionalString(siteId);
        if (normalizedSiteId == null) {
            throw new IllegalArgumentException("Site ID is required");
        }
        String requestSiteId = normalizeOptionalString(request.siteId());
        if (requestSiteId != null && !Objects.equals(requestSiteId, normalizedSiteId)) {
            throw new IllegalArgumentException("Site ID in the request body must match the path parameter");
        }

        int requestIndex = findSiteMembershipRequestIndex(requests, normalizedSiteId);
        if (requestIndex < 0) {
            throw new ResourceNotFoundException("Site membership request not found: " + siteId);
        }

        Map<String, Object> current = requests.get(requestIndex);
        LocalDateTime requestedAt = parseDateTime(current.get("requestedAt"));
        String status = normalizeOptionalString(current.get("status"));
        Map<String, Object> updated = buildSiteMembershipRequest(
            normalizedSiteId,
            request,
            requestedAt,
            status != null ? status : "PENDING",
            current
        );
        requests.set(requestIndex, updated);
        preferences.put(SITE_REQUESTS_KEY, requests);
        user.setPreferences(preferences);
        userRepository.save(user);
        return ResponseEntity.ok(PersonSiteMembershipRequestDto.fromPreferenceValue(username, List.of(updated)).get(0));
    }

    @PostMapping("/{username}/site-membership-requests/{siteId}/approve")
    @Transactional
    @Operation(summary = "Approve person site membership request", description = "Approve a collaboration request and persist decision metadata")
    public ResponseEntity<PersonSiteMembershipRequestDto> approvePersonSiteMembershipRequest(
        @PathVariable String username,
        @PathVariable String siteId,
        @RequestBody(required = false) SiteMembershipRequestDecisionRequest request
    ) {
        return moderatePersonSiteMembershipRequest(username, siteId, "APPROVED", request);
    }

    @PostMapping("/{username}/site-membership-requests/{siteId}/reject")
    @Transactional
    @Operation(summary = "Reject person site membership request", description = "Reject a collaboration request and persist decision metadata")
    public ResponseEntity<PersonSiteMembershipRequestDto> rejectPersonSiteMembershipRequest(
        @PathVariable String username,
        @PathVariable String siteId,
        @RequestBody(required = false) SiteMembershipRequestDecisionRequest request
    ) {
        return moderatePersonSiteMembershipRequest(username, siteId, "REJECTED", request);
    }

    @DeleteMapping("/{username}/site-membership-requests/{siteId}")
    @Transactional
    @Operation(summary = "Withdraw person site membership request", description = "Remove a pending collaboration request from the current user's preference payload")
    public ResponseEntity<Void> withdrawPersonSiteMembershipRequest(
        @PathVariable String username,
        @PathVariable String siteId
    ) {
        User user = requireWritableUser(username);
        Map<String, Object> preferences = user.getPreferences() == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(user.getPreferences());
        Object rawRequests = preferences.get(SITE_REQUESTS_KEY);
        if (!(rawRequests instanceof List<?> rawList)) {
            throw new ResourceNotFoundException("Site membership request not found: " + siteId);
        }

        List<Map<String, Object>> remaining = new ArrayList<>();
        boolean removed = false;
        for (Object rawItem : rawList) {
            if (!(rawItem instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> request = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> request.put(String.valueOf(key), value));
            String requestSiteId = request.get("siteId") != null ? String.valueOf(request.get("siteId")) : null;
            if (!removed && Objects.equals(requestSiteId, siteId)) {
                removed = true;
                continue;
            }
            remaining.add(request);
        }

        if (!removed) {
            throw new ResourceNotFoundException("Site membership request not found: " + siteId);
        }

        preferences.put(SITE_REQUESTS_KEY, remaining);
        user.setPreferences(preferences);
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private User requireWritableUser(String username) {
        User user = requireUser(username);
        String currentUser = securityService.getCurrentUser();
        if (!Objects.equals(currentUser, username) && !securityService.isAdmin(currentUser)) {
            throw new AccessDeniedException("You are not allowed to update this profile");
        }
        return user;
    }

    private void requireModerationAccess() {
        String currentUser = securityService.getCurrentUser();
        if (!securityService.isAdmin(currentUser)) {
            throw new AccessDeniedException("You are not allowed to manage site membership requests");
        }
    }

    private String normalizeOptionalString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeOptionalString(Object value) {
        return value == null ? null : normalizeOptionalString(String.valueOf(value));
    }

    private UUID parseRequiredUuid(String value, String fieldName) {
        String normalizedValue = normalizeOptionalString(value);
        if (normalizedValue == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        try {
            return UUID.fromString(normalizedValue);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(fieldName + " must be a valid UUID");
        }
    }

    private Page<Favorite> loadFavorites(String username, Pageable pageable) {
        String currentUser = securityService.getCurrentUser();
        return Objects.equals(currentUser, username)
            ? favoriteService.getMyFavorites(pageable)
            : favoriteRepository.findByUserIdOrderByCreatedAtDesc(username, pageable);
    }

    private Favorite loadFavorite(String username, String nodeId) {
        return favoriteService.getFavoriteForUser(username, parseRequiredUuid(nodeId, "nodeId"));
    }

    private Favorite requireFolderFavorite(Favorite favorite, String siteId) {
        if (!(favorite.getNode() instanceof Folder)) {
            throw new ResourceNotFoundException("Favorite site not found: " + siteId);
        }
        return favorite;
    }

    private List<PersonActivityDto> loadRecentFavoriteActivities(String username) {
        return loadFavorites(username, PageRequest.of(0, MAX_RECENT_FAVORITES)).stream()
            .map(PersonActivityDto::fromFavorite)
            .toList();
    }

    private List<PersonActivityDto> loadRecentCommentActivities(String username) {
        return commentRepository.findByAuthorAndDeletedFalseOrderByCreatedDesc(username, PageRequest.of(0, MAX_RECENT_COMMENTS))
            .stream()
            .map(PersonActivityDto::fromComment)
            .toList();
    }

    private List<PersonActivityDto> loadRecentGroupActivities(User user) {
        return user.getGroups().stream()
            .filter(group -> group.getCreatedDate() != null)
            .sorted(Comparator.comparing(Group::getCreatedDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .map(PersonActivityDto::fromGroup)
            .toList();
    }

    private GroupDto toGroupDto(Group group) {
        return new GroupDto(
            group.getId() != null ? group.getId().toString() : null,
            group.getName(),
            group.getDisplayName(),
            group.getDescription(),
            group.getEmail(),
            group.isEnabled(),
            group.getGroupType() != null ? group.getGroupType().name() : null,
            null
        );
    }

    public record FavoriteDto(UUID id, UUID nodeId, String nodeName, String nodeType, LocalDateTime createdAt) {
        public static FavoriteDto from(Favorite favorite) {
            return new FavoriteDto(
                favorite.getId(),
                favorite.getNode().getId(),
                favorite.getNode().getName(),
                favorite.getNode().getNodeType().name(),
                favorite.getCreatedAt()
            );
        }
    }

    public record PersonFavoriteWriteRequest(String nodeId) {}

    public record PersonFavoriteSiteWriteRequest(String nodeId) {}

    public record PeoplePreferencesDto(
        String username,
        String displayName,
        String firstName,
        String lastName,
        String email,
        String phone,
        String department,
        String jobTitle,
        String avatarUrl,
        String locale,
        String timezone,
        boolean enabled,
        boolean locked,
        LocalDateTime lastLoginDate,
        LocalDateTime lastPasswordChangeDate,
        Long quotaSizeMb,
        Long usedSizeMb,
        Map<String, Object> preferences
    ) {
        static PeoplePreferencesDto from(User user) {
            Map<String, Object> preferences = user.getPreferences() == null
                ? Map.of()
                : new LinkedHashMap<>(user.getPreferences());
            return fromFiltered(user, preferences);
        }

        static PeoplePreferencesDto fromFiltered(User user, Map<String, Object> preferences) {
            return new PeoplePreferencesDto(
                user.getUsername(),
                user.getDisplayName(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.getDepartment(),
                user.getJobTitle(),
                user.getAvatarUrl(),
                user.getLocale(),
                user.getTimezone(),
                user.isEnabled(),
                user.isLocked(),
                user.getLastLoginDate(),
                user.getLastPasswordChangeDate(),
                user.getQuotaSizeMb(),
                user.getUsedSizeMb(),
                preferences
            );
        }
    }

    public record PeopleProfileUpdateRequest(
        String displayName,
        String firstName,
        String lastName,
        String phone,
        String department,
        String jobTitle,
        String avatarUrl,
        String locale,
        String timezone
    ) {}

    public record PreferenceEntryDto(String key, Object value) {}

    public record PeoplePreferencesUpdateRequest(Map<String, Object> preferences) {}

    public record PreferenceValueUpdateRequest(Object value) {}

    public record PersonSiteMembershipRequestWriteRequest(
        String siteId,
        String siteTitle,
        String role,
        String message
    ) {}

    public record SiteMembershipRequestDecisionRequest(String decisionComment) {}

    public record PersonActivityDto(
        String id,
        String type,
        String title,
        String summary,
        LocalDateTime occurredAt,
        String nodeId,
        String nodeName,
        String nodeType,
        Map<String, Object> metadata
    ) {
        static PersonActivityDto fromProfile(String id, String title, String summary, LocalDateTime occurredAt, Map<String, Object> metadata) {
            return new PersonActivityDto(id, "PROFILE", title, summary, occurredAt, null, null, null, metadata);
        }

        static PersonActivityDto fromFavorite(Favorite favorite) {
            String nodeType = favorite.getNode() != null ? favorite.getNode().getNodeType().name() : null;
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("favoriteId", favorite.getId());
            metadata.put("favoriteAt", favorite.getCreatedAt());
            return new PersonActivityDto(
                "favorite-" + favorite.getId(),
                "FAVORITE",
                favorite.getNode() != null ? favorite.getNode().getName() : "Favorite",
                "Added to favorites",
                favorite.getCreatedAt(),
                favorite.getNode() != null ? favorite.getNode().getId().toString() : null,
                favorite.getNode() != null ? favorite.getNode().getName() : null,
                nodeType,
                metadata
            );
        }

        static PersonActivityDto fromComment(Comment comment) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("commentId", comment.getId());
            metadata.put("mentions", comment.getMentionedUsers());
            return new PersonActivityDto(
                "comment-" + comment.getId(),
                "COMMENT",
                comment.getNode() != null ? comment.getNode().getName() : "Comment",
                "Commented on a document",
                comment.getCreated() != null ? comment.getCreated().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() : null,
                comment.getNode() != null && comment.getNode().getId() != null ? comment.getNode().getId().toString() : null,
                comment.getNode() != null ? comment.getNode().getName() : null,
                comment.getNode() != null ? comment.getNode().getNodeType().name() : null,
                metadata
            );
        }

        static PersonActivityDto fromGroup(Group group) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("groupName", group.getName());
            metadata.put("groupType", group.getGroupType() != null ? group.getGroupType().name() : null);
            return new PersonActivityDto(
                "group-" + group.getName(),
                "SITE_MEMBERSHIP",
                group.getDisplayName() != null ? group.getDisplayName() : group.getName(),
                "Member of collaboration group",
                group.getCreatedDate(),
                null,
                group.getDisplayName() != null ? group.getDisplayName() : group.getName(),
                "GROUP",
                metadata
            );
        }
    }

    public record PersonSiteDto(
        String siteId,
        String title,
        String description,
        String role,
        String visibility,
        Integer memberCount,
        LocalDateTime createdAt,
        LocalDateTime lastModifiedAt
    ) {
        static PersonSiteDto fromGroup(Group group) {
            return new PersonSiteDto(
                group.getName(),
                group.getDisplayName() != null ? group.getDisplayName() : group.getName(),
                group.getDescription(),
                group.getGroupType() != null ? group.getGroupType().name() : "CUSTOM",
                group.getGroupType() == Group.GroupType.SYSTEM ? "SYSTEM" : "PRIVATE",
                group.getUsers() != null ? group.getUsers().size() : 0,
                group.getCreatedDate(),
                group.getLastModifiedDate()
            );
        }
    }

    public record PersonFavoriteSiteDto(
        String siteId,
        String title,
        String description,
        String folderType,
        String nodeType,
        String nodeId,
        LocalDateTime favoritedAt,
        String path
    ) {
        static PersonFavoriteSiteDto fromFavorite(Favorite favorite) {
            Folder folder = (Folder) favorite.getNode();
            return new PersonFavoriteSiteDto(
                folder.getId().toString(),
                folder.getName(),
                folder.getDescription(),
                folder.getFolderType() != null ? folder.getFolderType().name() : null,
                folder.getNodeType().name(),
                folder.getId().toString(),
                favorite.getCreatedAt(),
                folder.getPath()
            );
        }
    }

    public record PersonSiteMembershipRequestDto(
        String username,
        String siteId,
        String siteTitle,
        String role,
        String status,
        String message,
        LocalDateTime requestedAt,
        String decisionBy,
        LocalDateTime decisionAt,
        String decisionComment,
        Map<String, Object> metadata
    ) {
        static List<PersonSiteMembershipRequestDto> fromPreferenceValue(Object rawValue) {
            return fromPreferenceValue(null, rawValue);
        }

        static List<PersonSiteMembershipRequestDto> fromPreferenceValue(String username, Object rawValue) {
            if (!(rawValue instanceof List<?> rawList)) {
                return List.of();
            }
            List<PersonSiteMembershipRequestDto> requests = new ArrayList<>();
            for (Object item : rawList) {
                if (item instanceof Map<?, ?> map) {
                    requests.add(fromMap(username, map));
                }
            }
            return requests;
        }

        private static PersonSiteMembershipRequestDto fromMap(String username, Map<?, ?> map) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            map.forEach((key, value) -> metadata.put(String.valueOf(key), value));
            return new PersonSiteMembershipRequestDto(
                username,
                stringValue(map.get("siteId")),
                stringValue(map.get("siteTitle")),
                stringValue(map.get("role")),
                stringValue(map.get("status")),
                stringValue(map.get("message")),
                parseDateTime(map.get("requestedAt")),
                stringValue(map.get("decisionBy")),
                parseDateTime(map.get("decisionAt")),
                stringValue(map.get("decisionComment")),
                metadata
            );
        }

        private static String stringValue(Object value) {
            return value != null ? String.valueOf(value) : null;
        }

        private static LocalDateTime parseDateTime(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof LocalDateTime dateTime) {
                return dateTime;
            }
            try {
                return LocalDateTime.parse(String.valueOf(value));
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private Map<String, Object> editablePreferences(User user) {
        return user.getPreferences() == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(user.getPreferences());
    }

    private List<Map<String, Object>> mutableSiteMembershipRequests(Map<String, Object> preferences) {
        Object rawRequests = preferences.get(SITE_REQUESTS_KEY);
        if (rawRequests == null) {
            return new ArrayList<>();
        }
        if (!(rawRequests instanceof List<?> rawList)) {
            throw new IllegalArgumentException("Site membership requests preference payload is invalid");
        }
        List<Map<String, Object>> requests = new ArrayList<>();
        for (Object rawItem : rawList) {
            if (rawItem instanceof Map<?, ?> rawMap) {
                Map<String, Object> request = new LinkedHashMap<>();
                rawMap.forEach((key, value) -> request.put(String.valueOf(key), value));
                requests.add(request);
            }
        }
        return requests;
    }

    private ResponseEntity<PersonSiteMembershipRequestDto> moderatePersonSiteMembershipRequest(
        String username,
        String siteId,
        String decisionStatus,
        SiteMembershipRequestDecisionRequest request
    ) {
        requireModerationAccess();
        User user = requireUser(username);
        Map<String, Object> preferences = editablePreferences(user);
        List<Map<String, Object>> requests = mutableSiteMembershipRequests(preferences);
        String normalizedSiteId = normalizeOptionalString(siteId);
        if (normalizedSiteId == null) {
            throw new IllegalArgumentException("Site ID is required");
        }

        int requestIndex = findSiteMembershipRequestIndex(requests, normalizedSiteId);
        if (requestIndex < 0) {
            throw new ResourceNotFoundException("Site membership request not found: " + siteId);
        }

        Map<String, Object> current = requests.get(requestIndex);
        LocalDateTime requestedAt = parseDateTime(current.get("requestedAt"));
        Map<String, Object> updated = buildSiteMembershipRequest(
            normalizedSiteId,
            toSiteMembershipRequestWriteRequest(current),
            requestedAt,
            decisionStatus,
            current
        );
        updated.put("status", decisionStatus);
        updated.put("decisionBy", securityService.getCurrentUser());
        updated.put("decisionAt", LocalDateTime.now());
        updated.put("decisionComment", normalizeOptionalString(request != null ? request.decisionComment() : null));

        requests.set(requestIndex, updated);
        preferences.put(SITE_REQUESTS_KEY, requests);
        user.setPreferences(preferences);
        userRepository.save(user);
        return ResponseEntity.ok(PersonSiteMembershipRequestDto.fromPreferenceValue(username, List.of(updated)).get(0));
    }

    private int findSiteMembershipRequestIndex(List<Map<String, Object>> requests, String siteId) {
        for (int i = 0; i < requests.size(); i++) {
            Map<String, Object> request = requests.get(i);
            if (Objects.equals(normalizeOptionalString(request.get("siteId")), siteId)) {
                return i;
            }
        }
        return -1;
    }

    private Map<String, Object> buildSiteMembershipRequest(
        String siteId,
        PersonSiteMembershipRequestWriteRequest request,
        LocalDateTime requestedAt,
        String status,
        Map<String, Object> inheritedRequest
    ) {
        Map<String, Object> storedRequest = new LinkedHashMap<>();
        storedRequest.put("siteId", siteId);
        storedRequest.put("siteTitle", normalizeOptionalString(request.siteTitle()) != null ? normalizeOptionalString(request.siteTitle()) : siteId);
        storedRequest.put("role", normalizeOptionalString(request.role()));
        storedRequest.put("status", normalizeOptionalString(status) != null ? normalizeOptionalString(status) : "PENDING");
        storedRequest.put("message", normalizeOptionalString(request.message()));
        storedRequest.put("requestedAt", requestedAt != null ? requestedAt : LocalDateTime.now());
        if (inheritedRequest != null) {
            if (inheritedRequest.containsKey("decisionBy")) {
                storedRequest.put("decisionBy", inheritedRequest.get("decisionBy"));
            }
            if (inheritedRequest.containsKey("decisionAt")) {
                storedRequest.put("decisionAt", inheritedRequest.get("decisionAt"));
            }
            if (inheritedRequest.containsKey("decisionComment")) {
                storedRequest.put("decisionComment", inheritedRequest.get("decisionComment"));
            }
        }
        return storedRequest;
    }

    private Page<PersonSiteMembershipRequestDto> pageVisibleSiteMembershipRequests(
        List<PersonSiteMembershipRequestDto> requests,
        Pageable pageable
    ) {
        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(requests);
        }
        int offset = Math.toIntExact(pageable.getOffset());
        int end = Math.min(offset + pageable.getPageSize(), requests.size());
        List<PersonSiteMembershipRequestDto> pageContent = offset >= requests.size()
            ? List.of()
            : requests.subList(offset, end);
        return new PageImpl<>(pageContent, pageable, requests.size());
    }

    private boolean matchesVisibleSiteMembershipRequest(
        PersonSiteMembershipRequestDto request,
        String siteId,
        String status,
        String requester
    ) {
        String normalizedSiteId = normalizeOptionalString(siteId);
        String normalizedStatus = normalizeOptionalString(status);
        String normalizedRequester = normalizeOptionalString(requester);
        return (normalizedSiteId == null || Objects.equals(normalizeOptionalString(request.siteId()), normalizedSiteId))
            && (normalizedStatus == null || Objects.equals(normalizeOptionalString(request.status()), normalizedStatus))
            && (normalizedRequester == null || Objects.equals(normalizeOptionalString(request.username()), normalizedRequester));
    }

    private PersonSiteMembershipRequestWriteRequest toSiteMembershipRequestWriteRequest(Map<String, Object> request) {
        return new PersonSiteMembershipRequestWriteRequest(
            normalizeOptionalString(request.get("siteId")),
            normalizeOptionalString(request.get("siteTitle")),
            normalizeOptionalString(request.get("role")),
            normalizeOptionalString(request.get("message"))
        );
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        try {
            return LocalDateTime.parse(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }
}
