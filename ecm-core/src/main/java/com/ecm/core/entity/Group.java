package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "groups", indexes = {
    @Index(name = "idx_group_name", columnList = "name", unique = true)
})
@EqualsAndHashCode(callSuper = true, exclude = {"users", "roles", "parentGroup", "subGroups"})
@ToString(callSuper = true, exclude = {"users", "roles", "parentGroup", "subGroups"})
public class Group extends BaseEntity {
    
    @Column(name = "name", nullable = false, unique = true)
    private String name;
    
    @Column(name = "display_name")
    private String displayName;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "email")
    private String email;
    
    @ManyToMany(mappedBy = "groups", fetch = FetchType.LAZY)
    private Set<User> users = new HashSet<>();
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "group_roles",
        joinColumns = @JoinColumn(name = "group_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_group_id")
    private Group parentGroup;
    
    @OneToMany(mappedBy = "parentGroup", cascade = CascadeType.ALL)
    private Set<Group> subGroups = new HashSet<>();
    
    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "group_type")
    private GroupType groupType = GroupType.CUSTOM;
    
    public void addUser(User user) {
        users.add(user);
        user.getGroups().add(this);
    }
    
    public void removeUser(User user) {
        users.remove(user);
        user.getGroups().remove(this);
    }
    
    public void addRole(Role role) {
        roles.add(role);
    }
    
    public void removeRole(Role role) {
        roles.remove(role);
    }
    
    public void addSubGroup(Group subGroup) {
        subGroups.add(subGroup);
        subGroup.setParentGroup(this);
    }
    
    public void removeSubGroup(Group subGroup) {
        subGroups.remove(subGroup);
        subGroup.setParentGroup(null);
    }
}

enum GroupType {
    SYSTEM,
    DEPARTMENT,
    PROJECT,
    CUSTOM
}