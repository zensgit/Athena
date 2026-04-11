package com.ecm.core.cmis;

import com.ecm.core.controller.CmisAtomPubController;
import com.ecm.core.controller.CmisBrowserController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CmisInteropSmokePackTest {

    @Mock
    private CmisAclService aclService;

    @Mock
    private CmisBrowserService browserService;

    @Mock
    private CmisChangeLogService changeLogService;

    @Mock
    private CmisQueryService queryService;

    @Mock
    private CmisMutationService mutationService;

    @Mock
    private CmisContentVersioningService contentVersioningService;

    @Mock
    private CmisRelationshipService relationshipService;

    @Mock
    private CmisRenditionService renditionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc browserMvc;
    private MockMvc atomMvc;

    @BeforeEach
    void setUp() {
        browserMvc = MockMvcBuilders.standaloneSetup(
            new CmisBrowserController(aclService, browserService, changeLogService, queryService, mutationService, contentVersioningService, relationshipService, renditionService)
        ).build();
        atomMvc = MockMvcBuilders.standaloneSetup(
            new CmisAtomPubController(browserService, mutationService, contentVersioningService, new CmisAtomPubSerializer())
        ).build();
    }

    @Test
    @DisplayName("Browser query matches shared response fixture")
    void browserQueryMatchesFixture() throws Exception {
        when(queryService.query("SELECT * FROM cmis:document WHERE IN_FOLDER('folder-1')", 0, 25))
            .thenReturn(new CmisModels.QueryResponse(
                "athena",
                "SELECT * FROM cmis:document WHERE IN_FOLDER('folder-1')",
                List.of(sampleDocument("doc-1", "contract.pdf", "folder-1", "/Contracts/contract.pdf")),
                0,
                25,
                1,
                false
            ));

        MvcResult result = browserMvc.perform(get("/api/v1/cmis/browser")
                .param("cmisselector", "query")
                .param("statement", "SELECT * FROM cmis:document WHERE IN_FOLDER('folder-1')")
                .param("skipCount", "0")
                .param("maxItems", "25"))
            .andExpect(status().isOk())
            .andReturn();

        assertJsonFixture("cmis/interop/browser-query-response.json", result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Browser setContentStream matches shared response fixture")
    void browserSetContentStreamMatchesFixture() throws Exception {
        when(contentVersioningService.setContentStream(any())).thenReturn(new CmisModels.MutationResponse(
            "athena",
            "setContentStream",
            sampleDocument("doc-1", "contract.pdf", "folder-1", "/Contracts/contract.pdf"),
            null,
            "Content stream updated"
        ));

        MvcResult result = browserMvc.perform(post("/api/v1/cmis/browser")
                .param("cmisaction", "setContentStream")
                .contentType("application/json")
                .content(readFixture("cmis/interop/browser-set-content-stream-request.json")))
            .andExpect(status().isOk())
            .andReturn();

        assertJsonFixture("cmis/interop/browser-set-content-stream-response.json", result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Browser content selector streams shared text fixture")
    void browserContentStreamsFixture() throws Exception {
        when(contentVersioningService.getContentStream("doc-1", null))
            .thenReturn(new CmisContentVersioningService.ContentStreamResponse(
                new java.io.ByteArrayInputStream(readFixture("cmis/interop/browser-content-stream.txt").getBytes(StandardCharsets.UTF_8)),
                "text/plain",
                "contract.txt",
                17L
            ));

        MvcResult result = browserMvc.perform(get("/api/v1/cmis/browser")
                .param("cmisselector", "content")
                .param("objectId", "doc-1"))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals("text/plain", result.getResponse().getContentType());
        assertEquals(readFixture("cmis/interop/browser-content-stream.txt"), result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Atom object response matches shared xml fixture")
    void atomObjectMatchesFixture() throws Exception {
        when(browserService.getObject("doc-1", null))
            .thenReturn(sampleDocument("doc-1", "contract.pdf", "folder-1", "/Contracts/contract.pdf"));

        MvcResult result = atomMvc.perform(get("/api/v1/cmis/atom/object").param("objectId", "doc-1"))
            .andExpect(status().isOk())
            .andReturn();

        assertXmlFixture("cmis/interop/atom-object-entry.xml", result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Atom checkout response matches fixture and carries object id")
    void atomCheckoutMatchesFixture() throws Exception {
        when(contentVersioningService.checkOutWorkingCopy(any())).thenReturn(new CmisModels.MutationResponse(
            "athena",
            "checkOut",
            sampleDocument("wc-1", "(Working Copy) contract.pdf", "folder-1", "/Contracts/(Working Copy) contract.pdf"),
            null,
            "Checked out"
        ));

        MvcResult result = atomMvc.perform(post("/api/v1/cmis/atom/checkout").param("objectId", "doc-1"))
            .andExpect(status().isOk())
            .andReturn();

        ArgumentCaptor<CmisModels.MutationRequest> requestCaptor = ArgumentCaptor.forClass(CmisModels.MutationRequest.class);
        verify(contentVersioningService).checkOutWorkingCopy(requestCaptor.capture());
        assertEquals("doc-1", requestCaptor.getValue().objectId());
        assertXmlFixture("cmis/interop/atom-checkout-response.xml", result.getResponse().getContentAsString());
    }

    private CmisModels.ObjectEntry sampleDocument(String objectId, String name, String parentId, String path) {
        LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
        properties.put("cmis:objectId", objectId);
        properties.put("cmis:name", name);
        properties.put("cmis:baseTypeId", "cmis:document");
        return new CmisModels.ObjectEntry(
            "athena",
            objectId,
            name,
            "cmis:document",
            "cmis:document",
            path,
            parentId,
            false,
            properties,
            List.of("canGetProperties", "canGetContentStream")
        );
    }

    private void assertJsonFixture(String fixturePath, String actualJson) throws Exception {
        JsonNode expected = objectMapper.readTree(readFixture(fixturePath));
        JsonNode actual = objectMapper.readTree(actualJson);
        assertEquals(expected, actual);
    }

    private void assertXmlFixture(String fixturePath, String actualXml) throws Exception {
        assertEquals(normalizeXml(readFixture(fixturePath)), normalizeXml(actualXml));
    }

    private String normalizeXml(String xml) {
        return xml.replaceAll(">\\s+<", "><").trim();
    }

    private String readFixture(String path) throws IOException {
        return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
