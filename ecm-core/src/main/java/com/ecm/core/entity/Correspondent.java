package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * Correspondent Entity
 * 
 * Represents a person, company, or organization that documents are associated with.
 * e.g., "China Telecom", "Amazon AWS", "John Doe"
 */
@Data
@Entity
@Table(name = "correspondents", indexes = {
    @Index(name = "idx_correspondent_name", columnList = "name", unique = true)
})
@EqualsAndHashCode(callSuper = true)
public class Correspondent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    /**
     * Matching algorithm to use for auto-assignment.
     * Values: AUTO, ANY, ALL, EXACT, REGEX, FUZZY
     */
    @Column(name = "match_algorithm")
    private String matchAlgorithm = "AUTO";

    /**
     * The pattern to match against (keywords, regex, etc.)
     */
    @Column(name = "match_pattern", columnDefinition = "TEXT")
    private String matchPattern;

    @Column(name = "is_insensitive")
    private boolean insensitive = true;

    // Optional: Contact details
    @Column(name = "email")
    private String email;
    
    @Column(name = "phone")
    private String phone;
}
