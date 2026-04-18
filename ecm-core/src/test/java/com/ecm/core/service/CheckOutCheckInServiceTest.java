package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.ContentReference.OwnerType;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckOutCheckInServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;
    @Mock private ContentReferenceService contentReferenceService;
    @Mock private ContentService contentService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private VersionService versionService;

    private CheckOutCheckInService service;

    @BeforeEach
    void setUp() {
        service = new CheckOutCheckInService(
            documentRepository,
            folderRepository,
            nodeRepository,
            securityService,
            tenantWorkspaceScopeService,
            contentReferenceService,
            contentService,
            eventPublisher
        );
        ReflectionTestUtils.setField(service, "versionService", versionService);
    }

    // ===================================================================== checkout

    @Nested
    @DisplayName("checkout()")
    class Checkout {

        @Test
        @DisplayName("creates persisted working copy in same folder")
        void createsWorkingCopyInSameFolder() {
            UUID docId = UUID.randomUUID();
            Folder parent = folder(UUID.randomUUID(), "/reports");
            Document original = document(docId, "report.docx", parent);
            original.setContentId("content-abc");
            original.setContentHash("hash-abc");
            original.setFileSize(5000L);

            when(documentRepository.findById(docId)).thenReturn(Optional.of(original));
            when(securityService.hasPermission(original, PermissionType.WRITE)).thenReturn(true);
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
                Document d = inv.getArgument(0);
                if (d.getId() == null) d.setId(UUID.randomUUID());
                return d;
            });

            Document wc = service.checkout(docId);

            assertTrue(wc.isWorkingCopy());
            assertEquals(docId, wc.getWorkingCopyOf());
            assertEquals("(Working Copy) report.docx", wc.getName());
            assertEquals("content-abc", wc.getContentId());
            assertEquals("hash-abc", wc.getContentHash());
            assertEquals(5000L, wc.getFileSize());
            assertEquals("alice", wc.getCheckoutUser());
            assertNotNull(wc.getCheckoutDate());
            assertFalse(wc.isVersioned());

            // original should be marked checked out
            ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
            verify(documentRepository, times(2)).save(captor.capture());
            Document savedOriginal = captor.getAllValues().stream()
                .filter(d -> d.getId().equals(docId))
                .findFirst().orElseThrow();
            assertEquals("alice", savedOriginal.getCheckoutUser());
        }

        @Test
        @DisplayName("creates working copy in specified destination folder")
        void createsWorkingCopyInDestination() {
            UUID docId = UUID.randomUUID();
            Folder parent = folder(UUID.randomUUID(), "/reports");
            Document original = document(docId, "report.docx", parent);

            UUID destId = UUID.randomUUID();
            Folder dest = folder(destId, "/drafts");

            when(documentRepository.findById(docId)).thenReturn(Optional.of(original));
            when(folderRepository.findById(destId)).thenReturn(Optional.of(dest));
            when(securityService.hasPermission(original, PermissionType.WRITE)).thenReturn(true);
            when(securityService.hasPermission(dest, PermissionType.CREATE_CHILDREN)).thenReturn(true);
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
                Document d = inv.getArgument(0);
                if (d.getId() == null) d.setId(UUID.randomUUID());
                return d;
            });

            Document wc = service.checkout(docId, destId);

            assertEquals("/drafts/(Working Copy) report.docx", wc.getPath());
        }

        @Test
        @DisplayName("rejects checkout of already checked-out document")
        void rejectsAlreadyCheckedOut() {
            UUID docId = UUID.randomUUID();
            Document original = document(docId, "report.docx", folder(UUID.randomUUID(), "/"));
            original.checkout("bob");

            when(documentRepository.findById(docId)).thenReturn(Optional.of(original));
            when(securityService.hasPermission(original, PermissionType.WRITE)).thenReturn(true);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.checkout(docId));
            assertTrue(ex.getMessage().contains("already checked out"));
        }

        @Test
        @DisplayName("rejects checkout of a working copy")
        void rejectsCheckoutOfWorkingCopy() {
            UUID wcId = UUID.randomUUID();
            Document wc = document(wcId, "(Working Copy) report.docx", folder(UUID.randomUUID(), "/"));
            wc.setWorkingCopy(true);
            wc.setWorkingCopyOf(UUID.randomUUID());

            when(documentRepository.findById(wcId)).thenReturn(Optional.of(wc));
            when(securityService.hasPermission(wc, PermissionType.WRITE)).thenReturn(true);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.checkout(wcId));
            assertEquals("Cannot check out a working copy", ex.getMessage());
        }

        @Test
        @DisplayName("rejects checkout without write permission")
        void rejectsWithoutPermission() {
            UUID docId = UUID.randomUUID();
            Document original = document(docId, "report.docx", folder(UUID.randomUUID(), "/"));

            when(documentRepository.findById(docId)).thenReturn(Optional.of(original));
            when(securityService.hasPermission(original, PermissionType.WRITE)).thenReturn(false);

            assertThrows(SecurityException.class, () -> service.checkout(docId));
        }

        @Test
        @DisplayName("rejects checkout of deleted document")
        void rejectsDeletedDocument() {
            UUID docId = UUID.randomUUID();
            Document original = document(docId, "report.docx", folder(UUID.randomUUID(), "/"));
            original.setDeleted(true);

            when(documentRepository.findById(docId)).thenReturn(Optional.of(original));

            assertThrows(NoSuchElementException.class, () -> service.checkout(docId));
        }

        @Test
        @DisplayName("rejects checkout of hidden tenant document")
        void rejectsHiddenTenantDocument() {
            UUID docId = UUID.randomUUID();
            Document original = document(docId, "report.docx", folder(UUID.randomUUID(), "/foreign"));

            when(documentRepository.findById(docId)).thenReturn(Optional.of(original));
            when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
            when(tenantWorkspaceScopeService.isPathVisible(original.getPath())).thenReturn(false);

            assertThrows(NoSuchElementException.class, () -> service.checkout(docId));
        }

        @Test
        @DisplayName("rejects checkout when locked by another user")
        void rejectsLockedByOther() {
            UUID docId = UUID.randomUUID();
            Document original = document(docId, "report.docx", folder(UUID.randomUUID(), "/"));
            original.applyLock("bob", java.time.LocalDateTime.now(),
                com.ecm.core.entity.LockLifetime.PERSISTENT, null);

            when(documentRepository.findById(docId)).thenReturn(Optional.of(original));
            when(securityService.hasPermission(original, PermissionType.WRITE)).thenReturn(true);
            when(securityService.getCurrentUser()).thenReturn("alice");

            assertThrows(IllegalStateException.class, () -> service.checkout(docId));
        }

        @Test
        @DisplayName("copies properties and metadata to working copy")
        void copiesPropertiesAndMetadata() {
            UUID docId = UUID.randomUUID();
            Folder parent = folder(UUID.randomUUID(), "/reports");
            Document original = document(docId, "report.docx", parent);
            original.setProperties(java.util.Map.of("dept", "legal", "priority", "high"));
            original.setMetadata(java.util.Map.of("extracted", true));

            when(documentRepository.findById(docId)).thenReturn(Optional.of(original));
            when(securityService.hasPermission(original, PermissionType.WRITE)).thenReturn(true);
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
                Document d = inv.getArgument(0);
                if (d.getId() == null) d.setId(UUID.randomUUID());
                return d;
            });

            Document wc = service.checkout(docId);

            assertEquals("legal", wc.getProperties().get("dept"));
            assertEquals(true, wc.getMetadata().get("extracted"));
        }

        @Test
        @DisplayName("rejects hidden tenant destination folder")
        void rejectsHiddenTenantDestinationFolder() {
            UUID docId = UUID.randomUUID();
            Folder parent = folder(UUID.randomUUID(), "/reports");
            Document original = document(docId, "report.docx", parent);
            UUID destId = UUID.randomUUID();
            Folder hiddenDestination = folder(destId, "/foreign");

            when(documentRepository.findById(docId)).thenReturn(Optional.of(original));
            when(folderRepository.findById(destId)).thenReturn(Optional.of(hiddenDestination));
            when(securityService.hasPermission(original, PermissionType.WRITE)).thenReturn(true);
            when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
            when(tenantWorkspaceScopeService.isPathVisible(original.getPath())).thenReturn(true);
            when(tenantWorkspaceScopeService.isPathVisible(hiddenDestination.getPath())).thenReturn(false);

            assertThrows(NoSuchElementException.class, () -> service.checkout(docId, destId));
        }
    }

    // ===================================================================== checkin

    @Nested
    @DisplayName("checkin()")
    class Checkin {

        @Test
        @DisplayName("soft-deletes working copy and clears original checkout state")
        void checkinClearsStateAndDeletesWc() throws Exception {
            UUID originalId = UUID.randomUUID();
            UUID wcId = UUID.randomUUID();

            Document original = document(originalId, "report.docx", folder(UUID.randomUUID(), "/"));
            original.checkout("alice");
            original.setContentId("old-content");

            Document wc = document(wcId, "(Working Copy) report.docx", folder(UUID.randomUUID(), "/"));
            wc.setWorkingCopy(true);
            wc.setWorkingCopyOf(originalId);
            wc.setCheckoutUser("alice");
            wc.setContentId("new-content");
            wc.setContentHash("new-hash");
            wc.setFileSize(6000L);

            when(documentRepository.findById(wcId)).thenReturn(Optional.of(wc));
            when(documentRepository.findById(originalId)).thenReturn(Optional.of(original));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
            when(contentService.getContent("new-content")).thenReturn(new ByteArrayInputStream("updated".getBytes()));
            doAnswer(inv -> {
                original.setContentId("new-content");
                original.setContentHash("new-hash");
                original.setFileSize(6000L);
                return null;
            }).when(versionService).createVersion(eq(originalId), any(), eq("report.docx"), eq("Check-in"), eq(false));

            Document result = service.checkin(wcId, false);

            // original should be returned with cleared checkout state
            assertEquals(originalId, result.getId());
            assertNull(result.getCheckoutUser());
            assertFalse(result.isCheckedOut());
            // content should be propagated
            assertEquals("new-content", result.getContentId());
            assertEquals("new-hash", result.getContentHash());
            assertEquals(6000L, result.getFileSize());
            // working copy should be soft-deleted
            assertTrue(wc.isDeleted());
            verify(versionService).createVersion(eq(originalId), any(), eq("report.docx"), eq("Check-in"), eq(false));
            verify(contentReferenceService).detach("new-content", OwnerType.WORKING_COPY, wcId);
        }

        @Test
        @DisplayName("rejects checkin of non-working-copy")
        void rejectsNonWorkingCopy() {
            UUID docId = UUID.randomUUID();
            Document doc = document(docId, "report.docx", folder(UUID.randomUUID(), "/"));

            when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

            assertThrows(IllegalArgumentException.class, () -> service.checkin(docId, false));
        }

        @Test
        @DisplayName("rejects checkin by non-owner non-admin")
        void rejectsNonOwner() {
            UUID wcId = UUID.randomUUID();
            Document wc = document(wcId, "(Working Copy) report.docx", folder(UUID.randomUUID(), "/"));
            wc.setWorkingCopy(true);
            wc.setWorkingCopyOf(UUID.randomUUID());
            wc.setCheckoutUser("bob");

            when(documentRepository.findById(wcId)).thenReturn(Optional.of(wc));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

            assertThrows(SecurityException.class, () -> service.checkin(wcId, false));
        }

        @Test
        @DisplayName("admin can check in another user's working copy")
        void adminCanCheckinForeignWc() {
            UUID originalId = UUID.randomUUID();
            UUID wcId = UUID.randomUUID();

            Document original = document(originalId, "report.docx", folder(UUID.randomUUID(), "/"));
            original.checkout("bob");

            Document wc = document(wcId, "(Working Copy) report.docx", folder(UUID.randomUUID(), "/"));
            wc.setWorkingCopy(true);
            wc.setWorkingCopyOf(originalId);
            wc.setCheckoutUser("bob");

            when(documentRepository.findById(wcId)).thenReturn(Optional.of(wc));
            when(documentRepository.findById(originalId)).thenReturn(Optional.of(original));
            when(securityService.getCurrentUser()).thenReturn("admin");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

            Document result = service.checkin(wcId, false);
            assertNull(result.getCheckoutUser());
        }

        @Test
        @DisplayName("keepCheckedOut rejects admin taking over ownership")
        void keepCheckedOutRejectsAdminTakeover() {
            UUID wcId = UUID.randomUUID();
            Document wc = document(wcId, "(Working Copy) report.docx", folder(UUID.randomUUID(), "/"));
            wc.setWorkingCopy(true);
            wc.setWorkingCopyOf(UUID.randomUUID());
            wc.setCheckoutUser("bob");

            when(documentRepository.findById(wcId)).thenReturn(Optional.of(wc));
            when(securityService.getCurrentUser()).thenReturn("admin");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);

            assertThrows(SecurityException.class, () -> service.checkin(wcId, true));
        }

        @Test
        @DisplayName("does not create version when content and metadata are unchanged")
        void skipsContentPropagationWhenUnchanged() throws Exception {
            UUID originalId = UUID.randomUUID();
            UUID wcId = UUID.randomUUID();

            Document original = document(originalId, "report.docx", folder(UUID.randomUUID(), "/"));
            original.checkout("alice");
            original.setContentId("same-content");
            original.setProperties(Map.of("dept", "legal"));

            Document wc = document(wcId, "(Working Copy) report.docx", folder(UUID.randomUUID(), "/"));
            wc.setWorkingCopy(true);
            wc.setWorkingCopyOf(originalId);
            wc.setCheckoutUser("alice");
            wc.setContentId("same-content");
            wc.setContentHash("same-hash");
            wc.setProperties(Map.of("dept", "legal"));

            when(documentRepository.findById(wcId)).thenReturn(Optional.of(wc));
            when(documentRepository.findById(originalId)).thenReturn(Optional.of(original));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

            Document result = service.checkin(wcId, false);
            assertEquals("same-content", result.getContentId());
            verify(versionService, never()).createVersion(any(), any(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("creates version and persists properties when only metadata changed")
        void createsVersionAndPersistsPropertiesWhenMetadataChanged() throws Exception {
            UUID originalId = UUID.randomUUID();
            UUID wcId = UUID.randomUUID();

            Document original = document(originalId, "report.docx", folder(UUID.randomUUID(), "/"));
            original.checkout("alice");
            original.setContentId("same-content");
            original.setProperties(Map.of("dept", "legal"));

            Document wc = document(wcId, "(Working Copy) report.docx", folder(UUID.randomUUID(), "/"));
            wc.setWorkingCopy(true);
            wc.setWorkingCopyOf(originalId);
            wc.setCheckoutUser("alice");
            wc.setContentId("same-content");
            wc.setProperties(Map.of("dept", "finance"));

            when(documentRepository.findById(wcId)).thenReturn(Optional.of(wc));
            when(documentRepository.findById(originalId)).thenReturn(Optional.of(original));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
            when(contentService.getContent("same-content")).thenReturn(new ByteArrayInputStream("same".getBytes()));

            Document result = service.checkin(wcId, false, "metadata update", false);

            assertEquals("finance", result.getProperties().get("dept"));
            verify(versionService).createVersion(eq(originalId), any(), eq("report.docx"), eq("metadata update"), eq(false));
        }

        @Test
        @DisplayName("uploaded file is persisted on working copy before version creation")
        void uploadedFileUpdatesWorkingCopyBeforeVersionCreation() throws Exception {
            UUID originalId = UUID.randomUUID();
            UUID wcId = UUID.randomUUID();

            Document original = document(originalId, "report.docx", folder(UUID.randomUUID(), "/"));
            original.checkout("alice");
            original.setContentId("old-content");

            Document wc = document(wcId, "(Working Copy) report.docx", folder(UUID.randomUUID(), "/"));
            wc.setWorkingCopy(true);
            wc.setWorkingCopyOf(originalId);
            wc.setCheckoutUser("alice");
            wc.setContentId("old-content");

            MockMultipartFile file = new MockMultipartFile(
                "file",
                "report-v2.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "updated".getBytes()
            );

            when(documentRepository.findById(wcId)).thenReturn(Optional.of(wc));
            when(documentRepository.findById(originalId)).thenReturn(Optional.of(original));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
            when(contentService.storeContent(file)).thenReturn("uploaded-content");
            when(contentService.detectMimeType("uploaded-content", "report-v2.docx"))
                .thenReturn("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            when(contentService.getContentSize("uploaded-content")).thenReturn(7L);
            when(contentService.extractMetadata("uploaded-content")).thenReturn(Map.of("textContent", "updated"));
            when(contentService.getContent("uploaded-content")).thenReturn(new ByteArrayInputStream("updated".getBytes()));
            doAnswer(inv -> {
                original.setContentId("uploaded-content");
                original.setFileSize(7L);
                original.setMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                return null;
            }).when(versionService).createVersion(eq(originalId), any(), eq("report-v2.docx"), eq("check-in upload"), eq(true));

            Document result = service.checkin(wcId, false, "check-in upload", true, file);

            assertEquals("uploaded-content", wc.getContentId());
            assertEquals("uploaded-content", result.getContentId());
            verify(contentReferenceService).syncOwnerReference("old-content", "uploaded-content", OwnerType.WORKING_COPY, wcId);
            verify(versionService).createVersion(eq(originalId), any(), eq("report-v2.docx"), eq("check-in upload"), eq(true));
        }
    }

    @Test
    @DisplayName("getCheckedOutWorkingCopies filters hidden tenant working copies")
    void getCheckedOutWorkingCopiesFiltersHiddenTenantWorkingCopies() {
        Document visible = document(UUID.randomUUID(), "visible.docx", folder(UUID.randomUUID(), "/tenant-a"));
        visible.setWorkingCopy(true);
        Document hidden = document(UUID.randomUUID(), "hidden.docx", folder(UUID.randomUUID(), "/tenant-b"));
        hidden.setWorkingCopy(true);

        when(documentRepository.findWorkingCopiesByUser("alice")).thenReturn(java.util.List.of(visible, hidden));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
        when(tenantWorkspaceScopeService.isPathVisible(visible.getPath())).thenReturn(true);
        when(tenantWorkspaceScopeService.isPathVisible(hidden.getPath())).thenReturn(false);

        assertEquals(1, service.getCheckedOutWorkingCopies("alice").size());
        assertEquals(visible.getId(), service.getCheckedOutWorkingCopies("alice").get(0).getId());
    }

    // ===================================================================== cancel

    @Nested
    @DisplayName("cancelCheckout()")
    class CancelCheckout {

        @Test
        @DisplayName("accepts working copy ID and deletes it")
        void cancelViaWorkingCopyId() {
            UUID originalId = UUID.randomUUID();
            UUID wcId = UUID.randomUUID();

            Document original = document(originalId, "report.docx", folder(UUID.randomUUID(), "/"));
            original.checkout("alice");

            Document wc = document(wcId, "(Working Copy) report.docx", folder(UUID.randomUUID(), "/"));
            wc.setWorkingCopy(true);
            wc.setWorkingCopyOf(originalId);
            wc.setCheckoutUser("alice");

            when(documentRepository.findById(wcId)).thenReturn(Optional.of(wc));
            when(documentRepository.findById(originalId)).thenReturn(Optional.of(original));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

            Document result = service.cancelCheckout(wcId);

            assertEquals(originalId, result.getId());
            assertNull(result.getCheckoutUser());
            assertTrue(wc.isDeleted());
        }

        @Test
        @DisplayName("accepts original document ID and finds working copy to delete")
        void cancelViaOriginalId() {
            UUID originalId = UUID.randomUUID();
            UUID wcId = UUID.randomUUID();

            Document original = document(originalId, "report.docx", folder(UUID.randomUUID(), "/"));
            original.checkout("alice");

            Document wc = document(wcId, "(Working Copy) report.docx", folder(UUID.randomUUID(), "/"));
            wc.setWorkingCopy(true);
            wc.setWorkingCopyOf(originalId);
            wc.setCheckoutUser("alice");

            when(documentRepository.findById(originalId)).thenReturn(Optional.of(original));
            when(documentRepository.findWorkingCopyOf(originalId)).thenReturn(Optional.of(wc));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

            Document result = service.cancelCheckout(originalId);

            assertEquals(originalId, result.getId());
            assertNull(result.getCheckoutUser());
            assertTrue(wc.isDeleted());
        }

        @Test
        @DisplayName("rejects cancel by non-owner non-admin")
        void rejectsNonOwner() {
            UUID originalId = UUID.randomUUID();
            Document original = document(originalId, "report.docx", folder(UUID.randomUUID(), "/"));
            original.checkout("bob");

            when(documentRepository.findById(originalId)).thenReturn(Optional.of(original));
            when(documentRepository.findWorkingCopyOf(originalId)).thenReturn(Optional.empty());
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

            assertThrows(SecurityException.class, () -> service.cancelCheckout(originalId));
        }
    }

    // ===================================================================== queries

    @Nested
    @DisplayName("queries")
    class Queries {

        @Test
        @DisplayName("getWorkingCopy delegates to repository")
        void getWorkingCopyDelegates() {
            UUID originalId = UUID.randomUUID();
            Document wc = new Document();
            wc.setId(UUID.randomUUID());
            wc.setWorkingCopy(true);

            when(documentRepository.findWorkingCopyOf(originalId)).thenReturn(Optional.of(wc));

            Optional<Document> result = service.getWorkingCopy(originalId);
            assertTrue(result.isPresent());
            assertTrue(result.get().isWorkingCopy());
        }

        @Test
        @DisplayName("getOriginal returns original document for a working copy")
        void getOriginalReturnsOriginal() {
            UUID originalId = UUID.randomUUID();
            UUID wcId = UUID.randomUUID();

            Document original = document(originalId, "report.docx", folder(UUID.randomUUID(), "/"));
            Document wc = document(wcId, "(Working Copy) report.docx", folder(UUID.randomUUID(), "/"));
            wc.setWorkingCopy(true);
            wc.setWorkingCopyOf(originalId);

            when(documentRepository.findById(wcId)).thenReturn(Optional.of(wc));
            when(documentRepository.findById(originalId)).thenReturn(Optional.of(original));

            Optional<Document> result = service.getOriginal(wcId);
            assertTrue(result.isPresent());
            assertEquals(originalId, result.get().getId());
        }

        @Test
        @DisplayName("getOriginal returns empty for non-working-copy")
        void getOriginalReturnsEmptyForNonWc() {
            UUID docId = UUID.randomUUID();
            Document doc = document(docId, "report.docx", folder(UUID.randomUUID(), "/"));

            when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

            Optional<Document> result = service.getOriginal(docId);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("getCheckedOutWorkingCopies delegates to repository")
        void getCheckedOutWorkingCopiesDelegates() {
            service.getCheckedOutWorkingCopies("alice");
            verify(documentRepository).findWorkingCopiesByUser("alice");
        }
    }

    // ===================================================================== helpers

    private Document document(UUID id, String name, Folder parent) {
        Document doc = new Document();
        doc.setId(id);
        doc.setName(name);
        doc.setMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        doc.setParent(parent);
        doc.setPath(parent.getPath() + "/" + name);
        return doc;
    }

    private Folder folder(UUID id, String path) {
        Folder f = new Folder();
        f.setId(id);
        f.setName(path.substring(path.lastIndexOf('/') + 1));
        f.setPath(path);
        return f;
    }
}
