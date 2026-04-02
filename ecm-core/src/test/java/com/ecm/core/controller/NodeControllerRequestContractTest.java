package com.ecm.core.controller;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.exception.PropertyValidationException;
import com.ecm.core.service.DocumentRelationService;
import com.ecm.core.service.LockService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.RenditionResourceService;
import com.ecm.core.service.VersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class NodeControllerRequestContractTest {

    private MockMvc mockMvc;

    @Mock private NodeService nodeService;
    @Mock private DocumentRelationService relationService;
    @Mock private VersionService versionService;
    @Mock private RenditionResourceService renditionResourceService;
    @Mock private LockService lockService;

    @BeforeEach
    void setUp() {
        NodeController controller = new NodeController(
            nodeService, relationService, versionService, renditionResourceService, lockService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    // ================================================================= CreateNodeRequest

    @Nested
    @DisplayName("POST /nodes (CreateNodeRequest)")
    class CreateNode {

        @Test
        @DisplayName("creates folder from typed request")
        void createsFolderFromDto() throws Exception {
            UUID parentId = UUID.randomUUID();
            Folder folder = new Folder();
            folder.setId(UUID.randomUUID());
            folder.setName("reports");
            folder.setPath("/reports");

            when(nodeService.createNode(any(Node.class), eq(parentId))).thenReturn(folder);

            mockMvc.perform(post("/api/v1/nodes")
                    .param("parentId", parentId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "reports",
                          "nodeType": "FOLDER",
                          "description": "Monthly reports",
                          "aspects": ["cm:titled"],
                          "properties": {"cm:title": "Reports"}
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("reports"));

            ArgumentCaptor<Node> captor = ArgumentCaptor.forClass(Node.class);
            verify(nodeService).createNode(captor.capture(), eq(parentId));
            Node captured = captor.getValue();
            assertTrue(captured instanceof Folder);
            assertEquals("reports", captured.getName());
            assertTrue(captured.hasAspect("cm:titled"));
            assertEquals("Reports", captured.getProperties().get("cm:title"));
        }

        @Test
        @DisplayName("creates document from typed request with mimeType")
        void createsDocumentFromDto() throws Exception {
            Document doc = new Document();
            doc.setId(UUID.randomUUID());
            doc.setName("file.pdf");
            doc.setMimeType("application/pdf");
            doc.setPath("/file.pdf");

            when(nodeService.createNode(any(Node.class), isNull())).thenReturn(doc);

            mockMvc.perform(post("/api/v1/nodes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "file.pdf",
                          "nodeType": "DOCUMENT",
                          "mimeType": "application/pdf"
                        }
                        """))
                .andExpect(status().isCreated());

            ArgumentCaptor<Node> captor = ArgumentCaptor.forClass(Node.class);
            verify(nodeService).createNode(captor.capture(), isNull());
            assertTrue(captor.getValue() instanceof Document);
            assertEquals("application/pdf", ((Document) captor.getValue()).getMimeType());
        }

        @Test
        @DisplayName("rejects missing name with 400")
        void rejectsMissingName() throws Exception {
            mockMvc.perform(post("/api/v1/nodes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "nodeType": "FOLDER" }
                        """))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("rejects missing nodeType with 400")
        void rejectsMissingNodeType() throws Exception {
            mockMvc.perform(post("/api/v1/nodes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "name": "test" }
                        """))
                .andExpect(status().isBadRequest());
        }
    }

    // ================================================================= UpdateNodeRequest

    @Nested
    @DisplayName("PATCH /nodes/{id} (UpdateNodeRequest)")
    class UpdateNode {

        @Test
        @DisplayName("passes typed fields to service")
        void passesTypedFields() throws Exception {
            UUID nodeId = UUID.randomUUID();
            Folder folder = new Folder();
            folder.setId(nodeId);
            folder.setName("updated");
            folder.setPath("/updated");

            when(nodeService.updateNode(eq(nodeId), anyMap())).thenReturn(folder);

            mockMvc.perform(patch("/api/v1/nodes/{nodeId}", nodeId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "updated",
                          "description": "New desc",
                          "properties": {"key": "value"}
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("updated"));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(nodeService).updateNode(eq(nodeId), captor.capture());
            Map<String, Object> updates = captor.getValue();
            assertEquals("updated", updates.get("name"));
            assertEquals("New desc", updates.get("description"));
            assertNotNull(updates.get("properties"));
        }

        @Test
        @DisplayName("omits null fields from update map")
        void omitsNullFields() throws Exception {
            UUID nodeId = UUID.randomUUID();
            Folder folder = new Folder();
            folder.setId(nodeId);
            folder.setName("test");
            folder.setPath("/test");

            when(nodeService.updateNode(eq(nodeId), anyMap())).thenReturn(folder);

            mockMvc.perform(patch("/api/v1/nodes/{nodeId}", nodeId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "name": "test" }
                        """))
                .andExpect(status().isOk());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(nodeService).updateNode(eq(nodeId), captor.capture());
            Map<String, Object> updates = captor.getValue();
            assertTrue(updates.containsKey("name"));
            assertFalse(updates.containsKey("description"));
            assertFalse(updates.containsKey("properties"));
        }
    }

    // ================================================================= AddAspectRequest

    @Nested
    @DisplayName("POST /nodes/{id}/aspects (AddAspectRequest)")
    class AddAspect {

        @Test
        @DisplayName("body-style passes aspectName and properties to service")
        void bodyStylePassesProperties() throws Exception {
            UUID nodeId = UUID.randomUUID();
            Folder folder = new Folder();
            folder.setId(nodeId);
            folder.setName("test");
            folder.setPath("/test");

            when(nodeService.addAspect(eq(nodeId), eq("cm:titled"), anyMap())).thenReturn(folder);

            mockMvc.perform(post("/api/v1/nodes/{nodeId}/aspects", nodeId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "aspectName": "cm:titled",
                          "properties": {"cm:title": "Hello World"}
                        }
                        """))
                .andExpect(status().isOk());

            verify(nodeService).addAspect(eq(nodeId), eq("cm:titled"), anyMap());
        }

        @Test
        @DisplayName("path-style passes properties from body")
        void pathStylePassesProperties() throws Exception {
            UUID nodeId = UUID.randomUUID();
            Folder folder = new Folder();
            folder.setId(nodeId);
            folder.setName("test");
            folder.setPath("/test");

            when(nodeService.addAspect(eq(nodeId), eq("cm:titled"), anyMap())).thenReturn(folder);

            mockMvc.perform(post("/api/v1/nodes/{nodeId}/aspects/{aspectName}", nodeId, "cm:titled")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"cm:title": "Hello World"}
                        """))
                .andExpect(status().isOk());

            verify(nodeService).addAspect(eq(nodeId), eq("cm:titled"), anyMap());
        }

        @Test
        @DisplayName("body-style rejects blank aspectName")
        void rejectsBlankAspectName() throws Exception {
            UUID nodeId = UUID.randomUUID();

            mockMvc.perform(post("/api/v1/nodes/{nodeId}/aspects", nodeId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "aspectName": "" }
                        """))
                .andExpect(status().isBadRequest());
        }
    }

    // ================================================================= validation detail exposure

    @Nested
    @DisplayName("PropertyValidationException details in response")
    class ValidationDetails {

        @Test
        @DisplayName("response body includes details array from PropertyValidationException")
        void responseIncludesDetails() throws Exception {
            UUID nodeId = UUID.randomUUID();

            when(nodeService.addAspect(eq(nodeId), eq("cm:titled"), isNull()))
                .thenThrow(new PropertyValidationException(
                    "Property validation failed",
                    List.of(
                        "Missing mandatory property 'cm:title' for aspect cm:titled",
                        "Value 'X' is not in the allowed list [A, B, C]"
                    )
                ));

            mockMvc.perform(post("/api/v1/nodes/{nodeId}/aspects/{aspectName}", nodeId, "cm:titled"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Property validation failed"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details.length()").value(2))
                .andExpect(jsonPath("$.details[0]").value("Missing mandatory property 'cm:title' for aspect cm:titled"))
                .andExpect(jsonPath("$.details[1]").value("Value 'X' is not in the allowed list [A, B, C]"));
        }

        @Test
        @DisplayName("response body has empty details for non-validation errors")
        void nonValidationErrorHasNoDetails() throws Exception {
            UUID nodeId = UUID.randomUUID();

            when(nodeService.addAspect(eq(nodeId), eq("cm:titled"), isNull()))
                .thenThrow(new SecurityException("No permission"));

            mockMvc.perform(post("/api/v1/nodes/{nodeId}/aspects/{aspectName}", nodeId, "cm:titled"))
                .andExpect(status().isForbidden());
        }
    }
}
