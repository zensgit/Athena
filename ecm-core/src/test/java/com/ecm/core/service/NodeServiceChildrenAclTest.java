package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.CorrespondentRepository;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.PermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeServiceChildrenAclTest {

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private CorrespondentRepository correspondentRepository;

    @Mock
    private SecurityService securityService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private NodeService nodeService;

    @BeforeEach
    void setUp() {
        nodeService = new NodeService(
            nodeRepository,
            folderRepository,
            documentRepository,
            permissionRepository,
            correspondentRepository,
            securityService,
            eventPublisher
        );
    }

    @Test
    @DisplayName("Filters children before paging when access is denied")
    void filtersChildrenBeforePaging() {
        UUID parentId = UUID.randomUUID();
        Folder parent = folder(parentId, "Parent");

        Document denied = document("A-denied");
        Document allowed = document("B-allowed");

        Pageable pageable = PageRequest.of(0, 1, Sort.by("name").ascending());

        when(nodeRepository.findByIdAndDeletedFalse(parentId)).thenReturn(Optional.of(parent));
        when(securityService.hasPermission(parent, PermissionType.READ)).thenReturn(true);
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);
        when(nodeRepository.findByParentIdAndDeletedFalse(parentId, pageable.getSort()))
            .thenReturn(List.of(denied, allowed));
        when(securityService.hasPermission(denied, PermissionType.READ)).thenReturn(false);
        when(securityService.hasPermission(allowed, PermissionType.READ)).thenReturn(true);

        Page<Node> result = nodeService.getChildren(parentId, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals("B-allowed", result.getContent().get(0).getName());
    }

    private static Folder folder(UUID id, String name) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setDeleted(false);
        return folder;
    }

    private static Document document(String name) {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName(name);
        document.setDeleted(false);
        return document;
    }
}
