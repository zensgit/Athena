package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.LockLifetime;
import com.ecm.core.entity.Version;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.VersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VersionServiceLockSemanticsTest {

    @Mock private VersionRepository versionRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private ContentService contentService;
    @Mock private SecurityService securityService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private VersionLabelService versionLabelService;

    private VersionService versionService;

    @BeforeEach
    void setUp() {
        versionService = new VersionService(
            versionRepository,
            documentRepository,
            contentService,
            securityService,
            eventPublisher,
            versionLabelService
        );
    }

    @Test
    @DisplayName("Create version rejects effective lock held by another user")
    void createVersionRejectsActiveForeignLock() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId, "contract.pdf");
        document.applyLock("bob", LocalDateTime.now().minusMinutes(2), LockLifetime.PERSISTENT, null);

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(securityService.hasPermission(document, com.ecm.core.entity.Permission.PermissionType.WRITE)).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("alice");

        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> versionService.createVersion(documentId, new ByteArrayInputStream("next".getBytes()), "contract.pdf", "updated", false)
        );

        assertEquals("Document is locked by: bob [PERSISTENT]", ex.getMessage());
        verify(contentService, never()).storeContent(any(), any());
    }

    @Test
    @DisplayName("Create version clears expired lock before storing new version")
    void createVersionClearsExpiredLock() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId, "contract.pdf");
        document.applyLock("bob", LocalDateTime.now().minusHours(1), LockLifetime.EPHEMERAL, LocalDateTime.now().minusMinutes(5));

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(securityService.hasPermission(document, com.ecm.core.entity.Permission.PermissionType.WRITE)).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(contentService.storeContent(any(), any())).thenReturn("content-1");
        when(contentService.detectMimeType("content-1", "contract.pdf")).thenReturn("application/pdf");
        when(contentService.getContentSize("content-1")).thenReturn(4L);
        when(contentService.extractMetadata("content-1")).thenReturn(Map.of("contentHash", "hash-1"));
        when(versionRepository.findMaxVersionNumber(documentId)).thenReturn(1);
        when(versionLabelService.generateLabel(document, 2)).thenReturn("1.1");
        when(versionRepository.save(any(Version.class))).thenAnswer(invocation -> invocation.getArgument(0));

        versionService.createVersion(documentId, new ByteArrayInputStream("next".getBytes()), "contract.pdf", "updated", false);

        assertFalse(document.isLocked());
        verify(documentRepository, atLeastOnce()).save(document);
    }

    private Document document(UUID id, String name) {
        Document document = new Document();
        document.setId(id);
        document.setName(name);
        document.setPath("/" + name);
        document.setMimeType("application/pdf");
        return document;
    }
}
