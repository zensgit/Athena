package com.ecm.core.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.Type;

import com.ecm.core.model.Category;
import com.ecm.core.model.Comment;
import com.ecm.core.model.Tag;

import java.time.LocalDateTime;
import java.util.*;

@Data
@Entity
@Table(name = "nodes", indexes = {
    @Index(name = "idx_node_path", columnList = "path"),
    @Index(name = "idx_node_parent", columnList = "parent_id"),
    @Index(name = "idx_node_type", columnList = "node_type"),
    @Index(name = "idx_node_name", columnList = "name"),
    @Index(name = "idx_node_archive_status", columnList = "archive_status")
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

    @Column(name = "type_qname", length = 200)
    private String typeQName;
    
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
    @Column(name = "encrypted_properties", columnDefinition = "jsonb")
    private Map<String, String> encryptedProperties = new HashMap<>();
    
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
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "node_aspects", joinColumns = @JoinColumn(name = "node_id"))
    @Column(name = "aspect_name", length = 200)
    private Set<String> aspects = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "correspondent_id")
    private Correspondent correspondent;

    @OneToMany(mappedBy = "node", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("created DESC")
    private List<Comment> comments = new ArrayList<>();
    
    @Column(name = "is_locked", nullable = false)
    private boolean locked = false;
    
    @Column(name = "locked_by")
    private String lockedBy;
    
    @Column(name = "locked_date")
    private LocalDateTime lockedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "lock_lifetime")
    private LockLifetime lockLifetime;

    @Column(name = "lock_expires_at")
    private LocalDateTime lockExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "lock_type")
    private LockType lockType;

    @Column(name = "lock_additional_info")
    private String lockAdditionalInfo;

    @Column(name = "lock_deep")
    private boolean lockDeep = false;
    
    @Column(name = "inherit_permissions", nullable = false)
    private boolean inheritPermissions = true;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NodeStatus status = NodeStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "archive_status", nullable = false, length = 20)
    private ArchiveStatus archiveStatus = ArchiveStatus.LIVE;

    @Column(name = "archived_date")
    private LocalDateTime archivedDate;

    @Column(name = "archived_by")
    private String archivedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "archive_store_tier", nullable = false, length = 20)
    private ArchiveStoreTier archiveStoreTier = ArchiveStoreTier.HOT;
    
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

    /**
     * Check if this node is a folder
     */
    public boolean isFolder() {
        return getNodeType() == NodeType.FOLDER;
    }

    /**
     * Get file size (for documents) or null (for folders)
     */
    public Long getSize() {
        return null; // Override in Document subclass
    }

    @PrePersist
    @PreUpdate
    protected void updatePath() {
        this.path = getFullPath();
    }

    public enum NodeType {
        FOLDER,
        DOCUMENT
    }

    public enum NodeStatus {
        ACTIVE,
        ARCHIVED,
        DELETED,
        DRAFT,
        PENDING_APPROVAL,
        APPROVED,
        REJECTED
    }

    public enum ArchiveStatus {
        LIVE,
        ARCHIVED,
        RESTORING
    }

    public enum ArchiveStoreTier {
        HOT,
        WARM,
        COLD,
        GLACIER
    }

    public boolean hasAspect(String aspectName) {
        return aspects != null && aspects.contains(aspectName);
    }

    public void addAspect(String aspectName) {
        if (aspects == null) {
            aspects = new HashSet<>();
        }
        aspects.add(aspectName);
    }

    public void removeAspect(String aspectName) {
        if (aspects != null) {
            aspects.remove(aspectName);
        }
    }

    public boolean isLockExpired(LocalDateTime now) {
        return locked && lockExpiresAt != null && !lockExpiresAt.isAfter(now);
    }

    public boolean isEffectivelyLocked(LocalDateTime now) {
        return locked && !isLockExpired(now);
    }

    public void applyLock(String username, LocalDateTime now, LockLifetime lifetime, LocalDateTime expiresAt) {
        applyLock(username, now, lifetime, expiresAt, LockType.WRITE_LOCK, null, false);
    }

    public void applyLock(String username, LocalDateTime now, LockLifetime lifetime,
                          LocalDateTime expiresAt, LockType type, String additionalInfo, boolean deep) {
        this.locked = true;
        this.lockedBy = username;
        this.lockedDate = now;
        this.lockLifetime = lifetime;
        this.lockExpiresAt = expiresAt;
        this.lockType = type != null ? type : LockType.WRITE_LOCK;
        this.lockAdditionalInfo = additionalInfo;
        this.lockDeep = deep;
    }

    public void clearLock() {
        this.locked = false;
        this.lockedBy = null;
        this.lockedDate = null;
        this.lockLifetime = null;
        this.lockExpiresAt = null;
        this.lockType = null;
        this.lockAdditionalInfo = null;
        this.lockDeep = false;
    }

    public String describeActiveLock(LocalDateTime now) {
        if (!isEffectivelyLocked(now)) {
            return "Node is not locked";
        }
        StringBuilder message = new StringBuilder("locked");
        if (lockedBy != null && !lockedBy.isBlank()) {
            message.append(" by: ").append(lockedBy);
        }
        if (lockLifetime != null) {
            message.append(" [").append(lockLifetime);
            if (lockExpiresAt != null) {
                message.append(", expires: ").append(lockExpiresAt);
            }
            message.append("]");
        }
        if (lockType != null && lockType != LockType.WRITE_LOCK) {
            message.append(" (").append(lockType).append(")");
        }
        return message.toString();
    }

    /**
     * Check whether the given user is allowed to write to this node under the
     * current lock state. This respects lock type semantics:
     * <ul>
     *   <li>READ_ONLY_LOCK — no one can write (including owner)</li>
     *   <li>WRITE_LOCK — owner can write, others cannot</li>
     *   <li>NODE_LOCK — no one can update/delete, but adding children is allowed</li>
     * </ul>
     */
    public boolean isWriteAllowed(String username, LocalDateTime now) {
        if (!isEffectivelyLocked(now)) {
            return true;
        }
        if (lockType == LockType.READ_ONLY_LOCK) {
            return false;
        }
        if (lockType == LockType.NODE_LOCK) {
            return false;
        }
        // WRITE_LOCK or null — owner can write
        return lockedBy != null && lockedBy.equals(username);
    }
}
