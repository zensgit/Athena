package com.ecm.core.controller;

import com.ecm.core.entity.Node.ArchiveStatus;
import com.ecm.core.entity.Node.ArchiveStoreTier;
import com.ecm.core.service.ArchivePolicyService;
import com.ecm.core.service.ContentArchiveService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContentArchiveControllerTest {

    @Mock private ContentArchiveService contentArchiveService;
    @Mock private ArchivePolicyService archivePolicyService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ContentArchiveController controller = new ContentArchiveController(contentArchiveService, archivePolicyService);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("POST /nodes/{nodeId}/archive archives node")
    void archiveNodeArchivesNode() throws Exception {
        UUID nodeId = UUID.randomUUID();
        when(contentArchiveService.archiveNode(eq(nodeId), eq(ArchiveStoreTier.GLACIER)))
            .thenReturn(new ContentArchiveService.ArchiveMutationDto(
                nodeId,
                "spec.docx",
                ArchiveStatus.ARCHIVED,
                ArchiveStoreTier.GLACIER,
                LocalDateTime.of(2026, 4, 3, 10, 0),
                "alice",
                1
            ));

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/archive", nodeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new ContentArchiveController.ArchiveNodeRequest(ArchiveStoreTier.GLACIER))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archiveStatus").value("ARCHIVED"))
            .andExpect(jsonPath("$.archiveStoreTier").value("GLACIER"))
            .andExpect(jsonPath("$.affectedNodeCount").value(1));
    }

    @Test
    @DisplayName("POST /nodes/{nodeId}/restore restores node")
    void restoreNodeRestoresNode() throws Exception {
        UUID nodeId = UUID.randomUUID();
        when(contentArchiveService.restoreNode(eq(nodeId)))
            .thenReturn(new ContentArchiveService.ArchiveMutationDto(
                nodeId,
                "spec.docx",
                ArchiveStatus.LIVE,
                ArchiveStoreTier.HOT,
                null,
                null,
                1
            ));

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/restore", nodeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archiveStatus").value("LIVE"))
            .andExpect(jsonPath("$.archiveStoreTier").value("HOT"));
    }

    @Test
    @DisplayName("GET /nodes/{nodeId}/archive-status returns node archive status")
    void getArchiveStatusReturnsStatus() throws Exception {
        UUID nodeId = UUID.randomUUID();
        when(contentArchiveService.getArchiveStatus(eq(nodeId)))
            .thenReturn(new ContentArchiveService.ArchiveStatusDto(
                nodeId,
                "spec.docx",
                "DOCUMENT",
                "/finance/spec.docx",
                ArchiveStatus.ARCHIVED,
                ArchiveStoreTier.COLD,
                LocalDateTime.of(2026, 4, 3, 10, 0),
                "alice"
            ));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/archive-status", nodeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodeType").value("DOCUMENT"))
            .andExpect(jsonPath("$.archiveStatus").value("ARCHIVED"))
            .andExpect(jsonPath("$.archiveStoreTier").value("COLD"));
    }

    @Test
    @DisplayName("GET /nodes/archived lists archived nodes")
    void listArchivedNodesReturnsPage() throws Exception {
        when(contentArchiveService.listArchivedNodes(eq(PageRequest.of(0, 20))))
            .thenReturn(new PageImpl<>(
                List.of(new ContentArchiveService.ArchivedNodeDto(
                    UUID.randomUUID(),
                    "spec.docx",
                    "DOCUMENT",
                    "/finance/spec.docx",
                    1024L,
                    "alice",
                    LocalDateTime.of(2026, 4, 1, 9, 0),
                    ArchiveStatus.ARCHIVED,
                    ArchiveStoreTier.WARM,
                    LocalDateTime.of(2026, 4, 3, 10, 0),
                    "admin"
                )),
                PageRequest.of(0, 20),
                1
            ));

        mockMvc.perform(get("/api/v1/nodes/archived").param("page", "0").param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].archiveStatus").value("ARCHIVED"))
            .andExpect(jsonPath("$.content[0].archiveStoreTier").value("WARM"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("PUT /folders/{folderId}/archive-policy upserts folder archive policy")
    void upsertArchivePolicyReturnsPolicy() throws Exception {
        UUID folderId = UUID.randomUUID();
        when(archivePolicyService.upsertPolicy(eq(folderId), any()))
            .thenReturn(new ArchivePolicyService.ArchivePolicyDto(
                UUID.randomUUID(),
                folderId,
                "Finance",
                "/Finance",
                true,
                90,
                ArchiveStoreTier.COLD,
                true,
                100,
                null,
                null,
                null,
                null,
                null
            ));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/folders/{folderId}/archive-policy", folderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "enabled": true,
                      "inactivityDays": 90,
                      "storageTier": "COLD",
                      "includeSubfolders": true,
                      "maxCandidatesPerRun": 100
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.folderId").value(folderId.toString()))
            .andExpect(jsonPath("$.storageTier").value("COLD"));
    }

    @Test
    @DisplayName("POST /folders/{folderId}/archive-policy/dry-run returns candidates")
    void dryRunArchivePolicyReturnsCandidates() throws Exception {
        UUID folderId = UUID.randomUUID();
        when(archivePolicyService.dryRunPolicy(eq(folderId), any()))
            .thenReturn(new ArchivePolicyService.ArchivePolicyDryRunDto(
                folderId,
                "Finance",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                ArchiveStoreTier.GLACIER,
                true,
                50,
                1,
                List.of(new ArchivePolicyService.ArchivePolicyCandidateDto(
                    UUID.randomUUID(),
                    "legacy.pdf",
                    "DOCUMENT",
                    "/Finance/legacy.pdf",
                    LocalDateTime.of(2025, 10, 1, 0, 0)
                ))
            ));

        mockMvc.perform(post("/api/v1/folders/{folderId}/archive-policy/dry-run", folderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "enabled": true,
                      "inactivityDays": 90,
                      "storageTier": "GLACIER",
                      "includeSubfolders": true,
                      "maxCandidatesPerRun": 50
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidateCount").value(1))
            .andExpect(jsonPath("$.candidates[0].path").value("/Finance/legacy.pdf"));
    }

    @Test
    @DisplayName("POST /archive-policies/run executes enabled policies")
    void runArchivePoliciesReturnsSummary() throws Exception {
        when(archivePolicyService.runScheduledPolicies())
            .thenReturn(new ArchivePolicyService.ArchivePolicyBatchExecutionDto(
                1,
                2,
                3,
                0,
                List.of(new ArchivePolicyService.ArchivePolicyExecutionDto(
                    UUID.randomUUID(),
                    "Finance",
                    2,
                    3,
                    0,
                    List.of(),
                    null
                ))
            ));

        mockMvc.perform(post("/api/v1/archive-policies/run"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.executedPolicies").value(1))
            .andExpect(jsonPath("$.archivedNodeCount").value(3));
    }
}
