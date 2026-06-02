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

    List<Tenant> findByDeletedFalseOrderByTenantDomainAsc();
}
