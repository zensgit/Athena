package com.ecm.core.service;

import com.ecm.core.entity.Node;
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
import org.mockito.ArgumentCaptor;
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
class ShareLinkEnhancementTest {

    @Mock private ShareLinkRepository shareLinkRepo;
    @Mock private ShareLinkAccessLogRepository accessLogRepo;
    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;
    @Mock private PasswordEncoder passwordEncoder;

    private ShareLinkService service;

    @BeforeEach
    void setUp() {
        service = new ShareLinkService(shareLinkRepo, accessLogRepo, nodeRepository, securityService, passwordEncoder);
    }

    // ================================================================= access logging

    @Nested
    @DisplayName("access audit logging")
    class AccessLogging {

        @Test
        @DisplayName("successful access records log entry with success=true")
        void successfulAccessRecordsLog() {
            ShareLink link = activeLink("abc123");
            when(shareLinkRepo.findByToken("abc123")).thenReturn(Optional.of(link));
            when(shareLinkRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(accessLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ShareLinkService.ShareLinkAccessResult result = service.accessShareLink("abc123", null, "192.168.1.1");

            assertTrue(result.success());
            ArgumentCaptor<ShareLinkAccessLog> logCaptor = ArgumentCaptor.forClass(ShareLinkAccessLog.class);
            verify(accessLogRepo).save(logCaptor.capture());
            ShareLinkAccessLog log = logCaptor.getValue();
            assertTrue(log.isSuccess());
            assertEquals("192.168.1.1", log.getClientIp());
            assertNull(log.getFailureReason());
        }

        @Test
        @DisplayName("expired link records log entry with success=false")
        void expiredLinkRecordsFailure() {
            ShareLink link = activeLink("abc123");
            link.setExpiryDate(LocalDateTime.now().minusDays(1));
            when(shareLinkRepo.findByToken("abc123")).thenReturn(Optional.of(link));
            when(accessLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ShareLinkService.ShareLinkAccessResult result = service.accessShareLink("abc123", null, "10.0.0.1");

            assertFalse(result.success());
            ArgumentCaptor<ShareLinkAccessLog> logCaptor = ArgumentCaptor.forClass(ShareLinkAccessLog.class);
            verify(accessLogRepo).save(logCaptor.capture());
            assertFalse(logCaptor.getValue().isSuccess());
            assertNotNull(logCaptor.getValue().getFailureReason());
        }

        @Test
        @DisplayName("wrong password records log entry with failure reason")
        void wrongPasswordRecordsFailure() {
            ShareLink link = activeLink("abc123");
            link.setPasswordHash("$2a$10$hashed");
            when(shareLinkRepo.findByToken("abc123")).thenReturn(Optional.of(link));
            when(passwordEncoder.matches("wrong", "$2a$10$hashed")).thenReturn(false);
            when(accessLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ShareLinkService.ShareLinkAccessResult result = service.accessShareLink("abc123", "wrong", "10.0.0.1");

            assertFalse(result.success());
            assertTrue(result.passwordRequired());
            verify(accessLogRepo).save(any());
        }

        @Test
        @DisplayName("IP restriction records log entry with failure reason")
        void ipRestrictionRecordsFailure() {
            ShareLink link = activeLink("abc123");
            link.setAllowedIps("192.168.1.0/24");
            when(shareLinkRepo.findByToken("abc123")).thenReturn(Optional.of(link));
            when(accessLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ShareLinkService.ShareLinkAccessResult result = service.accessShareLink("abc123", null, "10.0.0.1");

            assertFalse(result.success());
            ArgumentCaptor<ShareLinkAccessLog> logCaptor = ArgumentCaptor.forClass(ShareLinkAccessLog.class);
            verify(accessLogRepo).save(logCaptor.capture());
            assertEquals("IP restricted", logCaptor.getValue().getFailureReason());
        }
    }

    // ================================================================= reactivate

    @Nested
    @DisplayName("reactivate")
    class Reactivate {

        @Test
        @DisplayName("creator can reactivate deactivated link")
        void creatorReactivates() {
            ShareLink link = activeLink("abc123");
            link.setActive(false);
            when(shareLinkRepo.findByToken("abc123")).thenReturn(Optional.of(link));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(shareLinkRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ShareLink result = service.reactivateShareLink("abc123");

            assertTrue(result.isActive());
        }

        @Test
        @DisplayName("non-creator non-admin cannot reactivate")
        void nonCreatorRejected() {
            ShareLink link = activeLink("abc123");
            link.setActive(false);
            when(shareLinkRepo.findByToken("abc123")).thenReturn(Optional.of(link));
            when(securityService.getCurrentUser()).thenReturn("bob");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

            assertThrows(SecurityException.class,
                () -> service.reactivateShareLink("abc123"));
        }
    }

    // ================================================================= admin list

    @Nested
    @DisplayName("admin list all")
    class AdminListAll {

        @Test
        @DisplayName("admin can list all share links")
        void adminListsAll() {
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
            when(shareLinkRepo.findAll()).thenReturn(List.of(activeLink("a"), activeLink("b")));

            List<ShareLink> result = service.listAllShareLinks();

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("non-admin is rejected")
        void nonAdminRejected() {
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

            assertThrows(SecurityException.class, () -> service.listAllShareLinks());
        }
    }

    // ================================================================= access stats

    @Nested
    @DisplayName("access stats")
    class AccessStatsTest {

        @Test
        @DisplayName("returns correct counts")
        void returnsCorrectCounts() {
            ShareLink link = activeLink("abc123");
            when(shareLinkRepo.findByToken("abc123")).thenReturn(Optional.of(link));
            when(accessLogRepo.countByShareLinkId(link.getId())).thenReturn(10L);
            when(accessLogRepo.countByShareLinkIdAndSuccessTrue(link.getId())).thenReturn(8L);
            when(accessLogRepo.countByShareLinkIdAndSuccessFalse(link.getId())).thenReturn(2L);

            ShareLinkService.ShareLinkAccessStats stats = service.getAccessStats("abc123");

            assertEquals(10L, stats.totalAccesses());
            assertEquals(8L, stats.successfulAccesses());
            assertEquals(2L, stats.failedAccesses());
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
        return link;
    }
}
