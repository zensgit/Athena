package com.ecm.core.cmis;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.NodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CmisMutationServiceTest {

    @Mock
    private FolderService folderService;

    @Mock
    private NodeService nodeService;

    private CmisMutationService cmisMutationService;

    @BeforeEach
    void setUp() {
        cmisMutationService = new CmisMutationService(folderService, nodeService, new CmisObjectFactory());
    }

    @Test
    @DisplayName("Create folder resolves root target to null parent")
    void createFolderSupportsVirtualRootParent() {
        Folder folder = buildFolder("Contracts", "/Contracts");
        when(folderService.createFolder(any())).thenReturn(folder);

        CmisModels.MutationResponse response = cmisMutationService.createFolder(new CmisModels.MutationRequest(
            null,
            null,
            CmisObjectFactory.ROOT_OBJECT_ID,
            null,
            "Contracts",
            "Contract folder",
            null,
            null,
            null,
            null
        ));

        ArgumentCaptor<FolderService.CreateFolderRequest> requestCaptor = ArgumentCaptor.forClass(FolderService.CreateFolderRequest.class);
        verify(folderService).createFolder(requestCaptor.capture());
        assertNull(requestCaptor.getValue().parentId());
        assertEquals("createFolder", response.action());
        assertEquals("Contracts", response.object().name());
    }

    @Test
    @DisplayName("Create document applies metadata and property updates after create")
    void createDocumentAppliesSecondaryUpdates() {
        UUID parentId = UUID.randomUUID();
        Folder parent = buildFolder("Contracts", "/Sites/contracts");
        parent.setId(parentId);
        Document created = buildDocument(parent, "contract.pdf");
        Document updated = buildDocument(parent, "contract.pdf");
        updated.setDescription("Imported contract");
        updated.getMetadata().put("source", "cmis");
        updated.getProperties().put("reviewState", "pending");

        when(folderService.getFolder(parentId)).thenReturn(parent);
        when(nodeService.createDocument("contract.pdf", "application/pdf", 128L, parentId)).thenReturn(created);
        when(nodeService.updateNode(eq(created.getId()), any())).thenReturn(updated);

        CmisModels.MutationResponse response = cmisMutationService.createDocument(new CmisModels.MutationRequest(
            null,
            null,
            parentId.toString(),
            null,
            "contract.pdf",
            "Imported contract",
            "application/pdf",
            128L,
            Map.of("reviewState", "pending"),
            Map.of("source", "cmis")
        ));

        ArgumentCaptor<Map<String, Object>> updatesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(nodeService).updateNode(eq(created.getId()), updatesCaptor.capture());
        assertEquals("Imported contract", updatesCaptor.getValue().get("description"));
        assertEquals(Map.of("source", "cmis"), updatesCaptor.getValue().get("metadata"));
        assertEquals(Map.of("reviewState", "pending"), updatesCaptor.getValue().get("properties"));
        assertEquals("createDocument", response.action());
        assertEquals("contract.pdf", response.object().name());
    }

    @Test
    @DisplayName("Update properties maps CMIS and Athena-prefixed fields")
    void updatePropertiesMapsSupportedFields() {
        Document document = buildDocument(buildFolder("Contracts", "/Sites/contracts"), "contract.pdf");
        when(nodeService.getNode(document.getId())).thenReturn(document);
        when(nodeService.updateNode(eq(document.getId()), any())).thenReturn(document);

        cmisMutationService.updateProperties(new CmisModels.MutationRequest(
            document.getId().toString(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Map.of(
                "cmis:name", "renamed-contract.pdf",
                "cmis:description", "Updated contract",
                "athena:metadata.category", "finance",
                "athena:property.reviewState", "approved"
            ),
            Map.of("sourceSystem", "cmis")
        ));

        ArgumentCaptor<Map<String, Object>> updatesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(nodeService).updateNode(eq(document.getId()), updatesCaptor.capture());
        assertEquals("renamed-contract.pdf", updatesCaptor.getValue().get("name"));
        assertEquals("Updated contract", updatesCaptor.getValue().get("description"));
        assertEquals(Map.of("category", "finance", "sourceSystem", "cmis"), updatesCaptor.getValue().get("metadata"));
        assertEquals(Map.of("reviewState", "approved"), updatesCaptor.getValue().get("properties"));
    }

    @Test
    @DisplayName("Update properties rejects blank cmis:name")
    void updatePropertiesRejectsBlankName() {
        Document document = buildDocument(buildFolder("Contracts", "/Sites/contracts"), "contract.pdf");
        when(nodeService.getNode(document.getId())).thenReturn(document);

        assertThrows(IllegalArgumentException.class, () -> cmisMutationService.updateProperties(new CmisModels.MutationRequest(
            document.getId().toString(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Map.of("cmis:name", "   "),
            null
        )));
    }

    @Test
    @DisplayName("Delete object delegates soft delete")
    void deleteObjectDelegatesSoftDelete() {
        Document document = buildDocument(buildFolder("Contracts", "/Sites/contracts"), "contract.pdf");
        when(nodeService.getNode(document.getId())).thenReturn(document);

        CmisModels.MutationResponse response = cmisMutationService.deleteObject(new CmisModels.MutationRequest(
            document.getId().toString(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ));

        verify(nodeService).deleteNode(document.getId(), false);
        assertEquals(document.getId().toString(), response.deletedObjectId());
        assertEquals("deleteObject", response.action());
    }

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

    private Document buildDocument(Folder parent, String name) {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName(name);
        document.setPath(parent.getPath() + "/" + name);
        document.setParent(parent);
        document.setMimeType("application/pdf");
        document.setFileSize(1024L);
        document.setCreatedBy("alice");
        document.setCreatedDate(LocalDateTime.now());
        document.setLastModifiedBy("alice");
        document.setLastModifiedDate(LocalDateTime.now());
        document.setArchiveStatus(Node.ArchiveStatus.LIVE);
        return document;
    }
}
