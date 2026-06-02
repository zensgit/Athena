package com.ecm.core.service;

import com.ecm.core.repository.DocumentRelationRepository;
import com.ecm.core.repository.FavoriteRepository;
import com.ecm.core.repository.NodeRelationRepository;
import com.ecm.core.repository.RatingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * The node FKs cleared here (node_relations/document_relations fk_*_source / fk_*_target,
 * and rating fk_rating_node) have NO ON DELETE CASCADE, so leaving any of them would make a
 * permanent node delete throw a constraint violation. This test pins that the cleanup clears
 * BOTH relation directions plus ratings and favorites.
 */
@ExtendWith(MockitoExtension.class)
class NodeDependentCleanupServiceTest {

    @Mock private NodeRelationRepository nodeRelationRepository;
    @Mock private DocumentRelationRepository documentRelationRepository;
    @Mock private RatingRepository ratingRepository;
    @Mock private FavoriteRepository favoriteRepository;

    @InjectMocks private NodeDependentCleanupService service;

    @Test
    void deleteByNodeId_clearsBothRelationDirectionsRatingsAndFavorites() {
        UUID nodeId = UUID.randomUUID();

        service.deleteByNodeId(nodeId);

        // A node can be the source OR the target of an association — both must be cleared,
        // otherwise the FK on the surviving side blocks the node delete.
        verify(nodeRelationRepository).deleteBySourceId(nodeId);
        verify(nodeRelationRepository).deleteByTargetId(nodeId);
        verify(documentRelationRepository).deleteBySourceId(nodeId);
        verify(documentRelationRepository).deleteByTargetId(nodeId);
        verify(ratingRepository).deleteByNodeId(nodeId);
        verify(favoriteRepository).deleteByNodeId(nodeId);

        verifyNoMoreInteractions(
            nodeRelationRepository, documentRelationRepository, ratingRepository, favoriteRepository);
    }
}
