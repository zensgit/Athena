package com.ecm.core.controller;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.service.DocumentRelationService;
import com.ecm.core.service.LockService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.RenditionResourceService;
import com.ecm.core.service.VersionService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NodeControllerPreviewSemanticsTest {

    private MockMvc mockMvc;

    @Mock private NodeService nodeService;
    @Mock private DocumentRelationService relationService;
    @Mock private VersionService versionService;
    @Mock private RenditionResourceService renditionResourceService;
    @Mock private LockService lockService;

    @BeforeEach
    void setUp() {
        NodeController nodeController = new NodeController(nodeService, relationService, versionService, renditionResourceService, lockService);
        mockMvc = MockMvcBuilders.standaloneSetup(nodeController)
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("Node payload normalizes generic binary preview status to unsupported")
    void getNodeReturnsEffectiveUnsupportedPreviewStatus() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setName("blob.bin");
        document.setPath("/blob.bin");
        document.setMimeType("application/octet-stream");

        Mockito.when(nodeService.getNode(documentId)).thenReturn(document);
        Mockito.when(renditionResourceService.summarizeDocument(document)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                documentId,
                true,
                "UNSUPPORTED",
                false,
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                null,
                null
            )
        );

        mockMvc.perform(get("/api/v1/nodes/{nodeId}", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.previewStatus").value("UNSUPPORTED"))
            .andExpect(jsonPath("$.previewFailureReason")
                .value("Preview definition is not registered for generic binary sources"))
            .andExpect(jsonPath("$.previewFailureCategory").value("UNSUPPORTED"));
    }

    @Test
    @DisplayName("Node payload keeps applicable missing preview status pending-compatible")
    void getNodeLeavesApplicablePreviewUnset() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setName("report.pdf");
        document.setPath("/report.pdf");
        document.setMimeType("application/pdf");

        Mockito.when(nodeService.getNode(documentId)).thenReturn(document);
        Mockito.when(renditionResourceService.summarizeDocument(document)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                documentId,
                true,
                null,
                false,
                null,
                null,
                null,
                null
            )
        );

        mockMvc.perform(get("/api/v1/nodes/{nodeId}", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.previewStatus").doesNotExist())
            .andExpect(jsonPath("$.previewFailureReason").doesNotExist())
            .andExpect(jsonPath("$.previewFailureCategory").doesNotExist());
    }

    @Test
    @DisplayName("Node payload normalizes unsupported failures to unsupported status")
    void getNodeReturnsEffectiveUnsupportedFailureStatus() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setName("broken.bin");
        document.setPath("/broken.bin");
        document.setMimeType("application/octet-stream");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("unsupported_media_type");

        Mockito.when(nodeService.getNode(documentId)).thenReturn(document);
        Mockito.when(renditionResourceService.summarizeDocument(document)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                documentId,
                true,
                "UNSUPPORTED",
                false,
                "unsupported_media_type",
                "UNSUPPORTED",
                null,
                null
            )
        );

        mockMvc.perform(get("/api/v1/nodes/{nodeId}", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.previewStatus").value("UNSUPPORTED"))
            .andExpect(jsonPath("$.previewFailureReason").value("unsupported_media_type"))
            .andExpect(jsonPath("$.previewFailureCategory").value("UNSUPPORTED"));
    }

    @Test
    @DisplayName("Node children payloads use rendition summary preview semantics")
    void getChildrenUsesRenditionSummaryPreviewSemantics() throws Exception {
        UUID parentId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        Document child = new Document();
        child.setId(documentId);
        child.setName("draft.docx");
        child.setPath("/workspace/draft.docx");
        child.setMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        Mockito.when(nodeService.getChildren(parentId, PageRequest.of(0, 20))).thenReturn(
            new PageImpl<>(List.of(child), PageRequest.of(0, 20), 1)
        );
        Mockito.when(renditionResourceService.summarizeDocument(child)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                documentId,
                true,
                "PROCESSING",
                false,
                null,
                null,
                LocalDateTime.of(2026, 3, 28, 15, 45),
                "1.2"
            )
        );

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/children", parentId)
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(documentId.toString()))
            .andExpect(jsonPath("$.content[0].previewStatus").value("PROCESSING"))
            .andExpect(jsonPath("$.content[0].previewLastUpdated").exists());
    }

    @Test
    @DisplayName("Node search payloads use rendition summary preview semantics")
    void searchNodesUsesRenditionSummaryPreviewSemantics() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document result = new Document();
        result.setId(documentId);
        result.setName("archive.bin");
        result.setPath("/archive.bin");
        result.setMimeType("application/octet-stream");

        Mockito.when(nodeService.searchNodes(eq("archive"), anyMap(), any())).thenReturn(
            List.of(result)
        );
        Mockito.when(renditionResourceService.summarizeDocument(result)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                documentId,
                true,
                "UNSUPPORTED",
                false,
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                null,
                null
            )
        );

        mockMvc.perform(get("/api/v1/nodes/search")
                .param("query", "archive")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(documentId.toString()))
            .andExpect(jsonPath("$[0].previewStatus").value("UNSUPPORTED"))
            .andExpect(jsonPath("$[0].previewFailureCategory").value("UNSUPPORTED"));
    }
}
