package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "permissions", indexes = {
    @Index(name = "idx_permission_node", columnList = "node_id"),
    @Index(name = "idx_permission_authority", columnList = "authority"),
    @Index(name = "idx_permission_type", columnList = "authority_type")
})
@EqualsAndHashCode(callSuper = true, exclude = {"node"})
@ToString(callSuper = true, exclude = {"node"})
public class Permission extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_id", nullable = false)
    private Node node;
    
    @Column(name = "authority", nullable = false)
    private String authority;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "authority_type", nullable = false)
    private AuthorityType authorityType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false)
    private PermissionType permission;
    
    @Column(name = "is_allowed", nullable = false)
    private boolean allowed = true;
    
    @Column(name = "is_inherited", nullable = false)
    private boolean inherited = false;
    
    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;
    
    @Column(name = "notes")
    private String notes;
    
    public boolean isExpired() {
        return expiryDate != null && LocalDateTime.now().isAfter(expiryDate);
    }
}

enum AuthorityType {
    USER,
    GROUP,
    ROLE,
    EVERYONE
}

enum PermissionType {
    READ,
    WRITE,
    DELETE,
    CREATE_CHILDREN,
    DELETE_CHILDREN,
    EXECUTE,
    CHANGE_PERMISSIONS,
    TAKE_OWNERSHIP,
    CHECKOUT,
    CHECKIN,
    CANCEL_CHECKOUT,
    APPROVE,
    REJECT
}