package com.ecm.core.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing a user's saved search configuration.
 */
@Entity
@Table(name = "saved_searches")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Type(JsonType.class)
    @Column(name = "query_params", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> queryParams;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}