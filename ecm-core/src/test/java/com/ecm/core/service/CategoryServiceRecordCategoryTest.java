package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.exception.IllegalOperationException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceRecordCategoryTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;
    @Mock private RecordsManagementService recordsManagementService;

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService();
        ReflectionTestUtils.setField(service, "categoryRepository", categoryRepository);
        ReflectionTestUtils.setField(service, "nodeRepository", nodeRepository);
        ReflectionTestUtils.setField(service, "securityService", securityService);
        ReflectionTestUtils.setField(service, "tenantWorkspaceScopeService", tenantWorkspaceScopeService);
        ReflectionTestUtils.setField(service, "recordsManagementService", recordsManagementService);
    }

    @Test
    @DisplayName("generic category API rejects category changes on declared records")
    void genericCategoryApiRejectsDeclaredRecord() {
        UUID nodeId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Document document = document(nodeId, "/tenant/doc");
        Category category = generalCategory(categoryId, "General");

        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nodeId, Node.ArchiveStatus.LIVE)).thenReturn(Optional.of(document));
        when(tenantWorkspaceScopeService.isPathVisible("/tenant/doc")).thenReturn(true);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(recordsManagementService.isDeclaredRecord(document)).thenReturn(true);

        IllegalOperationException ex = assertThrows(
            IllegalOperationException.class,
            () -> service.addCategoryToNode(nodeId.toString(), categoryId.toString())
        );

        assertEquals("Use the records management API to add category on declared record 'doc.txt'", ex.getMessage());
        verify(nodeRepository, never()).save(document);
    }

    @Test
    @DisplayName("record categories can only be assigned to declared records")
    void recordCategoriesRequireDeclaredRecord() {
        UUID nodeId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Document document = document(nodeId, "/tenant/doc");
        Category category = recordCategory(categoryId, "Contracts");

        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nodeId, Node.ArchiveStatus.LIVE)).thenReturn(Optional.of(document));
        when(tenantWorkspaceScopeService.isPathVisible("/tenant/doc")).thenReturn(true);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(recordsManagementService.isDeclaredRecord(document)).thenReturn(false);

        IllegalOperationException ex = assertThrows(
            IllegalOperationException.class,
            () -> service.addCategoryToNode(nodeId.toString(), categoryId.toString())
        );

        assertEquals("Record categories can only be assigned to declared records", ex.getMessage());
    }

    @Test
    @DisplayName("generic category create rejects record-category parents")
    void genericCreateRejectsRecordCategoryParent() {
        UUID parentId = UUID.randomUUID();
        Category parent = recordCategory(parentId, "Records Management");

        when(categoryRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(securityService.getCurrentUser()).thenReturn("admin");

        IllegalOperationException ex = assertThrows(
            IllegalOperationException.class,
            () -> service.createCategory("Contracts", "RM", parentId.toString())
        );

        assertEquals("Use the records management API to create record categories", ex.getMessage());
    }

    @Test
    @DisplayName("findNodesByCategory still filters by tenant visibility")
    void findNodesByCategoryStillFiltersTenantVisibility() {
        UUID categoryId = UUID.randomUUID();
        Category category = generalCategory(categoryId, "Finance");
        Document visible = document(UUID.randomUUID(), "/tenant/doc");
        Document hidden = document(UUID.randomUUID(), "/foreign/doc");

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(nodeRepository.findByCategoriesInAndDeletedFalseAndArchiveStatus(anySet(), eq(Node.ArchiveStatus.LIVE)))
            .thenReturn(List.of(visible, hidden));
        when(tenantWorkspaceScopeService.isPathVisible("/tenant/doc")).thenReturn(true);
        when(tenantWorkspaceScopeService.isPathVisible("/foreign/doc")).thenReturn(false);
        when(securityService.hasPermission(visible, com.ecm.core.entity.Permission.PermissionType.READ)).thenReturn(true);

        List<Node> result = service.findNodesByCategory(categoryId.toString(), false);

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

    private Category generalCategory(UUID id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setPurpose(Category.Purpose.GENERAL);
        category.setActive(true);
        return category;
    }

    private Category recordCategory(UUID id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setPurpose(Category.Purpose.RECORD);
        category.setActive(true);
        return category;
    }
}
