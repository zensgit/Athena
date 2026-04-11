package com.ecm.core.cmis;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.NodeRelation;
import com.ecm.core.service.NodeRelationService;
import com.ecm.core.service.NodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CmisRelationshipServiceTest {

    @Mock
    private NodeRelationService nodeRelationService;

    @Mock
    private NodeService nodeService;

    @Mock
    private CmisObjectFactory objectFactory;

    private CmisRelationshipService cmisRelationshipService;

    private final UUID nodeA = UUID.randomUUID();
    private final UUID nodeB = UUID.randomUUID();
    private final UUID nodeC = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cmisRelationshipService = new CmisRelationshipService(nodeRelationService, nodeService, objectFactory);
    }

    @Test
    @DisplayName("getObjectRelationships returns outgoing relations for source direction")
    void getObjectRelationshipsSourceDirection() {
        NodeRelation outgoing = buildRelation(nodeA, nodeB, "REFERENCES");
        when(nodeRelationService.getRelations(nodeA)).thenReturn(List.of(outgoing));

        CmisModels.RelationshipsResponse response =
            cmisRelationshipService.getObjectRelationships(nodeA.toString(), "source", null);

        assertEquals(nodeA.toString(), response.objectId());
        assertEquals(1, response.relationships().size());
        assertEquals(nodeB.toString(), response.relationships().get(0).targetId());
    }

    @Test
    @DisplayName("getObjectRelationships returns incoming relations for target direction")
    void getObjectRelationshipsTargetDirection() {
        NodeRelation incoming = buildRelation(nodeC, nodeA, "REFERENCES");
        when(nodeRelationService.getIncomingRelations(nodeA)).thenReturn(List.of(incoming));

        CmisModels.RelationshipsResponse response =
            cmisRelationshipService.getObjectRelationships(nodeA.toString(), "target", null);

        assertEquals(1, response.relationships().size());
        assertEquals(nodeC.toString(), response.relationships().get(0).sourceId());
    }

    @Test
    @DisplayName("getObjectRelationships returns both directions for either")
    void getObjectRelationshipsEitherDirection() {
        NodeRelation outgoing = buildRelation(nodeA, nodeB, "REFERENCES");
        NodeRelation incoming = buildRelation(nodeC, nodeA, "RELATED");
        when(nodeRelationService.getRelations(nodeA)).thenReturn(List.of(outgoing));
        when(nodeRelationService.getIncomingRelations(nodeA)).thenReturn(List.of(incoming));

        CmisModels.RelationshipsResponse response =
            cmisRelationshipService.getObjectRelationships(nodeA.toString(), "either", null);

        assertEquals(2, response.relationships().size());
    }

    @Test
    @DisplayName("getObjectRelationships filters by typeId")
    void getObjectRelationshipsFiltersByTypeId() {
        NodeRelation ref = buildRelation(nodeA, nodeB, "REFERENCES");
        NodeRelation rel = buildRelation(nodeA, nodeC, "RELATED");
        when(nodeRelationService.getRelations(nodeA)).thenReturn(List.of(ref, rel));

        CmisModels.RelationshipsResponse response =
            cmisRelationshipService.getObjectRelationships(nodeA.toString(), "source", "REFERENCES");

        assertEquals(1, response.relationships().size());
        assertEquals("REFERENCES", response.relationships().get(0).relationshipType());
    }

    @Test
    @DisplayName("createRelationship delegates to NodeRelationService")
    void createRelationshipDelegatesToService() {
        NodeRelation created = buildRelation(nodeA, nodeB, "REFERENCES");
        when(nodeRelationService.createRelation(nodeA, nodeB, "REFERENCES")).thenReturn(created);

        CmisModels.RelationshipEntry entry =
            cmisRelationshipService.createRelationship(nodeA.toString(), nodeB.toString(), "REFERENCES");

        assertEquals(nodeA.toString(), entry.sourceId());
        assertEquals(nodeB.toString(), entry.targetId());
        assertEquals("REFERENCES", entry.relationshipType());
        verify(nodeRelationService).createRelation(nodeA, nodeB, "REFERENCES");
    }

    @Test
    @DisplayName("deleteRelationship delegates to NodeRelationService")
    void deleteRelationshipDelegatesToService() {
        cmisRelationshipService.deleteRelationship(nodeA.toString(), nodeB.toString(), "REFERENCES");

        verify(nodeRelationService).deleteRelation(nodeA, nodeB, "REFERENCES");
    }

    @Test
    @DisplayName("Version-specific objectIds are resolved to the live node ids")
    void versionSpecificObjectIdsResolveToLiveNodeIds() {
        NodeRelation created = buildRelation(nodeA, nodeB, "REFERENCES");
        when(nodeRelationService.createRelation(nodeA, nodeB, "REFERENCES")).thenReturn(created);

        CmisModels.RelationshipEntry entry =
            cmisRelationshipService.createRelationship(nodeA + ";v2.0", nodeB + ";v1.0", "REFERENCES");

        assertEquals(nodeA.toString(), entry.sourceId());
        assertEquals(nodeB.toString(), entry.targetId());
        verify(nodeRelationService).createRelation(nodeA, nodeB, "REFERENCES");
    }

    private NodeRelation buildRelation(UUID sourceId, UUID targetId, String relationType) {
        Folder source = new Folder();
        source.setId(sourceId);
        source.setName("source-" + sourceId);

        Folder target = new Folder();
        target.setId(targetId);
        target.setName("target-" + targetId);

        NodeRelation relation = new NodeRelation();
        relation.setId(UUID.randomUUID());
        relation.setSource(source);
        relation.setTarget(target);
        relation.setRelationType(relationType);
        relation.setCreatedDate(LocalDateTime.of(2026, 1, 15, 10, 30));
        return relation;
    }
}
