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
                null,
                null  // releaseReason added 2026-05-24 (migration 094)
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
                null,  // releaseReason
                0,
                List.of(),
                null   // bulkApplyResults (no nodeIds supplied)
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

    @Test
    @DisplayName("createHold with nodeIds returns dto carrying bulkApplyResults shape")
    void createHoldWithNodeIdsReturnsBulkApplyResults() throws Exception {
        UUID holdId = UUID.randomUUID();
        UUID n1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID n2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Mockito.when(legalHoldService.createHold(Mockito.any())).thenReturn(
            new LegalHoldService.LegalHoldDto(
                holdId,
                "Bulk Litigation",
                null,
                LegalHold.HoldStatus.ACTIVE,
                "admin",
                LocalDateTime.of(2026, 5, 24, 10, 0),
                null,
                null,
                null,
                null,
                1,
                List.of(),
                new LegalHoldService.BulkApplyResults(List.of(
                    LegalHoldService.BulkApplyResult.added(
                        n1,
                        new LegalHoldService.LegalHoldItemDto(
                            n1, "a.pdf", "DOCUMENT", "/X/a.pdf",
                            LocalDateTime.of(2026, 5, 24, 10, 0), "admin"
                        )
                    ),
                    LegalHoldService.BulkApplyResult.failed(
                        n2,
                        LegalHoldService.BulkApplyErrorCategory.NODE_NOT_FOUND,
                        "Requested node was not found."
                    )
                ))
            )
        );

        mockMvc.perform(post("/api/v1/legal-holds")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Bulk Litigation",
                      "nodeIds": ["11111111-1111-1111-1111-111111111111",
                                  "22222222-2222-2222-2222-222222222222"]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.bulkApplyResults.rows[0].status").value("ADDED"))
            .andExpect(jsonPath("$.bulkApplyResults.rows[1].status").value("FAILED"))
            .andExpect(jsonPath("$.bulkApplyResults.rows[1].errorCategory").value("NODE_NOT_FOUND"));
    }

    @Test
    @DisplayName("releaseHold missing releaseReason maps IllegalArgumentException to 400")
    void releaseHoldMissingReasonReturns400() throws Exception {
        UUID holdId = UUID.randomUUID();
        Mockito.when(legalHoldService.releaseHold(Mockito.eq(holdId), Mockito.any()))
            .thenThrow(new IllegalArgumentException("releaseReason is required"));

        mockMvc.perform(post("/api/v1/legal-holds/" + holdId + "/release")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "releaseReason": null, "comment": "no reason" }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("releaseHold with releaseReason returns 200 + releaseReason in body")
    void releaseHoldWithReasonReturns200WithBody() throws Exception {
        UUID holdId = UUID.randomUUID();
        Mockito.when(legalHoldService.releaseHold(Mockito.eq(holdId), Mockito.any())).thenReturn(
            new LegalHoldService.LegalHoldDto(
                holdId,
                "Hold",
                null,
                LegalHold.HoldStatus.RELEASED,
                "admin",
                LocalDateTime.of(2026, 4, 14, 10, 0),
                "admin",
                LocalDateTime.of(2026, 5, 24, 11, 0),
                "Matter closed",
                LegalHold.HoldReleaseReason.LITIGATION_ENDED,
                0,
                List.of(),
                null
            )
        );

        mockMvc.perform(post("/api/v1/legal-holds/" + holdId + "/release")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "releaseReason": "LITIGATION_ENDED", "comment": "Matter closed" }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.releaseReason").value("LITIGATION_ENDED"))
            .andExpect(jsonPath("$.status").value("RELEASED"))
            .andExpect(jsonPath("$.releaseComment").value("Matter closed"));
    }
}
