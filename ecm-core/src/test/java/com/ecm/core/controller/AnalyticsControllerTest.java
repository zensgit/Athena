package com.ecm.core.controller;

import com.ecm.core.entity.AuditLog;
import com.ecm.core.service.AnalyticsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AnalyticsService analyticsService;

    @InjectMocks
    private AnalyticsController analyticsController;

    private TimeZone originalTimeZone;

    @BeforeEach
    void setup() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        ReflectionTestUtils.setField(analyticsController, "auditExportMaxRangeDays", 30);
        mockMvc = MockMvcBuilders.standaloneSetup(analyticsController).build();
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    @DisplayName("Audit export accepts ISO offset datetime")
    void exportAuditLogsAcceptsOffsetDatetime() throws Exception {
        Mockito.when(analyticsService.exportAuditLogsCsv(Mockito.any(), Mockito.any()))
            .thenReturn(new AnalyticsService.AuditExportResult("header\n", 0));

        mockMvc.perform(get("/api/v1/analytics/audit/export")
                .param("from", "2026-01-05T10:15:30+08:00")
                .param("to", "2026-01-05T12:15:30+08:00"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")))
            .andExpect(header().string("X-Audit-Export-Count", "0"));

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        Mockito.verify(analyticsService).exportAuditLogsCsv(fromCaptor.capture(), toCaptor.capture());

        assertEquals(LocalDateTime.of(2026, 1, 5, 2, 15, 30), fromCaptor.getValue());
        assertEquals(LocalDateTime.of(2026, 1, 5, 4, 15, 30), toCaptor.getValue());
    }

    @Test
    @DisplayName("Audit export accepts local datetime without offset")
    void exportAuditLogsAcceptsLocalDatetime() throws Exception {
        Mockito.when(analyticsService.exportAuditLogsCsv(Mockito.any(), Mockito.any()))
            .thenReturn(new AnalyticsService.AuditExportResult("header\n", 0));

        mockMvc.perform(get("/api/v1/analytics/audit/export")
                .param("from", "2026-01-05T10:15:30")
                .param("to", "2026-01-05T12:15:30"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")));

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        Mockito.verify(analyticsService).exportAuditLogsCsv(fromCaptor.capture(), toCaptor.capture());

        assertEquals(LocalDateTime.of(2026, 1, 5, 10, 15, 30), fromCaptor.getValue());
        assertEquals(LocalDateTime.of(2026, 1, 5, 12, 15, 30), toCaptor.getValue());
    }

    @Test
    @DisplayName("Audit export rejects invalid datetime")
    void exportAuditLogsRejectsInvalidDatetime() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/audit/export")
                .param("from", "not-a-date")
                .param("to", "2026-01-05T12:15:30"))
            .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("Audit export rejects reversed range")
    void exportAuditLogsRejectsReversedRange() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/audit/export")
                .param("from", "2026-01-05T12:15:30")
                .param("to", "2026-01-05T10:15:30"))
            .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("Audit export rejects range after offset normalization")
    void exportAuditLogsRejectsOffsetNormalizedRange() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/audit/export")
                .param("from", "2026-01-05T10:00:00+08:00")
                .param("to", "2026-01-05T01:00:00+00:00"))
            .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("Audit export rejects empty range")
    void exportAuditLogsRejectsEmptyRange() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/audit/export")
                .param("from", "2026-01-05T10:15:30")
                .param("to", "2026-01-05T10:15:30"))
            .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("Audit export rejects range exceeding max window")
    void exportAuditLogsRejectsRangeExceedingMax() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/audit/export")
                .param("from", "2026-01-01T00:00:00")
                .param("to", "2026-02-02T00:00:00"))
            .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("Audit export rejects blank parameters")
    void exportAuditLogsRejectsBlankParameters() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/audit/export")
                .param("from", " ")
                .param("to", "2026-01-05T12:15:30"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/analytics/audit/export")
                .param("from", "2026-01-05T10:15:30")
                .param("to", " "))
            .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("Audit export accepts range at max window")
    void exportAuditLogsAcceptsMaxWindow() throws Exception {
        Mockito.when(analyticsService.exportAuditLogsCsv(Mockito.any(), Mockito.any()))
            .thenReturn(new AnalyticsService.AuditExportResult("header\nrow\n", 1));

        mockMvc.perform(get("/api/v1/analytics/audit/export")
                .param("from", "2026-01-01T00:00:00")
                .param("to", "2026-01-31T00:00:00"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Audit-Export-Count", "1"));

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        Mockito.verify(analyticsService).exportAuditLogsCsv(fromCaptor.capture(), toCaptor.capture());

        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0, 0), fromCaptor.getValue());
        assertEquals(LocalDateTime.of(2026, 1, 31, 0, 0, 0), toCaptor.getValue());
    }

    @Test
    @DisplayName("Audit retention info returns policy details")
    void auditRetentionInfoReturnsPolicyDetails() throws Exception {
        Mockito.when(analyticsService.getAuditRetentionDays()).thenReturn(120);
        Mockito.when(analyticsService.getExpiredAuditLogCount()).thenReturn(42L);

        mockMvc.perform(get("/api/v1/analytics/audit/retention"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.retentionDays").value(120))
            .andExpect(jsonPath("$.expiredLogCount").value(42))
            .andExpect(jsonPath("$.exportMaxRangeDays").value(30));
    }

    @Test
    @DisplayName("Audit cleanup returns deletion summary")
    void auditCleanupReturnsDeletionSummary() throws Exception {
        Mockito.when(analyticsService.manualCleanupExpiredAuditLogs()).thenReturn(7L);
        Mockito.when(analyticsService.getAuditRetentionDays()).thenReturn(120);

        mockMvc.perform(post("/api/v1/analytics/audit/cleanup"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletedCount").value(7))
            .andExpect(jsonPath("$.retentionDays").value(120))
            .andExpect(jsonPath("$.message").value("Deleted 7 expired audit logs"));
    }

    @Test
    @DisplayName("Audit cleanup returns no-op message when nothing deleted")
    void auditCleanupReturnsNoopMessage() throws Exception {
        Mockito.when(analyticsService.manualCleanupExpiredAuditLogs()).thenReturn(0L);
        Mockito.when(analyticsService.getAuditRetentionDays()).thenReturn(90);

        mockMvc.perform(post("/api/v1/analytics/audit/cleanup"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletedCount").value(0))
            .andExpect(jsonPath("$.retentionDays").value(90))
            .andExpect(jsonPath("$.message").value("No expired audit logs to delete"));
    }

    @Test
    @DisplayName("Recent audit defaults to limit 50")
    void recentAuditDefaultsToLimit() throws Exception {
        AuditLog log = new AuditLog();
        log.setEventType("LOGIN");
        log.setUsername("alice");
        log.setEventTime(LocalDateTime.of(2026, 1, 1, 0, 0));

        Mockito.when(analyticsService.getRecentActivity(50)).thenReturn(List.of(log));

        mockMvc.perform(get("/api/v1/analytics/audit/recent"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].eventType").value("LOGIN"))
            .andExpect(jsonPath("$[0].username").value("alice"));

        Mockito.verify(analyticsService).getRecentActivity(50);
    }

    @Test
    @DisplayName("Recent audit respects explicit limit parameter")
    void recentAuditRespectsLimitParameter() throws Exception {
        AuditLog log = new AuditLog();
        log.setEventType("VIEW");
        log.setUsername("bob");
        log.setEventTime(LocalDateTime.of(2026, 1, 2, 0, 0));

        Mockito.when(analyticsService.getRecentActivity(5)).thenReturn(List.of(log));

        mockMvc.perform(get("/api/v1/analytics/audit/recent")
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].eventType").value("VIEW"))
            .andExpect(jsonPath("$[0].username").value("bob"));

        Mockito.verify(analyticsService).getRecentActivity(5);
    }

    @Test
    @DisplayName("Recent rule activity defaults to limit 20")
    void recentRuleActivityDefaultsToLimit() throws Exception {
        AuditLog log = new AuditLog();
        log.setEventType("RULE_EXECUTED");
        log.setUsername("system");
        log.setEventTime(LocalDateTime.of(2026, 1, 3, 0, 0));

        Mockito.when(analyticsService.getRecentRuleActivity(20)).thenReturn(List.of(log));

        mockMvc.perform(get("/api/v1/analytics/rules/recent"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].eventType").value("RULE_EXECUTED"))
            .andExpect(jsonPath("$[0].username").value("system"));

        Mockito.verify(analyticsService).getRecentRuleActivity(20);
    }

    @Test
    @DisplayName("Recent rule activity respects explicit limit parameter")
    void recentRuleActivityRespectsLimitParameter() throws Exception {
        AuditLog log = new AuditLog();
        log.setEventType("RULE_EXECUTED");
        log.setUsername("automation");
        log.setEventTime(LocalDateTime.of(2026, 1, 4, 0, 0));

        Mockito.when(analyticsService.getRecentRuleActivity(3)).thenReturn(List.of(log));

        mockMvc.perform(get("/api/v1/analytics/rules/recent")
                .param("limit", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].eventType").value("RULE_EXECUTED"))
            .andExpect(jsonPath("$[0].username").value("automation"));

        Mockito.verify(analyticsService).getRecentRuleActivity(3);
    }

    @Test
    @DisplayName("Daily activity defaults to 30 days")
    void dailyActivityDefaultsToWindow() throws Exception {
        List<AnalyticsService.DailyActivityStats> stats = List.of(
            new AnalyticsService.DailyActivityStats(LocalDate.of(2026, 1, 1), 4)
        );

        Mockito.when(analyticsService.getDailyActivity(30)).thenReturn(stats);

        mockMvc.perform(get("/api/v1/analytics/activity/daily"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].date[0]").value(2026))
            .andExpect(jsonPath("$[0].date[1]").value(1))
            .andExpect(jsonPath("$[0].date[2]").value(1))
            .andExpect(jsonPath("$[0].eventCount").value(4));

        Mockito.verify(analyticsService).getDailyActivity(30);
    }

    @Test
    @DisplayName("Daily activity respects explicit days parameter")
    void dailyActivityRespectsDaysParameter() throws Exception {
        List<AnalyticsService.DailyActivityStats> stats = List.of(
            new AnalyticsService.DailyActivityStats(LocalDate.of(2026, 1, 2), 2)
        );

        Mockito.when(analyticsService.getDailyActivity(7)).thenReturn(stats);

        mockMvc.perform(get("/api/v1/analytics/activity/daily")
                .param("days", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].date[0]").value(2026))
            .andExpect(jsonPath("$[0].date[1]").value(1))
            .andExpect(jsonPath("$[0].date[2]").value(2))
            .andExpect(jsonPath("$[0].eventCount").value(2));

        Mockito.verify(analyticsService).getDailyActivity(7);
    }

    @Test
    @DisplayName("Top users defaults to limit 10")
    void topUsersDefaultsToLimit() throws Exception {
        List<AnalyticsService.UserActivityStats> stats = List.of(
            new AnalyticsService.UserActivityStats("alice", 3)
        );

        Mockito.when(analyticsService.getTopUsers(10)).thenReturn(stats);

        mockMvc.perform(get("/api/v1/analytics/users/top"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].username").value("alice"))
            .andExpect(jsonPath("$[0].activityCount").value(3));

        Mockito.verify(analyticsService).getTopUsers(10);
    }

    @Test
    @DisplayName("Top users respects explicit limit parameter")
    void topUsersRespectsLimitParameter() throws Exception {
        List<AnalyticsService.UserActivityStats> stats = List.of(
            new AnalyticsService.UserActivityStats("bob", 1)
        );

        Mockito.when(analyticsService.getTopUsers(5)).thenReturn(stats);

        mockMvc.perform(get("/api/v1/analytics/users/top")
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].username").value("bob"))
            .andExpect(jsonPath("$[0].activityCount").value(1));

        Mockito.verify(analyticsService).getTopUsers(5);
    }

    @Test
    @DisplayName("Dashboard aggregates summary, storage, activity, and top users")
    void dashboardAggregatesAnalytics() throws Exception {
        AnalyticsService.SystemSummaryStats summary = new AnalyticsService.SystemSummaryStats(10, 4, 2048);
        List<AnalyticsService.MimeTypeStats> storage = List.of(
            new AnalyticsService.MimeTypeStats("application/pdf", 2, 1024)
        );
        List<AnalyticsService.DailyActivityStats> activity = List.of(
            new AnalyticsService.DailyActivityStats(LocalDate.of(2026, 1, 5), 6)
        );
        List<AnalyticsService.UserActivityStats> topUsers = List.of(
            new AnalyticsService.UserActivityStats("carol", 8)
        );

        Mockito.when(analyticsService.getSystemSummary()).thenReturn(summary);
        Mockito.when(analyticsService.getStorageByMimeType()).thenReturn(storage);
        Mockito.when(analyticsService.getDailyActivity(14)).thenReturn(activity);
        Mockito.when(analyticsService.getTopUsers(5)).thenReturn(topUsers);

        mockMvc.perform(get("/api/v1/analytics/dashboard"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.totalDocuments").value(10))
            .andExpect(jsonPath("$.summary.totalFolders").value(4))
            .andExpect(jsonPath("$.summary.totalSizeBytes").value(2048))
            .andExpect(jsonPath("$.storage[0].mimeType").value("application/pdf"))
            .andExpect(jsonPath("$.activity[0].date[0]").value(2026))
            .andExpect(jsonPath("$.activity[0].date[2]").value(5))
            .andExpect(jsonPath("$.topUsers[0].username").value("carol"))
            .andExpect(jsonPath("$.topUsers[0].activityCount").value(8));

        Mockito.verify(analyticsService).getSystemSummary();
        Mockito.verify(analyticsService).getStorageByMimeType();
        Mockito.verify(analyticsService).getDailyActivity(14);
        Mockito.verify(analyticsService).getTopUsers(5);
    }
}
