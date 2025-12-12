package com.ecm.core.integration.webdav;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.SecurityService;
import io.milton.http.Auth;
import io.milton.http.Request;
import io.milton.resource.CollectionResource;
import io.milton.resource.MakeCollectionableResource;
import io.milton.resource.Resource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
public class WebDavFolderResource implements MakeCollectionableResource {

    @Getter
    private final Folder folder;
    private final FolderService folderService;
    private final NodeService nodeService;
    private final SecurityService securityService;
    private final WebDavResourceFactory resourceFactory;

    public WebDavFolderResource(Folder folder, FolderService folderService, NodeService nodeService, 
                                SecurityService securityService, WebDavResourceFactory resourceFactory) {
        this.folder = folder;
        this.folderService = folderService;
        this.nodeService = nodeService;
        this.securityService = securityService;
        this.resourceFactory = resourceFactory;
    }

    @Override
    public CollectionResource createCollection(String newName) {
        // Create new subfolder
        // Note: Auth check is done in authorise()
        Folder newFolder = new Folder(); // Need DTO logic mapping
        // Calling service directly
        // folderService.createFolder(...)
        log.info("WebDAV: Create folder '{}'", newName);
        return null; // Implement actual creation logic
    }

    @Override
    public Resource child(String childName) {
        // Lookup child by name in this folder
        // Use nodeService/folderService to find child
        return null; 
    }

    @Override
    public List<? extends Resource> getChildren() {
        List<Resource> resources = new ArrayList<>();
        // Fetch children
        var nodes = folderService.getFolderContents(folder.getId(), Pageable.unpaged());
        for (Node node : nodes) {
            if (node instanceof Folder) {
                resources.add(new WebDavFolderResource((Folder) node, folderService, nodeService, securityService, resourceFactory));
            } else if (node instanceof Document) {
                resources.add(new WebDavFileResource((Document) node, nodeService, securityService));
            }
        }
        return resources;
    }

    @Override
    public String getUniqueId() {
        return folder.getId().toString();
    }

    @Override
    public String getName() {
        return folder.getName();
    }

    @Override
    public Object authenticate(String user, String password) {
        return user;
    }

    @Override
    public boolean authorise(Request request, Request.Method method, Auth auth) {
        return auth != null; // Implement actual permission check based on method
    }

    @Override
    public String getRealm() {
        return "AthenaECM";
    }

    @Override
    public Date getModifiedDate() {
        return java.sql.Timestamp.valueOf(folder.getLastModifiedDate());
    }

    @Override
    public String checkRedirect(Request request) {
        return null;
    }

    @Override
    public Date getCreateDate() {
        return java.sql.Timestamp.valueOf(folder.getCreatedDate());
    }
}
