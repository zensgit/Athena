package com.ecm.core.alfresco;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Alfresco-compatible ServiceRegistry
 * Provides a central registry for all Alfresco-compatible services
 */
@Component
@RequiredArgsConstructor
public class AlfrescoServiceRegistry {
    
    private final AlfrescoNodeService nodeService;
    private final AlfrescoContentService contentService;
    private final AlfrescoSearchService searchService;
    private final AlfrescoPermissionService permissionService;
    
    public AlfrescoNodeService getNodeService() {
        return nodeService;
    }
    
    public AlfrescoContentService getContentService() {
        return contentService;
    }
    
    public AlfrescoSearchService getSearchService() {
        return searchService;
    }
    
    public AlfrescoPermissionService getPermissionService() {
        return permissionService;
    }
}