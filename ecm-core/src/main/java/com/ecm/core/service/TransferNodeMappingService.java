package com.ecm.core.service;

import com.ecm.core.entity.TransferNodeMapping;
import com.ecm.core.repository.TransferNodeMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferNodeMappingService {

    private final TransferNodeMappingRepository transferNodeMappingRepository;

    public Optional<TransferNodeMapping> findMapping(UUID rootFolderId, String sourceRepositoryId, UUID sourceNodeId) {
        return transferNodeMappingRepository.findByRootFolderIdAndSourceRepositoryIdAndSourceNodeId(
            requiredId(rootFolderId, "rootFolderId"),
            normalizeRequired(sourceRepositoryId, "sourceRepositoryId"),
            requiredId(sourceNodeId, "sourceNodeId")
        );
    }

    public TransferNodeMapping upsertMapping(
        UUID rootFolderId,
        String sourceRepositoryId,
        UUID sourceNodeId,
        UUID localNodeId,
        LocalDateTime lastSourceModifiedAt,
        LocalDateTime lastSyncedAt
    ) {
        UUID requiredRootFolderId = requiredId(rootFolderId, "rootFolderId");
        String normalizedSourceRepositoryId = normalizeRequired(sourceRepositoryId, "sourceRepositoryId");
        UUID requiredSourceNodeId = requiredId(sourceNodeId, "sourceNodeId");
        UUID requiredLocalNodeId = requiredId(localNodeId, "localNodeId");

        TransferNodeMapping mapping = findMapping(requiredRootFolderId, normalizedSourceRepositoryId, requiredSourceNodeId)
            .orElseGet(TransferNodeMapping::new);
        mapping.setRootFolderId(requiredRootFolderId);
        mapping.setSourceRepositoryId(normalizedSourceRepositoryId);
        mapping.setSourceNodeId(requiredSourceNodeId);
        mapping.setLocalNodeId(requiredLocalNodeId);
        mapping.setLastSourceModifiedAt(lastSourceModifiedAt);
        mapping.setLastSyncedAt(lastSyncedAt);
        return transferNodeMappingRepository.save(mapping);
    }

    public TransferNodeMapping refreshSyncTimestamps(
        UUID rootFolderId,
        String sourceRepositoryId,
        UUID sourceNodeId,
        LocalDateTime lastSourceModifiedAt,
        LocalDateTime lastSyncedAt
    ) {
        TransferNodeMapping mapping = findMapping(rootFolderId, sourceRepositoryId, sourceNodeId)
            .orElseThrow(() -> new IllegalArgumentException("Transfer node mapping not found for receiver root scoped source"));
        mapping.setLastSourceModifiedAt(lastSourceModifiedAt);
        mapping.setLastSyncedAt(lastSyncedAt);
        return transferNodeMappingRepository.save(mapping);
    }

    private UUID requiredId(UUID value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
