package com.ecm.core.alfresco;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Alfresco-compatible model classes
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
class NodeRef implements Serializable {
    private String id;
    
    @Override
    public String toString() {
        return "workspace://SpacesStore/" + id;
    }
    
    public static NodeRef fromString(String nodeRef) {
        if (nodeRef.contains("/")) {
            String[] parts = nodeRef.split("/");
            return new NodeRef(parts[parts.length - 1]);
        }
        return new NodeRef(nodeRef);
    }
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class QName implements Serializable {
    private String namespace;
    private String localName;
    
    public static QName createQName(String localName) {
        return new QName("{http://www.alfresco.org/model/content/1.0}", localName);
    }
    
    public static QName createQName(String namespace, String localName) {
        return new QName(namespace, localName);
    }
    
    @Override
    public String toString() {
        return namespace + localName;
    }
}

@Data
@AllArgsConstructor
class ChildAssociationRef implements Serializable {
    private QName typeQName;
    private NodeRef parentRef;
    private QName qName;
    private NodeRef childRef;
}

@Data
@AllArgsConstructor
class Path implements Serializable {
    private String path;
    
    public String toDisplayPath() {
        return path;
    }
}

@Data
class ContentData implements Serializable {
    private String contentUrl;
    private String mimetype;
    private long size;
    private String encoding;
    private String locale;
}