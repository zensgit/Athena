package com.ecm.core.service;

import com.ecm.core.entity.Site;
import com.ecm.core.entity.SiteMember;
import com.ecm.core.entity.SiteMember.SiteMemberRole;
import com.ecm.core.entity.SiteMembershipRequest;
import com.ecm.core.entity.SiteMembershipRequest.RequestStatus;
import com.ecm.core.entity.User;
import com.ecm.core.exception.AccessDeniedException;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.repository.SiteMemberRepository;
import com.ecm.core.repository.SiteMembershipRequestRepository;
import com.ecm.core.repository.SiteRepository;
import com.ecm.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteMembershipServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private SiteMemberRepository siteMemberRepository;
    @Mock private SiteMembershipRequestRepository siteMembershipRequestRepository;
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
            siteMembershipRequestRepository,
            securityService,
            activityEventListener,
            tenantWorkspaceScopeService
        );
        lenient().when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse(anyString()))
            .thenAnswer(invocation -> Optional.of(site(invocation.getArgument(0))));
        lenient().when(siteMemberRepository.findByUsername(anyString())).thenReturn(List.of());
        lenient().when(siteMembershipRequestRepository.save(any(SiteMembershipRequest.class)))
            .thenAnswer(invocation -> {
                SiteMembershipRequest request = invocation.getArgument(0);
                if (request.getId() == null) {
                    request.setId(UUID.randomUUID());
                }
                return request;
            });
    }

    @Nested
    @DisplayName("createRequest")
    class CreateRequest {

        @Test
        @DisplayName("creates a persistent pending request")
        void createsPendingPersistentRequest() {
            Site site = site("finance");
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWith("alice", new HashMap<>())));
            when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));
            when(siteMembershipRequestRepository.findBySiteIdAndUsername(site.getId(), "alice")).thenReturn(Optional.empty());
            when(siteMemberRepository.findBySiteIdAndUsername(site.getId(), "alice")).thenReturn(Optional.empty());

            var result = service.createRequest("finance",
                new SiteMembershipService.CreateMembershipRequest("Finance", "CONTRIBUTOR", "Please add me"));

            assertEquals("finance", result.siteId());
            assertEquals("alice", result.username());
            assertEquals("PENDING", result.status());
            assertEquals("CONTRIBUTOR", result.role());
            verify(siteMembershipRequestRepository).save(any(SiteMembershipRequest.class));
            verify(activityEventListener).postMembershipActivity(
                eq("site.membership.requested"),
                eq("alice"),
                eq("finance"),
                any(Map.class)
            );
        }

        @Test
        @DisplayName("rejects duplicate request for same site")
        void rejectsDuplicatePersistentRequest() {
            Site site = site("finance");
            SiteMembershipRequest existing = request(site, "alice", RequestStatus.PENDING, "CONSUMER");
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWith("alice", new HashMap<>())));
            when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));
            when(siteMembershipRequestRepository.findBySiteIdAndUsername(site.getId(), "alice")).thenReturn(Optional.of(existing));
            when(siteMemberRepository.findBySiteIdAndUsername(site.getId(), "alice")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                () -> service.createRequest("finance",
                    new SiteMembershipService.CreateMembershipRequest("Finance", "CONSUMER", null)));
        }

        @Test
        @DisplayName("rejects request for site outside current tenant workspace")
        void rejectsForeignTenantSite() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWith("alice", new HashMap<>())));
            when(securityService.getCurrentUser()).thenReturn("alice");
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
        @DisplayName("approve sets status and creates membership")
        void approveSetsStatusAndCreatesMember() {
            Site site = site("hr");
            SiteMembershipRequest existing = request(site, "bob", RequestStatus.PENDING, "CONSUMER");

            when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("hr")).thenReturn(Optional.of(site));
            when(securityService.getCurrentUser()).thenReturn("admin");
            when(securityService.hasRole("ROLE_ADMIN", "admin")).thenReturn(true);
            when(siteMembershipRequestRepository.findBySiteIdAndUsername(site.getId(), "bob")).thenReturn(Optional.of(existing));
            when(siteMemberRepository.findBySiteIdAndUsername(site.getId(), "bob")).thenReturn(Optional.empty());
            when(siteMemberRepository.save(any(SiteMember.class))).thenAnswer(invocation -> {
                SiteMember member = invocation.getArgument(0);
                member.setId(UUID.randomUUID());
                return member;
            });

            var result = service.approve("hr", "bob", "Welcome aboard");

            assertEquals("APPROVED", result.status());
            assertEquals("admin", result.decisionBy());
            assertEquals("Welcome aboard", result.decisionComment());
            verify(siteMemberRepository).save(any(SiteMember.class));
            verify(activityEventListener).postMembershipActivity(
                eq("site.membership.approved"),
                eq("bob"),
                eq("hr"),
                any(Map.class)
            );
        }

        @Test
        @DisplayName("reject sets status to REJECTED")
        void rejectSetsStatus() {
            Site site = site("hr");
            SiteMembershipRequest existing = request(site, "bob", RequestStatus.PENDING, "CONSUMER");

            when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("hr")).thenReturn(Optional.of(site));
            when(securityService.getCurrentUser()).thenReturn("admin");
            when(securityService.hasRole("ROLE_ADMIN", "admin")).thenReturn(true);
            when(siteMembershipRequestRepository.findBySiteIdAndUsername(site.getId(), "bob")).thenReturn(Optional.of(existing));

            var result = service.reject("hr", "bob", "Not eligible");

            assertEquals("REJECTED", result.status());
            assertEquals("Not eligible", result.decisionComment());
            verify(activityEventListener).postMembershipActivity(
                eq("site.membership.rejected"),
                eq("bob"),
                eq("hr"),
                any(Map.class)
            );
        }

        @Test
        @DisplayName("non-admin non-manager cannot moderate")
        void nonModeratorRejected() {
            Site site = site("hr");
            when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("hr")).thenReturn(Optional.of(site));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(securityService.hasRole("ROLE_ADMIN", "alice")).thenReturn(false);
            when(siteMemberRepository.findByUsername("alice")).thenReturn(List.of());

            assertThrows(AccessDeniedException.class,
                () -> service.approve("hr", "bob", null));
        }
    }

    @Nested
    @DisplayName("withdraw")
    class Withdraw {

        @Test
        @DisplayName("marks request withdrawn instead of deleting it")
        void marksRequestWithdrawn() {
            Site site = site("finance");
            SiteMembershipRequest existing = request(site, "alice", RequestStatus.PENDING, "CONSUMER");
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWith("alice", new HashMap<>())));
            when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));
            when(siteMembershipRequestRepository.findBySiteIdAndUsername(site.getId(), "alice")).thenReturn(Optional.of(existing));

            service.withdraw("finance");

            assertEquals(RequestStatus.WITHDRAWN, existing.getStatus());
            verify(siteMembershipRequestRepository).save(existing);
            verify(activityEventListener).postMembershipActivity(
                eq("site.membership.withdrawn"),
                eq("alice"),
                eq("finance"),
                any(Map.class)
            );
        }

        @Test
        @DisplayName("throws when request not found")
        void throwsNotFound() {
            Site site = site("finance");
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));
            when(siteMembershipRequestRepository.findBySiteIdAndUsername(site.getId(), "alice")).thenReturn(Optional.empty());
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWith("alice", new HashMap<>())));

            assertThrows(NoSuchElementException.class,
                () -> service.withdraw("finance"));
        }
    }

    @Nested
    @DisplayName("queries")
    class Querying {

        @Test
        @DisplayName("returns persistent requests for a site")
        void returnsRequestsForSite() {
            Site site = site("finance");
            when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));
            when(securityService.getCurrentUser()).thenReturn("admin");
            when(securityService.hasRole("ROLE_ADMIN", "admin")).thenReturn(true);
            when(siteMembershipRequestRepository.findBySiteIdOrderByRequestedAtDesc(site.getId())).thenReturn(List.of(
                request(site, "alice", RequestStatus.PENDING, "CONTRIBUTOR"),
                request(site, "bob", RequestStatus.APPROVED, "CONSUMER")
            ));

            var result = service.getRequestsForSite("finance");

            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(r -> "finance".equals(r.siteId())));
        }

        @Test
        @DisplayName("getRequestsForUser filters requests outside current tenant workspace")
        void getRequestsForUserFiltersForeignTenantRequests() {
            Site finance = site("finance");
            Site hr = site("hr");
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWith("alice", new HashMap<>())));
            when(siteMembershipRequestRepository.findByUsernameOrderByRequestedAtDesc("alice")).thenReturn(List.of(
                request(finance, "alice", RequestStatus.PENDING, "CONTRIBUTOR"),
                request(hr, "alice", RequestStatus.PENDING, "CONSUMER")
            ));
            when(tenantWorkspaceScopeService.resolveCurrentTenantRootPath()).thenReturn("/Tenant Workspace");
            when(tenantWorkspaceScopeService.isSiteVisible("finance", "/Tenant Workspace")).thenReturn(true);
            when(tenantWorkspaceScopeService.isSiteVisible("hr", "/Tenant Workspace")).thenReturn(false);

            var result = service.getRequestsForUser("alice");

            assertEquals(1, result.size());
            assertEquals("finance", result.get(0).siteId());
        }

        @Test
        @DisplayName("legacy reader still surfaces preference-backed requests during compatibility window")
        void legacyReaderStillWorks() {
            User alice = userWith("alice", new HashMap<>(Map.of(
                "siteMembershipRequests", List.of(Map.of("siteId", "finance", "status", "PENDING", "role", "CONSUMER"))
            )));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(siteMembershipRequestRepository.findByUsernameOrderByRequestedAtDesc("alice")).thenReturn(List.of());

            var result = service.getRequestsForUser("alice");

            assertEquals(1, result.size());
            assertEquals("finance", result.get(0).siteId());
            assertEquals("PENDING", result.get(0).status());
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

    private SiteMembershipRequest request(Site site, String username, RequestStatus status, String role) {
        SiteMembershipRequest request = new SiteMembershipRequest();
        request.setId(UUID.randomUUID());
        request.setSite(site);
        request.setUsername(username);
        request.setSiteTitle(site.getTitle());
        request.setRequestedRole(role);
        request.setMessage("Need access");
        request.setStatus(status);
        request.setRequestedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        return request;
    }
}
