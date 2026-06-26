package com.ecm.core.controller;

import com.ecm.core.queuebacklog.QueueBacklogObservabilityService;
import com.ecm.core.queuebacklog.QueueBacklogSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QueueBacklogAdminControllerTest {

    private final QueueBacklogObservabilityService service = mock(QueueBacklogObservabilityService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new QueueBacklogAdminController(service)).build();
    }

    @Test
    void returnsQueueBacklogShape() throws Exception {
        when(service.getSummary()).thenReturn(new QueueBacklogSummaryDto(
            new QueueBacklogSummaryDto.OcrBacklog(true, 7L, 120L),
            new QueueBacklogSummaryDto.MailBacklog(true, LocalDateTime.parse("2026-06-26T09:00:00"), 0.2d, 2L, "DEGRADED"),
            new QueueBacklogSummaryDto.TransferBacklog(true, 3L, 1L, 2L, 1800L, 1L, 60L)));

        mockMvc.perform(get("/api/v1/admin/queue-backlog"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ocr.available").value(true))
            .andExpect(jsonPath("$.ocr.pendingDepth").value(7))
            .andExpect(jsonPath("$.ocr.oldestPendingAgeSeconds").value(120))
            .andExpect(jsonPath("$.mail.errorRate").value(0.2))
            .andExpect(jsonPath("$.mail.status").value("DEGRADED"))
            .andExpect(jsonPath("$.transfer.pendingCount").value(3))
            .andExpect(jsonPath("$.transfer.runningCount").value(1))
            .andExpect(jsonPath("$.transfer.failedCount").value(2))
            .andExpect(jsonPath("$.transfer.stuckRunningCount").value(1))
            .andExpect(jsonPath("$.transfer.stuckThresholdMinutes").value(60));
    }
}
