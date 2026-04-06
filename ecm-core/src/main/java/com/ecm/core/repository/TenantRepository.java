package com.ecm.core.repository;

import com.ecm.core.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByTenantDomainIgnoreCaseAndDeletedFalse(String tenantDomain);

    boolean existsByTenantDomainIgnoreCaseAndDeletedFalse(String tenantDomain);

    List<Tenant> findByDeletedFalseOrderByTenantDomainAsc();
}
