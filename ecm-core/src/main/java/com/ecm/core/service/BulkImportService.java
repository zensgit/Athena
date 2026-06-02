package com.ecm.core.service;

import com.ecm.core.config.TenantAwareExecutor;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.ImportJob;
import com.ecm.core.entity.ImportJob.ConflictPolicy;
import com.ecm.core.entity.ImportJob.ImportJobStatus;
import com.ecm.core.entity.Node;
import com.ecm.core.pipeline.PipelineResult;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.repository.ImportJobRepository;
import com.ecm.core.repository.NodeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Autowired;

@Service
@Slf4j
public class BulkImportService {

    private final ImportJobRepository importJobRepository;
    private final DocumentUploadService documentUploadService;
    private final FolderService folderService;
    private final NodeRepository nodeRepository;
    private final NodeService nodeService;
    private final SecurityService securityService;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;
    private final RecordsManagementService recordsManagementService;
    private final Executor importExecutor;

    @Autowired
    public BulkImportService(
        ImportJobRepository importJobRepository,
        DocumentUploadService documentUploadService,
        FolderService folderService,
        NodeRepository nodeRepository,
        NodeService nodeService,
        SecurityService securityService,
        TenantWorkspaceScopeService tenantWorkspaceScopeService,
        RecordsManagementService recordsManagementService
    ) {
        this(
            importJobRepository,
            documentUploadService,
            folderService,
            nodeRepository,
            nodeService,
            securityService,
            tenantWorkspaceScopeService,
            recordsManagementService,
            Executors.newCachedThreadPool()
        );
    }

    BulkImportService(
        ImportJobRepository importJobRepository,
        DocumentUploadService documentUploadService,
        FolderService folderService,
        NodeRepository nodeRepository,
        NodeService nodeService,
        SecurityService securityService,
        TenantWorkspaceScopeService tenantWorkspaceScopeService,
        RecordsManagementService recordsManagementService,
        Executor importExecutor
    ) {
        this.importJobRepository = importJobRepository;
        this.documentUploadService = documentUploadService;
        this.folderService = folderService;
        this.nodeRepository = nodeRepository;
        this.nodeService = nodeService;
        this.securityService = securityService;
        this.tenantWorkspaceScopeService = tenantWorkspaceScopeService;
        this.recordsManagementService = recordsManagementService;
        this.importExecutor = new TenantAwareExecutor(importExecutor);
    }

    public ImportJobDto startImport(
        MultipartFile[] files,
        List<String> relativePaths,
        UUID targetFolderId,
        ConflictPolicy conflictPolicy
    ) throws IOException {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("At least one file is required for bulk import");
        }

        String currentUser = securityService.getCurrentUser();
        UUID effectiveTargetFolderId = resolveTargetFolderId(targetFolderId);
        ImportJob job = new ImportJob();
        job.setUserId(currentUser);
        job.setTargetFolderId(effectiveTargetFolderId);
        job.setConflictPolicy(conflictPolicy != null ? conflictPolicy : ConflictPolicy.SKIP);
        job.setStatus(ImportJobStatus.PENDING);
        job.setTotalFiles(files.length);
        job.setLastMessage("Queued bulk import");
        ImportJob saved = importJobRepository.save(job);

        Path stagingDir = Files.createTempDirectory("athena-bulk-import-");
        List<StagedImportFile> stagedFiles = stageFiles(stagingDir, files, relativePaths);

