package com.ecm.core.repository;

import com.ecm.core.entity.AspectDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AspectDefinitionRepository extends JpaRepository<AspectDefinition, UUID> {
    List<AspectDefinition> findByModelId(UUID modelId);

    @Query("SELECT a FROM AspectDefinition a WHERE a.model.prefix = :prefix AND a.name = :name")
    Optional<AspectDefinition> findByQualifiedName(@Param("prefix") String prefix, @Param("name") String name);

    @Query("SELECT a FROM AspectDefinition a WHERE a.model.status = 'ACTIVE'")
    List<AspectDefinition> findAllActive();
}
