package com.ecm.core.service;

import com.ecm.core.entity.ArchivePolicy;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.repository.ArchivePolicyRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchivePolicyServiceTest {

    @Mock private ArchivePolicyRepository archivePolicyRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;
    @Mock private ContentArchiveService contentArchiveService;

    private ArchivePolicyService archivePolicyService;

    @BeforeEach
    void setUp() {
        archivePolicyService = new ArchivePolicyService(
            archivePolicyRepository,
            folderRepository,
            nodeRepository,
            securityService,
            contentArchiveService
        );
    }

    @Test
    @DisplayName("Dry-run collapses nested descendants and skips recent nodes")
    void dryRunCollapsesNestedDescendants() {
        UUID folderId = UUID.randomUUID();
        Folder folder = folder(folderId, "/Finance");

        Folder oldFolder = folder(UUID.randomUUID(), "/Finance/Old");
        oldFolder.setLastModifiedDate(LocalDateTime.now().minusDays(200));

        Document nested = document(UUID.randomUUID(), "/Finance/Old/spec.docx");
        nested.setLastModifiedDate(LocalDateTime.now().minusDays(190));

        Document loose = document(UUID.randomUUID(), "/Finance/loose.pdf");
        loose.setLastModifiedDate(LocalDateTime.now().minusDays(120));

        Document recent = document(UUID.randomUUID(), "/Finance/recent.pdf");
        recent.setLastModifiedDate(LocalDateTime.now().minusDays(5));

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(nodeRepository.findByPathPrefix("/Finance/")).thenReturn(List.of(oldFolder, nested, loose, recent));

        ArchivePolicyService.ArchivePolicyDryRunDto result = archivePolicyService.dryRunPolicy(
            folderId,
            new ArchivePolicyService.ArchivePolicyUpsertRequest(true, 90, Node.ArchiveStoreTier.COLD, true, 10)
        );

        assertEquals(2, result.candidateCount());
        assertEquals(Set.of("/Finance/Old", "/Finance/loose.pdf"), Set.copyOf(result.candidates().stream().map(
            ArchivePolicyService.ArchivePolicyCandidateDto::path
        ).toList()));
    }

    @Test
    @DisplayName("Execute archives eligible candidates and updates policy stats")
    void executeArchivesCandidatesAndUpdatesPolicy() {
        UUID folderId = UUID.randomUUID();
        Folder folder = folder(folderId, "/Finance");

        ArchivePolicy policy = new ArchivePolicy();
        policy.setFolder(folder);
        policy.setEnabled(true);
        policy.setInactivityDays(30);
        policy.setStorageTier(Node.ArchiveStoreTier.GLACIER);
        policy.setIncludeSubfolders(false);
        policy.setMaxCandidatesPerRun(25);

        Document stale = document(UUID.randomUUID(), "/Finance/contract.pdf");
        stale.setLastModifiedDate(LocalDateTime.now().minusDays(60));

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(archivePolicyRepository.findByFolderId(folderId)).thenReturn(Optional.of(policy));
        when(nodeRepository.findByParentIdAndDeletedFalseAndArchiveStatus(folderId, Node.ArchiveStatus.LIVE))
            .thenReturn(List.of(stale));
        when(contentArchiveService.archiveNodeByPolicy(stale.getId(), Node.ArchiveStoreTier.GLACIER, "admin"))
            .thenReturn(new ContentArchiveService.ArchiveMutationDto(
                stale.getId(),
                stale.getName(),
                Node.ArchiveStatus.ARCHIVED,
                Node.ArchiveStoreTier.GLACIER,
                LocalDateTime.now(),
                "admin",
                1
            ));
        when(archivePolicyRepository.save(any(ArchivePolicy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArchivePolicyService.ArchivePolicyExecutionDto result = archivePolicyService.executePolicy(folderId);

        assertEquals(1, result.candidateCount());
        assertEquals(1, result.archivedNodeCount());
        ArgumentCaptor<ArchivePolicy> policyCaptor = ArgumentCaptor.forClass(ArchivePolicy.class);
        verify(archivePolicyRepository).save(policyCaptor.capture());
        assertNotNull(policyCaptor.getValue().getLastExecutedAt());
        assertEquals(1, policyCaptor.getValue().getLastCandidateCount());
        assertEquals(1, policyCaptor.getValue().getLastArchivedNodeCount());
    }

    private static Folder folder(UUID id, String path) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(path.substring(path.lastIndexOf('/') + 1));
        folder.setPath(path);
        folder.setArchiveStatus(Node.ArchiveStatus.LIVE);
        folder.setDeleted(false);
        return folder;
    }

    private static Document document(UUID id, String path) {
        Document document = new Document();
        document.setId(id);
        document.setName(path.substring(path.lastIndexOf('/') + 1));
        document.setPath(path);
        document.setArchiveStatus(Node.ArchiveStatus.LIVE);
        document.setDeleted(false);
        return document;
    }
}
