package com.ecm.core.config;

import java.util.UUID;

public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CURRENT_TENANT_ROOT_NODE_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCurrentTenantDomain(String tenantDomain) {
        CURRENT_TENANT.set(tenantDomain);
    }

    public static String getCurrentTenantDomain() {
        return CURRENT_TENANT.get();
    }

    public static void setCurrentTenantRootNodeId(UUID rootNodeId) {
        CURRENT_TENANT_ROOT_NODE_ID.set(rootNodeId);
    }

    public static UUID getCurrentTenantRootNodeId() {
        return CURRENT_TENANT_ROOT_NODE_ID.get();
    }

    public static Snapshot capture() {
        return new Snapshot(getCurrentTenantDomain(), getCurrentTenantRootNodeId());
    }

    public static void restore(Snapshot snapshot) {
        clear();
        if (snapshot == null) {
            return;
        }
        if (snapshot.tenantDomain() != null) {
            setCurrentTenantDomain(snapshot.tenantDomain());
        }
        if (snapshot.rootNodeId() != null) {
            setCurrentTenantRootNodeId(snapshot.rootNodeId());
        }
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_TENANT_ROOT_NODE_ID.remove();
    }

    public record Snapshot(String tenantDomain, UUID rootNodeId) {
    }
}
