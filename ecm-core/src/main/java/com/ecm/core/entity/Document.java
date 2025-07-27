package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    
    @Column(name = "content_hash")
    private String contentHash;
    
    @Column(name = "thumbnail_id")
    private String thumbnailId;
    
    @Column(name = "preview_available")
    private boolean previewAvailable = false;
    
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
    }
    
    public void checkin() {
        this.checkoutUser = null;
        this.checkoutDate = null;
    }
}