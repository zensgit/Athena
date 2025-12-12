package com.ecm.core.service;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Folder.FolderType;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.event.NodeCreatedEvent;
import com.ecm.core.event.NodeDeletedEvent;
import com.ecm.core.event.NodeUpdatedEvent;
import com.ecm.core.search.SimplePageRequest;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for folder-specific operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FolderService {

    private final FolderRepository folderRepository;
    private final NodeRepository nodeRepository;
    private final PermissionRepository permissionRepository;
    private final SecurityService securityService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.ecm.core.search.FacetedSearchService searchService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * Create a new folder
     */
    public Folder createFolder(CreateFolderRequest request) {
        log.debug("Creating folder: {} under parent: {}", request.name(), request.parentId());

        Folder folder = new Folder();
        folder.setName(request.name());
        folder.setDescription(request.description());
        folder.setFolderType(request.folderType() != null ? request.folderType() : FolderType.GENERAL);
        folder.setMaxItems(request.maxItems());
        folder.setAllowedTypes(request.allowedTypes());
        folder.setAutoFileNaming(request.autoFileNaming() != null && request.autoFileNaming());
        folder.setNamingPattern(request.namingPattern());
        
        // Smart Folder
        if (Boolean.TRUE.equals(request.isSmart())) {
            folder.setSmart(true);
            folder.setQueryCriteria(request.queryCriteria());
            // Smart folders typically don't have children in the DB sense
        }

        if (request.parentId() != null) {
            Folder parent = folderRepository.findById(request.parentId())
                .orElseThrow(() -> new NoSuchElementException("Parent folder not found: " + request.parentId()));

            // Check permission to create children
            if (!securityService.hasPermission(parent, PermissionType.CREATE_CHILDREN)) {
                throw new SecurityException("No permission to create folder in: " + parent.getName());
            }

            // Check if parent is full
            if (parent.getMaxItems() != null) {
                long childCount = folderRepository.countChildren(parent.getId());
                if (childCount >= parent.getMaxItems()) {
                    throw new IllegalStateException("Parent folder is full: " + parent.getName());
                }
            }

            folder.setParent(parent);
        }

        // Check for duplicate names
        if (nodeRepository.findByParentIdAndName(request.parentId(), request.name()).isPresent()) {
            throw new IllegalArgumentException("Folder with name already exists: " + request.name());
        }

        folder.setInheritPermissions(request.inheritPermissions() != null ? request.inheritPermissions() : true);

        Folder savedFolder = folderRepository.save(folder);

        // Copy parent permissions if inherit is true
        if (savedFolder.isInheritPermissions() && savedFolder.getParent() != null) {
            copyPermissions(savedFolder.getParent(), savedFolder);
        }

        log.info("Created folder: {} ({})", savedFolder.getName(), savedFolder.getId());
        eventPublisher.publishEvent(new NodeCreatedEvent(savedFolder, securityService.getCurrentUser()));

        return savedFolder;
    }

    /**
     * Get a folder by ID
     */
    @Transactional(readOnly = true)
    public Folder getFolder(UUID folderId) {
        Folder folder = folderRepository.findById(folderId)
            .orElseThrow(() -> new NoSuchElementException("Folder not found: " + folderId));

        if (folder.isDeleted()) {
            throw new NoSuchElementException("Folder has been deleted: " + folderId);
        }

        if (!securityService.hasPermission(folder, PermissionType.READ)) {
            throw new SecurityException("No permission to read folder: " + folder.getName());
        }

        return folder;
    }

    /**
     * Get folder by path
     */
    @Transactional(readOnly = true)
    public Folder getFolderByPath(String path) {
        return folderRepository.findByPath(path)
            .orElseThrow(() -> new NoSuchElementException("Folder not found at path: " + path));
    }

    /**
     * Get root folders
     */
    @Transactional(readOnly = true)
    public List<Folder> getRootFolders() {
        return folderRepository.findRootFolders().stream()
            .filter(f -> securityService.hasPermission(f, PermissionType.READ))
            .collect(Collectors.toList());
    }

    /**
     * Get folder contents (children)
     */
    @Transactional(readOnly = true)
    public Page<Node> getFolderContents(UUID folderId, Pageable pageable) {
        Folder folder = getFolder(folderId);
        
        // Smart Folder Logic
        if (folder.isSmart() && folder.getQueryCriteria() != null) {
            try {
                // Convert stored criteria to search request
                var criteria = folder.getQueryCriteria();
                var request = objectMapper.convertValue(criteria, com.ecm.core.search.FacetedSearchService.FacetedSearchRequest.class);
                var simplePageRequest = new SimplePageRequest();
                simplePageRequest.setPage(pageable.getPageNumber());
                simplePageRequest.setSize(pageable.getPageSize());
                request.setPageable(simplePageRequest);
                
                var results = searchService.search(request);
                
                // Map search results back to Node entities (simplified)
                // Ideally, SearchResult should be compatible or we fetch nodes by ID
                List<UUID> ids = results.getResults().getContent().stream()
                    .map(r -> UUID.fromString(r.getId()))
                    .collect(Collectors.toList());
                
                if (ids.isEmpty()) {
                    return Page.empty(pageable);
                }
                
                // Preserve order from search result if possible, or just fetch
                return nodeRepository.findAllById(ids).stream()
                    .filter(n -> !n.isDeleted())
                    .collect(Collectors.collectingAndThen(Collectors.toList(), 
                        list -> new org.springframework.data.domain.PageImpl<>(list, pageable, results.getTotalHits())));
                        
            } catch (Exception e) {
                log.error("Failed to execute smart folder query for {}", folderId, e);
                return Page.empty(pageable);
            }
        }
        
        return nodeRepository.findByParentIdAndDeletedFalse(folderId, pageable);
    }

    /**
     * Get folder contents with filtering
     */
    @Transactional(readOnly = true)
    public FolderContentsResponse getFolderContentsFiltered(UUID folderId, FolderContentsFilter filter) {
        Folder folder = getFolder(folderId);

        List<Node> children = nodeRepository.findByParentIdAndDeletedFalse(folderId);

        // Separate folders and documents
        List<Node> folders = new ArrayList<>();
        List<Node> documents = new ArrayList<>();

        for (Node child : children) {
            if (securityService.hasPermission(child, PermissionType.READ)) {
                if (child.isFolder()) {
                    folders.add(child);
                } else {
                    documents.add(child);
                }
            }
        }

        // Apply sorting
        Comparator<Node> comparator = getNodeComparator(filter.sortBy(), filter.sortDirection());
        folders.sort(comparator);
        documents.sort(comparator);

        // Combine: folders first, then documents
        List<Node> combined = new ArrayList<>();
        combined.addAll(folders);
        combined.addAll(documents);

        return new FolderContentsResponse(
            folder,
            combined,
            folders.size(),
            documents.size(),
            calculateTotalSize(documents)
        );
    }

    /**
     * Update folder
     */
    public Folder updateFolder(UUID folderId, UpdateFolderRequest request) {
        Folder folder = getFolder(folderId);

        if (!securityService.hasPermission(folder, PermissionType.WRITE)) {
            throw new SecurityException("No permission to update folder: " + folder.getName());
        }

        if (folder.isLocked() && !folder.getLockedBy().equals(securityService.getCurrentUser())) {
            throw new IllegalStateException("Folder is locked by: " + folder.getLockedBy());
        }

        // Update name if provided
        if (request.name() != null && !request.name().equals(folder.getName())) {
            UUID parentId = folder.getParent() != null ? folder.getParent().getId() : null;
            if (nodeRepository.findByParentIdAndName(parentId, request.name()).isPresent()) {
                throw new IllegalArgumentException("Folder with name already exists: " + request.name());
            }
            folder.setName(request.name());
        }

        if (request.description() != null) {
            folder.setDescription(request.description());
        }
        if (request.folderType() != null) {
            folder.setFolderType(request.folderType());
        }
        if (request.maxItems() != null) {
            folder.setMaxItems(request.maxItems());
        }
        if (request.allowedTypes() != null) {
            folder.setAllowedTypes(request.allowedTypes());
        }
        if (request.autoFileNaming() != null) {
            folder.setAutoFileNaming(request.autoFileNaming());
        }
        if (request.namingPattern() != null) {
            folder.setNamingPattern(request.namingPattern());
        }

        Folder updatedFolder = folderRepository.save(folder);
        eventPublisher.publishEvent(new NodeUpdatedEvent(updatedFolder, securityService.getCurrentUser()));

        return updatedFolder;
    }

    /**
     * Rename folder
     */
    public Folder renameFolder(UUID folderId, String newName) {
        return updateFolder(folderId, new UpdateFolderRequest(
            newName, null, null, null, null, null, null
        ));
    }

    /**
     * Delete folder (soft delete by default)
     */
    public void deleteFolder(UUID folderId, boolean permanent, boolean recursive) {
        Folder folder = getFolder(folderId);

        if (!securityService.hasPermission(folder, PermissionType.DELETE)) {
            throw new SecurityException("No permission to delete folder: " + folder.getName());
        }

        // Check if folder has children
        long childCount = folderRepository.countChildren(folderId);
        if (childCount > 0 && !recursive) {
            throw new IllegalStateException("Folder is not empty. Use recursive=true to delete with contents.");
        }

        if (permanent) {
            deleteFolderRecursive(folder);
        } else {
            softDeleteFolderRecursive(folder);
        }

        log.info("Deleted folder: {} (permanent: {})", folder.getName(), permanent);
        eventPublisher.publishEvent(new NodeDeletedEvent(folder, securityService.getCurrentUser(), permanent));
    }

    /**
     * Get folder tree (hierarchy)
     */
    @Transactional(readOnly = true)
    public List<FolderTreeNode> getFolderTree(UUID rootId, int maxDepth) {
        List<FolderTreeNode> tree = new ArrayList<>();

        List<Folder> roots;
        if (rootId != null) {
            Folder root = getFolder(rootId);
            roots = Collections.singletonList(root);
        } else {
            roots = getRootFolders();
        }

        for (Folder root : roots) {
            tree.add(buildFolderTreeNode(root, 0, maxDepth));
        }

        return tree;
    }

    /**
     * Get folder breadcrumb (path from root)
     */
    @Transactional(readOnly = true)
    public List<BreadcrumbItem> getFolderBreadcrumb(UUID folderId) {
        Folder folder = getFolder(folderId);
        List<BreadcrumbItem> breadcrumb = new ArrayList<>();

        Node current = folder;
        while (current != null) {
            breadcrumb.add(0, new BreadcrumbItem(
                current.getId(),
                current.getName(),
                current.getPath()
            ));
            current = current.getParent();
        }

        return breadcrumb;
    }

    /**
     * Get folders by type
     */
    @Transactional(readOnly = true)
    public List<Folder> getFoldersByType(FolderType type) {
        return folderRepository.findActiveFoldersByType(type).stream()
            .filter(f -> securityService.hasPermission(f, PermissionType.READ))
            .collect(Collectors.toList());
    }

    /**
     * Get folder statistics
     */
    @Transactional(readOnly = true)
    public FolderStats getFolderStats(UUID folderId) {
        Folder folder = getFolder(folderId);

        // Count direct children
        List<Node> children = nodeRepository.findByParentIdAndDeletedFalse(folderId);
        int directFolders = 0;
        int directDocuments = 0;
        long directSize = 0;

        for (Node child : children) {
            if (child.isFolder()) {
                directFolders++;
            } else {
                directDocuments++;
                if (child.getSize() != null) {
                    directSize += child.getSize();
                }
            }
        }

        // Count all descendants recursively
        int[] totalCounts = countDescendantsRecursive(folderId);

        return new FolderStats(
            directFolders,
            directDocuments,
            directSize,
            totalCounts[0], // total folders
            totalCounts[1], // total documents
            totalCounts[2], // total size
            folder.getMaxItems(),
            folder.getMaxItems() != null ? folder.getMaxItems() - (directFolders + directDocuments) : null
        );
    }

    /**
     * Check if folder can accept more items
     */
    @Transactional(readOnly = true)
    public boolean canAcceptItems(UUID folderId, int itemCount) {
        Folder folder = getFolder(folderId);

        if (folder.getMaxItems() == null) {
            return true;
        }

        long currentCount = folderRepository.countChildren(folderId);
        return currentCount + itemCount <= folder.getMaxItems();
    }

    /**
     * Check if folder can accept file type
     */
    @Transactional(readOnly = true)
    public boolean canAcceptFileType(UUID folderId, String mimeType) {
        Folder folder = getFolder(folderId);
        return folder.canContainType(mimeType);
    }

    // === Private helper methods ===

    private void copyPermissions(Node source, Node target) {
        var sourcePermissions = permissionRepository.findByNodeId(source.getId());
        for (var perm : sourcePermissions) {
            var copy = new com.ecm.core.entity.Permission();
            copy.setNode(target);
            copy.setAuthority(perm.getAuthority());
            copy.setAuthorityType(perm.getAuthorityType());
            copy.setPermission(perm.getPermission());
            copy.setAllowed(perm.isAllowed());
            copy.setInherited(true);
            permissionRepository.save(copy);
        }
    }

    private void deleteFolderRecursive(Folder folder) {
        List<Node> children = nodeRepository.findByParentIdAndDeletedFalse(folder.getId());
        for (Node child : children) {
            if (child instanceof Folder) {
                deleteFolderRecursive((Folder) child);
            } else {
                permissionRepository.deleteByNodeId(child.getId());
                nodeRepository.delete(child);
            }
        }
        permissionRepository.deleteByNodeId(folder.getId());
        folderRepository.delete(folder);
    }

    private void softDeleteFolderRecursive(Folder folder) {
        String currentUser = securityService.getCurrentUser();
        nodeRepository.softDeleteByPathPrefix(folder.getPath());
        nodeRepository.softDelete(folder.getId());
    }

    private FolderTreeNode buildFolderTreeNode(Folder folder, int currentDepth, int maxDepth) {
        List<FolderTreeNode> children = new ArrayList<>();
        long childCount = folderRepository.countChildren(folder.getId());

        if (currentDepth < maxDepth) {
            List<Node> childNodes = nodeRepository.findByParentIdAndDeletedFalse(folder.getId());
            for (Node child : childNodes) {
                if (child instanceof Folder && securityService.hasPermission(child, PermissionType.READ)) {
                    children.add(buildFolderTreeNode((Folder) child, currentDepth + 1, maxDepth));
                }
            }
        }

        return new FolderTreeNode(
            folder.getId(),
            folder.getName(),
            folder.getPath(),
            folder.getFolderType(),
            childCount,
            children,
            currentDepth < maxDepth && !children.isEmpty()
        );
    }

    private int[] countDescendantsRecursive(UUID folderId) {
        int totalFolders = 0;
        int totalDocuments = 0;
        long totalSize = 0;

        List<Node> children = nodeRepository.findByParentIdAndDeletedFalse(folderId);
        for (Node child : children) {
            if (child.isFolder()) {
                totalFolders++;
                int[] descendantCounts = countDescendantsRecursive(child.getId());
                totalFolders += descendantCounts[0];
                totalDocuments += descendantCounts[1];
                totalSize += descendantCounts[2];
            } else {
                totalDocuments++;
                if (child.getSize() != null) {
                    totalSize += child.getSize();
                }
            }
        }

        return new int[] { totalFolders, totalDocuments, (int) totalSize };
    }

    private long calculateTotalSize(List<Node> documents) {
        return documents.stream()
            .filter(d -> d.getSize() != null)
            .mapToLong(Node::getSize)
            .sum();
    }

    private Comparator<Node> getNodeComparator(String sortBy, String direction) {
        Comparator<Node> comparator;

        switch (sortBy != null ? sortBy.toLowerCase() : "name") {
            case "created":
            case "createddate":
                comparator = Comparator.comparing(Node::getCreatedDate, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "modified":
            case "lastmodifieddate":
                comparator = Comparator.comparing(Node::getLastModifiedDate, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "size":
                comparator = Comparator.comparing(Node::getSize, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "name":
            default:
                comparator = Comparator.comparing(Node::getName, String.CASE_INSENSITIVE_ORDER);
                break;
        }

        if ("desc".equalsIgnoreCase(direction)) {
            comparator = comparator.reversed();
        }

        return comparator;
    }

    // === Request/Response DTOs ===

    public record CreateFolderRequest(
        String name,
        String description,
        UUID parentId,
        FolderType folderType,
        Integer maxItems,
        String allowedTypes,
        Boolean autoFileNaming,
        String namingPattern,
        Boolean inheritPermissions,
        Boolean isSmart,
        Map<String, Object> queryCriteria
    ) {}

    public record UpdateFolderRequest(
        String name,
        String description,
        FolderType folderType,
        Integer maxItems,
        String allowedTypes,
        Boolean autoFileNaming,
        String namingPattern
    ) {}

    public record FolderContentsFilter(
        String sortBy,
        String sortDirection
    ) {
        public FolderContentsFilter {
            if (sortBy == null) sortBy = "name";
            if (sortDirection == null) sortDirection = "asc";
        }
    }

    public record FolderContentsResponse(
        Folder folder,
        List<Node> contents,
        int folderCount,
        int documentCount,
        long totalSize
    ) {}

    public record FolderTreeNode(
        UUID id,
        String name,
        String path,
        FolderType folderType,
        long childCount,
        List<FolderTreeNode> children,
        boolean hasChildren
    ) {}

    public record BreadcrumbItem(
        UUID id,
        String name,
        String path
    ) {}

    public record FolderStats(
        int directFolders,
        int directDocuments,
        long directSize,
        int totalFolders,
        int totalDocuments,
        long totalSize,
        Integer maxItems,
        Integer remainingCapacity
    ) {
        public String formattedDirectSize() {
            return formatSize(directSize);
        }

        public String formattedTotalSize() {
            return formatSize(totalSize);
        }

        private static String formatSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
