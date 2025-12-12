package com.ecm.core.ingestion;

import com.ecm.core.entity.Document;
import com.ecm.core.service.DocumentUploadService;
import com.ecm.core.service.FolderService;
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

            uploadService.uploadDocument(multipartFile, targetFolderId, null);
            
            // Delete processed file
            Files.delete(file);
            log.info("Successfully ingested and deleted: {}", file.getFileName());

        } catch (Exception e) {
            log.error("Failed to ingest file: {}", file.getFileName(), e);
            // Move to error folder? For now just leave it or rename to .error
            try {
                Path errorFile = file.resolveSibling(file.getFileName() + ".error");
                Files.move(file, errorFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                log.error("Failed to rename error file", ex);
            }
        }
    }
}
