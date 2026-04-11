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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private CmisChangeLogService cmisChangeLogService;

    @BeforeEach
    void setUp() {
        cmisChangeLogService = new CmisChangeLogService(
            auditLogRepository,
            nodeRepository,
            securityService,
            tenantWorkspaceScopeService
        );
    }

    @Test
    @DisplayName("No token returns first visible page ordered by eventTime/id with cursor token")
    void noTokenReturnsFirstVisiblePage() {
        LocalDateTime t1 = LocalDateTime.of(2026, 4, 11, 10, 0, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 4, 11, 11, 0, 0);
        AuditLog created = buildAuditLog(UUID.fromString("00000000-0000-0000-0000-000000000101"), "NODE_CREATED", t1, "alice");
        AuditLog updated = buildAuditLog(UUID.fromString("00000000-0000-0000-0000-000000000202"), "NODE_UPDATED", t2, "bob");

        when(auditLogRepository.findByEventTypeInOrderByEventTimeAscIdAsc(eq(CMIS_EVENT_TYPES), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(created, updated), PageRequest.of(0, 50), 2));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        mockReadable(created.getNodeId(), "/Sites/a.doc", true);
        mockReadable(updated.getNodeId(), "/Sites/b.doc", true);

        CmisModels.ContentChangesResponse response = cmisChangeLogService.getContentChanges(null, 50);

        assertEquals(2, response.changes().size());
        assertEquals("created", response.changes().get(0).changeType());
        assertEquals("updated", response.changes().get(1).changeType());
        assertEquals("2026-04-11T11:00:00|00000000-0000-0000-0000-000000000202", response.latestChangeLogToken());
        assertFalse(response.hasMoreItems());
    }

    @Test
    @DisplayName("Cursor token does not skip same-timestamp events")
    void cursorTokenDoesNotSkipSameTimestampEvents() {
        LocalDateTime sameTime = LocalDateTime.of(2026, 4, 11, 12, 0, 0);
        AuditLog first = buildAuditLog(UUID.fromString("00000000-0000-0000-0000-000000000101"), "NODE_CREATED", sameTime, "alice");
        AuditLog second = buildAuditLog(UUID.fromString("00000000-0000-0000-0000-000000000202"), "NODE_UPDATED", sameTime, "bob");
        AuditLog third = buildAuditLog(UUID.fromString("00000000-0000-0000-0000-000000000303"), "NODE_DELETED", sameTime, "carol");

        when(auditLogRepository.findByEventTypeInOrderByEventTimeAscIdAsc(eq(CMIS_EVENT_TYPES), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 2), 3));
        when(auditLogRepository.findByEventTypeInAfterCursorOrderByEventTimeAscIdAsc(
            eq(CMIS_EVENT_TYPES), eq(sameTime), eq(second.getId()), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(third), PageRequest.of(0, 2), 1));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        mockReadable(first.getNodeId(), "/Sites/one.doc", true);
        mockReadable(second.getNodeId(), "/Sites/two.doc", true);
        mockReadable(third.getNodeId(), "/Sites/three.doc", true);

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
        AuditLog hidden = buildAuditLog(UUID.fromString("00000000-0000-0000-0000-000000000111"), "NODE_CREATED", t1, "alice");
        AuditLog visible = buildAuditLog(UUID.fromString("00000000-0000-0000-0000-000000000222"), "NODE_UPDATED", t2, "bob");

        when(auditLogRepository.findByEventTypeInOrderByEventTimeAscIdAsc(eq(CMIS_EVENT_TYPES), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(hidden, visible), PageRequest.of(0, 50), 2));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
        mockNode(hidden.getNodeId(), "/Hidden/node.doc");
        mockReadable(visible.getNodeId(), "/Sites/visible.doc", true);
        when(tenantWorkspaceScopeService.isPathVisible("/Hidden/node.doc")).thenReturn(false);
        when(tenantWorkspaceScopeService.isPathVisible("/Sites/visible.doc")).thenReturn(true);

        CmisModels.ContentChangesResponse response = cmisChangeLogService.getContentChanges(null, 50);

        assertEquals(1, response.changes().size());
        assertEquals(visible.getNodeId().toString(), response.changes().get(0).objectId());
        assertEquals("2026-04-11T14:00:00|00000000-0000-0000-0000-000000000222", response.latestChangeLogToken());
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

        cmisChangeLogService.getContentChanges("2026-04-11T16:00:00", 50);

        verify(auditLogRepository).findByEventTypeInAndEventTimeAfterOrderByEventTimeAsc(
            eq(CMIS_EVENT_TYPES), eq(tokenTime), any(Pageable.class)
        );
    }

    private AuditLog buildAuditLog(UUID id, String eventType, LocalDateTime eventTime, String username) {
        return AuditLog.builder()
            .id(id)
            .eventType(eventType)
            .nodeId(UUID.randomUUID())
            .nodeName("test-node")
            .username(username)
            .eventTime(eventTime)
            .details("test details")
            .build();
    }

    private void mockReadable(UUID nodeId, String path, boolean readable) {
        Document node = mockNode(nodeId, path);
        when(securityService.hasPermission(node, PermissionType.READ)).thenReturn(readable);
    }

    private Document mockNode(UUID nodeId, String path) {
        Document node = new Document();
        node.setId(nodeId);
        node.setPath(path);
        node.setName("node-" + nodeId);
        node.setArchiveStatus(Node.ArchiveStatus.LIVE);
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nodeId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(node));
        return node;
    }
}
