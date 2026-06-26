package com.ecm.core.controller;

import com.ecm.core.scheduler.SchedulerJobSnapshotDto;
import com.ecm.core.scheduler.SchedulerObservabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SchedulerAdminControllerTest {

    private final SchedulerObservabilityService service = mock(SchedulerObservabilityService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SchedulerAdminController(service)).build();
    }

    @Test
    void returnsSchedulerSnapshotShape() throws Exception {
        when(service.getSnapshot()).thenReturn(List.of(
            new SchedulerJobSnapshotDto(
                "com.ecm.core.service.TrashService#purgeOldTrashItems",
                LocalDateTime.parse("2026-06-25T02:00:00"),
                "SUCCESS",
                1200L,
                null,
                5L,
                0L,
                LocalDateTime.parse("2026-06-26T02:00:00"),
                "cron: 0 0 2 * * *"
            ),
            new SchedulerJobSnapshotDto(
                "com.ecm.core.integration.mail.service.MailFetcherService#fetchAllAccounts",
                LocalDateTime.parse("2026-06-25T10:00:00"),
                "FAILED",
                300L,
                "MailConnectException",
                40L,
                3L,
                null,
                "fixedDelay=60000ms"
            )
        ));

        mockMvc.perform(get("/api/v1/admin/schedulers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].jobId").value("com.ecm.core.service.TrashService#purgeOldTrashItems"))
            .andExpect(jsonPath("$[0].lastStatus").value("SUCCESS"))
            .andExpect(jsonPath("$[0].scheduleDescription").value("cron: 0 0 2 * * *"))
            .andExpect(jsonPath("$[1].lastStatus").value("FAILED"))
            .andExpect(jsonPath("$[1].lastErrorType").value("MailConnectException"))
            .andExpect(jsonPath("$[1].scheduleDescription").value("fixedDelay=60000ms"));
    }
}
