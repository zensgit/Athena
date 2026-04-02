package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(
    name = "follow_subscriptions",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_follow_subscription_user_target",
            columnNames = {"user_id", "target_type", "target_id"}
        )
    },
    indexes = {
        @Index(name = "idx_follow_subscription_user_id", columnList = "user_id"),
        @Index(name = "idx_follow_subscription_target_type_target_id", columnList = "target_type, target_id")
    }
)
public class FollowSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private FollowTargetType targetType;

    @Column(name = "target_id", nullable = false, length = 255)
    private String targetId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
