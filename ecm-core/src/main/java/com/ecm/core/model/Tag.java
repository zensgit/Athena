package com.ecm.core.model;

import javax.persistence.*;
import java.util.*;

@Entity
@Table(name = "ecm_tags")
public class Tag {
    @Id
    @GeneratedValue(generator = "uuid2")
    private String id;
    
    @Column(nullable = false, unique = true)
    private String name;
    
    @Column(length = 500)
    private String description;
    
    @Column(nullable = false)
    private String color = "#1976d2";
    
    @ManyToMany(mappedBy = "tags")
    private Set<Node> nodes = new HashSet<>();
    
    @Column(nullable = false)
    private Date created;
    
    @Column(nullable = false)
    private String creator;
    
    @Column(nullable = false)
    private Integer usageCount = 0;
    
    @PrePersist
    protected void onCreate() {
        created = new Date();
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
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