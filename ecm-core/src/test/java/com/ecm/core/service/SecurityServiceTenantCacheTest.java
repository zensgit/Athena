package com.ecm.core.service;

import com.ecm.core.config.TenantContext;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SecurityServiceTenantCacheTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Permission cache key includes tenant domain and falls back to default")
    void permissionCacheKeyIncludesTenantDomain() {
        Node node = node();

        TenantContext.clear();
        String defaultKey = SecurityService.permissionCacheKey(node, PermissionType.READ, "alice");

        TenantContext.setCurrentTenantDomain("tenant-a");
        String tenantAKey = SecurityService.permissionCacheKey(node, PermissionType.READ, "alice");

        TenantContext.setCurrentTenantDomain("tenant-b");
        String tenantBKey = SecurityService.permissionCacheKey(node, PermissionType.READ, "alice");

        assertEquals("default_" + node.getId() + "_READ_alice", defaultKey);
        assertNotEquals(defaultKey, tenantAKey);
        assertNotEquals(tenantAKey, tenantBKey);
        assertEquals("tenant-a_" + node.getId() + "_READ_alice", tenantAKey);
        assertEquals("tenant-b_" + node.getId() + "_READ_alice", tenantBKey);
    }

    @Test
    @DisplayName("Tenant cache helper normalizes blank and mixed-case domains")
    void currentTenantDomainForCacheNormalizesAndFallsBack() {
        TenantContext.setCurrentTenantDomain("  TENANT-A  ");
        assertEquals("tenant-a", SecurityService.currentTenantDomainForCache());

        TenantContext.setCurrentTenantDomain("   ");
        assertEquals(TenantService.DEFAULT_TENANT_DOMAIN, SecurityService.currentTenantDomainForCache());
    }

    private static Node node() {
        Document node = new Document();
        node.setId(UUID.randomUUID());
        node.setName("doc");
        node.setCreatedBy("owner");
        node.setInheritPermissions(false);
        return node;
    }
}
