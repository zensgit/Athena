package com.ecm.core.repository;

import com.ecm.core.entity.ConstraintDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConstraintDefinitionRepository extends JpaRepository<ConstraintDefinition, UUID> {
    List<ConstraintDefinition> findByPropertyDefinitionId(UUID propertyDefinitionId);
}
