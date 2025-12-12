package com.ecm.core.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@Entity
@Table(name = "versions", indexes = {
    @Index(name = "idx_version_document", columnList = "document_id"),
    @Index(name = "idx_version_number", columnList = "version_number")
})
@EqualsAndHashCode(callSuper = true, exclude = {"document"})
@ToString(callSuper = true, exclude = {"document"})
public class Version extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;
    
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;
    
    @Column(name = "version_label")
    private String versionLabel;
    
    @Column(name = "major_version", nullable = false)
    private Integer majorVersion;
    
    @Column(name = "minor_version", nullable = false)
    private Integer minorVersion;
    
    @Column(name = "content_id", nullable = false)
    private String contentId;
    
    @Column(name = "mime_type", nullable = false)
    private String mimeType;
    
    @Column(name = "file_size", nullable = false)
    private Long fileSize;
    
    @Column(name = "content_hash")
    private String contentHash;
    
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;
    
    @Type(JsonType.class)
    @Column(name = "changes", columnDefinition = "jsonb")
    private Map<String, Object> changes = new HashMap<>();
    
    @Column(name = "is_major_version")
    private boolean majorVersionFlag;
    
    @Column(name = "frozen_date")
    private LocalDateTime frozenDate;
    
    @Column(name = "frozen_by")
    private String frozenBy;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private VersionStatus status = VersionStatus.RELEASED;
    
    public String getVersionString() {
        return majorVersion + "." + minorVersion;
    }

    public enum VersionStatus {
        DRAFT,
        RELEASED,
        SUPERSEDED,
        OBSOLETE
    }
}