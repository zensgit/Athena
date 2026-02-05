package com.ecm.core.repository;

import com.ecm.core.entity.PermissionTemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionTemplateVersionRepository extends JpaRepository<PermissionTemplateVersion, UUID> {

    List<PermissionTemplateVersion> findByTemplateIdOrderByVersionNumberDesc(UUID templateId);

    Optional<PermissionTemplateVersion> findTopByTemplateIdOrderByVersionNumberDesc(UUID templateId);
}
