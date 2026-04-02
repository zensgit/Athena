package com.ecm.core.repository;

import com.ecm.core.entity.RenditionResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RenditionResourceRepository extends JpaRepository<RenditionResource, UUID> {

    List<RenditionResource> findByDocumentIdOrderBySortOrderAsc(UUID documentId);

    Optional<RenditionResource> findByDocumentIdAndRenditionKey(UUID documentId, String renditionKey);
}
