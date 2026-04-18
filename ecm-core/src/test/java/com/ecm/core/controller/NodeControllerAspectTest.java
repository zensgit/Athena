package com.ecm.core.controller;

import com.ecm.core.entity.Folder;
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

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NodeControllerAspectTest {

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
    @DisplayName("Get aspects returns aspect names")
    void getAspectsReturnsAspectNames() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Mockito.when(nodeService.getAspects(nodeId)).thenReturn(Set.of("cm:titled", "cm:auditable"));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/aspects", nodeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").exists());
    }

    @Test
    @DisplayName("Add aspect supports path-based endpoint used by frontend")
    void addAspectSupportsPathEndpoint() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Folder folder = folder(nodeId, "Contracts");
        folder.addAspect("cm:titled");

        Mockito.when(nodeService.addAspect(Mockito.eq(nodeId), Mockito.eq("cm:titled"), Mockito.anyMap()))
            .thenReturn(folder);

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/aspects/{aspectName}", nodeId, "cm:titled")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Contracts\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(nodeId.toString()))
            .andExpect(jsonPath("$.aspects[0]").value("cm:titled"));
    }

    @Test
    @DisplayName("Remove aspect returns updated node DTO")
    void removeAspectReturnsUpdatedNode() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Folder folder = folder(nodeId, "Contracts");

        Mockito.when(nodeService.removeAspect(nodeId, "cm:titled")).thenReturn(folder);

        mockMvc.perform(delete("/api/v1/nodes/{nodeId}/aspects/{aspectName}", nodeId, "cm:titled"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(nodeId.toString()))
            .andExpect(jsonPath("$.aspects").isArray());
    }

    private Folder folder(UUID id, String name) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setPath("/" + name);
        return folder;
    }
}
