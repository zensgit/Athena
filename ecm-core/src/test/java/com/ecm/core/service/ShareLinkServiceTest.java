package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.entity.ShareLink;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.ShareLinkAccessLogRepository;
import com.ecm.core.repository.ShareLinkRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareLinkServiceTest {

    @Mock
    private ShareLinkRepository shareLinkRepository;

    @Mock
    private ShareLinkAccessLogRepository accessLogRepository;

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private SecurityService securityService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TenantWorkspaceScopeService tenantWorkspaceScopeService;

    @InjectMocks
    private ShareLinkService shareLinkService;

    @BeforeEach
    void setUp() {
        lenient().when(tenantWorkspaceScopeService.isPathVisible("/doc")).thenReturn(true);
    }

    @Test
    @DisplayName("Allows IPv4 addresses within CIDR range")
    void allowsIpv4WithinCidr() {
        ShareLink shareLink = buildShareLink("192.168.1.0/24");
        when(shareLinkRepository.findByToken("token")).thenReturn(Optional.of(shareLink));
        when(shareLinkRepository.save(shareLink)).thenReturn(shareLink);

        ShareLinkService.ShareLinkAccessResult result =
            shareLinkService.accessShareLink("token", null, "192.168.1.42");

        assertTrue(result.success());
        verify(shareLinkRepository).save(shareLink);
    }

    @Test
    @DisplayName("Denies IPv4 addresses outside CIDR range")
    void deniesIpv4OutsideCidr() {
        ShareLink shareLink = buildShareLink("192.168.1.0/24");
        when(shareLinkRepository.findByToken("token")).thenReturn(Optional.of(shareLink));

        ShareLinkService.ShareLinkAccessResult result =
            shareLinkService.accessShareLink("token", null, "192.168.2.1");

        assertFalse(result.success());
        assertEquals("Access from this IP is not allowed", result.error());
        verify(shareLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("Allows IPv6 addresses within CIDR range")
    void allowsIpv6WithinCidr() {
        ShareLink shareLink = buildShareLink("2001:db8::/32");
        when(shareLinkRepository.findByToken("token")).thenReturn(Optional.of(shareLink));
        when(shareLinkRepository.save(shareLink)).thenReturn(shareLink);

        ShareLinkService.ShareLinkAccessResult result =
            shareLinkService.accessShareLink("token", null, "2001:db8::1");

        assertTrue(result.success());
        verify(shareLinkRepository).save(shareLink);
    }

    @Test
    @DisplayName("Rejects invalid CIDR entries")
    void rejectsInvalidCidr() {
        ShareLink shareLink = buildShareLink("192.168.1.0/33");
        when(shareLinkRepository.findByToken("token")).thenReturn(Optional.of(shareLink));

        ShareLinkService.ShareLinkAccessResult result =
            shareLinkService.accessShareLink("token", null, "192.168.1.10");

        assertFalse(result.success());
        assertEquals("Access from this IP is not allowed", result.error());
    }

    @Test
    @DisplayName("Create share link rejects invalid allowed IP entries")
    void createShareLinkRejectsInvalidAllowedIps() {
        Document document = buildDocument();
        when(nodeRepository.findById(any(UUID.class))).thenReturn(Optional.of(document));
        when(securityService.hasPermission(document, PermissionType.READ)).thenReturn(true);

        ShareLinkService.CreateShareLinkRequest request = new ShareLinkService.CreateShareLinkRequest(
            "share",
            null,
            null,
            ShareLink.SharePermission.VIEW,
            null,
            "192.168.1.0/33"
        );

        assertThrows(IllegalArgumentException.class,
            () -> shareLinkService.createShareLink(UUID.randomUUID(), request));
        verify(shareLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("Update share link rejects invalid allowed IP entries")
    void updateShareLinkRejectsInvalidAllowedIps() {
        ShareLink shareLink = buildShareLink("192.168.1.0/24");
        when(shareLinkRepository.findByToken("token")).thenReturn(Optional.of(shareLink));
        when(securityService.getCurrentUser()).thenReturn("tester");
        when(securityService.hasPermission(shareLink.getNode(), PermissionType.CHANGE_PERMISSIONS))
            .thenReturn(true);

        ShareLinkService.UpdateShareLinkRequest request = new ShareLinkService.UpdateShareLinkRequest(
            null,
            null,
            null,
            null,
            null,
            "192.168.1.0/33",
            null
        );

        assertThrows(IllegalArgumentException.class,
            () -> shareLinkService.updateShareLink("token", request));
        verify(shareLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("Create share link normalizes allowed IP entries")
    void createShareLinkNormalizesAllowedIps() {
        Document document = buildDocument();
        when(nodeRepository.findById(any(UUID.class))).thenReturn(Optional.of(document));
        when(securityService.hasPermission(document, PermissionType.READ)).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("tester");
        when(tenantWorkspaceScopeService.isPathVisible("/doc")).thenReturn(true);
        when(shareLinkRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShareLinkService.CreateShareLinkRequest request = new ShareLinkService.CreateShareLinkRequest(
            "share",
            null,
            null,
            ShareLink.SharePermission.VIEW,
            null,
            " 10.0.0.1 , 10.0.0.2 , "
        );

        ShareLink shareLink = shareLinkService.createShareLink(UUID.randomUUID(), request);

        assertEquals("10.0.0.1,10.0.0.2", shareLink.getAllowedIps());
    }

    @Test
    @DisplayName("Create share link hides foreign-tenant node")
    void createShareLinkHidesForeignTenantNode() {
        Document document = buildDocument();
        UUID nodeId = UUID.randomUUID();
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(document));
        when(tenantWorkspaceScopeService.isPathVisible("/doc")).thenReturn(false);

        ShareLinkService.CreateShareLinkRequest request = new ShareLinkService.CreateShareLinkRequest(
            "share",
            null,
            null,
            ShareLink.SharePermission.VIEW,
            null,
            null
        );

        assertThrows(ResourceNotFoundException.class, () -> shareLinkService.createShareLink(nodeId, request));
    }

    @Test
    @DisplayName("Update share link clears allowed IPs on blank input")
    void updateShareLinkClearsAllowedIpsOnBlankInput() {
        ShareLink shareLink = buildShareLink("10.0.0.1/32");
        when(shareLinkRepository.findByToken("token")).thenReturn(Optional.of(shareLink));
        when(securityService.getCurrentUser()).thenReturn("tester");
        when(securityService.hasPermission(shareLink.getNode(), PermissionType.CHANGE_PERMISSIONS))
            .thenReturn(true);
        when(tenantWorkspaceScopeService.isPathVisible("/doc")).thenReturn(true);
        when(shareLinkRepository.save(shareLink)).thenReturn(shareLink);

        ShareLinkService.UpdateShareLinkRequest request = new ShareLinkService.UpdateShareLinkRequest(
            null,
            null,
            null,
            null,
            null,
            "   ",
            null
        );

        ShareLink updated = shareLinkService.updateShareLink("token", request);

        assertEquals(null, updated.getAllowedIps());
    }

    @Test
    @DisplayName("Get share link hides token outside current tenant workspace")
    void getShareLinkHidesForeignTenantToken() {
        ShareLink shareLink = buildShareLink("192.168.1.0/24");
        when(shareLinkRepository.findByToken("token")).thenReturn(Optional.of(shareLink));
        when(tenantWorkspaceScopeService.isPathVisible("/doc")).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> shareLinkService.getByToken("token"));
    }

    private Document buildDocument() {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("doc");
        document.setPath("/doc");
        document.setMimeType("application/pdf");
        return document;
    }

    private ShareLink buildShareLink(String allowedIps) {
        Document document = buildDocument();
        return ShareLink.builder()
            .token("token")
            .node(document)
            .createdBy("tester")
            .allowedIps(allowedIps)
            .build();
    }
}
