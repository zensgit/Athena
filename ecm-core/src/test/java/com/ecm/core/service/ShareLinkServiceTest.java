package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.ShareLink;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.ShareLinkRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareLinkServiceTest {

    @Mock
    private ShareLinkRepository shareLinkRepository;

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private SecurityService securityService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ShareLinkService shareLinkService;

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

    private ShareLink buildShareLink(String allowedIps) {
        Document document = new Document();
        document.setName("doc");
        document.setPath("/doc");
        document.setMimeType("application/pdf");

        return ShareLink.builder()
            .token("token")
            .node(document)
            .createdBy("tester")
            .allowedIps(allowedIps)
            .build();
    }
}
