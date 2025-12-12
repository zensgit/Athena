package com.ecm.core.model;

import com.ecm.core.entity.Node;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tags")
public class Tag {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "color", nullable = false)
    private String color = "#1976d2";

    @ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY)
    private Set<Node> nodes = new HashSet<>();

    @Column(name = "created_date", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date created;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String creator;

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    @PrePersist
    protected void onCreate() {
        if (created == null) {
            created = new Date();
        }
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public Set<Node> getNodes() { return nodes; }
    public void setNodes(Set<Node> nodes) { this.nodes = nodes; }

    public Date getCreated() { return created; }
    public void setCreated(Date created) { this.created = created; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public Integer getUsageCount() { return usageCount; }
    public void setUsageCount(Integer usageCount) { this.usageCount = usageCount; }

    public void incrementUsage() {
        this.usageCount++;
    }

    public void decrementUsage() {
        if (this.usageCount > 0) {
            this.usageCount--;
        }
    }
}
