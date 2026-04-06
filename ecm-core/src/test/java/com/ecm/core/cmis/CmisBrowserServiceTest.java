package com.ecm.core.cmis;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.SecurityService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CmisBrowserServiceTest {

    @Mock
    private NodeService nodeService;

    @Mock
    private FolderService folderService;

    @Mock
    private SecurityService securityService;

    private final CmisTypeManager typeManager = new CmisTypeManager();
    private final CmisObjectFactory objectFactory = new CmisObjectFactory();

    private CmisBrowserService cmisBrowserService;
    private CmisQueryService cmisQueryService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        cmisBrowserService = new CmisBrowserService(nodeService, folderService, typeManager, objectFactory);
        cmisQueryService = new CmisQueryService(nodeRepository, folderService, securityService, objectFactory);
        lenient().when(securityService.hasPermission(any(Node.class), eq(com.ecm.core.entity.Permission.PermissionType.READ)))
            .thenReturn(true);
    }

    @Test
    @DisplayName("Repository info exposes Athena CMIS repository metadata")
    void getRepositoryInfoReturnsStaticMetadata() {
        CmisModels.RepositoryInfo info = cmisBrowserService.getRepositoryInfo();

        assertEquals("athena", info.repositoryId());
        assertEquals("Athena Repository", info.repositoryName());
        assertEquals("1.1", info.cmisVersionSupported());
        assertEquals("root", info.rootFolderId());
        assertTrue(info.capabilities().contains("read"));
    }

    @Test
    @DisplayName("Type children exposes folder and document base types")
    void getTypeChildrenReturnsBaseTypes() {
        CmisModels.TypeChildrenResponse response = cmisBrowserService.getTypeChildren();

        assertEquals(2, response.totalNumItems());
        assertTrue(response.types().stream().anyMatch(type -> "cmis:folder".equals(type.id())));
        assertTrue(response.types().stream().anyMatch(type -> "cmis:document".equals(type.id())));
    }

    @Test
    @DisplayName("Object selector resolves root path to virtual root folder")
    void getObjectByPathReturnsRootObject() {
        CmisModels.ObjectEntry object = cmisBrowserService.getObject(null, "/");

        assertEquals("root", object.objectId());
        assertTrue(object.root());
        assertEquals("cmis:folder", object.baseTypeId());
        assertEquals("/", object.path());
    }

    @Test
    @DisplayName("Object selector maps document fields into CMIS properties")
    void getObjectByIdMapsDocument() {
        Document document = buildDocument();
        when(nodeService.getNode(document.getId())).thenReturn(document);

        CmisModels.ObjectEntry object = cmisBrowserService.getObject(document.getId().toString(), null);

        assertEquals(document.getId().toString(), object.objectId());
        assertEquals("cmis:document", object.baseTypeId());
        assertEquals(document.getMimeType(), object.properties().get("cmis:contentStreamMimeType"));
        assertEquals(document.getFileSize(), object.properties().get("cmis:contentStreamLength"));
        assertFalse(object.allowableActions().isEmpty());
    }

    @Test
    @DisplayName("Children selector returns root folders for virtual root")
    void getChildrenForRootReturnsRootFolders() {
        Folder rootA = buildFolder("Sites", "/Sites");
        Folder rootB = buildFolder("Shared", "/Shared");
        when(folderService.getRootFolders()).thenReturn(List.of(rootA, rootB));

        CmisModels.ChildrenResponse response = cmisBrowserService.getChildren("root", null, 0, 50);

        assertEquals("root", response.parentObjectId());
        assertEquals(2, response.totalNumItems());
        assertEquals(2, response.objects().size());
        assertEquals("Sites", response.objects().get(0).name());
    }

    @Test
    @DisplayName("Children selector returns folder contents for folder object")
    void getChildrenForFolderReturnsMappedChildren() {
        Folder folder = buildFolder("Contracts", "/Sites/contracts");
        folder.setId(UUID.randomUUID());
        Document child = buildDocument();
        child.setParent(folder);
        when(nodeService.getNode(folder.getId())).thenReturn(folder);
        when(nodeService.getChildren(eq(folder.getId()), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(child)));

        CmisModels.ChildrenResponse response = cmisBrowserService.getChildren(folder.getId().toString(), null, 0, 50);

        assertEquals(folder.getId().toString(), response.parentObjectId());
        assertEquals(1, response.totalNumItems());
        assertEquals(child.getId().toString(), response.objects().get(0).objectId());
        assertNotNull(response.objects().get(0).properties().get("cmis:name"));
    }

    @Test
    @DisplayName("Query returns repository-backed document matches by name")
    void queryReturnsDocumentMatches() {
        Document document = buildDocument();
        when(nodeRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(document));

        CmisModels.QueryResponse response = cmisQueryService.query(
            "SELECT * FROM cmis:document WHERE cmis:name = 'contract.pdf' ORDER BY cmis:name ASC",
            0,
            25
        );

        assertEquals(1, response.totalNumItems());
        assertEquals(document.getId().toString(), response.objects().get(0).objectId());
        assertEquals("contract.pdf", response.objects().get(0).name());
    }

    @Test
    @DisplayName("Query resolves IN_FOLDER by path and supports pagination")
    void querySupportsInFolderByPath() {
        Folder folder = buildFolder("Contracts", "/Sites/contracts");
        folder.setId(UUID.randomUUID());
        Document first = buildDocument();
        Document second = buildDocument();
        second.setId(UUID.randomUUID());
        second.setName("contract-2.pdf");
        when(folderService.getFolderByPath("/Sites/contracts")).thenReturn(folder);
        when(nodeRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(first, second));

        CmisModels.QueryResponse response = cmisQueryService.query(
            "SELECT * FROM cmis:document WHERE IN_FOLDER('/Sites/contracts') AND cmis:name LIKE 'contract%' ORDER BY cmis:lastModificationDate DESC",
            0,
            1
        );

        assertEquals(2, response.totalNumItems());
        assertEquals(1, response.objects().size());
        assertTrue(response.hasMoreItems());
    }

    @Mock
    private com.ecm.core.repository.NodeRepository nodeRepository;

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

    private Document buildDocument() {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("contract.pdf");
        document.setPath("/Sites/contracts/contract.pdf");
        document.setMimeType("application/pdf");
        document.setFileSize(4096L);
        document.setVersionLabel("1.0");
        document.setCreatedBy("alice");
        document.setCreatedDate(LocalDateTime.now());
        document.setLastModifiedBy("alice");
        document.setLastModifiedDate(LocalDateTime.now());
        Folder parent = buildFolder("contracts", "/Sites/contracts");
        document.setParent(parent);
        return document;
    }
}
