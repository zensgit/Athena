package com.ecm.core.integration.wopi.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WopiEditorServiceSecurityTest {

    @Mock private CollaboraDiscoveryService discoveryService;
    @Mock private NodeService nodeService;
    @Mock private SecurityService securityService;
    @Mock private WopiAccessTokenService accessTokenService;

    private WopiEditorService service;

    @BeforeEach
    void setUp() {
        service = new WopiEditorService(discoveryService, nodeService, securityService, accessTokenService);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "wopiHostUrl", "http://ecm-core:8080");
        ReflectionTestUtils.setField(service, "accessTokenTtlSeconds", 3600L);
    }

    @Test
    @DisplayName("read editor URL checks READ permission before issuing WOPI token")
    void readEditorUrlChecksReadPermission() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId);
        when(nodeService.getNode(documentId)).thenReturn(document);
        when(discoveryService.findUrlsrc("docx", "view")).thenReturn("https://office.example.com/browser");
        when(discoveryService.getPublicUrl()).thenReturn("https://office.example.com");
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(accessTokenService.issue(eq(documentId), eq("alice"), eq("alice"), eq(false), any(), any(Duration.class)))
            .thenReturn("opaque-read-token");

        service.generateEditorUrl(documentId, "read");

        verify(securityService).checkPermission(document, PermissionType.READ);
        verify(securityService, never()).checkPermission(document, PermissionType.WRITE);
        verify(accessTokenService).issue(eq(documentId), eq("alice"), eq("alice"), eq(false), any(), any(Duration.class));
    }

    @Test
    @DisplayName("write editor URL checks WRITE permission before issuing writable WOPI token")
    void writeEditorUrlChecksWritePermission() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId);
        when(nodeService.getNode(documentId)).thenReturn(document);
        when(discoveryService.findUrlsrc("docx", "edit")).thenReturn("https://office.example.com/browser");
        when(discoveryService.getPublicUrl()).thenReturn("https://office.example.com");
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(accessTokenService.issue(eq(documentId), eq("alice"), eq("alice"), eq(true), any(), any(Duration.class)))
            .thenReturn("opaque-write-token");

        service.generateEditorUrl(documentId, "write");

        verify(securityService).checkPermission(document, PermissionType.WRITE);
        verify(securityService, never()).checkPermission(document, PermissionType.READ);
        verify(accessTokenService).issue(eq(documentId), eq("alice"), eq("alice"), eq(true), any(), any(Duration.class));
    }

    @Test
    @DisplayName("permission denial stops editor URL generation before token issue")
    void permissionDenialStopsBeforeTokenIssue() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId);
        when(nodeService.getNode(documentId)).thenReturn(document);
        org.mockito.Mockito.doThrow(new SecurityException("No permission: WRITE on node: contract.docx"))
            .when(securityService).checkPermission(document, PermissionType.WRITE);

        assertThrows(SecurityException.class, () -> service.generateEditorUrl(documentId, "write"));

        verify(accessTokenService, never()).issue(
            any(UUID.class),
            anyString(),
            anyString(),
            anyBoolean(),
            any(),
            any(Duration.class)
        );
    }

    private Document document(UUID documentId) {
        Document document = new Document();
        document.setId(documentId);
        document.setName("contract.docx");
        document.setMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        return document;
    }
}
