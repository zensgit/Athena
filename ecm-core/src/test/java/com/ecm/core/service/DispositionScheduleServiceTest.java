package com.ecm.core.service;

import com.ecm.core.entity.ArchivePolicy;
import com.ecm.core.entity.DispositionActionExecution;
import com.ecm.core.entity.DispositionSchedule;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.exception.IllegalOperationException;
import com.ecm.core.repository.ArchivePolicyRepository;
import com.ecm.core.repository.DispositionActionExecutionRepository;
import com.ecm.core.repository.DispositionScheduleRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispositionScheduleServiceTest {

    @Mock private DispositionScheduleRepository dispositionScheduleRepository;
    @Mock private DispositionActionExecutionRepository dispositionActionExecutionRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private ArchivePolicyRepository archivePolicyRepository;
    @Mock private SecurityService securityService;
    @Mock private ContentArchiveService contentArchiveService;
    @Mock private DispositionActionExecutorService dispositionActionExecutorService;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;
    @Mock private LegalHoldService legalHoldService;
    @Mock private RecordsManagementService recordsManagementService;

    private DispositionScheduleService dispositionScheduleService;

    @BeforeEach
    void setUp() {
        dispositionScheduleService = new DispositionScheduleService(
            dispositionScheduleRepository,
            dispositionActionExecutionRepository,
            folderRepository,
            nodeRepository,
            archivePolicyRepository,
            securityService,
            contentArchiveService,
            dispositionActionExecutorService,
            tenantWorkspaceScopeService,
            legalHoldService,
            recordsManagementService
        );
        org.mockito.Mockito.lenient().when(tenantWorkspaceScopeService.isPathVisible(anyString())).thenReturn(true);
        org.mockito.Mockito.lenient().when(recordsManagementService.isDeclaredRecord(any(Node.class))).thenReturn(true);
        org.mockito.Mockito.lenient().when(recordsManagementService.isFilePlanFolder(any(Folder.class))).thenAnswer(invocation -> {
            Folder folder = invocation.getArgument(0);
            return folder != null && folder.getFolderType() == Folder.FolderType.FILE_PLAN;
        });
    }

    @Test
    @DisplayName("dryRunSchedule groups cutoff archive and destroy candidates")
    void dryRunScheduleGroupsCandidates() {
        UUID folderId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        Folder folder = folder(folderId, "/Finance");
        DispositionSchedule schedule = schedule(scheduleId, folder);

        Document cutoffDoc = document(UUID.randomUUID(), "/Finance/cutoff.pdf", Node.ArchiveStatus.LIVE);
        cutoffDoc.setLastModifiedDate(LocalDateTime.now().minusDays(95));

        Document archiveDoc = document(UUID.randomUUID(), "/Finance/archive.pdf", Node.ArchiveStatus.LIVE);
        archiveDoc.setLastModifiedDate(LocalDateTime.now().minusDays(40));

        Document destroyDoc = document(UUID.randomUUID(), "/Finance/destroy.pdf", Node.ArchiveStatus.ARCHIVED);
        destroyDoc.setLastModifiedDate(LocalDateTime.now().minusDays(20));

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(tenantWorkspaceScopeService.isPathVisible("/Finance")).thenReturn(true);
        when(dispositionScheduleRepository.findByFolderId(folderId)).thenReturn(Optional.of(schedule));
        when(nodeRepository.findByPathPrefix("/Finance/")).thenReturn(List.of(cutoffDoc, archiveDoc, destroyDoc));
        when(dispositionActionExecutionRepository.findByScheduleIdAndStatusOrderByExecutedAtDesc(
            scheduleId, DispositionActionExecution.ExecutionStatus.SUCCESS
        )).thenReturn(List.of(
            successExecution(schedule, archiveDoc, DispositionActionExecution.ActionType.CUTOFF, LocalDateTime.now().minusDays(10)),
            successExecution(schedule, destroyDoc, DispositionActionExecution.ActionType.CUTOFF, LocalDateTime.now().minusDays(12)),
            successExecution(schedule, destroyDoc, DispositionActionExecution.ActionType.ARCHIVE, LocalDateTime.now().minusDays(8))
        ));
        when(legalHoldService.findBlockingActiveHolds(destroyDoc)).thenReturn(List.of(
            new LegalHoldService.BlockingHoldDto(UUID.randomUUID(), "Matter A", destroyDoc.getId(), destroyDoc.getName(), destroyDoc.getPath())
        ));

        DispositionScheduleService.DispositionDryRunDto result = dispositionScheduleService.dryRunSchedule(folderId, null);

        assertEquals(1, result.cutoffCount());
        assertEquals(1, result.archiveCount());
        assertEquals(1, result.destroyCount());
        assertEquals("Matter A", result.candidates().stream()
            .filter(candidate -> candidate.actionType().equals("DESTROY"))
            .findFirst()
            .orElseThrow()
            .blockedByHoldNames());
    }

    @Test
    @DisplayName("executeSchedule records archive success and destroy block")
    void executeScheduleRecordsArchiveSuccessAndDestroyBlock() {
        UUID folderId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        Folder folder = folder(folderId, "/Finance");
        DispositionSchedule schedule = schedule(scheduleId, folder);

        Document archiveDoc = document(UUID.randomUUID(), "/Finance/archive.pdf", Node.ArchiveStatus.LIVE);
        archiveDoc.setLastModifiedDate(LocalDateTime.now().minusDays(40));

        Document destroyDoc = document(UUID.randomUUID(), "/Finance/destroy.pdf", Node.ArchiveStatus.ARCHIVED);
        destroyDoc.setLastModifiedDate(LocalDateTime.now().minusDays(20));

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(tenantWorkspaceScopeService.isPathVisible("/Finance")).thenReturn(true);
        when(dispositionScheduleRepository.findByFolderId(folderId)).thenReturn(Optional.of(schedule));
        when(nodeRepository.findByPathPrefix("/Finance/")).thenReturn(List.of(archiveDoc, destroyDoc));
        when(dispositionActionExecutionRepository.findByScheduleIdAndStatusOrderByExecutedAtDesc(
            scheduleId, DispositionActionExecution.ExecutionStatus.SUCCESS
        )).thenReturn(List.of(
            successExecution(schedule, archiveDoc, DispositionActionExecution.ActionType.CUTOFF, LocalDateTime.now().minusDays(10)),
            successExecution(schedule, destroyDoc, DispositionActionExecution.ActionType.CUTOFF, LocalDateTime.now().minusDays(12)),
            successExecution(schedule, destroyDoc, DispositionActionExecution.ActionType.ARCHIVE, LocalDateTime.now().minusDays(8))
        ));
        when(contentArchiveService.archiveNodeByPolicy(archiveDoc.getId(), Node.ArchiveStoreTier.COLD, "admin"))
            .thenReturn(new ContentArchiveService.ArchiveMutationDto(
                archiveDoc.getId(),
                archiveDoc.getName(),
                Node.ArchiveStatus.ARCHIVED,
                Node.ArchiveStoreTier.COLD,
                LocalDateTime.now(),
                "admin",
                1
            ));
        doThrow(new IllegalOperationException("held")).when(dispositionActionExecutorService)
            .destroyNodeByDisposition(destroyDoc.getId(), "admin");
        when(dispositionActionExecutionRepository.save(any(DispositionActionExecution.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(dispositionScheduleRepository.save(any(DispositionSchedule.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        DispositionScheduleService.DispositionExecutionDto result = dispositionScheduleService.executeSchedule(folderId);

        assertEquals(1, result.archiveCandidateCount());
        assertEquals(1, result.archivedNodeCount());
        assertEquals(1, result.destroyCandidateCount());
        assertEquals(1, result.blockedCount());
        assertEquals(0, result.failureCount());

        ArgumentCaptor<DispositionActionExecution> executionCaptor = ArgumentCaptor.forClass(DispositionActionExecution.class);
        verify(dispositionActionExecutionRepository, org.mockito.Mockito.times(2)).save(executionCaptor.capture());
        assertEquals(
            List.of(
                DispositionActionExecution.ExecutionStatus.SUCCESS,
                DispositionActionExecution.ExecutionStatus.BLOCKED
            ),
            executionCaptor.getAllValues().stream().map(DispositionActionExecution::getStatus).toList()
        );
    }

    @Test
    @DisplayName("upsertSchedule rejects active archive policy on same folder")
    void upsertScheduleRejectsActiveArchivePolicy() {
        UUID folderId = UUID.randomUUID();
        Folder folder = folder(folderId, "/Finance");
        ArchivePolicy policy = new ArchivePolicy();
        policy.setFolder(folder);
        policy.setEnabled(true);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(tenantWorkspaceScopeService.isPathVisible("/Finance")).thenReturn(true);
        when(archivePolicyRepository.findByFolderId(folderId)).thenReturn(Optional.of(policy));

        assertThrows(
            IllegalOperationException.class,
            () -> dispositionScheduleService.upsertSchedule(
                folderId,
                new DispositionScheduleService.DispositionScheduleUpsertRequest(
                    true,
                    true,
                    90,
                    30,
                    30,
                    Node.ArchiveStoreTier.COLD,
                    100
                )
            )
        );
    }

    @Test
    @DisplayName("upsertSchedule rejects non file-plan folders")
    void upsertScheduleRejectsNonFilePlanFolder() {
        UUID folderId = UUID.randomUUID();
        Folder folder = folder(folderId, "/Finance");
        folder.setFolderType(Folder.FolderType.GENERAL);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(tenantWorkspaceScopeService.isPathVisible("/Finance")).thenReturn(true);

        IllegalOperationException ex = assertThrows(
            IllegalOperationException.class,
            () -> dispositionScheduleService.upsertSchedule(
                folderId,
                new DispositionScheduleService.DispositionScheduleUpsertRequest(
                    true,
                    true,
                    90,
                    30,
                    30,
                    Node.ArchiveStoreTier.COLD,
                    100
                )
            )
        );

        assertEquals("Disposition schedules require a FILE_PLAN folder: Finance", ex.getMessage());
    }

    private DispositionSchedule schedule(UUID id, Folder folder) {
        DispositionSchedule schedule = new DispositionSchedule();
        schedule.setId(id);
        schedule.setFolder(folder);
        schedule.setEnabled(true);
        schedule.setIncludeSubfolders(true);
        schedule.setCutoffAfterDays(90);
        schedule.setArchiveAfterCutoffDays(5);
        schedule.setDestroyAfterArchiveDays(5);
        schedule.setArchiveStorageTier(Node.ArchiveStoreTier.COLD);
        schedule.setMaxCandidatesPerAction(50);
        return schedule;
    }

    private Folder folder(UUID id, String path) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName("Finance");
        folder.setPath(path);
        folder.setFolderType(Folder.FolderType.FILE_PLAN);
        folder.setArchiveStatus(Node.ArchiveStatus.LIVE);
        folder.setDeleted(false);
        return folder;
    }

    private Document document(UUID id, String path, Node.ArchiveStatus archiveStatus) {
        Document document = new Document();
        document.setId(id);
        document.setName(path.substring(path.lastIndexOf('/') + 1));
        document.setPath(path);
        document.setArchiveStatus(archiveStatus);
        document.setDeleted(false);
        document.addAspect(RecordsManagementService.RECORD_ASPECT);
        return document;
    }

    private DispositionActionExecution successExecution(
        DispositionSchedule schedule,
        Node node,
        DispositionActionExecution.ActionType actionType,
        LocalDateTime executedAt
    ) {
        DispositionActionExecution execution = new DispositionActionExecution();
        execution.setSchedule(schedule);
        execution.setActionType(actionType);
        execution.setStatus(DispositionActionExecution.ExecutionStatus.SUCCESS);
        execution.setNodeId(node.getId());
        execution.setNodeName(node.getName());
        execution.setNodeType(node.getNodeType().name());
        execution.setNodePath(node.getPath());
        execution.setAffectedNodeCount(1);
        execution.setActor("admin");
        execution.setExecutedAt(executedAt);
        return execution;
    }
}
