package com.ecm.core.service.transfer;

import com.ecm.core.config.RepositoryIdentityProvider;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.ReplicationDefinition;
import com.ecm.core.entity.TransferNodeMapping;
import com.ecm.core.entity.TransferTarget;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.service.ContentService;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.TransferNodeMappingService;
import com.ecm.core.service.VersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
            new RepositoryIdentityProvider("athena", "athena"),
            transferNodeMappingService
        );
    }

    @Test
    @DisplayName("Folder replication with children does not short-circuit on unchanged mapping")
    void folderReplicationWithChildrenDoesNotShortCircuitOnUnchangedMapping() {
        UUID targetFolderId = UUID.randomUUID();
        UUID sourceFolderId = UUID.randomUUID();
        LocalDateTime watermark = LocalDateTime.parse("2026-04-11T12:00:00");

        Folder source = new Folder();
        source.setId(sourceFolderId);
        source.setName("Contracts");
        source.setPath("/Contracts");
        source.setLastModifiedDate(watermark.minusHours(1));

        Folder existingTarget = new Folder();
        existingTarget.setId(UUID.randomUUID());
        existingTarget.setName("Contracts");
        existingTarget.setPath("/Outbound/Contracts");

        TransferNodeMapping mapping = new TransferNodeMapping();
        mapping.setRootFolderId(targetFolderId);
        mapping.setSourceRepositoryId("athena");
        mapping.setSourceNodeId(sourceFolderId);
        mapping.setLocalNodeId(existingTarget.getId());
        mapping.setLastSourceModifiedAt(source.getLastModifiedDate());

        TransferTarget target = new TransferTarget();
        target.setTransportType(TransferTarget.TransportType.LOOPBACK);
        target.setTargetFolderId(targetFolderId);

        when(folderService.getFolder(targetFolderId)).thenReturn(existingTarget);
        when(transferNodeMappingService.findMapping(targetFolderId, "athena", sourceFolderId)).thenReturn(Optional.of(mapping));
        when(nodeRepository.findByParentIdAndName(targetFolderId, "Contracts")).thenReturn(Optional.of(existingTarget));

        TransferClient.TransferExecutionResult result = client.replicate(
            target,
            source,
            true,
            ReplicationDefinition.ConflictPolicy.SKIP,
            watermark
        );

        assertEquals(existingTarget.getId(), result.copiedNodeId());
        assertEquals("Loopback replication skipped existing node", result.message());
        verify(nodeRepository).findByParentIdAndName(targetFolderId, "Contracts");
        verify(transferNodeMappingService, never()).refreshSyncTimestamps(eq(targetFolderId), eq("athena"), eq(sourceFolderId), any(), any());
    }
}
