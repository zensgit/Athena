package com.ecm.core.controller;

import com.ecm.core.entity.AssocDirection;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.DocumentRelation;
import com.ecm.core.entity.Folder;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class NodeControllerAssociationEndpointTest {

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

    @Nested
    @DisplayName("GET /nodes/{id}/targets")
    class GetTargets {

        @Test
        @DisplayName("returns peer target associations as edge DTOs")
        void returnsTargets() throws Exception {
            UUID nodeId = UUID.randomUUID();
            DocumentRelation rel = peerRelation(nodeId, "cm:references");

            when(relationService.getTargetAssociations(nodeId, null)).thenReturn(List.of(rel));

            mockMvc.perform(get("/api/v1/nodes/{nodeId}/targets", nodeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].relationType").value("cm:references"))
                .andExpect(jsonPath("$[0].source.name").value("source.pdf"))
                .andExpect(jsonPath("$[0].target.name").value("target.pdf"));
        }

        @Test
        @DisplayName("filters by assocType parameter")
        void filtersByAssocType() throws Exception {
            UUID nodeId = UUID.randomUUID();

            when(relationService.getTargetAssociations(nodeId, "cm:related")).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/nodes/{nodeId}/targets", nodeId)
                    .param("assocType", "cm:related"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

            verify(relationService).getTargetAssociations(nodeId, "cm:related");
        }
    }

    @Nested
    @DisplayName("POST /nodes/{id}/targets")
    class CreateTarget {

        @Test
        @DisplayName("creates peer association and returns 201")
        void createsTarget() throws Exception {
            UUID nodeId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            DocumentRelation rel = peerRelation(nodeId, "cm:references");

            when(relationService.createPeerAssociation(nodeId, targetId, "cm:references")).thenReturn(rel);

            mockMvc.perform(post("/api/v1/nodes/{nodeId}/targets", nodeId)
                    .param("targetId", targetId.toString())
                    .param("assocType", "cm:references"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relationType").value("cm:references"));
        }
    }

    @Nested
    @DisplayName("DELETE /nodes/{id}/targets/{targetId}")
    class RemoveTarget {

        @Test
        @DisplayName("removes peer association and returns 204")
        void removesTarget() throws Exception {
            UUID nodeId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();

            mockMvc.perform(delete("/api/v1/nodes/{nodeId}/targets/{targetId}", nodeId, targetId))
                .andExpect(status().isNoContent());

            verify(relationService).removePeerAssociation(nodeId, targetId);
        }
    }

    @Nested
    @DisplayName("POST /nodes/{id}/secondary-children")
    class AddSecondaryChild {

        @Test
        @DisplayName("adds secondary child and returns 201")
        void addsSecondaryChild() throws Exception {
            UUID parentId = UUID.randomUUID();
            UUID childId = UUID.randomUUID();
            DocumentRelation rel = secondaryRelation(parentId);

            when(relationService.addSecondaryChild(parentId, childId)).thenReturn(rel);

            mockMvc.perform(post("/api/v1/nodes/{nodeId}/secondary-children", parentId)
                    .param("childId", childId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relationType").value("cm:contains"));
        }
    }

    @Nested
    @DisplayName("GET /nodes/{id}/secondary-children")
    class GetSecondaryChildren {

        @Test
        @DisplayName("returns secondary children as edge DTOs")
        void returnsChildren() throws Exception {
            UUID parentId = UUID.randomUUID();
            DocumentRelation rel = secondaryRelation(parentId);

            when(relationService.getSecondaryChildren(parentId)).thenReturn(List.of(rel));

            mockMvc.perform(get("/api/v1/nodes/{nodeId}/secondary-children", parentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        }
    }

    @Nested
    @DisplayName("DELETE /nodes/{id}/secondary-children/{childId}")
    class RemoveSecondaryChild {

        @Test
        @DisplayName("removes secondary child and returns 204")
        void removesChild() throws Exception {
            UUID parentId = UUID.randomUUID();
            UUID childId = UUID.randomUUID();

            mockMvc.perform(delete("/api/v1/nodes/{nodeId}/secondary-children/{childId}", parentId, childId))
                .andExpect(status().isNoContent());

            verify(relationService).removeSecondaryChild(parentId, childId);
        }
    }

    // ================================================================= helpers

    private DocumentRelation peerRelation(UUID sourceId, String assocType) {
        Document source = doc(sourceId, "source.pdf");
        Document target = doc(UUID.randomUUID(), "target.pdf");
        Folder parent = new Folder();
        parent.setId(UUID.randomUUID());
        parent.setName("root");
        parent.setPath("/root");
        source.setParent(parent);
        target.setParent(parent);

        DocumentRelation rel = new DocumentRelation();
        rel.setId(UUID.randomUUID());
        rel.setSource(source);
        rel.setTarget(target);
        rel.setRelationType(assocType.toUpperCase());
        rel.setAssocType(assocType);
        rel.setDirection(AssocDirection.PEER);
        return rel;
    }

    private DocumentRelation secondaryRelation(UUID parentId) {
        Document parent = doc(parentId, "parent.pdf");
        Document child = doc(UUID.randomUUID(), "child.pdf");
        Folder folder = new Folder();
        folder.setId(UUID.randomUUID());
        folder.setName("root");
        folder.setPath("/root");
        parent.setParent(folder);
        child.setParent(folder);

        DocumentRelation rel = new DocumentRelation();
        rel.setId(UUID.randomUUID());
        rel.setSource(parent);
        rel.setTarget(child);
        rel.setRelationType("CM:CONTAINS");
        rel.setAssocType("cm:contains");
        rel.setDirection(AssocDirection.CHILD_SECONDARY);
        return rel;
    }

    private Document doc(UUID id, String name) {
        Document d = new Document();
        d.setId(id);
        d.setName(name);
        d.setMimeType("application/pdf");
        d.setPath("/" + name);
        return d;
    }
}
