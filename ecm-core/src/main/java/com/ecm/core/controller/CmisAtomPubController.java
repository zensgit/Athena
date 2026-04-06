package com.ecm.core.controller;

import com.ecm.core.cmis.CmisAtomPubSerializer;
import com.ecm.core.cmis.CmisBrowserService;
import com.ecm.core.cmis.CmisModels;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * CMIS 1.1 AtomPub binding — read-only endpoints.
 * Reuses CmisBrowserService for data access and CmisAtomPubSerializer for XML output.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/cmis/atom", "/api/v1/cmis/atom"})
@Tag(name = "CMIS AtomPub", description = "CMIS 1.1 AtomPub binding (read-only)")
public class CmisAtomPubController {

    private static final MediaType ATOM_XML = MediaType.valueOf("application/atom+xml;charset=UTF-8");
    private static final MediaType ATOM_SERVICE = MediaType.valueOf("application/atomsvc+xml;charset=UTF-8");

    private final CmisBrowserService browserService;
    private final CmisAtomPubSerializer serializer;

    @GetMapping(produces = "application/atomsvc+xml")
    @Operation(summary = "Service document", description = "CMIS AtomPub service document with repository info")
    public ResponseEntity<String> getServiceDocument(HttpServletRequest request) {
        CmisModels.RepositoryInfo info = browserService.getRepositoryInfo();
        String baseUrl = resolveBaseUrl(request);
        return ResponseEntity.ok()
            .contentType(ATOM_SERVICE)
            .body(serializer.serializeServiceDocument(info, baseUrl));
    }

    @GetMapping(value = "/object", produces = "application/atom+xml")
    @Operation(summary = "Get object entry", description = "Fetch a single CMIS object as an Atom entry")
    public ResponseEntity<String> getObject(
            @RequestParam(required = false) String objectId,
            @RequestParam(required = false) String path,
            HttpServletRequest request) {
        CmisModels.ObjectEntry entry = browserService.getObject(objectId, path);
        String baseUrl = resolveBaseUrl(request);
        return ResponseEntity.ok()
            .contentType(ATOM_XML)
            .body(serializer.serializeObjectEntry(entry, baseUrl));
    }

    @GetMapping(value = "/children", produces = "application/atom+xml")
    @Operation(summary = "Get children feed", description = "List children of a folder as an Atom feed")
    public ResponseEntity<String> getChildren(
            @RequestParam(required = false) String objectId,
            @RequestParam(required = false) String path,
            @RequestParam(defaultValue = "0") int skipCount,
            @RequestParam(defaultValue = "25") int maxItems,
            HttpServletRequest request) {
        CmisModels.ChildrenResponse children = browserService.getChildren(objectId, path, skipCount, maxItems);
        String baseUrl = resolveBaseUrl(request);
        return ResponseEntity.ok()
            .contentType(ATOM_XML)
            .body(serializer.serializeChildrenFeed(children, baseUrl));
    }

    private String resolveBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String contextPath = request.getContextPath();
        String base = scheme + "://" + host;
        if (("http".equals(scheme) && port != 80) || ("https".equals(scheme) && port != 443)) {
            base += ":" + port;
        }
        return base + contextPath + "/api/cmis/atom";
    }
}
