package com.ecm.core.controller;

import com.ecm.core.dto.CheckoutInfoDto;
import com.ecm.core.entity.CheckoutStatus;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.DocumentRelation;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Version;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NodeControllerRelationResponseContractTest {

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
    @DisplayName("GET /nodes/{id}/relations/summary locks summary DTO field set")
    void relationSummaryLocksFieldSet() throws Exception {
        Folder parent = folder(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "Records",
            "/Records"
        );
        Folder child = folder(
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "Cases",
            "/Records/Cases"
        );
        child.setParent(parent);

        when(nodeService.getNode(child.getId())).thenReturn(child);
        when(nodeService.getNode(parent.getId())).thenReturn(parent);
        when(nodeService.getChildren(eq(child.getId()), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.<Node>of(), PageRequest.of(0, 1), 0));

        MvcResult result = mockMvc.perform(get("/api/v1/nodes/{nodeId}/relations/summary", child.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodeId").value(child.getId().toString()))
            .andExpect(jsonPath("$.nodeType").value("FOLDER"))
            .andExpect(jsonPath("$.parentCount").value(1))
            .andExpect(jsonPath("$.childCount").value(0))
            .andExpect(jsonPath("$.sourceRelationCount").value(0))
            .andExpect(jsonPath("$.targetRelationCount").value(0))
            .andExpect(jsonPath("$.versionCount").value(0))
            .andExpect(jsonPath("$.previewStatus", nullValue()))
            .andExpect(jsonPath("$.renditionAvailable").value(false))
            .andExpect(jsonPath("$.checkedOut").value(false))
            .andExpect(jsonPath("$.checkoutUser", nullValue()))
            .andExpect(jsonPath("$.checkoutDate", nullValue()))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(nodeRelationsSummaryFieldNames(), fieldNames(root));
    }

    @Test
    @DisplayName("GET /nodes/{id}/relations/parents locks node-ref DTO field set")
    void relationParentsLockNodeRefFieldSet() throws Exception {
        Folder root = folder(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            "Root",
            "/Root"
        );
        Folder parent = folder(
            UUID.fromString("44444444-4444-4444-4444-444444444444"),
            "Workspace",
            "/Root/Workspace"
        );
        parent.setParent(root);
        Document child = document(
            UUID.fromString("55555555-5555-5555-5555-555555555555"),
            "design.pdf",
            "/Root/Workspace/design.pdf"
        );
        child.setParent(parent);

        when(nodeService.getNode(child.getId())).thenReturn(child);
        when(nodeService.getNode(parent.getId())).thenReturn(parent);
        when(nodeService.getNode(root.getId())).thenReturn(root);

        MvcResult result = mockMvc.perform(get("/api/v1/nodes/{nodeId}/relations/parents", child.getId())
                .param("maxDepth", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(root.getId().toString()))
            .andExpect(jsonPath("$[0].name").value("Root"))
            .andExpect(jsonPath("$[0].path").value("/Root"))
            .andExpect(jsonPath("$[0].nodeType").value("FOLDER"))
            .andExpect(jsonPath("$[0].parentId", nullValue()))
            .andExpect(jsonPath("$[1].id").value(parent.getId().toString()))
            .andExpect(jsonPath("$[1].parentId").value(root.getId().toString()))
            .andReturn();

        JsonNode rootNode = objectMapper.readTree(result.getResponse().getContentAsString()).get(0);
        assertEquals(nodeRelationNodeRefFieldNames(), fieldNames(rootNode));
    }

    @Test
    @DisplayName("GET /nodes/{id}/relations/targets locks edge DTO and nested node-ref field sets")
    void relationTargetsLockEdgeAndNestedNodeRefFieldSets() throws Exception {
        Folder parent = folder(
            UUID.fromString("66666666-6666-6666-6666-666666666666"),
            "Workspace",
            "/Workspace"
        );
        Document source = document(
            UUID.fromString("77777777-7777-7777-7777-777777777777"),
            "source.pdf",
            "/Workspace/source.pdf"
        );
        source.setParent(parent);
        Document target = document(
            UUID.fromString("88888888-8888-8888-8888-888888888888"),
            "target.pdf",
            "/Workspace/target.pdf"
        );
        target.setParent(parent);
        DocumentRelation relation = new DocumentRelation();
        relation.setId(UUID.fromString("99999999-9999-9999-9999-999999999999"));
        relation.setRelationType("ATTACHMENT");
        relation.setSource(source);
        relation.setTarget(target);
        relation.setCreatedDate(LocalDateTime.of(2026, 5, 22, 10, 0));

        when(nodeService.getNode(source.getId())).thenReturn(source);
        when(relationService.getOutgoingRelationsPage(eq(source.getId()), any(Pageable.class), eq("ATTACHMENT")))
            .thenReturn(new PageImpl<>(List.of(relation), PageRequest.of(0, 10), 1));

        MvcResult result = mockMvc.perform(get("/api/v1/nodes/{nodeId}/relations/targets", source.getId())
                .param("page", "0")
                .param("size", "10")
                .param("relationType", "ATTACHMENT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].relationId").value(relation.getId().toString()))
            .andExpect(jsonPath("$.content[0].relationType").value("ATTACHMENT"))
            .andExpect(jsonPath("$.content[0].source.id").value(source.getId().toString()))
            .andExpect(jsonPath("$.content[0].source.parentId").value(parent.getId().toString()))
            .andExpect(jsonPath("$.content[0].target.id").value(target.getId().toString()))
            .andExpect(jsonPath("$.content[0].target.parentId").value(parent.getId().toString()))
            .andExpect(jsonPath("$.content[0].createdDate").value("2026-05-22T10:00:00"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andReturn();

        JsonNode edge = objectMapper.readTree(result.getResponse().getContentAsString()).at("/content/0");
        assertEquals(nodeRelationEdgeFieldNames(), fieldNames(edge));
        assertEquals(nodeRelationNodeRefFieldNames(), fieldNames(edge.get("source")));
        assertEquals(nodeRelationNodeRefFieldNames(), fieldNames(edge.get("target")));
    }

    @Test
    @DisplayName("GET /nodes/{id}/relations/versions locks VersionDto field set")
    void relationVersionsLockVersionDtoFieldSet() throws Exception {
        Document document = document(
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            "contract.docx",
            "/Workspace/contract.docx"
        );
        Version version = version(
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
            document,
            "1.4"
        );
        version.setComment(null);
        version.setCreatedDate(null);
        version.setCreatedBy(null);
        version.setMimeType(null);
        version.setContentHash(null);
        version.setContentId(null);
        version.setStatus(null);
        document.setCurrentVersion(version);

        when(nodeService.getNode(document.getId())).thenReturn(document);
        when(versionService.getVersionHistory(eq(document.getId()), any(Pageable.class), eq(false)))
            .thenReturn(new PageImpl<>(List.of(version), PageRequest.of(0, 10), 1));

        MvcResult result = mockMvc.perform(get("/api/v1/nodes/{nodeId}/relations/versions", document.getId())
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(version.getId().toString()))
            .andExpect(jsonPath("$.content[0].documentId").value(document.getId().toString()))
            .andExpect(jsonPath("$.content[0].versionLabel").value("1.4"))
            .andExpect(jsonPath("$.content[0].comment", nullValue()))
            .andExpect(jsonPath("$.content[0].createdDate", nullValue()))
            .andExpect(jsonPath("$.content[0].creator", nullValue()))
            .andExpect(jsonPath("$.content[0].size").value(0))
            .andExpect(jsonPath("$.content[0].major").value(false))
            .andExpect(jsonPath("$.content[0].mimeType", nullValue()))
            .andExpect(jsonPath("$.content[0].contentHash", nullValue()))
            .andExpect(jsonPath("$.content[0].contentId", nullValue()))
            .andExpect(jsonPath("$.content[0].status", nullValue()))
            .andExpect(jsonPath("$.content[0].checkoutBaseline").value(false))
            .andExpect(jsonPath("$.content[0].checkoutCurrent").value(true))
            .andReturn();

        JsonNode versionNode = objectMapper.readTree(result.getResponse().getContentAsString()).at("/content/0");
        assertEquals(versionDtoFieldNames(), fieldNames(versionNode));
    }

    @Test
    @DisplayName("GET /nodes/{id}/relations/checkout locks checkout relation DTO field set")
    void relationCheckoutLocksFieldSet() throws Exception {
        Folder folder = folder(
            UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
            "Workspace",
            "/Workspace"
        );

        when(nodeService.getNode(folder.getId())).thenReturn(folder);

        MvcResult result = mockMvc.perform(get("/api/v1/nodes/{nodeId}/relations/checkout", folder.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodeId").value(folder.getId().toString()))
            .andExpect(jsonPath("$.document").value(false))
            .andExpect(jsonPath("$.checkedOut").value(false))
            .andExpect(jsonPath("$.checkoutUser", nullValue()))
            .andExpect(jsonPath("$.checkoutDate", nullValue()))
            .andExpect(jsonPath("$.checkoutBaselineVersionId", nullValue()))
            .andExpect(jsonPath("$.checkoutBaselineVersionLabel", nullValue()))
            .andExpect(jsonPath("$.currentVersionLabel", nullValue()))
            .andExpect(jsonPath("$.canCheckout").value(false))
            .andExpect(jsonPath("$.canCheckIn").value(false))
            .andExpect(jsonPath("$.canCancelCheckout").value(false))
            .andExpect(jsonPath("$.canKeepCheckedOut").value(false))
            .andExpect(jsonPath("$.requiresNewVersionFile").value(false))
            .andExpect(jsonPath("$.blockingReason", nullValue()))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(nodeCheckoutRelationFieldNames(), fieldNames(root));
    }

    @Test
    @DisplayName("GET /nodes/{id}/relations/checkout-graph locks graph DTO and nested field sets")
    void relationCheckoutGraphLocksNestedFieldSets() throws Exception {
        Folder parent = folder(
            UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
            "Workspace",
            "/Workspace"
        );
        Document document = document(
            UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
            "contract.docx",
            "/Workspace/contract.docx"
        );
        document.setParent(parent);
        document.setCheckoutUser("alice");
        document.setCheckoutDate(LocalDateTime.of(2026, 5, 22, 11, 0));
        document.setCheckoutBaselineVersionId("ffffffff-ffff-ffff-ffff-ffffffffffff");
        document.setCheckoutBaselineVersionLabel("1.3");
        document.setVersionLabel("1.4");

        Version baseline = version(
            UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
            document,
            "1.3"
        );
        Version current = version(
            UUID.fromString("12121212-1212-1212-1212-121212121212"),
            document,
            "1.4"
        );
        document.setCurrentVersion(current);

        when(nodeService.getNode(document.getId())).thenReturn(document);
        when(nodeService.getCheckoutInfo(document.getId())).thenReturn(new CheckoutInfoDto(
            CheckoutStatus.CHECKED_OUT_BY_YOU,
            "alice",
            document.getCheckoutDate(),
            300L,
            false,
            true,
            true,
            true,
            true,
            null
        ));
        when(versionService.getVersion(baseline.getId())).thenReturn(baseline);

        MvcResult result = mockMvc.perform(get("/api/v1/nodes/{nodeId}/relations/checkout-graph", document.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodeId").value(document.getId().toString()))
            .andExpect(jsonPath("$.document").value(true))
            .andExpect(jsonPath("$.checkedOut").value(true))
            .andExpect(jsonPath("$.checkoutUser").value("alice"))
            .andExpect(jsonPath("$.checkoutDate").value("2026-05-22T11:00:00"))
            .andExpect(jsonPath("$.documentNode.id").value(document.getId().toString()))
            .andExpect(jsonPath("$.workingCopyNode.id").value("working-copy:" + document.getId()))
            .andExpect(jsonPath("$.destinationNode.id").value(parent.getId().toString()))
            .andExpect(jsonPath("$.baselineVersion.id").value(baseline.getId().toString()))
            .andExpect(jsonPath("$.currentVersion.id").value(current.getId().toString()))
            .andExpect(jsonPath("$.nodes.length()").value(5))
            .andExpect(jsonPath("$.edges.length()").value(6))
            .andExpect(jsonPath("$.canCheckIn").value(true))
            .andExpect(jsonPath("$.canCancelCheckout").value(true))
            .andExpect(jsonPath("$.canKeepCheckedOut").value(true))
            .andExpect(jsonPath("$.blockingReason", nullValue()))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(nodeCheckoutGraphFieldNames(), fieldNames(root));
        assertEquals(nodeCheckoutGraphNodeFieldNames(), fieldNames(root.get("documentNode")));
        assertEquals(nodeCheckoutGraphNodeFieldNames(), fieldNames(root.get("workingCopyNode")));
        assertEquals(nodeCheckoutGraphNodeFieldNames(), fieldNames(root.get("nodes").get(0)));
        assertEquals(nodeCheckoutGraphEdgeFieldNames(), fieldNames(root.get("edges").get(0)));
        assertEquals(versionDtoFieldNames(), fieldNames(root.get("baselineVersion")));
        assertEquals(versionDtoFieldNames(), fieldNames(root.get("currentVersion")));
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
        document.setVersionLabel("1.0");
        return document;
    }

    private static Version version(UUID id, Document document, String label) {
        Version version = new Version();
        version.setId(id);
        version.setDocument(document);
        version.setVersionLabel(label);
        version.setFileSize(200L);
        version.setMimeType("application/pdf");
        return version;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> nodeRelationsSummaryFieldNames() {
        return List.of(
            "nodeId",
            "nodeType",
            "parentCount",
            "childCount",
            "sourceRelationCount",
            "targetRelationCount",
            "versionCount",
            "previewStatus",
            "renditionAvailable",
            "checkedOut",
            "checkoutUser",
            "checkoutDate"
        );
    }

    private static List<String> nodeRelationNodeRefFieldNames() {
        return List.of(
            "id",
            "name",
            "path",
            "nodeType",
            "parentId"
        );
    }

    private static List<String> nodeRelationEdgeFieldNames() {
        return List.of(
            "relationId",
            "relationType",
            "source",
            "target",
            "createdDate"
        );
    }

    private static List<String> versionDtoFieldNames() {
        return List.of(
            "id",
            "documentId",
            "versionLabel",
            "comment",
            "createdDate",
            "creator",
            "size",
            "major",
            "mimeType",
            "contentHash",
            "contentId",
            "status",
            "checkoutBaseline",
            "checkoutCurrent"
        );
    }

    private static List<String> nodeCheckoutRelationFieldNames() {
        return List.of(
            "nodeId",
            "document",
            "checkedOut",
            "checkoutUser",
            "checkoutDate",
            "checkoutBaselineVersionId",
            "checkoutBaselineVersionLabel",
            "currentVersionLabel",
            "canCheckout",
            "canCheckIn",
            "canCancelCheckout",
            "canKeepCheckedOut",
            "requiresNewVersionFile",
            "blockingReason"
        );
    }

    private static List<String> nodeCheckoutGraphNodeFieldNames() {
        return List.of(
            "id",
            "kind",
            "label",
            "focus",
            "virtualNode",
            "available"
        );
    }

    private static List<String> nodeCheckoutGraphEdgeFieldNames() {
        return List.of(
            "relationType",
            "sourceId",
            "targetId",
            "label"
        );
    }

    private static List<String> nodeCheckoutGraphFieldNames() {
        return List.of(
            "nodeId",
            "document",
            "checkedOut",
            "checkoutUser",
            "checkoutDate",
            "documentNode",
            "workingCopyNode",
            "destinationNode",
            "baselineVersion",
            "currentVersion",
            "nodes",
            "edges",
            "canCheckIn",
            "canCancelCheckout",
            "canKeepCheckedOut",
            "blockingReason"
        );
    }
}
