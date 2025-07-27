package com.ecm.core.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.Type;

import java.util.HashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "roles", indexes = {
    @Index(name = "idx_role_name", columnList = "name", unique = true)
})
@EqualsAndHashCode(callSuper = true, exclude = {"users", "groups", "privileges"})
@ToString(callSuper = true, exclude = {"users", "groups", "privileges"})
public class Role extends BaseEntity {
    
    @Column(name = "name", nullable = false, unique = true)
    private String name;
    
    @Column(name = "display_name")
    private String displayName;
    
    @Column(name = "description")
    private String description;
    
    @ManyToMany(mappedBy = "roles", fetch = FetchType.LAZY)
    private Set<User> users = new HashSet<>();
    
    @ManyToMany(mappedBy = "roles", fetch = FetchType.LAZY)
    private Set<Group> groups = new HashSet<>();
    
    @ElementCollection
    @CollectionTable(name = "role_privileges", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "privilege")
    @Enumerated(EnumType.STRING)
    private Set<Privilege> privileges = new HashSet<>();
    
    @Type(JsonType.class)
    @Column(name = "permissions", columnDefinition = "jsonb")
    private Set<String> permissions = new HashSet<>();
    
    @Column(name = "is_system", nullable = false)
    private boolean system = false;
    
    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;
    
    public void addPrivilege(Privilege privilege) {
        privileges.add(privilege);
    }
    
    public void removePrivilege(Privilege privilege) {
        privileges.remove(privilege);
    }
    
    public void addPermission(String permission) {
        permissions.add(permission);
    }
    
    public void removePermission(String permission) {
        permissions.remove(permission);
    }
}

enum Privilege {
    ADMIN_USERS,
    ADMIN_GROUPS,
    ADMIN_ROLES,
    ADMIN_SYSTEM,
    CREATE_SITES,
    DELETE_SITES,
    MANAGE_WORKFLOWS,
    MANAGE_TEMPLATES,
    MANAGE_CATEGORIES,
    MANAGE_TAGS,
    BULK_IMPORT,
    BULK_EXPORT,
    VIEW_AUDIT,
    MANAGE_AUDIT,
    EXECUTE_SCRIPTS
}