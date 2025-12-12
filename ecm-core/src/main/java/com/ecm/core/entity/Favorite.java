package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a user's favorite node (document or folder).
 */
@Entity
@Table(name = "favorites", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "node_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "node_id", nullable = false)
    private Node node;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}