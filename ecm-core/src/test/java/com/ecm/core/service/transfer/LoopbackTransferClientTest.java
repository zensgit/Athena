package com.ecm.core.service.transfer;

import com.ecm.core.config.RepositoryIdentityProvider;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.ReplicationDefinition;
import com.ecm.core.entity.TransferNodeMapping;
import com.ecm.core.entity.TransferTarget;
import com.ecm.core.entity.Version;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.service.ContentService;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.RecordsManagementService;
import com.ecm.core.service.TransferNodeMappingService;
import com.ecm.core.service.VersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoopbackTransferClientTest {

    @Mock
    private FolderService folderService;

    @Mock
    private NodeService nodeService;

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private ContentService contentService;

    @Mock
    private VersionService versionService;

    @Mock
    private RecordsManagementService recordsManagementService;

    @Mock
    private TransferNodeMappingService transferNodeMappingService;

    private LoopbackTransferClient client;

    @BeforeEach
    void setUp() {
        client = new LoopbackTransferClient(
            folderService,
            nodeService,
            nodeRepository,
            contentService,
            versionService,
            recordsManagementService,
            new RepositoryIdentityProvider("athena", "athena"),
            transferNodeMappingService
        );
    }

    @Test
    @DisplayName("mapped unchanged folder still recurses into changed descendants")
    void mappedUnchangedFolderStillRecursesIntoChangedDescendants() throws Exception {
        UUID receiverRootId = UUID.randomUUID();
        UUID sourceFolderId = UUID.randomUUID();
        UUID sourceDocumentId = UUID.randomUUID();
        LocalDateTime watermark = LocalDateTime.parse("2026-04-11T12:00:00");

        Folder receiverRoot = folder(receiverRootId, "Outbound", "/Outbound");
        Folder source = folder(sourceFolderId, "Contracts", "/Contracts");
        source.setLastModifiedDate(watermark.minusHours(1));

        Folder existingTarget = folder(UUID.randomUUID(), "Contracts", "/Outbound/Contracts");
        existingTarget.setParent(receiverRoot);

        Document sourceDocument = document(sourceDocumentId, "contract.pdf", "/Contracts/contract.pdf", "content-source");
        sourceDocument.setParent(source);
        sourceDocument.setLastModifiedDate(watermark.plusMinutes(30));

        Document existingTargetDocument = document(UUID.randomUUID(), "contract.pdf", "/Outbound/Contracts/contract.pdf", "content-target");
        existingTargetDocument.setParent(existingTarget);

        TransferNodeMapping mapping = new TransferNodeMapping();
        mapping.setRootFolderId(receiverRootId);
        mapping.setSourceRepositoryId("athena");
        mapping.setSourceNodeId(sourceFolderId);
        mapping.setLocalNodeId(existingTarget.getId());
        mapping.setLastSourceModifiedAt(source.getLastModifiedDate());

        TransferNodeMapping documentMapping = new TransferNodeMapping();
        documentMapping.setRootFolderId(receiverRootId);
        documentMapping.setSourceRepositoryId("athena");
        documentMapping.setSourceNodeId(sourceDocumentId);
        documentMapping.setLocalNodeId(existingTargetDocument.getId());
        documentMapping.setLastSourceModifiedAt(watermark.minusDays(1));

        TransferTarget target = new TransferTarget();
        target.setTransportType(TransferTarget.TransportType.LOOPBACK);
        target.setTargetFolderId(receiverRootId);

        when(folderService.getFolder(receiverRootId)).thenReturn(receiverRoot);
        when(transferNodeMappingService.findMapping(receiverRootId, "athena", sourceFolderId)).thenReturn(Optional.of(mapping));
        when(transferNodeMappingService.findMapping(receiverRootId, "athena", sourceDocumentId)).thenReturn(Optional.of(documentMapping));
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(existingTarget.getId(), Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(existingTarget));
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(existingTargetDocument.getId(), Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(existingTargetDocument));
        when(nodeRepository.findByParentIdAndDeletedFalseAndArchiveStatus(sourceFolderId, Node.ArchiveStatus.LIVE))
            .thenReturn(List.of(sourceDocument));
        when(contentService.getContent("content-source"))
            .thenReturn(new ByteArrayInputStream("updated".getBytes(StandardCharsets.UTF_8)));
        when(versionService.createVersion(eq(existingTargetDocument.getId()), any(InputStream.class), eq("contract.pdf"), anyString(), eq(false)))
            .thenReturn(new Version());

        TransferClient.TransferExecutionResult result = client.replicate(
            target,
            source,
            true,
            ReplicationDefinition.ConflictPolicy.SKIP,
            watermark
        );

        assertEquals(existingTarget.getId(), result.copiedNodeId());
        assertEquals("Loopback mapped folder already up to date", result.message());
        assertEquals(2, result.entries().size());
        assertEquals("UNCHANGED", result.entries().get(0).action());
        assertEquals(existingTarget.getId(), result.entries().get(0).targetNodeId());
        assertEquals("OVERWRITTEN", result.entries().get(1).action());
        assertEquals(existingTargetDocument.getId(), result.entries().get(1).targetNodeId());
        verify(transferNodeMappingService).refreshSyncTimestamps(eq(receiverRootId), eq("athena"), eq(sourceFolderId), eq(source.getLastModifiedDate()), any());
        verify(versionService).createVersion(eq(existingTargetDocument.getId()), any(InputStream.class), eq("contract.pdf"), anyString(), eq(false));
        verify(transferNodeMappingService).upsertMapping(eq(receiverRootId), eq("athena"), eq(sourceDocumentId), eq(existingTargetDocument.getId()), eq(sourceDocument.getLastModifiedDate()), any());
        verify(nodeRepository, never()).findByParentIdAndName(receiverRootId, "Contracts");
    }

    @Test
    @DisplayName("unmapped skip conflict stops folder recursion at the conflicting node")
    void unmappedSkipConflictStopsFolderRecursionAtConflictingNode() {
        UUID receiverRootId = UUID.randomUUID();
        UUID sourceFolderId = UUID.randomUUID();
        LocalDateTime watermark = LocalDateTime.parse("2026-04-11T12:00:00");

        Folder receiverRoot = folder(receiverRootId, "Outbound", "/Outbound");
        Folder source = folder(sourceFolderId, "Contracts", "/Contracts");
        source.setLastModifiedDate(watermark.minusHours(1));
        Folder existingTarget = folder(UUID.randomUUID(), "Contracts", "/Outbound/Contracts");
        existingTarget.setParent(receiverRoot);

        TransferTarget target = new TransferTarget();
        target.setTransportType(TransferTarget.TransportType.LOOPBACK);
        target.setTargetFolderId(receiverRootId);

        when(folderService.getFolder(receiverRootId)).thenReturn(receiverRoot);
        when(transferNodeMappingService.findMapping(receiverRootId, "athena", sourceFolderId)).thenReturn(Optional.empty());
        when(nodeRepository.findByParentIdAndName(receiverRootId, "Contracts")).thenReturn(Optional.of(existingTarget));

        TransferClient.TransferExecutionResult result = client.replicate(
            target,
            source,
            true,
            ReplicationDefinition.ConflictPolicy.SKIP,
            watermark
        );

        assertEquals(existingTarget.getId(), result.copiedNodeId());
        assertEquals("Loopback replication skipped existing node", result.message());
        assertEquals(1, result.entries().size());
        assertEquals("SKIPPED", result.entries().get(0).action());
        verify(nodeRepository, never()).findByParentIdAndDeletedFalseAndArchiveStatus(sourceFolderId, Node.ArchiveStatus.LIVE);
        verify(transferNodeMappingService).upsertMapping(eq(receiverRootId), eq("athena"), eq(sourceFolderId), eq(existingTarget.getId()), eq(source.getLastModifiedDate()), any());
    }

    @Test
    @DisplayName("mapped unchanged document returns unchanged target instead of null skip")
    void mappedUnchangedDocumentReturnsUnchangedTargetInsteadOfNullSkip() {
        UUID receiverRootId = UUID.randomUUID();
        UUID sourceDocumentId = UUID.randomUUID();
        LocalDateTime watermark = LocalDateTime.parse("2026-04-11T12:00:00");

        Folder receiverRoot = folder(receiverRootId, "Outbound", "/Outbound");
        Document source = document(sourceDocumentId, "contract.pdf", "/contract.pdf", "content-source");
        source.setLastModifiedDate(watermark.minusMinutes(30));
        Document existingTarget = document(UUID.randomUUID(), "contract.pdf", "/Outbound/contract.pdf", "content-target");
        existingTarget.setParent(receiverRoot);

        TransferNodeMapping mapping = new TransferNodeMapping();
        mapping.setRootFolderId(receiverRootId);
        mapping.setSourceRepositoryId("athena");
        mapping.setSourceNodeId(sourceDocumentId);
        mapping.setLocalNodeId(existingTarget.getId());
        mapping.setLastSourceModifiedAt(source.getLastModifiedDate());

        TransferTarget target = new TransferTarget();
        target.setTransportType(TransferTarget.TransportType.LOOPBACK);
        target.setTargetFolderId(receiverRootId);

        when(folderService.getFolder(receiverRootId)).thenReturn(receiverRoot);
        when(transferNodeMappingService.findMapping(receiverRootId, "athena", sourceDocumentId)).thenReturn(Optional.of(mapping));
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(existingTarget.getId(), Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(existingTarget));

        TransferClient.TransferExecutionResult result = client.replicate(
            target,
            source,
            false,
            ReplicationDefinition.ConflictPolicy.SKIP,
            watermark
        );

        assertEquals(existingTarget.getId(), result.copiedNodeId());
        assertEquals("Loopback mapped document already up to date", result.message());
        assertEquals(1, result.entries().size());
        assertEquals("UNCHANGED", result.entries().get(0).action());
        assertEquals(existingTarget.getId(), result.entries().get(0).targetNodeId());
        verify(transferNodeMappingService).refreshSyncTimestamps(eq(receiverRootId), eq("athena"), eq(sourceDocumentId), eq(source.getLastModifiedDate()), any());
        verify(nodeRepository, never()).findByParentIdAndName(any(), any());
    }

    @Test
    @DisplayName("replicate rejects governed target folder before loopback copy")
    void replicateRejectsGovernedTargetFolderBeforeLoopbackCopy() {
        UUID receiverRootId = UUID.randomUUID();
        UUID sourceDocumentId = UUID.randomUUID();

        Folder receiverRoot = folder(receiverRootId, "Corporate File Plan", "/Corporate File Plan");
        receiverRoot.setFolderType(Folder.FolderType.FILE_PLAN);
        Document source = document(sourceDocumentId, "contract.pdf", "/contract.pdf", "content-source");
        source.setLastModifiedDate(LocalDateTime.parse("2026-04-11T12:30:00"));

        TransferTarget target = new TransferTarget();
        target.setTransportType(TransferTarget.TransportType.LOOPBACK);
        target.setTargetFolderId(receiverRootId);

        when(folderService.getFolder(receiverRootId)).thenReturn(receiverRoot);
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(receiverRootId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(receiverRoot));
        when(transferNodeMappingService.findMapping(receiverRootId, "athena", sourceDocumentId)).thenReturn(Optional.empty());
        doThrow(new com.ecm.core.exception.IllegalOperationException(
            "Cannot replicate document into loopback target folder because target folder 'Corporate File Plan' is a file plan"
        )).when(recordsManagementService).assertCreateInFolderAllowed(receiverRoot, "replicate document into loopback target folder");

        assertThrows(com.ecm.core.exception.IllegalOperationException.class, () -> client.replicate(
            target,
            source,
            false,
            ReplicationDefinition.ConflictPolicy.RENAME,
            null
        ));
        verify(nodeService, never()).copyNode(any(), any(), anyString(), anyBoolean());
    }

    private Folder folder(UUID id, String name, String path) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setPath(path);
        return folder;
    }

    private Document document(UUID id, String name, String path, String contentId) {
        Document document = new Document();
        document.setId(id);
        document.setName(name);
        document.setPath(path);
        document.setContentId(contentId);
        return document;
    }
}
