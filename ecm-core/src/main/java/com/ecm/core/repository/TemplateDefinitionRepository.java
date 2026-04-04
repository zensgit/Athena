package com.ecm.core.repository;

import com.ecm.core.entity.TemplateDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemplateDefinitionRepository extends JpaRepository<TemplateDefinition, UUID> {

    List<TemplateDefinition> findAllByOrderByNameAsc();

    Optional<TemplateDefinition> findByTemplatePathAndActiveTrue(String templatePath);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByTemplatePath(String templatePath);
}
