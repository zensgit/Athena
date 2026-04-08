package com.ecm.core.service;

import com.ecm.core.entity.Favorite;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.repository.FavoriteRepository;
import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final NodeRepository nodeRepository;
    private final SecurityService securityService;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;

    /**
     * Add a node to favorites for the current user.
     */
    @Transactional
    public Favorite addFavorite(UUID nodeId) {
        String userId = securityService.getCurrentUser();
        return addFavoriteForUser(userId, nodeId);
    }

    /**
     * Remove a node from favorites.
     */
    @Transactional
    public void removeFavorite(UUID nodeId) {
        String userId = securityService.getCurrentUser();
        removeFavoriteForUser(userId, nodeId);
    }

    /**
     * Get favorites for the current user.
     */
    @Transactional(readOnly = true)
    public Page<Favorite> getMyFavorites(Pageable pageable) {
        String userId = securityService.getCurrentUser();
        return getFavoritesForUser(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Favorite> getFavoritesForUser(String userId, Pageable pageable) {
        List<Favorite> visibleFavorites = favoriteRepository.findByUserId(userId).stream()
            .filter(this::isFavoriteVisible)
            .sorted(Comparator.comparing(Favorite::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(visibleFavorites);
        }
        int start = (int) pageable.getOffset();
        if (start >= visibleFavorites.size()) {
            return new PageImpl<>(List.of(), pageable, visibleFavorites.size());
        }
        int end = Math.min(start + pageable.getPageSize(), visibleFavorites.size());
        return new PageImpl<>(visibleFavorites.subList(start, end), pageable, visibleFavorites.size());
    }

    @Transactional
    public Favorite addFavoriteForUser(String userId, UUID nodeId) {
        Node node = loadNode(nodeId);

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

    @Transactional
    public Favorite addFavoriteSiteForUser(String userId, UUID nodeId) {
        Node node = loadNode(nodeId);
        if (!(node instanceof Folder)) {
            throw new IllegalArgumentException("Favorite site target must be a folder");
        }

        if (favoriteRepository.existsByUserIdAndNodeId(userId, nodeId)) {
            throw new IllegalStateException("Node is already a favorite");
        }

        Favorite favorite = Favorite.builder()
            .userId(userId)
            .node(node)
            .build();

        log.info("User {} added folder {} to favorite sites", userId, nodeId);
        return favoriteRepository.save(favorite);
    }

    @Transactional
    public void removeFavoriteForUser(String userId, UUID nodeId) {
        Favorite favorite = getFavoriteForUser(userId, nodeId);
        favoriteRepository.delete(favorite);
        log.info("User {} removed node {} from favorites", userId, nodeId);
    }

    @Transactional(readOnly = true)
    public Favorite getFavoriteForUser(String userId, UUID nodeId) {
        return favoriteRepository.findByUserIdAndNodeId(userId, nodeId)
            .filter(this::isFavoriteVisible)
            .orElseThrow(() -> new IllegalArgumentException("Favorite not found"));
    }

    private Node loadNode(UUID nodeId) {
        return nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nodeId, Node.ArchiveStatus.LIVE)
            .filter(node -> tenantWorkspaceScopeService.isPathVisible(node.getPath()))
            .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeId));
    }

    /**
     * Check if a node is favorited by the current user.
     */
    @Transactional(readOnly = true)
    public boolean isFavorite(UUID nodeId) {
        String userId = securityService.getCurrentUser();
        return favoriteRepository.findByUserIdAndNodeId(userId, nodeId)
            .filter(this::isFavoriteVisible)
            .isPresent();
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
            .filter(this::isFavoriteVisible)
            .map(fav -> fav.getNode().getId())
            .collect(java.util.stream.Collectors.toSet());
    }

    private boolean isFavoriteVisible(Favorite favorite) {
        return favorite != null && isNodeVisible(favorite.getNode());
    }

    private boolean isNodeVisible(Node node) {
        return node != null
            && !node.isDeleted()
            && node.getArchiveStatus() == Node.ArchiveStatus.LIVE
            && tenantWorkspaceScopeService.isPathVisible(node.getPath());
    }
}
