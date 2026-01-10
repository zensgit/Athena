package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.PermissionRepository;
import com.ecm.core.search.FacetedSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class FolderServiceContentsAclTest {

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private SecurityService securityService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private FacetedSearchService searchService;

    private FolderService folderService;

    @BeforeEach
    void setUp() {
        folderService = new FolderService(
            folderRepository,
            nodeRepository,
            permissionRepository,
            securityService,
            eventPublisher,
            searchService,
            new ObjectMapper()
        );
    }

    @Test
    @DisplayName("Filters folder contents before paging when access is denied")
    void filtersFolderContentsBeforePaging() {
        UUID folderId = UUID.randomUUID();
        Folder folder = folder(folderId, "Parent");

        Document denied = document("A-denied");
        Document allowed = document("B-allowed");

        Pageable pageable = PageRequest.of(0, 1, Sort.by("name").ascending());

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(securityService.hasPermission(folder, PermissionType.READ)).thenReturn(true);
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);
        when(nodeRepository.findByParentIdAndDeletedFalse(folderId, pageable.getSort()))
            .thenReturn(List.of(denied, allowed));
        when(securityService.hasPermission(denied, PermissionType.READ)).thenReturn(false);
        when(securityService.hasPermission(allowed, PermissionType.READ)).thenReturn(true);

        Page<Node> result = folderService.getFolderContents(folderId, pageable);

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
