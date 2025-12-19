package com.ecm.core.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_username", columnList = "username", unique = true),
    @Index(name = "idx_user_email", columnList = "email", unique = true)
})
@EqualsAndHashCode(callSuper = true, exclude = {"groups", "roles"})
@ToString(callSuper = true, exclude = {"groups", "roles", "password"})
public class User extends BaseEntity {
    
    @Column(name = "username", nullable = false, unique = true)
    private String username;
    
    @Column(name = "email", nullable = false, unique = true)
    private String email;
    
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "password", nullable = false)
    private String password;
    
    @Column(name = "first_name")
    private String firstName;
    
    @Column(name = "last_name")
    private String lastName;
    
    @Column(name = "display_name")
    private String displayName;
    
    @Column(name = "avatar_url")
    private String avatarUrl;
    
    @Column(name = "phone")
    private String phone;
    
    @Column(name = "department")
    private String department;
    
    @Column(name = "job_title")
    private String jobTitle;
    
    @Column(name = "locale")
    private String locale = "en_US";
    
    @Column(name = "timezone")
    private String timezone = "UTC";
    
    @Type(JsonType.class)
    @Column(name = "preferences", columnDefinition = "jsonb")
    private Map<String, Object> preferences = new HashMap<>();
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_groups",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "group_id")
    )
    private Set<Group> groups = new HashSet<>();
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();
    
    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;
    
    @Column(name = "is_locked", nullable = false)
    private boolean locked = false;
    
    @Column(name = "is_expired", nullable = false)
    private boolean expired = false;
    
    @Column(name = "password_expired", nullable = false)
    private boolean passwordExpired = false;
    
    @Column(name = "last_login_date")
    private LocalDateTime lastLoginDate;
    
    @Column(name = "last_password_change_date")
    private LocalDateTime lastPasswordChangeDate;
    
    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts = 0;
    
    @Column(name = "locked_date")
    private LocalDateTime lockedDate;
    
    @Column(name = "unlock_date")
    private LocalDateTime unlockDate;
    
    @Column(name = "quota_size_mb")
    private Long quotaSizeMb;
    
    @Column(name = "used_size_mb")
    private Long usedSizeMb = 0L;
    
    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        return displayName != null ? displayName : username;
    }
    
    public void addGroup(Group group) {
        groups.add(group);
    }
    
    public void removeGroup(Group group) {
        groups.remove(group);
    }
    
    public void addRole(Role role) {
        roles.add(role);
    }
    
    public void removeRole(Role role) {
        roles.remove(role);
    }
    
    public boolean hasQuotaExceeded() {
        return quotaSizeMb != null && usedSizeMb >= quotaSizeMb;
    }
}
