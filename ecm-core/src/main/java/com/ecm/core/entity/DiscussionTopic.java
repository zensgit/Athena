package com.ecm.core.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "discussion_topics", indexes = {
    @Index(name = "idx_dt_site_id", columnList = "site_id"),
    @Index(name = "idx_dt_status", columnList = "status")
})
@EqualsAndHashCode(callSuper = true, exclude = {"replies"})
public class DiscussionTopic extends BaseEntity {

    @Column(name = "site_id", nullable = false, length = 100)
    private String siteId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TopicStatus status = TopicStatus.OPEN;

    @Type(JsonType.class)
    @Column(name = "tags", columnDefinition = "jsonb")
    private List<String> tags = new ArrayList<>();

    @OneToMany(mappedBy = "topic", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdDate ASC")
    private List<DiscussionReply> replies = new ArrayList<>();

    public enum TopicStatus {
        OPEN,
        CLOSED,
        PINNED
    }
}
