package com.ecm.core.controller;

import com.ecm.core.failureinventory.FailureInventoryService;
import com.ecm.core.failureinventory.FailureInventorySummaryDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FailureInventoryAdminControllerTest {

    private final FailureInventoryService service = mock(FailureInventoryService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Production-equivalent JSON: Spring Boot's JacksonAutoConfiguration disables
        // WRITE_DATES_AS_TIMESTAMPS, so Instant serializes as an ISO-8601 string (what the
        // frontend's `latestFailedAt: string` + fmtTime expect). A bare standaloneSetup would
        // default to epoch-number timestamps and mask the real wire contract (repo convention:
        // the *ResponseContractTest siblings all install this same converter).
        ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new FailureInventoryAdminController(service))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
    }

    @Test
    void returnsFailureInventoryShape() throws Exception {
        when(service.getSummary()).thenReturn(new FailureInventorySummaryDto(
            new FailureInventorySummaryDto.PreviewDeadLetter(
                true, 5L, Map.of("TIMEOUT", 3L, "UNKNOWN", 2L), Instant.parse("2026-06-29T12:00:00Z")),
            new FailureInventorySummaryDto.TransferFailures(true, 4L),
            new FailureInventorySummaryDto.MailFetchErrors(true, 2L)));

        mockMvc.perform(get("/api/v1/admin/failure-inventory"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.preview.available").value(true))
            .andExpect(jsonPath("$.preview.deadLetterCount").value(5))
            .andExpect(jsonPath("$.preview.categoryTally.TIMEOUT").value(3))
            .andExpect(jsonPath("$.preview.categoryTally.UNKNOWN").value(2))
            .andExpect(jsonPath("$.preview.latestFailedAt").value("2026-06-29T12:00:00Z"))
            .andExpect(jsonPath("$.transfer.available").value(true))
            .andExpect(jsonPath("$.transfer.failedCount").value(4))
            .andExpect(jsonPath("$.mail.available").value(true))
            .andExpect(jsonPath("$.mail.errorAccountCount").value(2));
    }
}
