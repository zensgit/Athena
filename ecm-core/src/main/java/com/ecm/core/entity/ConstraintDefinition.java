package com.ecm.core.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;

import java.util.HashMap;
import java.util.Map;

@Data
@Entity
@Table(name = "constraint_definitions")
@EqualsAndHashCode(callSuper = true, exclude = {"propertyDefinition"})
public class ConstraintDefinition extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "constraint_type", nullable = false)
    private ConstraintType constraintType;

    @Type(JsonType.class)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private Map<String, Object> parameters = new HashMap<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_definition_id", nullable = false)
    private PropertyDefinition propertyDefinition;
}
