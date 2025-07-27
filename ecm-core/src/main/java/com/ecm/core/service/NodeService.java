package com.ecm.core.service;

import com.ecm.core.entity.*;
import com.ecm.core.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NodeService {
    
    private final NodeRepository nodeRepository;
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final PermissionRepository permissionRepository;
    private final SecurityService securityService;
    private final ApplicationEventPublisher eventPublisher;
    
    public Node createNode(Node node, UUID parentId) {
        log.debug("Creating node: {} under parent: {}", node.getName(), parentId);
        
        if (parentId != null) {
            Folder parent = folderRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Parent folder not found: " + parentId));
            
            // Check permissions
            if (!securityService.hasPermission(parent, PermissionType.CREATE_CHILDREN)) {
                throw new SecurityException("No permission to create children in folder: " + parent.getName());
            }
            
            // Check if folder is full
            if (parent.getMaxItems() != null) {
                long childCount = nodeRepository.countByParentId(parentId);
                if (childCount >= parent.getMaxItems()) {
                    throw new IllegalStateException("Folder is full: " + parent.getName());
                }
            }
            
            node.setParent(parent);
        }
        
        // Check for duplicate names
        if (nodeRepository.findByParentIdAndName(parentId, node.getName()).isPresent()) {
            throw new IllegalArgumentException("Node with name already exists: " + node.getName());
        }
        
        Node savedNode = nodeRepository.save(node);
        
        // Copy parent permissions if inherit is true
        if (node.isInheritPermissions() && node.getParent() != null) {
            copyPermissions(node.getParent(), savedNode);
        }
        
        eventPublisher.publishEvent(new NodeCreatedEvent(savedNode));
        
        return savedNode;
    }
    
    public Node getNode(UUID nodeId) {
        Node node = nodeRepository.findByIdAndDeletedFalse(nodeId)
            .orElseThrow(() -> new NoSuchElementException("Node not found: " + nodeId));
        
        if (!securityService.hasPermission(node, PermissionType.READ)) {
            throw new SecurityException("No permission to read node: " + node.getName());
        }
        
        return node;
    }
    
    public Node getNodeByPath(String path) {
        Node node = nodeRepository.findByPath(path)
            .orElseThrow(() -> new NoSuchElementException("Node not found at path: " + path));
        
        if (!securityService.hasPermission(node, PermissionType.READ)) {
            throw new SecurityException("No permission to read node: " + node.getName());
        }
        
        return node;
    }
    
    public Page<Node> getChildren(UUID parentId, Pageable pageable) {
        Node parent = getNode(parentId);
        return nodeRepository.findByParentIdAndDeletedFalse(parentId, pageable);
    }
    
    public Node updateNode(UUID nodeId, Map<String, Object> updates) {
        Node node = getNode(nodeId);
        
        if (!securityService.hasPermission(node, PermissionType.WRITE)) {
            throw new SecurityException("No permission to update node: " + node.getName());
        }
        
        if (node.isLocked() && !node.getLockedBy().equals(securityService.getCurrentUser())) {
            throw new IllegalStateException("Node is locked by: " + node.getLockedBy());
        }
        
        // Update allowed fields
        if (updates.containsKey("name")) {
            String newName = (String) updates.get("name");
            if (!node.getName().equals(newName)) {
                // Check for duplicate names
                UUID parentId = node.getParent() != null ? node.getParent().getId() : null;
                if (nodeRepository.findByParentIdAndName(parentId, newName).isPresent()) {
                    throw new IllegalArgumentException("Node with name already exists: " + newName);
                }
                node.setName(newName);
            }
        }
        
        if (updates.containsKey("description")) {
            node.setDescription((String) updates.get("description"));
        }
        
        if (updates.containsKey("properties")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) updates.get("properties");
            node.getProperties().putAll(properties);
        }
        
        if (updates.containsKey("metadata")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) updates.get("metadata");
            node.getMetadata().putAll(metadata);
        }
        
        Node updatedNode = nodeRepository.save(node);
        eventPublisher.publishEvent(new NodeUpdatedEvent(updatedNode));
        
        return updatedNode;
    }
    
    public Node moveNode(UUID nodeId, UUID targetParentId) {
        Node node = getNode(nodeId);
        Folder targetParent = folderRepository.findById(targetParentId)
            .orElseThrow(() -> new IllegalArgumentException("Target parent not found: " + targetParentId));
        
        // Check permissions
        if (!securityService.hasPermission(node, PermissionType.DELETE)) {
            throw new SecurityException("No permission to move node: " + node.getName());
        }
        if (!securityService.hasPermission(targetParent, PermissionType.CREATE_CHILDREN)) {
            throw new SecurityException("No permission to create children in target folder");
        }
        
        // Check for circular reference
        if (isDescendant(targetParent, node)) {
            throw new IllegalArgumentException("Cannot move node to its own descendant");
        }
        
        // Check for duplicate names
        if (nodeRepository.findByParentIdAndName(targetParentId, node.getName()).isPresent()) {
            throw new IllegalArgumentException("Node with name already exists in target folder: " + node.getName());
        }
        
        Node oldParent = node.getParent();
        node.setParent(targetParent);
        
        Node movedNode = nodeRepository.save(node);
        
        eventPublisher.publishEvent(new NodeMovedEvent(movedNode, oldParent, targetParent));
        
        return movedNode;
    }
    
    public Node copyNode(UUID nodeId, UUID targetParentId, String newName, boolean deep) {
        Node source = getNode(nodeId);
        Folder targetParent = folderRepository.findById(targetParentId)
            .orElseThrow(() -> new IllegalArgumentException("Target parent not found: " + targetParentId));
        
        // Check permissions
        if (!securityService.hasPermission(source, PermissionType.READ)) {
            throw new SecurityException("No permission to read source node: " + source.getName());
        }
        if (!securityService.hasPermission(targetParent, PermissionType.CREATE_CHILDREN)) {
            throw new SecurityException("No permission to create children in target folder");
        }
        
        String copyName = newName != null ? newName : source.getName() + " (Copy)";
        
        // Check for duplicate names
        if (nodeRepository.findByParentIdAndName(targetParentId, copyName).isPresent()) {
            throw new IllegalArgumentException("Node with name already exists: " + copyName);
        }
        
        Node copy = copyNodeRecursive(source, targetParent, copyName, deep);
        
        eventPublisher.publishEvent(new NodeCopiedEvent(copy, source));
        
        return copy;
    }
    
    public void deleteNode(UUID nodeId, boolean permanent) {
        Node node = getNode(nodeId);
        
        if (!securityService.hasPermission(node, PermissionType.DELETE)) {
            throw new SecurityException("No permission to delete node: " + node.getName());
        }
        
        if (permanent) {
            deleteNodeRecursive(node);
        } else {
            softDeleteNodeRecursive(node);
        }
        
        eventPublisher.publishEvent(new NodeDeletedEvent(node, permanent));
    }
    
    public void lockNode(UUID nodeId) {
        Node node = getNode(nodeId);
        
        if (!securityService.hasPermission(node, PermissionType.WRITE)) {
            throw new SecurityException("No permission to lock node: " + node.getName());
        }
        
        if (node.isLocked()) {
            throw new IllegalStateException("Node is already locked by: " + node.getLockedBy());
        }
        
        node.setLocked(true);
        node.setLockedBy(securityService.getCurrentUser());
        node.setLockedDate(LocalDateTime.now());
        
        nodeRepository.save(node);
        eventPublisher.publishEvent(new NodeLockedEvent(node));
    }
    
    public void unlockNode(UUID nodeId) {
        Node node = getNode(nodeId);
        
        String currentUser = securityService.getCurrentUser();
        boolean isOwner = node.getLockedBy() != null && node.getLockedBy().equals(currentUser);
        boolean isAdmin = securityService.hasRole("ROLE_ADMIN");
        
        if (!isOwner && !isAdmin) {
            throw new SecurityException("Only lock owner or admin can unlock node");
        }
        
        node.setLocked(false);
        node.setLockedBy(null);
        node.setLockedDate(null);
        
        nodeRepository.save(node);
        eventPublisher.publishEvent(new NodeUnlockedEvent(node));
    }
    
    public List<Node> searchNodes(String query, Map<String, Object> filters, Pageable pageable) {
        // This is a simplified search - in production, use Elasticsearch
        return nodeRepository.findAll((root, criteriaQuery, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            
            // Text search
            if (query != null && !query.isEmpty()) {
                predicates.add(criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), 
                        "%" + query.toLowerCase() + "%"),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), 
                        "%" + query.toLowerCase() + "%")
                ));
            }
            
            // Apply filters
            if (filters != null) {
                if (filters.containsKey("mimeType")) {
                    predicates.add(criteriaBuilder.equal(root.get("mimeType"), 
                        filters.get("mimeType")));
                }
                if (filters.containsKey("createdBy")) {
                    predicates.add(criteriaBuilder.equal(root.get("createdBy"), 
                        filters.get("createdBy")));
                }
                if (filters.containsKey("status")) {
                    predicates.add(criteriaBuilder.equal(root.get("status"), 
                        NodeStatus.valueOf((String) filters.get("status"))));
                }
            }
            
            predicates.add(criteriaBuilder.equal(root.get("deleted"), false));
            
            return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }, pageable).getContent();
    }
    
    private void copyPermissions(Node source, Node target) {
        List<Permission> sourcePermissions = permissionRepository.findByNodeId(source.getId());
        for (Permission perm : sourcePermissions) {
            Permission copy = new Permission();
            copy.setNode(target);
            copy.setAuthority(perm.getAuthority());
            copy.setAuthorityType(perm.getAuthorityType());
            copy.setPermission(perm.getPermission());
            copy.setAllowed(perm.isAllowed());
            copy.setInherited(true);
            permissionRepository.save(copy);
        }
    }
    
    private boolean isDescendant(Node node, Node potentialAncestor) {
        Node current = node;
        while (current != null) {
            if (current.getId().equals(potentialAncestor.getId())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }
    
    private Node copyNodeRecursive(Node source, Node targetParent, String name, boolean deep) {
        Node copy;
        
        if (source instanceof Document) {
            Document docSource = (Document) source;
            Document docCopy = new Document();
            docCopy.setName(name);
            docCopy.setDescription(docSource.getDescription());
            docCopy.setMimeType(docSource.getMimeType());
            docCopy.setFileSize(docSource.getFileSize());
            docCopy.setContentId(docSource.getContentId());
            docCopy.setContentHash(docSource.getContentHash());
            copy = docCopy;
        } else {
            Folder folderCopy = new Folder();
            folderCopy.setName(name);
            folderCopy.setDescription(source.getDescription());
            copy = folderCopy;
        }
        
        copy.setParent(targetParent);
        copy.getProperties().putAll(source.getProperties());
        copy.getMetadata().putAll(source.getMetadata());
        
        copy = nodeRepository.save(copy);
        
        // Copy permissions
        copyPermissions(source, copy);
        
        // Copy children if deep copy and source is folder
        if (deep && source instanceof Folder) {
            List<Node> children = nodeRepository.findByParentIdAndDeletedFalse(source.getId());
            for (Node child : children) {
                copyNodeRecursive(child, (Folder) copy, child.getName(), true);
            }
        }
        
        return copy;
    }
    
    private void deleteNodeRecursive(Node node) {
        // Delete children first
        List<Node> children = nodeRepository.findByParentIdAndDeletedFalse(node.getId());
        for (Node child : children) {
            deleteNodeRecursive(child);
        }
        
        // Delete permissions
        permissionRepository.deleteByNodeId(node.getId());
        
        // Delete node
        nodeRepository.delete(node);
    }
    
    private void softDeleteNodeRecursive(Node node) {
        // Soft delete children
        nodeRepository.softDeleteByPathPrefix(node.getPath());
        
        // Soft delete node
        nodeRepository.softDelete(node.getId());
    }
}