package com.ecm.core.cmis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CmisAtomPubSerializerTest {

    private CmisAtomPubSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new CmisAtomPubSerializer();
    }

    @Nested
    @DisplayName("serializeServiceDocument")
    class ServiceDoc {

        @Test
        @DisplayName("produces valid XML with repositoryInfo and collection")
        void producesServiceXml() {
            CmisModels.RepositoryInfo info = new CmisModels.RepositoryInfo(
                "athena", "Athena Repository", "Athena", "Athena ECM",
                "1.0", "1.1", "root", List.of("read")
            );

            String xml = serializer.serializeServiceDocument(info, "http://localhost/api/cmis/atom");

            assertTrue(xml.startsWith("<?xml"));
            assertTrue(xml.contains("<app:service"));
            assertTrue(xml.contains("<cmis:repositoryId>athena</cmis:repositoryId>"));
            assertTrue(xml.contains("<cmis:repositoryName>Athena Repository</cmis:repositoryName>"));
            assertTrue(xml.contains("<cmis:cmisVersionSupported>1.1</cmis:cmisVersionSupported>"));
            assertTrue(xml.contains("<cmis:rootFolderId>root</cmis:rootFolderId>"));
            assertTrue(xml.contains("href=\"http://localhost/api/cmis/atom/children?objectId=root\""));
        }
    }

    @Nested
    @DisplayName("serializeObjectEntry")
    class ObjectEntry {

        @Test
        @DisplayName("produces Atom entry with properties")
        void producesEntryXml() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("cmis:objectId", "abc-123");
            props.put("cmis:name", "report.pdf");
            props.put("cmis:baseTypeId", "cmis:document");

            CmisModels.ObjectEntry entry = new CmisModels.ObjectEntry(
                "athena", "abc-123", "report.pdf", "cmis:document", "cmis:document",
                "/report.pdf", "root", false, props, List.of("canGetProperties")
            );

            String xml = serializer.serializeObjectEntry(entry, "http://localhost/api/cmis/atom");

            assertTrue(xml.contains("<atom:entry"));
            assertTrue(xml.contains("<atom:title>report.pdf</atom:title>"));
            assertTrue(xml.contains("propertyDefinitionId=\"cmis:objectId\""));
            assertTrue(xml.contains("<cmis:value>abc-123</cmis:value>"));
            assertTrue(xml.contains("rel=\"self\""));
            assertTrue(xml.contains("rel=\"up\""));
            // document: no "down" link
            assertFalse(xml.contains("rel=\"down\""));
        }

        @Test
        @DisplayName("folder entry includes down link for children")
        void folderHasDownLink() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("cmis:objectId", "folder-1");
            CmisModels.ObjectEntry entry = new CmisModels.ObjectEntry(
                "athena", "folder-1", "Documents", "cmis:folder", "cmis:folder",
                "/Documents", "root", false, props, List.of()
            );

            String xml = serializer.serializeObjectEntry(entry, "http://localhost/api/cmis/atom");

            assertTrue(xml.contains("rel=\"down\""));
            assertTrue(xml.contains("type=\"application/atom+xml;type=feed\""));
        }
    }

    @Nested
    @DisplayName("serializeChildrenFeed")
    class ChildrenFeed {

        @Test
        @DisplayName("produces Atom feed with entries and pagination")
        void producesFeedXml() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("cmis:objectId", "child-1");
            props.put("cmis:name", "sub-folder");
            CmisModels.ObjectEntry child = new CmisModels.ObjectEntry(
                "athena", "child-1", "sub-folder", "cmis:folder", "cmis:folder",
                "/Documents/sub-folder", "folder-1", false, props, List.of()
            );

            CmisModels.ChildrenResponse response = new CmisModels.ChildrenResponse(
                "athena", "folder-1", List.of(child), 0, 25, 1, false
            );

            String xml = serializer.serializeChildrenFeed(response, "http://localhost/api/cmis/atom");

            assertTrue(xml.contains("<atom:feed"));
            assertTrue(xml.contains("<cmisra:numItems>1</cmisra:numItems>"));
            assertTrue(xml.contains("<cmisra:hasMoreItems>false</cmisra:hasMoreItems>"));
            assertTrue(xml.contains("<atom:title>sub-folder</atom:title>"));
        }

        @Test
        @DisplayName("escapes special XML characters in names")
        void escapesSpecialChars() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("cmis:name", "R&D <report>");
            CmisModels.ObjectEntry child = new CmisModels.ObjectEntry(
                "athena", "id-1", "R&D <report>", "cmis:document", "cmis:document",
                "/R&D", "root", false, props, List.of()
            );
            CmisModels.ChildrenResponse response = new CmisModels.ChildrenResponse(
                "athena", "root", List.of(child), 0, 25, 1, false
            );

            String xml = serializer.serializeChildrenFeed(response, "http://localhost/api/cmis/atom");

            assertTrue(xml.contains("R&amp;D &lt;report&gt;"));
            assertFalse(xml.contains("R&D <report>"));
        }
    }
}
