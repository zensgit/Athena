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
import java.util.HashSet;
import java.util.List;
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

    /**
     * Streams multiple nodes (documents and folders) into a ZIP output stream.
     *
     * @param nodeIds IDs of nodes to download
     * @param zipOut  The output stream to write the ZIP to
     */
    @Transactional(readOnly = true)
    public void streamNodesAsZip(List<UUID> nodeIds, ZipOutputStream zipOut) {
        Set<String> usedPaths = new HashSet<>();

        for (UUID id : nodeIds) {
            try {
                Node node = nodeRepository.findById(id).orElse(null);
                if (node == null || node.isDeleted()) {
                    continue;
                }

                if (!securityService.hasPermission(node, Permission.PermissionType.READ)) {
                    log.warn("User {} tried to download node {} without permission", 
                        securityService.getCurrentUser(), id);
                    continue;
                }

                String rootPath = ""; // Root of the ZIP
                processNode(node, rootPath, zipOut, usedPaths);

            } catch (Exception e) {
                log.error("Error processing node {} for batch download", id, e);
                // Continue with next node, don't break the whole zip
            }
        }
    }

    private void processNode(Node node, String currentPath, ZipOutputStream zipOut, Set<String> usedPaths) throws IOException {
        if (node instanceof Document) {
            addDocumentToZip((Document) node, currentPath, zipOut, usedPaths);
        } else if (node instanceof Folder) {
            addFolderToZip((Folder) node, currentPath, zipOut, usedPaths);
        }
    }

    private void addDocumentToZip(Document doc, String path, ZipOutputStream zipOut, Set<String> usedPaths) throws IOException {
        String entryPath = path + doc.getName();
        entryPath = ensureUniquePath(entryPath, usedPaths);

        ZipEntry zipEntry = new ZipEntry(entryPath);
        zipOut.putNextEntry(zipEntry);

        try (InputStream contentStream = contentService.getContent(doc.getContentId())) {
            IOUtils.copy(contentStream, zipOut);
        } catch (Exception e) {
            log.error("Failed to read content for document {}", doc.getId(), e);
            // Write a small error note in the zip instead of the file? 
            // Or just skip content.
            zipOut.write(("Error reading content: " + e.getMessage()).getBytes());
        }

        zipOut.closeEntry();
    }

    private void addFolderToZip(Folder folder, String path, ZipOutputStream zipOut, Set<String> usedPaths) throws IOException {
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
            if (securityService.hasPermission(child, Permission.PermissionType.READ)) {
                processNode(child, folderPath, zipOut, usedPaths);
            }
        }
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
}
