package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "ratings", uniqueConstraints = {
    @UniqueConstraint(name = "uq_rating_node_user_scheme",
                      columnNames = {"node_id", "user_id", "scheme"})
}, indexes = {
    @Index(name = "idx_rating_node_id", columnList = "node_id"),
    @Index(name = "idx_rating_user_id", columnList = "user_id"),
    @Index(name = "idx_rating_scheme", columnList = "scheme")
})
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false)
    private Node node;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scheme", nullable = false, length = 20)
    private RatingScheme scheme;

    @Column(name = "score", nullable = false)
    private int score;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum RatingScheme {
        LIKES,
        FIVE_STAR
    }
}
