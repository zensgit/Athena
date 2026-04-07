package com.ecm.core.service;

import com.ecm.core.entity.Site;
import com.ecm.core.entity.SiteMember;
import com.ecm.core.entity.SiteMember.SiteMemberRole;
import com.ecm.core.entity.User;
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

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SiteMemberRosterTest {

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
    }

    @Nested
    @DisplayName("getMembers")
    class GetMembers {

        @Test
        @DisplayName("returns all members for a site")
        void returnsMembers() {
            Site site = site("finance");
            SiteMember m1 = member(site, "alice", SiteMemberRole.MANAGER);
            SiteMember m2 = member(site, "bob", SiteMemberRole.CONSUMER);

            when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));
            when(siteMemberRepository.findBySiteIdOrderByRoleAscUsernameAsc(site.getId())).thenReturn(List.of(m1, m2));

            var result = service.getMembers("finance");

            assertEquals(2, result.size());
            assertEquals("alice", result.get(0).username());
            assertEquals("MANAGER", result.get(0).role());
        }
    }

    @Nested
    @DisplayName("addMember")
    class AddMember {

        @Test
        @DisplayName("adds member with specified role")
        void addsMember() {
            Site site = site("finance");
            User user = new User();
            user.setUsername("charlie");

            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
            when(securityService.getCurrentUser()).thenReturn("admin");
            when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));
            when(siteMemberRepository.findBySiteIdAndUsername(site.getId(), "charlie")).thenReturn(Optional.empty());
            when(userRepository.findByUsername("charlie")).thenReturn(Optional.of(user));
            when(siteMemberRepository.save(any())).thenAnswer(inv -> {
                SiteMember m = inv.getArgument(0);
                m.setId(UUID.randomUUID());
                return m;
            });

            var result = service.addMember("finance", "charlie", SiteMemberRole.CONTRIBUTOR);

            assertEquals("charlie", result.username());
            assertEquals("CONTRIBUTOR", result.role());
            assertEquals("finance", result.siteId());
            verify(activityEventListener).postSiteMemberActivity(
                eq("site.member.added"),
                eq("admin"),
                eq("finance"),
                eq("charlie"),
                eq("CONTRIBUTOR")
            );
        }

        @Test
        @DisplayName("rejects duplicate member")
        void rejectsDuplicate() {
            Site site = site("finance");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
            when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));
            when(siteMemberRepository.findBySiteIdAndUsername(site.getId(), "alice")).thenReturn(Optional.of(new SiteMember()));

            assertThrows(IllegalArgumentException.class,
                () -> service.addMember("finance", "alice", SiteMemberRole.CONSUMER));
        }

        @Test
        @DisplayName("non-admin cannot add members")
        void nonAdminRejected() {
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

            assertThrows(SecurityException.class,
                () -> service.addMember("finance", "alice", SiteMemberRole.CONSUMER));
        }
    }

    @Nested
    @DisplayName("updateMemberRole")
    class UpdateRole {

        @Test
        @DisplayName("updates member role")
        void updatesRole() {
            Site site = site("finance");
            SiteMember existing = member(site, "alice", SiteMemberRole.CONSUMER);

            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
            when(securityService.getCurrentUser()).thenReturn("admin");
            when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));
            when(siteMemberRepository.findBySiteIdAndUsername(site.getId(), "alice")).thenReturn(Optional.of(existing));
            when(siteMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = service.updateMemberRole("finance", "alice", SiteMemberRole.COLLABORATOR);

            assertEquals("COLLABORATOR", result.role());
            verify(activityEventListener).postSiteMemberActivity(
                eq("site.member.role_changed"),
                eq("admin"),
                eq("finance"),
                eq("alice"),
                eq("COLLABORATOR")
            );
        }

        @Test
        @DisplayName("throws when member not found")
        void throwsNotFound() {
            Site site = site("finance");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
            when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));
            when(siteMemberRepository.findBySiteIdAndUsername(site.getId(), "nobody")).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class,
                () -> service.updateMemberRole("finance", "nobody", SiteMemberRole.MANAGER));
        }
    }

    @Nested
    @DisplayName("removeMember")
    class RemoveMember {

        @Test
        @DisplayName("removes member by username")
        void removesMember() {
            Site site = site("finance");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
            when(securityService.getCurrentUser()).thenReturn("admin");
            when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));

            service.removeMember("finance", "alice");

            verify(siteMemberRepository).deleteBySiteIdAndUsername(site.getId(), "alice");
            verify(activityEventListener).postSiteMemberActivity(
                eq("site.member.removed"),
                eq("admin"),
                eq("finance"),
                eq("alice"),
                eq("REMOVED")
            );
        }
    }

    @Nested
    @DisplayName("getUserSites")
    class UserSites {

        @Test
        @DisplayName("returns sites a user belongs to")
        void returnsUserSites() {
            Site s1 = site("finance");
            Site s2 = site("hr");
            SiteMember m1 = member(s1, "alice", SiteMemberRole.MANAGER);
            SiteMember m2 = member(s2, "alice", SiteMemberRole.CONSUMER);

            when(siteMemberRepository.findByUsername("alice")).thenReturn(List.of(m1, m2));

            var result = service.getUserSites("alice");

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("filters user sites outside current tenant workspace")
        void filtersForeignTenantSites() {
            Site s1 = site("finance");
            Site s2 = site("hr");
            SiteMember m1 = member(s1, "alice", SiteMemberRole.MANAGER);
            SiteMember m2 = member(s2, "alice", SiteMemberRole.CONSUMER);

            when(siteMemberRepository.findByUsername("alice")).thenReturn(List.of(m1, m2));
            when(tenantWorkspaceScopeService.resolveCurrentTenantRootPath()).thenReturn("/Tenant Workspace");
            when(tenantWorkspaceScopeService.isSiteVisible("finance", "/Tenant Workspace")).thenReturn(true);
            when(tenantWorkspaceScopeService.isSiteVisible("hr", "/Tenant Workspace")).thenReturn(false);

            var result = service.getUserSites("alice");

            assertEquals(1, result.size());
            assertEquals("finance", result.get(0).siteId());
        }
    }

    // helpers
    private Site site(String siteId) {
        Site s = new Site();
        s.setId(UUID.randomUUID());
        s.setSiteId(siteId);
        s.setTitle(siteId.toUpperCase());
        s.setVisibility(Site.SiteVisibility.PUBLIC);
        s.setStatus(Site.SiteStatus.ACTIVE);
        return s;
    }

    private SiteMember member(Site site, String username, SiteMemberRole role) {
        SiteMember m = new SiteMember();
        m.setId(UUID.randomUUID());
        m.setSite(site);
        m.setUsername(username);
        m.setRole(role);
        return m;
    }
}
