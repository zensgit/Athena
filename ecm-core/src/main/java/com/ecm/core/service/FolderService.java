package com.ecm.core.service;

import com.ecm.core.config.TenantContext;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Folder.FolderType;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.event.RepositoryLifecyclePublisher;
import com.ecm.core.search.SearchFilters;
import com.ecm.core.search.SearchResult;
import com.ecm.core.search.SimplePageRequest;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.PermissionRepository;
import com.ecm.core.repository.RenditionResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private static final int SMART_FOLDER_FILTERED_FETCH_SIZE = 1000;

    private final FolderRepository folderRepository;
    private final NodeRepository nodeRepository;
    private final PermissionRepository permissionRepository;
    private final RenditionResourceRepository renditionResourceRepository;
    private final SecurityService securityService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.ecm.core.search.FacetedSearchService searchService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    @Lazy
    private LegalHoldService legalHoldService;

    @Autowired
    @Lazy
    private ShareLinkNodeCleanupService shareLinkNodeCleanupService;

    /**
     * Create a new folder
     */
    public Folder createFolder(CreateFolderRequest request) {
        UUID effectiveParentId = resolveScopedParentId(request.parentId());
        log.debug("Creating folder: {} under parent: {}", request.name(), effectiveParentId);
        boolean smartFolder = Boolean.TRUE.equals(request.isSmart());
        Map<String, Object> queryCriteria = smartFolder
            ? normalizeQueryCriteria(request.queryCriteria())
            : new LinkedHashMap<>();

        Folder folder = new Folder();
        folder.setName(request.name());
        folder.setDescription(request.description());
        folder.setFolderType(request.folderType() != null ? request.folderType() : FolderType.GENERAL);
        folder.setMaxItems(request.maxItems());
        folder.setAllowedTypes(request.allowedTypes());
        folder.setAutoFileNaming(request.autoFileNaming() != null && request.autoFileNaming());
        folder.setNamingPattern(request.namingPattern());
        
        if (smartFolder) {
            validateSmartFolderQueryCriteria(queryCriteria);
            folder.setSmart(true);
            folder.setQueryCriteria(queryCriteria);
        }

        if (effectiveParentId != null) {
            Folder parent = folderRepository.findById(effectiveParentId)
                .orElseThrow(() -> new NoSuchElementException("Parent folder not found: " + effectiveParentId));
            assertTenantScoped(parent);

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

            assertAcceptsPhysicalChildren(parent);
            folder.setParent(parent);
        }

        // Check for duplicate names
        if (nodeRepository.findByParentIdAndName(effectiveParentId, request.name()).isPresent()) {
            throw new IllegalArgumentException("Folder with name already exists: " + request.name());
        }

        folder.setInheritPermissions(request.inheritPermissions() != null ? request.inheritPermissions() : true);

        Folder savedFolder = folderRepository.save(folder);

        // Copy parent permissions if inherit is true
        if (savedFolder.isInheritPermissions() && savedFolder.getParent() != null) {
            copyPermissions(savedFolder.getParent(), savedFolder);
        }

        log.info("Created folder: {} ({})", savedFolder.getName(), savedFolder.getId());
        RepositoryLifecyclePublisher.publishNodeCreated(
            eventPublisher,
            savedFolder,
            securityService.getCurrentUser(),
            null
        );

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

        if (folder.getArchiveStatus() != Node.ArchiveStatus.LIVE) {
            throw new NoSuchElementException("Folder not found: " + folderId);
        }

        assertTenantScoped(folder);

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
        Folder folder = folderRepository.findFirstByPathAndDeletedFalseOrderByCreatedDateAsc(path)
            .filter(candidate -> candidate.getArchiveStatus() == Node.ArchiveStatus.LIVE)
            .orElseThrow(() -> new NoSuchElementException("Folder not found at path: " + path));
        assertTenantScoped(folder);
        return folder;
    }

    /**
     * Get root folders
     */
    @Transactional(readOnly = true)
    public List<Folder> getRootFolders() {
        Optional<Folder> tenantRoot = getScopedTenantRootFolder();
        if (tenantRoot.isPresent()) {
            Folder root = tenantRoot.get();
            if (securityService.hasPermission(root, PermissionType.READ)) {
                return List.of(root);
            }
            return List.of();
        }
        return folderRepository.findRootFolders().stream()
            .filter(folder -> folder.getArchiveStatus() == Node.ArchiveStatus.LIVE)
            .filter(f -> securityService.hasPermission(f, PermissionType.READ))
            .collect(Collectors.toList());
    }

    /**
     * Get folder contents (children)
     */
    @Transactional(readOnly = true)
    public Page<Node> getFolderContents(UUID folderId, Pageable pageable) {
        Folder folder = getFolder(folderId);
        boolean isAdmin = securityService.hasRole("ROLE_ADMIN");
        
        if (folder.isSmart() && folder.getQueryCriteria() != null) {
            return getSmartFolderContents(folder, pageable);
        }

        if (isAdmin) {
            return nodeRepository.findByParentIdAndDeletedFalseAndArchiveStatus(folderId, Node.ArchiveStatus.LIVE, pageable);
        }

        List<Node> children = nodeRepository.findByParentIdAndDeletedFalseAndArchiveStatus(folderId, Node.ArchiveStatus.LIVE, pageable.getSort());
        List<Node> permitted = children.stream()
            .filter(child -> securityService.hasPermission(child, PermissionType.READ))
            .collect(Collectors.toList());

        return pageFromList(permitted, pageable);
    }

    /**
     * Get folder contents with filtering
     */
    @Transactional(readOnly = true)
    public FolderContentsResponse getFolderContentsFiltered(UUID folderId, FolderContentsFilter filter) {
        Folder folder = getFolder(folderId);

        if (folder.isSmart()) {
            Page<Node> page = getFolderContents(
                folderId,
                org.springframework.data.domain.PageRequest.of(0, SMART_FOLDER_FILTERED_FETCH_SIZE)
            );
            List<Node> combined = new ArrayList<>(page.getContent());
            combined.sort(getNodeComparator(filter.sortBy(), filter.sortDirection()));
            List<Node> folders = combined.stream().filter(Node::isFolder).toList();
            List<Node> documents = combined.stream().filter(node -> !node.isFolder()).toList();
            return new FolderContentsResponse(
                folder,
                combined,
                folders.size(),
                documents.size(),
                calculateTotalSize(documents)
            );
        }

        List<Node> children = nodeRepository.findByParentIdAndDeletedFalseAndArchiveStatus(folderId, Node.ArchiveStatus.LIVE);

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
        folder = normalizeExpiredLock(folder);

        if (!securityService.hasPermission(folder, PermissionType.WRITE)) {
            throw new SecurityException("No permission to update folder: " + folder.getName());
        }

        if (folder.isEffectivelyLocked(LocalDateTime.now()) && !folder.getLockedBy().equals(securityService.getCurrentUser())) {
            throw new IllegalStateException("Folder is " + folder.describeActiveLock(LocalDateTime.now()));
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
        applySmartFolderSettings(folder, request);

        Folder updatedFolder = folderRepository.save(folder);
        RepositoryLifecyclePublisher.publishNodeUpdated(
            eventPublisher,
            updatedFolder,
            securityService.getCurrentUser(),
            null
        );

        return updatedFolder;
    }

    private Folder normalizeExpiredLock(Folder folder) {
        if (folder.isLockExpired(LocalDateTime.now())) {
            folder.clearLock();
            return folderRepository.save(folder);
        }
        return folder;
    }

    /**
     * Rename folder
     */
    public Folder renameFolder(UUID folderId, String newName) {
        return updateFolder(folderId, new UpdateFolderRequest(
            newName, null, null, null, null, null, null, null, null
        ));
    }

    /**
     * Delete folder (soft delete by default)
     */
    public void deleteFolder(UUID folderId, boolean permanent, boolean recursive) {
        Folder folder = getFolder(folderId);
        String deletedPath = folder.getPath();
        java.util.Set<String> readableAuthorities = securityService.resolveReadAuthorities(folder);

        if (!securityService.hasPermission(folder, PermissionType.DELETE)) {
            throw new SecurityException("No permission to delete folder: " + folder.getName());
        }
        assertNoActiveHold(folder, permanent ? "permanently delete" : "delete");

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
        RepositoryLifecyclePublisher.publishNodeDeleted(
            eventPublisher,
            folder,
            securityService.getCurrentUser(),
            permanent,
            deletedPath,
            readableAuthorities
        );
    }

    /**
     * Get folder tree (hierarchy)
     */
    @Transactional(readOnly = true)
    public List<FolderTreeNode> getFolderTree(UUID rootId, int maxDepth) {
        List<FolderTreeNode> tree = new ArrayList<>();
        boolean isAdmin = securityService.hasRole("ROLE_ADMIN");

        List<Folder> roots;
        if (rootId != null) {
            Folder root = getFolder(rootId);
            roots = Collections.singletonList(root);
        } else {
            roots = getRootFolders();
        }

        for (Folder root : roots) {
            tree.add(buildFolderTreeNode(root, 0, maxDepth, isAdmin));
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
            .filter(this::isTenantScoped)
            .filter(f -> securityService.hasPermission(f, PermissionType.READ))
            .collect(Collectors.toList());
    }

    /**
     * Get folder statistics
     */
    @Transactional(readOnly = true)
    public FolderStats getFolderStats(UUID folderId) {
        Folder folder = getFolder(folderId);
        boolean isAdmin = securityService.hasRole("ROLE_ADMIN");

        // Count direct children
        List<Node> children = loadReadableChildren(folderId, isAdmin);
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
        int[] totalCounts = countDescendantsRecursive(folderId, isAdmin);

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
        if (folder.isSmart()) {
            return false;
        }

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
        if (folder.isSmart()) {
            return false;
        }
        return folder.canContainType(mimeType);
    }

    // === Private helper methods ===

    private Page<Node> getSmartFolderContents(Folder folder, Pageable pageable) {
        try {
            Map<String, Object> criteria = normalizeQueryCriteria(folder.getQueryCriteria());
            validateSmartFolderQueryCriteria(criteria);

            var request = objectMapper.convertValue(
                criteria,
                com.ecm.core.search.FacetedSearchService.FacetedSearchRequest.class
            );
            var simplePageRequest = new SimplePageRequest();
            if (pageable != null && pageable.isPaged()) {
                simplePageRequest.setPage(pageable.getPageNumber());
                simplePageRequest.setSize(pageable.getPageSize());
            } else {
                simplePageRequest.setPage(0);
                simplePageRequest.setSize(SMART_FOLDER_FILTERED_FETCH_SIZE);
            }
            request.setPageable(simplePageRequest);

            var results = searchService.search(request);
            List<UUID> ids = results.getResults().getContent().stream()
                .map(SearchResult::getId)
                .filter(Objects::nonNull)
                .map(UUID::fromString)
                .toList();

            if (ids.isEmpty()) {
                return Page.empty(pageable == null ? Pageable.unpaged() : pageable);
            }

            Map<UUID, Integer> order = new HashMap<>();
            for (int i = 0; i < ids.size(); i++) {
                order.put(ids.get(i), i);
            }

            List<Node> content = nodeRepository.findAllById(ids).stream()
                .filter(n -> !n.isDeleted() && n.getArchiveStatus() == Node.ArchiveStatus.LIVE)
                .sorted(Comparator.comparingInt(node -> order.getOrDefault(node.getId(), Integer.MAX_VALUE)))
                .toList();

            Pageable effectivePageable = pageable == null ? Pageable.unpaged() : pageable;
            return new PageImpl<>(content, effectivePageable, results.getTotalHits());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute smart folder query for {}", folder.getId(), e);
            throw new IllegalArgumentException("Invalid smart folder queryCriteria", e);
        }
    }

    private void applySmartFolderSettings(Folder folder, UpdateFolderRequest request) {
        boolean currentSmart = folder.isSmart();
        Boolean requestedSmart = request.isSmart();
        Map<String, Object> requestedCriteria = request.queryCriteria();

        if (requestedSmart == null && requestedCriteria == null) {
            return;
        }

        boolean targetSmart = requestedSmart != null ? requestedSmart : currentSmart;
        if (!targetSmart) {
            if (requestedCriteria != null) {
                throw new IllegalArgumentException("queryCriteria is only supported for smart folders");
            }
            folder.setSmart(false);
            folder.setQueryCriteria(new LinkedHashMap<>());
            return;
        }

        Map<String, Object> effectiveCriteria = requestedCriteria != null
            ? normalizeQueryCriteria(requestedCriteria)
            : normalizeQueryCriteria(folder.getQueryCriteria());
        validateSmartFolderQueryCriteria(effectiveCriteria);

        if (!currentSmart && nodeRepository.countByParentId(folder.getId()) > 0) {
            throw new IllegalArgumentException("Cannot convert a non-empty folder into a smart folder");
        }

        folder.setSmart(true);
        folder.setQueryCriteria(effectiveCriteria);
    }

    private void validateSmartFolderQueryCriteria(Map<String, Object> queryCriteria) {
        if (queryCriteria == null || queryCriteria.isEmpty()) {
            throw new IllegalArgumentException("Smart folders require non-empty queryCriteria");
        }

        com.ecm.core.search.FacetedSearchService.FacetedSearchRequest request;
        try {
            request = objectMapper.convertValue(
                queryCriteria,
                com.ecm.core.search.FacetedSearchService.FacetedSearchRequest.class
            );
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid smart folder queryCriteria", e);
        }

        boolean hasQuery = request.getQuery() != null && !request.getQuery().isBlank();
        boolean hasFilters = hasSearchFilters(request.getFilters());
        boolean hasPathPrefix = request.getPathPrefix() != null && !request.getPathPrefix().isBlank();

        if (!hasQuery && !hasFilters && !hasPathPrefix) {
            throw new IllegalArgumentException("Smart folder queryCriteria must define a query, filters, or pathPrefix");
        }
    }

    private boolean hasSearchFilters(SearchFilters filters) {
        if (filters == null) {
            return false;
        }
        return hasValues(filters.getNodeTypes())
            || hasValues(filters.getMimeTypes())
            || filters.getLocked() != null
            || hasText(filters.getLockedBy())
            || filters.getCheckedOut() != null
            || hasText(filters.getCheckoutUser())
            || hasText(filters.getCreatedBy())
            || hasValues(filters.getCreatedByList())
            || filters.getDateFrom() != null
            || filters.getDateTo() != null
            || filters.getModifiedFrom() != null
            || filters.getModifiedTo() != null
            || filters.getMinSize() != null
            || filters.getMaxSize() != null
            || hasValues(filters.getTags())
            || hasValues(filters.getCategories())
            || hasValues(filters.getCorrespondents())
            || hasText(filters.getPath())
            || hasText(filters.getFolderId())
            || hasValues(filters.getPreviewStatuses())
            || filters.isIncludeDeleted();
    }

    private boolean hasValues(Collection<?> values) {
        return values != null && !values.isEmpty();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Map<String, Object> normalizeQueryCriteria(Map<String, Object> queryCriteria) {
        if (queryCriteria == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(queryCriteria);
    }

    private void assertAcceptsPhysicalChildren(Folder parent) {
        if (parent.isSmart()) {
            throw new IllegalArgumentException("Smart folders cannot contain physical child nodes: " + parent.getName());
        }
    }

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
                deleteNodePermanently(child);
            }
        }
        permissionRepository.deleteByNodeId(folder.getId());
        folderRepository.delete(folder);
    }

    private void deleteNodePermanently(Node node) {
        if (node instanceof Document document && document.getId() != null) {
            shareLinkNodeCleanupService.deleteByNodeId(document.getId());
            renditionResourceRepository.deleteByDocumentId(document.getId());
        }
        nodeRepository.delete(node);
    }

    private void softDeleteFolderRecursive(Folder folder) {
        String currentUser = securityService.getCurrentUser();
        LocalDateTime deletedAt = LocalDateTime.now();
        nodeRepository.softDeleteByPathPrefix(folder.getPath(), deletedAt, currentUser);
    }

    private FolderTreeNode buildFolderTreeNode(Folder folder, int currentDepth, int maxDepth, boolean isAdmin) {
        List<FolderTreeNode> children = new ArrayList<>();
        List<Node> childNodes = isAdmin && currentDepth >= maxDepth
            ? Collections.emptyList()
            : loadReadableChildren(folder.getId(), isAdmin);
        long childCount = isAdmin && currentDepth >= maxDepth
            ? folderRepository.countChildren(folder.getId())
            : childNodes.size();

        if (currentDepth < maxDepth) {
            for (Node child : childNodes) {
                if (child instanceof Folder) {
                    children.add(buildFolderTreeNode((Folder) child, currentDepth + 1, maxDepth, isAdmin));
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

    private List<Node> loadReadableChildren(UUID folderId, boolean isAdmin) {
        List<Node> children = nodeRepository.findByParentIdAndDeletedFalseAndArchiveStatus(folderId, Node.ArchiveStatus.LIVE);
        if (isAdmin) {
            return children.stream()
                .filter(this::isTenantScoped)
                .collect(Collectors.toList());
        }
        return children.stream()
            .filter(this::isTenantScoped)
            .filter(child -> securityService.hasPermission(child, PermissionType.READ))
            .collect(Collectors.toList());
    }

    private UUID resolveScopedParentId(UUID requestedParentId) {
        return requestedParentId != null ? requestedParentId : TenantContext.getCurrentTenantRootNodeId();
    }

    private Optional<Folder> getScopedTenantRootFolder() {
        UUID tenantRootNodeId = TenantContext.getCurrentTenantRootNodeId();
        if (tenantRootNodeId == null) {
            return Optional.empty();
        }
        return folderRepository.findById(tenantRootNodeId)
            .filter(folder -> !folder.isDeleted())
            .filter(folder -> folder.getArchiveStatus() == Node.ArchiveStatus.LIVE);
    }

    private boolean isTenantScoped(Node node) {
        UUID tenantRootNodeId = TenantContext.getCurrentTenantRootNodeId();
        if (tenantRootNodeId == null) {
            return true;
        }
        Folder root = getScopedTenantRootFolder()
            .orElseThrow(() -> new NoSuchElementException("Tenant root workspace not found"));
        if (tenantRootNodeId.equals(node.getId())) {
            return true;
        }
        return node.getPath() != null && node.getPath().startsWith(root.getPath() + "/");
    }

    private void assertTenantScoped(Node node) {
        if (!isTenantScoped(node)) {
            throw new NoSuchElementException("Folder not found: " + node.getId());
        }
    }

    private void assertNoActiveHold(Node node, String operation) {
        if (legalHoldService != null) {
            legalHoldService.assertOperationAllowed(node, operation);
        }
    }

    private int[] countDescendantsRecursive(UUID folderId, boolean isAdmin) {
        int totalFolders = 0;
        int totalDocuments = 0;
        long totalSize = 0;

        List<Node> children = loadReadableChildren(folderId, isAdmin);
        for (Node child : children) {
            if (child.isFolder()) {
                totalFolders++;
                int[] descendantCounts = countDescendantsRecursive(child.getId(), isAdmin);
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

    private Page<Node> pageFromList(List<Node> nodes, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(nodes);
        }

        long offset = pageable.getOffset();
        if (offset >= nodes.size()) {
            return new PageImpl<>(List.of(), pageable, nodes.size());
        }

        int start = Math.toIntExact(offset);
        int end = Math.min(start + pageable.getPageSize(), nodes.size());
        return new PageImpl<>(nodes.subList(start, end), pageable, nodes.size());
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
        String namingPattern,
        Boolean isSmart,
        Map<String, Object> queryCriteria
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
