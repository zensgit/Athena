package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@Entity
@Table(name = "document_relations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"source_id", "target_id", "relation_type"})
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
    private String relationType; // e.g., "RELATED", "ATTACHMENT", "REPLACEMENT"

}
