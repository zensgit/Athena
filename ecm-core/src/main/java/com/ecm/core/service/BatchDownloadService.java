package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission;
import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchDownloadService {

    private final NodeRepository nodeRepository;
    private final ContentService contentService;
    private final SecurityService securityService;
    private final FolderService folderService;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;

    /**
     * Streams multiple nodes (documents and folders) into a ZIP output stream.
     *
     * @param nodeIds IDs of nodes to download
     * @param zipOut  The output stream to write the ZIP to
     */
    @Transactional(readOnly = true)
    public void streamNodesAsZip(List<UUID> nodeIds, ZipOutputStream zipOut) {
        writeNodesAsZip(nodeIds, zipOut, BatchDownloadProgressListener.noop());
    }

    @Transactional(readOnly = true)
    public BatchDownloadManifest inspectNodes(List<UUID> nodeIds) {
        BatchDownloadPreflightSummary preflight = inspectNodesPreflight(nodeIds);
        return new BatchDownloadManifest(preflight.includedFileCount(), preflight.includedBytes());
    }

    @Transactional(readOnly = true)
    public BatchDownloadPreflightSummary inspectNodesPreflight(List<UUID> requestedNodeIds) {
        List<UUID> sanitizedNodeIds = requestedNodeIds == null
            ? List.of()
            : requestedNodeIds.stream()
                .filter(Objects::nonNull)
                .toList();
        List<UUID> distinctNodeIds = sanitizedNodeIds.stream().distinct().toList();
        int duplicateCount = Math.max(0, sanitizedNodeIds.size() - distinctNodeIds.size());

        List<UUID> includedNodeIds = new ArrayList<>();
        List<BatchDownloadPreflightItem> items = new ArrayList<>();
        int includedNodeCount = 0;
        int includedFileCount = 0;
        long includedBytes = 0L;
        int missingCount = 0;
        int deletedCount = 0;
        int forbiddenCount = 0;
        int emptyFolderCount = 0;

        for (UUID nodeId : distinctNodeIds) {
            Node node = nodeRepository.findById(nodeId).orElse(null);
            if (node == null) {
                missingCount += 1;
                items.add(new BatchDownloadPreflightItem(
                    nodeId,
                    null,
                    null,
                    BatchDownloadPreflightOutcome.MISSING,
                    0,
                    0L,
                    "Node not found"
                ));
                continue;
            }
            if (!isNodeVisible(node)) {
                missingCount += 1;
                items.add(new BatchDownloadPreflightItem(
                    nodeId,
                    null,
                    null,
                    BatchDownloadPreflightOutcome.MISSING,
                    0,
                    0L,
                    "Node not found"
                ));
                continue;
            }
            if (node.isDeleted()) {
                deletedCount += 1;
                items.add(new BatchDownloadPreflightItem(
                    nodeId,
                    node.getName(),
                    node.getNodeType().name(),
                    BatchDownloadPreflightOutcome.DELETED,
                    0,
                    0L,
                    "Node is deleted"
                ));
                continue;
            }
            if (!securityService.hasPermission(node, Permission.PermissionType.READ)) {
                forbiddenCount += 1;
                items.add(new BatchDownloadPreflightItem(
                    nodeId,
                    node.getName(),
                    node.getNodeType().name(),
                    BatchDownloadPreflightOutcome.FORBIDDEN,
                    0,
                    0L,
                    "Read permission required"
                ));
                continue;
            }

            BatchDownloadManifest manifest = inspectNodeManifest(node);
            if (manifest.totalFiles() <= 0) {
                emptyFolderCount += 1;
                items.add(new BatchDownloadPreflightItem(
                    nodeId,
                    node.getName(),
                    node.getNodeType().name(),
                    BatchDownloadPreflightOutcome.EMPTY_FOLDER,
                    0,
                    0L,
                    node instanceof Folder
                        ? "Folder contains no readable files"
                        : "Node produced no readable files"
                ));
                continue;
            }

            includedNodeIds.add(nodeId);
            includedNodeCount += 1;
            includedFileCount += manifest.totalFiles();
            includedBytes += manifest.totalBytes();
            items.add(new BatchDownloadPreflightItem(
                nodeId,
                node.getName(),
                node.getNodeType().name(),
                BatchDownloadPreflightOutcome.INCLUDED,
                manifest.totalFiles(),
                manifest.totalBytes(),
                node instanceof Folder
                    ? String.format("Included %d readable file(s) from folder", manifest.totalFiles())
                    : "Included document"
            ));
        }

        int skippedCount = duplicateCount + missingCount + deletedCount + forbiddenCount + emptyFolderCount;
        List<String> warnings = new ArrayList<>();
        if (duplicateCount > 0) {
            warnings.add(String.format("Skipped %d duplicate node reference(s)", duplicateCount));
        }
        if (missingCount > 0) {
            warnings.add(String.format("%d node(s) were not found", missingCount));
        }
        if (deletedCount > 0) {
            warnings.add(String.format("%d node(s) are deleted", deletedCount));
        }
        if (forbiddenCount > 0) {
            warnings.add(String.format("%d node(s) are not readable", forbiddenCount));
        }
        if (emptyFolderCount > 0) {
            warnings.add(String.format("%d folder(s) contained no readable files", emptyFolderCount));
        }

        boolean executable = includedFileCount > 0;
        BatchDownloadPreflightDecision decision = resolveDecision(executable, skippedCount);
        BatchDownloadPreflightPrimaryReason primaryReason = resolvePrimaryReason(
            executable,
            duplicateCount,
            missingCount,
            deletedCount,
            forbiddenCount,
            emptyFolderCount
        );
        String message;
        if (!executable) {
            message = "No readable files available for batch download";
        } else if (skippedCount > 0) {
            message = String.format(
                "Ready to download %d file(s) from %d node(s); skipped %d item(s) during preflight",
                includedFileCount,
                includedNodeCount,
                skippedCount
            );
        } else {
            message = String.format(
                "Ready to download %d file(s) from %d node(s)",
                includedFileCount,
                includedNodeCount
            );
        }

        return new BatchDownloadPreflightSummary(
            sanitizedNodeIds.size(),
            distinctNodeIds.size(),
            duplicateCount,
            List.copyOf(includedNodeIds),
            includedNodeCount,
            includedFileCount,
            includedBytes,
            missingCount,
            deletedCount,
            forbiddenCount,
            emptyFolderCount,
            skippedCount,
            executable,
            decision,
            primaryReason,
            message,
            List.copyOf(warnings),
            List.copyOf(items)
        );
    }

    private BatchDownloadPreflightDecision resolveDecision(boolean executable, int skippedCount) {
        if (!executable) {
            return BatchDownloadPreflightDecision.BLOCKED;
        }
        if (skippedCount > 0) {
            return BatchDownloadPreflightDecision.PARTIAL;
        }
        return BatchDownloadPreflightDecision.READY;
    }

    private BatchDownloadPreflightPrimaryReason resolvePrimaryReason(
        boolean executable,
        int duplicateCount,
        int missingCount,
        int deletedCount,
        int forbiddenCount,
        int emptyFolderCount
    ) {
        if (!executable) {
            if (forbiddenCount > 0) {
                return BatchDownloadPreflightPrimaryReason.FORBIDDEN_NODES;
            }
            if (emptyFolderCount > 0) {
                return BatchDownloadPreflightPrimaryReason.EMPTY_FOLDERS;
            }
            if (deletedCount > 0) {
                return BatchDownloadPreflightPrimaryReason.DELETED_NODES;
            }
            if (missingCount > 0) {
                return BatchDownloadPreflightPrimaryReason.MISSING_NODES;
            }
            if (duplicateCount > 0) {
                return BatchDownloadPreflightPrimaryReason.DUPLICATE_REFERENCES;
            }
            return BatchDownloadPreflightPrimaryReason.NO_READABLE_FILES;
        }

        if (forbiddenCount > 0) {
            return BatchDownloadPreflightPrimaryReason.FORBIDDEN_NODES;
        }
        if (missingCount > 0) {
            return BatchDownloadPreflightPrimaryReason.MISSING_NODES;
        }
        if (deletedCount > 0) {
            return BatchDownloadPreflightPrimaryReason.DELETED_NODES;
        }
        if (emptyFolderCount > 0) {
            return BatchDownloadPreflightPrimaryReason.EMPTY_FOLDERS;
        }
        if (duplicateCount > 0) {
            return BatchDownloadPreflightPrimaryReason.DUPLICATE_REFERENCES;
        }
        return BatchDownloadPreflightPrimaryReason.NONE;
    }

    /**
     * Writes multiple nodes into a ZIP output stream and reports coarse progress.
     */
    @Transactional(readOnly = true)
    public BatchDownloadArchiveSummary writeNodesAsZip(
        List<UUID> nodeIds,
        ZipOutputStream zipOut,
        BatchDownloadProgressListener progressListener
    ) {
        Set<String> usedPaths = new HashSet<>();
        BatchDownloadArchiveProgress progress = new BatchDownloadArchiveProgress();

        for (UUID id : nodeIds) {
            if (progressListener.isCancellationRequested()) {
                return progress.cancelledSummary();
            }
            try {
                Node node = nodeRepository.findById(id).orElse(null);
                if (node == null || node.isDeleted() || !isNodeVisible(node)) {
                    continue;
                }

                if (!securityService.hasPermission(node, Permission.PermissionType.READ)) {
                    log.warn("User {} tried to download node {} without permission", 
                        securityService.getCurrentUser(), id);
                    continue;
                }

                String rootPath = ""; // Root of the ZIP
                processNode(node, rootPath, zipOut, usedPaths, progress, progressListener);

            } catch (Exception e) {
                log.error("Error processing node {} for batch download", id, e);
                // Continue with next node, don't break the whole zip
            }
        }
        return progress.completedSummary();
    }

    private void inspectNode(Node node, BatchDownloadManifestAccumulator accumulator) {
        if (node instanceof Document document) {
            accumulator.recordDocument(document);
            return;
        }

        if (node instanceof Folder folder) {
            List<Node> children = nodeRepository.findByParentIdAndDeletedFalse(folder.getId());
            for (Node child : children) {
                if (isNodeVisible(child) && securityService.hasPermission(child, Permission.PermissionType.READ)) {
                    inspectNode(child, accumulator);
                }
            }
        }
    }

    private BatchDownloadManifest inspectNodeManifest(Node node) {
        BatchDownloadManifestAccumulator accumulator = new BatchDownloadManifestAccumulator();
        inspectNode(node, accumulator);
        return accumulator.toManifest();
    }

    private void processNode(
        Node node,
        String currentPath,
        ZipOutputStream zipOut,
        Set<String> usedPaths,
        BatchDownloadArchiveProgress progress,
        BatchDownloadProgressListener progressListener
    ) throws IOException {
        if (progressListener.isCancellationRequested()) {
            return;
        }
        if (node instanceof Document) {
            addDocumentToZip((Document) node, currentPath, zipOut, usedPaths, progress, progressListener);
        } else if (node instanceof Folder) {
            addFolderToZip((Folder) node, currentPath, zipOut, usedPaths, progress, progressListener);
        }
    }

    private void addDocumentToZip(
        Document doc,
        String path,
        ZipOutputStream zipOut,
        Set<String> usedPaths,
        BatchDownloadArchiveProgress progress,
        BatchDownloadProgressListener progressListener
    ) throws IOException {
        String entryPath = path + doc.getName();
        entryPath = ensureUniquePath(entryPath, usedPaths);

        ZipEntry zipEntry = new ZipEntry(entryPath);
        zipOut.putNextEntry(zipEntry);

        long bytesWritten = 0L;
        try (InputStream contentStream = contentService.getContent(doc.getContentId())) {
            bytesWritten = IOUtils.copyLarge(contentStream, zipOut);
        } catch (Exception e) {
            log.error("Failed to read content for document {}", doc.getId(), e);
            byte[] message = ("Error reading content: " + e.getMessage()).getBytes();
            zipOut.write(message);
            bytesWritten = message.length;
        }

        zipOut.closeEntry();
        progress.recordFile(bytesWritten);
        progressListener.onFileAdded(doc.getId(), entryPath, bytesWritten, progress.filesAdded(), progress.bytesAdded());
    }

    private void addFolderToZip(
        Folder folder,
        String path,
        ZipOutputStream zipOut,
        Set<String> usedPaths,
        BatchDownloadArchiveProgress progress,
        BatchDownloadProgressListener progressListener
    ) throws IOException {
        String folderPath = path + folder.getName() + "/";
        folderPath = ensureUniquePath(folderPath, usedPaths);

        // Add empty folder entry
        ZipEntry zipEntry = new ZipEntry(folderPath);
        zipOut.putNextEntry(zipEntry);
        zipOut.closeEntry();

        // Recursively add children
        // Note: For very large trees, this might be slow. 
        // In a real prod system, we might limit depth or count.
        List<Node> children = nodeRepository.findByParentIdAndDeletedFalse(folder.getId());
        
        for (Node child : children) {
            if (progressListener.isCancellationRequested()) {
                return;
            }
            if (isNodeVisible(child) && securityService.hasPermission(child, Permission.PermissionType.READ)) {
                processNode(child, folderPath, zipOut, usedPaths, progress, progressListener);
            }
        }
    }

    private boolean isNodeVisible(Node node) {
        if (node == null) {
            return false;
        }
        if (!tenantWorkspaceScopeService.hasScopedTenantWorkspace()) {
            return true;
        }
        return tenantWorkspaceScopeService.isPathVisible(node.getPath());
    }

    private String ensureUniquePath(String path, Set<String> usedPaths) {
        if (!usedPaths.contains(path)) {
            usedPaths.add(path);
            return path;
        }

        // Handle collision
        String base = path;
        String ext = "";
        boolean isFolder = path.endsWith("/");
        
        if (isFolder) {
            base = path.substring(0, path.length() - 1);
        } else {
            int dotIndex = path.lastIndexOf('.');
            if (dotIndex > 0) {
                base = path.substring(0, dotIndex);
                ext = path.substring(dotIndex);
            }
        }

        int counter = 1;
        while (true) {
            String newPath = base + " (" + counter + ")" + ext + (isFolder ? "/" : "");
            if (!usedPaths.contains(newPath)) {
                usedPaths.add(newPath);
                return newPath;
            }
            counter++;
        }
    }

    public interface BatchDownloadProgressListener {
        boolean isCancellationRequested();

        void onFileAdded(UUID nodeId, String entryPath, long bytesWritten, int filesAdded, long totalBytesAdded);

        static BatchDownloadProgressListener noop() {
            return new BatchDownloadProgressListener() {
                @Override
                public boolean isCancellationRequested() {
                    return false;
                }

                @Override
                public void onFileAdded(UUID nodeId, String entryPath, long bytesWritten, int filesAdded, long totalBytesAdded) {
                }
            };
        }
    }

    public record BatchDownloadManifest(int totalFiles, long totalBytes) {}

    public record BatchDownloadArchiveSummary(int filesAdded, long bytesAdded, boolean cancelled) {}

    public enum BatchDownloadPreflightOutcome {
        INCLUDED,
        MISSING,
        DELETED,
        FORBIDDEN,
        EMPTY_FOLDER
    }

    public enum BatchDownloadPreflightDecision {
        READY,
        PARTIAL,
        BLOCKED
    }

    public enum BatchDownloadPreflightPrimaryReason {
        NONE,
        DUPLICATE_REFERENCES,
        MISSING_NODES,
        DELETED_NODES,
        FORBIDDEN_NODES,
        EMPTY_FOLDERS,
        NO_READABLE_FILES
    }

    public record BatchDownloadPreflightItem(
        UUID nodeId,
        String nodeName,
        String nodeType,
        BatchDownloadPreflightOutcome outcome,
        int includedFiles,
        long includedBytes,
        String message
    ) {}

    public record BatchDownloadPreflightSummary(
        int requestedCount,
        int distinctCount,
        int duplicateCount,
        List<UUID> includedNodeIds,
        int includedNodeCount,
        int includedFileCount,
        long includedBytes,
        int missingCount,
        int deletedCount,
        int forbiddenCount,
        int emptyFolderCount,
        int skippedCount,
        boolean executable,
        BatchDownloadPreflightDecision decision,
        BatchDownloadPreflightPrimaryReason primaryReason,
        String message,
        List<String> warnings,
        List<BatchDownloadPreflightItem> items
    ) {}

    private static final class BatchDownloadManifestAccumulator {
        private int totalFiles;
        private long totalBytes;

        private void recordDocument(Document document) {
            totalFiles += 1;
            totalBytes += document.getSize() != null ? document.getSize() : 0L;
        }

        private BatchDownloadManifest toManifest() {
            return new BatchDownloadManifest(totalFiles, totalBytes);
        }
    }

    private static final class BatchDownloadArchiveProgress {
        private int filesAdded;
        private long bytesAdded;

        private void recordFile(long bytesWritten) {
            filesAdded += 1;
            bytesAdded += Math.max(bytesWritten, 0L);
        }

        private int filesAdded() {
            return filesAdded;
        }

        private long bytesAdded() {
            return bytesAdded;
        }

        private BatchDownloadArchiveSummary completedSummary() {
            return new BatchDownloadArchiveSummary(filesAdded, bytesAdded, false);
        }

        private BatchDownloadArchiveSummary cancelledSummary() {
            return new BatchDownloadArchiveSummary(filesAdded, bytesAdded, true);
        }
    }
}
