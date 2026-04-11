package com.ecm.core.service.transfer;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.ReplicationDefinition;
import com.ecm.core.entity.TransferReceiverRegistration;
import com.ecm.core.entity.TransferTarget;
import com.ecm.core.pipeline.PipelineResult;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.TransferReceiverRegistrationRepository;
import com.ecm.core.service.DocumentUploadService;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.NodeService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
                ReplicationDefinition.ConflictPolicy.RENAME
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
                ReplicationDefinition.ConflictPolicy.SKIP
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
            versionService
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
