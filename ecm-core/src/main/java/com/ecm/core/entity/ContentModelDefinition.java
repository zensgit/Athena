package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "content_model_definitions", indexes = {
    @Index(name = "idx_cmd_prefix", columnList = "prefix", unique = true),
    @Index(name = "idx_cmd_status", columnList = "status")
})
@EqualsAndHashCode(callSuper = true, exclude = {"types", "aspects"})
public class ContentModelDefinition extends BaseEntity {

    @Column(name = "namespace_uri", nullable = false, unique = true)
    private String namespaceUri;

    @Column(name = "prefix", nullable = false, unique = true, length = 50)
    private String prefix;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "author")
    private String author;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ModelStatus status = ModelStatus.DRAFT;

    @Column(name = "version_label")
    private String versionLabel = "1.0";

    @OneToMany(mappedBy = "model", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name ASC")
    private List<TypeDefinition> types = new ArrayList<>();

    @OneToMany(mappedBy = "model", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name ASC")
    private List<AspectDefinition> aspects = new ArrayList<>();

    public String qualifiedName() {
        return prefix + ":" + name;
    }
}
