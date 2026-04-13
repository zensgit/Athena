package com.ecm.core.service;

import com.ecm.core.entity.ContentReference.OwnerType;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Persisted working-copy backbone for check-out / check-in.
 * <p>
 * On checkout a real {@link Document} row is created with
 * {@code workingCopy=true} and {@code workingCopyOf} pointing at the original.
 * On checkin the working copy is soft-deleted and the original is updated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CheckOutCheckInService {

    static final String WC_NAME_PREFIX = "(Working Copy) ";

    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final NodeRepository nodeRepository;
    private final SecurityService securityService;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;
    private final ContentReferenceService contentReferenceService;
    private final ContentService contentService;

    @Autowired
    @Lazy
    private VersionService versionService;

    // ------------------------------------------------------------------ checkout

    /**
     * Check out a document, creating the working copy in the same folder.
     */
    public Document checkout(UUID documentId) {
        return checkout(documentId, null);
    }

    /**
     * Check out a document, creating the working copy in {@code destinationFolderId}
     * (or the same parent folder when {@code null}).
     */
    public Document checkout(UUID documentId, UUID destinationFolderId) {
        Document original = loadLiveDocument(documentId);

        if (!securityService.hasPermission(original, PermissionType.WRITE)) {
            throw new SecurityException("No permission to check out document: " + original.getName());
        }
        if (original.isCheckedOut()) {
            throw new IllegalStateException("Document is already checked out by: " + original.getCheckoutUser());
        }
        if (original.isWorkingCopy()) {
            throw new IllegalStateException("Cannot check out a working copy");
        }
        if (original.isEffectivelyLocked(LocalDateTime.now())
                && !Objects.equals(original.getLockedBy(), securityService.getCurrentUser())) {
            throw new IllegalStateException("Document is locked by another user");
        }

        Folder destination = resolveDestination(original, destinationFolderId);

        String currentUser = securityService.getCurrentUser();

        // --- create persisted working copy ---------------------------------
        Document wc = new Document();
        wc.setName(WC_NAME_PREFIX + original.getName());
        wc.setDescription(original.getDescription());
        wc.setParent(destination);
        wc.setPath(destination.getPath() + "/" + wc.getName());
        wc.setMimeType(original.getMimeType());
        wc.setFileSize(original.getFileSize());
        wc.setFileExtension(original.getFileExtension());
        wc.setEncoding(original.getEncoding());
        wc.setContentId(original.getContentId());
        wc.setContentHash(original.getContentHash());
        wc.setVersionLabel(original.getVersionLabel());
        wc.setMajorVersion(original.getMajorVersion());
        wc.setMinorVersion(original.getMinorVersion());
        wc.setVersioned(false);
        wc.setWorkingCopy(true);
        wc.setWorkingCopyOf(original.getId());
        wc.setCheckoutUser(currentUser);
        wc.setCheckoutDate(LocalDateTime.now());
        wc.setCreatedBy(currentUser);
        wc.setCreatedDate(LocalDateTime.now());
        wc.setLastModifiedBy(currentUser);
        wc.setLastModifiedDate(LocalDateTime.now());
        if (original.getProperties() != null) {
            wc.setProperties(new java.util.HashMap<>(original.getProperties()));
        }
        if (original.getMetadata() != null) {
            wc.setMetadata(new java.util.HashMap<>(original.getMetadata()));
        }

        Document savedWc = documentRepository.save(wc);

        // Register binary ownership for working copy
        contentReferenceService.attach(savedWc.getContentId(), OwnerType.WORKING_COPY, savedWc.getId());

        // --- mark original as checked-out ----------------------------------
        original.checkout(currentUser);
        documentRepository.save(original);

        log.info("Checked out document {} → working copy {} in folder {}",
                original.getId(), savedWc.getId(), destination.getId());

        return savedWc;
    }

    // ------------------------------------------------------------------ checkin

    /**
     * Check in a working copy. If content or metadata changed, a new version
     * is created before the working copy is soft-deleted.
     *
     * @param workingCopyId   ID of the working-copy document
     * @param keepCheckedOut  if {@code true}, a fresh working copy is created after checkin
     * @return the original document after checkin
     */
    public Document checkin(UUID workingCopyId, boolean keepCheckedOut) {
        return checkin(workingCopyId, keepCheckedOut, null, false);
    }

    /**
     * Check in a working copy with explicit version comment and major/minor flag.
     *
     * @param workingCopyId   ID of the working-copy document
     * @param keepCheckedOut  if {@code true}, a fresh working copy is created after checkin
     * @param comment         version comment (nullable)
     * @param majorVersion    if {@code true}, create a major version; otherwise minor
     * @return the original document after checkin
     */
    public Document checkin(UUID workingCopyId, boolean keepCheckedOut,
                            String comment, boolean majorVersion) {
        return checkin(workingCopyId, keepCheckedOut, comment, majorVersion, null);
    }

    /**
     * Check in a working copy with an optional uploaded file.
     * The file update, version creation, and working-copy cleanup run in one transaction.
     */
    public Document checkin(UUID workingCopyId, boolean keepCheckedOut,
                            String comment, boolean majorVersion, MultipartFile file) {
        Document wc = loadLiveDocument(workingCopyId);
        if (!wc.isWorkingCopy()) {
            throw new IllegalArgumentException("Node is not a working copy");
        }

        String currentUser = securityService.getCurrentUser();
        boolean isAdmin = securityService.hasRole("ROLE_ADMIN");
        if (!Objects.equals(wc.getCheckoutUser(), currentUser) && !isAdmin) {
            throw new SecurityException("Only checkout owner or admin can check in");
        }
        if (keepCheckedOut && !Objects.equals(wc.getCheckoutUser(), currentUser)) {
            throw new SecurityException("Only checkout owner can keep document checked out");
        }

        if (file != null && !file.isEmpty()) {
            updateWorkingCopyContent(wc, file, currentUser);
        }

        Document original = loadLiveDocument(wc.getWorkingCopyOf());

        boolean contentChanged = !Objects.equals(original.getContentId(), wc.getContentId());
        boolean metadataChanged = hasVersionableMetadataChanges(original, wc);

        // --- create a version if content or metadata changed ----------------
        if (contentChanged || metadataChanged) {
            try (InputStream contentStream = contentService.getContent(wc.getContentId())) {
                String versionComment = comment != null ? comment : "Check-in";
                String versionFilename = resolveVersionFilename(original, wc, file);
                versionService.createVersion(
                        original.getId(), contentStream, versionFilename,
                        versionComment, majorVersion);
                // Re-load original after version creation updated it
                original = loadLiveDocument(original.getId());
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Failed to create version during check-in: " + e.getMessage(), e);
            }
        }

        applyWorkingCopyState(original, wc);

        // --- clear checkout state on original ------------------------------
        original.checkin();
        original.setLastModifiedBy(currentUser);
        original.setLastModifiedDate(LocalDateTime.now());
        documentRepository.save(original);

        // --- soft-delete the working copy ----------------------------------
        wc.setDeleted(true);
        wc.setLastModifiedBy(currentUser);
        wc.setLastModifiedDate(LocalDateTime.now());
        documentRepository.save(wc);

        // Detach binary ownership for working copy
        contentReferenceService.detach(wc.getContentId(), OwnerType.WORKING_COPY, wc.getId());

        log.info("Checked in working copy {} → original {} (contentChanged={}, metadataChanged={})",
                workingCopyId, original.getId(), contentChanged, metadataChanged);

        if (keepCheckedOut) {
            return checkout(original.getId());
        }
        return original;
    }

    // ------------------------------------------------------------------ cancel

    /**
     * Cancel checkout: soft-delete working copy, clear checkout state on original.
     */
    public Document cancelCheckout(UUID documentOrWorkingCopyId) {
        Document target = loadLiveDocument(documentOrWorkingCopyId);

        Document wc;
        Document original;
        if (target.isWorkingCopy()) {
            wc = target;
            original = loadLiveDocument(target.getWorkingCopyOf());
        } else {
            original = target;
            wc = documentRepository.findWorkingCopyOf(original.getId()).orElse(null);
        }

        String currentUser = securityService.getCurrentUser();
        boolean isAdmin = securityService.hasRole("ROLE_ADMIN");
        if (!Objects.equals(original.getCheckoutUser(), currentUser) && !isAdmin) {
            throw new SecurityException("Only checkout owner or admin can cancel checkout");
        }

        original.checkin();
        documentRepository.save(original);

        if (wc != null) {
            wc.setDeleted(true);
            wc.setLastModifiedBy(currentUser);
            wc.setLastModifiedDate(LocalDateTime.now());
            documentRepository.save(wc);

            // Detach binary ownership for cancelled working copy
            contentReferenceService.detach(wc.getContentId(), OwnerType.WORKING_COPY, wc.getId());

            log.info("Cancelled checkout and soft-deleted working copy {}", wc.getId());
        }

        return original;
    }

    // ------------------------------------------------------------------ queries

    /**
     * Return the working copy of a checked-out document, or empty.
     */
    public Optional<Document> getWorkingCopy(UUID originalDocumentId) {
        return documentRepository.findWorkingCopyOf(originalDocumentId)
            .filter(this::isNodeVisible);
    }

    /**
     * Return the original document for a working copy, or empty.
     */
    public Optional<Document> getOriginal(UUID workingCopyId) {
        Document wc = loadLiveDocument(workingCopyId);
        if (!wc.isWorkingCopy() || wc.getWorkingCopyOf() == null) {
            return Optional.empty();
        }
        return documentRepository.findById(wc.getWorkingCopyOf())
                .filter(d -> !d.isDeleted())
                .filter(this::isNodeVisible);
    }

    /**
     * List all working copies owned by the given user.
     */
    public List<Document> getCheckedOutWorkingCopies(String userId) {
        return documentRepository.findWorkingCopiesByUser(userId).stream()
            .filter(this::isNodeVisible)
            .toList();
    }

    // ------------------------------------------------------------------ helpers

    private Document loadLiveDocument(UUID id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + id));
        if (doc.isDeleted() || !isNodeVisible(doc)) {
            throw new NoSuchElementException("Document not found: " + id);
        }
        return doc;
    }

    private Folder resolveDestination(Document original, UUID destinationFolderId) {
        if (destinationFolderId != null) {
            Folder dest = folderRepository.findById(destinationFolderId)
                    .orElseThrow(() -> new NoSuchElementException("Destination folder not found: " + destinationFolderId));
            if (!isNodeVisible(dest)) {
                throw new NoSuchElementException("Destination folder not found: " + destinationFolderId);
            }
            if (!securityService.hasPermission(dest, PermissionType.CREATE_CHILDREN)) {
                throw new SecurityException("No permission to create children in destination folder");
            }
            return dest;
        }
        if (original.getParent() instanceof Folder parentFolder) {
            return parentFolder;
        }
        throw new IllegalStateException("Document has no parent folder");
    }

    private boolean isNodeVisible(com.ecm.core.entity.Node node) {
        if (node == null) {
            return false;
        }
        if (!tenantWorkspaceScopeService.hasScopedTenantWorkspace()) {
            return true;
        }
        return tenantWorkspaceScopeService.isPathVisible(node.getPath());
    }

    private boolean hasVersionableMetadataChanges(Document original, Document wc) {
        return !Objects.equals(original.getProperties(), wc.getProperties())
            || !Objects.equals(original.getMetadata(), wc.getMetadata())
            || !Objects.equals(original.getDescription(), wc.getDescription())
            || !Objects.equals(original.getEncoding(), wc.getEncoding())
            || !Objects.equals(original.getFileExtension(), wc.getFileExtension());
    }

    private void applyWorkingCopyState(Document original, Document wc) {
        original.setDescription(wc.getDescription());
        original.setEncoding(wc.getEncoding());
        original.setFileExtension(wc.getFileExtension());
        original.setProperties(wc.getProperties() != null
            ? new java.util.HashMap<>(wc.getProperties())
            : new java.util.HashMap<>());
        original.setMetadata(wc.getMetadata() != null
            ? new java.util.HashMap<>(wc.getMetadata())
            : new java.util.HashMap<>());
    }

    private void updateWorkingCopyContent(Document wc, MultipartFile file, String currentUser) {
        String previousContentId = wc.getContentId();
        try {
            String contentId = contentService.storeContent(file);
            String mimeType = contentService.detectMimeType(contentId, file.getOriginalFilename());
            long fileSize = contentService.getContentSize(contentId);
            Map<String, Object> extracted = contentService.extractMetadata(contentId);

            wc.setContentId(contentId);
            wc.setMimeType(mimeType);
            wc.setFileSize(fileSize);
            wc.setFileExtension(FilenameUtils.getExtension(file.getOriginalFilename()));
            wc.setContentHash((String) extracted.get("contentHash"));
            String textContent = (String) extracted.get("textContent");
            if (textContent != null) {
                wc.setTextContent(textContent);
            }
            wc.setLastModifiedBy(currentUser);
            wc.setLastModifiedDate(LocalDateTime.now());
            documentRepository.save(wc);
            contentReferenceService.syncOwnerReference(
                previousContentId,
                contentId,
                OwnerType.WORKING_COPY,
                wc.getId()
            );
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to update working copy content before check-in: " + e.getMessage(), e);
        }
    }

    private String resolveVersionFilename(Document original, Document wc, MultipartFile file) {
        if (file != null && !file.isEmpty()) {
            String uploadedFilename = file.getOriginalFilename();
            if (uploadedFilename != null && !uploadedFilename.isBlank()) {
                return uploadedFilename;
            }
        }

        String originalName = original.getName();
        if (originalName == null || originalName.isBlank()) {
            String extension = normalizeExtension(wc.getFileExtension());
            return extension == null ? "document" : "document." + extension;
        }

        String extension = normalizeExtension(wc.getFileExtension());
        if (extension == null) {
            return originalName;
        }

        String baseName = FilenameUtils.getBaseName(originalName);
        if (baseName == null || baseName.isBlank()) {
            return originalName;
        }
        return baseName + "." + extension;
    }

    private String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return null;
        }
        return extension.startsWith(".") ? extension.substring(1) : extension;
    }
}
