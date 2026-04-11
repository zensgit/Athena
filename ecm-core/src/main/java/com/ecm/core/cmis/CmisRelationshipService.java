package com.ecm.core.cmis;

import com.ecm.core.entity.NodeRelation;
import com.ecm.core.service.NodeRelationService;
import com.ecm.core.service.NodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CmisRelationshipService {

    private final NodeRelationService nodeRelationService;
    private final NodeService nodeService;
    private final CmisObjectFactory objectFactory;

    /**
     * Retrieve relationships for a CMIS object.
     *
     * @param objectId  the object whose relationships to query
     * @param direction "source" (outgoing), "target" (incoming), or anything else / null for both
     * @param typeId    optional relationship-type filter (case-insensitive)
     */
    public CmisModels.RelationshipsResponse getObjectRelationships(String objectId, String direction, String typeId) {
        UUID nodeId = CmisObjectReference.parse(objectId).nodeId();
        List<NodeRelation> relations;

        if ("source".equals(direction)) {
            relations = nodeRelationService.getRelations(nodeId);
        } else if ("target".equals(direction)) {
            relations = nodeRelationService.getIncomingRelations(nodeId);
        } else {
            // "either" or null — combine both directions
            relations = new ArrayList<>();
            relations.addAll(nodeRelationService.getRelations(nodeId));
            relations.addAll(nodeRelationService.getIncomingRelations(nodeId));
        }

        // Filter by typeId when specified
        if (typeId != null && !typeId.isBlank()) {
            relations = relations.stream()
                .filter(r -> typeId.equalsIgnoreCase(r.getRelationType()))
                .toList();
        }

        List<CmisModels.RelationshipEntry> entries = relations.stream()
            .map(this::toRelationshipEntry)
            .toList();

        return new CmisModels.RelationshipsResponse(objectId.trim(), entries);
    }

    /**
     * Create a CMIS relationship between two objects.
     */
    public CmisModels.RelationshipEntry createRelationship(String sourceId, String targetId, String relationshipType) {
        NodeRelation relation = nodeRelationService.createRelation(
            CmisObjectReference.parse(sourceId).nodeId(),
            CmisObjectReference.parse(targetId).nodeId(),
            relationshipType != null ? relationshipType : "RELATED"
        );
        return toRelationshipEntry(relation);
    }

    /**
     * Delete a CMIS relationship between two objects.
     */
    public void deleteRelationship(String sourceId, String targetId, String relationshipType) {
        nodeRelationService.deleteRelation(
            CmisObjectReference.parse(sourceId).nodeId(),
            CmisObjectReference.parse(targetId).nodeId(),
            relationshipType
        );
    }

    private CmisModels.RelationshipEntry toRelationshipEntry(NodeRelation relation) {
        return new CmisModels.RelationshipEntry(
            relation.getId().toString(),
            relation.getSource().getId().toString(),
            relation.getTarget().getId().toString(),
            relation.getRelationType(),
            relation.getCreatedDate() != null ? relation.getCreatedDate().toString() : null
        );
    }
}
