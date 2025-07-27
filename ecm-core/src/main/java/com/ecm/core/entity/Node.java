package com.ecm.core.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.Type;

import java.util.*;

@Data
@Entity
@Table(name = "nodes", indexes = {
    @Index(name = "idx_node_path", columnList = "path"),
    @Index(name = "idx_node_parent", columnList = "parent_id"),
    @Index(name = "idx_node_type", columnList = "node_type"),
    @Index(name = "idx_node_name", columnList = "name")
})
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "node_type", discriminatorType = DiscriminatorType.STRING)
@EqualsAndHashCode(callSuper = true, exclude = {"parent", "children", "permissions", "tags", "categories"})
@ToString(callSuper = true, exclude = {"parent", "children", "permissions"})
public abstract class Node extends BaseEntity {
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "path", nullable = false, length = 1000)
    private String path;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Node parent;
    
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Node> children = new HashSet<>();
    
    @Type(JsonType.class)
    @Column(name = "properties", columnDefinition = "jsonb")
    private Map<String, Object> properties = new HashMap<>();
    
    @Type(JsonType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();
    
    @OneToMany(mappedBy = "node", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Permission> permissions = new HashSet<>();
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "node_tags",
        joinColumns = @JoinColumn(name = "node_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "node_categories",
        joinColumns = @JoinColumn(name = "node_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();
    
    @OneToMany(mappedBy = "node", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdDate DESC")
    private List<Comment> comments = new ArrayList<>();
    
    @Column(name = "is_locked", nullable = false)
    private boolean locked = false;
    
    @Column(name = "locked_by")
    private String lockedBy;
    
    @Column(name = "locked_date")
    private LocalDateTime lockedDate;
    
    @Column(name = "inherit_permissions", nullable = false)
    private boolean inheritPermissions = true;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NodeStatus status = NodeStatus.ACTIVE;
    
    public abstract NodeType getNodeType();
    
    public void addChild(Node child) {
        children.add(child);
        child.setParent(this);
    }
    
    public void removeChild(Node child) {
        children.remove(child);
        child.setParent(null);
    }
    
    public void addPermission(Permission permission) {
        permissions.add(permission);
        permission.setNode(this);
    }
    
    public void removePermission(Permission permission) {
        permissions.remove(permission);
        permission.setNode(null);
    }
    
    public void addTag(Tag tag) {
        tags.add(tag);
    }
    
    public void removeTag(Tag tag) {
        tags.remove(tag);
    }
    
    public void addCategory(Category category) {
        categories.add(category);
    }
    
    public void removeCategory(Category category) {
        categories.remove(category);
    }
    
    public String getFullPath() {
        if (parent != null) {
            return parent.getFullPath() + "/" + name;
        }
        return "/" + name;
    }
    
    @PrePersist
    @PreUpdate
    protected void updatePath() {
        this.path = getFullPath();
    }
}

enum NodeType {
    FOLDER,
    DOCUMENT
}

enum NodeStatus {
    ACTIVE,
    ARCHIVED,
    DELETED
}