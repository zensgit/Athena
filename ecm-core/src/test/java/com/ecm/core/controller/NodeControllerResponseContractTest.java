package com.ecm.core.controller;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NodeControllerResponseContractTest {

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
    @DisplayName("GET /nodes/{id} locks NodeDto field set and nullable folder fields")
    void getNodeLocksNodeDtoContract() throws Exception {
        Folder folder = folder(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "Records",
            "/Records"
        );

        when(nodeService.getNode(folder.getId())).thenReturn(folder);

        MvcResult result = mockMvc.perform(get("/api/v1/nodes/{nodeId}", folder.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(folder.getId().toString()))
            .andExpect(jsonPath("$.name").value("Records"))
            .andExpect(jsonPath("$.description").value("Records description"))
            .andExpect(jsonPath("$.path").value("/Records"))
            .andExpect(jsonPath("$.nodeType").value("FOLDER"))
            .andExpect(jsonPath("$.typeQName", nullValue()))
            .andExpect(jsonPath("$.parentId", nullValue()))
            .andExpect(jsonPath("$.size", nullValue()))
            .andExpect(jsonPath("$.contentType", nullValue()))
            .andExpect(jsonPath("$.currentVersionLabel", nullValue()))
            .andExpect(jsonPath("$.correspondentId", nullValue()))
            .andExpect(jsonPath("$.correspondentName", nullValue()))
            .andExpect(jsonPath("$.inheritPermissions").value(true))
            .andExpect(jsonPath("$.locked").value(false))
            .andExpect(jsonPath("$.lockedBy", nullValue()))
            .andExpect(jsonPath("$.lockedDate", nullValue()))
            .andExpect(jsonPath("$.lockLifetime", nullValue()))
            .andExpect(jsonPath("$.lockExpiresAt", nullValue()))
            .andExpect(jsonPath("$.checkedOut").value(false))
            .andExpect(jsonPath("$.checkoutUser", nullValue()))
            .andExpect(jsonPath("$.checkoutDate", nullValue()))
            .andExpect(jsonPath("$.workingCopyOf", nullValue()))
            .andExpect(jsonPath("$.isWorkingCopy").value(false))
            .andExpect(jsonPath("$.createdBy").value("alice"))
            .andExpect(jsonPath("$.createdDate").value("2026-05-22T09:00:00"))
            .andExpect(jsonPath("$.lastModifiedBy").value("alice"))
            .andExpect(jsonPath("$.lastModifiedDate").value("2026-05-22T09:15:00"))
            .andExpect(jsonPath("$.previewStatus", nullValue()))
            .andExpect(jsonPath("$.previewFailureReason", nullValue()))
            .andExpect(jsonPath("$.previewFailureCategory", nullValue()))
            .andExpect(jsonPath("$.previewLastUpdated", nullValue()))
            .andExpect(jsonPath("$.queryCriteria").doesNotExist())
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(nodeDtoWireFieldNames(), fieldNames(root));
        assertTrue(root.get("properties").isObject());
        assertEquals(0, root.get("properties").size());
        assertTrue(root.get("metadata").isObject());
        assertEquals(0, root.get("metadata").size());
        assertTrue(root.get("aspects").isArray());
        assertTrue(root.get("tags").isArray());
        assertTrue(root.get("categories").isArray());
        assertTrue(root.has("size"), "folder NodeDto size must remain present even when null");
        assertTrue(root.get("size").isNull(), "folder NodeDto size must serialize as JSON null");
    }

    @Test
    @DisplayName("GET /nodes/{id}/children keeps Page<NodeDto> content size as explicit null")
    void getChildrenLocksPageNodeDtoContract() throws Exception {
        UUID parentId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Folder child = folder(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            "Child",
            "/Records/Child"
        );

        when(nodeService.getChildren(eq(parentId), any(Pageable.class))).thenReturn(
            new PageImpl<>(List.<Node>of(child), PageRequest.of(0, 20), 1)
        );

        MvcResult result = mockMvc.perform(get("/api/v1/nodes/{nodeId}/children", parentId)
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(child.getId().toString()))
            .andExpect(jsonPath("$.content[0].name").value("Child"))
            .andExpect(jsonPath("$.content[0].description").value("Child description"))
            .andExpect(jsonPath("$.content[0].path").value("/Records/Child"))
            .andExpect(jsonPath("$.content[0].nodeType").value("FOLDER"))
            .andExpect(jsonPath("$.content[0].size", nullValue()))
            .andExpect(jsonPath("$.content[0].queryCriteria").doesNotExist())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.number").value(0))
            .andExpect(jsonPath("$.first").value(true))
            .andExpect(jsonPath("$.last").value(true))
            .andExpect(jsonPath("$.pageable").exists())
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode childNode = root.at("/content/0");
        assertEquals(nodeDtoWireFieldNames(), fieldNames(childNode));
        assertFalse(childNode.has("queryCriteria"), "NodeDto must stay distinct from FolderResponse");
        assertTrue(childNode.has("size"), "Page<NodeDto> folder content size must remain present");
        assertTrue(childNode.get("size").isNull(), "Page<NodeDto> folder content size must serialize as JSON null");
    }

    private static Folder folder(UUID id, String name, String path) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setDescription(name + " description");
        folder.setPath(path);
        folder.setCreatedBy("alice");
        folder.setCreatedDate(LocalDateTime.of(2026, 5, 22, 9, 0));
        folder.setLastModifiedBy("alice");
        folder.setLastModifiedDate(LocalDateTime.of(2026, 5, 22, 9, 15));
        folder.setInheritPermissions(true);
        return folder;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> nodeDtoWireFieldNames() {
        return List.of(
            "id",
            "name",
            "description",
            "path",
            "nodeType",
            "typeQName",
            "parentId",
            "size",
            "contentType",
            "currentVersionLabel",
            "correspondentId",
            "correspondentName",
            "properties",
            "metadata",
            "aspects",
            "tags",
            "categories",
            "inheritPermissions",
            "locked",
            "lockedBy",
            "lockedDate",
            "lockLifetime",
            "lockExpiresAt",
            "checkedOut",
            "checkoutUser",
            "checkoutDate",
            "workingCopyOf",
            "isWorkingCopy",
            "createdBy",
            "createdDate",
            "lastModifiedBy",
            "lastModifiedDate",
            "previewStatus",
            "previewFailureReason",
            "previewFailureCategory",
            "previewLastUpdated"
        );
    }
}
