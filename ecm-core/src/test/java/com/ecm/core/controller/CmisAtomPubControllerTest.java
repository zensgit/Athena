package com.ecm.core.controller;

import com.ecm.core.cmis.CmisAtomPubSerializer;
import com.ecm.core.cmis.CmisBrowserService;
import com.ecm.core.cmis.CmisContentVersioningService;
import com.ecm.core.cmis.CmisModels;
import com.ecm.core.cmis.CmisMutationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CmisAtomPubControllerTest {

    @Mock
    private CmisBrowserService browserService;

    @Mock
    private CmisMutationService mutationService;

    @Mock
    private CmisContentVersioningService contentVersioningService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CmisAtomPubController controller = new CmisAtomPubController(
            browserService,
            mutationService,
            contentVersioningService,
            new CmisAtomPubSerializer()
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Service document returns Atom service xml")
    void serviceDocumentReturnsAtomServiceXml() throws Exception {
        when(browserService.getRepositoryInfo()).thenReturn(new CmisModels.RepositoryInfo(
            "athena",
            "Athena Repository",
            "Athena",
            "Athena ECM",
            "1.0",
            "1.1",
            "root",
            List.of("read", "write")
        ));

        MvcResult result = mockMvc.perform(get("/api/v1/cmis/atom"))
            .andExpect(status().isOk())
            .andReturn();

        assertTrue(result.getResponse().getContentType().startsWith("application/atomsvc+xml"));
        assertTrue(result.getResponse().getContentAsString().contains("<app:service"));
        assertTrue(result.getResponse().getContentAsString().contains("<cmis:repositoryId>athena</cmis:repositoryId>"));
    }

    @Test
    @DisplayName("Object endpoint returns Atom entry")
    void objectReturnsAtomEntry() throws Exception {
        when(browserService.getObject("root", null)).thenReturn(new CmisModels.ObjectEntry(
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

        MvcResult result = mockMvc.perform(get("/api/v1/cmis/atom/object").param("objectId", "root"))
            .andExpect(status().isOk())
            .andReturn();

        assertTrue(result.getResponse().getContentType().startsWith("application/atom+xml"));
        assertTrue(result.getResponse().getContentAsString().contains("<atom:entry"));
        assertTrue(result.getResponse().getContentAsString().contains("<atom:title>Company Home</atom:title>"));
    }

    @Test
    @DisplayName("Missing object maps to 404")
    void objectMapsMissingTo404() throws Exception {
        when(browserService.getObject("missing", null)).thenThrow(new NoSuchElementException("Not found"));

        mockMvc.perform(get("/api/v1/cmis/atom/object").param("objectId", "missing"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Create folder returns created Atom entry")
    void createFolderReturnsCreatedAtomEntry() throws Exception {
        when(mutationService.createFolder(any())).thenReturn(new CmisModels.MutationResponse(
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

        MvcResult result = mockMvc.perform(post("/api/v1/cmis/atom/folder")
                .contentType("application/json")
                .content("""
                    {
                      "folderId": "root",
                      "name": "Contracts"
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        assertTrue(result.getResponse().getContentType().startsWith("application/atom+xml"));
        assertTrue(result.getResponse().getContentAsString().contains("createFolder: Folder created"));
    }

    @Test
    @DisplayName("Mutation security errors map to forbidden")
    void mutationSecurityErrorsMapToForbidden() throws Exception {
        when(mutationService.updateProperties(any())).thenThrow(new SecurityException("Forbidden"));

        mockMvc.perform(put("/api/v1/cmis/atom/object")
                .contentType("application/json")
                .content("""
                    {
                      "objectId": "doc-1",
                      "properties": {
                        "cmis:name": "contract-v2.pdf"
                      }
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Delete object returns mutation response xml")
    void deleteObjectReturnsMutationResponseXml() throws Exception {
        when(mutationService.deleteObject(any())).thenReturn(new CmisModels.MutationResponse(
            "athena",
            "deleteObject",
            null,
            "doc-1",
            "Object deleted"
        ));

        MvcResult result = mockMvc.perform(delete("/api/v1/cmis/atom/object").param("objectId", "doc-1"))
            .andExpect(status().isOk())
            .andReturn();

        assertTrue(result.getResponse().getContentType().startsWith("application/atom+xml"));
        assertTrue(result.getResponse().getContentAsString().contains("<cmisra:deletedObjectId>doc-1</cmisra:deletedObjectId>"));
    }

    @Test
    @DisplayName("Checkout delegates to content versioning service")
    void checkOutDelegatesToContentVersioningService() throws Exception {
        when(contentVersioningService.checkOutWorkingCopy(any())).thenReturn(new CmisModels.MutationResponse(
            "athena",
            "checkOut",
            new CmisModels.ObjectEntry(
                "athena",
                "wc-1",
                "(Working Copy) contract.pdf",
                "cmis:document",
                "cmis:document",
                "/Contracts/(Working Copy) contract.pdf",
                "root",
                false,
                Map.of("cmis:objectId", "wc-1", "cmis:name", "(Working Copy) contract.pdf"),
                List.of("canGetProperties")
            ),
            null,
            "Checked out"
        ));

        MvcResult result = mockMvc.perform(post("/api/v1/cmis/atom/checkout").param("objectId", "doc-1"))
            .andExpect(status().isOk())
            .andReturn();

        verify(contentVersioningService).checkOutWorkingCopy(any());
        assertTrue(result.getResponse().getContentAsString().contains("(Working Copy) contract.pdf"));
    }

    @Test
    @DisplayName("Check in carries keepCheckedOut into mutation request")
    void checkInCarriesKeepCheckedOutFlag() throws Exception {
        when(contentVersioningService.checkInWorkingCopy(any())).thenReturn(new CmisModels.MutationResponse(
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
            "Checked in"
        ));

        mockMvc.perform(post("/api/v1/cmis/atom/checkin")
                .param("objectId", "wc-1")
                .param("keepCheckedOut", "true"))
            .andExpect(status().isOk());

        ArgumentCaptor<CmisModels.MutationRequest> captor = ArgumentCaptor.forClass(CmisModels.MutationRequest.class);
        verify(contentVersioningService).checkInWorkingCopy(captor.capture());
        assertEquals("wc-1", captor.getValue().objectId());
        assertEquals(Boolean.TRUE, captor.getValue().keepCheckedOut());
    }

    @Test
    @DisplayName("Mutation IO failures map to internal server error")
    void mutationIoFailuresMapToInternalServerError() throws Exception {
        when(contentVersioningService.checkInWorkingCopy(any())).thenThrow(new IOException("Boom"));

        mockMvc.perform(post("/api/v1/cmis/atom/checkin")
                .param("objectId", "wc-1"))
            .andExpect(status().isInternalServerError());
    }
}
