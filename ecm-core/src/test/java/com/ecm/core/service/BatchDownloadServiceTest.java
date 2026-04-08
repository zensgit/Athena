package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission;
import com.ecm.core.repository.NodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipOutputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchDownloadServiceTest {

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private ContentService contentService;

    @Mock
    private SecurityService securityService;

    @Mock
    private FolderService folderService;

    @Mock
    private TenantWorkspaceScopeService tenantWorkspaceScopeService;

    private BatchDownloadService batchDownloadService;

    @BeforeEach
    void setUp() {
        batchDownloadService = new BatchDownloadService(
            nodeRepository,
            contentService,
            securityService,
            folderService,
            tenantWorkspaceScopeService
        );
    }

    @Test
    @DisplayName("Preflight aggregates included readable content and structured skips")
    void inspectNodesPreflightAggregatesStructuredSkips() {
        UUID documentId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID forbiddenId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();

        Document readableDocument = document(documentId, "readable.txt", 100L, false);
        Folder readableFolder = folder(folderId, "project");
        Document nestedDocument = document(UUID.randomUUID(), "nested.txt", 200L, false);
        Document forbiddenDocument = document(forbiddenId, "forbidden.txt", 300L, false);
        Document deletedDocument = document(deletedId, "deleted.txt", 400L, true);

        when(nodeRepository.findById(documentId)).thenReturn(Optional.of(readableDocument));
        when(nodeRepository.findById(folderId)).thenReturn(Optional.of(readableFolder));
        when(nodeRepository.findById(forbiddenId)).thenReturn(Optional.of(forbiddenDocument));
        when(nodeRepository.findById(deletedId)).thenReturn(Optional.of(deletedDocument));
        when(nodeRepository.findById(missingId)).thenReturn(Optional.empty());
        when(nodeRepository.findByParentIdAndDeletedFalse(folderId)).thenReturn(List.of(nestedDocument));

        when(securityService.hasPermission(readableDocument, Permission.PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(readableFolder, Permission.PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(nestedDocument, Permission.PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(forbiddenDocument, Permission.PermissionType.READ)).thenReturn(false);

        BatchDownloadService.BatchDownloadPreflightSummary summary = batchDownloadService.inspectNodesPreflight(List.of(
            documentId,
            folderId,
            forbiddenId,
            deletedId,
            missingId,
            folderId
        ));

        assertEquals(6, summary.requestedCount());
        assertEquals(5, summary.distinctCount());
        assertEquals(1, summary.duplicateCount());
        assertEquals(List.of(documentId, folderId), summary.includedNodeIds());
        assertEquals(2, summary.includedNodeCount());
        assertEquals(2, summary.includedFileCount());
        assertEquals(300L, summary.includedBytes());
        assertEquals(1, summary.missingCount());
        assertEquals(1, summary.deletedCount());
        assertEquals(1, summary.forbiddenCount());
        assertEquals(0, summary.emptyFolderCount());
        assertEquals(4, summary.skippedCount());
        assertTrue(summary.executable());
        assertEquals(BatchDownloadService.BatchDownloadPreflightDecision.PARTIAL, summary.decision());
        assertEquals(BatchDownloadService.BatchDownloadPreflightPrimaryReason.FORBIDDEN_NODES, summary.primaryReason());
        assertTrue(summary.message().contains("skipped 4 item(s)"));
        assertTrue(summary.warnings().contains("Skipped 1 duplicate node reference(s)"));
        assertTrue(summary.items().stream().anyMatch(
            item -> item.outcome() == BatchDownloadService.BatchDownloadPreflightOutcome.MISSING
                && missingId.equals(item.nodeId())
        ));
    }

    @Test
    @DisplayName("Preflight marks readable empty folders as non-executable")
    void inspectNodesPreflightMarksEmptyFolder() {
        UUID folderId = UUID.randomUUID();
        Folder emptyFolder = folder(folderId, "empty");

        when(nodeRepository.findById(folderId)).thenReturn(Optional.of(emptyFolder));
        when(nodeRepository.findByParentIdAndDeletedFalse(folderId)).thenReturn(List.of());
        when(securityService.hasPermission(emptyFolder, Permission.PermissionType.READ)).thenReturn(true);

        BatchDownloadService.BatchDownloadPreflightSummary summary =
            batchDownloadService.inspectNodesPreflight(List.of(folderId));

        assertEquals(1, summary.requestedCount());
        assertEquals(1, summary.distinctCount());
        assertEquals(0, summary.includedNodeCount());
        assertEquals(0, summary.includedFileCount());
        assertEquals(1, summary.emptyFolderCount());
        assertEquals(1, summary.skippedCount());
        assertFalse(summary.executable());
        assertEquals(BatchDownloadService.BatchDownloadPreflightDecision.BLOCKED, summary.decision());
        assertEquals(BatchDownloadService.BatchDownloadPreflightPrimaryReason.EMPTY_FOLDERS, summary.primaryReason());
        assertEquals("No readable files available for batch download", summary.message());
        assertEquals(BatchDownloadService.BatchDownloadPreflightOutcome.EMPTY_FOLDER, summary.items().get(0).outcome());
    }

    @Test
    @DisplayName("Preflight treats hidden tenant node as missing")
    void inspectNodesPreflightTreatsHiddenTenantNodeAsMissing() {
        UUID documentId = UUID.randomUUID();
        Document hiddenDocument = document(documentId, "hidden.txt", 100L, false);

        when(nodeRepository.findById(documentId)).thenReturn(Optional.of(hiddenDocument));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
        when(tenantWorkspaceScopeService.isPathVisible(hiddenDocument.getPath())).thenReturn(false);

        BatchDownloadService.BatchDownloadPreflightSummary summary =
            batchDownloadService.inspectNodesPreflight(List.of(documentId));

        assertEquals(1, summary.missingCount());
        assertEquals(0, summary.includedNodeCount());
        assertEquals(BatchDownloadService.BatchDownloadPreflightOutcome.MISSING, summary.items().get(0).outcome());
    }

    @Test
    @DisplayName("ZIP streaming skips hidden tenant nodes")
    void writeNodesAsZipSkipsHiddenTenantNode() {
        UUID documentId = UUID.randomUUID();
        Document hiddenDocument = document(documentId, "hidden.txt", 100L, false);

        when(nodeRepository.findById(documentId)).thenReturn(Optional.of(hiddenDocument));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
        when(tenantWorkspaceScopeService.isPathVisible(hiddenDocument.getPath())).thenReturn(false);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BatchDownloadService.BatchDownloadArchiveSummary summary =
            batchDownloadService.writeNodesAsZip(List.of(documentId), new ZipOutputStream(bytes), BatchDownloadService.BatchDownloadProgressListener.noop());

        assertEquals(0, summary.filesAdded());
        assertEquals(0L, summary.bytesAdded());
        assertFalse(summary.cancelled());
    }

    private Document document(UUID id, String name, long size, boolean deleted) {
        Document document = new Document();
        document.setId(id);
        document.setName(name);
        document.setFileSize(size);
        document.setMimeType("text/plain");
        document.setDeleted(deleted);
        document.setPath("/" + name);
        return document;
    }

    private Folder folder(UUID id, String name) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setDeleted(false);
        folder.setPath("/" + name);
        return folder;
    }
}
