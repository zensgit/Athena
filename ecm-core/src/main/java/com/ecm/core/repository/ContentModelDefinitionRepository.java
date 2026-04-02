package com.ecm.core.repository;

import com.ecm.core.entity.ContentModelDefinition;
import com.ecm.core.entity.ModelStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContentModelDefinitionRepository extends JpaRepository<ContentModelDefinition, UUID> {
    Optional<ContentModelDefinition> findByPrefix(String prefix);
    Optional<ContentModelDefinition> findByNamespaceUri(String namespaceUri);
    List<ContentModelDefinition> findByStatus(ModelStatus status);
    List<ContentModelDefinition> findByDeletedFalse();
    boolean existsByPrefix(String prefix);
    boolean existsByNamespaceUri(String namespaceUri);
}
