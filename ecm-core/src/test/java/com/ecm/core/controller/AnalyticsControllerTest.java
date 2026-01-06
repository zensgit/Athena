package com.ecm.core.controller;

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

import java.time.LocalDateTime;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
}
