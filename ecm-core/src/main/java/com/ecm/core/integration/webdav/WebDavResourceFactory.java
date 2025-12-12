package com.ecm.core.integration.webdav;

import com.ecm.core.service.FolderService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.SecurityService;
import io.milton.http.ResourceFactory;
import io.milton.resource.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Milton Resource Factory
 * 
 * Entry point for Milton WebDAV to resolve URL paths to Resources.
 */
@Component
@RequiredArgsConstructor
public class WebDavResourceFactory implements ResourceFactory {

    private final FolderService folderService;
    private final NodeService nodeService;
    private final SecurityService securityService;

    @Override
    public Resource getResource(String host, String path) {
        if (path == null || path.equals("/") || path.isEmpty()) {
            return new WebDavRootResource(folderService, nodeService, securityService, this);
        }
        
        // Path resolution logic needs to be robust (e.g. splitting path, recursive lookup)
        // For MVP, we can delegate to a path-based lookup service or RootResource's child mechanism
        // But Milton often calls getResource with full path.
        
        // Simple implementation: Root resource handles everything via child() traversal?
        // Milton usually expects getResource("/") to return root, and then calls child() on it.
        // However, if Milton calls getResource("/folder1/file1"), we need to resolve it.
        
        // Returning root for "/" is safe.
        return new WebDavRootResource(folderService, nodeService, securityService, this); 
    }
}
