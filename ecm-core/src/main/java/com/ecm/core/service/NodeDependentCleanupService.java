package com.ecm.core.service;

import com.ecm.core.repository.DocumentRelationRepository;
import com.ecm.core.repository.FavoriteRepository;
import com.ecm.core.repository.NodeRelationRepository;
import com.ecm.core.repository.RatingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Clears node-scoped dependent rows whose foreign keys to {@code nodes(id)} have no
 * {@code ON DELETE CASCADE}, before a node is permanently deleted. Without this, permanently
 * deleting a node that is the source/target of a relation — or that has ratings — fails with an
 * FK constraint violation (the delete throws), and favorites (bare {@code node_id}) are orphaned.
 *
 * Deliberately NOT handled here (covered elsewhere or retained by design):
 * <ul>
 *   <li>share-links — {@code ShareLinkNodeCleanupService}</li>
 *   <li>permissions, comments, versions — JPA {@code orphanRemoval} on Node/Document</li>
 *   <li>tags / categories / aspects — JPA join-table / element-collection cleanup</li>
 *   <li>localized_content — FK {@code ON DELETE CASCADE} (DB handles it)</li>
 *   <li>audit logs, follow subscriptions — retained by design</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class NodeDependentCleanupService {

    private final NodeRelationRepository nodeRelationRepository;
    private final DocumentRelationRepository documentRelationRepository;
    private final RatingRepository ratingRepository;
    private final FavoriteRepository favoriteRepository;

    public void deleteByNodeId(UUID nodeId) {
        // Relations: the node may be either the source or the target of an association.
        nodeRelationRepository.deleteBySourceId(nodeId);
        nodeRelationRepository.deleteByTargetId(nodeId);
        documentRelationRepository.deleteBySourceId(nodeId);
        documentRelationRepository.deleteByTargetId(nodeId);
        // Ratings (FK fk_rating_node, no cascade) and favorites (bare node_id).
        ratingRepository.deleteByNodeId(nodeId);
        favoriteRepository.deleteByNodeId(nodeId);
    }
}
