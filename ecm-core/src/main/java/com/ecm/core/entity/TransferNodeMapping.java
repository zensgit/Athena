package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "transfer_node_mappings", indexes = {
    @Index(name = "idx_transfer_node_mapping_root_folder", columnList = "root_folder_id"),
    @Index(name = "idx_transfer_node_mapping_local_node", columnList = "local_node_id"),
    @Index(name = "idx_transfer_node_mapping_source_scope", columnList = "root_folder_id,source_repository_id,source_node_id", unique = true)
})
public class TransferNodeMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "root_folder_id", nullable = false)
    private UUID rootFolderId;

    @Column(name = "source_repository_id", nullable = false, length = 255)
    private String sourceRepositoryId;

    @Column(name = "source_node_id", nullable = false)
    private UUID sourceNodeId;

    @Column(name = "local_node_id", nullable = false)
    private UUID localNodeId;

    @Column(name = "last_source_modified_at")
    private LocalDateTime lastSourceModifiedAt;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
