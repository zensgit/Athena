package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.PermissionRepository;
import com.ecm.core.search.FacetedSearchService;
import com.ecm.core.search.SearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderServiceSmartFolderTest {

    @Mock private FolderRepository folderRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private SecurityService securityService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private FacetedSearchService searchService;

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
    @DisplayName("Smart folder creation requires non-empty query criteria")
    void createSmartFolderRequiresCriteria() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> folderService.createFolder(new FolderService.CreateFolderRequest(
                "smart",
                null,
                null,
                Folder.FolderType.GENERAL,
                null,
                null,
                null,
                null,
                true,
                true,
                Map.of()
            ))
        );

        assertEquals("Smart folders require non-empty queryCriteria", ex.getMessage());
    }

    @Test
    @DisplayName("Physical children cannot be created under smart folders")
    void createFolderRejectsSmartParent() {
        UUID parentId = UUID.randomUUID();
        Folder smartParent = folder(parentId, "smart-parent");
        smartParent.setSmart(true);

        when(folderRepository.findById(parentId)).thenReturn(Optional.of(smartParent));
        when(securityService.hasPermission(smartParent, PermissionType.CREATE_CHILDREN)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> folderService.createFolder(new FolderService.CreateFolderRequest(
                "child",
                null,
                parentId,
                Folder.FolderType.GENERAL,
                null,
                null,
                null,
                null,
                true,
                false,
                null
            ))
        );

        assertEquals("Smart folders cannot contain physical child nodes: smart-parent", ex.getMessage());
    }

    @Test
    @DisplayName("Smart folder content order follows search result order")
    void getFolderContentsPreservesSearchOrder() {
        UUID folderId = UUID.randomUUID();
        Folder smartFolder = folder(folderId, "smart");
        smartFolder.setSmart(true);
        smartFolder.setQueryCriteria(Map.of("query", "invoice"));

        Document first = document(UUID.randomUUID(), "a.pdf");
        Document second = document(UUID.randomUUID(), "b.pdf");

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(smartFolder));
        when(securityService.hasPermission(smartFolder, PermissionType.READ)).thenReturn(true);
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);
        when(searchService.search(any())).thenReturn(FacetedSearchService.FacetedSearchResponse.builder()
            .results(new PageImpl<>(List.of(
                SearchResult.builder().id(second.getId().toString()).name(second.getName()).build(),
                SearchResult.builder().id(first.getId().toString()).name(first.getName()).build()
            ), PageRequest.of(0, 2), 2))
            .totalHits(2)
            .build());
        when(nodeRepository.findAllById(List.of(second.getId(), first.getId()))).thenReturn(List.of(first, second));

        Page<Node> result = folderService.getFolderContents(folderId, PageRequest.of(0, 20));

        assertEquals(2, result.getTotalElements());
        assertEquals(List.of(second.getId(), first.getId()), result.getContent().stream().map(Node::getId).toList());
    }

    @Test
    @DisplayName("Cannot convert non-empty physical folder into smart folder")
    void updateFolderRejectsSmartConversionWhenFolderHasChildren() {
        UUID folderId = UUID.randomUUID();
        Folder folder = folder(folderId, "workspace");

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(securityService.hasPermission(folder, PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(folder, PermissionType.WRITE)).thenReturn(true);
        when(nodeRepository.countByParentId(folderId)).thenReturn(1L);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> folderService.updateFolder(folderId, new FolderService.UpdateFolderRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                Map.of("query", "contract")
            ))
        );

        assertEquals("Cannot convert a non-empty folder into a smart folder", ex.getMessage());
    }

    @Test
    @DisplayName("Smart folders reject new physical items in capacity checks")
    void canAcceptItemsReturnsFalseForSmartFolder() {
        UUID folderId = UUID.randomUUID();
        Folder smartFolder = folder(folderId, "smart");
        smartFolder.setSmart(true);
        smartFolder.setQueryCriteria(Map.of("query", "invoice"));

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(smartFolder));
        when(securityService.hasPermission(smartFolder, PermissionType.READ)).thenReturn(true);

        assertFalse(folderService.canAcceptItems(folderId, 1));
    }

    private Folder folder(UUID id, String name) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setPath("/" + name);
        folder.setDeleted(false);
        folder.setArchiveStatus(Node.ArchiveStatus.LIVE);
        folder.setCreatedDate(LocalDateTime.now());
        return folder;
    }

    private Document document(UUID id, String name) {
        Document document = new Document();
        document.setId(id);
        document.setName(name);
        document.setPath("/docs/" + name);
        document.setDeleted(false);
        document.setArchiveStatus(Node.ArchiveStatus.LIVE);
        return document;
    }
}
