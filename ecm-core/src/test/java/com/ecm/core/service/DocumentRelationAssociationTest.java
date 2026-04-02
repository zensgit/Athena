package com.ecm.core.service;

import com.ecm.core.entity.AssocDirection;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.DocumentRelation;
import com.ecm.core.repository.DocumentRelationRepository;
import com.ecm.core.repository.NodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentRelationAssociationTest {

    @Mock private DocumentRelationRepository relationRepo;
    @Mock private NodeRepository nodeRepository;

    private DocumentRelationService service;

    @BeforeEach
    void setUp() {
        service = new DocumentRelationService(relationRepo, nodeRepository);
    }

    // ================================================================= peer associations

    @Nested
    @DisplayName("peer associations")
    class PeerAssociations {

        @Test
        @DisplayName("createPeerAssociation saves with PEER direction and assocType")
        void createsPeer() {
            UUID srcId = UUID.randomUUID();
            UUID tgtId = UUID.randomUUID();
            Document src = document(srcId, "a.pdf");
            Document tgt = document(tgtId, "b.pdf");

            when(nodeRepository.findById(srcId)).thenReturn(Optional.of(src));
            when(nodeRepository.findById(tgtId)).thenReturn(Optional.of(tgt));
            when(relationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DocumentRelation rel = service.createPeerAssociation(srcId, tgtId, "cm:references");

            assertEquals(AssocDirection.PEER, rel.getDirection());
            assertEquals("cm:references", rel.getAssocType());
            assertEquals(src, rel.getSource());
            assertEquals(tgt, rel.getTarget());
        }

        @Test
        @DisplayName("rejects self-association")
        void rejectsSelfAssoc() {
            UUID id = UUID.randomUUID();
            Document doc = document(id, "a.pdf");
            when(nodeRepository.findById(id)).thenReturn(Optional.of(doc));

            assertThrows(IllegalArgumentException.class,
                () -> service.createPeerAssociation(id, id, "cm:references"));
        }

        @Test
        @DisplayName("getTargetAssociations filters by assocType")
        void filtersTargetsByAssocType() {
            UUID nodeId = UUID.randomUUID();

            DocumentRelation r1 = new DocumentRelation();
            r1.setAssocType("cm:references");
            DocumentRelation r2 = new DocumentRelation();
            r2.setAssocType("cm:related");

            when(relationRepo.findBySourceIdAndDirection(nodeId, AssocDirection.PEER))
                .thenReturn(List.of(r1, r2));

            List<DocumentRelation> result = service.getTargetAssociations(nodeId, "cm:references");
            assertEquals(1, result.size());
            assertEquals("cm:references", result.get(0).getAssocType());
        }

        @Test
        @DisplayName("getTargetAssociations returns all when assocType is null")
        void returnsAllTargets() {
            UUID nodeId = UUID.randomUUID();
            when(relationRepo.findBySourceIdAndDirection(nodeId, AssocDirection.PEER))
                .thenReturn(List.of(new DocumentRelation(), new DocumentRelation()));

            assertEquals(2, service.getTargetAssociations(nodeId, null).size());
        }

        @Test
        @DisplayName("getSourceAssociations returns incoming peer associations")
        void returnsSources() {
            UUID nodeId = UUID.randomUUID();
            when(relationRepo.findByTargetIdAndDirection(nodeId, AssocDirection.PEER))
                .thenReturn(List.of(new DocumentRelation()));

            assertEquals(1, service.getSourceAssociations(nodeId, null).size());
        }

        @Test
        @DisplayName("removePeerAssociation delegates to repo")
        void removesPeer() {
            UUID src = UUID.randomUUID();
            UUID tgt = UUID.randomUUID();
            service.removePeerAssociation(src, tgt);
            verify(relationRepo).deleteBySourceIdAndTargetId(src, tgt);
        }
    }

    // ================================================================= secondary children

    @Nested
    @DisplayName("secondary children")
    class SecondaryChildren {

        @Test
        @DisplayName("addSecondaryChild saves with CHILD_SECONDARY direction")
        void addsSecondaryChild() {
            UUID parentId = UUID.randomUUID();
            UUID childId = UUID.randomUUID();
            Document parent = document(parentId, "folder");
            Document child = document(childId, "doc.pdf");

            when(nodeRepository.findById(parentId)).thenReturn(Optional.of(parent));
            when(nodeRepository.findById(childId)).thenReturn(Optional.of(child));
            when(relationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DocumentRelation rel = service.addSecondaryChild(parentId, childId);

            assertEquals(AssocDirection.CHILD_SECONDARY, rel.getDirection());
            assertEquals("cm:contains", rel.getAssocType());
        }

        @Test
        @DisplayName("getSecondaryChildren returns children with CHILD_SECONDARY direction")
        void getsSecondaryChildren() {
            UUID parentId = UUID.randomUUID();
            when(relationRepo.findBySourceIdAndDirection(parentId, AssocDirection.CHILD_SECONDARY))
                .thenReturn(List.of(new DocumentRelation()));

            assertEquals(1, service.getSecondaryChildren(parentId).size());
        }

        @Test
        @DisplayName("getSecondaryParents returns parents with CHILD_SECONDARY direction")
        void getsSecondaryParents() {
            UUID childId = UUID.randomUUID();
            when(relationRepo.findByTargetIdAndDirection(childId, AssocDirection.CHILD_SECONDARY))
                .thenReturn(List.of(new DocumentRelation()));

            assertEquals(1, service.getSecondaryParents(childId).size());
        }

        @Test
        @DisplayName("removeSecondaryChild delegates to repo")
        void removesSecondary() {
            UUID p = UUID.randomUUID();
            UUID c = UUID.randomUUID();
            service.removeSecondaryChild(p, c);
            verify(relationRepo).deleteBySourceIdAndTargetId(p, c);
        }
    }

    // ================================================================= helpers

    private Document document(UUID id, String name) {
        Document d = new Document();
        d.setId(id);
        d.setName(name);
        d.setMimeType("application/pdf");
        d.setPath("/" + name);
        return d;
    }
}
