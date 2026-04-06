package com.ecm.core.cmis;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Serializes CMIS model objects to Atom Publishing Protocol XML.
 * Produces CMIS 1.1 AtomPub compliant responses.
 */
@Component
public class CmisAtomPubSerializer {

    private static final String CMIS_NS = "http://docs.oasis-open.org/ns/cmis/core/200908/";
    private static final String CMISRA_NS = "http://docs.oasis-open.org/ns/cmis/restatom/200908/";
    private static final String ATOM_NS = "http://www.w3.org/2005/Atom";
    private static final String APP_NS = "http://www.w3.org/2007/app";

    public String serializeServiceDocument(CmisModels.RepositoryInfo info, String baseUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<app:service xmlns:app=\"").append(APP_NS).append("\" xmlns:atom=\"").append(ATOM_NS)
          .append("\" xmlns:cmisra=\"").append(CMISRA_NS).append("\" xmlns:cmis=\"").append(CMIS_NS).append("\">\n");
        sb.append("  <app:workspace>\n");
        sb.append("    <atom:title>").append(esc(info.repositoryName())).append("</atom:title>\n");
        sb.append("    <cmisra:repositoryInfo>\n");
        sb.append("      <cmis:repositoryId>").append(esc(info.repositoryId())).append("</cmis:repositoryId>\n");
        sb.append("      <cmis:repositoryName>").append(esc(info.repositoryName())).append("</cmis:repositoryName>\n");
        sb.append("      <cmis:vendorName>").append(esc(info.vendorName())).append("</cmis:vendorName>\n");
        sb.append("      <cmis:productName>").append(esc(info.productName())).append("</cmis:productName>\n");
        sb.append("      <cmis:productVersion>").append(esc(info.productVersion())).append("</cmis:productVersion>\n");
        sb.append("      <cmis:cmisVersionSupported>").append(esc(info.cmisVersionSupported())).append("</cmis:cmisVersionSupported>\n");
        sb.append("      <cmis:rootFolderId>").append(esc(info.rootFolderId())).append("</cmis:rootFolderId>\n");
        sb.append("    </cmisra:repositoryInfo>\n");
        sb.append("    <app:collection href=\"").append(esc(baseUrl)).append("/children?objectId=root\">\n");
        sb.append("      <atom:title>Root Collection</atom:title>\n");
        sb.append("      <app:accept>application/atom+xml;type=entry</app:accept>\n");
        sb.append("    </app:collection>\n");
        sb.append("  </app:workspace>\n");
        sb.append("</app:service>\n");
        return sb.toString();
    }

    public String serializeObjectEntry(CmisModels.ObjectEntry entry, String baseUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<atom:entry xmlns:atom=\"").append(ATOM_NS)
          .append("\" xmlns:cmisra=\"").append(CMISRA_NS)
          .append("\" xmlns:cmis=\"").append(CMIS_NS).append("\">\n");
        appendEntryBody(sb, entry, baseUrl, "  ");
        sb.append("</atom:entry>\n");
        return sb.toString();
    }

    public String serializeChildrenFeed(CmisModels.ChildrenResponse children, String baseUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<atom:feed xmlns:atom=\"").append(ATOM_NS)
          .append("\" xmlns:cmisra=\"").append(CMISRA_NS)
          .append("\" xmlns:cmis=\"").append(CMIS_NS).append("\">\n");
        sb.append("  <atom:title>Children of ").append(esc(children.parentObjectId())).append("</atom:title>\n");
        sb.append("  <atom:id>urn:").append(esc(children.repositoryId())).append(":children:").append(esc(children.parentObjectId())).append("</atom:id>\n");
        sb.append("  <cmisra:numItems>").append(children.totalNumItems()).append("</cmisra:numItems>\n");
        sb.append("  <cmisra:hasMoreItems>").append(children.hasMoreItems()).append("</cmisra:hasMoreItems>\n");
        for (CmisModels.ObjectEntry entry : children.objects()) {
            sb.append("  <atom:entry>\n");
            appendEntryBody(sb, entry, baseUrl, "    ");
            sb.append("  </atom:entry>\n");
        }
        sb.append("</atom:feed>\n");
        return sb.toString();
    }

    private void appendEntryBody(StringBuilder sb, CmisModels.ObjectEntry entry, String baseUrl, String indent) {
        sb.append(indent).append("<atom:id>urn:").append(esc(entry.repositoryId())).append(":").append(esc(entry.objectId())).append("</atom:id>\n");
        sb.append(indent).append("<atom:title>").append(esc(entry.name())).append("</atom:title>\n");
        sb.append(indent).append("<atom:link rel=\"self\" href=\"").append(esc(baseUrl)).append("/object?objectId=").append(esc(entry.objectId())).append("\"/>\n");
        if ("cmis:folder".equals(entry.baseTypeId())) {
            sb.append(indent).append("<atom:link rel=\"down\" type=\"application/atom+xml;type=feed\" href=\"")
              .append(esc(baseUrl)).append("/children?objectId=").append(esc(entry.objectId())).append("\"/>\n");
        }
        if (entry.parentId() != null) {
            sb.append(indent).append("<atom:link rel=\"up\" href=\"").append(esc(baseUrl)).append("/object?objectId=").append(esc(entry.parentId())).append("\"/>\n");
        }
        sb.append(indent).append("<cmisra:object>\n");
        sb.append(indent).append("  <cmis:properties>\n");
        for (Map.Entry<String, Object> prop : entry.properties().entrySet()) {
            if (prop.getValue() != null) {
                sb.append(indent).append("    <cmis:propertyString propertyDefinitionId=\"").append(esc(prop.getKey())).append("\">");
                sb.append("<cmis:value>").append(esc(String.valueOf(prop.getValue()))).append("</cmis:value>");
                sb.append("</cmis:propertyString>\n");
            }
        }
        sb.append(indent).append("  </cmis:properties>\n");
        sb.append(indent).append("</cmisra:object>\n");
    }

    private String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
