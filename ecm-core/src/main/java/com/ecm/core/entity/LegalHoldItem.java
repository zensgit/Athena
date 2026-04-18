package com.ecm.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "legal_hold_items", uniqueConstraints = {
    @UniqueConstraint(name = "uq_legal_hold_item", columnNames = {"hold_id", "node_id"})
}, indexes = {
    @Index(name = "idx_legal_hold_item_hold", columnList = "hold_id"),
    @Index(name = "idx_legal_hold_item_node", columnList = "node_id")
})
public class LegalHoldItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hold_id", nullable = false)
    private LegalHold hold;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_id", nullable = false)
    private Node node;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 20)
    private Node.NodeType nodeType;

    @Column(name = "node_path", nullable = false, length = 2000)
    private String nodePath;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    @Column(name = "added_by", nullable = false, length = 255)
    private String addedBy;

    @PrePersist
    void onCreate() {
        if (addedAt == null) {
            addedAt = LocalDateTime.now();
        }
        if (node != null) {
            nodeType = node.getNodeType();
            nodePath = node.getPath();
        }
    }
}
