package com.ecm.core.service;

import com.ecm.core.entity.Favorite;
import com.ecm.core.entity.Node;
import com.ecm.core.repository.FavoriteRepository;
import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final NodeRepository nodeRepository;
    private final SecurityService securityService;

    /**
     * Add a node to favorites for the current user.
     */
    @Transactional
    public Favorite addFavorite(UUID nodeId) {
        String userId = securityService.getCurrentUser();
        
        Node node = nodeRepository.findById(nodeId)
            .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeId));

        if (favoriteRepository.existsByUserIdAndNodeId(userId, nodeId)) {
            throw new IllegalStateException("Node is already a favorite");
        }

        Favorite favorite = Favorite.builder()
            .userId(userId)
            .node(node)
            .build();

        log.info("User {} added node {} to favorites", userId, nodeId);
        return favoriteRepository.save(favorite);
    }

    /**
     * Remove a node from favorites.
     */
    @Transactional
    public void removeFavorite(UUID nodeId) {
        String userId = securityService.getCurrentUser();
        
        if (!favoriteRepository.existsByUserIdAndNodeId(userId, nodeId)) {
            throw new IllegalArgumentException("Favorite not found");
        }

        favoriteRepository.deleteByUserIdAndNodeId(userId, nodeId);
        log.info("User {} removed node {} from favorites", userId, nodeId);
    }

    /**
     * Get favorites for the current user.
     */
    @Transactional(readOnly = true)
    public Page<Favorite> getMyFavorites(Pageable pageable) {
        String userId = securityService.getCurrentUser();
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Check if a node is favorited by the current user.
     */
    @Transactional(readOnly = true)
    public boolean isFavorite(UUID nodeId) {
        String userId = securityService.getCurrentUser();
        return favoriteRepository.existsByUserIdAndNodeId(userId, nodeId);
    }

    /**
     * Resolve which nodes are favorited by the current user (batch).
     */
    @Transactional(readOnly = true)
    public Set<UUID> getFavoriteNodeIds(Collection<UUID> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return Set.of();
        }
        String userId = securityService.getCurrentUser();
        return favoriteRepository.findByUserIdAndNodeIdIn(userId, nodeIds).stream()
            .map(fav -> fav.getNode().getId())
            .collect(java.util.stream.Collectors.toSet());
    }
}
