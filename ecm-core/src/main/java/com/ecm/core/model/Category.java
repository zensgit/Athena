package com.ecm.core.model;

import javax.persistence.*;
import java.util.*;

@Entity
@Table(name = "ecm_categories")
public class Category {
    @Id
    @GeneratedValue(generator = "uuid2")
    private String id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(length = 500)
    private String description;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;
    
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<Category> children = new ArrayList<>();
    
    @ManyToMany(mappedBy = "categories")
    private Set<Node> nodes = new HashSet<>();
    
    @Column(nullable = false)
    private String path;
    
    @Column(nullable = false)
    private Integer level = 0;
    
    @Column(nullable = false)
    private Date created;
    
    @Column(nullable = false)
    private String creator;
    
    @Column(nullable = false)
    private Boolean active = true;
    
    @PrePersist
    protected void onCreate() {
        created = new Date();
        updatePath();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatePath();
    }
    
    private void updatePath() {
        if (parent != null) {
            path = parent.getPath() + "/" + name;
            level = parent.getLevel() + 1;
        } else {
            path = "/" + name;
            level = 0;
        }
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Category getParent() { return parent; }
    public void setParent(Category parent) { 
        this.parent = parent;
        updatePath();
    }
    
    public List<Category> getChildren() { return children; }
    public void setChildren(List<Category> children) { this.children = children; }
    
    public Set<Node> getNodes() { return nodes; }
    public void setNodes(Set<Node> nodes) { this.nodes = nodes; }
    
    public String getPath() { return path; }
    
    public Integer getLevel() { return level; }
    
    public Date getCreated() { return created; }
    public void setCreated(Date created) { this.created = created; }
    
    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }
    
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}