package com.ecm.core.controller;

import com.ecm.core.entity.Document;
import com.ecm.core.service.ContentTypeService;
import com.ecm.core.service.NodePropertyEncryptionService;
import com.ecm.core.service.RenditionResourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContentTypeControllerPreviewSemanticsTest {

    private MockMvc mockMvc;

    @Mock
    private ContentTypeService contentTypeService;

    @Mock
    private RenditionResourceService renditionResourceService;

    @Mock
    private NodePropertyEncryptionService nodePropertyEncryptionService;

    @BeforeEach
    void setUp() {
        ContentTypeController controller = new ContentTypeController(
            contentTypeService,
            renditionResourceService,
            nodePropertyEncryptionService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("Apply type response prefers rendition summary preview semantics")
    void applyTypeReturnsRenditionSummaryPreviewSemantics() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Document document = new Document();
        document.setId(nodeId);
        document.setName("archive.bin");
        document.setPath("/archive.bin");
        document.setMimeType("application/octet-stream");

        Mockito.when(contentTypeService.applyType(nodeId, "cm:typed", java.util.Map.of("title", "Archive"))).thenReturn(document);
        Mockito.when(renditionResourceService.summarizeDocument(document)).thenReturn(
            new RenditionResourceService.RenditionSummary(
                nodeId,
                true,
                "UNSUPPORTED",
                false,
                "Preview definition is not registered for generic binary sources",
                "UNSUPPORTED",
                null,
                null
            )
        );

        mockMvc.perform(post("/api/v1/types/nodes/{nodeId}/apply", nodeId)
                .param("type", "cm:typed")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Archive"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(nodeId.toString()))
            .andExpect(jsonPath("$.previewStatus").value("UNSUPPORTED"))
            .andExpect(jsonPath("$.previewFailureCategory").value("UNSUPPORTED"));
    }

    @Test
    @DisplayName("Apply type response uses masked property response projection")
    void applyTypeUsesMaskedPropertyResponseProjection() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Document document = new Document();
        document.setId(nodeId);
        document.setName("archive.bin");
        document.setPath("/archive.bin");

        Mockito.when(contentTypeService.applyType(nodeId, "cm:typed", java.util.Map.of("title", "Archive"))).thenReturn(document);
        Mockito.when(nodePropertyEncryptionService.resolveResponseProperties(document))
            .thenReturn(Map.of("acme:secretCode", NodePropertyEncryptionService.REDACTED_PROTECTED_PAYLOAD));

        mockMvc.perform(post("/api/v1/types/nodes/{nodeId}/apply", nodeId)
                .param("type", "cm:typed")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Archive"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.properties['acme:secretCode']")
                .value(NodePropertyEncryptionService.REDACTED_PROTECTED_PAYLOAD));
    }
}
