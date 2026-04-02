package com.ecm.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(name = "sites", indexes = {
    @Index(name = "idx_site_site_id", columnList = "site_id", unique = true),
    @Index(name = "idx_site_status", columnList = "status"),
    @Index(name = "idx_site_visibility", columnList = "visibility")
})
@EqualsAndHashCode(callSuper = true, exclude = {"rootFolder"})
@ToString(callSuper = true, exclude = {"rootFolder"})
public class Site extends BaseEntity {

    @Column(name = "site_id", nullable = false, unique = true, length = 100)
    private String siteId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private SiteVisibility visibility = SiteVisibility.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SiteStatus status = SiteStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "root_folder_id")
    private Folder rootFolder;

    public enum SiteVisibility {
        PUBLIC,
        MODERATED,
        PRIVATE
    }

    public enum SiteStatus {
        ACTIVE,
        ARCHIVED
    }
}
