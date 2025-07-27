package com.ecm.core.service;

import com.ecm.core.entity.*;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.VersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class VersionService {
    
    private final VersionRepository versionRepository;
    private final DocumentRepository documentRepository;
    private final ContentService contentService;
    private final SecurityService securityService;
    private final ApplicationEventPublisher eventPublisher;
    
    public Version createVersion(UUID documentId, InputStream content, String filename, 
                                 String comment, boolean majorVersion) throws IOException {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));
        
        // Check permissions
        if (!securityService.hasPermission(document, PermissionType.WRITE)) {
            throw new SecurityException("No permission to create version for document: " + document.getName());
        }
        
        // Check if document is checked out
        if (document.isCheckedOut() && !document.getCheckoutUser().equals(securityService.getCurrentUser())) {
            throw new IllegalStateException("Document is checked out by: " + document.getCheckoutUser());
        }
        
        // Store content
        String contentId = contentService.storeContent(content, filename);
        String mimeType = contentService.detectMimeType(contentId);
        long fileSize = contentService.getContentSize(contentId);
        
        // Extract content hash
        Map<String, Object> metadata = contentService.extractMetadata(contentId);
        String contentHash = (String) metadata.get("contentHash");
        
        // Create version
        Version version = new Version();
        version.setDocument(document);
        version.setContentId(contentId);
        version.setMimeType(mimeType);
        version.setFileSize(fileSize);
        version.setContentHash(contentHash);
        version.setComment(comment);
        version.setMajorVersionFlag(majorVersion);
        
        // Set version numbers
        if (majorVersion) {
            document.incrementMajorVersion();
        } else {
            document.incrementMinorVersion();
        }
        
        version.setMajorVersion(document.getMajorVersion());
        version.setMinorVersion(document.getMinorVersion());
        version.setVersionNumber(versionRepository.findMaxVersionNumber(documentId) + 1);
        version.setVersionLabel(document.getVersionString());
        
        // Record changes
        if (document.getCurrentVersion() != null) {
            Map<String, Object> changes = compareVersions(document.getCurrentVersion(), version);
            version.setChanges(changes);
        }
        
        Version savedVersion = versionRepository.save(version);
        
        // Update document
        document.setCurrentVersion(savedVersion);
        document.setContentId(contentId);
        document.setMimeType(mimeType);
        document.setFileSize(fileSize);
        document.setContentHash(contentHash);
        document.setVersionLabel(savedVersion.getVersionLabel());
        
        // Extract and store text content
        String textContent = (String) metadata.get("textContent");
        if (textContent != null) {
            document.setTextContent(textContent);
        }
        
        documentRepository.save(document);
        
        eventPublisher.publishEvent(new VersionCreatedEvent(savedVersion));
        
        return savedVersion;
    }
    
    public Version createVersion(UUID documentId, MultipartFile file, String comment, 
                                 boolean majorVersion) throws IOException {
        return createVersion(documentId, file.getInputStream(), file.getOriginalFilename(), 
            comment, majorVersion);
    }
    
    public List<Version> getVersionHistory(UUID documentId) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));
        
        // Check permissions
        if (!securityService.hasPermission(document, PermissionType.READ)) {
            throw new SecurityException("No permission to view version history");
        }
        
        return versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);
    }
    
    public Version getVersion(UUID versionId) {
        Version version = versionRepository.findById(versionId)
            .orElseThrow(() -> new NoSuchElementException("Version not found: " + versionId));
        
        // Check permissions on document
        if (!securityService.hasPermission(version.getDocument(), PermissionType.READ)) {
            throw new SecurityException("No permission to view version");
        }
        
        return version;
    }
    
    public Version getVersionByNumber(UUID documentId, Integer versionNumber) {
        return versionRepository.findByDocumentIdAndVersionNumber(documentId, versionNumber)
            .orElseThrow(() -> new NoSuchElementException(
                "Version not found: " + versionNumber + " for document: " + documentId));
    }
    
    public Version getVersionByLabel(UUID documentId, String label) {
        return versionRepository.findByDocumentIdAndLabel(documentId, label)
            .orElseThrow(() -> new NoSuchElementException(
                "Version not found with label: " + label + " for document: " + documentId));
    }
    
    public void revertToVersion(UUID documentId, UUID versionId) throws IOException {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));
        
        Version targetVersion = getVersion(versionId);
        
        // Check permissions
        if (!securityService.hasPermission(document, PermissionType.WRITE)) {
            throw new SecurityException("No permission to revert document version");
        }
        
        // Check if document belongs to version
        if (!targetVersion.getDocument().getId().equals(documentId)) {
            throw new IllegalArgumentException("Version does not belong to document");
        }
        
        // Create new version with content from target version
        String comment = "Reverted to version " + targetVersion.getVersionLabel();
        try (InputStream content = contentService.getContent(targetVersion.getContentId())) {
            createVersion(documentId, content, document.getName(), comment, false);
        }
        
        eventPublisher.publishEvent(new VersionRevertedEvent(document, targetVersion));
    }
    
    public void deleteVersion(UUID versionId) {
        Version version = getVersion(versionId);
        Document document = version.getDocument();
        
        // Check permissions
        if (!securityService.hasPermission(document, PermissionType.DELETE) && 
            !securityService.hasRole("ROLE_ADMIN")) {
            throw new SecurityException("No permission to delete version");
        }
        
        // Cannot delete current version
        if (document.getCurrentVersion() != null && 
            document.getCurrentVersion().getId().equals(versionId)) {
            throw new IllegalStateException("Cannot delete current version");
        }
        
        // Cannot delete if it's the only version
        long versionCount = versionRepository.count((root, query, cb) -> 
            cb.equal(root.get("document").get("id"), document.getId()));
        
        if (versionCount <= 1) {
            throw new IllegalStateException("Cannot delete the only version");
        }
        
        versionRepository.delete(version);
        
        // Try to delete content if not referenced
        try {
            contentService.deleteContent(version.getContentId());
        } catch (IOException e) {
            log.warn("Failed to delete content for version: {}", versionId, e);
        }
        
        eventPublisher.publishEvent(new VersionDeletedEvent(version));
    }
    
    public void freezeVersion(UUID versionId) {
        Version version = getVersion(versionId);
        
        // Check permissions
        if (!securityService.hasPermission(version.getDocument(), PermissionType.WRITE)) {
            throw new SecurityException("No permission to freeze version");
        }
        
        version.setStatus(VersionStatus.SUPERSEDED);
        version.setFrozenDate(LocalDateTime.now());
        version.setFrozenBy(securityService.getCurrentUser());
        
        versionRepository.save(version);
    }
    
    public Map<String, Object> compareVersions(UUID versionId1, UUID versionId2) {
        Version version1 = getVersion(versionId1);
        Version version2 = getVersion(versionId2);
        
        // Ensure versions belong to same document
        if (!version1.getDocument().getId().equals(version2.getDocument().getId())) {
            throw new IllegalArgumentException("Versions belong to different documents");
        }
        
        return compareVersions(version1, version2);
    }
    
    public List<Version> getMajorVersions(UUID documentId) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));
        
        // Check permissions
        if (!securityService.hasPermission(document, PermissionType.READ)) {
            throw new SecurityException("No permission to view versions");
        }
        
        return versionRepository.findMajorVersions(documentId);
    }
    
    public InputStream getVersionContent(UUID versionId) throws IOException {
        Version version = getVersion(versionId);
        return contentService.getContent(version.getContentId());
    }
    
    public void promoteVersion(UUID versionId) {
        Version version = getVersion(versionId);
        Document document = version.getDocument();
        
        // Check permissions
        if (!securityService.hasPermission(document, PermissionType.WRITE)) {
            throw new SecurityException("No permission to promote version");
        }
        
        // Update document to use this version
        document.setCurrentVersion(version);
        document.setContentId(version.getContentId());
        document.setMimeType(version.getMimeType());
        document.setFileSize(version.getFileSize());
        document.setContentHash(version.getContentHash());
        document.setVersionLabel(version.getVersionLabel());
        document.setMajorVersion(version.getMajorVersion());
        document.setMinorVersion(version.getMinorVersion());
        
        documentRepository.save(document);
        
        eventPublisher.publishEvent(new VersionPromotedEvent(version));
    }
    
    private Map<String, Object> compareVersions(Version v1, Version v2) {
        Map<String, Object> comparison = new HashMap<>();
        
        comparison.put("version1", Map.of(
            "id", v1.getId(),
            "label", v1.getVersionLabel(),
            "createdDate", v1.getCreatedDate(),
            "createdBy", v1.getCreatedBy(),
            "fileSize", v1.getFileSize(),
            "mimeType", v1.getMimeType()
        ));
        
        comparison.put("version2", Map.of(
            "id", v2.getId(),
            "label", v2.getVersionLabel(),
            "createdDate", v2.getCreatedDate(),
            "createdBy", v2.getCreatedBy(),
            "fileSize", v2.getFileSize(),
            "mimeType", v2.getMimeType()
        ));
        
        // Compare metadata
        boolean metadataChanged = !v1.getMimeType().equals(v2.getMimeType()) ||
                                 !v1.getFileSize().equals(v2.getFileSize());
        
        comparison.put("metadataChanged", metadataChanged);
        comparison.put("contentChanged", !v1.getContentHash().equals(v2.getContentHash()));
        comparison.put("sizeDifference", v2.getFileSize() - v1.getFileSize());
        
        return comparison;
    }
}