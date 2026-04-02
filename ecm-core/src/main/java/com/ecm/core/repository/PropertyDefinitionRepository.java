package com.ecm.core.repository;

import com.ecm.core.entity.PropertyDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PropertyDefinitionRepository extends JpaRepository<PropertyDefinition, UUID> {
    List<PropertyDefinition> findByTypeDefinitionId(UUID typeDefinitionId);
    List<PropertyDefinition> findByAspectDefinitionId(UUID aspectDefinitionId);

    @Query("SELECT p FROM PropertyDefinition p WHERE p.typeDefinition.model.prefix = :prefix AND p.typeDefinition.name = :typeName")
    List<PropertyDefinition> findByTypeName(@Param("prefix") String prefix, @Param("typeName") String typeName);

    @Query("SELECT p FROM PropertyDefinition p WHERE p.aspectDefinition.model.prefix = :prefix AND p.aspectDefinition.name = :aspectName")
    List<PropertyDefinition> findByAspectName(@Param("prefix") String prefix, @Param("aspectName") String aspectName);
}
