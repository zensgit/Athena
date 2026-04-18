package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.ImportJob;
import com.ecm.core.entity.ImportJob.ConflictPolicy;
import com.ecm.core.entity.ImportJob.ImportJobStatus;
import com.ecm.core.entity.Node;
import com.ecm.core.pipeline.PipelineResult;
import com.ecm.core.repository.ImportJobRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkImportServiceTest {

    @Mock private ImportJobRepository importJobRepository;
    @Mock private DocumentUploadService documentUploadService;
    @Mock private FolderService folderService;
    @Mock private NodeRepository nodeRepository;
    @Mock private NodeService nodeService;
    @Mock private SecurityService securityService;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;
    @Mock private RecordsManagementService recordsManagementService;

    private BulkImportService service;
    private Map<UUID, ImportJob> storedJobs;

    @BeforeEach
    void setUp() {
        service = new BulkImportService(
            importJobRepository,
            documentUploadService,
            folderService,
            nodeRepository,
            nodeService,
            securityService,
            tenantWorkspaceScopeService,
            recordsManagementService,
            Runnable::run
        );
        storedJobs = new LinkedHashMap<>();

        lenient().when(securityService.getCurrentUser()).thenReturn("alice");

        lenient().when(importJobRepository.save(any(ImportJob.class))).thenAnswer(invocation -> {
            ImportJob job = invocation.getArgument(0);
            if (job.getId() == null) {
                job.setId(UUID.randomUUID());
                if (job.getCreatedAt() == null) {
                    job.setCreatedAt(LocalDateTime.now());
                }
            }
            job.setUpdatedAt(LocalDateTime.now());
            storedJobs.put(job.getId(), job);
            return job;
        });
        lenient().when(importJobRepository.findById(any(UUID.class))).thenAnswer(invocation ->
            Optional.ofNullable(storedJobs.get(invocation.getArgument(0)))
        );
    }

    @Test
    @DisplayName("startImport skips conflicting file when policy is SKIP")
    void startImportSkipsConflictingFileWhenPolicyIsSkip() throws Exception {
        UUID parentFolderId = UUID.randomUUID();
        when(nodeRepository.findByParentIdAndName(parentFolderId, "report.txt"))
            .thenReturn(Optional.of(existingDocument("report.txt")));

        BulkImportService.ImportJobDto result = service.startImport(
            new MultipartFile[]{multipart("report.txt", "hello")},
            List.of("report.txt"),
            parentFolderId,
            ConflictPolicy.SKIP
        );

        BulkImportService.ImportJobDto refreshed = service.getJob(result.id());
        assertEquals(ImportJobStatus.COMPLETED, refreshed.status());
        assertEquals(1, refreshed.totalFiles());
        assertEquals(1, refreshed.processedFiles());
        assertEquals(1, refreshed.skippedFiles());
        assertEquals(0, refreshed.importedFiles());
        verify(documentUploadService, never()).uploadDocument(any(), any(), any());
    }

    @Test
    @DisplayName("startImport renames conflicting file when policy is RENAME")
    void startImportRenamesConflictingFileWhenPolicyIsRename() throws Exception {
        UUID parentFolderId = UUID.randomUUID();
        when(nodeRepository.findByParentIdAndName(parentFolderId, "report.txt"))
            .thenReturn(Optional.of(existingDocument("report.txt")));
        when(nodeRepository.findByParentIdAndName(parentFolderId, "report (1).txt"))
            .thenReturn(Optional.empty());
        when(documentUploadService.uploadDocument(any(), eq(parentFolderId), isNull()))
            .thenReturn(successResult());

        service.startImport(
            new MultipartFile[]{multipart("report.txt", "hello")},
            List.of("report.txt"),
            parentFolderId,
            ConflictPolicy.RENAME
        );

        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(documentUploadService).uploadDocument(fileCaptor.capture(), eq(parentFolderId), isNull());
        assertEquals("report (1).txt", fileCaptor.getValue().getOriginalFilename());
        verify(nodeService, never()).deleteNode(any(), anyBoolean());
    }

    @Test
    @DisplayName("startImport overwrites conflicting document when policy is OVERWRITE")
    void startImportOverwritesConflictingFileWhenPolicyIsOverwrite() throws Exception {
        UUID parentFolderId = UUID.randomUUID();
        Document existing = existingDocument("report.txt");
        existing.setId(UUID.randomUUID());

        when(nodeRepository.findByParentIdAndName(parentFolderId, "report.txt"))
            .thenReturn(Optional.of(existing));
        when(documentUploadService.uploadDocument(any(), eq(parentFolderId), isNull()))
            .thenReturn(successResult());

        service.startImport(
            new MultipartFile[]{multipart("report.txt", "hello")},
            List.of("report.txt"),
            parentFolderId,
            ConflictPolicy.OVERWRITE
        );

        verify(nodeService).deleteNode(existing.getId(), false);
        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(documentUploadService).uploadDocument(fileCaptor.capture(), eq(parentFolderId), isNull());
        assertEquals("report.txt", fileCaptor.getValue().getOriginalFilename());
    }

    @Test
    @DisplayName("startImport creates missing folder chain for nested paths")
    void startImportCreatesMissingFolderChainForNestedPaths() throws Exception {
        UUID rootFolderId = UUID.randomUUID();
        Folder engineering = folder(UUID.randomUUID(), "engineering");
        Folder quarter = folder(UUID.randomUUID(), "q1");

        when(nodeRepository.findByParentIdAndName(any(), anyString())).thenReturn(Optional.empty());
        when(folderService.createFolder(any(FolderService.CreateFolderRequest.class)))
            .thenReturn(engineering, quarter);
        when(documentUploadService.uploadDocument(any(), eq(quarter.getId()), isNull()))
            .thenReturn(successResult());

        BulkImportService.ImportJobDto result = service.startImport(
            new MultipartFile[]{multipart("budget.xlsx", "sheet")},
            List.of("engineering/q1/budget.xlsx"),
            rootFolderId,
            ConflictPolicy.SKIP
        );

        BulkImportService.ImportJobDto refreshed = service.getJob(result.id());
        assertEquals(ImportJobStatus.COMPLETED, refreshed.status());
        assertEquals(1, refreshed.importedFiles());

        ArgumentCaptor<FolderService.CreateFolderRequest> folderCaptor =
            ArgumentCaptor.forClass(FolderService.CreateFolderRequest.class);
        verify(folderService, times(2)).createFolder(folderCaptor.capture());
        List<FolderService.CreateFolderRequest> requests = folderCaptor.getAllValues();
        assertEquals("engineering", requests.get(0).name());
        assertEquals(rootFolderId, requests.get(0).parentId());
        assertEquals("q1", requests.get(1).name());
        assertEquals(engineering.getId(), requests.get(1).parentId());
        verify(documentUploadService).uploadDocument(any(), eq(quarter.getId()), isNull());
    }

    @Test
    @DisplayName("startImport rejects upload into file plan target folder")
    void startImportRejectsUploadIntoFilePlanTargetFolder() throws Exception {
        UUID targetFolderId = UUID.randomUUID();
        Folder filePlan = folder(targetFolderId, "Corporate File Plan");
        filePlan.setPath("/Corporate File Plan");
        filePlan.setFolderType(Folder.FolderType.FILE_PLAN);

        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(targetFolderId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(filePlan));
        doThrow(new com.ecm.core.exception.IllegalOperationException(
            "Cannot bulk import into target folder because target folder 'Corporate File Plan' is a file plan"
        )).when(recordsManagementService).assertCreateInFolderAllowed(filePlan, "bulk import into target folder");

        BulkImportService.ImportJobDto result = service.startImport(
            new MultipartFile[]{multipart("report.txt", "hello")},
            List.of("report.txt"),
            targetFolderId,
            ConflictPolicy.SKIP
        );

        BulkImportService.ImportJobDto refreshed = service.getJob(result.id());
        assertEquals(ImportJobStatus.FAILED, refreshed.status());
        assertEquals(1, refreshed.failedFiles());
        assertTrue(refreshed.errorLog().contains("Corporate File Plan"));
        verify(documentUploadService, never()).uploadDocument(any(), any(), any());
    }

    @Test
    @DisplayName("startImport rejects overwrite when existing node is RM governed")
    void startImportRejectsOverwriteWhenExistingNodeIsRmGoverned() throws Exception {
        UUID parentFolderId = UUID.randomUUID();
        Folder generalFolder = folder(parentFolderId, "General");
        generalFolder.setPath("/General");
        Document existing = existingDocument("report.txt");
        existing.setId(UUID.randomUUID());
        existing.setPath("/Corporate File Plan/report.txt");

        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(parentFolderId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(generalFolder));
        when(nodeRepository.findByParentIdAndName(parentFolderId, "report.txt"))
            .thenReturn(Optional.of(existing));
        doThrow(new com.ecm.core.exception.IllegalOperationException(
            "Cannot overwrite existing node via bulk import because node 'report.txt' is governed by file plan 'Corporate File Plan'"
        )).when(recordsManagementService).assertArchiveMutationAllowed(existing, "overwrite existing node via bulk import");

        BulkImportService.ImportJobDto result = service.startImport(
            new MultipartFile[]{multipart("report.txt", "hello")},
            List.of("report.txt"),
            parentFolderId,
            ConflictPolicy.OVERWRITE
        );

        BulkImportService.ImportJobDto refreshed = service.getJob(result.id());
        assertEquals(ImportJobStatus.FAILED, refreshed.status());
        assertEquals(1, refreshed.failedFiles());
        assertTrue(refreshed.errorLog().contains("governed by file plan"));
        verify(nodeService, never()).deleteNode(any(), anyBoolean());
        verify(documentUploadService, never()).uploadDocument(any(), any(), any());
    }

    @Test
    @DisplayName("startImport without target folder defaults to current tenant root workspace")
    void startImportWithoutTargetUsesTenantRootWorkspace() throws Exception {
        UUID tenantRootId = UUID.randomUUID();
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
        when(tenantWorkspaceScopeService.resolveCurrentTenantRootNodeId()).thenReturn(tenantRootId);
        when(tenantWorkspaceScopeService.isNodeVisible(tenantRootId)).thenReturn(true);
        when(nodeRepository.findByParentIdAndName(tenantRootId, "report.txt")).thenReturn(Optional.empty());
        when(documentUploadService.uploadDocument(any(), eq(tenantRootId), isNull())).thenReturn(successResult());

        BulkImportService.ImportJobDto result = service.startImport(
            new MultipartFile[]{multipart("report.txt", "hello")},
            List.of("report.txt"),
            null,
            ConflictPolicy.SKIP
        );

        assertEquals(tenantRootId, result.targetFolderId());
        verify(documentUploadService).uploadDocument(any(), eq(tenantRootId), isNull());
    }

    @Test
    @DisplayName("getJob hides import jobs outside current tenant workspace")
    void getJobHidesForeignTenantJob() {
        UUID jobId = UUID.randomUUID();
        UUID foreignFolderId = UUID.randomUUID();
        ImportJob job = new ImportJob();
        job.setId(jobId);
        job.setUserId("alice");
        job.setTargetFolderId(foreignFolderId);
        job.setStatus(ImportJobStatus.PENDING);
        storedJobs.put(jobId, job);

        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
        when(tenantWorkspaceScopeService.isNodeVisible(foreignFolderId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.getJob(jobId));
    }

    private MockMultipartFile multipart(String filename, String content) {
        return new MockMultipartFile("files", filename, "text/plain", content.getBytes());
    }

    private PipelineResult successResult() {
        return PipelineResult.builder()
            .success(true)
            .documentId(UUID.randomUUID())
            .errors(Map.of())
            .build();
    }

    private Document existingDocument(String name) {
        Document document = new Document();
        document.setName(name);
        document.setPath("/" + name);
        return document;
    }

    private Folder folder(UUID id, String name) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setPath("/" + name);
        return folder;
    }
}
