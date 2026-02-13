package com.ecm.core.service;

import com.ecm.core.dto.TextDiffDto;
import com.ecm.core.dto.VersionCompareResultDto;
import com.ecm.core.dto.VersionDto;
import com.ecm.core.entity.*;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.entity.Version.VersionStatus;
import com.ecm.core.entity.AutomationRule.TriggerType;
import com.ecm.core.event.*;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.VersionRepository;
import com.ecm.core.util.LineDiffUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Locale;

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
    private final VersionLabelService versionLabelService;

    @Autowired
    @Lazy
    private RuleEngineService ruleEngineService;

    @Value("${ecm.rules.enabled:true}")
    private boolean rulesEnabled;

    private static final int MAX_COMPARE_TEXT_BYTES_HARD_LIMIT = 1_000_000;
    private static final int MAX_COMPARE_TEXT_LINES_HARD_LIMIT = 10_000;
    private static final int MAX_COMPARE_DIFF_CHARS_HARD_LIMIT = 400_000;
    
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
        String mimeType = contentService.detectMimeType(contentId, filename);
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
        Integer currentMaxVersion = versionRepository.findMaxVersionNumber(documentId);
        boolean isFirstVersion = currentMaxVersion == null || currentMaxVersion == 0;
        version.setMajorVersionFlag(majorVersion || isFirstVersion);
        
        // Set version numbers
        if (!isFirstVersion) {
            if (majorVersion) {
                document.incrementMajorVersion();
            } else {
                document.incrementMinorVersion();
            }
        }
        
        version.setMajorVersion(document.getMajorVersion());
        version.setMinorVersion(document.getMinorVersion());
        version.setVersionNumber(isFirstVersion ? 1 : currentMaxVersion + 1);
        String versionLabel = versionLabelService.generateLabel(document, version.getVersionNumber());
        version.setVersionLabel(versionLabel);
        
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

        eventPublisher.publishEvent(new VersionCreatedEvent(savedVersion, securityService.getCurrentUser()));

        // Trigger automation rules for new version
        triggerRulesForDocument(document, TriggerType.VERSION_CREATED);

        return savedVersion;
    }

    public Version createVersion(UUID documentId, MultipartFile file, String comment,
                                 boolean majorVersion) throws IOException {
        return createVersion(documentId, file.getInputStream(), file.getOriginalFilename(), 
            comment, majorVersion);
    }
    
    public List<Version> getVersionHistory(UUID documentId) {
        return getVersionHistory(documentId, false);
    }

    public List<Version> getVersionHistory(UUID documentId, boolean majorOnly) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));
        
        // Check permissions
        if (!securityService.hasPermission(document, PermissionType.READ)) {
            throw new SecurityException("No permission to view version history");
        }

        if (majorOnly) {
            return versionRepository.findMajorVersions(documentId);
        }

        return versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);
    }

    public Page<Version> getVersionHistory(UUID documentId, Pageable pageable, boolean majorOnly) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));

        if (!securityService.hasPermission(document, PermissionType.READ)) {
            throw new SecurityException("No permission to view version history");
        }

        if (majorOnly) {
            return versionRepository.findMajorVersions(documentId, pageable);
        }

        return versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId, pageable);
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
        
        eventPublisher.publishEvent(new VersionRevertedEvent(
            document, targetVersion, securityService.getCurrentUser()));
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
        
        long versionCount = versionRepository.countByDocumentId(document.getId());
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
        
        eventPublisher.publishEvent(new VersionDeletedEvent(version, securityService.getCurrentUser()));
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

    /**
     * Compare two versions and optionally compute a line-based diff for small text content.
     *
     * <p>This is intended for UI display (bounded by byte/line/char limits).</p>
     */
    public VersionCompareResultDto compareVersionsDetailed(
        UUID documentId,
        UUID fromVersionId,
        UUID toVersionId,
        boolean includeTextDiff,
        int maxBytes,
        int maxLines
    ) throws IOException {
        Version from = getVersion(fromVersionId);
        Version to = getVersion(toVersionId);

        UUID fromDocId = from.getDocument() != null ? from.getDocument().getId() : null;
        UUID toDocId = to.getDocument() != null ? to.getDocument().getId() : null;
        if (!Objects.equals(fromDocId, toDocId)) {
            throw new IllegalArgumentException("Versions belong to different documents");
        }
        if (documentId != null && !Objects.equals(documentId, fromDocId)) {
            throw new IllegalArgumentException("Versions do not belong to document: " + documentId);
        }

        boolean metadataChanged = !Objects.equals(from.getMimeType(), to.getMimeType())
            || !Objects.equals(from.getFileSize(), to.getFileSize());
        boolean contentChanged = !Objects.equals(from.getContentHash(), to.getContentHash());
        Long sizeDifference = null;
        if (from.getFileSize() != null && to.getFileSize() != null) {
            sizeDifference = to.getFileSize() - from.getFileSize();
        }

        TextDiffDto textDiff = null;
        if (includeTextDiff) {
            int safeMaxBytes = clamp(maxBytes, 1, MAX_COMPARE_TEXT_BYTES_HARD_LIMIT);
            int safeMaxLines = clamp(maxLines, 1, MAX_COMPARE_TEXT_LINES_HARD_LIMIT);
            textDiff = buildTextDiff(from, to, safeMaxBytes, safeMaxLines, MAX_COMPARE_DIFF_CHARS_HARD_LIMIT);
        }

        return new VersionCompareResultDto(
            VersionDto.from(from),
            VersionDto.from(to),
            metadataChanged,
            contentChanged,
            sizeDifference,
            textDiff
        );
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
        
        eventPublisher.publishEvent(new VersionPromotedEvent(version, securityService.getCurrentUser()));
    }
    
    private Map<String, Object> compareVersions(Version v1, Version v2) {
        Map<String, Object> comparison = new HashMap<>();

        Map<String, Object> version1 = new LinkedHashMap<>();
        version1.put("id", v1.getId());
        version1.put("label", v1.getVersionLabel());
        version1.put("createdDate", v1.getCreatedDate());
        version1.put("createdBy", v1.getCreatedBy());
        version1.put("fileSize", v1.getFileSize());
        version1.put("mimeType", v1.getMimeType());
        comparison.put("version1", version1);

        Map<String, Object> version2 = new LinkedHashMap<>();
        version2.put("id", v2.getId()); // may be null before persistence
        version2.put("label", v2.getVersionLabel());
        version2.put("createdDate", v2.getCreatedDate());
        version2.put("createdBy", v2.getCreatedBy());
        version2.put("fileSize", v2.getFileSize());
        version2.put("mimeType", v2.getMimeType());
        comparison.put("version2", version2);

        // Compare metadata (null-safe)
        boolean metadataChanged = !Objects.equals(v1.getMimeType(), v2.getMimeType()) ||
                                 !Objects.equals(v1.getFileSize(), v2.getFileSize());

        comparison.put("metadataChanged", metadataChanged);
        comparison.put("contentChanged", !Objects.equals(v1.getContentHash(), v2.getContentHash()));

        Long size1 = v1.getFileSize();
        Long size2 = v2.getFileSize();
        if (size1 != null && size2 != null) {
            comparison.put("sizeDifference", size2 - size1);
        } else {
            comparison.put("sizeDifference", null);
        }

        return comparison;
    }

    private record TextReadResult(String text, boolean truncated) {}

    private TextDiffDto buildTextDiff(
        Version from,
        Version to,
        int maxBytes,
        int maxLines,
        int maxChars
    ) throws IOException {
        String fromMime = normalizeMimeType(from.getMimeType());
        String toMime = normalizeMimeType(to.getMimeType());
        if (!isTextLikeMimeType(fromMime) || !isTextLikeMimeType(toMime)) {
            return new TextDiffDto(false, false, "Text diff is only available for text/* and json/xml content types.", null);
        }
        if (from.getContentId() == null || to.getContentId() == null) {
            return new TextDiffDto(false, false, "Missing content for one or both versions.", null);
        }

        TextReadResult fromText = readTextContent(from.getContentId(), maxBytes);
        TextReadResult toText = readTextContent(to.getContentId(), maxBytes);

        LineDiffUtils.DiffOutput diff = LineDiffUtils.diff(fromText.text(), toText.text(), maxLines, maxChars);
        boolean truncated = fromText.truncated() || toText.truncated() || diff.truncated();
        return new TextDiffDto(true, truncated, null, diff.diff());
    }

    private TextReadResult readTextContent(String contentId, int maxBytes) throws IOException {
        try (InputStream in = contentService.getContent(contentId)) {
            byte[] bytes = in.readNBytes(maxBytes + 1);
            boolean truncated = bytes.length > maxBytes;
            if (truncated) {
                bytes = Arrays.copyOf(bytes, maxBytes);
            }
            // For the supported mime types, UTF-8 decoding is a pragmatic default for UI diffs.
            return new TextReadResult(new String(bytes, StandardCharsets.UTF_8), truncated);
        }
    }

    private static boolean isTextLikeMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return false;
        }
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("text/")) {
            return true;
        }
        return normalized.equals("application/json")
            || normalized.equals("application/xml")
            || normalized.equals("text/xml")
            || normalized.equals("application/x-yaml")
            || normalized.equals("text/yaml")
            || normalized.equals("application/yaml");
    }

    private static String normalizeMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return null;
        }
        return mimeType.split(";")[0].trim();
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    /**
     * Trigger automation rules for a document.
     */
    private void triggerRulesForDocument(Document document, TriggerType triggerType) {
        if (!rulesEnabled) {
            return;
        }

        try {
            log.debug("Triggering {} rules for document: {} ({})",
                triggerType, document.getName(), document.getId());

            ruleEngineService.evaluateAndExecute(document, triggerType);
        } catch (Exception e) {
            // Log but don't fail the main operation
            log.error("Failed to trigger {} rules for document {}: {}",
                triggerType, document.getId(), e.getMessage(), e);
        }
    }
}
