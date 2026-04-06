package com.ecm.core.config;

public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCurrentTenantDomain(String tenantDomain) {
        CURRENT_TENANT.set(tenantDomain);
    }

    public static String getCurrentTenantDomain() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
