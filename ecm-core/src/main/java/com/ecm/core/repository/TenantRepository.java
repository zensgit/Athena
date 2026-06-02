package com.ecm.core.repository;

import com.ecm.core.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByTenantDomainIgnoreCaseAndDeletedFalse(String tenantDomain);

    // Reverse-lookup the tenant that owns a node as its workspace root (Q2b: resolve tenant from a
    // target folder by walking the parent chain and checking each ancestor id against tenant roots).
    Optional<Tenant> findByRootNodeIdAndDeletedFalse(UUID rootNodeId);

    boolean existsByTenantDomainIgnoreCaseAndDeletedFalse(String tenantDomain);

    // Q2b: lets the resolver distinguish "no tenant the write could be scoped to" (legacy single-tenant
    // deployment — the migration-seeded systemDefault "default" tenant is enabled but has a null
    // rootNodeId, so the parent-chain walk can never match it — write untenanted) from "a real tenant
    // exists but this folder is under none" (config error, reject). Keyed on rootNodeId-not-null
    // because that is exactly what resolveTenantForTargetFolder matches on: a tenant with no root can
    // never be a resolution target, so it must not force every root-workspace write to reject.
    boolean existsByDeletedFalseAndEnabledTrueAndRootNodeIdNotNull();

    List<Tenant> findByDeletedFalseOrderByTenantDomainAsc();
}
