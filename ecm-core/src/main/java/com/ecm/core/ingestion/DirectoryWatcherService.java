package com.ecm.core.ingestion;

import com.ecm.core.entity.Document;
import com.ecm.core.config.TenantContext;
import com.ecm.core.service.DocumentUploadService;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.TenantContextResolverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Directory Watcher Service (Hot Folder)
 * 
 * Monitors a local directory for new files and automatically ingests them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DirectoryWatcherService {

    private final DocumentUploadService uploadService;
    private final FolderService folderService;
    private final TenantContextResolverService tenantContextResolverService;

    @Value("${ecm.ingestion.watch-folder:/var/ecm/import}")
    private String watchFolderPath;

    @Value("${ecm.ingestion.enabled:true}")
    private boolean enabled;
    
    @Value("${ecm.ingestion.target-folder-id:}")
    private String targetFolderIdStr;

    private UUID targetFolderId;

    @PostConstruct
    public void init() {
        if (!enabled) return;

        try {
            Path path = Paths.get(watchFolderPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("Created watch folder: {}", watchFolderPath);
            }
            
            if (targetFolderIdStr != null && !targetFolderIdStr.isBlank()) {
                targetFolderId = UUID.fromString(targetFolderIdStr);
            } else {
                // Default to first root folder or create "Inbox"
                // For MVP, we'll log a warning if not set
                log.warn("Target folder ID for ingestion not set. Files will go to root.");
            }
            
        } catch (Exception e) {
            log.error("Failed to initialize directory watcher", e);
        }
    }

    /**
     * Poll the directory for new files every 10 seconds.
     * (Polling is often more robust than WatchService across different OS/Docker mounts)
     */
    @Scheduled(fixedDelay = 10000)
    public void pollDirectory() {
        if (!enabled) return;

        try (Stream<Path> paths = Files.list(Paths.get(watchFolderPath))) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> !p.getFileName().toString().endsWith(".error"))
                 .forEach(this::processFile);
        } catch (IOException e) {
            log.error("Error polling watch folder", e);
        }
    }

    private void processFile(Path file) {
        log.info("Detected new file in watch folder: {}", file.getFileName());

        try {
            // Wait briefly to ensure file write is complete (simple debounce)
            Thread.sleep(500); 
            
            String contentType = Files.probeContentType(file);
            byte[] content = Files.readAllBytes(file);
            
            MultipartFile multipartFile = new MockMultipartFile(
                file.getFileName().toString(),
                file.getFileName().toString(),
                contentType,
                content
            );

            if (!ingestUnderResolvedTenant(multipartFile, file.getFileName().toString())) {
                moveToError(file);
                return;
            }

            // Delete processed file
            Files.delete(file);
            log.info("Successfully ingested and deleted: {}", file.getFileName());

        } catch (Exception e) {
            log.error("Failed to ingest file: {}", file.getFileName(), e);
            moveToError(file);
        }
    }

    /**
     * Resolve the owning tenant from the configured target folder, set TenantContext for the upload,
     * then restore the caller's previous context (so a manual/request-thread trigger keeps its tenant;
     * under the scheduler the previous context is empty, equivalent to clear). Returns {@code false}
     * (caller skips → .error) instead of writing into a system
     * root when there is no tenant target — this scheduler thread has no request tenant, and a silent
     * no-quota/no-tenant system write is exactly what Q2b rejects:
     * <ul>
     *   <li>{@code ecm.ingestion.target-folder-id} not configured, or</li>
     *   <li>target folder not found / not under any enabled tenant root.</li>
     * </ul>
     * Package-private for unit testing.
     */
    boolean ingestUnderResolvedTenant(MultipartFile multipartFile, String label) throws IOException {
        if (targetFolderId == null) {
            log.warn("Skipping ingest of {}: ecm.ingestion.target-folder-id is not set (no tenant target)", label);
            return false;
        }
        TenantContextResolverService.TenantResolution tenant =
            tenantContextResolverService.resolveTenantForTargetFolder(targetFolderId);
        if (tenant.isReject()) {
            // Tenants exist but the watch target folder is under none — a configuration error. Skip
            // (caller moves the file to .error) rather than writing untenanted. The resolver returns
            // instead of throwing, so no surrounding transaction is poisoned.
            log.warn("Skipping ingest of {}: target folder {} is not under any enabled tenant root",
                label, targetFolderId);
            return false;
        }
        TenantContext.Snapshot previous = TenantContext.capture();
        try {
            if (tenant.isResolved()) {
                TenantContext.setCurrentTenantDomain(tenant.tenantDomain());
                TenantContext.setCurrentTenantRootNodeId(tenant.rootNodeId());
            }
            // else NO_TENANT_SYSTEM: legacy single-tenant deployment — write untenanted (no scope).
            uploadService.uploadDocument(multipartFile, targetFolderId, null);
            return true;
        } finally {
            TenantContext.restore(previous);
        }
    }

    private void moveToError(Path file) {
        try {
            Path errorFile = file.resolveSibling(file.getFileName() + ".error");
            Files.move(file, errorFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.error("Failed to rename error file: {}", file.getFileName(), ex);
        }
    }
}
