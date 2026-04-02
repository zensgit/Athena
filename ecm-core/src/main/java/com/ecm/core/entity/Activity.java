package com.ecm.core.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Entity
@Table(name = "activities", indexes = {
    @Index(name = "idx_activity_user_id", columnList = "user_id"),
    @Index(name = "idx_activity_site_id", columnList = "site_id"),
    @Index(name = "idx_activity_node_id", columnList = "node_id"),
    @Index(name = "idx_activity_type", columnList = "activity_type"),
    @Index(name = "idx_activity_posted_at", columnList = "posted_at")
})
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "activity_type", nullable = false, length = 100)
    private String activityType;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "site_id", length = 100)
    private String siteId;

    @Column(name = "node_id")
    private UUID nodeId;

    @Column(name = "node_name")
    private String nodeName;

    @Type(JsonType.class)
    @Column(name = "summary", columnDefinition = "jsonb")
    private Map<String, Object> summary = new HashMap<>();

    @CreationTimestamp
    @Column(name = "posted_at", nullable = false, updatable = false)
    private LocalDateTime postedAt;
}
