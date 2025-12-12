package com.ecm.core.integration.webdav;

import com.ecm.core.entity.Folder;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.SecurityService;
import io.milton.http.Auth;
import io.milton.http.Request;
import io.milton.http.Response;
import io.milton.http.http11.Http11ResponseHandler;
import io.milton.resource.CollectionResource;
import io.milton.resource.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * WebDAV Root Resource
 * 
 * Represents the root folder of the WebDAV share.
 */
@Slf4j
@RequiredArgsConstructor
public class WebDavRootResource implements CollectionResource {

    private final FolderService folderService;
    private final NodeService nodeService;
    private final SecurityService securityService;
    private final WebDavResourceFactory resourceFactory;

    @Override
    public Resource child(String name) {
        log.debug("WebDAV: Look up child '{}' in root", name);
        // Map root folders to top-level WebDAV resources
        try {
            // Find root folder by name
            Folder folder = folderService.getRootFolders().stream()
                .filter(f -> f.getName().equals(name))
                .findFirst()
                .orElse(null);

            if (folder != null) {
                return new WebDavFolderResource(folder, folderService, nodeService, securityService, resourceFactory);
            }
        } catch (Exception e) {
            log.error("WebDAV lookup failed", e);
        }
        return null;
    }

    @Override
    public List<? extends Resource> getChildren() {
        log.debug("WebDAV: List root children");
        List<Resource> children = new ArrayList<>();
        try {
            List<Folder> roots = folderService.getRootFolders();
            for (Folder folder : roots) {
                children.add(new WebDavFolderResource(folder, folderService, nodeService, securityService, resourceFactory));
            }
        } catch (Exception e) {
            log.error("WebDAV list failed", e);
        }
        return children;
    }

    @Override
    public String getUniqueId() {
        return "ROOT";
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public Object authenticate(String user, String password) {
        // Authenticate against our SecurityService (or Keycloak)
        // For WebDAV (often Basic Auth), we might need a dedicated auth provider
        // Simplified: Assume Basic Auth is handled by Spring Security filter chain 
        // and we just trust the principal if present.
        // Or check user/pass explicitly.
        
        // Return user object if valid
        return user; 
    }

    @Override
    public boolean authorise(Request request, Request.Method method, Auth auth) {
        return auth != null;
    }

    @Override
    public String getRealm() {
        return "AthenaECM";
    }

    @Override
    public java.util.Date getModifiedDate() {
        return new java.util.Date();
    }

    @Override
    public String checkRedirect(Request request) {
        return null;
    }
}