        try {
            importExecutor.execute(() -> processImportJob(saved.getId(), stagedFiles, stagingDir));
        } catch (RuntimeException e) {
            cleanupStagingDirectory(stagingDir);
            saved.setStatus(ImportJobStatus.FAILED);
            saved.setCompletedAt(LocalDateTime.now());
            saved.setLastMessage("Failed to start bulk import");
            appendError(saved, "Failed to start bulk import: " + e.getMessage());
            importJobRepository.save(saved);
            throw e;
        }
        return ImportJobDto.from(saved);
    }

    public ImportJobDto getJob(UUID jobId) {
        ImportJob job = requireAccessibleJob(jobId);
        return ImportJobDto.from(job);
    }

    public Page<ImportJobDto> listJobs(Pageable pageable) {
        boolean scopedTenant = tenantWorkspaceScopeService.hasScopedTenantWorkspace();
        Page<ImportJob> page = securityService.hasRole("ROLE_ADMIN")
            ? importJobRepository.findAllByOrderByCreatedAtDesc(scopedTenant ? Pageable.unpaged() : pageable)
            : importJobRepository.findByUserIdOrderByCreatedAtDesc(securityService.getCurrentUser(), scopedTenant ? Pageable.unpaged() : pageable);
        if (!scopedTenant) {
            return page.map(ImportJobDto::from);
        }
        List<ImportJobDto> visible = page.getContent().stream()
            .filter(this::isJobVisible)
            .map(ImportJobDto::from)
            .toList();
        if (!pageable.isPaged()) {
            return new PageImpl<>(visible);
        }
        int start = Math.min((int) pageable.getOffset(), visible.size());
        int end = Math.min(start + pageable.getPageSize(), visible.size());
        return new PageImpl<>(visible.subList(start, end), pageable, visible.size());
    }

    public ImportJobDto cancelImport(UUID jobId) {
        ImportJob job = requireAccessibleJob(jobId);
        if (job.getStatus() == ImportJobStatus.COMPLETED || job.getStatus() == ImportJobStatus.FAILED) {
            return ImportJobDto.from(job);
        }
        job.setStatus(ImportJobStatus.CANCELED);
        job.setCompletedAt(LocalDateTime.now());
        job.setLastMessage("Bulk import canceled");
        job.setCurrentItemPath(null);
        return ImportJobDto.from(importJobRepository.save(job));
    }

    void processImportJob(UUID jobId, List<StagedImportFile> stagedFiles, Path stagingDir) {
        Map<String, UUID> folderCache = new HashMap<>();

        try {
            ImportJob job = requireJob(jobId);
            job.setStatus(ImportJobStatus.RUNNING);
            job.setStartedAt(LocalDateTime.now());
            job.setLastMessage("Bulk import started");
            job = importJobRepository.save(job);

            folderCache.put("", job.getTargetFolderId());
            stagedFiles.sort(Comparator.comparingInt(file -> pathDepth(file.relativePath())));

            for (StagedImportFile stagedFile : stagedFiles) {
                job = requireJob(jobId);
                if (job.getStatus() == ImportJobStatus.CANCELED) {
                    job.setLastMessage("Bulk import canceled");
                    job.setCurrentItemPath(null);
                    importJobRepository.save(job);
                    return;
                }

                String normalizedRelativePath = normalizeRelativePath(stagedFile.relativePath(), stagedFile.originalFilename());
                job.setCurrentItemPath(normalizedRelativePath);
                job.setLastMessage("Importing " + normalizedRelativePath);
                importJobRepository.save(job);

                try {
                    UUID parentFolderId = resolveParentFolder(job, normalizedRelativePath, folderCache);
                    String requestedName = leafName(normalizedRelativePath);
                    String effectiveName = resolveEffectiveName(job, parentFolderId, requestedName);

                    if (effectiveName == null) {
                        incrementSkipped(job, "Skipped existing file: " + normalizedRelativePath);
                        continue;
                    }

                    PipelineResult result = documentUploadService.uploadDocument(
                        stagedFile.toMultipartFile(effectiveName),
                        parentFolderId,
                        null
                    );

                    if (result.isSuccess()) {
                        incrementImported(job, "Imported " + normalizedRelativePath);
                    } else {
                        incrementFailed(job, "Failed to import " + normalizedRelativePath + formatErrors(result.getErrors()));
                    }
                } catch (Exception e) {
                    incrementFailed(job, "Failed to import " + normalizedRelativePath + ": " + e.getMessage());
                } finally {
                    try {
                        Files.deleteIfExists(stagedFile.path());
                    } catch (IOException cleanupError) {
                        log.debug("Failed to remove staged file {}: {}", stagedFile.path(), cleanupError.getMessage());
                    }
                }
            }

            job = requireJob(jobId);
            if (job.getStatus() != ImportJobStatus.CANCELED) {
                job.setStatus(job.getFailedFiles() > 0 ? ImportJobStatus.FAILED : ImportJobStatus.COMPLETED);
                job.setCompletedAt(LocalDateTime.now());
                job.setCurrentItemPath(null);
                job.setLastMessage(job.getStatus() == ImportJobStatus.COMPLETED
                    ? "Bulk import completed"
                    : "Bulk import completed with failures");
                importJobRepository.save(job);
            }
        } finally {
            cleanupStagingDirectory(stagingDir);
        }
    }

    private List<StagedImportFile> stageFiles(Path stagingDir, MultipartFile[] files, List<String> relativePaths) throws IOException {
        List<StagedImportFile> stagedFiles = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            Path stagedPath = Files.createTempFile(stagingDir, "file-", ".bin");
            file.transferTo(stagedPath);
            String relativePath = relativePaths != null && relativePaths.size() > i
                ? relativePaths.get(i)
                : file.getOriginalFilename();
            stagedFiles.add(new StagedImportFile(
                stagedPath,
                relativePath,
                file.getOriginalFilename(),
                file.getContentType()
            ));
        }
        return stagedFiles;
    }

    private UUID resolveParentFolder(ImportJob job, String relativePath, Map<String, UUID> folderCache) {
        String parentPath = parentPath(relativePath);
        if (folderCache.containsKey(parentPath)) {
            UUID cachedParentId = folderCache.get(parentPath);
            assertCreateAllowedInFolder(cachedParentId, "bulk import into target folder");
            return cachedParentId;
        }

        UUID currentParentId = job.getTargetFolderId();
        assertCreateAllowedInFolder(currentParentId, "bulk import into target folder");
        String[] segments = parentPath.isBlank() ? new String[0] : parentPath.split("/");
        StringBuilder currentPath = new StringBuilder();

        for (String rawSegment : segments) {
            String segment = rawSegment.trim();
            if (segment.isEmpty()) {
                continue;
            }

            if (currentPath.length() > 0) {
                currentPath.append('/');
            }
            currentPath.append(segment);

            UUID cached = folderCache.get(currentPath.toString());
            if (cached != null) {
                currentParentId = cached;
                continue;
            }

            Optional<Node> existing = nodeRepository.findByParentIdAndName(currentParentId, segment);
            if (existing.isPresent()) {
                Node node = existing.get();
                if (!node.isFolder()) {
                    throw new IllegalStateException("Import path segment conflicts with existing document: " + segment);
                }
                assertCreateAllowedInFolder(node, "bulk import into target folder");
                currentParentId = node.getId();
                folderCache.put(currentPath.toString(), currentParentId);
                continue;
            }

            Folder createdFolder = folderService.createFolder(new FolderService.CreateFolderRequest(
                segment,
                null,
                currentParentId,
                Folder.FolderType.GENERAL,
                null,
                null,
                null,
                null,
                true,
                false,
                null
            ));
            currentParentId = createdFolder.getId();
            folderCache.put(currentPath.toString(), currentParentId);
        }

        folderCache.put(parentPath, currentParentId);
        return currentParentId;
    }

    private String resolveEffectiveName(ImportJob job, UUID parentFolderId, String requestedName) {
        Optional<Node> existing = nodeRepository.findByParentIdAndName(parentFolderId, requestedName);
        if (existing.isEmpty()) {
            return requestedName;
        }

        return switch (job.getConflictPolicy()) {
            case SKIP -> null;
            case RENAME -> generateUniqueName(parentFolderId, requestedName);
            case OVERWRITE -> {
                Node existingNode = existing.get();
                if (existingNode.isFolder()) {
                    throw new IllegalStateException("Cannot overwrite existing folder: " + requestedName);
                }
                assertOverwriteAllowed(existingNode, "overwrite existing node via bulk import");
                nodeService.deleteNode(existingNode.getId(), false);
                yield requestedName;
            }
        };
    }

    private void assertCreateAllowedInFolder(UUID folderId, String operation) {
        if (folderId == null) {
            return;
        }
        nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folderId, Node.ArchiveStatus.LIVE)
            .ifPresent(node -> {
                if (recordsManagementService != null) {
                    recordsManagementService.assertCreateInFolderAllowed(node, operation);
                }
            });
    }

    private void assertCreateAllowedInFolder(Node folder, String operation) {
        if (folder != null && recordsManagementService != null) {
            recordsManagementService.assertCreateInFolderAllowed(folder, operation);
        }
    }

    private void assertOverwriteAllowed(Node node, String operation) {
        if (node != null && recordsManagementService != null) {
            recordsManagementService.assertArchiveMutationAllowed(node, operation);
        }
    }

    private String generateUniqueName(UUID parentFolderId, String requestedName) {
        String basename = requestedName;
        String extension = "";
        int dotIndex = requestedName.lastIndexOf('.');
        if (dotIndex > 0) {
            basename = requestedName.substring(0, dotIndex);
            extension = requestedName.substring(dotIndex);
        }

        int attempt = 1;
        String candidate = requestedName;
        while (nodeRepository.findByParentIdAndName(parentFolderId, candidate).isPresent()) {
            candidate = "%s (%d)%s".formatted(basename, attempt, extension);
            attempt++;
        }
        return candidate;
    }

    private void incrementImported(ImportJob job, String message) {
        job.setProcessedFiles(job.getProcessedFiles() + 1);
        job.setImportedFiles(job.getImportedFiles() + 1);
        job.setLastMessage(message);
        importJobRepository.save(job);
    }

    private void incrementSkipped(ImportJob job, String message) {
        job.setProcessedFiles(job.getProcessedFiles() + 1);
        job.setSkippedFiles(job.getSkippedFiles() + 1);
        appendError(job, message);
        job.setLastMessage(message);
        importJobRepository.save(job);
    }

    private void incrementFailed(ImportJob job, String message) {
        job.setProcessedFiles(job.getProcessedFiles() + 1);
        job.setFailedFiles(job.getFailedFiles() + 1);
        appendError(job, message);
        job.setLastMessage(message);
        importJobRepository.save(job);
    }

    private void appendError(ImportJob job, String message) {
        String existing = job.getErrorLog();
        job.setErrorLog(existing == null || existing.isBlank() ? message : existing + "\n" + message);
    }

    private String normalizeRelativePath(String relativePath, String fallbackName) {
        String candidate = relativePath == null || relativePath.isBlank() ? fallbackName : relativePath;
        candidate = candidate == null ? "unnamed" : candidate.trim();
        candidate = candidate.replace('\\', '/');
        while (candidate.startsWith("./")) {
            candidate = candidate.substring(2);
        }
        while (candidate.startsWith("/")) {
            candidate = candidate.substring(1);
        }
        return candidate.isBlank() ? "unnamed" : candidate;
    }

    private String parentPath(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash >= 0 ? relativePath.substring(0, slash) : "";
    }

    private String leafName(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash >= 0 ? relativePath.substring(slash + 1) : relativePath;
    }

    private int pathDepth(String relativePath) {
        return normalizeRelativePath(relativePath, relativePath).split("/").length;
    }

    private String formatErrors(Map<String, String> errors) {
        if (errors == null || errors.isEmpty()) {
            return "";
        }
        return ": " + String.join(", ", errors.values());
    }

    private ImportJob requireAccessibleJob(UUID jobId) {
        ImportJob job = requireJob(jobId);
        if (!isJobVisible(job)) {
            throw new ResourceNotFoundException("Import job not found: " + jobId);
        }
        String currentUser = securityService.getCurrentUser();
        if (!securityService.hasRole("ROLE_ADMIN") && !Objects.equals(job.getUserId(), currentUser)) {
            throw new SecurityException("Cannot access another user's import job");
        }
        return job;
    }

    private UUID resolveTargetFolderId(UUID targetFolderId) {
        if (targetFolderId != null) {
            if (tenantWorkspaceScopeService.hasScopedTenantWorkspace()
                && !tenantWorkspaceScopeService.isNodeVisible(targetFolderId)) {
                throw new ResourceNotFoundException("Target folder not found: " + targetFolderId);
            }
            return targetFolderId;
        }
        if (tenantWorkspaceScopeService.hasScopedTenantWorkspace()) {
            UUID tenantRootNodeId = tenantWorkspaceScopeService.resolveCurrentTenantRootNodeId();
            if (tenantRootNodeId == null || !tenantWorkspaceScopeService.isNodeVisible(tenantRootNodeId)) {
                throw new ResourceNotFoundException("Target folder not found");
            }
            return tenantRootNodeId;
        }
        return null;
    }

    private boolean isJobVisible(ImportJob job) {
        if (!tenantWorkspaceScopeService.hasScopedTenantWorkspace()) {
            return true;
        }
        return job.getTargetFolderId() != null && tenantWorkspaceScopeService.isNodeVisible(job.getTargetFolderId());
    }

    private ImportJob requireJob(UUID jobId) {
        return importJobRepository.findById(jobId)
            .orElseThrow(() -> new NoSuchElementException("Import job not found: " + jobId));
    }

    private void cleanupStagingDirectory(Path stagingDir) {
        if (stagingDir == null) {
            return;
        }
        try (var paths = Files.walk(stagingDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException cleanupError) {
                    log.debug("Failed to delete staged import path {}: {}", path, cleanupError.getMessage());
                }
            });
        } catch (IOException e) {
            log.debug("Failed to walk staged import directory {}: {}", stagingDir, e.getMessage());
        }
    }

    record StagedImportFile(
        Path path,
        String relativePath,
        String originalFilename,
        String contentType
    ) {
        MultipartFile toMultipartFile(String effectiveFilename) {
            return new MultipartFile() {
                @Override
                public String getName() {
                    return "file";
                }

                @Override
                public String getOriginalFilename() {
                    return effectiveFilename;
                }

                @Override
                public String getContentType() {
                    return contentType;
                }

                @Override
                public boolean isEmpty() {
                    try {
                        return Files.size(path) == 0;
                    } catch (IOException e) {
                        return true;
                    }
                }

                @Override
                public long getSize() {
                    try {
                        return Files.size(path);
                    } catch (IOException e) {
                        return 0L;
                    }
                }

                @Override
                public byte[] getBytes() throws IOException {
                    return Files.readAllBytes(path);
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    return Files.newInputStream(path);
                }

                @Override
                public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
                    Files.copy(path, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }

                @Override
                public void transferTo(Path dest) throws IOException, IllegalStateException {
                    Files.copy(path, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            };
        }
    }

    public record ImportJobDto(
        UUID id,
        String userId,
        ImportJobStatus status,
        UUID targetFolderId,
        ConflictPolicy conflictPolicy,
        int totalFiles,
        int processedFiles,
        int importedFiles,
        int skippedFiles,
        int failedFiles,
        String currentItemPath,
        String lastMessage,
        String errorLog,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        public static ImportJobDto from(ImportJob job) {
            return new ImportJobDto(
                job.getId(),
                job.getUserId(),
                job.getStatus(),
                job.getTargetFolderId(),
                job.getConflictPolicy(),
                job.getTotalFiles(),
                job.getProcessedFiles(),
                job.getImportedFiles(),
                job.getSkippedFiles(),
                job.getFailedFiles(),
                job.getCurrentItemPath(),
                job.getLastMessage(),
                job.getErrorLog(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getCreatedAt(),
                job.getUpdatedAt()
            );
        }
    }
}
