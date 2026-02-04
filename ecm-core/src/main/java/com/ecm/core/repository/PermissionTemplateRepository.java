package com.ecm.core.repository;

import com.ecm.core.entity.PermissionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PermissionTemplateRepository extends JpaRepository<PermissionTemplate, UUID> {
    boolean existsByNameIgnoreCase(String name);
    Optional<PermissionTemplate> findByNameIgnoreCase(String name);
}
