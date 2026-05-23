package com.ecm.core.controller;

import com.ecm.core.entity.ImportJob;
import com.ecm.core.entity.ReplicationJob;
import com.ecm.core.service.RecordsManagementService;
import com.ecm.core.service.RmReportPresetService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RecordsManagementControllerResponseContractTest {

    @Mock
    private RecordsManagementService recordsManagementService;

    @Mock
    private RmReportPresetService rmReportPresetService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new RecordsManagementController(recordsManagementService, rmReportPresetService)
            )
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
    }

    @Test
    @DisplayName("GET /records locks RecordDeclarationDto field set and nullable category fields")
    void listRecordsLocksDeclarationContract() throws Exception {
        UUID nodeId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(recordsManagementService.listRecords()).thenReturn(List.of(new RecordsManagementService.RecordDeclarationDto(
            nodeId,
            "board-minutes.pdf",
            "/Records/board-minutes.pdf",
            "2.0",
            "1.0",
            "records-admin",
            LocalDateTime.of(2026, 5, 22, 9, 30, 0),
            null,
            null,
            null,
            null
        )));

        MvcResult result = mockMvc.perform(get("/api/v1/records"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nodeId").value(nodeId.toString()))
            .andExpect(jsonPath("$[0].declaredAt").value("2026-05-22T09:30:00"))
            .andExpect(jsonPath("$[0].declarationComment", nullValue()))
            .andExpect(jsonPath("$[0].recordCategoryId", nullValue()))
            .andExpect(jsonPath("$[0].recordCategoryName", nullValue()))
            .andExpect(jsonPath("$[0].recordCategoryPath", nullValue()))
            .andReturn();

        JsonNode item = objectMapper.readTree(result.getResponse().getContentAsString()).get(0);
        assertEquals(recordDeclarationFieldNames(), fieldNames(item));
    }

    @Test
    @DisplayName("GET /records/summary locks summary and bucket DTO field sets")
    void getSummaryLocksSummaryContract() throws Exception {
        when(recordsManagementService.getSummary()).thenReturn(new RecordsManagementService.RecordsSummaryDto(
            7,
            2,
            3,
            1L,
            2L,
            List.of(new RecordsManagementService.SummaryBucketDto("/Records/Contracts", 4L)),
            List.of(new RecordsManagementService.SummaryBucketDto("/File Plan", 6L))
        ));

        MvcResult result = mockMvc.perform(get("/api/v1/records/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.declaredRecordCount").value(7))
            .andExpect(jsonPath("$.categoryBreakdown[0].key").value("/Records/Contracts"))
            .andExpect(jsonPath("$.filePlanBreakdown[0].count").value(6))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(recordsSummaryFieldNames(), fieldNames(root));
        assertEquals(summaryBucketFieldNames(), fieldNames(root.get("categoryBreakdown").get(0)));
        assertEquals(summaryBucketFieldNames(), fieldNames(root.get("filePlanBreakdown").get(0)));
    }

    @Test
    @DisplayName("GET /records/audit locks Page envelope and RecordAuditEntryDto field set")
    void listAuditLocksPageAndAuditEntryContract() throws Exception {
        UUID auditId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID nodeId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(recordsManagementService.listAudit(isNull(), isNull(), isNull(), isNull(), isNull(), any()))
            .thenReturn(new PageImpl<>(
                List.of(new RecordsManagementService.RecordAuditEntryDto(
                    auditId,
                    "RM_RECORD_DECLARED",
                    nodeId,
                    "board-minutes.pdf",
                    "records-admin",
                    LocalDateTime.of(2026, 5, 22, 10, 0, 0),
                    null
                )),
                PageRequest.of(0, 20),
                1
            ));

        MvcResult result = mockMvc.perform(get("/api/v1/records/audit"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].auditLogId").value(auditId.toString()))
            .andExpect(jsonPath("$.content[0].details", nullValue()))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.number").value(0))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(pageEnvelopeFieldNames(), fieldNames(root));
        assertEquals(recordAuditEntryFieldNames(), fieldNames(root.get("content").get(0)));
    }

    @Test
    @DisplayName("GET /records/operations locks operations telemetry and job DTO field sets")
    void getOperationsTelemetryLocksTelemetryContract() throws Exception {
        UUID importJobId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID transferJobId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        when(recordsManagementService.getOperationsTelemetry(3)).thenReturn(new RecordsManagementService.RecordsOperationsTelemetryDto(
            1,
            1L,
            0L,
            1,
            0L,
            1L,
            List.of(new RecordsManagementService.SummaryBucketDto("RUNNING", 1L)),
            List.of(new RecordsManagementService.SummaryBucketDto("FAILED / FAILED", 1L)),
            List.of(new RecordsManagementService.SummaryBucketDto("TARGET_FILE_PLAN", 1L)),
            List.of(new RecordsManagementService.SummaryBucketDto("SOURCE_DECLARED_RECORD", 1L)),
            List.of(new RecordsManagementService.GovernedImportJobDto(
                importJobId,
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                "File Plan",
                "/Records/File Plan",
                ImportJob.ImportJobStatus.RUNNING,
                ImportJob.ConflictPolicy.SKIP,
                10,
                4,
                1,
                0,
                null,
                List.of("TARGET_FILE_PLAN"),
                LocalDateTime.of(2026, 5, 22, 8, 0, 0),
                null,
                LocalDateTime.of(2026, 5, 22, 7, 55, 0)
            )),
            List.of(new RecordsManagementService.GovernedTransferJobDto(
                transferJobId,
                UUID.fromString("77777777-7777-7777-7777-777777777777"),
                UUID.fromString("88888888-8888-8888-8888-888888888888"),
                "source.pdf",
                "/Source/source.pdf",
                UUID.fromString("99999999-9999-9999-9999-999999999999"),
                "Target Plan",
                "/Records/Target Plan",
                ReplicationJob.ReplicationJobStatus.FAILED,
                ReplicationJob.TransportStatus.FAILED,
                "Replication failed",
                null,
                List.of("SOURCE_DECLARED_RECORD"),
                LocalDateTime.of(2026, 5, 22, 8, 30, 0),
                LocalDateTime.of(2026, 5, 22, 8, 45, 0),
                LocalDateTime.of(2026, 5, 22, 8, 15, 0)
            ))
        ));

        MvcResult result = mockMvc.perform(get("/api/v1/records/operations").param("limit", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recentImportJobs[0].lastMessage", nullValue()))
            .andExpect(jsonPath("$.recentImportJobs[0].completedAt", nullValue()))
            .andExpect(jsonPath("$.recentTransferJobs[0].transportMessage", nullValue()))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(recordsOperationsTelemetryFieldNames(), fieldNames(root));
        assertEquals(summaryBucketFieldNames(), fieldNames(root.get("importStatusBreakdown").get(0)));
        assertEquals(governedImportJobFieldNames(), fieldNames(root.get("recentImportJobs").get(0)));
        assertEquals(governedTransferJobFieldNames(), fieldNames(root.get("recentTransferJobs").get(0)));
    }

    @Test
    @DisplayName("GET /records/activity-timeline locks activity timeline and point DTOs")
    void getActivityTimelineLocksTimelineContract() throws Exception {
        when(recordsManagementService.getActivityTimeline(14)).thenReturn(new RecordsManagementService.RecordsActivityTimelineDto(
            14,
            List.of(new RecordsManagementService.RecordsActivityPointDto(
                "2026-05-22",
                2L,
                1L,
                3L,
                0L,
                6L
            ))
        ));

        MvcResult result = mockMvc.perform(get("/api/v1/records/activity-timeline").param("days", "14"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.points[0].totalCount").value(6))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(recordsActivityTimelineFieldNames(), fieldNames(root));
        assertEquals(recordsActivityPointFieldNames(), fieldNames(root.get("points").get(0)));
    }

    @Test
    @DisplayName("GET /records/activity-highlights locks nested window and peak DTO field sets")
    void getActivityHighlightsLocksNestedContracts() throws Exception {
        when(recordsManagementService.getActivityHighlights(7)).thenReturn(new RecordsManagementService.RecordsActivityHighlightsDto(
            7,
            new RecordsManagementService.RecordsActivityWindowDto("2026-05-16", "2026-05-22", 4L, 2L, 0L, 1L, 1L, 4L),
            new RecordsManagementService.RecordsActivityWindowDto("2026-05-09", "2026-05-15", 3L, 1L, 1L, 0L, 0L, 2L),
            new RecordsManagementService.RecordsActivityPeakDto("2026-05-22", 4L)
        ));

        MvcResult result = mockMvc.perform(get("/api/v1/records/activity-highlights").param("windowDays", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.busiestDay.day").value("2026-05-22"))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(recordsActivityHighlightsFieldNames(), fieldNames(root));
        assertEquals(recordsActivityWindowFieldNames(), fieldNames(root.get("currentWindow")));
        assertEquals(recordsActivityWindowFieldNames(), fieldNames(root.get("previousWindow")));
        assertEquals(recordsActivityPeakFieldNames(), fieldNames(root.get("busiestDay")));
    }

    @Test
    @DisplayName("GET /records/file-plans and /records/categories lock RM tree DTO field sets")
    void listRecordTreesLocksFilePlanAndCategoryContracts() throws Exception {
        UUID filePlanId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID categoryId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID parentId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        when(recordsManagementService.listFilePlans()).thenReturn(List.of(new RecordsManagementService.FilePlanDto(
            filePlanId,
            "Corporate File Plan",
            null,
            "/Corporate File Plan",
            null
        )));
        when(recordsManagementService.listRecordCategories()).thenReturn(List.of(new RecordsManagementService.RecordCategoryDto(
            categoryId,
            "Contracts",
            null,
            "/Corporate File Plan/Contracts",
            2,
            parentId
        )));

        MvcResult filePlansResult = mockMvc.perform(get("/api/v1/records/file-plans"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].description", nullValue()))
            .andExpect(jsonPath("$[0].parentId", nullValue()))
            .andReturn();
        MvcResult categoriesResult = mockMvc.perform(get("/api/v1/records/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].description", nullValue()))
            .andExpect(jsonPath("$[0].parentId").value(parentId.toString()))
            .andReturn();

        JsonNode filePlan = objectMapper.readTree(filePlansResult.getResponse().getContentAsString()).get(0);
        JsonNode category = objectMapper.readTree(categoriesResult.getResponse().getContentAsString()).get(0);
        assertEquals(filePlanFieldNames(), fieldNames(filePlan));
        assertEquals(recordCategoryFieldNames(), fieldNames(category));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> recordDeclarationFieldNames() {
        return List.of("nodeId", "name", "path", "currentVersionLabel", "declaredVersionLabel", "declaredBy", "declaredAt", "declarationComment", "recordCategoryId", "recordCategoryName", "recordCategoryPath");
    }

    private static List<String> recordsSummaryFieldNames() {
        return List.of("declaredRecordCount", "filePlanCount", "recordCategoryCount", "uncategorizedRecordCount", "outsideFilePlanRecordCount", "categoryBreakdown", "filePlanBreakdown");
    }

    private static List<String> summaryBucketFieldNames() {
        return List.of("key", "count");
    }

    private static List<String> pageEnvelopeFieldNames() {
        return List.of("content", "pageable", "totalElements", "totalPages", "last", "size", "number", "sort", "numberOfElements", "first", "empty");
    }

    private static List<String> recordAuditEntryFieldNames() {
        return List.of("auditLogId", "eventType", "nodeId", "nodeName", "username", "eventTime", "details");
    }

    private static List<String> recordsOperationsTelemetryFieldNames() {
        return List.of("governedImportJobCount", "activeGovernedImportJobCount", "failedGovernedImportJobCount", "governedTransferJobCount", "activeGovernedTransferJobCount", "failedGovernedTransferJobCount", "importStatusBreakdown", "transferStatusBreakdown", "importGovernanceReasonBreakdown", "transferGovernanceReasonBreakdown", "recentImportJobs", "recentTransferJobs");
    }

    private static List<String> governedImportJobFieldNames() {
        return List.of("jobId", "targetFolderId", "targetFolderName", "targetFolderPath", "status", "conflictPolicy", "totalFiles", "importedFiles", "skippedFiles", "failedFiles", "lastMessage", "governanceReasons", "startedAt", "completedAt", "createdAt");
    }

    private static List<String> governedTransferJobFieldNames() {
        return List.of("jobId", "definitionId", "sourceNodeId", "sourceNodeName", "sourceNodePath", "targetFolderId", "targetFolderName", "targetFolderPath", "status", "transportStatus", "lastMessage", "transportMessage", "governanceReasons", "startedAt", "completedAt", "createdAt");
    }

    private static List<String> recordsActivityTimelineFieldNames() {
        return List.of("days", "points");
    }

    private static List<String> recordsActivityPointFieldNames() {
        return List.of("day", "declaredCount", "undeclaredCount", "categoryAssignedCount", "governanceChangeCount", "totalCount");
    }

    private static List<String> recordsActivityHighlightsFieldNames() {
        return List.of("windowDays", "currentWindow", "previousWindow", "busiestDay");
    }

    private static List<String> recordsActivityWindowFieldNames() {
        return List.of("fromDay", "toDay", "activeDayCount", "declaredCount", "undeclaredCount", "categoryAssignedCount", "governanceChangeCount", "totalCount");
    }

    private static List<String> recordsActivityPeakFieldNames() {
        return List.of("day", "totalCount");
    }

    private static List<String> filePlanFieldNames() {
        return List.of("folderId", "name", "description", "path", "parentId");
    }

    private static List<String> recordCategoryFieldNames() {
        return List.of("categoryId", "name", "description", "path", "level", "parentId");
    }
}
