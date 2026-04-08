package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Favorite;
import com.ecm.core.repository.FavoriteRepository;
import com.ecm.core.repository.NodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock private FavoriteRepository favoriteRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;

    private FavoriteService service;

    @BeforeEach
    void setUp() {
        service = new FavoriteService(favoriteRepository, nodeRepository, securityService, tenantWorkspaceScopeService);
    }

    @Test
    @DisplayName("addFavorite hides nodes outside current tenant workspace")
    void addFavoriteHidesForeignTenantNode() {
        UUID nodeId = UUID.randomUUID();
        Document hidden = document(nodeId, "/foreign/doc");
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nodeId, com.ecm.core.entity.Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(hidden));
        when(tenantWorkspaceScopeService.isPathVisible("/foreign/doc")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.addFavoriteForUser("alice", nodeId));
        verify(favoriteRepository, never()).save(any());
    }

    @Test
    @DisplayName("getFavoritesForUser filters hidden favorites")
    void getFavoritesForUserFiltersHiddenFavorites() {
        Favorite visible = favorite("alice", document(UUID.randomUUID(), "/tenant/doc"));
        Favorite hidden = favorite("alice", document(UUID.randomUUID(), "/foreign/doc"));
        when(favoriteRepository.findByUserId("alice")).thenReturn(List.of(hidden, visible));
        when(tenantWorkspaceScopeService.isPathVisible("/tenant/doc")).thenReturn(true);
        when(tenantWorkspaceScopeService.isPathVisible("/foreign/doc")).thenReturn(false);

        Page<Favorite> result = service.getFavoritesForUser("alice", PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals("/tenant/doc", result.getContent().get(0).getNode().getPath());
    }

    @Test
    @DisplayName("getFavoriteForUser hides favorite for foreign tenant node")
    void getFavoriteForUserHidesForeignTenantFavorite() {
        UUID nodeId = UUID.randomUUID();
        Favorite hidden = favorite("alice", document(nodeId, "/foreign/doc"));
        when(favoriteRepository.findByUserIdAndNodeId("alice", nodeId)).thenReturn(Optional.of(hidden));
        when(tenantWorkspaceScopeService.isPathVisible("/foreign/doc")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.getFavoriteForUser("alice", nodeId));
    }

    private Favorite favorite(String userId, Document node) {
        Favorite favorite = new Favorite();
        favorite.setId(UUID.randomUUID());
        favorite.setUserId(userId);
        favorite.setNode(node);
        favorite.setCreatedAt(LocalDateTime.now());
        return favorite;
    }

    private Document document(UUID id, String path) {
        Document document = new Document();
        document.setId(id);
        document.setName("doc.txt");
        document.setPath(path);
        document.setArchiveStatus(com.ecm.core.entity.Node.ArchiveStatus.LIVE);
        document.setDeleted(false);
        return document;
    }
}
