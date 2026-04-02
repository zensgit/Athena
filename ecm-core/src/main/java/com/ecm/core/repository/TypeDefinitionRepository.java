package com.ecm.core.repository;

import com.ecm.core.entity.TypeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TypeDefinitionRepository extends JpaRepository<TypeDefinition, UUID> {
    List<TypeDefinition> findByModelId(UUID modelId);

    @Query("SELECT t FROM TypeDefinition t WHERE t.model.prefix = :prefix AND t.name = :name")
    Optional<TypeDefinition> findByQualifiedName(@Param("prefix") String prefix, @Param("name") String name);

    @Query("SELECT t FROM TypeDefinition t WHERE t.model.status = 'ACTIVE'")
    List<TypeDefinition> findAllActive();
}
