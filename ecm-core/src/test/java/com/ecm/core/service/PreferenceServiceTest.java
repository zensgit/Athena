package com.ecm.core.service;

import com.ecm.core.entity.User;
import com.ecm.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PreferenceServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private SecurityService securityService;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;

    private PreferenceService service;

    @BeforeEach
    void setUp() {
        service = new PreferenceService(userRepository, securityService, tenantWorkspaceScopeService);
    }

    // ================================================================= namespace filter

    @Nested
    @DisplayName("getPreferences with filter")
    class GetFiltered {

        @Test
        @DisplayName("returns all when filter is null")
        void returnsAllWhenNoFilter() {
            User user = userWith(Map.of("ui.theme", "dark", "org.athena.locale", "en"));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

            Map<String, Object> result = service.getPreferences("alice", null);

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("filters by prefix")
        void filtersByPrefix() {
            User user = userWith(Map.of("ui.theme", "dark", "ui.sidebar", "open", "org.athena.locale", "en"));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

            Map<String, Object> result = service.getPreferences("alice", "ui.");

            assertEquals(2, result.size());
            assertTrue(result.containsKey("ui.theme"));
            assertTrue(result.containsKey("ui.sidebar"));
            assertFalse(result.containsKey("org.athena.locale"));
        }

        @Test
        @DisplayName("returns empty map when no matches")
        void returnsEmptyWhenNoMatch() {
            User user = userWith(Map.of("ui.theme", "dark"));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

            Map<String, Object> result = service.getPreferences("alice", "org.");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("filters structured site preferences outside current tenant workspace")
        void filtersStructuredSitePreferencesOutsideCurrentTenantWorkspace() {
            User user = userWith(Map.of(
                "siteMembershipRequests", List.of(
                    Map.of("siteId", "finance", "status", "PENDING"),
                    Map.of("siteId", "legal", "status", "PENDING")
                )
            ));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
            when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
            when(tenantWorkspaceScopeService.isSiteVisible("finance")).thenReturn(true);
            when(tenantWorkspaceScopeService.isSiteVisible("legal")).thenReturn(false);

            Map<String, Object> result = service.getPreferences("alice", null);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> requests = (List<Map<String, Object>>) result.get("siteMembershipRequests");
            assertEquals(1, requests.size());
            assertEquals("finance", requests.get(0).get("siteId"));
        }
    }

    // ================================================================= namespaces

    @Nested
    @DisplayName("listNamespaces")
    class Namespaces {

        @Test
        @DisplayName("returns distinct top-level prefixes sorted")
        void returnsSortedNamespaces() {
            User user = userWith(Map.of(
                "ui.theme", "dark", "ui.sidebar", "open",
                "org.athena.locale", "en", "app.version", "1"
            ));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

            List<String> ns = service.listNamespaces("alice");

            assertEquals(List.of("app", "org", "ui"), ns);
        }

        @Test
        @DisplayName("returns key itself when no dot")
        void returnsKeyForNoDot() {
            User user = userWith(Map.of("theme", "dark"));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

            List<String> ns = service.listNamespaces("alice");

            assertEquals(List.of("theme"), ns);
        }
    }

    // ================================================================= key validation

    @Nested
    @DisplayName("key validation")
    class KeyValidation {

        @Test
        @DisplayName("accepts valid dotted key")
        void acceptsValid() {
            assertDoesNotThrow(() -> service.validateKey("org.athena.ui.theme"));
        }

        @Test
        @DisplayName("accepts alphanumeric with dashes and underscores")
        void acceptsDashUnderscore() {
            assertDoesNotThrow(() -> service.validateKey("my-app_v2.setting"));
        }

        @Test
        @DisplayName("rejects blank key")
        void rejectsBlank() {
            assertThrows(IllegalArgumentException.class, () -> service.validateKey(""));
        }

        @Test
        @DisplayName("rejects key with spaces")
        void rejectsSpaces() {
            assertThrows(IllegalArgumentException.class, () -> service.validateKey("has space"));
        }

        @Test
        @DisplayName("rejects key starting with dot")
        void rejectsLeadingDot() {
            assertThrows(IllegalArgumentException.class, () -> service.validateKey(".leading"));
        }

        @Test
        @DisplayName("rejects key exceeding max length")
        void rejectsLongKey() {
            String longKey = "a".repeat(201);
            assertThrows(IllegalArgumentException.class, () -> service.validateKey(longKey));
        }
    }

    // ================================================================= value validation

    @Nested
    @DisplayName("value validation")
    class ValueValidation {

        @Test
        @DisplayName("accepts null value")
        void acceptsNull() {
            assertDoesNotThrow(() -> service.validateValueSize(null));
        }

        @Test
        @DisplayName("rejects oversized value")
        void rejectsOversized() {
            String big = "x".repeat(10_001);
            assertThrows(IllegalArgumentException.class, () -> service.validateValueSize(big));
        }
    }

    // ================================================================= setPreference

    @Nested
    @DisplayName("setPreference")
    class SetPreference {

        @Test
        @DisplayName("creates new preference")
        void createsNew() {
            User user = userWith(new HashMap<>());
            stubWritable(user);

            Map<String, Object> result = service.setPreference("alice", "ui.theme", "dark");

            assertEquals("dark", result.get("ui.theme"));
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("updates existing preference")
        void updatesExisting() {
            User user = userWith(new HashMap<>(Map.of("ui.theme", "light")));
            stubWritable(user);

            Map<String, Object> result = service.setPreference("alice", "ui.theme", "dark");

            assertEquals("dark", result.get("ui.theme"));
        }

        @Test
        @DisplayName("rejects invalid key format")
        void rejectsInvalidKey() {
            assertThrows(IllegalArgumentException.class,
                () -> service.setPreference("alice", "has space", "value"));
            verify(userRepository, never()).save(any());
        }
    }

    // ================================================================= replaceAll

    @Nested
    @DisplayName("replaceAll")
    class ReplaceAll {

        @Test
        @DisplayName("replaces all preferences atomically")
        void replacesAll() {
            User user = userWith(new HashMap<>(Map.of("old.key", "old")));
            stubWritable(user);

            Map<String, Object> next = Map.of("new.key", "new");
            Map<String, Object> result = service.replaceAll("alice", next);

            assertEquals(1, result.size());
            assertEquals("new", result.get("new.key"));
            assertFalse(result.containsKey("old.key"));
        }

        @Test
        @DisplayName("rejects batch with invalid keys")
        void rejectsBatchInvalidKey() {
            Map<String, Object> bad = Map.of("valid.key", "ok", "has space", "bad");

            assertThrows(IllegalArgumentException.class,
                () -> service.replaceAll("alice", bad));
            verify(userRepository, never()).save(any());
        }
    }

    // ================================================================= export/import

    @Nested
    @DisplayName("exportPreferences/importPreferences")
    class ExportImport {

        @Test
        @DisplayName("exports the full stored preference map")
        void exportsFullMap() {
            User user = userWith(new HashMap<>(Map.of("ui.theme", "dark", "compactMode", true)));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

            Map<String, Object> result = service.exportPreferences("alice");

            assertEquals(2, result.size());
            assertEquals("dark", result.get("ui.theme"));
            assertEquals(true, result.get("compactMode"));
        }

        @Test
        @DisplayName("imports by replacing the existing map")
        void importsByReplacingMap() {
            User user = userWith(new HashMap<>(Map.of("old.key", "old")));
            stubWritable(user);

            Map<String, Object> next = Map.of("ui.theme", "dark");
            Map<String, Object> result = service.importPreferences("alice", next);

            assertEquals(1, result.size());
            assertEquals("dark", result.get("ui.theme"));
            assertFalse(result.containsKey("old.key"));
        }
    }

    @Nested
    @DisplayName("getPreference")
    class GetPreference {

        @Test
        @DisplayName("hides foreign-tenant node reference preference")
        void hidesForeignTenantNodeReference() {
            UUID foreignNodeId = UUID.randomUUID();
            User user = userWith(new HashMap<>(Map.of("workspace.rootNodeId", foreignNodeId.toString())));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
            when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
            when(tenantWorkspaceScopeService.isNodeVisible(foreignNodeId)).thenReturn(false);

            assertThrows(NoSuchElementException.class,
                () -> service.getPreference("alice", "workspace.rootNodeId"));
        }

        @Test
        @DisplayName("missing preference reads do not mark caller transaction rollback-only")
        void missingPreferenceReadDoesNotRollbackCallerTransaction() throws Exception {
            Transactional transactional = PreferenceService.class
                .getMethod("getPreference", String.class, String.class)
                .getAnnotation(Transactional.class);

            assertNotNull(transactional);
            assertTrue(List.of(transactional.noRollbackFor()).contains(NoSuchElementException.class));
        }
    }

    // ================================================================= permission

    @Nested
    @DisplayName("permission checks")
    class Permissions {

        @Test
        @DisplayName("rejects non-owner non-admin writes")
        void rejectsUnauthorized() {
            User user = userWith(new HashMap<>());
            user.setUsername("bob");
            when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
            when(securityService.getCurrentUser()).thenReturn("eve");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

            assertThrows(SecurityException.class,
                () -> service.setPreference("bob", "ui.theme", "dark"));
        }

        @Test
        @DisplayName("allows admin to modify other user's preferences")
        void allowsAdmin() {
            User user = userWith(new HashMap<>());
            user.setUsername("bob");
            when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
            when(securityService.getCurrentUser()).thenReturn("admin");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertDoesNotThrow(() -> service.setPreference("bob", "ui.theme", "dark"));
        }
    }

    // ================================================================= helpers

    private User userWith(Map<String, Object> prefs) {
        User user = new User();
        user.setUsername("alice");
        user.setPreferences(prefs);
        return user;
    }

    private void stubWritable(User user) {
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        when(securityService.getCurrentUser()).thenReturn(user.getUsername());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }
}
