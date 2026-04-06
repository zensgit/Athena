package com.ecm.core.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ecm.core.cmis.CmisBrowserService;
import com.ecm.core.cmis.CmisContentVersioningService;
import com.ecm.core.cmis.CmisModels;
import com.ecm.core.cmis.CmisMutationService;
import com.ecm.core.cmis.CmisQueryService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CmisBrowserControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CmisBrowserService cmisBrowserService;

    @Mock
    private CmisQueryService cmisQueryService;

    @Mock
    private CmisMutationService cmisMutationService;

    @Mock
    private CmisContentVersioningService cmisContentVersioningService;

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
    @DisplayName("Query selector returns CMIS query payload")
    void browserQueryReturnsQueryResponse() throws Exception {
        when(cmisQueryService.query("SELECT * FROM cmis:document", 0, 25)).thenReturn(new CmisModels.QueryResponse(
            "athena",
            "SELECT * FROM cmis:document",
            List.of(new CmisModels.ObjectEntry(
                "athena",
                "doc-1",
                "contract.pdf",
                "cmis:document",
                "cmis:document",
                "/Sites/contracts/contract.pdf",
                "folder-1",
                false,
                Map.of("cmis:objectId", "doc-1", "cmis:name", "contract.pdf"),
                List.of("canGetProperties")
            )),
            0,
            25,
            1,
            false
        ));

        MvcResult mvcResult = mockMvc.perform(get("/api/v1/cmis/browser")
                .param("cmisselector", "query")
                .param("statement", "SELECT * FROM cmis:document")
                .param("skipCount", "0")
                .param("maxItems", "25"))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode root = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertEquals("SELECT * FROM cmis:document", root.get("statement").asText());
        assertEquals("contract.pdf", root.get("objects").get(0).get("name").asText());
    }

    @Test
    @DisplayName("Unsupported selector returns bad request")
    void browserRejectsUnsupportedSelector() throws Exception {
        mockMvc.perform(get("/api/v1/cmis/browser")
                .param("cmisselector", "bogus"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Content selector streams document payload")
    void browserContentReturnsStream() throws Exception {
        when(cmisContentVersioningService.getContentStream("doc-1", null))
            .thenReturn(new CmisContentVersioningService.ContentStreamResponse(
                new java.io.ByteArrayInputStream("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "text/plain",
                "hello.txt",
                5L
            ));

        MvcResult mvcResult = mockMvc.perform(get("/api/v1/cmis/browser")
                .param("cmisselector", "content")
                .param("objectId", "doc-1"))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals("hello", mvcResult.getResponse().getContentAsString());
        assertEquals("text/plain", mvcResult.getResponse().getContentType());
    }

    @Test
    @DisplayName("Create folder action returns mutation payload")
    void mutateCreateFolderReturnsPayload() throws Exception {
        when(cmisMutationService.createFolder(any())).thenReturn(new CmisModels.MutationResponse(
            "athena",
            "createFolder",
            new CmisModels.ObjectEntry(
                "athena",
                "folder-1",
                "Contracts",
                "cmis:folder",
                "cmis:folder",
                "/Contracts",
                "root",
                false,
                Map.of("cmis:objectId", "folder-1", "cmis:name", "Contracts"),
                List.of("canGetChildren")
            ),
            null,
            "Folder created"
        ));

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/cmis/browser")
                .param("cmisaction", "createFolder")
                .contentType("application/json")
                .content("""
                    {
                      "folderId": "root",
                      "name": "Contracts",
                      "description": "Contract folder"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode root = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertEquals("createFolder", root.get("action").asText());
        assertEquals("Contracts", root.get("object").get("name").asText());
    }

    @Test
    @DisplayName("Unsupported action returns bad request")
    void mutateRejectsUnsupportedAction() throws Exception {
        mockMvc.perform(post("/api/v1/cmis/browser")
                .param("cmisaction", "bogus")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Mutation security errors return forbidden")
    void mutateMapsSecurityErrorsToForbidden() throws Exception {
        when(cmisMutationService.deleteObject(any()))
            .thenThrow(new SecurityException("No permission to delete node"));

        mockMvc.perform(post("/api/v1/cmis/browser")
                .param("cmisaction", "deleteObject")
                .contentType("application/json")
                .content("""
                    {
                      "objectId": "4ff5bca6-62dc-4e10-ad75-3b3dce90395f"
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Check in action dispatches to content/versioning service")
    void mutateCheckInReturnsPayload() throws Exception {
        when(cmisContentVersioningService.checkIn(any())).thenReturn(new CmisModels.MutationResponse(
            "athena",
            "checkIn",
            new CmisModels.ObjectEntry(
                "athena",
                "doc-1",
                "contract.pdf",
                "cmis:document",
                "cmis:document",
                "/Contracts/contract.pdf",
                "root",
                false,
                Map.of("cmis:objectId", "doc-1", "cmis:name", "contract.pdf"),
                List.of("canGetProperties")
            ),
            null,
            "Document checked in"
        ));

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/cmis/browser")
                .param("cmisaction", "checkIn")
                .contentType("application/json")
                .content("""
                    {
                      "objectId": "doc-1",
                      "contentBase64": "aGVsbG8=",
                      "filename": "contract.pdf",
                      "comment": "updated",
                      "majorVersion": true
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode root = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertEquals("checkIn", root.get("action").asText());
        assertEquals("contract.pdf", root.get("object").get("name").asText());
    }
}
