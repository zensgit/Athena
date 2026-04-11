package com.ecm.core.repository;

import com.ecm.core.entity.TransferNodeMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferNodeMappingRepository extends JpaRepository<TransferNodeMapping, UUID> {

    Optional<TransferNodeMapping> findByRootFolderIdAndSourceRepositoryIdAndSourceNodeId(
        UUID rootFolderId,
        String sourceRepositoryId,
        UUID sourceNodeId
    );
}
