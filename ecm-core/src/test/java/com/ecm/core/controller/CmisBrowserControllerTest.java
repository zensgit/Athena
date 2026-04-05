package com.ecm.core.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ecm.core.cmis.CmisBrowserService;
import com.ecm.core.cmis.CmisModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CmisBrowserControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CmisBrowserService cmisBrowserService;

    @InjectMocks
    private CmisBrowserController cmisBrowserController;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(cmisBrowserController).build();
    }

    @Test
    @DisplayName("Repository info selector returns repository payload")
    void browserRepositoryInfoReturnsMetadata() throws Exception {
        when(cmisBrowserService.getRepositoryInfo()).thenReturn(new CmisModels.RepositoryInfo(
            "athena",
            "Athena Repository",
            "Athena",
            "Athena ECM",
            "1.0",
            "1.1",
            "root",
            List.of("read")
        ));

        MvcResult mvcResult = mockMvc.perform(get("/api/v1/cmis/browser")
                .param("cmisselector", "repositoryInfo"))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode root = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertEquals("athena", root.get("repositoryId").asText());
        assertEquals("root", root.get("rootFolderId").asText());
    }

    @Test
    @DisplayName("Object selector returns CMIS object payload")
    void browserObjectReturnsObjectEntry() throws Exception {
        when(cmisBrowserService.getObject("root", null)).thenReturn(new CmisModels.ObjectEntry(
            "athena",
            "root",
            "Company Home",
            "cmis:folder",
            "cmis:folder",
            "/",
            null,
            true,
            Map.of("cmis:objectId", "root", "cmis:name", "Company Home"),
            List.of("canGetChildren")
        ));

        MvcResult mvcResult = mockMvc.perform(get("/api/v1/cmis/browser")
                .param("cmisselector", "object")
                .param("objectId", "root"))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode root = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertEquals("root", root.get("objectId").asText());
        assertTrue(root.get("root").asBoolean());
    }

    @Test
    @DisplayName("Children selector returns list payload")
    void browserChildrenReturnsChildrenResponse() throws Exception {
        when(cmisBrowserService.getChildren("root", null, 0, 10)).thenReturn(new CmisModels.ChildrenResponse(
            "athena",
            "root",
            List.of(new CmisModels.ObjectEntry(
                "athena",
                "folder-1",
                "Sites",
                "cmis:folder",
                "cmis:folder",
                "/Sites",
                "root",
                false,
                Map.of("cmis:objectId", "folder-1", "cmis:name", "Sites"),
                List.of("canGetChildren")
            )),
            0,
            10,
            1,
            false
        ));

        MvcResult mvcResult = mockMvc.perform(get("/api/v1/cmis/browser")
                .param("cmisselector", "children")
                .param("objectId", "root")
                .param("skipCount", "0")
                .param("maxItems", "10"))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode root = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertEquals(1, root.get("totalNumItems").asInt());
        assertEquals("Sites", root.get("objects").get(0).get("name").asText());
    }

    @Test
    @DisplayName("Unsupported selector returns bad request")
    void browserRejectsUnsupportedSelector() throws Exception {
        mockMvc.perform(get("/api/v1/cmis/browser")
                .param("cmisselector", "query"))
            .andExpect(status().isBadRequest());
    }
}
