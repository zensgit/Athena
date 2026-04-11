package com.ecm.core.cmis;

import com.ecm.core.config.RepositoryIdentityProvider;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.SecurityService;
import com.ecm.core.service.TenantWorkspaceScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CmisQueryEnhancedTest {

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private FolderService folderService;

    @Mock
    private SecurityService securityService;

    @Mock
    private TenantWorkspaceScopeService tenantWorkspaceScopeService;

    private final CmisObjectFactory objectFactory = new CmisObjectFactory(
        new RepositoryIdentityProvider("athena", "athena"));

    private CmisQueryService cmisQueryService;

    @BeforeEach
    void setUp() {
        cmisQueryService = new CmisQueryService(
            nodeRepository, documentRepository, folderService,
            securityService, tenantWorkspaceScopeService, objectFactory);
        lenient().when(securityService.hasPermission(any(Node.class), eq(com.ecm.core.entity.Permission.PermissionType.READ)))
            .thenReturn(true);
        lenient().when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
    }

    // ---- CONTAINS tests ----

    @Test
    @DisplayName("CONTAINS query returns full-text search results")
    void containsQueryReturnsFullTextResults() {
        Document doc1 = buildDocument("/Legal/invoice-001.pdf", "invoice-001.pdf");
        Document doc2 = buildDocument("/Legal/invoice-002.pdf", "invoice-002.pdf");

        when(documentRepository.fullTextSearch(eq("invoice"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(doc1, doc2)));
        when(nodeRepository.findAll(
            any(org.springframework.data.jpa.domain.Specification.class),
            any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(doc1, doc2));

        CmisModels.QueryResponse response = cmisQueryService.query(
            "SELECT * FROM cmis:document WHERE CONTAINS('invoice')", 0, 25);

        assertEquals(2, response.totalNumItems());
        assertEquals(doc1.getId().toString(), response.objects().get(0).objectId());
        assertEquals(doc2.getId().toString(), response.objects().get(1).objectId());
    }

    @Test
    @DisplayName("CONTAINS on cmis:folder returns empty result set gracefully")
    void containsOnFolderReturnsEmpty() {
        // CONTAINS is not meaningful for folders (no text_content),
        // so the service should return an empty result set.
        when(nodeRepository.findAll(
            any(org.springframework.data.jpa.domain.Specification.class),
            any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of());

        CmisModels.QueryResponse response = cmisQueryService.query(
            "SELECT * FROM cmis:folder WHERE CONTAINS('anything')", 0, 25);

        assertEquals(0, response.totalNumItems());
        assertTrue(response.objects().isEmpty());
    }

    // ---- IN_TREE tests ----

    @Test
    @DisplayName("IN_TREE query returns all descendants, not just direct children")
    void inTreeReturnsDescendants() {
        Folder legalFolder = buildFolder("Legal", "/Legal");
        Document child = buildDocument("/Legal/contract.pdf", "contract.pdf");
        Document grandchild = buildDocument("/Legal/Archived/old-contract.pdf", "old-contract.pdf");

        when(folderService.getFolder(legalFolder.getId())).thenReturn(legalFolder);
        when(nodeRepository.findAll(
            any(org.springframework.data.jpa.domain.Specification.class),
            any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(child, grandchild));

        CmisModels.QueryResponse response = cmisQueryService.query(
            "SELECT * FROM cmis:document WHERE IN_TREE('" + legalFolder.getId() + "')",
            0, 25);

        assertEquals(2, response.totalNumItems());
        assertEquals(child.getId().toString(), response.objects().get(0).objectId());
        assertEquals(grandchild.getId().toString(), response.objects().get(1).objectId());
    }

    @Test
    @DisplayName("IN_TREE by path resolves folder and returns descendants")
    void inTreeByPathReturnsDescendants() {
        Folder legalFolder = buildFolder("Legal", "/Legal");
        Document child = buildDocument("/Legal/memo.pdf", "memo.pdf");

        when(folderService.getFolderByPath("/Legal")).thenReturn(legalFolder);
        when(nodeRepository.findAll(
            any(org.springframework.data.jpa.domain.Specification.class),
            any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(child));

        CmisModels.QueryResponse response = cmisQueryService.query(
            "SELECT * FROM cmis:document WHERE IN_TREE('/Legal')", 0, 25);

        assertEquals(1, response.totalNumItems());
        assertEquals("memo.pdf", response.objects().get(0).name());
    }

    // ---- Combination tests ----

    @Test
    @DisplayName("CONTAINS + IN_FOLDER combination works")
    void containsPlusInFolderCombination() {
        Folder folder = buildFolder("Invoices", "/Invoices");
        Document doc = buildDocument("/Invoices/invoice-march.pdf", "invoice-march.pdf");
        doc.setParent(folder);

        when(folderService.getFolderByPath("/Invoices")).thenReturn(folder);
        when(documentRepository.fullTextSearch(eq("march"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(doc)));
        when(nodeRepository.findAll(
            any(org.springframework.data.jpa.domain.Specification.class),
            any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(doc));

        CmisModels.QueryResponse response = cmisQueryService.query(
            "SELECT * FROM cmis:document WHERE CONTAINS('march') AND IN_FOLDER('/Invoices')",
            0, 25);

        assertEquals(1, response.totalNumItems());
        assertEquals("invoice-march.pdf", response.objects().get(0).name());
    }

    @Test
    @DisplayName("IN_TREE + cmis:name LIKE combination works")
    void inTreePlusNameLikeCombination() {
        Folder folder = buildFolder("Reports", "/Reports");
        Document match = buildDocument("/Reports/Q1/annual-report.pdf", "annual-report.pdf");
        Document noMatch = buildDocument("/Reports/Q2/summary.pdf", "summary.pdf");

        when(folderService.getFolderByPath("/Reports")).thenReturn(folder);
        // The repo returns only what matches the JPA spec (IN_TREE + name LIKE)
        when(nodeRepository.findAll(
            any(org.springframework.data.jpa.domain.Specification.class),
            any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(match));

        CmisModels.QueryResponse response = cmisQueryService.query(
            "SELECT * FROM cmis:document WHERE IN_TREE('/Reports') AND cmis:name LIKE '%report%'",
            0, 25);

        assertEquals(1, response.totalNumItems());
        assertEquals("annual-report.pdf", response.objects().get(0).name());
    }

    @Test
    @DisplayName("CONTAINS + IN_TREE combination works")
    void containsPlusInTreeCombination() {
        Folder legalFolder = buildFolder("Legal", "/Legal");
        Document doc = buildDocument("/Legal/Contracts/nda.pdf", "nda.pdf");

        when(folderService.getFolderByPath("/Legal")).thenReturn(legalFolder);
        when(documentRepository.fullTextSearch(eq("contract"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(doc)));
        when(nodeRepository.findAll(
            any(org.springframework.data.jpa.domain.Specification.class),
            any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(doc));

        CmisModels.QueryResponse response = cmisQueryService.query(
            "SELECT * FROM cmis:document WHERE CONTAINS('contract') AND IN_TREE('/Legal')",
            0, 25);

        assertEquals(1, response.totalNumItems());
        assertEquals("nda.pdf", response.objects().get(0).name());
    }

    @Test
    @DisplayName("CONTAINS with no full-text matches returns empty results")
    void containsWithNoMatchesReturnsEmpty() {
        when(documentRepository.fullTextSearch(eq("nonexistent"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        when(nodeRepository.findAll(
            any(org.springframework.data.jpa.domain.Specification.class),
            any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of());

        CmisModels.QueryResponse response = cmisQueryService.query(
            "SELECT * FROM cmis:document WHERE CONTAINS('nonexistent')", 0, 25);

        assertEquals(0, response.totalNumItems());
        assertTrue(response.objects().isEmpty());
        assertFalse(response.hasMoreItems());
    }

    // ---- Helpers ----

    private Folder buildFolder(String name, String path) {
        Folder folder = new Folder();
        folder.setId(UUID.randomUUID());
        folder.setName(name);
        folder.setPath(path);
        folder.setCreatedBy("admin");
        folder.setCreatedDate(LocalDateTime.now());
        folder.setLastModifiedBy("admin");
        folder.setLastModifiedDate(LocalDateTime.now());
        return folder;
    }

    private Document buildDocument(String path, String name) {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName(name);
        document.setPath(path);
        document.setMimeType("application/pdf");
        document.setFileSize(4096L);
        document.setVersionLabel("1.0");
        document.setCreatedBy("alice");
        document.setCreatedDate(LocalDateTime.now());
        document.setLastModifiedBy("alice");
        document.setLastModifiedDate(LocalDateTime.now());
        String parentPath = deriveParentPath(path);
        Folder parent = buildFolder("parent", parentPath != null ? parentPath : "/");
        document.setParent(parent);
        return document;
    }

    private String deriveParentPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "/";
        }
        return path.substring(0, lastSlash);
    }
}
