package com.ecm.core.controller;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.RenditionResource;
import com.ecm.core.entity.RenditionState;
import com.ecm.core.service.DocumentRelationService;
import com.ecm.core.service.LockService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.RenditionResourceService;
import com.ecm.core.service.VersionService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NodeControllerRenditionRelationResponseContractTest {

    @Mock
    private NodeService nodeService;

    @Mock
    private DocumentRelationService relationService;

    @Mock
    private VersionService versionService;

    @Mock
    private RenditionResourceService renditionResourceService;

    @Mock
    private LockService lockService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        NodeController controller = new NodeController(
            nodeService,
            relationService,
            versionService,
            renditionResourceService,
            lockService
        );
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @Test
    @DisplayName("GET /nodes/{id}/relations/renditions/summary locks summary DTO field set")
    void renditionSummaryLocksFieldSet() throws Exception {
        Document document = document(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "report.pdf",
            "/Workspace/report.pdf"
        );
        LocalDateTime lastUpdated = LocalDateTime.of(2026, 5, 22, 12, 0);

        when(nodeService.getNode(document.getId())).thenReturn(document);
        when(renditionResourceService.summarizeDocument(document)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                document.getId(),
                true,
                "FAILED",
                false,
                "Hash mismatch",
                "CONTENT",
                lastUpdated,
                "1.2"
            )
        );

        MvcResult result = mockMvc.perform(get(
                "/api/v1/nodes/{nodeId}/relations/renditions/summary",
                document.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodeId").value(document.getId().toString()))
            .andExpect(jsonPath("$.document").value(true))
            .andExpect(jsonPath("$.previewStatus").value("FAILED"))
            .andExpect(jsonPath("$.renditionAvailable").value(false))
            .andExpect(jsonPath("$.previewFailureReason").value("Hash mismatch"))
            .andExpect(jsonPath("$.previewFailureCategory").value("CONTENT"))
            .andExpect(jsonPath("$.previewLastUpdated").value("2026-05-22T12:00:00"))
            .andExpect(jsonPath("$.currentVersionLabel").value("1.2"))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(renditionSummaryFieldNames(), fieldNames(root));
    }

    @Test
    @DisplayName("GET /nodes/{id}/relations/renditions/summary keeps folder nullable summary fields explicit")
    void renditionSummaryForFolderKeepsNullFieldsExplicit() throws Exception {
        Folder folder = folder(
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "Workspace",
            "/Workspace"
        );

        when(nodeService.getNode(folder.getId())).thenReturn(folder);

        MvcResult result = mockMvc.perform(get(
                "/api/v1/nodes/{nodeId}/relations/renditions/summary",
                folder.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodeId").value(folder.getId().toString()))
            .andExpect(jsonPath("$.document").value(false))
            .andExpect(jsonPath("$.previewStatus", nullValue()))
            .andExpect(jsonPath("$.renditionAvailable").value(false))
            .andExpect(jsonPath("$.previewFailureReason", nullValue()))
            .andExpect(jsonPath("$.previewFailureCategory", nullValue()))
            .andExpect(jsonPath("$.previewLastUpdated", nullValue()))
            .andExpect(jsonPath("$.currentVersionLabel", nullValue()))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(renditionSummaryFieldNames(), fieldNames(root));
    }

    @Test
    @DisplayName("GET /nodes/{id}/relations/renditions locks paged rendition DTO field set")
    void renditionListLocksPagedDtoFieldSet() throws Exception {
        Document document = document(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            "report.pdf",
            "/Workspace/report.pdf"
        );
        RenditionResource preview = rendition(
            document,
            "preview",
            "Preview",
            RenditionState.FAILED,
            false,
            false
        );
        preview.setErrorReason("Hash mismatch");
        preview.setErrorCategory("CONTENT");
        preview.setSourceUpdatedAt(LocalDateTime.of(2026, 5, 22, 12, 15));
        preview.setVersionLabel("1.2");

        when(nodeService.getNode(document.getId())).thenReturn(document);
        when(renditionResourceService.listForDocument(document)).thenReturn(List.of(preview));

        MvcResult result = mockMvc.perform(get(
                "/api/v1/nodes/{nodeId}/relations/renditions",
                document.getId())
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].nodeId").value(document.getId().toString()))
            .andExpect(jsonPath("$.content[0].renditionId").value("preview"))
            .andExpect(jsonPath("$.content[0].label").value("Preview"))
            .andExpect(jsonPath("$.content[0].status").value("FAILED"))
            .andExpect(jsonPath("$.content[0].available").value(false))
            .andExpect(jsonPath("$.content[0].mimeType").value("application/json"))
            .andExpect(jsonPath("$.content[0].url").value("/api/v1/documents/" + document.getId() + "/preview"))
            .andExpect(jsonPath("$.content[0].downloadable").value(false))
            .andExpect(jsonPath("$.content[0].failureReason").value("Hash mismatch"))
            .andExpect(jsonPath("$.content[0].failureCategory").value("CONTENT"))
            .andExpect(jsonPath("$.content[0].previewLastUpdated").value("2026-05-22T12:15:00"))
            .andExpect(jsonPath("$.content[0].currentVersionLabel").value("1.2"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.size").value(10))
            .andExpect(jsonPath("$.number").value(0))
            .andReturn();

        JsonNode item = objectMapper.readTree(result.getResponse().getContentAsString()).at("/content/0");
        assertEquals(renditionRelationFieldNames(), fieldNames(item));
    }

    @Test
    @DisplayName("GET /nodes/{id}/relations/renditions/{renditionId} locks single rendition DTO field set")
    void singleRenditionLocksDtoFieldSet() throws Exception {
        Document document = document(
            UUID.fromString("44444444-4444-4444-4444-444444444444"),
            "report.pdf",
            "/Workspace/report.pdf"
        );
        RenditionResource thumbnail = rendition(
            document,
            "thumbnail",
            "Thumbnail",
            RenditionState.READY,
            true,
            true
        );
        thumbnail.setErrorReason(null);
        thumbnail.setErrorCategory(null);
        thumbnail.setSourceUpdatedAt(null);
        thumbnail.setVersionLabel(null);

        when(nodeService.getNode(document.getId())).thenReturn(document);
        when(renditionResourceService.listForDocument(document)).thenReturn(List.of(thumbnail));

        MvcResult result = mockMvc.perform(get(
                "/api/v1/nodes/{nodeId}/relations/renditions/{renditionId}",
                document.getId(),
                "thumbnail"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodeId").value(document.getId().toString()))
            .andExpect(jsonPath("$.renditionId").value("thumbnail"))
            .andExpect(jsonPath("$.label").value("Thumbnail"))
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.available").value(true))
            .andExpect(jsonPath("$.mimeType").value("image/png"))
            .andExpect(jsonPath("$.url").value("/api/v1/documents/" + document.getId() + "/thumbnail"))
            .andExpect(jsonPath("$.downloadable").value(true))
            .andExpect(jsonPath("$.failureReason", nullValue()))
            .andExpect(jsonPath("$.failureCategory", nullValue()))
            .andExpect(jsonPath("$.previewLastUpdated", nullValue()))
            .andExpect(jsonPath("$.currentVersionLabel", nullValue()))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(renditionRelationFieldNames(), fieldNames(root));
    }

    private static Folder folder(UUID id, String name, String path) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setPath(path);
        return folder;
    }

    private static Document document(UUID id, String name, String path) {
        Document document = new Document();
        document.setId(id);
        document.setName(name);
        document.setPath(path);
        document.setMimeType("application/pdf");
        return document;
    }

    private static RenditionResource rendition(
        Document document,
        String key,
        String label,
        RenditionState state,
        boolean available,
        boolean downloadable
    ) {
        return RenditionResource.builder()
            .document(document)
            .renditionKey(key)
            .label(label)
            .mimeType("thumbnail".equals(key) ? "image/png" : "application/json")
            .state(state)
            .available(available)
            .downloadable(downloadable)
            .contentUrl("/api/v1/documents/" + document.getId() + "/" + key)
            .build();
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> renditionSummaryFieldNames() {
        return List.of(
            "nodeId",
            "document",
            "previewStatus",
            "renditionAvailable",
            "previewFailureReason",
            "previewFailureCategory",
            "previewLastUpdated",
            "currentVersionLabel"
        );
    }

    private static List<String> renditionRelationFieldNames() {
        return List.of(
            "nodeId",
            "renditionId",
            "label",
            "status",
            "available",
            "mimeType",
            "url",
            "downloadable",
            "failureReason",
            "failureCategory",
            "previewLastUpdated",
            "currentVersionLabel"
        );
    }
}
