package com.ecm.core.service;

import com.ecm.core.entity.DispositionSchedule;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.exception.IllegalOperationException;
import com.ecm.core.repository.ArchivePolicyRepository;
import com.ecm.core.repository.DispositionScheduleRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchivePolicyServiceDispositionConflictTest {

    @Mock private ArchivePolicyRepository archivePolicyRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;
    @Mock private ContentArchiveService contentArchiveService;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;
    @Mock private DispositionScheduleRepository dispositionScheduleRepository;
    @Mock private RecordsManagementService recordsManagementService;

    private ArchivePolicyService archivePolicyService;

    @BeforeEach
    void setUp() {
        archivePolicyService = new ArchivePolicyService(
            archivePolicyRepository,
            folderRepository,
            nodeRepository,
            securityService,
            contentArchiveService,
            tenantWorkspaceScopeService,
            recordsManagementService
        );
        ReflectionTestUtils.setField(archivePolicyService, "dispositionScheduleRepository", dispositionScheduleRepository);
    }

    @Test
    @DisplayName("upsertPolicy rejects active disposition schedule on same folder")
    void upsertPolicyRejectsActiveDispositionSchedule() {
        UUID folderId = UUID.randomUUID();
        Folder folder = new Folder();
        folder.setId(folderId);
        folder.setName("Finance");
        folder.setPath("/Finance");
        folder.setArchiveStatus(Node.ArchiveStatus.LIVE);

        DispositionSchedule schedule = new DispositionSchedule();
        schedule.setId(UUID.randomUUID());
        schedule.setFolder(folder);
        schedule.setEnabled(true);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(tenantWorkspaceScopeService.isPathVisible("/Finance")).thenReturn(true);
        when(dispositionScheduleRepository.findByFolderId(folderId)).thenReturn(Optional.of(schedule));

        assertThrows(
            IllegalOperationException.class,
            () -> archivePolicyService.upsertPolicy(
                folderId,
                new ArchivePolicyService.ArchivePolicyUpsertRequest(true, 90, Node.ArchiveStoreTier.COLD, true, 100)
            )
        );
    }

    @Test
    @DisplayName("upsertPolicy rejects file plan folders")
    void upsertPolicyRejectsFilePlanFolder() {
        UUID folderId = UUID.randomUUID();
        Folder folder = new Folder();
        folder.setId(folderId);
        folder.setName("Corporate File Plan");
        folder.setPath("/Corporate File Plan");
        folder.setFolderType(Folder.FolderType.FILE_PLAN);
        folder.setArchiveStatus(Node.ArchiveStatus.LIVE);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(tenantWorkspaceScopeService.isPathVisible("/Corporate File Plan")).thenReturn(true);
        when(recordsManagementService.isFilePlanFolder(folder)).thenReturn(true);

        assertThrows(
            IllegalOperationException.class,
            () -> archivePolicyService.upsertPolicy(
                folderId,
                new ArchivePolicyService.ArchivePolicyUpsertRequest(true, 90, Node.ArchiveStoreTier.COLD, true, 100)
            )
        );
    }
}
