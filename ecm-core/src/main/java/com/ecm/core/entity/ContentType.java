package com.ecm.core.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Definition of a Content Type (e.g., "Invoice", "Contract").
 * Defines the schema for custom metadata.
 */
@Data
@Entity
@Table(name = "content_types")
@EqualsAndHashCode(callSuper = true)
public class ContentType extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name; // System name: "ecm:invoice"

    @Column(nullable = false)
    private String displayName; // UI name: "Invoice"

    private String description;

    @Column(name = "parent_type")
    private String parentType; // Inheritance: "ecm:document"

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<PropertyDefinition> properties = new ArrayList<>();

    @Data
    public static class PropertyDefinition {
        private String name;        // "amount"
        private String title;       // "Total Amount"
        private String type;        // "text", "number", "date", "boolean", "list"
        private boolean required;
        private boolean searchable;
        private String defaultValue;
        private List<String> options; // For list types
        private String regex;       // Validation regex
    }
}
