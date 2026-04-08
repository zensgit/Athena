package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.entity.ShareLink;
import com.ecm.core.entity.ShareLink.SharePermission;
import com.ecm.core.entity.ShareLinkAccessLog;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.ShareLinkAccessLogRepository;
import com.ecm.core.repository.ShareLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareLinkGovernanceTest {

    @Mock private ShareLinkRepository shareLinkRepo;
    @Mock private ShareLinkAccessLogRepository accessLogRepo;
    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;

    private ShareLinkService service;

    @BeforeEach
    void setUp() {
        lenient().when(tenantWorkspaceScopeService.isPathVisible("/doc")).thenReturn(true);
        service = new ShareLinkService(
            shareLinkRepo,
            accessLogRepo,
            nodeRepository,
            securityService,
            passwordEncoder,
            tenantWorkspaceScopeService
        );
    }

    // ================================================================= deactivate → reactivate cycle

    @Nested
    @DisplayName("deactivate → reactivate lifecycle")
    class LifecycleCycle {

        @Test
        @DisplayName("deactivateShareLink sets active=false")
        void deactivateSetsInactive() {
            ShareLink link = activeLink("tok1");
            Document doc = document();
            link.setNode(doc);

            when(shareLinkRepo.findByToken("tok1")).thenReturn(Optional.of(link));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(securityService.hasPermission(doc, PermissionType.CHANGE_PERMISSIONS)).thenReturn(true);
            when(shareLinkRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.deactivateShareLink("tok1");

            assertFalse(link.isActive());
        }

        @Test
        @DisplayName("reactivateShareLink sets active=true after deactivation")
        void reactivateSetsActive() {
            ShareLink link = activeLink("tok1");
            link.setActive(false);

            when(shareLinkRepo.findByToken("tok1")).thenReturn(Optional.of(link));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(shareLinkRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ShareLink result = service.reactivateShareLink("tok1");

            assertTrue(result.isActive());
        }

        @Test
        @DisplayName("admin can deactivate other user's link")
        void adminDeactivatesOtherLink() {
            ShareLink link = activeLink("tok1");
            link.setCreatedBy("bob");
            Document doc = document();
            link.setNode(doc);

            when(shareLinkRepo.findByToken("tok1")).thenReturn(Optional.of(link));
            when(securityService.getCurrentUser()).thenReturn("admin");
            when(securityService.hasPermission(doc, PermissionType.CHANGE_PERMISSIONS)).thenReturn(true);
            when(shareLinkRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.deactivateShareLink("tok1");

            assertFalse(link.isActive());
        }

        @Test
        @DisplayName("admin can reactivate other user's link")
        void adminReactivatesOtherLink() {
            ShareLink link = activeLink("tok1");
            link.setCreatedBy("bob");
            link.setActive(false);

            when(shareLinkRepo.findByToken("tok1")).thenReturn(Optional.of(link));
            when(securityService.getCurrentUser()).thenReturn("admin");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
            when(shareLinkRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ShareLink result = service.reactivateShareLink("tok1");

            assertTrue(result.isActive());
        }
    }

    // ================================================================= delete governance

    @Nested
    @DisplayName("delete governance")
    class DeleteGovernance {

        @Test
        @DisplayName("creator can delete own link")
        void creatorDeletes() {
            ShareLink link = activeLink("tok1");
            Document doc = document();
            link.setNode(doc);

            when(shareLinkRepo.findByToken("tok1")).thenReturn(Optional.of(link));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(securityService.hasPermission(doc, PermissionType.CHANGE_PERMISSIONS)).thenReturn(false);

            service.deleteShareLink("tok1");

            verify(shareLinkRepo).delete(link);
        }

        @Test
        @DisplayName("non-creator without permission cannot delete")
        void nonCreatorCannotDelete() {
            ShareLink link = activeLink("tok1");
            link.setCreatedBy("bob");
            Document doc = document();
            link.setNode(doc);

            when(shareLinkRepo.findByToken("tok1")).thenReturn(Optional.of(link));
            when(securityService.getCurrentUser()).thenReturn("eve");
            when(securityService.hasPermission(doc, PermissionType.CHANGE_PERMISSIONS)).thenReturn(false);

            assertThrows(SecurityException.class, () -> service.deleteShareLink("tok1"));
            verify(shareLinkRepo, never()).delete(any());
        }
    }

    // ================================================================= access log drill-down

    @Nested
    @DisplayName("access log drill-down")
    class AccessLogDrillDown {

        @Test
        @DisplayName("getAccessLog returns entries ordered by time desc")
        void returnsOrderedLog() {
            ShareLink link = activeLink("tok1");
            when(shareLinkRepo.findByToken("tok1")).thenReturn(Optional.of(link));
            when(securityService.getCurrentUser()).thenReturn("alice");

            ShareLinkAccessLog e1 = logEntry(link, true, null);
            ShareLinkAccessLog e2 = logEntry(link, false, "Expired");

            when(accessLogRepo.findByShareLinkIdOrderByAccessedAtDesc(link.getId()))
                .thenReturn(List.of(e2, e1));

            List<ShareLinkAccessLog> log = service.getAccessLog("tok1");

            assertEquals(2, log.size());
            assertFalse(log.get(0).isSuccess()); // most recent first
        }

        @Test
        @DisplayName("non-creator non-admin cannot view access log")
        void nonCreatorCannotViewLog() {
            ShareLink link = activeLink("tok1");
            link.setCreatedBy("bob");
            when(shareLinkRepo.findByToken("tok1")).thenReturn(Optional.of(link));
            when(securityService.getCurrentUser()).thenReturn("eve");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

            assertThrows(SecurityException.class, () -> service.getAccessLog("tok1"));
        }

        @Test
        @DisplayName("getAccessStats aggregates correctly")
        void statsAggregateCorrectly() {
            ShareLink link = activeLink("tok1");
            when(shareLinkRepo.findByToken("tok1")).thenReturn(Optional.of(link));
            when(accessLogRepo.countByShareLinkId(link.getId())).thenReturn(25L);
            when(accessLogRepo.countByShareLinkIdAndSuccessTrue(link.getId())).thenReturn(20L);
            when(accessLogRepo.countByShareLinkIdAndSuccessFalse(link.getId())).thenReturn(5L);

            ShareLinkService.ShareLinkAccessStats stats = service.getAccessStats("tok1");

            assertEquals(25L, stats.totalAccesses());
            assertEquals(20L, stats.successfulAccesses());
            assertEquals(5L, stats.failedAccesses());
        }
    }

    // ================================================================= filter verification (client-side, but test service list)

    @Nested
    @DisplayName("admin list for filtering")
    class AdminListFiltering {

        @Test
        @DisplayName("listAllShareLinks returns all links for admin")
        void returnsAllForAdmin() {
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
            ShareLink a = activeLink("a");
            ShareLink b = activeLink("b");
            b.setActive(false);
            when(shareLinkRepo.findAll()).thenReturn(List.of(a, b));

            List<ShareLink> all = service.listAllShareLinks();

            assertEquals(2, all.size());
        }
    }

    // ================================================================= helpers

    private ShareLink activeLink(String token) {
        ShareLink link = new ShareLink();
        link.setId(UUID.randomUUID());
        link.setToken(token);
        link.setActive(true);
        link.setCreatedBy("alice");
        link.setPermissionLevel(SharePermission.VIEW);
        link.setAccessCount(0);
        link.setNode(document());
        return link;
    }

    private Document document() {
        Document doc = new Document();
        doc.setId(UUID.randomUUID());
        doc.setName("report.pdf");
        doc.setMimeType("application/pdf");
        doc.setPath("/doc");
        return doc;
    }

    private ShareLinkAccessLog logEntry(ShareLink link, boolean success, String failureReason) {
        ShareLinkAccessLog entry = new ShareLinkAccessLog();
        entry.setId(UUID.randomUUID());
        entry.setShareLink(link);
        entry.setAccessedAt(LocalDateTime.now());
        entry.setClientIp("10.0.0.1");
        entry.setSuccess(success);
        entry.setFailureReason(failureReason);
        return entry;
    }
}
