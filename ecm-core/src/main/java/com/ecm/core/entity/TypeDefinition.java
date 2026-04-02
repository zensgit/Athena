package com.ecm.core.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "type_definitions", indexes = {
    @Index(name = "idx_td_name", columnList = "name"),
    @Index(name = "idx_td_model_id", columnList = "model_id")
})
@EqualsAndHashCode(callSuper = true, exclude = {"properties", "model"})
public class TypeDefinition extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "title")
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "parent_name", length = 200)
    private String parentName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id", nullable = false)
    private ContentModelDefinition model;

    @Type(JsonType.class)
    @Column(name = "mandatory_aspects", columnDefinition = "jsonb")
    private List<String> mandatoryAspects = new ArrayList<>();

    @OneToMany(mappedBy = "typeDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name ASC")
    private List<PropertyDefinition> properties = new ArrayList<>();

    public String qualifiedName() {
        return model != null ? model.getPrefix() + ":" + name : name;
    }
}
