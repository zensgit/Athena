package com.ecm.core.alfresco;

import com.ecm.core.entity.AuthorityType;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.PermissionType;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * Alfresco-compatible PermissionService implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlfrescoPermissionService {
    
    private final SecurityService securityService;
    private final NodeService nodeService;
    
    /**
     * Check if user has permission (Alfresco-compatible method)
     */
    public AccessStatus hasPermission(NodeRef nodeRef, String permission) {
        try {
            Node node = nodeService.getNode(UUID.fromString(nodeRef.getId()));
            PermissionType permType = mapAlfrescoPermission(permission);
            
            boolean hasPermission = securityService.hasPermission(node, permType);
            return hasPermission ? AccessStatus.ALLOWED : AccessStatus.DENIED;
            
        } catch (Exception e) {
            log.error("Error checking permission for node: {}", nodeRef, e);
            return AccessStatus.DENIED;
        }
    }
    
    /**
     * Set permission (Alfresco-compatible method)
     */
    public void setPermission(NodeRef nodeRef, String authority, String permission, 
                              boolean allow) {
        try {
            Node node = nodeService.getNode(UUID.fromString(nodeRef.getId()));
            PermissionType permType = mapAlfrescoPermission(permission);
            AuthorityType authType = determineAuthorityType(authority);
            
            securityService.setPermission(node, authority, authType, permType, allow);
            
        } catch (Exception e) {
            log.error("Error setting permission for node: {}", nodeRef, e);
            throw new RuntimeException("Failed to set permission", e);
        }
    }
    
    /**
     * Delete permission (Alfresco-compatible method)
     */
    public void deletePermission(NodeRef nodeRef, String authority, String permission) {
        try {
            Node node = nodeService.getNode(UUID.fromString(nodeRef.getId()));
            PermissionType permType = mapAlfrescoPermission(permission);
            
            securityService.removePermission(node, authority, permType);
            
        } catch (Exception e) {
            log.error("Error deleting permission for node: {}", nodeRef, e);
            throw new RuntimeException("Failed to delete permission", e);
        }
    }
    
    /**
     * Get all permissions for a node (Alfresco-compatible method)
     */
    public Set<AccessPermission> getAllSetPermissions(NodeRef nodeRef) {
        try {
            Node node = nodeService.getNode(UUID.fromString(nodeRef.getId()));
            // This would convert our permissions to Alfresco format
            return Set.of(); // Simplified
            
        } catch (Exception e) {
            log.error("Error getting permissions for node: {}", nodeRef, e);
            return Set.of();
        }
    }
    
    /**
     * Set inheritance (Alfresco-compatible method)
     */
    public void setInheritParentPermissions(NodeRef nodeRef, boolean inherit) {
        try {
            Node node = nodeService.getNode(UUID.fromString(nodeRef.getId()));
            securityService.setInheritPermissions(node, inherit);
            
        } catch (Exception e) {
            log.error("Error setting inheritance for node: {}", nodeRef, e);
            throw new RuntimeException("Failed to set inheritance", e);
        }
    }
    
    private PermissionType mapAlfrescoPermission(String alfrescoPermission) {
        switch (alfrescoPermission.toLowerCase()) {
            case "read":
            case "consumer":
                return PermissionType.READ;
            case "write":
            case "editor":
                return PermissionType.WRITE;
            case "delete":
                return PermissionType.DELETE;
            case "coordinator":
            case "collaborator":
                return PermissionType.WRITE;
            case "contributor":
                return PermissionType.CREATE_CHILDREN;
            default:
                return PermissionType.READ;
        }
    }
    
    private AuthorityType determineAuthorityType(String authority) {
        if (authority.startsWith("GROUP_")) {
            return AuthorityType.GROUP;
        } else if (authority.startsWith("ROLE_")) {
            return AuthorityType.ROLE;
        } else {
            return AuthorityType.USER;
        }
    }
}

enum AccessStatus {
    ALLOWED,
    DENIED,
    UNDETERMINED
}

@Data
class AccessPermission {
    private String authority;
    private String permission;
    private AccessStatus accessStatus;
    private boolean inherited;
}