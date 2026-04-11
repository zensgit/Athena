package com.ecm.core.cmis;

import com.ecm.core.entity.AuditLog;
import com.ecm.core.repository.AuditLogRepository;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
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

    private CmisChangeLogService cmisChangeLogService;

    @BeforeEach
    void setUp() {
        cmisChangeLogService = new CmisChangeLogService(auditLogRepository);
    }

    @Test
    @DisplayName("No token returns first page of changes ordered by eventTime ASC")
    void noTokenReturnsFirstPage() {
        LocalDateTime t1 = LocalDateTime.of(2026, 4, 11, 10, 0, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 4, 11, 11, 0, 0);
        AuditLog log1 = buildAuditLog("NODE_CREATED", t1, "alice");
        AuditLog log2 = buildAuditLog("NODE_UPDATED", t2, "bob");

        when(auditLogRepository.findByEventTypeInOrderByEventTimeAsc(eq(CMIS_EVENT_TYPES), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(log1, log2), PageRequest.of(0, 50), 2));

        CmisModels.ContentChangesResponse response = cmisChangeLogService.getContentChanges(null, 50);

        assertEquals(2, response.changes().size());
        assertEquals("created", response.changes().get(0).changeType());
        assertEquals("updated", response.changes().get(1).changeType());
        assertEquals("alice", response.changes().get(0).user());
        assertEquals("bob", response.changes().get(1).user());
        assertFalse(response.hasMoreItems());
    }

    @Test
    @DisplayName("With token returns changes after that time")
    void withTokenReturnsChangesAfterTime() {
        LocalDateTime token = LocalDateTime.of(2026, 4, 11, 10, 0, 0);
        LocalDateTime t1 = LocalDateTime.of(2026, 4, 11, 11, 0, 0);
        AuditLog log1 = buildAuditLog("NODE_DELETED", t1, "carol");

        when(auditLogRepository.findByEventTypeInAndEventTimeAfterOrderByEventTimeAsc(
            eq(CMIS_EVENT_TYPES), eq(token), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(log1), PageRequest.of(0, 50), 1));

        CmisModels.ContentChangesResponse response = cmisChangeLogService.getContentChanges(
            token.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), 50
        );

        assertEquals(1, response.changes().size());
        assertEquals("deleted", response.changes().get(0).changeType());
        assertEquals("carol", response.changes().get(0).user());
        verify(auditLogRepository).findByEventTypeInAndEventTimeAfterOrderByEventTimeAsc(
            eq(CMIS_EVENT_TYPES), eq(token), any(Pageable.class)
        );
    }

    @Test
    @DisplayName("Event types are mapped correctly to CMIS change types")
    void mapsEventTypesCorrectly() {
        LocalDateTime t1 = LocalDateTime.of(2026, 4, 11, 10, 0, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 4, 11, 11, 0, 0);
        LocalDateTime t3 = LocalDateTime.of(2026, 4, 11, 12, 0, 0);
        LocalDateTime t4 = LocalDateTime.of(2026, 4, 11, 13, 0, 0);
        AuditLog created = buildAuditLog("NODE_CREATED", t1, "alice");
        AuditLog updated = buildAuditLog("NODE_UPDATED", t2, "alice");
        AuditLog deleted = buildAuditLog("NODE_DELETED", t3, "alice");
        AuditLog versionCreated = buildAuditLog("VERSION_CREATED", t4, "alice");

        when(auditLogRepository.findByEventTypeInOrderByEventTimeAsc(eq(CMIS_EVENT_TYPES), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(created, updated, deleted, versionCreated), PageRequest.of(0, 50), 4));

        CmisModels.ContentChangesResponse response = cmisChangeLogService.getContentChanges(null, 50);

        assertEquals("created", response.changes().get(0).changeType());
        assertEquals("updated", response.changes().get(1).changeType());
        assertEquals("deleted", response.changes().get(2).changeType());
        assertEquals("updated", response.changes().get(3).changeType());
    }

    @Test
    @DisplayName("latestChangeLogToken is the eventTime of the last entry in ISO format")
    void latestChangeLogTokenIsLastEntryTime() {
        LocalDateTime t1 = LocalDateTime.of(2026, 4, 11, 10, 0, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 4, 11, 14, 30, 45);
        AuditLog log1 = buildAuditLog("NODE_CREATED", t1, "alice");
        AuditLog log2 = buildAuditLog("NODE_UPDATED", t2, "bob");

        when(auditLogRepository.findByEventTypeInOrderByEventTimeAsc(eq(CMIS_EVENT_TYPES), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(log1, log2), PageRequest.of(0, 50), 2));

        CmisModels.ContentChangesResponse response = cmisChangeLogService.getContentChanges(null, 50);

        assertEquals("2026-04-11T14:30:45", response.latestChangeLogToken());
    }

    @Test
    @DisplayName("Empty result returns empty list and null token")
    void emptyResultReturnsEmptyListAndNullToken() {
        when(auditLogRepository.findByEventTypeInOrderByEventTimeAsc(eq(CMIS_EVENT_TYPES), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        CmisModels.ContentChangesResponse response = cmisChangeLogService.getContentChanges(null, 50);

        assertTrue(response.changes().isEmpty());
        assertNull(response.latestChangeLogToken());
        assertFalse(response.hasMoreItems());
    }

    @Test
    @DisplayName("hasMoreItems is true when more pages exist")
    void hasMoreItemsTrueWhenMorePagesExist() {
        LocalDateTime t1 = LocalDateTime.of(2026, 4, 11, 10, 0, 0);
        AuditLog log1 = buildAuditLog("NODE_CREATED", t1, "alice");

        // totalElements=5 but only returning 1 item on page of size 1 -> hasNext() is true
        when(auditLogRepository.findByEventTypeInOrderByEventTimeAsc(eq(CMIS_EVENT_TYPES), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(log1), PageRequest.of(0, 1), 5));

        CmisModels.ContentChangesResponse response = cmisChangeLogService.getContentChanges(null, 1);

        assertEquals(1, response.changes().size());
        assertTrue(response.hasMoreItems());
    }

    private AuditLog buildAuditLog(String eventType, LocalDateTime eventTime, String username) {
        return AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType(eventType)
            .nodeId(UUID.randomUUID())
            .nodeName("test-node")
            .username(username)
            .eventTime(eventTime)
            .details("test details")
            .build();
    }
}
