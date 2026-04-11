package com.ecm.core.cmis;

import com.ecm.core.entity.AuditLog;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.AuditLogRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.service.SecurityService;
import com.ecm.core.service.TenantWorkspaceScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CmisChangeLogServiceTest {

    private static final List<String> CMIS_EVENT_TYPES = List.of(
        "NODE_CREATED", "NODE_UPDATED", "NODE_DELETED", "VERSION_CREATED"
    );

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private SecurityService securityService;

    @Mock
    private TenantWorkspaceScopeService tenantWorkspaceScopeService;

    private final Map<UUID, Node> nodesById = new HashMap<>();

    private CmisChangeLogService cmisChangeLogService;

    @BeforeEach
    void setUp() {
        cmisChangeLogService = new CmisChangeLogService(
            auditLogRepository,
            nodeRepository,
            securityService,
            tenantWorkspaceScopeService
        );
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(securityService.getUserAuthorities("alice")).thenReturn(Set.of("alice", "EVERYONE", "team-a"));
        lenient().when(nodeRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<UUID> requestedIds = invocation.getArgument(0);
            java.util.List<Node> resolved = new java.util.ArrayList<>();
            for (UUID id : requestedIds) {
                Node node = nodesById.get(id);
                if (node != null) {
                    resolved.add(node);
                }
            }
            return resolved;
        });
    }

    @Test
    @DisplayName("No token returns first visible page ordered by eventTime/id with cursor token")
    void noTokenReturnsFirstVisiblePage() {
        LocalDateTime t1 = LocalDateTime.of(2026, 4, 11, 10, 0, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 4, 11, 11, 0, 0);
        AuditLog created = buildAuditLog(
            UUID.fromString("00000000-0000-0000-0000-000000000101"),
            "NODE_CREATED",
            t1,
            "alice",
            null
        );
        AuditLog updated = buildAuditLog(
            UUID.fromString("00000000-0000-0000-0000-000000000202"),
            "NODE_UPDATED",
            t2,
            "bob",
            null
        );
        Document createdNode = registerNode(created.getNodeId(), "/Sites/a.doc", false);
        Document updatedNode = registerNode(updated.getNodeId(), "/Sites/b.doc", false);

        when(auditLogRepository.findByEventTypeInOrderByEventTimeAscIdAsc(eq(CMIS_EVENT_TYPES), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(created, updated), PageRequest.of(0, 50), 2));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        when(securityService.hasPermission(createdNode, PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(updatedNode, PermissionType.READ)).thenReturn(true);

        CmisModels.ContentChangesResponse response = cmisChangeLogService.getContentChanges(null, 50);

        assertEquals(2, response.changes().size());
        assertEquals("created", response.changes().get(0).changeType());
        assertEquals("updated", response.changes().get(1).changeType());
        assertEquals("2026-04-11T11:00:00|00000000-0000-0000-0000-000000000202", response.latestChangeLogToken());
        assertFalse(response.hasMoreItems());
        verify(nodeRepository).findAllById(any());
    }

    @Test
    @DisplayName("Cursor token does not skip same-timestamp events")
    void cursorTokenDoesNotSkipSameTimestampEvents() {
        LocalDateTime sameTime = LocalDateTime.of(2026, 4, 11, 12, 0, 0);
        AuditLog first = buildAuditLog(
            UUID.fromString("00000000-0000-0000-0000-000000000101"),
            "NODE_CREATED",
            sameTime,
            "alice",
            null
        );
        AuditLog second = buildAuditLog(
            UUID.fromString("00000000-0000-0000-0000-000000000202"),
            "NODE_UPDATED",
            sameTime,
            "bob",
            null
        );
        AuditLog third = buildAuditLog(
            UUID.fromString("00000000-0000-0000-0000-000000000303"),
            "NODE_DELETED",
            sameTime,
            "carol",
            "{\"path\":\"/Sites/three.doc\",\"readableAuthorities\":[\"team-a\"]}"
        );
        Document firstNode = registerNode(first.getNodeId(), "/Sites/one.doc", false);
        Document secondNode = registerNode(second.getNodeId(), "/Sites/two.doc", false);

        when(auditLogRepository.findByEventTypeInOrderByEventTimeAscIdAsc(eq(CMIS_EVENT_TYPES), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 2), 3));
        when(auditLogRepository.findByEventTypeInAfterCursorOrderByEventTimeAscIdAsc(
            eq(CMIS_EVENT_TYPES), eq(sameTime), eq(second.getId()), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(third), PageRequest.of(0, 2), 1));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        when(securityService.hasPermission(firstNode, PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(secondNode, PermissionType.READ)).thenReturn(true);

        CmisModels.ContentChangesResponse firstPage = cmisChangeLogService.getContentChanges(null, 2);
        CmisModels.ContentChangesResponse secondPage =
            cmisChangeLogService.getContentChanges(firstPage.latestChangeLogToken(), 2);

        assertEquals(2, firstPage.changes().size());
        assertTrue(firstPage.hasMoreItems());
        assertEquals(1, secondPage.changes().size());
        assertEquals(third.getNodeId().toString(), secondPage.changes().get(0).objectId());
        assertEquals("deleted", secondPage.changes().get(0).changeType());
    }

    @Test
    @DisplayName("Invisible nodes are filtered out from CMIS change log")
    void invisibleNodesAreFilteredOut() {
        LocalDateTime t1 = LocalDateTime.of(2026, 4, 11, 13, 0, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 4, 11, 14, 0, 0);
        AuditLog hidden = buildAuditLog(
            UUID.fromString("00000000-0000-0000-0000-000000000111"),
            "NODE_CREATED",
            t1,
            "alice",
            null
        );
        AuditLog visible = buildAuditLog(
            UUID.fromString("00000000-0000-0000-0000-000000000222"),
            "NODE_UPDATED",
            t2,
            "bob",
            null
        );
        registerNode(hidden.getNodeId(), "/Hidden/node.doc", false);
        Document visibleNode = registerNode(visible.getNodeId(), "/Sites/visible.doc", false);

        when(auditLogRepository.findByEventTypeInOrderByEventTimeAscIdAsc(eq(CMIS_EVENT_TYPES), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(hidden, visible), PageRequest.of(0, 50), 2));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
        when(tenantWorkspaceScopeService.isPathVisible("/Hidden/node.doc")).thenReturn(false);
        when(tenantWorkspaceScopeService.isPathVisible("/Sites/visible.doc")).thenReturn(true);
        when(securityService.hasPermission(visibleNode, PermissionType.READ)).thenReturn(true);

        CmisModels.ContentChangesResponse response = cmisChangeLogService.getContentChanges(null, 50);

        assertEquals(1, response.changes().size());
        assertEquals(visible.getNodeId().toString(), response.changes().get(0).objectId());
        assertEquals("2026-04-11T14:00:00|00000000-0000-0000-0000-000000000222", response.latestChangeLogToken());
    }

    @Test
    @DisplayName("Hard-deleted node uses audit metadata for visibility when repository row is gone")
    void hardDeletedNodeUsesAuditMetadataForVisibility() {
        LocalDateTime t1 = LocalDateTime.of(2026, 4, 11, 15, 0, 0);
        AuditLog deleted = buildAuditLog(
            UUID.fromString("00000000-0000-0000-0000-000000000333"),
            "NODE_DELETED",
            t1,
            "admin",
            "{\"path\":\"/Sites/deleted.doc\",\"readableAuthorities\":[\"team-a\"]}"
        );

        when(auditLogRepository.findByEventTypeInOrderByEventTimeAscIdAsc(eq(CMIS_EVENT_TYPES), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(deleted), PageRequest.of(0, 50), 1));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

        CmisModels.ContentChangesResponse response = cmisChangeLogService.getContentChanges(null, 50);

        assertEquals(1, response.changes().size());
        assertEquals("deleted", response.changes().get(0).changeType());
        assertEquals(deleted.getNodeId().toString(), response.changes().get(0).objectId());
    }

    @Test
    @DisplayName("Empty result keeps caller token stable")
    void emptyResultKeepsCallerTokenStable() {
        String token = "2026-04-11T15:00:00|00000000-0000-0000-0000-000000000404";
        when(auditLogRepository.findByEventTypeInAfterCursorOrderByEventTimeAscIdAsc(
            eq(CMIS_EVENT_TYPES),
            eq(LocalDateTime.of(2026, 4, 11, 15, 0, 0)),
            eq(UUID.fromString("00000000-0000-0000-0000-000000000404")),
            any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        CmisModels.ContentChangesResponse response = cmisChangeLogService.getContentChanges(token, 50);

        assertTrue(response.changes().isEmpty());
        assertEquals(token, response.latestChangeLogToken());
        assertFalse(response.hasMoreItems());
    }

    @Test
    @DisplayName("Null token and empty result return null token")
    void nullTokenAndEmptyResultReturnNullToken() {
        when(auditLogRepository.findByEventTypeInOrderByEventTimeAscIdAsc(eq(CMIS_EVENT_TYPES), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        CmisModels.ContentChangesResponse response = cmisChangeLogService.getContentChanges(null, 50);

        assertTrue(response.changes().isEmpty());
        assertNull(response.latestChangeLogToken());
        assertFalse(response.hasMoreItems());
    }

    @Test
    @DisplayName("Legacy timestamp token still uses strict after query")
    void legacyTimestampTokenStillUsesStrictAfterQuery() {
        LocalDateTime tokenTime = LocalDateTime.of(2026, 4, 11, 16, 0, 0);
        when(auditLogRepository.findByEventTypeInAndEventTimeAfterOrderByEventTimeAsc(
            eq(CMIS_EVENT_TYPES), eq(tokenTime), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        CmisModels.ContentChangesResponse response = cmisChangeLogService.getContentChanges("2026-04-11T16:00:00", 50);

        assertTrue(response.changes().isEmpty());
        verify(auditLogRepository).findByEventTypeInAndEventTimeAfterOrderByEventTimeAsc(
            eq(CMIS_EVENT_TYPES), eq(tokenTime), any(Pageable.class)
        );
    }

    private AuditLog buildAuditLog(UUID id, String eventType, LocalDateTime eventTime, String username, String metadata) {
        return AuditLog.builder()
            .id(id)
            .eventType(eventType)
            .nodeId(UUID.randomUUID())
            .nodeName("test-node")
            .username(username)
            .eventTime(eventTime)
            .details("test details")
            .metadata(metadata)
            .build();
    }

    private Document registerNode(UUID nodeId, String path, boolean deleted) {
        Document node = new Document();
        node.setId(nodeId);
        node.setPath(path);
        node.setName("node-" + nodeId);
        node.setDeleted(deleted);
        node.setArchiveStatus(Node.ArchiveStatus.LIVE);
        nodesById.put(nodeId, node);
        return node;
    }
}
