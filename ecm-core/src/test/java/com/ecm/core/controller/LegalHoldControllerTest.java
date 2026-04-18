package com.ecm.core.controller;

import com.ecm.core.entity.LegalHold;
import com.ecm.core.service.LegalHoldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LegalHoldControllerTest {

    private MockMvc mockMvc;

    @Mock
    private LegalHoldService legalHoldService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LegalHoldController(legalHoldService))
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("listHolds returns summary payload")
    void listHoldsReturnsSummaryPayload() throws Exception {
        UUID holdId = UUID.randomUUID();
        Mockito.when(legalHoldService.listHolds()).thenReturn(List.of(
            new LegalHoldService.LegalHoldSummaryDto(
                holdId,
                "Quarter Close",
                "Finance records",
                LegalHold.HoldStatus.ACTIVE,
                2,
                "admin",
                LocalDateTime.of(2026, 4, 14, 9, 0),
                null,
                null
            )
        ));

        mockMvc.perform(get("/api/v1/legal-holds"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(holdId.toString()))
            .andExpect(jsonPath("$[0].name").value("Quarter Close"))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$[0].itemCount").value(2));
    }

    @Test
    @DisplayName("createHold returns created payload")
    void createHoldReturnsCreatedPayload() throws Exception {
        UUID holdId = UUID.randomUUID();
        Mockito.when(legalHoldService.createHold(Mockito.any())).thenReturn(
            new LegalHoldService.LegalHoldDto(
                holdId,
                "Litigation 2026",
                "Preserve finance records",
                LegalHold.HoldStatus.ACTIVE,
                "admin",
                LocalDateTime.of(2026, 4, 14, 10, 0),
                null,
                null,
                null,
                0,
                List.of()
            )
        );

        mockMvc.perform(post("/api/v1/legal-holds")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Litigation 2026",
                      "description": "Preserve finance records"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(holdId.toString()))
            .andExpect(jsonPath("$.name").value("Litigation 2026"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.itemCount").value(0));
    }
}
