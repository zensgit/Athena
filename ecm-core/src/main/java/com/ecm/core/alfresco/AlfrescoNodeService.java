package com.ecm.core.alfresco;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.service.NodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Alfresco-compatible NodeService implementation
 * Provides compatibility with Alfresco's NodeService API
 */
@Slf4j
@Service("alfrescoNodeService")
@RequiredArgsConstructor
public class AlfrescoNodeService {
    
    private final NodeService nodeService;
    
    /**
     * Create a new node (Alfresco-compatible method)
     */
    public NodeRef createNode(NodeRef parent, QName assocTypeQName, QName assocQName, 
                              QName nodeTypeQName, Map<QName, Serializable> properties) {
        log.debug("Creating node with Alfresco API - parent: {}, type: {}", parent, nodeTypeQName);
        
        Node node;
        if (nodeTypeQName.getLocalName().equals("folder")) {
            node = new Folder();
        } else {
            node = new Document();
        }
        
        // Map Alfresco properties to our model
        if (properties != null) {
            for (Map.Entry<QName, Serializable> entry : properties.entrySet()) {
                String key = entry.getKey().getLocalName();
                Object value = entry.getValue();
                
                switch (key) {
                    case "name":
                        node.setName((String) value);
                        break;
                    case "title":
                        node.setDescription((String) value);
                        break;
                    default:
                        node.getProperties().put(key, value);
                }
            }
        }
        
        UUID parentId = parent != null ? UUID.fromString(parent.getId()) : null;
        Node created = nodeService.createNode(node, parentId);
        
        return new NodeRef(created.getId().toString());
    }
    
    /**
     * Get node properties (Alfresco-compatible method)
     */
    public Map<QName, Serializable> getProperties(NodeRef nodeRef) {
        Node node = nodeService.getNode(UUID.fromString(nodeRef.getId()));
        
        Map<QName, Serializable> properties = new HashMap<>();
        properties.put(QName.createQName("name"), node.getName());
        properties.put(QName.createQName("created"), Date.from(
            node.getCreatedDate().atZone(java.time.ZoneId.systemDefault()).toInstant()));
        properties.put(QName.createQName("creator"), node.getCreatedBy());
        properties.put(QName.createQName("modified"), Date.from(
            node.getLastModifiedDate().atZone(java.time.ZoneId.systemDefault()).toInstant()));
        properties.put(QName.createQName("modifier"), node.getLastModifiedBy());
        
        if (node instanceof Document) {
            Document doc = (Document) node;
            properties.put(QName.createQName("content"), doc.getContentId());
            properties.put(QName.createQName("mimetype"), doc.getMimeType());
            properties.put(QName.createQName("size"), doc.getFileSize());
        }
        
        // Add custom properties
        for (Map.Entry<String, Object> entry : node.getProperties().entrySet()) {
            if (entry.getValue() instanceof Serializable) {
                properties.put(QName.createQName(entry.getKey()), (Serializable) entry.getValue());
            }
        }
        
        return properties;
    }
    
    /**
     * Set node properties (Alfresco-compatible method)
     */
    public void setProperties(NodeRef nodeRef, Map<QName, Serializable> properties) {
        Map<String, Object> updates = new HashMap<>();
        
        for (Map.Entry<QName, Serializable> entry : properties.entrySet()) {
            String key = entry.getKey().getLocalName();
            Object value = entry.getValue();
            
            switch (key) {
                case "name":
                    updates.put("name", value);
                    break;
                case "title":
                    updates.put("description", value);
                    break;
                default:
                    if (updates.containsKey("properties")) {
                        ((Map<String, Object>) updates.get("properties")).put(key, value);
                    } else {
                        Map<String, Object> props = new HashMap<>();
                        props.put(key, value);
                        updates.put("properties", props);
                    }
            }
        }
        
        nodeService.updateNode(UUID.fromString(nodeRef.getId()), updates);
    }
    
    /**
     * Get node type (Alfresco-compatible method)
     */
    public QName getType(NodeRef nodeRef) {
        Node node = nodeService.getNode(UUID.fromString(nodeRef.getId()));
        
        if (node instanceof Folder) {
            return QName.createQName("folder");
        } else if (node instanceof Document) {
            return QName.createQName("content");
        } else {
            return QName.createQName("node");
        }
    }
    
    /**
     * Move node (Alfresco-compatible method)
     */
    public ChildAssociationRef moveNode(NodeRef nodeToMoveRef, NodeRef newParentRef, 
                                        QName assocTypeQName, QName assocQName) {
        Node moved = nodeService.moveNode(
            UUID.fromString(nodeToMoveRef.getId()),
            UUID.fromString(newParentRef.getId())
        );
        
        return new ChildAssociationRef(
            assocTypeQName,
            new NodeRef(moved.getParent().getId().toString()),
            assocQName,
            new NodeRef(moved.getId().toString())
        );
    }
    
    /**
     * Copy node (Alfresco-compatible method)
     */
    public NodeRef copyNode(NodeRef sourceNodeRef, NodeRef targetParentRef, 
                            QName assocTypeQName, QName assocQName, boolean deepCopy) {
        Node copied = nodeService.copyNode(
            UUID.fromString(sourceNodeRef.getId()),
            UUID.fromString(targetParentRef.getId()),
            null,
            deepCopy
        );
        
        return new NodeRef(copied.getId().toString());
    }
    
    /**
     * Delete node (Alfresco-compatible method)
     */
    public void deleteNode(NodeRef nodeRef) {
        nodeService.deleteNode(UUID.fromString(nodeRef.getId()), false);
    }
    
    /**
     * Get child associations (Alfresco-compatible method)
     */
    public List<ChildAssociationRef> getChildAssocs(NodeRef nodeRef) {
        Node node = nodeService.getNode(UUID.fromString(nodeRef.getId()));
        
        if (node instanceof Folder) {
            List<Node> children = nodeService.getChildren(node.getId(), null).getContent();
            
            return children.stream()
                .map(child -> new ChildAssociationRef(
                    QName.createQName("contains"),
                    new NodeRef(node.getId().toString()),
                    QName.createQName(child.getName()),
                    new NodeRef(child.getId().toString())
                ))
                .collect(Collectors.toList());
        }
        
        return Collections.emptyList();
    }
    
    /**
     * Check if node exists (Alfresco-compatible method)
     */
    public boolean exists(NodeRef nodeRef) {
        try {
            nodeService.getNode(UUID.fromString(nodeRef.getId()));
            return true;
        } catch (NoSuchElementException e) {
            return false;
        }
    }
    
    /**
     * Get node path (Alfresco-compatible method)
     */
    public Path getPath(NodeRef nodeRef) {
        Node node = nodeService.getNode(UUID.fromString(nodeRef.getId()));
        return new Path(node.getPath());
    }
}