package com.ecm.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.UUID;

@Data
@Entity
@Table(name = "tenants", indexes = {
    @Index(name = "idx_tenant_domain", columnList = "tenant_domain", unique = true),
    @Index(name = "idx_tenant_enabled", columnList = "is_enabled")
})
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Tenant extends BaseEntity {

    @Column(name = "tenant_domain", nullable = false, unique = true, length = 120)
    private String tenantDomain;

    @Column(name = "tenant_name", nullable = false, length = 200)
    private String tenantName;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "root_node_id")
    private UUID rootNodeId;

    @Column(name = "quota_bytes")
    private Long quotaBytes;

    @Column(name = "is_system_default", nullable = false)
    private boolean systemDefault = false;
}
