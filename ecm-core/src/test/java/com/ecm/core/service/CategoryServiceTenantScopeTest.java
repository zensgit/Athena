package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.exception.NodeNotFoundException;
import com.ecm.core.model.Category;
import com.ecm.core.repository.CategoryRepository;
import com.ecm.core.repository.NodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTenantScopeTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService();
        ReflectionTestUtils.setField(service, "categoryRepository", categoryRepository);
        ReflectionTestUtils.setField(service, "nodeRepository", nodeRepository);
        ReflectionTestUtils.setField(service, "securityService", securityService);
        ReflectionTestUtils.setField(service, "tenantWorkspaceScopeService", tenantWorkspaceScopeService);
    }

    @Test
    @DisplayName("addCategoryToNode hides nodes outside current tenant workspace")
    void addCategoryToNodeHidesForeignTenantNode() {
        UUID nodeId = UUID.randomUUID();
        Document hidden = document(nodeId, "/foreign/doc");
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nodeId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(hidden));
        when(tenantWorkspaceScopeService.isPathVisible("/foreign/doc")).thenReturn(false);

        assertThrows(NodeNotFoundException.class, () -> service.addCategoryToNode(nodeId.toString(), UUID.randomUUID().toString()));
    }

    @Test
    @DisplayName("findNodesByCategory filters hidden tenant nodes")
    void findNodesByCategoryFiltersHiddenNodes() {
        UUID categoryId = UUID.randomUUID();
        Category category = new Category();
        category.setId(categoryId);
        category.setName("Finance");
        Document visible = document(UUID.randomUUID(), "/tenant/doc");
        Document hidden = document(UUID.randomUUID(), "/foreign/doc");
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(nodeRepository.findByCategoriesInAndDeletedFalseAndArchiveStatus(anySet(), eq(Node.ArchiveStatus.LIVE)))
            .thenReturn(List.of(visible, hidden));
        when(tenantWorkspaceScopeService.isPathVisible("/tenant/doc")).thenReturn(true);
        when(tenantWorkspaceScopeService.isPathVisible("/foreign/doc")).thenReturn(false);
        when(securityService.hasPermission(visible, com.ecm.core.entity.Permission.PermissionType.READ)).thenReturn(true);

        List<com.ecm.core.entity.Node> result = service.findNodesByCategory(categoryId.toString(), false);

        assertEquals(1, result.size());
        assertEquals("/tenant/doc", result.get(0).getPath());
    }

    private Document document(UUID id, String path) {
        Document document = new Document();
        document.setId(id);
        document.setName("doc.txt");
        document.setPath(path);
        document.setArchiveStatus(Node.ArchiveStatus.LIVE);
        document.setDeleted(false);
        return document;
    }
}
