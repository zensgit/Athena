package com.ecm.core.repository;

import com.ecm.core.entity.ArchivePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArchivePolicyRepository extends JpaRepository<ArchivePolicy, UUID> {

    Optional<ArchivePolicy> findByFolderId(UUID folderId);

    List<ArchivePolicy> findByEnabledTrueAndDeletedFalseOrderByCreatedDateAsc();

    List<ArchivePolicy> findByDeletedFalseOrderByCreatedDateDesc();
}
