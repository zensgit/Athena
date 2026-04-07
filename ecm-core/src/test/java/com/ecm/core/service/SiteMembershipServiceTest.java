package com.ecm.core.service;

import com.ecm.core.entity.Site;
import com.ecm.core.entity.User;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.repository.SiteMemberRepository;
import com.ecm.core.repository.SiteRepository;
import com.ecm.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SiteMembershipServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private SiteMemberRepository siteMemberRepository;
    @Mock private SecurityService securityService;
    @Mock private ActivityEventListener activityEventListener;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;

    private SiteMembershipService service;

    @BeforeEach
    void setUp() {
        service = new SiteMembershipService(
            userRepository,
            siteRepository,
            siteMemberRepository,
            securityService,
            activityEventListener,
            tenantWorkspaceScopeService
        );
        lenient().when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse(anyString()))
            .thenAnswer(invocation -> Optional.of(site(invocation.getArgument(0))));
    }

    @Nested
    @DisplayName("createRequest")
    class CreateRequest {

        @Test
        @DisplayName("creates pending request in user preferences")
        void createsPending() {
            User user = userWith("alice", new HashMap<>());
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = service.createRequest("finance",
                new SiteMembershipService.CreateMembershipRequest("Finance", "CONTRIBUTOR", "Please add me"));

            assertEquals("finance", result.siteId());
            assertEquals("alice", result.username());
            assertEquals("PENDING", result.status());
            assertEquals("CONTRIBUTOR", result.role());
            verify(activityEventListener).postMembershipActivity(
                eq("site.membership.requested"),
                eq("alice"),
                eq("finance"),
                anyMap()
            );
        }

        @Test
        @DisplayName("rejects duplicate request for same site")
        void rejectsDuplicate() {
            User user = userWith("alice", new HashMap<>(Map.of(
                "siteMembershipRequests", List.of(Map.of("siteId", "finance", "status", "PENDING"))
            )));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

            assertThrows(IllegalArgumentException.class,
                () -> service.createRequest("finance",
                    new SiteMembershipService.CreateMembershipRequest("Finance", "CONSUMER", null)));
        }

        @Test
        @DisplayName("rejects request for site outside current tenant workspace")
        void rejectsForeignTenantSite() {
            when(tenantWorkspaceScopeService.resolveCurrentTenantRootPath()).thenReturn("/Tenant Workspace");
            when(tenantWorkspaceScopeService.isSiteVisible("finance", "/Tenant Workspace")).thenReturn(false);

            assertThrows(ResourceNotFoundException.class,
                () -> service.createRequest("finance",
                    new SiteMembershipService.CreateMembershipRequest("Finance", "CONSUMER", null)));
        }
    }

    @Nested
    @DisplayName("approve / reject")
    class Moderation {

        @Test
        @DisplayName("approve sets status to APPROVED with decision metadata")
        void approveSetsStatus() {
            User user = userWith("bob", new HashMap<>(Map.of(
                "siteMembershipRequests", new ArrayList<>(List.of(
                    new LinkedHashMap<>(Map.of("siteId", "hr", "status", "PENDING", "role", "CONSUMER"))
                ))
            )));
            when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
            when(securityService.getCurrentUser()).thenReturn("admin");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = service.approve("hr", "bob", "Welcome aboard");

            assertEquals("APPROVED", result.status());
            assertEquals("admin", result.decisionBy());
            assertEquals("Welcome aboard", result.decisionComment());
            verify(activityEventListener).postMembershipActivity(
                eq("site.membership.approved"),
                eq("bob"),
                eq("hr"),
                anyMap()
            );
        }

        @Test
        @DisplayName("reject sets status to REJECTED")
        void rejectSetsStatus() {
            User user = userWith("bob", new HashMap<>(Map.of(
                "siteMembershipRequests", new ArrayList<>(List.of(
                    new LinkedHashMap<>(Map.of("siteId", "hr", "status", "PENDING", "role", "CONSUMER"))
                ))
            )));
            when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
            when(securityService.getCurrentUser()).thenReturn("admin");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = service.reject("hr", "bob", "Not eligible");

            assertEquals("REJECTED", result.status());
            verify(activityEventListener).postMembershipActivity(
                eq("site.membership.rejected"),
                eq("bob"),
                eq("hr"),
                anyMap()
            );
        }

        @Test
        @DisplayName("non-admin cannot moderate")
        void nonAdminRejected() {
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

            assertThrows(SecurityException.class,
                () -> service.approve("hr", "bob", null));
        }
    }

    @Nested
    @DisplayName("withdraw")
    class Withdraw {

        @Test
        @DisplayName("removes request from preferences")
        void removesRequest() {
            User user = userWith("alice", new HashMap<>(Map.of(
                "siteMembershipRequests", new ArrayList<>(List.of(
                    new LinkedHashMap<>(Map.of("siteId", "finance", "status", "PENDING")),
                    new LinkedHashMap<>(Map.of("siteId", "hr", "status", "PENDING"))
                ))
            )));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.withdraw("finance");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> remaining = (List<Map<String, Object>>) user.getPreferences().get("siteMembershipRequests");
            assertEquals(1, remaining.size());
            assertEquals("hr", remaining.get(0).get("siteId"));
            verify(activityEventListener).postMembershipActivity(
                eq("site.membership.withdrawn"),
                eq("alice"),
                eq("finance"),
                anyMap()
            );
        }

        @Test
        @DisplayName("throws when request not found")
        void throwsNotFound() {
            User user = userWith("alice", new HashMap<>());
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

            assertThrows(NoSuchElementException.class,
                () -> service.withdraw("nonexistent"));
        }
    }

    @Nested
    @DisplayName("getRequestsForSite")
    class QueryBySite {

        @Test
        @DisplayName("returns requests matching siteId across users")
        void filtersAndAggregates() {
            User alice = userWith("alice", new HashMap<>(Map.of(
                "siteMembershipRequests", List.of(Map.of("siteId", "finance", "status", "PENDING"))
            )));
            User bob = userWith("bob", new HashMap<>(Map.of(
                "siteMembershipRequests", List.of(
                    Map.of("siteId", "finance", "status", "APPROVED"),
                    Map.of("siteId", "hr", "status", "PENDING")
                )
            )));
            when(userRepository.findAll()).thenReturn(List.of(alice, bob));

            var result = service.getRequestsForSite("finance");

            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(r -> "finance".equals(r.siteId())));
        }

        @Test
        @DisplayName("getRequestsForUser filters requests outside current tenant workspace")
        void getRequestsForUserFiltersForeignTenantRequests() {
            User alice = userWith("alice", new HashMap<>(Map.of(
                "siteMembershipRequests", List.of(
                    Map.of("siteId", "finance", "status", "PENDING"),
                    Map.of("siteId", "hr", "status", "PENDING")
                )
            )));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(tenantWorkspaceScopeService.resolveCurrentTenantRootPath()).thenReturn("/Tenant Workspace");
            when(tenantWorkspaceScopeService.isSiteVisible("finance", "/Tenant Workspace")).thenReturn(true);
            when(tenantWorkspaceScopeService.isSiteVisible("hr", "/Tenant Workspace")).thenReturn(false);

            var result = service.getRequestsForUser("alice");

            assertEquals(1, result.size());
            assertEquals("finance", result.get(0).siteId());
        }
    }

    private User userWith(String username, Map<String, Object> prefs) {
        User user = new User();
        user.setUsername(username);
        user.setPreferences(prefs);
        return user;
    }

    private Site site(String siteId) {
        Site site = new Site();
        site.setId(UUID.randomUUID());
        site.setSiteId(siteId);
        site.setTitle(siteId.toUpperCase());
        site.setVisibility(Site.SiteVisibility.PUBLIC);
        site.setStatus(Site.SiteStatus.ACTIVE);
        return site;
    }
}
