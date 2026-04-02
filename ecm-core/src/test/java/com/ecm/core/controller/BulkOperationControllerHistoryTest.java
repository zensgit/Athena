package com.ecm.core.controller;

import com.ecm.core.entity.AuditLog;
import com.ecm.core.repository.AuditLogRepository;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.BulkMetadataService;
import com.ecm.core.service.BulkOperationService;
import com.ecm.core.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BulkOperationControllerHistoryTest {

    private MockMvc mockMvc;

    @Mock
    private BulkOperationService bulkOperationService;

    @Mock
    private BulkMetadataService bulkMetadataService;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private SecurityService securityService;

    @BeforeEach
    void setUp() {
        BulkOperationController controller = new BulkOperationController(
            bulkOperationService,
            bulkMetadataService,
            auditLogRepository,
            auditService,
            securityService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("History endpoint returns bulk audit timeline items")
    void listBulkHistoryShouldReturnItems() throws Exception {
        AuditLog audit = AuditLog.builder()
            .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
            .eventType("BULK_DELETE_COMPLETED")
            .username("admin")
            .eventTime(LocalDateTime.parse("2026-03-13T12:00:00"))
            .details("Bulk DELETE requested=2 success=2 failed=0")
            .build();
        when(auditLogRepository.findBulkOperationTimelineNoNodeId(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(PageRequest.of(0, 20))
        )).thenReturn(new PageImpl<>(List.of(audit), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/bulk/history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].eventType").value("BULK_DELETE_COMPLETED"))
            .andExpect(jsonPath("$.items[0].username").value("admin"));
    }

    @Test
    @DisplayName("History export endpoint returns CSV and writes audit event")
    void exportBulkHistoryShouldReturnCsv() throws Exception {
        AuditLog audit = AuditLog.builder()
            .id(UUID.fromString("22222222-2222-2222-2222-222222222222"))
            .eventType("BULK_METADATA_UPDATE_PARTIAL")
            .username("editor")
            .eventTime(LocalDateTime.parse("2026-03-13T13:00:00"))
            .details("Bulk metadata update requested=3 success=2 failed=1")
            .build();
        when(auditLogRepository.findBulkOperationTimelineNoNodeId(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(PageRequest.of(0, 500))
        )).thenReturn(new PageImpl<>(List.of(audit), PageRequest.of(0, 500), 1));
        when(securityService.getCurrentUser()).thenReturn("admin");

        mockMvc.perform(get("/api/v1/bulk/history/export"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("id,eventType,nodeId,nodeName,username,eventTime,details")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("BULK_METADATA_UPDATE_PARTIAL")));

        verify(auditService).logEvent(
            eq("BULK_HISTORY_EXPORTED"),
            isNull(),
            eq("bulk-history"),
            eq("admin"),
            anyString()
        );
        verify(auditLogRepository).findBulkOperationTimelineNoNodeId(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            any(PageRequest.class)
        );
    }

    @Test
    @DisplayName("History summary endpoint returns total/eventType/actor counters")
    void summarizeBulkHistoryShouldReturnSummary() throws Exception {
        when(auditLogRepository.findBulkOperationTimelineNoNodeId(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(PageRequest.of(0, 1))
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 7));
        when(auditLogRepository.countBulkByEventTypeWithFilters(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull()
        )).thenReturn(List.<Object[]>of(
            new Object[]{"BULK_DELETE_COMPLETED", 4L},
            new Object[]{"BULK_METADATA_UPDATE_PARTIAL", 2L}
        ));
        when(auditLogRepository.countBulkByUsernameWithFilters(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull()
        )).thenReturn(List.<Object[]>of(
            new Object[]{"admin", 5L},
            new Object[]{"editor", 2L}
        ));

        mockMvc.perform(get("/api/v1/bulk/history/summary").param("topN", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(7))
            .andExpect(jsonPath("$.topN").value(2))
            .andExpect(jsonPath("$.eventTypeItems.length()").value(2))
            .andExpect(jsonPath("$.eventTypeItems[0].key").value("BULK_DELETE_COMPLETED"))
            .andExpect(jsonPath("$.eventTypeItems[0].count").value(4))
            .andExpect(jsonPath("$.actorItems.length()").value(2))
            .andExpect(jsonPath("$.actorItems[0].key").value("admin"))
            .andExpect(jsonPath("$.actorItems[0].count").value(5));
    }

    @Test
    @DisplayName("History summary export endpoint returns CSV and writes audit event")
    void exportBulkHistorySummaryShouldReturnCsv() throws Exception {
        when(auditLogRepository.findBulkOperationTimelineNoNodeId(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(PageRequest.of(0, 1))
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 3));
        when(auditLogRepository.countBulkByEventTypeWithFilters(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull()
        )).thenReturn(List.<Object[]>of(new Object[]{"BULK_DELETE_COMPLETED", 3L}));
        when(auditLogRepository.countBulkByUsernameWithFilters(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull()
        )).thenReturn(List.<Object[]>of(new Object[]{"admin", 3L}));
        when(securityService.getCurrentUser()).thenReturn("admin");

        mockMvc.perform(get("/api/v1/bulk/history/summary/export"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("section,key,count")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("meta,total,3")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("eventType,BULK_DELETE_COMPLETED,3")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("actor,admin,3")));

        verify(auditService).logEvent(
            eq("BULK_HISTORY_SUMMARY_EXPORTED"),
            isNull(),
            eq("bulk-history-summary"),
            eq("admin"),
            anyString()
        );
    }

    @Test
    @DisplayName("History trend endpoint returns daily counters")
    void trendBulkHistorySummaryShouldReturnDailyCounters() throws Exception {
        AuditLog first = AuditLog.builder()
            .id(UUID.fromString("33333333-3333-3333-3333-333333333333"))
            .eventType("BULK_DELETE_COMPLETED")
            .username("admin")
            .eventTime(LocalDateTime.parse("2026-03-13T08:00:00"))
            .build();
        AuditLog second = AuditLog.builder()
            .id(UUID.fromString("44444444-4444-4444-4444-444444444444"))
            .eventType("BULK_DELETE_COMPLETED")
            .username("editor")
            .eventTime(LocalDateTime.parse("2026-03-13T09:30:00"))
            .build();
        AuditLog third = AuditLog.builder()
            .id(UUID.fromString("55555555-5555-5555-5555-555555555555"))
            .eventType("BULK_METADATA_UPDATE_COMPLETED")
            .username("admin")
            .eventTime(LocalDateTime.parse("2026-03-12T18:15:00"))
            .build();
        when(auditLogRepository.findBulkOperationTimelineNoNodeId(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(PageRequest.of(0, 500))
        )).thenReturn(new PageImpl<>(List.of(first, second, third), PageRequest.of(0, 500), 3));

        mockMvc.perform(get("/api/v1/bulk/history/summary/trend"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scanLimit").value(20000))
            .andExpect(jsonPath("$.truncated").value(false))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].date").value("2026-03-12"))
            .andExpect(jsonPath("$.items[0].count").value(1))
            .andExpect(jsonPath("$.items[1].date").value("2026-03-13"))
            .andExpect(jsonPath("$.items[1].count").value(2));
    }

    @Test
    @DisplayName("History trend export endpoint returns CSV and writes audit event")
    void exportBulkHistoryTrendShouldReturnCsv() throws Exception {
        AuditLog audit = AuditLog.builder()
            .id(UUID.fromString("66666666-6666-6666-6666-666666666666"))
            .eventType("BULK_DELETE_COMPLETED")
            .username("admin")
            .eventTime(LocalDateTime.parse("2026-03-13T10:00:00"))
            .details("Bulk DELETE requested=1 success=1 failed=0")
            .build();
        when(auditLogRepository.findBulkOperationTimelineNoNodeId(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(PageRequest.of(0, 500))
        )).thenReturn(new PageImpl<>(List.of(audit), PageRequest.of(0, 500), 1));
        when(securityService.getCurrentUser()).thenReturn("admin");

        mockMvc.perform(get("/api/v1/bulk/history/summary/trend/export"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("date,count")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-03-13,1")));

        verify(auditService).logEvent(
            eq("BULK_HISTORY_TREND_EXPORTED"),
            isNull(),
            eq("bulk-history-trend"),
            eq("admin"),
            anyString()
        );
    }
}
