package com.ecm.core.repository;

import com.ecm.core.entity.ScriptDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScriptDefinitionRepository extends JpaRepository<ScriptDefinition, UUID> {

    List<ScriptDefinition> findAllByOrderByNameAsc();

    Optional<ScriptDefinition> findByScriptPathAndActiveTrue(String scriptPath);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByScriptPath(String scriptPath);
}
