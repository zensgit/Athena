package com.ecm.core.controller;

import com.ecm.core.entity.RmReportPreset;
import com.ecm.core.service.RmReportPresetService;
import com.ecm.core.service.RecordsManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RecordsManagementControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RecordsManagementService recordsManagementService;

    @Mock
    private RmReportPresetService rmReportPresetService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RecordsManagementController(recordsManagementService, rmReportPresetService))
            .setControllerAdvice(new RestExceptionHandler())
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @Test
    @DisplayName("listRecords returns declared record payload")
    void listRecordsReturnsPayload() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Mockito.when(recordsManagementService.listRecords()).thenReturn(List.of(
            new RecordsManagementService.RecordDeclarationDto(
                nodeId,
                "report.pdf",
                "/Sites/Finance/report.pdf",
                "1.2",
                "1.2",
                "admin",
                LocalDateTime.of(2026, 4, 14, 18, 30),
                "Quarter close record",
                UUID.randomUUID(),
                "Contracts",
                "/Records Management/Contracts"
            )
        ));

        mockMvc.perform(get("/api/v1/records"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nodeId").value(nodeId.toString()))
            .andExpect(jsonPath("$[0].name").value("report.pdf"))
            .andExpect(jsonPath("$[0].declaredBy").value("admin"));
    }

    @Test
    @DisplayName("getSummary returns aggregate RM counts")
    void getSummaryReturnsAggregateCounts() throws Exception {
        Mockito.when(recordsManagementService.getSummary()).thenReturn(
            new RecordsManagementService.RecordsSummaryDto(
                5,
                2,
                4,
                1,
                1,
                List.of(new RecordsManagementService.SummaryBucketDto("/Records Management/Contracts", 3)),
                List.of(new RecordsManagementService.SummaryBucketDto("/Corporate File Plan", 4))
            )
        );

        mockMvc.perform(get("/api/v1/records/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.declaredRecordCount").value(5))
            .andExpect(jsonPath("$.filePlanCount").value(2))
            .andExpect(jsonPath("$.categoryBreakdown[0].count").value(3));
    }

    @Test
    @DisplayName("listAudit returns RM audit timeline payload")
    void listAuditReturnsPayload() throws Exception {
        UUID auditId = UUID.randomUUID();
        Mockito.when(recordsManagementService.listAudit(
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.any()))
            .thenReturn(new PageImpl<>(
                List.of(new RecordsManagementService.RecordAuditEntryDto(
                    auditId,
                    "RM_RECORD_DECLARED",
                    UUID.randomUUID(),
                    "report.pdf",
                    "admin",
                    LocalDateTime.of(2026, 4, 14, 20, 0),
                    "Declared document as record"
                )),
                PageRequest.of(0, 20),
                1
            ));

        mockMvc.perform(get("/api/v1/records/audit"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].auditLogId").value(auditId.toString()))
            .andExpect(jsonPath("$.content[0].eventType").value("RM_RECORD_DECLARED"));
    }

    @Test
    @DisplayName("listAudit passes to parameter to service")
    void listAuditPassesToParameter() throws Exception {
        LocalDateTime from = LocalDateTime.of(2026, 4, 10, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 4, 14, 23, 59, 59);
        UUID auditId = UUID.randomUUID();
        Mockito.when(recordsManagementService.listAudit(
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.eq(from),
                Mockito.eq(to),
                Mockito.isNull(),
                Mockito.any()))
            .thenReturn(new PageImpl<>(
                List.of(new RecordsManagementService.RecordAuditEntryDto(
                    auditId,
                    "RM_RECORD_DECLARED",
                    UUID.randomUUID(),
                    "report.pdf",
                    "admin",
                    LocalDateTime.of(2026, 4, 12, 15, 0),
                    "Declared document as record"
                )),
                PageRequest.of(0, 20),
                1
            ));

        mockMvc.perform(get("/api/v1/records/audit")
                .param("from", "2026-04-10T00:00:00")
                .param("to", "2026-04-14T23:59:59"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].auditLogId").value(auditId.toString()));

        Mockito.verify(recordsManagementService).listAudit(
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.eq(from),
            Mockito.eq(to),
            Mockito.isNull(),
            Mockito.any());
    }

    @Test
    @DisplayName("listAudit passes family parameter to service")
    void listAuditPassesFamilyParameter() throws Exception {
        Mockito.when(recordsManagementService.listAudit(
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.eq(RecordsManagementService.RmEventFamily.DECLARED),
                Mockito.any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/records/audit")
                .param("family", "DECLARED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty());

        Mockito.verify(recordsManagementService).listAudit(
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.eq(RecordsManagementService.RmEventFamily.DECLARED),
            Mockito.any());
    }

    @Test
    @DisplayName("listAudit with invalid family returns 400")
    void listAuditWithInvalidFamilyReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/records/audit")
                .param("family", "INVALID_FAMILY"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("getOperationsTelemetry returns governed import and transfer summary")
    void getOperationsTelemetryReturnsPayload() throws Exception {
        Mockito.when(recordsManagementService.getOperationsTelemetry(5)).thenReturn(
            new RecordsManagementService.RecordsOperationsTelemetryDto(
                2,
                1,
                1,
                3,
                1,
                2,
                List.of(new RecordsManagementService.SummaryBucketDto("RUNNING", 1)),
                List.of(new RecordsManagementService.SummaryBucketDto("COMPLETED / SUCCESS", 2)),
                List.of(new RecordsManagementService.SummaryBucketDto("TARGET_FILE_PLAN", 2)),
                List.of(new RecordsManagementService.SummaryBucketDto("SOURCE_DECLARED_RECORD", 1)),
                List.of(),
                List.of()
            )
        );

        mockMvc.perform(get("/api/v1/records/operations").param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.governedImportJobCount").value(2))
            .andExpect(jsonPath("$.failedGovernedImportJobCount").value(1))
            .andExpect(jsonPath("$.activeGovernedTransferJobCount").value(1))
            .andExpect(jsonPath("$.transferStatusBreakdown[0].key").value("COMPLETED / SUCCESS"))
            .andExpect(jsonPath("$.importGovernanceReasonBreakdown[0].key").value("TARGET_FILE_PLAN"));
    }

    @Test
    @DisplayName("getActivityTimeline returns RM activity trend payload")
    void getActivityTimelineReturnsPayload() throws Exception {
        Mockito.when(recordsManagementService.getActivityTimeline(7)).thenReturn(
            new RecordsManagementService.RecordsActivityTimelineDto(
                7,
                List.of(
                    new RecordsManagementService.RecordsActivityPointDto(
                        "2026-04-13",
                        2,
                        0,
                        1,
                        0,
                        3
                    ),
                    new RecordsManagementService.RecordsActivityPointDto(
                        "2026-04-14",
                        1,
                        1,
                        0,
                        2,
                        4
                    )
                )
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-timeline").param("days", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.days").value(7))
            .andExpect(jsonPath("$.points[0].day").value("2026-04-13"))
            .andExpect(jsonPath("$.points[1].governanceChangeCount").value(2))
            .andExpect(jsonPath("$.points[1].totalCount").value(4));
    }

    @Test
    @DisplayName("getActivityHighlights returns RM activity highlight payload")
    void getActivityHighlightsReturnsPayload() throws Exception {
        Mockito.when(recordsManagementService.getActivityHighlights(7)).thenReturn(
            new RecordsManagementService.RecordsActivityHighlightsDto(
                7,
                new RecordsManagementService.RecordsActivityWindowDto(
                    "2026-04-08",
                    "2026-04-14",
                    5,
                    3,
                    1,
                    4,
                    2,
                    10
                ),
                new RecordsManagementService.RecordsActivityWindowDto(
                    "2026-04-01",
                    "2026-04-07",
                    4,
                    1,
                    0,
                    2,
                    1,
                    4
                ),
                new RecordsManagementService.RecordsActivityPeakDto("2026-04-14", 4)
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-highlights").param("windowDays", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowDays").value(7))
            .andExpect(jsonPath("$.currentWindow.fromDay").value("2026-04-08"))
            .andExpect(jsonPath("$.currentWindow.totalCount").value(10))
            .andExpect(jsonPath("$.previousWindow.categoryAssignedCount").value(2))
            .andExpect(jsonPath("$.busiestDay.day").value("2026-04-14"))
            .andExpect(jsonPath("$.busiestDay.totalCount").value(4));
    }

    @Test
    @DisplayName("getActivityContributors returns top RM activity contributors")
    void getActivityContributorsReturnsPayload() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributors(28, 5)).thenReturn(
            new RecordsManagementService.ActivityContributorsDto(
                28,
                5,
                List.of(
                    new RecordsManagementService.ActivityContributorDto(
                        "admin",
                        "admin",
                        5,
                        2,
                        1,
                        1,
                        9,
                        LocalDateTime.of(2026, 4, 15, 10, 0)
                    ),
                    new RecordsManagementService.ActivityContributorDto(
                        null,
                        "(System)",
                        0,
                        0,
                        0,
                        2,
                        2,
                        LocalDateTime.of(2026, 4, 14, 8, 0)
                    )
                )
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-contributors")
                .param("days", "28")
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.days").value(28))
            .andExpect(jsonPath("$.limit").value(5))
            .andExpect(jsonPath("$.contributors[0].username").value("admin"))
            .andExpect(jsonPath("$.contributors[0].label").value("admin"))
            .andExpect(jsonPath("$.contributors[0].declaredCount").value(5))
            .andExpect(jsonPath("$.contributors[0].totalCount").value(9))
            .andExpect(jsonPath("$.contributors[1].username").isEmpty())
            .andExpect(jsonPath("$.contributors[1].label").value("(System)"))
            .andExpect(jsonPath("$.contributors[1].governanceChangeCount").value(2));
    }

    @Test
    @DisplayName("getActivityContributors uses defaults when no params")
    void getActivityContributorsUsesDefaults() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributors(null, null)).thenReturn(
            new RecordsManagementService.ActivityContributorsDto(28, 5, List.of())
        );

        mockMvc.perform(get("/api/v1/records/activity-contributors"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.days").value(28))
            .andExpect(jsonPath("$.limit").value(5))
            .andExpect(jsonPath("$.contributors").isEmpty());
    }

    @Test
    @DisplayName("getActivityContributorHighlights returns RM contributor highlights payload")
    void getActivityContributorHighlightsReturnsPayload() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributorHighlights(7, 5)).thenReturn(
            new RecordsManagementService.ActivityContributorHighlightsDto(
                7,
                5,
                new RecordsManagementService.ActivityWindowRangeDto("2026-04-09", "2026-04-15"),
                new RecordsManagementService.ActivityWindowRangeDto("2026-04-02", "2026-04-08"),
                List.of(
                    new RecordsManagementService.ActivityContributorHighlightDto(
                        "admin",
                        "admin",
                        8,
                        4,
                        4,
                        LocalDateTime.of(2026, 4, 15, 10, 0)
                    )
                )
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-contributor-highlights")
                .param("windowDays", "7")
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowDays").value(7))
            .andExpect(jsonPath("$.limit").value(5))
            .andExpect(jsonPath("$.currentWindow.fromDay").value("2026-04-09"))
            .andExpect(jsonPath("$.contributors[0].username").value("admin"))
            .andExpect(jsonPath("$.contributors[0].delta").value(4));
    }

    @Test
    @DisplayName("getActivityContributorHighlights uses defaults when no params")
    void getActivityContributorHighlightsUsesDefaults() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributorHighlights(null, null)).thenReturn(
            new RecordsManagementService.ActivityContributorHighlightsDto(
                7,
                5,
                new RecordsManagementService.ActivityWindowRangeDto("2026-04-09", "2026-04-15"),
                new RecordsManagementService.ActivityWindowRangeDto("2026-04-02", "2026-04-08"),
                List.of()
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-contributor-highlights"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowDays").value(7))
            .andExpect(jsonPath("$.limit").value(5))
            .andExpect(jsonPath("$.contributors").isEmpty());
    }

    @Test
    @DisplayName("getActivityContributorEventTypeHighlights returns RM contributor event-type highlights payload")
    void getActivityContributorEventTypeHighlightsReturnsPayload() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributorEventTypeHighlights(7, 5, 3)).thenReturn(
            new RecordsManagementService.ActivityContributorEventTypeHighlightsDto(
                7,
                5,
                3,
                new RecordsManagementService.ActivityWindowRangeDto("2026-04-10", "2026-04-16"),
                new RecordsManagementService.ActivityWindowRangeDto("2026-04-03", "2026-04-09"),
                List.of(
                    new RecordsManagementService.ActivityContributorEventTypeReportEntryDto(
                        "admin",
                        "admin",
                        8,
                        2,
                        6,
                        LocalDateTime.of(2026, 4, 16, 10, 0),
                        List.of(
                            new RecordsManagementService.ActivityContributorEventTypeReportEventTypeDto(
                                "RM_RECORD_DECLARED",
                                "DECLARED",
                                5,
                                2,
                                3,
                                LocalDateTime.of(2026, 4, 16, 10, 0)
                            )
                        )
                    )
                )
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-contributor-event-type-highlights")
                .param("windowDays", "7")
                .param("limit", "5")
                .param("eventTypeLimit", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowDays").value(7))
            .andExpect(jsonPath("$.limit").value(5))
            .andExpect(jsonPath("$.eventTypeLimit").value(3))
            .andExpect(jsonPath("$.currentWindow.fromDay").value("2026-04-10"))
            .andExpect(jsonPath("$.contributors[0].username").value("admin"))
            .andExpect(jsonPath("$.contributors[0].eventTypes[0].eventType").value("RM_RECORD_DECLARED"))
            .andExpect(jsonPath("$.contributors[0].eventTypes[0].delta").value(3));
    }

    @Test
    @DisplayName("getActivityContributorEventTypeHighlights uses defaults when no params")
    void getActivityContributorEventTypeHighlightsUsesDefaults() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributorEventTypeHighlights(null, null, null)).thenReturn(
            new RecordsManagementService.ActivityContributorEventTypeHighlightsDto(
                7,
                5,
                3,
                new RecordsManagementService.ActivityWindowRangeDto("2026-04-10", "2026-04-16"),
                new RecordsManagementService.ActivityWindowRangeDto("2026-04-03", "2026-04-09"),
                List.of()
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-contributor-event-type-highlights"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowDays").value(7))
            .andExpect(jsonPath("$.limit").value(5))
            .andExpect(jsonPath("$.eventTypeLimit").value(3))
            .andExpect(jsonPath("$.contributors").isEmpty());
    }

    @Test
    @DisplayName("getActivityContributorFamilyHighlights returns RM contributor family highlights payload")
    void getActivityContributorFamilyHighlightsReturnsPayload() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributorFamilyHighlights(7, 5)).thenReturn(
            new RecordsManagementService.ActivityContributorFamilyHighlightsDto(
                7,
                5,
                new RecordsManagementService.ActivityWindowRangeDto("2026-04-10", "2026-04-16"),
                new RecordsManagementService.ActivityWindowRangeDto("2026-04-03", "2026-04-09"),
                List.of(
                    new RecordsManagementService.ActivityContributorFamilyHighlightsEntryDto(
                        "admin",
                        "admin",
                        8,
                        2,
                        6,
                        LocalDateTime.of(2026, 4, 16, 10, 0),
                        List.of(
                            new RecordsManagementService.ActivityContributorFamilyReportFamilyDto(
                                "DECLARED",
                                5,
                                2,
                                3,
                                LocalDateTime.of(2026, 4, 16, 10, 0)
                            )
                        )
                    )
                )
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-contributor-family-highlights")
                .param("windowDays", "7")
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowDays").value(7))
            .andExpect(jsonPath("$.limit").value(5))
            .andExpect(jsonPath("$.currentWindow.fromDay").value("2026-04-10"))
            .andExpect(jsonPath("$.contributors[0].username").value("admin"))
            .andExpect(jsonPath("$.contributors[0].families[0].family").value("DECLARED"))
            .andExpect(jsonPath("$.contributors[0].families[0].delta").value(3));
    }

    @Test
    @DisplayName("getActivityContributorFamilyHighlights uses defaults when no params")
    void getActivityContributorFamilyHighlightsUsesDefaults() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributorFamilyHighlights(null, null)).thenReturn(
            new RecordsManagementService.ActivityContributorFamilyHighlightsDto(
                7,
                5,
                new RecordsManagementService.ActivityWindowRangeDto("2026-04-10", "2026-04-16"),
                new RecordsManagementService.ActivityWindowRangeDto("2026-04-03", "2026-04-09"),
                List.of()
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-contributor-family-highlights"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowDays").value(7))
            .andExpect(jsonPath("$.limit").value(5))
            .andExpect(jsonPath("$.contributors").isEmpty());
    }

    @Test
    @DisplayName("getActivityContributorReport returns RM contributor report payload")
    void getActivityContributorReportReturnsPayload() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributorReport(
                LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDateTime.of(2026, 4, 15, 23, 59, 59),
                5,
                3))
            .thenReturn(
                new RecordsManagementService.ActivityContributorReportDto(
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-01T00:00", "2026-04-15T23:59:59"),
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-16T00:00", "2026-03-31T23:59:59"),
                    5,
                    3,
                    12,
                    6,
                    List.of(
                        new RecordsManagementService.ActivityContributorReportEntryDto(
                            "admin",
                            "admin",
                            8,
                            4,
                            4,
                            LocalDateTime.of(2026, 4, 15, 10, 0),
                            List.of(
                                new RecordsManagementService.ActivityContributorReportEventTypeDto(
                                    "RM_RECORD_DECLARED",
                                    "DECLARED",
                                    5,
                                    LocalDateTime.of(2026, 4, 15, 10, 0)
                                )
                            )
                        )
                    )
                )
            );

        mockMvc.perform(get("/api/v1/records/activity-contributor-report")
                .param("from", "2026-04-01T00:00:00")
                .param("to", "2026-04-15T23:59:59")
                .param("limit", "5")
                .param("eventTypeLimit", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentWindow.from").value("2026-04-01T00:00"))
            .andExpect(jsonPath("$.eventTypeLimit").value(3))
            .andExpect(jsonPath("$.contributors[0].username").value("admin"))
            .andExpect(jsonPath("$.contributors[0].currentTopEventTypes[0].eventType").value("RM_RECORD_DECLARED"));
    }

    @Test
    @DisplayName("getActivityContributorReport returns CSV export when format=csv")
    void getActivityContributorReportReturnsCsv() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributorReport(
                LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDateTime.of(2026, 4, 15, 23, 59, 59),
                5,
                3))
            .thenReturn(
                new RecordsManagementService.ActivityContributorReportDto(
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-01T00:00", "2026-04-15T23:59:59"),
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-16T00:00", "2026-03-31T23:59:59"),
                    5,
                    3,
                    12,
                    6,
                    List.of(
                        new RecordsManagementService.ActivityContributorReportEntryDto(
                            "admin",
                            "admin",
                            8,
                            4,
                            4,
                            LocalDateTime.of(2026, 4, 15, 10, 0),
                            List.of(
                                new RecordsManagementService.ActivityContributorReportEventTypeDto(
                                    "RM_RECORD_DECLARED",
                                    "DECLARED",
                                    5,
                                    LocalDateTime.of(2026, 4, 15, 10, 0)
                                )
                            )
                        )
                    )
                )
            );

        mockMvc.perform(get("/api/v1/records/activity-contributor-report")
                .param("from", "2026-04-01T00:00:00")
                .param("to", "2026-04-15T23:59:59")
                .param("limit", "5")
                .param("eventTypeLimit", "3")
                .param("format", "csv"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("username,label,currentCount")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("admin,admin,8,4,4")));
    }

    @Test
    @DisplayName("getActivityContributorReport rejects unsupported export format")
    void getActivityContributorReportRejectsUnsupportedFormat() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributorReport(null, null, null, null)).thenReturn(
            new RecordsManagementService.ActivityContributorReportDto(
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-19T00:00", "2026-04-15T23:59:59"),
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-02-19T00:00", "2026-03-18T23:59:59"),
                5,
                3,
                0,
                0,
                List.of()
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-contributor-report").param("format", "xlsx"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Unsupported format: xlsx"));
    }

    @Test
    @DisplayName("getActivityContributorEventTypeReport returns RM contributor event-type report payload")
    void getActivityContributorEventTypeReportReturnsPayload() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributorEventTypeReport(
                LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDateTime.of(2026, 4, 15, 23, 59, 59),
                5,
                3))
            .thenReturn(
                new RecordsManagementService.ActivityContributorEventTypeReportDto(
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-01T00:00", "2026-04-15T23:59:59"),
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-16T00:00", "2026-03-31T23:59:59"),
                    5,
                    3,
                    12,
                    6,
                    List.of(
                        new RecordsManagementService.ActivityContributorEventTypeReportEntryDto(
                            "admin",
                            "admin",
                            8,
                            4,
                            4,
                            LocalDateTime.of(2026, 4, 15, 10, 0),
                            List.of(
                                new RecordsManagementService.ActivityContributorEventTypeReportEventTypeDto(
                                    "RM_RECORD_DECLARED",
                                    "DECLARED",
                                    5,
                                    2,
                                    3,
                                    LocalDateTime.of(2026, 4, 15, 10, 0)
                                )
                            )
                        )
                    )
                )
            );

        mockMvc.perform(get("/api/v1/records/activity-contributor-event-type-report")
                .param("from", "2026-04-01T00:00:00")
                .param("to", "2026-04-15T23:59:59")
                .param("limit", "5")
                .param("eventTypeLimit", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentWindow.from").value("2026-04-01T00:00"))
            .andExpect(jsonPath("$.eventTypeLimit").value(3))
            .andExpect(jsonPath("$.contributors[0].username").value("admin"))
            .andExpect(jsonPath("$.contributors[0].eventTypes[0].eventType").value("RM_RECORD_DECLARED"))
            .andExpect(jsonPath("$.contributors[0].eventTypes[0].delta").value(3));
    }

    @Test
    @DisplayName("getActivityContributorEventTypeReport returns CSV export when format=csv")
    void getActivityContributorEventTypeReportReturnsCsv() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributorEventTypeReport(
                LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDateTime.of(2026, 4, 15, 23, 59, 59),
                5,
                3))
            .thenReturn(
                new RecordsManagementService.ActivityContributorEventTypeReportDto(
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-01T00:00", "2026-04-15T23:59:59"),
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-16T00:00", "2026-03-31T23:59:59"),
                    5,
                    3,
                    12,
                    6,
                    List.of(
                        new RecordsManagementService.ActivityContributorEventTypeReportEntryDto(
                            "admin",
                            "admin",
                            8,
                            4,
                            4,
                            LocalDateTime.of(2026, 4, 15, 10, 0),
                            List.of(
                                new RecordsManagementService.ActivityContributorEventTypeReportEventTypeDto(
                                    "RM_RECORD_DECLARED",
                                    "DECLARED",
                                    5,
                                    2,
                                    3,
                                    LocalDateTime.of(2026, 4, 15, 10, 0)
                                )
                            )
                        )
                    )
                )
            );

        mockMvc.perform(get("/api/v1/records/activity-contributor-event-type-report")
                .param("from", "2026-04-01T00:00:00")
                .param("to", "2026-04-15T23:59:59")
                .param("limit", "5")
                .param("eventTypeLimit", "3")
                .param("format", "csv"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("username,label,eventType,family,currentCount")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("admin,admin,RM_RECORD_DECLARED,DECLARED,5,2,3")));
    }

    @Test
    @DisplayName("getActivityContributorEventTypeReport rejects unsupported export format")
    void getActivityContributorEventTypeReportRejectsUnsupportedFormat() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributorEventTypeReport(null, null, null, null)).thenReturn(
            new RecordsManagementService.ActivityContributorEventTypeReportDto(
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-19T00:00", "2026-04-15T23:59:59"),
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-02-19T00:00", "2026-03-18T23:59:59"),
                5,
                3,
                0,
                0,
                List.of()
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-contributor-event-type-report").param("format", "xlsx"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Unsupported format: xlsx"));
    }

    @Test
    @DisplayName("getActivityContributorFamilyReport returns RM contributor family report payload")
    void getActivityContributorFamilyReportReturnsPayload() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributorFamilyReport(
                LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDateTime.of(2026, 4, 15, 23, 59, 59),
                5))
            .thenReturn(
                new RecordsManagementService.ActivityContributorFamilyReportDto(
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-01T00:00", "2026-04-15T23:59:59"),
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-16T00:00", "2026-03-31T23:59:59"),
                    5,
                    12,
                    6,
                    List.of(
                        new RecordsManagementService.ActivityContributorFamilyReportEntryDto(
                            "admin",
                            "admin",
                            8,
                            4,
                            4,
                            LocalDateTime.of(2026, 4, 15, 10, 0),
                            List.of(
                                new RecordsManagementService.ActivityContributorFamilyReportFamilyDto(
                                    "DECLARED",
                                    5,
                                    2,
                                    3,
                                    LocalDateTime.of(2026, 4, 15, 10, 0)
                                )
                            )
                        )
                    )
                )
            );

        mockMvc.perform(get("/api/v1/records/activity-contributor-family-report")
                .param("from", "2026-04-01T00:00:00")
                .param("to", "2026-04-15T23:59:59")
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentWindow.from").value("2026-04-01T00:00"))
            .andExpect(jsonPath("$.contributors[0].username").value("admin"))
            .andExpect(jsonPath("$.contributors[0].families[0].family").value("DECLARED"));
    }

    @Test
    @DisplayName("getActivityContributorFamilyReport returns CSV export when format=csv")
    void getActivityContributorFamilyReportReturnsCsv() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributorFamilyReport(
                LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDateTime.of(2026, 4, 15, 23, 59, 59),
                5))
            .thenReturn(
                new RecordsManagementService.ActivityContributorFamilyReportDto(
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-01T00:00", "2026-04-15T23:59:59"),
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-16T00:00", "2026-03-31T23:59:59"),
                    5,
                    12,
                    6,
                    List.of(
                        new RecordsManagementService.ActivityContributorFamilyReportEntryDto(
                            "admin",
                            "admin",
                            8,
                            4,
                            4,
                            LocalDateTime.of(2026, 4, 15, 10, 0),
                            List.of(
                                new RecordsManagementService.ActivityContributorFamilyReportFamilyDto(
                                    "DECLARED",
                                    5,
                                    2,
                                    3,
                                    LocalDateTime.of(2026, 4, 15, 10, 0)
                                )
                            )
                        )
                    )
                )
            );

        mockMvc.perform(get("/api/v1/records/activity-contributor-family-report")
                .param("from", "2026-04-01T00:00:00")
                .param("to", "2026-04-15T23:59:59")
                .param("limit", "5")
                .param("format", "csv"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("username,label,family,currentCount")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("admin,admin,DECLARED,5,2,3")));
    }

    @Test
    @DisplayName("getActivityContributorFamilyReport rejects unsupported export format")
    void getActivityContributorFamilyReportRejectsUnsupportedFormat() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributorFamilyReport(null, null, null)).thenReturn(
            new RecordsManagementService.ActivityContributorFamilyReportDto(
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-19T00:00", "2026-04-15T23:59:59"),
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-02-19T00:00", "2026-03-18T23:59:59"),
                5,
                0,
                0,
                List.of()
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-contributor-family-report").param("format", "xlsx"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Unsupported format: xlsx"));
    }

    @Test
    @DisplayName("getActivityContributorTrend returns RM contributor trend payload")
    void getActivityContributorTrendReturnsPayload() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributorTrend(28, 7, 5)).thenReturn(
            new RecordsManagementService.ActivityContributorTrendDto(
                28,
                7,
                5,
                List.of(
                    new RecordsManagementService.ActivityContributorTrendContributorDto(
                        "admin",
                        "admin",
                        8,
                        LocalDateTime.of(2026, 4, 15, 10, 0)
                    )
                ),
                List.of(
                    new RecordsManagementService.ActivityContributorTrendBucketDto(
                        "2026-04-09 to 2026-04-15",
                        "2026-04-09",
                        "2026-04-15",
                        4,
                        10,
                        2,
                        List.of(
                            new RecordsManagementService.ActivityContributorTrendCountDto(
                                "admin",
                                "admin",
                                8
                            )
                        )
                    )
                )
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-contributor-trend")
                .param("days", "28")
                .param("bucketDays", "7")
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.days").value(28))
            .andExpect(jsonPath("$.bucketDays").value(7))
            .andExpect(jsonPath("$.limit").value(5))
            .andExpect(jsonPath("$.trackedContributors[0].username").value("admin"))
            .andExpect(jsonPath("$.buckets[0].otherCount").value(2))
            .andExpect(jsonPath("$.buckets[0].contributorCounts[0].label").value("admin"));
    }

    @Test
    @DisplayName("getActivityContributorTrend uses defaults when no params")
    void getActivityContributorTrendUsesDefaults() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributorTrend(null, null, null)).thenReturn(
            new RecordsManagementService.ActivityContributorTrendDto(28, 7, 5, List.of(), List.of())
        );

        mockMvc.perform(get("/api/v1/records/activity-contributor-trend"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.days").value(28))
            .andExpect(jsonPath("$.bucketDays").value(7))
            .andExpect(jsonPath("$.limit").value(5))
            .andExpect(jsonPath("$.trackedContributors").isEmpty())
            .andExpect(jsonPath("$.buckets").isEmpty());
    }

    @Test
    @DisplayName("getActivityContributorEventTypeTrend returns RM contributor event-type trend payload")
    void getActivityContributorEventTypeTrendReturnsPayload() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributorEventTypeTrend(28, 7, 5, 3)).thenReturn(
            new RecordsManagementService.ActivityContributorEventTypeTrendDto(
                28,
                7,
                5,
                3,
                List.of(
                    new RecordsManagementService.ActivityContributorTrendContributorDto(
                        "admin",
                        "admin",
                        8,
                        LocalDateTime.of(2026, 4, 15, 10, 0)
                    )
                ),
                List.of(
                    new RecordsManagementService.ActivityContributorEventTypeTrendBucketDto(
                        "2026-04-09 to 2026-04-15",
                        "2026-04-09",
                        "2026-04-15",
                        4,
                        10,
                        2,
                        List.of(
                            new RecordsManagementService.ActivityContributorEventTypeTrendContributorDto(
                                "admin",
                                "admin",
                                8,
                                List.of(
                                    new RecordsManagementService.ActivityContributorEventTypeTrendCountDto(
                                        "RM_RECORD_DECLARED",
                                        "DECLARED",
                                        5
                                    )
                                )
                            )
                        )
                    )
                )
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-contributor-event-type-trend")
                .param("days", "28")
                .param("bucketDays", "7")
                .param("limit", "5")
                .param("eventTypeLimit", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.days").value(28))
            .andExpect(jsonPath("$.bucketDays").value(7))
            .andExpect(jsonPath("$.limit").value(5))
            .andExpect(jsonPath("$.eventTypeLimit").value(3))
            .andExpect(jsonPath("$.trackedContributors[0].username").value("admin"))
            .andExpect(jsonPath("$.buckets[0].otherCount").value(2))
            .andExpect(jsonPath("$.buckets[0].contributorCounts[0].eventTypes[0].eventType").value("RM_RECORD_DECLARED"))
            .andExpect(jsonPath("$.buckets[0].contributorCounts[0].eventTypes[0].count").value(5));
    }

    @Test
    @DisplayName("getActivityContributorEventTypeTrend uses defaults when no params")
    void getActivityContributorEventTypeTrendUsesDefaults() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributorEventTypeTrend(null, null, null, null)).thenReturn(
            new RecordsManagementService.ActivityContributorEventTypeTrendDto(28, 7, 5, 5, List.of(), List.of())
        );

        mockMvc.perform(get("/api/v1/records/activity-contributor-event-type-trend"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.days").value(28))
            .andExpect(jsonPath("$.bucketDays").value(7))
            .andExpect(jsonPath("$.limit").value(5))
            .andExpect(jsonPath("$.eventTypeLimit").value(5))
            .andExpect(jsonPath("$.trackedContributors").isEmpty())
            .andExpect(jsonPath("$.buckets").isEmpty());
    }

    @Test
    @DisplayName("getActivityContributorFamilyTrend returns RM contributor family trend payload")
    void getActivityContributorFamilyTrendReturnsPayload() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributorFamilyTrend(28, 7, 5)).thenReturn(
            new RecordsManagementService.ActivityContributorFamilyTrendDto(
                28,
                7,
                5,
                List.of(
                    new RecordsManagementService.ActivityContributorTrendContributorDto(
                        "admin",
                        "admin",
                        8,
                        LocalDateTime.of(2026, 4, 15, 10, 0)
                    )
                ),
                List.of(
                    new RecordsManagementService.ActivityContributorFamilyTrendBucketDto(
                        "2026-04-09 to 2026-04-15",
                        "2026-04-09",
                        "2026-04-15",
                        4,
                        10,
                        2,
                        List.of(
                            new RecordsManagementService.ActivityContributorFamilyTrendContributorDto(
                                "admin",
                                "admin",
                                8,
                                List.of(
                                    new RecordsManagementService.ActivityContributorFamilyTrendCountDto(
                                        "DECLARED",
                                        5
                                    )
                                )
                            )
                        )
                    )
                )
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-contributor-family-trend")
                .param("days", "28")
                .param("bucketDays", "7")
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.days").value(28))
            .andExpect(jsonPath("$.bucketDays").value(7))
            .andExpect(jsonPath("$.limit").value(5))
            .andExpect(jsonPath("$.trackedContributors[0].username").value("admin"))
            .andExpect(jsonPath("$.buckets[0].otherCount").value(2))
            .andExpect(jsonPath("$.buckets[0].contributorCounts[0].families[0].family").value("DECLARED"))
            .andExpect(jsonPath("$.buckets[0].contributorCounts[0].families[0].count").value(5));
    }

    @Test
    @DisplayName("getActivityContributorFamilyTrend uses defaults when no params")
    void getActivityContributorFamilyTrendUsesDefaults() throws Exception {
        Mockito.when(recordsManagementService.getActivityContributorFamilyTrend(null, null, null)).thenReturn(
            new RecordsManagementService.ActivityContributorFamilyTrendDto(28, 7, 5, List.of(), List.of())
        );

        mockMvc.perform(get("/api/v1/records/activity-contributor-family-trend"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.days").value(28))
            .andExpect(jsonPath("$.bucketDays").value(7))
            .andExpect(jsonPath("$.limit").value(5))
            .andExpect(jsonPath("$.trackedContributors").isEmpty())
            .andExpect(jsonPath("$.buckets").isEmpty());
    }

    @Test
    @DisplayName("getActivityEventTypeTrend returns RM event-type trend payload")
    void getActivityEventTypeTrendReturnsPayload() throws Exception {
        Mockito.when(recordsManagementService.getActivityEventTypeTrend(28, 7, 8)).thenReturn(
            new RecordsManagementService.ActivityEventTypeTrendDto(
                28,
                7,
                8,
                List.of(
                    new RecordsManagementService.ActivityEventTypeDto(
                        "RM_RECORD_DECLARED",
                        "DECLARED",
                        5,
                        LocalDateTime.of(2026, 4, 15, 10, 0)
                    ),
                    new RecordsManagementService.ActivityEventTypeDto(
                        "RM_FILE_PLAN_MOVED",
                        "GOVERNANCE_CHANGE",
                        4,
                        LocalDateTime.of(2026, 4, 14, 8, 0)
                    )
                ),
                List.of(
                    new RecordsManagementService.ActivityEventTypeTrendBucketDto(
                        "2026-04-09 to 2026-04-15",
                        "2026-04-09",
                        "2026-04-15",
                        4,
                        10,
                        1,
                        List.of(
                            new RecordsManagementService.ActivityEventTypeTrendCountDto(
                                "RM_FILE_PLAN_MOVED",
                                "GOVERNANCE_CHANGE",
                                4
                            ),
                            new RecordsManagementService.ActivityEventTypeTrendCountDto(
                                "RM_RECORD_DECLARED",
                                "DECLARED",
                                5
                            )
                        )
                    )
                )
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-event-type-trend")
                .param("days", "28")
                .param("bucketDays", "7")
                .param("limit", "8"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.days").value(28))
            .andExpect(jsonPath("$.bucketDays").value(7))
            .andExpect(jsonPath("$.limit").value(8))
            .andExpect(jsonPath("$.trackedEventTypes[0].eventType").value("RM_RECORD_DECLARED"))
            .andExpect(jsonPath("$.buckets[0].otherCount").value(1))
            .andExpect(jsonPath("$.buckets[0].eventTypeCounts[0].eventType").value("RM_FILE_PLAN_MOVED"));
    }

    @Test
    @DisplayName("getActivityEventTypeTrend uses defaults when no params")
    void getActivityEventTypeTrendUsesDefaults() throws Exception {
        Mockito.when(recordsManagementService.getActivityEventTypeTrend(null, null, null)).thenReturn(
            new RecordsManagementService.ActivityEventTypeTrendDto(28, 7, 8, List.of(), List.of())
        );

        mockMvc.perform(get("/api/v1/records/activity-event-type-trend"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.days").value(28))
            .andExpect(jsonPath("$.bucketDays").value(7))
            .andExpect(jsonPath("$.limit").value(8))
            .andExpect(jsonPath("$.trackedEventTypes").isEmpty())
            .andExpect(jsonPath("$.buckets").isEmpty());
    }

    @Test
    @DisplayName("getActivityEventTypeReport returns RM event-type report payload")
    void getActivityEventTypeReportReturnsPayload() throws Exception {
        Mockito.when(recordsManagementService.getActivityEventTypeReport(
                LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDateTime.of(2026, 4, 15, 23, 59, 59),
                8))
            .thenReturn(
                new RecordsManagementService.ActivityEventTypeReportDto(
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-01T00:00", "2026-04-15T23:59:59"),
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-16T00:00", "2026-03-31T23:59:59"),
                    8,
                    9,
                    4,
                    List.of(
                        new RecordsManagementService.ActivityEventTypeReportEntryDto(
                            "RM_RECORD_DECLARED",
                            "DECLARED",
                            5,
                            2,
                            3,
                            LocalDateTime.of(2026, 4, 15, 10, 0)
                        ),
                        new RecordsManagementService.ActivityEventTypeReportEntryDto(
                            "RM_FILE_PLAN_MOVED",
                            "GOVERNANCE_CHANGE",
                            4,
                            2,
                            2,
                            LocalDateTime.of(2026, 4, 14, 8, 0)
                        )
                    )
                )
            );

        mockMvc.perform(get("/api/v1/records/activity-event-type-report")
                .param("from", "2026-04-01T00:00:00")
                .param("to", "2026-04-15T23:59:59")
                .param("limit", "8"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentWindow.from").value("2026-04-01T00:00"))
            .andExpect(jsonPath("$.limit").value(8))
            .andExpect(jsonPath("$.eventTypes[0].eventType").value("RM_RECORD_DECLARED"))
            .andExpect(jsonPath("$.eventTypes[1].family").value("GOVERNANCE_CHANGE"));
    }

    @Test
    @DisplayName("getActivityEventTypeReport returns CSV export when format=csv")
    void getActivityEventTypeReportReturnsCsv() throws Exception {
        Mockito.when(recordsManagementService.getActivityEventTypeReport(
                LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDateTime.of(2026, 4, 15, 23, 59, 59),
                8))
            .thenReturn(
                new RecordsManagementService.ActivityEventTypeReportDto(
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-01T00:00", "2026-04-15T23:59:59"),
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-16T00:00", "2026-03-31T23:59:59"),
                    8,
                    9,
                    4,
                    List.of(
                        new RecordsManagementService.ActivityEventTypeReportEntryDto(
                            "RM_RECORD_DECLARED",
                            "DECLARED",
                            5,
                            2,
                            3,
                            LocalDateTime.of(2026, 4, 15, 10, 0)
                        )
                    )
                )
            );

        mockMvc.perform(get("/api/v1/records/activity-event-type-report")
                .param("from", "2026-04-01T00:00:00")
                .param("to", "2026-04-15T23:59:59")
                .param("limit", "8")
                .param("format", "csv"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("eventType,family,currentCount")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("RM_RECORD_DECLARED,DECLARED,5,2,3")));
    }

    @Test
    @DisplayName("getActivityEventTypeReport rejects unsupported export format")
    void getActivityEventTypeReportRejectsUnsupportedFormat() throws Exception {
        Mockito.when(recordsManagementService.getActivityEventTypeReport(null, null, null)).thenReturn(
            new RecordsManagementService.ActivityEventTypeReportDto(
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-19T00:00", "2026-04-15T23:59:59"),
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-02-19T00:00", "2026-03-18T23:59:59"),
                8,
                0,
                0,
                List.of()
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-event-type-report").param("format", "xlsx"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Unsupported format: xlsx"));
    }

    @Test
    @DisplayName("getActivityEventTypes returns top RM activity event types")
    void getActivityEventTypesReturnsPayload() throws Exception {
        Mockito.when(recordsManagementService.getActivityEventTypes(28, 8)).thenReturn(
            new RecordsManagementService.ActivityEventTypesDto(
                28,
                8,
                List.of(
                    new RecordsManagementService.ActivityEventTypeDto(
                        "RM_RECORD_DECLARED",
                        "DECLARED",
                        5,
                        LocalDateTime.of(2026, 4, 15, 10, 0)
                    ),
                    new RecordsManagementService.ActivityEventTypeDto(
                        "RM_RECORD_UNDECLARE_BLOCKED",
                        "OTHER",
                        2,
                        LocalDateTime.of(2026, 4, 14, 8, 0)
                    )
                )
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-event-types")
                .param("days", "28")
                .param("limit", "8"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.days").value(28))
            .andExpect(jsonPath("$.limit").value(8))
            .andExpect(jsonPath("$.eventTypes[0].eventType").value("RM_RECORD_DECLARED"))
            .andExpect(jsonPath("$.eventTypes[0].family").value("DECLARED"))
            .andExpect(jsonPath("$.eventTypes[0].count").value(5))
            .andExpect(jsonPath("$.eventTypes[1].family").value("OTHER"));
    }

    @Test
    @DisplayName("getActivityEventTypes uses defaults when no params")
    void getActivityEventTypesUsesDefaults() throws Exception {
        Mockito.when(recordsManagementService.getActivityEventTypes(null, null)).thenReturn(
            new RecordsManagementService.ActivityEventTypesDto(28, 8, List.of())
        );

        mockMvc.perform(get("/api/v1/records/activity-event-types"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.days").value(28))
            .andExpect(jsonPath("$.limit").value(8))
            .andExpect(jsonPath("$.eventTypes").isEmpty());
    }

    @Test
    @DisplayName("getActivityFamilies returns RM activity family mix payload")
    void getActivityFamiliesReturnsPayload() throws Exception {
        Mockito.when(recordsManagementService.getActivityFamilies(28)).thenReturn(
            new RecordsManagementService.ActivityFamiliesDto(
                28,
                11,
                List.of(
                    new RecordsManagementService.ActivityFamilyDto(
                        "DECLARED",
                        5,
                        LocalDateTime.of(2026, 4, 15, 10, 0)
                    ),
                    new RecordsManagementService.ActivityFamilyDto(
                        "OTHER",
                        2,
                        LocalDateTime.of(2026, 4, 14, 8, 0)
                    )
                )
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-families")
                .param("days", "28"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.days").value(28))
            .andExpect(jsonPath("$.totalCount").value(11))
            .andExpect(jsonPath("$.families[0].family").value("DECLARED"))
            .andExpect(jsonPath("$.families[0].count").value(5))
            .andExpect(jsonPath("$.families[1].family").value("OTHER"));
    }

    @Test
    @DisplayName("getActivityFamilies uses defaults when no params")
    void getActivityFamiliesUsesDefaults() throws Exception {
        Mockito.when(recordsManagementService.getActivityFamilies(null)).thenReturn(
            new RecordsManagementService.ActivityFamiliesDto(28, 0, List.of())
        );

        mockMvc.perform(get("/api/v1/records/activity-families"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.days").value(28))
            .andExpect(jsonPath("$.totalCount").value(0))
            .andExpect(jsonPath("$.families").isEmpty());
    }

    @Test
    @DisplayName("getActivityFamilyHighlights returns family comparison payload")
    void getActivityFamilyHighlightsReturnsPayload() throws Exception {
        Mockito.when(recordsManagementService.getActivityFamilyHighlights(7)).thenReturn(
            new RecordsManagementService.ActivityFamilyHighlightsDto(
                7,
                new RecordsManagementService.ActivityWindowRangeDto("2026-04-09", "2026-04-15"),
                new RecordsManagementService.ActivityWindowRangeDto("2026-04-02", "2026-04-08"),
                List.of(
                    new RecordsManagementService.ActivityFamilyHighlightDto(
                        "DECLARED",
                        5,
                        2,
                        3,
                        LocalDateTime.of(2026, 4, 15, 10, 0)
                    ),
                    new RecordsManagementService.ActivityFamilyHighlightDto(
                        "OTHER",
                        1,
                        3,
                        -2,
                        LocalDateTime.of(2026, 4, 14, 8, 0)
                    )
                )
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-family-highlights").param("windowDays", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowDays").value(7))
            .andExpect(jsonPath("$.currentWindow.fromDay").value("2026-04-09"))
            .andExpect(jsonPath("$.previousWindow.toDay").value("2026-04-08"))
            .andExpect(jsonPath("$.families[0].family").value("DECLARED"))
            .andExpect(jsonPath("$.families[0].delta").value(3))
            .andExpect(jsonPath("$.families[1].family").value("OTHER"))
            .andExpect(jsonPath("$.families[1].previousCount").value(3));
    }

    @Test
    @DisplayName("getActivityFamilyHighlights uses defaults when no params")
    void getActivityFamilyHighlightsUsesDefaults() throws Exception {
        Mockito.when(recordsManagementService.getActivityFamilyHighlights(null)).thenReturn(
            new RecordsManagementService.ActivityFamilyHighlightsDto(
                7,
                new RecordsManagementService.ActivityWindowRangeDto("2026-04-09", "2026-04-15"),
                new RecordsManagementService.ActivityWindowRangeDto("2026-04-02", "2026-04-08"),
                List.of()
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-family-highlights"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowDays").value(7))
            .andExpect(jsonPath("$.families").isEmpty());
    }

    @Test
    @DisplayName("getActivityFamilyTrend returns bucketed RM family trend payload")
    void getActivityFamilyTrendReturnsPayload() throws Exception {
        Mockito.when(recordsManagementService.getActivityFamilyTrend(28, 7)).thenReturn(
            new RecordsManagementService.ActivityFamilyTrendDto(
                28,
                7,
                List.of(
                    new RecordsManagementService.ActivityFamilyTrendBucketDto(
                        "2026-04-02 to 2026-04-08",
                        "2026-04-02",
                        "2026-04-08",
                        3,
                        6,
                        List.of(
                            new RecordsManagementService.ActivityFamilyTrendFamilyCountDto("DECLARED", 3),
                            new RecordsManagementService.ActivityFamilyTrendFamilyCountDto("OTHER", 2)
                        )
                    ),
                    new RecordsManagementService.ActivityFamilyTrendBucketDto(
                        "2026-04-09 to 2026-04-15",
                        "2026-04-09",
                        "2026-04-15",
                        4,
                        9,
                        List.of(
                            new RecordsManagementService.ActivityFamilyTrendFamilyCountDto("GOVERNANCE_CHANGE", 4),
                            new RecordsManagementService.ActivityFamilyTrendFamilyCountDto("DECLARED", 3)
                        )
                    )
                )
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-family-trend")
                .param("days", "28")
                .param("bucketDays", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.days").value(28))
            .andExpect(jsonPath("$.bucketDays").value(7))
            .andExpect(jsonPath("$.buckets[0].label").value("2026-04-02 to 2026-04-08"))
            .andExpect(jsonPath("$.buckets[0].familyCounts[0].family").value("DECLARED"))
            .andExpect(jsonPath("$.buckets[1].familyCounts[0].family").value("GOVERNANCE_CHANGE"))
            .andExpect(jsonPath("$.buckets[1].totalCount").value(9));
    }

    @Test
    @DisplayName("getActivityFamilyTrend uses defaults when no params")
    void getActivityFamilyTrendUsesDefaults() throws Exception {
        Mockito.when(recordsManagementService.getActivityFamilyTrend(null, null)).thenReturn(
            new RecordsManagementService.ActivityFamilyTrendDto(28, 7, List.of())
        );

        mockMvc.perform(get("/api/v1/records/activity-family-trend"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.days").value(28))
            .andExpect(jsonPath("$.bucketDays").value(7))
            .andExpect(jsonPath("$.buckets").isEmpty());
    }

    @Test
    @DisplayName("getActivityFamilyReport returns RM family report payload")
    void getActivityFamilyReportReturnsPayload() throws Exception {
        Mockito.when(recordsManagementService.getActivityFamilyReport(
                LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDateTime.of(2026, 4, 15, 23, 59, 59),
                3,
                2))
            .thenReturn(
                new RecordsManagementService.ActivityFamilyReportDto(
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-01T00:00", "2026-04-15T23:59:59"),
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-16T00:00", "2026-03-31T23:59:59"),
                    3,
                    2,
                    8,
                    5,
                    List.of(
                        new RecordsManagementService.ActivityFamilyReportEntryDto(
                            "DECLARED",
                            5,
                            2,
                            3,
                            LocalDateTime.of(2026, 4, 15, 10, 0),
                            List.of(
                                new RecordsManagementService.ActivityFamilyReportEventTypeDto(
                                    "RM_RECORD_DECLARED",
                                    5,
                                    LocalDateTime.of(2026, 4, 15, 10, 0)
                                )
                            ),
                            List.of(
                                new RecordsManagementService.ActivityFamilyReportContributorDto(
                                    "admin",
                                    "admin",
                                    4,
                                    LocalDateTime.of(2026, 4, 15, 10, 0)
                                )
                            )
                        )
                    )
                )
            );

        mockMvc.perform(get("/api/v1/records/activity-family-report")
                .param("from", "2026-04-01T00:00:00")
                .param("to", "2026-04-15T23:59:59")
                .param("eventTypeLimit", "3")
                .param("contributorLimit", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentWindow.from").value("2026-04-01T00:00"))
            .andExpect(jsonPath("$.previousWindow.to").value("2026-03-31T23:59:59"))
            .andExpect(jsonPath("$.eventTypeLimit").value(3))
            .andExpect(jsonPath("$.families[0].family").value("DECLARED"))
            .andExpect(jsonPath("$.families[0].topEventTypes[0].eventType").value("RM_RECORD_DECLARED"))
            .andExpect(jsonPath("$.families[0].topContributors[0].label").value("admin"));
    }

    @Test
    @DisplayName("getActivityFamilyReport returns CSV export when format=csv")
    void getActivityFamilyReportReturnsCsv() throws Exception {
        Mockito.when(recordsManagementService.getActivityFamilyReport(
                LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDateTime.of(2026, 4, 15, 23, 59, 59),
                3,
                2))
            .thenReturn(
                new RecordsManagementService.ActivityFamilyReportDto(
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-01T00:00", "2026-04-15T23:59:59"),
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-16T00:00", "2026-03-31T23:59:59"),
                    3,
                    2,
                    8,
                    5,
                    List.of(
                        new RecordsManagementService.ActivityFamilyReportEntryDto(
                            "DECLARED",
                            5,
                            2,
                            3,
                            LocalDateTime.of(2026, 4, 15, 10, 0),
                            List.of(
                                new RecordsManagementService.ActivityFamilyReportEventTypeDto(
                                    "RM_RECORD_DECLARED",
                                    5,
                                    LocalDateTime.of(2026, 4, 15, 10, 0)
                                )
                            ),
                            List.of(
                                new RecordsManagementService.ActivityFamilyReportContributorDto(
                                    "admin",
                                    "admin",
                                    4,
                                    LocalDateTime.of(2026, 4, 15, 10, 0)
                                )
                            )
                        )
                    )
                )
            );

        mockMvc.perform(get("/api/v1/records/activity-family-report")
                .param("from", "2026-04-01T00:00:00")
                .param("to", "2026-04-15T23:59:59")
                .param("eventTypeLimit", "3")
                .param("contributorLimit", "2")
                .param("format", "csv"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("family,currentCount,previousCount")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("DECLARED,5,2,3")));
    }

    @Test
    @DisplayName("getActivityFamilyReport rejects unsupported export format")
    void getActivityFamilyReportRejectsUnsupportedFormat() throws Exception {
        Mockito.when(recordsManagementService.getActivityFamilyReport(null, null, null, null)).thenReturn(
            new RecordsManagementService.ActivityFamilyReportDto(
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-19T00:00", "2026-04-15T23:59:59"),
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-02-19T00:00", "2026-03-18T23:59:59"),
                3,
                3,
                0,
                0,
                List.of()
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-family-report").param("format", "xlsx"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Unsupported format: xlsx"));
    }

    @Test
    @DisplayName("executeReportPreset returns JSON for a saved family report preset")
    void executeReportPresetReturnsJsonForFamilyReport() throws Exception {
        UUID presetId = UUID.randomUUID();
        Mockito.when(rmReportPresetService.getOwned(presetId)).thenReturn(
            RmReportPreset.builder()
                .owner("admin")
                .name("Quarterly family report")
                .kind(RmReportPreset.Kind.ACTIVITY_FAMILY_REPORT)
                .params(Map.of(
                    "from", "2026-04-01T00:00:00",
                    "to", "2026-04-15T23:59:59",
                    "eventTypeLimit", 3,
                    "contributorLimit", 2
                ))
                .build()
        );
        Mockito.when(recordsManagementService.getActivityFamilyReport(
                LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDateTime.of(2026, 4, 15, 23, 59, 59),
                3,
                2))
            .thenReturn(
                new RecordsManagementService.ActivityFamilyReportDto(
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-01T00:00", "2026-04-15T23:59:59"),
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-16T00:00", "2026-03-31T23:59:59"),
                    3,
                    2,
                    8,
                    5,
                    List.of(
                        new RecordsManagementService.ActivityFamilyReportEntryDto(
                            "DECLARED",
                            5,
                            2,
                            3,
                            LocalDateTime.of(2026, 4, 15, 10, 0),
                            List.of(),
                            List.of()
                        )
                    )
                )
            );

        mockMvc.perform(post("/api/v1/records/report-presets/{presetId}/execute", presetId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentWindow.from").value("2026-04-01T00:00"))
            .andExpect(jsonPath("$.families[0].family").value("DECLARED"))
            .andExpect(jsonPath("$.families[0].delta").value(3));
    }

    @Test
    @DisplayName("executeReportPreset returns CSV for a saved contributor event-type report preset")
    void executeReportPresetReturnsCsvForContributorEventTypeReport() throws Exception {
        UUID presetId = UUID.randomUUID();
        Mockito.when(rmReportPresetService.getOwned(presetId)).thenReturn(
            RmReportPreset.builder()
                .owner("admin")
                .name("Contributor event types")
                .kind(RmReportPreset.Kind.ACTIVITY_CONTRIBUTOR_EVENT_TYPE_REPORT)
                .params(Map.of(
                    "from", "2026-04-01T00:00:00",
                    "to", "2026-04-15T23:59:59",
                    "limit", 5,
                    "eventTypeLimit", 3
                ))
                .build()
        );
        Mockito.when(recordsManagementService.getActivityContributorEventTypeReport(
                LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDateTime.of(2026, 4, 15, 23, 59, 59),
                5,
                3))
            .thenReturn(
                new RecordsManagementService.ActivityContributorEventTypeReportDto(
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-01T00:00", "2026-04-15T23:59:59"),
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-16T00:00", "2026-03-31T23:59:59"),
                    5,
                    3,
                    12,
                    6,
                    List.of(
                        new RecordsManagementService.ActivityContributorEventTypeReportEntryDto(
                            "admin",
                            "admin",
                            8,
                            4,
                            4,
                            LocalDateTime.of(2026, 4, 15, 10, 0),
                            List.of(
                                new RecordsManagementService.ActivityContributorEventTypeReportEventTypeDto(
                                    "RM_RECORD_DECLARED",
                                    "DECLARED",
                                    5,
                                    2,
                                    3,
                                    LocalDateTime.of(2026, 4, 15, 10, 0)
                                )
                            )
                        )
                    )
                )
            );

        mockMvc.perform(post("/api/v1/records/report-presets/{presetId}/execute", presetId)
                .param("format", "csv"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("username,label,eventType,family")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("admin,admin,RM_RECORD_DECLARED,DECLARED")));
    }

    @Test
    @DisplayName("executeReportPreset supports CSV for summary-only preset kinds")
    void executeReportPresetSupportsCsvForSummaryOnlyPresetKind() throws Exception {
        UUID presetId = UUID.randomUUID();
        Mockito.when(rmReportPresetService.getOwned(presetId)).thenReturn(
            RmReportPreset.builder()
                .owner("admin")
                .name("Family highlights")
                .kind(RmReportPreset.Kind.ACTIVITY_FAMILY_HIGHLIGHTS)
                .params(Map.of("windowDays", 7))
                .build()
        );
        Mockito.when(recordsManagementService.getActivityFamilyReport(
                Mockito.any(LocalDateTime.class),
                Mockito.any(LocalDateTime.class),
                Mockito.isNull(),
                Mockito.isNull()))
            .thenReturn(new RecordsManagementService.ActivityFamilyReportDto(
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-09T00:00:00", "2026-04-15T23:59:59"),
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-02T00:00:00", "2026-04-08T23:59:59"),
                5,
                5,
                7,
                4,
                List.of(
                    new RecordsManagementService.ActivityFamilyReportEntryDto(
                        "DECLARED",
                        5,
                        2,
                        3,
                        LocalDateTime.of(2026, 4, 15, 10, 0),
                        List.of(),
                        List.of()
                    )
                )
            ));

        mockMvc.perform(post("/api/v1/records/report-presets/{presetId}/execute", presetId)
                .param("format", "csv"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("family,currentCount,previousCount")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("DECLARED,5,2,3")));
    }

    @Test
    @DisplayName("executeReportPreset rejects missing required datetime params")
    void executeReportPresetRejectsMissingRequiredDatetimeParams() throws Exception {
        UUID presetId = UUID.randomUUID();
        Mockito.when(rmReportPresetService.getOwned(presetId)).thenReturn(
            RmReportPreset.builder()
                .owner("admin")
                .name("Broken family report")
                .kind(RmReportPreset.Kind.ACTIVITY_FAMILY_REPORT)
                .params(Map.of("from", "2026-04-01T00:00:00"))
                .build()
        );

        mockMvc.perform(post("/api/v1/records/report-presets/{presetId}/execute", presetId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                "Preset param \"to\" is required for ACTIVITY_FAMILY_REPORT"
            ));
    }

    @Test
    @DisplayName("getActivityBreakdown returns RM activity bucket payload")
    void getActivityBreakdownReturnsPayload() throws Exception {
        Mockito.when(recordsManagementService.getActivityBreakdown(28, 7)).thenReturn(
            new RecordsManagementService.RecordsActivityBreakdownDto(
                28,
                7,
                List.of(
                    new RecordsManagementService.RecordsActivityBucketDto(
                        "2026-03-18 to 2026-03-24",
                        "2026-03-18",
                        "2026-03-24",
                        3,
                        2,
                        0,
                        1,
                        1,
                        4
                    ),
                    new RecordsManagementService.RecordsActivityBucketDto(
                        "2026-04-08 to 2026-04-14",
                        "2026-04-08",
                        "2026-04-14",
                        5,
                        3,
                        1,
                        4,
                        2,
                        10
                    )
                )
            )
        );

        mockMvc.perform(get("/api/v1/records/activity-breakdown")
                .param("days", "28")
                .param("bucketDays", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.days").value(28))
            .andExpect(jsonPath("$.bucketDays").value(7))
            .andExpect(jsonPath("$.buckets[1].label").value("2026-04-08 to 2026-04-14"))
            .andExpect(jsonPath("$.buckets[1].governanceChangeCount").value(2))
            .andExpect(jsonPath("$.buckets[1].totalCount").value(10));
    }

    @Test
    @DisplayName("declareRecord returns declaration payload")
    void declareRecordReturnsPayload() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Mockito.when(recordsManagementService.declareRecord(Mockito.eq(nodeId), Mockito.any())).thenReturn(
            new RecordsManagementService.RecordDeclarationDto(
                nodeId,
                "report.pdf",
                "/Sites/Finance/report.pdf",
                "1.2",
                "1.2",
                "admin",
                LocalDateTime.of(2026, 4, 14, 18, 30),
                "Quarter close record",
                UUID.randomUUID(),
                "Contracts",
                "/Records Management/Contracts"
            )
        );

        mockMvc.perform(put("/api/v1/nodes/{nodeId}/record", nodeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "comment": "Quarter close record"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodeId").value(nodeId.toString()))
            .andExpect(jsonPath("$.declaredVersionLabel").value("1.2"))
            .andExpect(jsonPath("$.declarationComment").value("Quarter close record"));
    }

    @Test
    @DisplayName("declareRecordsBulk returns a wrapper with rows for DECLARED + SKIPPED_ALREADY_DECLARED + FAILED outcomes")
    void declareRecordsBulkReturnsMixedRowsPayload() throws Exception {
        UUID declared = UUID.randomUUID();
        UUID skipped = UUID.randomUUID();
        UUID failed = UUID.randomUUID();
        RecordsManagementService.RecordDeclarationDto declaredDto = new RecordsManagementService.RecordDeclarationDto(
            declared, "fresh.pdf", "/Sites/Finance/fresh.pdf",
            "1.0", "1.0", "admin", LocalDateTime.of(2026, 5, 24, 10, 0),
            "batch", null, null, null
        );
        RecordsManagementService.RecordDeclarationDto skippedDto = new RecordsManagementService.RecordDeclarationDto(
            skipped, "older.pdf", "/Sites/Finance/older.pdf",
            "2.1", "1.0", "older-admin", LocalDateTime.of(2026, 1, 1, 0, 0),
            "pre-existing", null, null, null
        );
        RecordsManagementService.BulkDeclareResponse response = new RecordsManagementService.BulkDeclareResponse(
            new RecordsManagementService.BulkDeclareResults(List.of(
                RecordsManagementService.BulkDeclareResult.declared(declared, declaredDto),
                RecordsManagementService.BulkDeclareResult.skippedAlreadyDeclared(skipped, skippedDto),
                RecordsManagementService.BulkDeclareResult.failed(
                    failed,
                    RecordsManagementService.BulkDeclareErrorCategory.NODE_NOT_FOUND,
                    "The target node was not found."
                )
            ))
        );
        Mockito.when(recordsManagementService.declareRecordsBulk(Mockito.any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/nodes/bulk-declare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "nodeIds": ["%s", "%s", "%s"],
                      "categoryId": null,
                      "comment": "batch"
                    }
                    """.formatted(declared, skipped, failed)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bulkDeclareResults.rows.length()").value(3))
            .andExpect(jsonPath("$.bulkDeclareResults.rows[0].status").value("DECLARED"))
            .andExpect(jsonPath("$.bulkDeclareResults.rows[0].declaration.nodeId").value(declared.toString()))
            .andExpect(jsonPath("$.bulkDeclareResults.rows[0].errorCategory").doesNotExist())
            .andExpect(jsonPath("$.bulkDeclareResults.rows[1].status").value("SKIPPED_ALREADY_DECLARED"))
            .andExpect(jsonPath("$.bulkDeclareResults.rows[1].declaration.nodeId").value(skipped.toString()))
            .andExpect(jsonPath("$.bulkDeclareResults.rows[1].errorCategory").doesNotExist())
            .andExpect(jsonPath("$.bulkDeclareResults.rows[2].status").value("FAILED"))
            .andExpect(jsonPath("$.bulkDeclareResults.rows[2].errorCategory").value("NODE_NOT_FOUND"))
            .andExpect(jsonPath("$.bulkDeclareResults.rows[2].declaration").doesNotExist());
    }

    @Test
    @DisplayName("declareRecordsBulk returns 400 when nodeIds is an empty array")
    void declareRecordsBulkEmptyArrayReturns400() throws Exception {
        Mockito.when(recordsManagementService.declareRecordsBulk(Mockito.any()))
            .thenThrow(new IllegalArgumentException("nodeIds must contain at least one entry"));

        mockMvc.perform(post("/api/v1/nodes/bulk-declare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "nodeIds": []
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("declareRecordsBulk returns 400 when nodeIds contains only null entries (v3.1 post-dedupe guard)")
    void declareRecordsBulkNullOnlyArrayReturns400() throws Exception {
        Mockito.when(recordsManagementService.declareRecordsBulk(Mockito.any()))
            .thenThrow(new IllegalArgumentException("nodeIds must contain at least one non-null entry"));

        mockMvc.perform(post("/api/v1/nodes/bulk-declare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "nodeIds": [null, null]
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("declareRecordsBulk returns 200 when some rows fail (partial failure is not an HTTP error)")
    void declareRecordsBulkPartialFailureReturns200() throws Exception {
        UUID ok = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        RecordsManagementService.RecordDeclarationDto okDto = new RecordsManagementService.RecordDeclarationDto(
            ok, "ok.pdf", "/Sites/Finance/ok.pdf",
            "1.0", "1.0", "admin", LocalDateTime.of(2026, 5, 24, 10, 0),
            null, null, null, null
        );
        RecordsManagementService.BulkDeclareResponse response = new RecordsManagementService.BulkDeclareResponse(
            new RecordsManagementService.BulkDeclareResults(List.of(
                RecordsManagementService.BulkDeclareResult.declared(ok, okDto),
                RecordsManagementService.BulkDeclareResult.failed(
                    missing,
                    RecordsManagementService.BulkDeclareErrorCategory.NODE_NOT_FOUND,
                    "The target node was not found."
                )
            ))
        );
        Mockito.when(recordsManagementService.declareRecordsBulk(Mockito.any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/nodes/bulk-declare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "nodeIds": ["%s", "%s"]
                    }
                    """.formatted(ok, missing)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bulkDeclareResults.rows.length()").value(2))
            .andExpect(jsonPath("$.bulkDeclareResults.rows[0].status").value("DECLARED"))
            .andExpect(jsonPath("$.bulkDeclareResults.rows[1].status").value("FAILED"));
    }

    @Test
    @DisplayName("undeclareRecord returns no content")
    void undeclareRecordReturnsNoContent() throws Exception {
        UUID nodeId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/record/undeclare", nodeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "Administrative correction"
                    }
                    """))
            .andExpect(status().isNoContent());

        Mockito.verify(recordsManagementService).undeclareRecord(Mockito.eq(nodeId), Mockito.any());
    }

    @Test
    @DisplayName("createFilePlan returns file plan payload")
    void createFilePlanReturnsPayload() throws Exception {
        UUID folderId = UUID.randomUUID();
        Mockito.when(recordsManagementService.createFilePlan(Mockito.any())).thenReturn(
            new RecordsManagementService.FilePlanDto(
                folderId,
                "Corporate File Plan",
                "RM root",
                "/Corporate File Plan",
                null
            )
        );

        mockMvc.perform(post("/api/v1/records/file-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Corporate File Plan",
                      "description": "RM root"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.folderId").value(folderId.toString()))
            .andExpect(jsonPath("$.name").value("Corporate File Plan"));
    }

    @Test
    @DisplayName("updateFilePlan returns updated file plan payload")
    void updateFilePlanReturnsPayload() throws Exception {
        UUID folderId = UUID.randomUUID();
        Mockito.when(recordsManagementService.updateFilePlan(Mockito.eq(folderId), Mockito.any())).thenReturn(
            new RecordsManagementService.FilePlanDto(
                folderId,
                "Corporate File Plan",
                "Updated description",
                "/Corporate File Plan",
                null
            )
        );

        mockMvc.perform(put("/api/v1/records/file-plans/{folderId}", folderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "description": "Updated description"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.folderId").value(folderId.toString()))
            .andExpect(jsonPath("$.description").value("Updated description"));
    }

    @Test
    @DisplayName("renameFilePlan returns renamed file plan payload")
    void renameFilePlanReturnsPayload() throws Exception {
        UUID folderId = UUID.randomUUID();
        Mockito.when(recordsManagementService.renameFilePlan(Mockito.eq(folderId), Mockito.any())).thenReturn(
            new RecordsManagementService.FilePlanDto(
                folderId,
                "HR File Plan",
                "RM root",
                "/HR File Plan",
                null
            )
        );

        mockMvc.perform(put("/api/v1/records/file-plans/{folderId}/rename", folderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "HR File Plan"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.folderId").value(folderId.toString()))
            .andExpect(jsonPath("$.name").value("HR File Plan"))
            .andExpect(jsonPath("$.path").value("/HR File Plan"));
    }

    @Test
    @DisplayName("moveFilePlan returns moved file plan payload")
    void moveFilePlanReturnsPayload() throws Exception {
        UUID folderId = UUID.randomUUID();
        UUID targetParentId = UUID.randomUUID();
        Mockito.when(recordsManagementService.moveFilePlan(Mockito.eq(folderId), Mockito.any())).thenReturn(
            new RecordsManagementService.FilePlanDto(
                folderId,
                "HR File Plan",
                "RM root",
                "/Company Home/HR File Plan",
                targetParentId
            )
        );

        mockMvc.perform(put("/api/v1/records/file-plans/{folderId}/move", folderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetParentId": "%s"
                    }
                    """.formatted(targetParentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.folderId").value(folderId.toString()))
            .andExpect(jsonPath("$.parentId").value(targetParentId.toString()))
            .andExpect(jsonPath("$.path").value("/Company Home/HR File Plan"));
    }

    @Test
    @DisplayName("deleteFilePlan returns no content")
    void deleteFilePlanReturnsNoContent() throws Exception {
        UUID folderId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/records/file-plans/{folderId}", folderId))
            .andExpect(status().isNoContent());

        Mockito.verify(recordsManagementService).deleteFilePlan(folderId);
    }

    @Test
    @DisplayName("assignRecordCategory returns updated declaration payload")
    void assignRecordCategoryReturnsPayload() throws Exception {
        UUID nodeId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Mockito.when(recordsManagementService.assignRecordCategory(nodeId, categoryId)).thenReturn(
            new RecordsManagementService.RecordDeclarationDto(
                nodeId,
                "report.pdf",
                "/Sites/Finance/report.pdf",
                "1.2",
                "1.2",
                "admin",
                LocalDateTime.of(2026, 4, 14, 18, 30),
                "Quarter close record",
                categoryId,
                "Contracts",
                "/Records Management/Contracts"
            )
        );

        mockMvc.perform(put("/api/v1/nodes/{nodeId}/record/category", nodeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "categoryId": "%s"
                    }
                    """.formatted(categoryId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recordCategoryId").value(categoryId.toString()))
            .andExpect(jsonPath("$.recordCategoryName").value("Contracts"));
    }

    @Test
    @DisplayName("updateRecordCategory returns updated record category payload")
    void updateRecordCategoryReturnsPayload() throws Exception {
        UUID categoryId = UUID.randomUUID();
        Mockito.when(recordsManagementService.updateRecordCategory(Mockito.eq(categoryId), Mockito.any())).thenReturn(
            new RecordsManagementService.RecordCategoryDto(
                categoryId,
                "Contracts",
                "Updated description",
                "/Records Management/Contracts",
                2,
                UUID.randomUUID()
            )
        );

        mockMvc.perform(put("/api/v1/records/categories/{categoryId}", categoryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "description": "Updated description"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
            .andExpect(jsonPath("$.description").value("Updated description"));
    }

    @Test
    @DisplayName("renameRecordCategory returns renamed record category payload")
    void renameRecordCategoryReturnsPayload() throws Exception {
        UUID categoryId = UUID.randomUUID();
        Mockito.when(recordsManagementService.renameRecordCategory(Mockito.eq(categoryId), Mockito.any())).thenReturn(
            new RecordsManagementService.RecordCategoryDto(
                categoryId,
                "Agreements",
                "Updated description",
                "/Records Management/Agreements",
                1,
                UUID.randomUUID()
            )
        );

        mockMvc.perform(put("/api/v1/records/categories/{categoryId}/rename", categoryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Agreements"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
            .andExpect(jsonPath("$.name").value("Agreements"))
            .andExpect(jsonPath("$.path").value("/Records Management/Agreements"));
    }

    @Test
    @DisplayName("moveRecordCategory returns moved record category payload")
    void moveRecordCategoryReturnsPayload() throws Exception {
        UUID categoryId = UUID.randomUUID();
        UUID targetParentId = UUID.randomUUID();
        Mockito.when(recordsManagementService.moveRecordCategory(Mockito.eq(categoryId), Mockito.any())).thenReturn(
            new RecordsManagementService.RecordCategoryDto(
                categoryId,
                "Contracts",
                "Updated description",
                "/Records Management/Finance/Contracts",
                2,
                targetParentId
            )
        );

        mockMvc.perform(put("/api/v1/records/categories/{categoryId}/move", categoryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetParentId": "%s"
                    }
                    """.formatted(targetParentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
            .andExpect(jsonPath("$.parentId").value(targetParentId.toString()))
            .andExpect(jsonPath("$.path").value("/Records Management/Finance/Contracts"));
    }

    @Test
    @DisplayName("deleteRecordCategory returns no content")
    void deleteRecordCategoryReturnsNoContent() throws Exception {
        UUID categoryId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/records/categories/{categoryId}", categoryId))
            .andExpect(status().isNoContent());

        Mockito.verify(recordsManagementService).deleteRecordCategory(categoryId);
    }

    @Test
    @DisplayName("getRecord returns 404 when declaration is missing")
    void getRecordReturnsNotFoundWhenMissing() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Mockito.when(recordsManagementService.getRecord(nodeId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/record", nodeId))
            .andExpect(status().isNotFound());
    }
}
