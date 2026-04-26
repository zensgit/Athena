package com.ecm.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(
    name = "localized_content",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_localized_content_node_locale", columnNames = {"node_id", "locale"})
    },
    indexes = {
        @Index(name = "idx_localized_content_node_id", columnList = "node_id")
    }
)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"node"})
public class LocalizedContent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false)
    private Node node;

    @Column(nullable = false, length = 20)
    private String locale;

    @Column(length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;
}
