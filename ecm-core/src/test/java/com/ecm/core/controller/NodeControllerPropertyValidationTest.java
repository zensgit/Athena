package com.ecm.core.controller;

import com.ecm.core.exception.PropertyValidationException;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NodeControllerPropertyValidationTest {

    private MockMvc mockMvc;

    @Mock private NodeService nodeService;
    @Mock private DocumentRelationService relationService;
    @Mock private VersionService versionService;
    @Mock private RenditionResourceService renditionResourceService;
    @Mock private LockService lockService;

    @BeforeEach
    void setUp() {
        NodeController controller = new NodeController(nodeService, relationService, versionService, renditionResourceService, lockService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("Add aspect returns structured property validation details")
    void addAspectReturnsStructuredValidationDetails() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Mockito.when(nodeService.addAspect(Mockito.eq(nodeId), Mockito.eq("cm:titled"), Mockito.anyMap()))
            .thenThrow(new PropertyValidationException(
                "Property validation failed: Missing mandatory property 'cm:title' for aspect cm:titled",
                List.of("Missing mandatory property 'cm:title' for aspect cm:titled")
            ));

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/aspects/{aspectName}", nodeId, "cm:titled")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Property validation failed: Missing mandatory property 'cm:title' for aspect cm:titled"))
            .andExpect(jsonPath("$.details[0]").value("Missing mandatory property 'cm:title' for aspect cm:titled"));
    }

    @Test
    @DisplayName("Update node returns validation details array")
    void updateNodeReturnsStructuredValidationDetails() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Mockito.when(nodeService.updateNode(Mockito.eq(nodeId), Mockito.anyMap()))
            .thenThrow(new PropertyValidationException(
                "Property validation failed: Value 'URGENT' is not in the allowed list [LOW, MEDIUM, HIGH]",
                List.of("Value 'URGENT' is not in the allowed list [LOW, MEDIUM, HIGH]")
            ));

        mockMvc.perform(patch("/api/v1/nodes/{nodeId}", nodeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "properties": {
                        "cm:priority": "URGENT"
                      }
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.details[0]").value("Value 'URGENT' is not in the allowed list [LOW, MEDIUM, HIGH]"));
    }

}
