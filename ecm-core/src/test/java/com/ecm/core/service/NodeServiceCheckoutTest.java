package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Permission;
import com.ecm.core.entity.CheckoutStatus;
import com.ecm.core.repository.CorrespondentRepository;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.PermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeServiceCheckoutTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private CorrespondentRepository correspondentRepository;
    @Mock private SecurityService securityService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ContentReferenceService contentReferenceService;

    private NodeService nodeService;

    @BeforeEach
    void setUp() {
        nodeService = new NodeService(
            nodeRepository,
            folderRepository,
            documentRepository,
            permissionRepository,
            correspondentRepository,
            securityService,
            eventPublisher,
            contentReferenceService
        );
    }

    @Test
    @DisplayName("Checkout persists checkout owner and timestamp")
    void checkoutDocumentPersistsState() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId, "contract.pdf");
        var version = new com.ecm.core.entity.Version();
        version.setId(UUID.randomUUID());
        document.setCurrentVersion(version);
        document.setVersionLabel("3.2");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(securityService.hasPermission(document, Permission.PermissionType.WRITE)).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(documentRepository.save(document)).thenReturn(document);

        Document updated = nodeService.checkoutDocument(documentId);

        assertEquals("alice", updated.getCheckoutUser());
        assertNotNull(updated.getCheckoutDate());
        assertEquals(version.getId().toString(), updated.getCheckoutBaselineVersionId());
        assertEquals("3.2", updated.getCheckoutBaselineVersionLabel());
        verify(documentRepository).save(document);
    }

    @Test
    @DisplayName("Checkout rejects document already checked out")
    void checkoutDocumentRejectsExistingCheckout() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId, "contract.pdf");
        document.checkout("bob");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(securityService.hasPermission(document, Permission.PermissionType.WRITE)).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> nodeService.checkoutDocument(documentId));
        assertEquals("Document is already checked out by: bob", ex.getMessage());
        verify(documentRepository, never()).save(document);
    }

    @Test
    @DisplayName("Checkin requires checkout owner or admin")
    void checkinDocumentRequiresOwnerOrAdmin() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId, "contract.pdf");
        document.checkout("bob");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

        SecurityException ex = assertThrows(SecurityException.class, () -> nodeService.checkinDocument(documentId));
        assertEquals("Only checkout owner or admin can check in document", ex.getMessage());
    }

    @Test
    @DisplayName("Keep checked out preserves checkout state for owner")
    void checkinKeepCheckedOutPreservesState() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId, "contract.pdf");
        document.checkout("alice");
        document.setVersionLabel("1.5");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);
        when(documentRepository.save(document)).thenReturn(document);

        Document updated = nodeService.checkinDocument(documentId, true);

        assertEquals("alice", updated.getCheckoutUser());
        assertNotNull(updated.getCheckoutDate());
        assertEquals("1.5", updated.getCheckoutBaselineVersionLabel());
        verify(documentRepository).save(document);
    }

    @Test
    @DisplayName("Keep checked out rejects admin takeover")
    void checkinKeepCheckedOutRejectsAdminTakeover() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId, "contract.pdf");
        document.checkout("bob");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);

        SecurityException ex = assertThrows(SecurityException.class, () -> nodeService.checkinDocument(documentId, true));
        assertEquals("Only checkout owner can keep document checked out", ex.getMessage());
        verify(documentRepository, never()).save(document);
    }

    @Test
    @DisplayName("Cancel checkout clears state for admin")
    void cancelCheckoutAllowsAdmin() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId, "contract.pdf");
        document.checkout("bob");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(documentRepository.save(document)).thenReturn(document);

        Document updated = nodeService.cancelCheckoutDocument(documentId);

        assertEquals(null, updated.getCheckoutUser());
        assertEquals(null, updated.getCheckoutDate());
        assertEquals(null, updated.getCheckoutBaselineVersionId());
        assertEquals(null, updated.getCheckoutBaselineVersionLabel());
        verify(documentRepository).save(document);
    }

    @Test
    @DisplayName("Checkout info reports available action for writable unlocked document")
    void checkoutInfoReportsAvailableDocument() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId, "contract.pdf");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(securityService.hasPermission(document, Permission.PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(document, Permission.PermissionType.WRITE)).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

        var info = nodeService.getCheckoutInfo(documentId);

        assertEquals(CheckoutStatus.AVAILABLE, info.status());
        assertTrue(info.canCheckout());
        assertFalse(info.canCheckIn());
        assertEquals(null, info.blockingReason());
    }

    @Test
    @DisplayName("Checkout info reports owner action affordances")
    void checkoutInfoReportsOwnerAffordances() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId, "contract.pdf");
        document.checkout("alice");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(securityService.hasPermission(document, Permission.PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(document, Permission.PermissionType.WRITE)).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

        var info = nodeService.getCheckoutInfo(documentId);

        assertEquals(CheckoutStatus.CHECKED_OUT_BY_YOU, info.status());
        assertTrue(info.canCheckIn());
        assertTrue(info.canCancelCheckout());
        assertTrue(info.canKeepCheckedOut());
        assertNotNull(info.checkoutAgeSeconds());
    }

    @Test
    @DisplayName("Checkout info reports foreign checkout and admin affordances")
    void checkoutInfoReportsForeignCheckout() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId, "contract.pdf");
        document.checkout("bob");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(securityService.hasPermission(document, Permission.PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(document, Permission.PermissionType.WRITE)).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);

        var info = nodeService.getCheckoutInfo(documentId);

        assertEquals(CheckoutStatus.CHECKED_OUT_BY_OTHER, info.status());
        assertEquals("bob", info.checkoutUser());
        assertTrue(info.canCheckIn());
        assertTrue(info.canCancelCheckout());
        assertFalse(info.canKeepCheckedOut());
        assertEquals("Checked out by bob.", info.blockingReason());
    }

    @Test
    @DisplayName("Checkout info blocks checkout behind foreign lock")
    void checkoutInfoReportsForeignLockBlocker() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId, "contract.pdf");
        document.applyLock("bob", java.time.LocalDateTime.now(), com.ecm.core.entity.LockLifetime.PERSISTENT, null);

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(securityService.hasPermission(document, Permission.PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(document, Permission.PermissionType.WRITE)).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

        var info = nodeService.getCheckoutInfo(documentId);

        assertEquals(CheckoutStatus.AVAILABLE, info.status());
        assertFalse(info.canCheckout());
        assertEquals("Cannot check out while locked by: bob [PERSISTENT].", info.blockingReason());
    }

    @Test
    @DisplayName("Checkin rejects documents that are not checked out")
    void checkinRejectsNonCheckedOut() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId, "contract.pdf");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> nodeService.checkinDocument(documentId));
        assertEquals("Document is not checked out", ex.getMessage());
    }

    @Test
    @DisplayName("Checkout rejects deleted or missing documents")
    void checkoutRejectsMissingOrDeletedDocument() {
        UUID missingId = UUID.randomUUID();
        when(documentRepository.findById(missingId)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> nodeService.checkoutDocument(missingId));

        UUID deletedId = UUID.randomUUID();
        Document deleted = document(deletedId, "deleted.pdf");
        deleted.setDeleted(true);
        when(documentRepository.findById(deletedId)).thenReturn(Optional.of(deleted));
        assertThrows(NoSuchElementException.class, () -> nodeService.checkoutDocument(deletedId));
    }

    private Document document(UUID id, String name) {
        Document document = new Document();
        document.setId(id);
        document.setName(name);
        document.setMimeType("application/pdf");
        document.setPath("/" + name);
        return document;
    }
}
