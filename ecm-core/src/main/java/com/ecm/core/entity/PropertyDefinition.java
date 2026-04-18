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
@Table(name = "property_definitions", indexes = {
    @Index(name = "idx_pd_name", columnList = "name")
})
@EqualsAndHashCode(callSuper = true, exclude = {"typeDefinition", "aspectDefinition", "constraints"})
public class PropertyDefinition extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "title")
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    private PropertyDataType dataType = PropertyDataType.TEXT;

    @Column(name = "mandatory")
    private boolean mandatory = false;

    @Column(name = "multi_valued")
    private boolean multiValued = false;

    @Column(name = "default_value")
    private String defaultValue;

    @Column(name = "indexed")
    private boolean indexed = true;

    @Column(name = "protected_field")
    private boolean protectedField = false;

    @Column(name = "encrypted", nullable = false)
    private boolean encrypted = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_definition_id")
    private TypeDefinition typeDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aspect_definition_id")
    private AspectDefinition aspectDefinition;

    @OneToMany(mappedBy = "propertyDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConstraintDefinition> constraints = new ArrayList<>();

    public String qualifiedName() {
        if (typeDefinition != null && typeDefinition.getModel() != null) {
            return typeDefinition.getModel().getPrefix() + ":" + name;
        }
        if (aspectDefinition != null && aspectDefinition.getModel() != null) {
            return aspectDefinition.getModel().getPrefix() + ":" + name;
        }
        return name;
    }
}
