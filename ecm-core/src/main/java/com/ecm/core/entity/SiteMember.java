package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "site_members", uniqueConstraints = {
    @UniqueConstraint(name = "uq_site_member", columnNames = {"site_id", "username"})
}, indexes = {
    @Index(name = "idx_sm_site_id", columnList = "site_id"),
    @Index(name = "idx_sm_username", columnList = "username")
})
public class SiteMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(name = "username", nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private SiteMemberRole role = SiteMemberRole.CONSUMER;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    public enum SiteMemberRole {
        MANAGER,
        COLLABORATOR,
        CONTRIBUTOR,
        CONSUMER
    }
}
