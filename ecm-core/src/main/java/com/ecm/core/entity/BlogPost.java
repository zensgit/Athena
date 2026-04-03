package com.ecm.core.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "blog_posts", indexes = {
    @Index(name = "idx_bp_site_id", columnList = "site_id"),
    @Index(name = "idx_bp_status", columnList = "status"),
    @Index(name = "idx_bp_published_date", columnList = "published_date")
})
@EqualsAndHashCode(callSuper = true)
public class BlogPost extends BaseEntity {

    @Column(name = "site_id", nullable = false, length = 100)
    private String siteId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BlogStatus status = BlogStatus.DRAFT;

    @Column(name = "published_date")
    private LocalDateTime publishedDate;

    @Type(JsonType.class)
    @Column(name = "tags", columnDefinition = "jsonb")
    private List<String> tags = new ArrayList<>();

    public enum BlogStatus {
        DRAFT,
        PUBLISHED
    }
}
