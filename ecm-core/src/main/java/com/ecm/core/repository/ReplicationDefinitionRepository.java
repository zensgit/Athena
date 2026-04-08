package com.ecm.core.repository;

import com.ecm.core.entity.ReplicationDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReplicationDefinitionRepository extends JpaRepository<ReplicationDefinition, UUID> {

    boolean existsByNameIgnoreCase(String name);
}
