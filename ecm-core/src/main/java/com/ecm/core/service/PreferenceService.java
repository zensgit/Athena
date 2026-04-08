package com.ecm.core.service;

import com.ecm.core.entity.User;
import com.ecm.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

/**
 * User preference service extracted from PeopleController.
 * Adds namespace filtering, key validation, and value size limits.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PreferenceService {

    private final UserRepository userRepository;
    private final SecurityService securityService;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;

    /** Keys must be dot-separated alphanumeric segments (e.g. "org.athena.ui.theme") */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9]([a-zA-Z0-9._-]*[a-zA-Z0-9])?$");
    private static final int MAX_KEY_LENGTH = 200;
    private static final int MAX_VALUE_JSON_SIZE = 10_000;
    private static final int MAX_PREFERENCE_COUNT = 500;

    // ------------------------------------------------------------------ read

    /**
     * Get all preferences for a user, optionally filtered by namespace prefix.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPreferences(String username, String filter) {
        User user = loadUser(username);
        Map<String, Object> all = user.getPreferences() != null
            ? new LinkedHashMap<>(user.getPreferences())
            : new LinkedHashMap<>();
        Map<String, Object> sanitized = sanitizePreferences(all);
        if (filter == null || filter.isBlank()) {
            return sanitized;
        }
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (var entry : sanitized.entrySet()) {
            if (entry.getKey().startsWith(filter)) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    /**
     * Export the full preference map for a user.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> exportPreferences(String username) {
        return getPreferences(username, null);
    }

    /**
     * Get a single preference value.
     */
    @Transactional(readOnly = true)
    public Object getPreference(String username, String key) {
        User user = loadUser(username);
        Map<String, Object> prefs = user.getPreferences();
        if (prefs == null || !prefs.containsKey(key)) {
            throw new NoSuchElementException("Preference not found: " + key);
        }
        return sanitizePreferenceEntry(key, prefs.get(key))
            .orElseThrow(() -> new NoSuchElementException("Preference not found: " + key));
    }

    /**
     * List distinct namespace prefixes (segments before first dot).
     */
    @Transactional(readOnly = true)
    public List<String> listNamespaces(String username) {
        Map<String, Object> prefs = getPreferences(username, null);
        if (prefs == null || prefs.isEmpty()) return List.of();
        Set<String> namespaces = new TreeSet<>();
        for (String key : prefs.keySet()) {
            int dot = key.indexOf('.');
            namespaces.add(dot > 0 ? key.substring(0, dot) : key);
        }
        return new ArrayList<>(namespaces);
    }

    // ------------------------------------------------------------------ write

    /**
     * Set a single preference. Validates key format and value size.
     */
    @Transactional
    public Map<String, Object> setPreference(String username, String key, Object value) {
        validateKey(key);
        validateValueSize(value);
        User user = loadWritableUser(username);
        Map<String, Object> prefs = user.getPreferences() != null
            ? new LinkedHashMap<>(user.getPreferences())
            : new LinkedHashMap<>();
        if (!prefs.containsKey(key) && prefs.size() >= MAX_PREFERENCE_COUNT) {
            throw new IllegalArgumentException("Maximum preference count (" + MAX_PREFERENCE_COUNT + ") reached");
        }
        prefs.put(key, value);
        user.setPreferences(prefs);
        userRepository.save(user);
        return prefs;
    }

    /**
     * Bulk replace all preferences. Validates every key and value.
     */
    @Transactional
    public Map<String, Object> replaceAll(String username, Map<String, Object> preferences) {
        if (preferences != null && preferences.size() > MAX_PREFERENCE_COUNT) {
            throw new IllegalArgumentException("Too many preferences (" + preferences.size() + "), max " + MAX_PREFERENCE_COUNT);
        }
        List<String> violations = new ArrayList<>();
        if (preferences != null) {
            for (var entry : preferences.entrySet()) {
                try { validateKey(entry.getKey()); } catch (IllegalArgumentException e) { violations.add(e.getMessage()); }
                try { validateValueSize(entry.getValue()); } catch (IllegalArgumentException e) { violations.add(e.getMessage()); }
            }
        }
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("Preference validation failed: " + String.join("; ", violations));
        }
        User user = loadWritableUser(username);
        Map<String, Object> nextPrefs = preferences != null
            ? new LinkedHashMap<>(preferences)
            : new LinkedHashMap<>();
        user.setPreferences(nextPrefs);
        userRepository.save(user);
        return nextPrefs;
    }

    /**
     * Import a preference map by replacing the current stored values.
     */
    @Transactional
    public Map<String, Object> importPreferences(String username, Map<String, Object> preferences) {
        return replaceAll(username, preferences);
    }

    /**
     * Delete a single preference.
     */
    @Transactional
    public Map<String, Object> deletePreference(String username, String key) {
        User user = loadWritableUser(username);
        Map<String, Object> prefs = user.getPreferences() != null
            ? new LinkedHashMap<>(user.getPreferences())
            : new LinkedHashMap<>();
        if (!prefs.containsKey(key)) {
            throw new NoSuchElementException("Preference not found: " + key);
        }
        prefs.remove(key);
        user.setPreferences(prefs);
        userRepository.save(user);
        return prefs;
    }

    /**
     * Clear all preferences.
     */
    @Transactional
    public void clearAll(String username) {
        User user = loadWritableUser(username);
        user.setPreferences(new LinkedHashMap<>());
        userRepository.save(user);
    }

    // ------------------------------------------------------------------ validation

    void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Preference key must not be blank");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Preference key too long (" + key.length() + " chars, max " + MAX_KEY_LENGTH + ")");
        }
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid preference key '" + key + "': must be alphanumeric with dots/dashes/underscores");
        }
    }

    void validateValueSize(Object value) {
        if (value == null) return;
        String json = value.toString();
        if (json.length() > MAX_VALUE_JSON_SIZE) {
            throw new IllegalArgumentException("Preference value too large (" + json.length() + " chars, max " + MAX_VALUE_JSON_SIZE + ")");
        }
    }

    // ------------------------------------------------------------------ helpers

    private User loadUser(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementException("User not found: " + username));
    }

    private User loadWritableUser(String username) {
        User user = loadUser(username);
        String currentUser = securityService.getCurrentUser();
        boolean isAdmin = securityService.hasRole("ROLE_ADMIN");
        if (!user.getUsername().equals(currentUser) && !isAdmin) {
            throw new SecurityException("Only the user or an admin can modify preferences");
        }
        return user;
    }

    private Map<String, Object> sanitizePreferences(Map<String, Object> preferences) {
        if (preferences == null || preferences.isEmpty() || !tenantWorkspaceScopeService.hasScopedTenantWorkspace()) {
            return preferences != null ? preferences : new LinkedHashMap<>();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (var entry : preferences.entrySet()) {
            sanitizePreferenceEntry(entry.getKey(), entry.getValue())
                .ifPresent(value -> sanitized.put(entry.getKey(), value));
        }
        return sanitized;
    }

    private Optional<Object> sanitizePreferenceEntry(String key, Object value) {
        if (!tenantWorkspaceScopeService.hasScopedTenantWorkspace()) {
            return Optional.ofNullable(value);
        }
        if (isSiteReferenceKey(key) && !isSiteVisible(value)) {
            return Optional.empty();
        }
        if (isNodeReferenceKey(key) && !isNodeVisible(value)) {
            return Optional.empty();
        }
        return sanitizeValue(value);
    }

    @SuppressWarnings("unchecked")
    private Optional<Object> sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = new LinkedHashMap<>();
            rawMap.forEach((k, v) -> map.put(String.valueOf(k), v));
            if (map.containsKey("siteId") && !isSiteVisible(map.get("siteId"))) {
                return Optional.empty();
            }
            if (containsHiddenNodeReference(map)) {
                return Optional.empty();
            }
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                sanitizePreferenceEntry(entry.getKey(), entry.getValue())
                    .ifPresent(next -> sanitized.put(entry.getKey(), next));
            }
            return Optional.of(sanitized);
        }
        if (value instanceof List<?> rawList) {
            List<Object> sanitized = new ArrayList<>();
            for (Object item : rawList) {
                sanitizeValue(item).ifPresent(sanitized::add);
            }
            return Optional.of(sanitized);
        }
        return Optional.ofNullable(value);
    }

    private boolean containsHiddenNodeReference(Map<String, Object> map) {
        for (String key : List.of("nodeId", "folderId", "rootFolderId", "rootNodeId", "targetFolderId", "workspaceId")) {
            if (map.containsKey(key) && !isNodeVisible(map.get(key))) {
                return true;
            }
        }
        return false;
    }

    private boolean isSiteReferenceKey(String key) {
        return key != null && (key.equals("siteId") || key.endsWith(".siteId"));
    }

    private boolean isNodeReferenceKey(String key) {
        return key != null && (
            key.equals("nodeId")
                || key.equals("folderId")
                || key.equals("rootFolderId")
                || key.equals("rootNodeId")
                || key.equals("targetFolderId")
                || key.equals("workspaceId")
                || key.endsWith(".nodeId")
                || key.endsWith(".folderId")
                || key.endsWith(".rootFolderId")
                || key.endsWith(".rootNodeId")
                || key.endsWith(".targetFolderId")
                || key.endsWith(".workspaceId")
        );
    }

    private boolean isSiteVisible(Object value) {
        if (value == null) {
            return false;
        }
        String siteId = value.toString().trim();
        return !siteId.isEmpty() && tenantWorkspaceScopeService.isSiteVisible(siteId);
    }

    private boolean isNodeVisible(Object value) {
        if (value == null) {
            return false;
        }
        try {
            return tenantWorkspaceScopeService.isNodeVisible(UUID.fromString(value.toString()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
