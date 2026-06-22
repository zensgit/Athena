package com.ecm.core.service.transfer;

import com.ecm.core.config.RepositoryIdentityProvider;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.ReplicationDefinition;
import com.ecm.core.entity.TransferNodeMapping;
import com.ecm.core.entity.TransferReceiverRegistration;
import com.ecm.core.entity.TransferTarget;
import com.ecm.core.exception.IllegalOperationException;
import com.ecm.core.pipeline.PipelineResult;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.TransferReceiverRegistrationRepository;
import com.ecm.core.service.DocumentUploadService;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.RecordsManagementService;
import com.ecm.core.service.TransferNodeMappingService;
import com.ecm.core.service.VersionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferReceiverServiceTest {

    @Mock
    private TransferReceiverRegistrationRepository receiverRepository;

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private FolderService folderService;

    @Mock
    private DocumentUploadService documentUploadService;

    @Mock
    private NodeService nodeService;

    @Mock
    private VersionService versionService;

    @Mock
    private RecordsManagementService recordsManagementService;

    @Mock
    private TransferNodeMappingService transferNodeMappingService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("verifyFolder accepts matching bearer secret inside target root")
    void verifyFolderAcceptsMatchingBearerSecret() {
        UUID rootId = UUID.randomUUID();
        Folder root = folder(rootId, "Outbound", "/Company Home/Outbound");
        TransferReceiverRegistration receiver = receiver(rootId);

        when(receiverRepository.findAll()).thenReturn(List.of(receiver));
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(rootId, Node.ArchiveStatus.LIVE)).thenReturn(Optional.of(root));
        when(receiverRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransferReceiverService service = service();

        TransferReceiverService.VerifyFolderResponse response = service.verifyFolder(rootId, null, "shared-secret");

        assertEquals(rootId, response.folderId());
        assertEquals("Outbound", response.folderName());
        assertEquals("athena", response.repositoryId());
        assertEquals(TransferReceiverRegistration.AccessStatus.SUCCESS, receiver.getLastAccessStatus());
    }

    @Test
    @DisplayName("createFolder renames duplicates and runs as transfer receiver admin")
    void createFolderRenamesDuplicateAndRunsAsTransferAdmin() {
        UUID rootId = UUID.randomUUID();
        Folder root = folder(rootId, "Outbound", "/Company Home/Outbound");
        TransferReceiverRegistration receiver = receiver(rootId);
        Folder created = folder(UUID.randomUUID(), "Contracts (Replica 1)", "/Company Home/Outbound/Contracts (Replica 1)");
        Node existing = folder(UUID.randomUUID(), "Contracts", "/Company Home/Outbound/Contracts");

        when(receiverRepository.findAll()).thenReturn(List.of(receiver));
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(rootId, Node.ArchiveStatus.LIVE)).thenReturn(Optional.of(root));
        when(nodeRepository.findByParentIdAndName(rootId, "Contracts"))
            .thenReturn(Optional.of(existing));
        when(nodeRepository.findByParentIdAndName(rootId, "Contracts (Replica 1)"))
            .thenReturn(Optional.empty());
        when(receiverRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(folderService.createFolder(any())).thenAnswer(invocation -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertEquals("transfer-receiver", auth.getPrincipal());
            assertTrue(auth.getAuthorities().stream().anyMatch(granted -> "ROLE_ADMIN".equals(granted.getAuthority())));
            return created;
        });

        TransferReceiverService service = service();

        TransferReceiverService.CreateFolderResponse response = service.createFolder(
            new TransferReceiverService.CreateFolderRequest(
                rootId,
                "Contracts",
                "Synced",
                ReplicationDefinition.ConflictPolicy.RENAME,
                null,
                null,
                null,
                null
            ),
            null,
            "shared-secret"
        );

        ArgumentCaptor<FolderService.CreateFolderRequest> requestCaptor = ArgumentCaptor.forClass(FolderService.CreateFolderRequest.class);
        verify(folderService).createFolder(requestCaptor.capture());
        assertEquals("Contracts (Replica 1)", requestCaptor.getValue().name());
        assertEquals(rootId, requestCaptor.getValue().parentId());
        assertEquals(created.getId(), response.folderId());
        assertEquals("Contracts (Replica 1)", response.folderName());
        assertEquals(TransferReceiverService.ConflictDisposition.RENAMED, response.disposition());
        assertEquals("Created renamed receiver folder", response.message());
    }

    @Test
    @DisplayName("createFolder skips existing folder when policy is skip")
    void createFolderSkipsExistingFolderWhenPolicySkip() {
        UUID rootId = UUID.randomUUID();
        Folder root = folder(rootId, "Outbound", "/Company Home/Outbound");
        Folder existing = folder(UUID.randomUUID(), "Contracts", "/Company Home/Outbound/Contracts");
        TransferReceiverRegistration receiver = receiver(rootId);

        when(receiverRepository.findAll()).thenReturn(List.of(receiver));
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(rootId, Node.ArchiveStatus.LIVE)).thenReturn(Optional.of(root));
        when(nodeRepository.findByParentIdAndName(rootId, "Contracts")).thenReturn(Optional.of(existing));
        when(receiverRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransferReceiverService service = service();

        TransferReceiverService.CreateFolderResponse response = service.createFolder(
            new TransferReceiverService.CreateFolderRequest(
                rootId,
                "Contracts",
                "Synced",
                ReplicationDefinition.ConflictPolicy.SKIP,
                null,
                null,
                null,
                null
            ),
            null,
            "shared-secret"
        );

        verify(folderService, never()).createFolder(any());
        assertEquals(existing.getId(), response.folderId());
        assertEquals(existing.getName(), response.folderName());
        assertEquals(TransferReceiverService.ConflictDisposition.SKIPPED, response.disposition());
        assertEquals("Skipped existing receiver folder", response.message());
    }

    @Test
    @DisplayName("createFolder resolves mapped parent from sourceParentNodeId")
    void createFolderResolvesMappedParentFromSourceParentNodeId() {
        UUID rootId = UUID.randomUUID();
        UUID sourceParentNodeId = UUID.randomUUID();
        UUID sourceNodeId = UUID.randomUUID();
        Folder root = folder(rootId, "Outbound", "/Company Home/Outbound");
        Folder mappedParent = folder(UUID.randomUUID(), "Team A", "/Company Home/Outbound/Team A");
        Folder created = folder(UUID.randomUUID(), "Contracts", "/Company Home/Outbound/Team A/Contracts");
        mappedParent.setParent(root);
        TransferReceiverRegistration receiver = receiver(rootId);
        TransferNodeMapping parentMapping = new TransferNodeMapping();
        parentMapping.setRootFolderId(rootId);
        parentMapping.setSourceRepositoryId("athena");
        parentMapping.setSourceNodeId(sourceParentNodeId);
        parentMapping.setLocalNodeId(mappedParent.getId());

        when(receiverRepository.findAll()).thenReturn(List.of(receiver));
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(rootId, Node.ArchiveStatus.LIVE)).thenReturn(Optional.of(root));
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(mappedParent.getId(), Node.ArchiveStatus.LIVE)).thenReturn(Optional.of(mappedParent));
        when(nodeRepository.findByParentIdAndName(mappedParent.getId(), "Contracts")).thenReturn(Optional.empty());
        when(receiverRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferNodeMappingService.findMapping(any(), anyString(), any())).thenReturn(Optional.empty());
        when(transferNodeMappingService.findMapping(rootId, "athena", sourceParentNodeId)).thenReturn(Optional.of(parentMapping));
        when(folderService.createFolder(any())).thenReturn(created);

        TransferReceiverService service = service();

        TransferReceiverService.CreateFolderResponse response = service.createFolder(
            new TransferReceiverService.CreateFolderRequest(
                rootId,
                "Contracts",
                "Synced",
                ReplicationDefinition.ConflictPolicy.RENAME,
                "athena",
                sourceNodeId,
                sourceParentNodeId,
                java.time.LocalDateTime.parse("2026-04-11T12:00:00")
            ),
            null,
            "shared-secret"
        );

        ArgumentCaptor<FolderService.CreateFolderRequest> requestCaptor = ArgumentCaptor.forClass(FolderService.CreateFolderRequest.class);
        verify(folderService).createFolder(requestCaptor.capture());
        assertEquals(mappedParent.getId(), requestCaptor.getValue().parentId());
        verify(transferNodeMappingService).upsertMapping(
            eq(rootId),
            eq("athena"),
            eq(sourceNodeId),
            eq(created.getId()),
            eq(java.time.LocalDateTime.parse("2026-04-11T12:00:00")),
            any(java.time.LocalDateTime.class)
        );
        assertEquals(created.getId(), response.folderId());
    }

    @Test
    @DisplayName("uploadDocument renames duplicate files and preserves description")
    void uploadDocumentRenamesDuplicateFilesAndPreservesDescription() throws IOException {
        UUID rootId = UUID.randomUUID();
        Folder root = folder(rootId, "Outbound", "/Company Home/Outbound");
        TransferReceiverRegistration receiver = receiver(rootId);
        Node existing = folder(UUID.randomUUID(), "contract.pdf", "/Company Home/Outbound/contract.pdf");
        UUID documentId = UUID.randomUUID();

        when(receiverRepository.findAll()).thenReturn(List.of(receiver));
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(rootId, Node.ArchiveStatus.LIVE)).thenReturn(Optional.of(root));
        when(nodeRepository.findByParentIdAndName(rootId, "contract.pdf")).thenReturn(Optional.of(existing));
        when(nodeRepository.findByParentIdAndName(rootId, "contract (Replica 1).pdf")).thenReturn(Optional.empty());
        when(receiverRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentUploadService.uploadDocument(any(), eq(rootId), eq(Map.of("description", "Signed")))).thenAnswer(invocation -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertEquals("transfer-receiver", auth.getPrincipal());
            MultipartFile uploaded = invocation.getArgument(0);
            assertEquals("contract (Replica 1).pdf", uploaded.getOriginalFilename());
            return PipelineResult.builder()
                .success(true)
                .documentId(documentId)
                .build();
        });

        TransferReceiverService service = service();

        TransferReceiverService.UploadDocumentResponse response = service.uploadDocument(
            new org.springframework.mock.web.MockMultipartFile("file", "contract.pdf", "application/pdf", "pdf".getBytes()),
            rootId,
            "Signed",
            ReplicationDefinition.ConflictPolicy.RENAME,
            null,
            null,
            null,
            null,
            null,
            "shared-secret"
        );

        assertEquals(documentId, response.documentId());
        assertEquals("contract (Replica 1).pdf", response.documentName());
        assertEquals(TransferReceiverService.ConflictDisposition.RENAMED, response.disposition());
        assertEquals("Uploaded renamed receiver document", response.message());
    }

    @Test
    @DisplayName("uploadDocument overwrites existing document as a new version")
    void uploadDocumentOverwritesExistingDocumentAsVersionWhenPolicyOverwrite() throws IOException {
        UUID rootId = UUID.randomUUID();
        Folder root = folder(rootId, "Outbound", "/Company Home/Outbound");
        TransferReceiverRegistration receiver = receiver(rootId);
        Document existing = document(UUID.randomUUID(), "contract.pdf", "/Company Home/Outbound/contract.pdf");

        when(receiverRepository.findAll()).thenReturn(List.of(receiver));
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(rootId, Node.ArchiveStatus.LIVE)).thenReturn(Optional.of(root));
        when(nodeRepository.findByParentIdAndName(rootId, "contract.pdf")).thenReturn(Optional.of(existing));
        when(receiverRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(versionService.createVersion(
            eq(existing.getId()),
            any(MultipartFile.class),
            eq("Replicated overwrite via transfer receiver"),
            eq(false)
        )).thenAnswer(invocation -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertEquals("transfer-receiver", auth.getPrincipal());
            MultipartFile uploaded = invocation.getArgument(1);
            assertEquals("contract.pdf", uploaded.getOriginalFilename());
            return null;
        });

        TransferReceiverService service = service();

        TransferReceiverService.UploadDocumentResponse response = service.uploadDocument(
            new org.springframework.mock.web.MockMultipartFile("file", "contract.pdf", "application/pdf", "pdf".getBytes()),
            rootId,
            "Signed",
            ReplicationDefinition.ConflictPolicy.OVERWRITE,
            null,
            null,
            null,
            null,
            null,
            "shared-secret"
        );

        verify(documentUploadService, never()).uploadDocument(any(), any(), any());
        verify(nodeService).updateNode(existing.getId(), Map.of("description", "Signed"));
        assertEquals(existing.getId(), response.documentId());
        assertEquals(existing.getName(), response.documentName());
        assertEquals(TransferReceiverService.ConflictDisposition.OVERWRITTEN, response.disposition());
        assertEquals("Overwrote receiver document", response.message());
    }

    @Test
    @DisplayName("uploadDocument returns unchanged when mapped source version already synced")
    void uploadDocumentReturnsUnchangedWhenMappedSourceAlreadySynced() throws IOException {
        UUID rootId = UUID.randomUUID();
        UUID sourceNodeId = UUID.randomUUID();
        Folder root = folder(rootId, "Outbound", "/Company Home/Outbound");
        Document existing = document(UUID.randomUUID(), "contract.pdf", "/Company Home/Outbound/contract.pdf");
        existing.setParent(root);
        TransferReceiverRegistration receiver = receiver(rootId);
        LocalDateTime syncedAt = LocalDateTime.parse("2026-04-11T12:00:00");
        TransferNodeMapping mapping = new TransferNodeMapping();
        mapping.setRootFolderId(rootId);
        mapping.setSourceRepositoryId("athena");
        mapping.setSourceNodeId(sourceNodeId);
        mapping.setLocalNodeId(existing.getId());
        mapping.setLastSourceModifiedAt(syncedAt);

        when(receiverRepository.findAll()).thenReturn(List.of(receiver));
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(rootId, Node.ArchiveStatus.LIVE)).thenReturn(Optional.of(root));
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(existing.getId(), Node.ArchiveStatus.LIVE)).thenReturn(Optional.of(existing));
        when(receiverRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferNodeMappingService.findMapping(any(), anyString(), any())).thenReturn(Optional.empty());
        when(transferNodeMappingService.findMapping(rootId, "athena", sourceNodeId)).thenReturn(Optional.of(mapping));

        TransferReceiverService service = service();

        TransferReceiverService.UploadDocumentResponse response = service.uploadDocument(
            new org.springframework.mock.web.MockMultipartFile("file", "contract.pdf", "application/pdf", "pdf".getBytes()),
            rootId,
            "Signed",
            ReplicationDefinition.ConflictPolicy.OVERWRITE,
            "athena",
            sourceNodeId,
            null,
            syncedAt,
            null,
            "shared-secret"
        );

        verify(documentUploadService, never()).uploadDocument(any(), any(), any());
        verify(versionService, never()).createVersion(any(), any(MultipartFile.class), anyString(), anyBoolean());
        verify(transferNodeMappingService).refreshSyncTimestamps(
            eq(rootId),
            eq("athena"),
            eq(sourceNodeId),
            eq(syncedAt),
            any(LocalDateTime.class)
        );
        assertEquals(TransferReceiverService.ConflictDisposition.UNCHANGED, response.disposition());
        assertEquals(existing.getId(), response.documentId());
    }

    @Test
    @DisplayName("uploadDocument revisions mapped document in place (OVERWRITTEN) when source modified newer")
    void uploadDocumentRevisionsMappedDocumentWhenSourceModifiedNewer() throws IOException {
        // A1 disposition contract: when an incoming document maps to an EXISTING Athena
        // document via (sourceRepositoryId, sourceNodeId) AND the incoming
        // sourceLastModifiedAt differs from (is newer than) the mapping's stored value,
        // the receiver must revise the SAME document in place via versionService.createVersion
        // (minor version, majorVersion=false) and return OVERWRITTEN with the existing id --
        // NOT create a new document. NOTE: matchesSourceVersion compares by EQUALITY
        // (Objects.equals), not ordering, so the branch fires on ANY mismatch; we set the
        // incoming timestamp strictly newer to document the intended "newer source wins" intent.
        UUID rootId = UUID.randomUUID();
        UUID sourceNodeId = UUID.randomUUID();
        Folder root = folder(rootId, "Outbound", "/Company Home/Outbound");
        Document existing = document(UUID.randomUUID(), "contract.pdf", "/Company Home/Outbound/contract.pdf");
        existing.setParent(root); // parent matches target -> no moveNode on this branch
        TransferReceiverRegistration receiver = receiver(rootId);
        LocalDateTime storedAt = LocalDateTime.parse("2026-04-11T12:00:00");
        LocalDateTime newerSourceModifiedAt = LocalDateTime.parse("2026-04-12T08:30:00");
        TransferNodeMapping mapping = new TransferNodeMapping();
        mapping.setRootFolderId(rootId);
        mapping.setSourceRepositoryId("athena");
        mapping.setSourceNodeId(sourceNodeId);
        mapping.setLocalNodeId(existing.getId());
        mapping.setLastSourceModifiedAt(storedAt); // OLDER than incoming -> matchesSourceVersion == false

        when(receiverRepository.findAll()).thenReturn(List.of(receiver));
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(rootId, Node.ArchiveStatus.LIVE)).thenReturn(Optional.of(root));
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(existing.getId(), Node.ArchiveStatus.LIVE)).thenReturn(Optional.of(existing));
        when(receiverRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferNodeMappingService.findMapping(rootId, "athena", sourceNodeId)).thenReturn(Optional.of(mapping));

        TransferReceiverService service = service();

        TransferReceiverService.UploadDocumentResponse response = service.uploadDocument(
            new org.springframework.mock.web.MockMultipartFile("file", "contract.pdf", "application/pdf", "pdf".getBytes()),
            rootId,
            "Signed",
            ReplicationDefinition.ConflictPolicy.OVERWRITE,
            "athena",
            sourceNodeId,
            null,
            newerSourceModifiedAt,
            null,
            "shared-secret"
        );

        // Revision-in-place: same document id gets a new (minor) version, no fresh upload.
        verify(versionService).createVersion(eq(existing.getId()), any(MultipartFile.class), anyString(), eq(false));
        verify(documentUploadService, never()).uploadDocument(any(), any(), any());
        // Mapping watermark advanced to the newer source timestamp on the same local node.
        verify(transferNodeMappingService).upsertMapping(
            eq(rootId),
            eq("athena"),
            eq(sourceNodeId),
            eq(existing.getId()),
            eq(newerSourceModifiedAt),
            any(LocalDateTime.class)
        );
        assertEquals(existing.getId(), response.documentId());
        assertEquals(TransferReceiverService.ConflictDisposition.OVERWRITTEN, response.disposition());
        assertEquals("OVERWRITTEN", response.disposition().name());
        assertEquals("Updated mapped receiver document", response.message());
    }

    @Test
    @DisplayName("createFolder rejects file plan parent")
    void createFolderRejectsFilePlanParent() {
        UUID rootId = UUID.randomUUID();
        Folder root = folder(rootId, "Corporate File Plan", "/Corporate File Plan");
        root.setFolderType(Folder.FolderType.FILE_PLAN);
        TransferReceiverRegistration receiver = receiver(rootId);

        when(receiverRepository.findAll()).thenReturn(List.of(receiver));
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(rootId, Node.ArchiveStatus.LIVE)).thenReturn(Optional.of(root));
        doThrow(new com.ecm.core.exception.IllegalOperationException(
            "Cannot replicate folder into target folder because target folder 'Corporate File Plan' is a file plan"
        )).when(recordsManagementService).assertCreateInFolderAllowed(root, "replicate folder into target folder");

        TransferReceiverService service = service();

        assertThrows(IllegalOperationException.class, () -> service.createFolder(
            new TransferReceiverService.CreateFolderRequest(
                rootId,
                "Contracts",
                "Synced",
                ReplicationDefinition.ConflictPolicy.RENAME,
                null,
                null,
                null,
                null
            ),
            null,
            "shared-secret"
        ));
        verify(folderService, never()).createFolder(any());
    }

    @Test
    @DisplayName("uploadDocument rejects overwrite when existing document is RM governed")
    void uploadDocumentRejectsOverwriteWhenExistingDocumentIsRmGoverned() throws IOException {
        UUID rootId = UUID.randomUUID();
        Folder root = folder(rootId, "Outbound", "/Company Home/Outbound");
        TransferReceiverRegistration receiver = receiver(rootId);
        Document existing = document(UUID.randomUUID(), "contract.pdf", "/Corporate File Plan/contract.pdf");

        when(receiverRepository.findAll()).thenReturn(List.of(receiver));
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(rootId, Node.ArchiveStatus.LIVE)).thenReturn(Optional.of(root));
        when(nodeRepository.findByParentIdAndName(rootId, "contract.pdf")).thenReturn(Optional.of(existing));
        doThrow(new com.ecm.core.exception.IllegalOperationException(
            "Cannot overwrite existing document via transfer receiver because node 'contract.pdf' is governed by file plan 'Corporate File Plan'"
        )).when(recordsManagementService).assertArchiveMutationAllowed(existing, "overwrite existing document via transfer receiver");

        TransferReceiverService service = service();

        assertThrows(IllegalOperationException.class, () -> service.uploadDocument(
            new org.springframework.mock.web.MockMultipartFile("file", "contract.pdf", "application/pdf", "pdf".getBytes()),
            rootId,
            "Signed",
            ReplicationDefinition.ConflictPolicy.OVERWRITE,
            null,
            null,
            null,
            null,
            null,
            "shared-secret"
        ));
        verify(versionService, never()).createVersion(any(), any(MultipartFile.class), anyString(), anyBoolean());
        verify(documentUploadService, never()).uploadDocument(any(), any(), any());
    }

    @Test
    @DisplayName("verifyFolder rejects credentials outside authorized root")
    void verifyFolderRejectsFolderOutsideAuthorizedRoot() {
        UUID rootId = UUID.randomUUID();
        UUID foreignFolderId = UUID.randomUUID();
        Folder root = folder(rootId, "Outbound", "/Company Home/Outbound");
        Folder foreign = folder(foreignFolderId, "Secret", "/Company Home/Secret");
        TransferReceiverRegistration receiver = receiver(rootId);

        when(receiverRepository.findAll()).thenReturn(List.of(receiver));
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(foreignFolderId, Node.ArchiveStatus.LIVE)).thenReturn(Optional.of(foreign));
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(rootId, Node.ArchiveStatus.LIVE)).thenReturn(Optional.of(root));
        when(receiverRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransferReceiverService service = service();

        assertThrows(SecurityException.class, () -> service.verifyFolder(foreignFolderId, null, "shared-secret"));
        assertEquals(TransferReceiverRegistration.AccessStatus.FAILED, receiver.getLastAccessStatus());
    }

    private TransferReceiverService service() {
        return new TransferReceiverService(
            receiverRepository,
            nodeRepository,
            folderService,
            documentUploadService,
            nodeService,
            versionService,
            recordsManagementService,
            new RepositoryIdentityProvider("athena", "athena"),
            transferNodeMappingService
        );
    }

    private TransferReceiverRegistration receiver(UUID folderId) {
        TransferReceiverRegistration receiver = new TransferReceiverRegistration();
        receiver.setRootFolderId(folderId);
        receiver.setEnabled(true);
        receiver.setAuthType(TransferTarget.AuthType.BEARER);
        receiver.setAuthSecret("shared-secret");
        receiver.setLastAccessStatus(TransferReceiverRegistration.AccessStatus.NEVER_USED);
        return receiver;
    }

    private Folder folder(UUID id, String name, String path) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setPath(path);
        folder.setArchiveStatus(Node.ArchiveStatus.LIVE);
        folder.setDeleted(false);
        folder.setCreatedDate(java.time.LocalDateTime.now());
        folder.setLastModifiedDate(java.time.LocalDateTime.now());
        return folder;
    }

    private Document document(UUID id, String name, String path) {
        Document document = new Document();
        document.setId(id);
        document.setName(name);
        document.setPath(path);
        document.setArchiveStatus(Node.ArchiveStatus.LIVE);
        document.setDeleted(false);
        document.setCreatedDate(java.time.LocalDateTime.now());
        document.setLastModifiedDate(java.time.LocalDateTime.now());
        return document;
    }
}
