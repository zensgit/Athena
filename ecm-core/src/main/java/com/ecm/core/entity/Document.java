package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Table(name = "documents", indexes = {
    @Index(name = "idx_document_content_id", columnList = "content_id"),
    @Index(name = "idx_document_mime_type", columnList = "mime_type")
})
@DiscriminatorValue("DOCUMENT")
@EqualsAndHashCode(callSuper = true, exclude = {"versions", "currentVersion"})
@ToString(callSuper = true, exclude = {"versions", "currentVersion"})
public class Document extends Node {
    
    @Column(name = "content_id")
    private String contentId;
    
    @Column(name = "mime_type", nullable = false)
    private String mimeType;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "file_extension")
    private String fileExtension;
    
    @Column(name = "encoding")
    private String encoding;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_version_id")
    private Version currentVersion;
    
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("versionNumber DESC")
    private List<Version> versions = new ArrayList<>();
    
    @Column(name = "version_label")
    private String versionLabel;
    
    @Column(name = "major_version")
    private Integer majorVersion = 1;
    
    @Column(name = "minor_version")
    private Integer minorVersion = 0;
    
    @Column(name = "is_versioned")
    private boolean versioned = true;
    
    @Column(name = "checkout_user")
    private String checkoutUser;
    
    @Column(name = "checkout_date")
    private LocalDateTime checkoutDate;

    @Column(name = "checkout_baseline_version_id")
    private String checkoutBaselineVersionId;

    @Column(name = "checkout_baseline_version_label")
    private String checkoutBaselineVersionLabel;

    @Column(name = "working_copy_of")
    private UUID workingCopyOf;

    @Column(name = "is_working_copy")
    private boolean workingCopy = false;

    @Column(name = "content_hash")
    private String contentHash;
    
    @Column(name = "thumbnail_id")
    private String thumbnailId;
    
    @Column(name = "preview_available")
    private boolean previewAvailable = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "preview_status")
    private PreviewStatus previewStatus;

    @Column(name = "preview_failure_reason", columnDefinition = "TEXT")
    private String previewFailureReason;

    @Column(name = "preview_failure_count")
    private Integer previewFailureCount;

    @Column(name = "preview_failed_at")
    private LocalDateTime previewFailedAt;

    @Column(name = "preview_last_failure_reason", columnDefinition = "TEXT")
    private String previewLastFailureReason;

    @Column(name = "preview_failure_content_hash")
    private String previewFailureContentHash;

    @Column(name = "preview_last_updated")
    private LocalDateTime previewLastUpdated;

    @Column(name = "preview_content_hash")
    private String previewContentHash;
    
    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;
    
    @Column(name = "language")
    private String language;
    
    @Column(name = "page_count")
    private Integer pageCount;
    
    @Override
    public NodeType getNodeType() {
        return NodeType.DOCUMENT;
    }

    @Override
    public Long getSize() {
        return fileSize;
    }

    public void addVersion(Version version) {
        versions.add(version);
        version.setDocument(this);
        version.setVersionNumber(versions.size());
    }
    
    public String getVersionString() {
        return majorVersion + "." + minorVersion;
    }
    
    public void incrementMajorVersion() {
        majorVersion++;
        minorVersion = 0;
    }
    
    public void incrementMinorVersion() {
        minorVersion++;
    }
    
    public boolean isCheckedOut() {
        return checkoutUser != null;
    }
    
    public void checkout(String userId) {
        this.checkoutUser = userId;
        this.checkoutDate = LocalDateTime.now();
        this.checkoutBaselineVersionId = currentVersion != null && currentVersion.getId() != null
            ? currentVersion.getId().toString()
            : null;
        this.checkoutBaselineVersionLabel = versionLabel != null ? versionLabel : getVersionString();
    }
    
    public void checkin() {
        this.checkoutUser = null;
        this.checkoutDate = null;
        this.checkoutBaselineVersionId = null;
        this.checkoutBaselineVersionLabel = null;
        this.workingCopyOf = null;
        this.workingCopy = false;
    }
}
