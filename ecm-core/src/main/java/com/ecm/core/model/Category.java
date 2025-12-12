package com.ecm.core.model;

import com.ecm.core.entity.Node;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<Category> children = new ArrayList<>();

    @ManyToMany(mappedBy = "categories")
    private Set<Node> nodes = new HashSet<>();

    @Column(name = "path", nullable = false, length = 1000)
    private String path;

    @Column(name = "level", nullable = false)
    private Integer level = 0;

    @Column(name = "created_date", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date created;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String creator;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "icon")
    private String icon;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @PrePersist
    protected void onCreate() {
        Date now = new Date();
        if (created == null) {
            created = now;
        }
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
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

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

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
