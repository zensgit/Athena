package com.ecm.core.cmis;

import com.ecm.core.config.RepositoryIdentityProvider;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Version;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.VersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CmisVersionHistoryTest {

    @Mock
    private NodeService nodeService;

    @Mock
    private FolderService folderService;

    @Mock
    private VersionService versionService;

    @Mock
    private CmisTypeManager typeManager;

    private final CmisObjectFactory objectFactory = new CmisObjectFactory(new RepositoryIdentityProvider("athena", "athena"));

    private CmisBrowserService cmisBrowserService;

    @BeforeEach
    void setUp() {
        cmisBrowserService = new CmisBrowserService(nodeService, folderService, typeManager, objectFactory, versionService);
    }

    @Test
    @DisplayName("getAllVersions returns version list mapped to ObjectEntries with version properties")
    void getAllVersionsReturnsMappedEntries() {
        Document document = buildDocument();
        UUID docId = document.getId();

        Version v1 = buildVersion(document, 1, 1, 0, true, "Initial upload");
        Version v2 = buildVersion(document, 2, 1, 1, false, "Minor edit");
        Version v3 = buildVersion(document, 3, 2, 0, true, "Major revision");

        when(nodeService.getNode(docId)).thenReturn(document);
        when(versionService.getVersionHistory(docId)).thenReturn(List.of(v3, v2, v1));

        List<CmisModels.ObjectEntry> entries = cmisBrowserService.getAllVersions(docId.toString());

        assertEquals(3, entries.size());

        // First entry is v3 (latest, version number 3)
        CmisModels.ObjectEntry latest = entries.get(0);
        assertEquals(docId + ";v2.0", latest.objectId());
        assertEquals("2.0", latest.properties().get("cmis:versionLabel"));
        assertEquals(docId.toString(), latest.properties().get("cmis:versionSeriesId"));
        assertEquals(true, latest.properties().get("cmis:isLatestVersion"));
        assertEquals(true, latest.properties().get("cmis:isMajorVersion"));
        assertEquals("Major revision", latest.properties().get("cmis:checkinComment"));
        assertEquals("cmis:document", latest.baseTypeId());
        assertEquals(document.getName(), latest.name());

        // Second entry is v2 (not latest)
        CmisModels.ObjectEntry middle = entries.get(1);
        assertEquals("1.1", middle.properties().get("cmis:versionLabel"));
        assertEquals(false, middle.properties().get("cmis:isLatestVersion"));
        assertEquals(false, middle.properties().get("cmis:isMajorVersion"));

        // Third entry is v1 (not latest, but major)
        CmisModels.ObjectEntry oldest = entries.get(2);
        assertEquals("1.0", oldest.properties().get("cmis:versionLabel"));
        assertEquals(false, oldest.properties().get("cmis:isLatestVersion"));
        assertEquals(true, oldest.properties().get("cmis:isMajorVersion"));
    }

    @Test
    @DisplayName("getLatestVersion returns the most recent version")
    void getLatestVersionReturnsMostRecent() {
        Document document = buildDocument();
        UUID docId = document.getId();

        Version v1 = buildVersion(document, 1, 1, 0, true, "Initial upload");
        Version v2 = buildVersion(document, 2, 2, 0, true, "Major update");

        when(nodeService.getNode(docId)).thenReturn(document);
        when(versionService.getVersionHistory(docId, false)).thenReturn(List.of(v2, v1));

        CmisModels.ObjectEntry entry = cmisBrowserService.getLatestVersion(docId.toString(), false);

        assertEquals(docId + ";v2.0", entry.objectId());
        assertEquals("2.0", entry.properties().get("cmis:versionLabel"));
        assertEquals(true, entry.properties().get("cmis:isLatestVersion"));
        assertEquals("Major update", entry.properties().get("cmis:checkinComment"));
        assertEquals("application/pdf", entry.properties().get("cmis:contentStreamMimeType"));
        assertEquals(4096L, entry.properties().get("cmis:contentStreamLength"));
    }

    @Test
    @DisplayName("getAllVersions for document with no version history returns current state")
    void getAllVersionsNoHistoryReturnsCurrentState() {
        Document document = buildDocument();
        UUID docId = document.getId();

        when(nodeService.getNode(docId)).thenReturn(document);
        when(versionService.getVersionHistory(docId)).thenReturn(List.of());

        List<CmisModels.ObjectEntry> entries = cmisBrowserService.getAllVersions(docId.toString());

        assertFalse(entries.isEmpty(), "Should return at least one entry for current document state");
        assertEquals(1, entries.size());
        assertEquals(docId.toString(), entries.get(0).objectId());
        assertEquals(document.getName(), entries.get(0).name());
        assertNotNull(entries.get(0).properties().get("cmis:name"));
    }

    @Test
    @DisplayName("getLatestVersion falls back to document when no versions exist")
    void getLatestVersionFallsBackToDocument() {
        Document document = buildDocument();
        UUID docId = document.getId();

        when(nodeService.getNode(docId)).thenReturn(document);
        when(versionService.getVersionHistory(docId, false)).thenReturn(List.of());

        CmisModels.ObjectEntry entry = cmisBrowserService.getLatestVersion(docId.toString(), false);

        assertEquals(docId.toString(), entry.objectId());
        assertEquals(document.getName(), entry.name());
        assertEquals("cmis:document", entry.baseTypeId());
    }

    @Test
    @DisplayName("Version entries include content stream metadata")
    void versionEntriesIncludeContentMetadata() {
        Document document = buildDocument();
        UUID docId = document.getId();

        Version v1 = buildVersion(document, 1, 1, 0, true, "Upload");
        v1.setMimeType("text/plain");
        v1.setFileSize(1024L);

        when(nodeService.getNode(docId)).thenReturn(document);
        when(versionService.getVersionHistory(docId)).thenReturn(List.of(v1));

        List<CmisModels.ObjectEntry> entries = cmisBrowserService.getAllVersions(docId.toString());

        assertEquals(1, entries.size());
        assertEquals("text/plain", entries.get(0).properties().get("cmis:contentStreamMimeType"));
        assertEquals(1024L, entries.get(0).properties().get("cmis:contentStreamLength"));
    }

    @Test
    @DisplayName("getObject resolves version-specific objectId to the matching version entry")
    void getObjectResolvesVersionSpecificObjectId() {
        Document document = buildDocument();
        UUID docId = document.getId();
        Version v1 = buildVersion(document, 1, 1, 0, true, "Initial upload");
        Version v2 = buildVersion(document, 2, 2, 0, true, "Major update");

        when(nodeService.getNode(docId)).thenReturn(document);
        when(versionService.getVersionByLabel(docId, "1.0")).thenReturn(v1);
        when(versionService.getVersionHistory(docId)).thenReturn(List.of(v2, v1));

        CmisModels.ObjectEntry entry = cmisBrowserService.getObject(docId + ";v1.0", null);

        assertEquals(docId + ";v1.0", entry.objectId());
        assertEquals("1.0", entry.properties().get("cmis:versionLabel"));
        assertEquals(false, entry.properties().get("cmis:isLatestVersion"));
    }

    private Document buildDocument() {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("contract.pdf");
        document.setPath("/Sites/contracts/contract.pdf");
        document.setMimeType("application/pdf");
        document.setFileSize(4096L);
        document.setVersionLabel("1.0");
        document.setMajorVersion(1);
        document.setMinorVersion(0);
        document.setCreatedBy("alice");
        document.setCreatedDate(LocalDateTime.now());
        document.setLastModifiedBy("alice");
        document.setLastModifiedDate(LocalDateTime.now());
        Folder parent = new Folder();
        parent.setId(UUID.randomUUID());
        parent.setName("contracts");
        parent.setPath("/Sites/contracts");
        parent.setCreatedBy("admin");
        parent.setCreatedDate(LocalDateTime.now());
        parent.setLastModifiedBy("admin");
        parent.setLastModifiedDate(LocalDateTime.now());
        document.setParent(parent);
        return document;
    }

    private Version buildVersion(Document document, int versionNumber, int major, int minor,
                                  boolean majorFlag, String comment) {
        Version version = new Version();
        version.setId(UUID.randomUUID());
        version.setDocument(document);
        version.setVersionNumber(versionNumber);
        version.setMajorVersion(major);
        version.setMinorVersion(minor);
        version.setMajorVersionFlag(majorFlag);
        version.setComment(comment);
        version.setVersionLabel(major + "." + minor);
        version.setContentId("content-" + versionNumber);
        version.setMimeType(document.getMimeType());
        version.setFileSize(document.getFileSize());
        version.setFrozenDate(LocalDateTime.now().minusDays(versionNumber));
        version.setFrozenBy("alice");
        version.setCreatedBy("alice");
        version.setCreatedDate(LocalDateTime.now().minusDays(versionNumber));
        return version;
    }
}
