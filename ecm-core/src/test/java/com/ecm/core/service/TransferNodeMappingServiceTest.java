package com.ecm.core.service;

import com.ecm.core.entity.TransferNodeMapping;
import com.ecm.core.repository.TransferNodeMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferNodeMappingServiceTest {

    @Mock
    private TransferNodeMappingRepository transferNodeMappingRepository;

    private TransferNodeMappingService service;
    private Map<String, TransferNodeMapping> storedMappings;

    @BeforeEach
    void setUp() {
        service = new TransferNodeMappingService(transferNodeMappingRepository);
        storedMappings = new LinkedHashMap<>();

        lenient().when(transferNodeMappingRepository.findByRootFolderIdAndSourceRepositoryIdAndSourceNodeId(any(), any(), any()))
            .thenAnswer(invocation -> Optional.ofNullable(storedMappings.get(key(
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2)
            ))));
        lenient().when(transferNodeMappingRepository.save(any(TransferNodeMapping.class))).thenAnswer(invocation -> {
            TransferNodeMapping mapping = invocation.getArgument(0);
            if (mapping.getId() == null) {
                mapping.setId(UUID.randomUUID());
            }
            storedMappings.put(key(mapping.getRootFolderId(), mapping.getSourceRepositoryId(), mapping.getSourceNodeId()), mapping);
            return mapping;
        });
    }

    @Test
    @DisplayName("upsertMapping creates a receiver-root scoped mapping")
    void upsertMappingCreatesReceiverRootScopedMapping() {
        UUID rootFolderId = UUID.randomUUID();
        UUID sourceNodeId = UUID.randomUUID();
        UUID localNodeId = UUID.randomUUID();
        LocalDateTime modifiedAt = LocalDateTime.now().minusMinutes(2);
        LocalDateTime syncedAt = LocalDateTime.now();

        TransferNodeMapping mapping = service.upsertMapping(
            rootFolderId,
            "remote-athena",
            sourceNodeId,
            localNodeId,
            modifiedAt,
            syncedAt
        );

        assertEquals(rootFolderId, mapping.getRootFolderId());
        assertEquals("remote-athena", mapping.getSourceRepositoryId());
        assertEquals(sourceNodeId, mapping.getSourceNodeId());
        assertEquals(localNodeId, mapping.getLocalNodeId());
        assertEquals(modifiedAt, mapping.getLastSourceModifiedAt());
        assertEquals(syncedAt, mapping.getLastSyncedAt());
    }

    @Test
    @DisplayName("refreshSyncTimestamps updates existing mapping in place")
    void refreshSyncTimestampsUpdatesExistingMapping() {
        UUID rootFolderId = UUID.randomUUID();
        UUID sourceNodeId = UUID.randomUUID();
        TransferNodeMapping existing = new TransferNodeMapping();
        existing.setId(UUID.randomUUID());
        existing.setRootFolderId(rootFolderId);
        existing.setSourceRepositoryId("remote-athena");
        existing.setSourceNodeId(sourceNodeId);
        existing.setLocalNodeId(UUID.randomUUID());
        storedMappings.put(key(rootFolderId, "remote-athena", sourceNodeId), existing);

        LocalDateTime modifiedAt = LocalDateTime.now().minusMinutes(1);
        LocalDateTime syncedAt = LocalDateTime.now();
        TransferNodeMapping updated = service.refreshSyncTimestamps(
            rootFolderId,
            "remote-athena",
            sourceNodeId,
            modifiedAt,
            syncedAt
        );

        assertSame(existing, updated);
        assertEquals(modifiedAt, updated.getLastSourceModifiedAt());
        assertEquals(syncedAt, updated.getLastSyncedAt());
        assertTrue(service.findMapping(rootFolderId, "remote-athena", sourceNodeId).isPresent());
    }

    private String key(UUID rootFolderId, String sourceRepositoryId, UUID sourceNodeId) {
        return rootFolderId + "|" + sourceRepositoryId + "|" + sourceNodeId;
    }
}
