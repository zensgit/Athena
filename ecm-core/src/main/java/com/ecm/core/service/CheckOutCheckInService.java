package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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

        // --- mark original as checked-out ----------------------------------
        original.checkout(currentUser);
        documentRepository.save(original);

        log.info("Checked out document {} → working copy {} in folder {}",
                original.getId(), savedWc.getId(), destination.getId());

        return savedWc;
    }

    // ------------------------------------------------------------------ checkin

    /**
     * Check in a working copy. The original document is updated and the
     * working copy is soft-deleted.
     *
     * @param workingCopyId   ID of the working-copy document
     * @param keepCheckedOut  if {@code true}, a fresh working copy is created after checkin
     * @return the original document after checkin
     */
    public Document checkin(UUID workingCopyId, boolean keepCheckedOut) {
        Document wc = loadLiveDocument(workingCopyId);
        if (!wc.isWorkingCopy()) {
            throw new IllegalStateException("Document is not a working copy");
        }

        String currentUser = securityService.getCurrentUser();
        boolean isAdmin = securityService.hasRole("ROLE_ADMIN");
        if (!Objects.equals(wc.getCheckoutUser(), currentUser) && !isAdmin) {
            throw new SecurityException("Only checkout owner or admin can check in");
        }
        if (keepCheckedOut && !Objects.equals(wc.getCheckoutUser(), currentUser)) {
            throw new SecurityException("Only checkout owner can keep document checked out");
        }

        Document original = loadLiveDocument(wc.getWorkingCopyOf());

        // --- propagate content changes from working copy → original ---------
        if (!Objects.equals(original.getContentId(), wc.getContentId())) {
            original.setContentId(wc.getContentId());
            original.setContentHash(wc.getContentHash());
            original.setFileSize(wc.getFileSize());
            original.setMimeType(wc.getMimeType());
            original.setEncoding(wc.getEncoding());
        }
        if (wc.getProperties() != null) {
            original.setProperties(new java.util.HashMap<>(wc.getProperties()));
        }

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

        log.info("Checked in working copy {} → original {}", workingCopyId, original.getId());

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
}
