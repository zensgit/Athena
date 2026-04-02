package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@Entity
@Table(name = "document_relations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"source_id", "target_id", "relation_type"})
}, indexes = {
    @Index(name = "idx_dr_source_id", columnList = "source_id"),
    @Index(name = "idx_dr_target_id", columnList = "target_id"),
    @Index(name = "idx_dr_assoc_type", columnList = "assoc_type"),
    @Index(name = "idx_dr_direction", columnList = "direction")
})
@EqualsAndHashCode(callSuper = true)
public class DocumentRelation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private Document source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", nullable = false)
    private Document target;

    @Column(name = "relation_type", nullable = false)
    private String relationType;

    @Column(name = "assoc_type", length = 200)
    private String assocType;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction")
    private AssocDirection direction = AssocDirection.PEER;

    @Column(name = "order_index")
    private Integer orderIndex;
}
