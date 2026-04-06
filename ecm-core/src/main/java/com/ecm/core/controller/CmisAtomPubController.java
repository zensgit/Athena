package com.ecm.core.controller;

import com.ecm.core.cmis.CmisAtomPubSerializer;
import com.ecm.core.cmis.CmisBrowserService;
import com.ecm.core.cmis.CmisModels;
import com.ecm.core.cmis.CmisMutationService;
import com.ecm.core.entity.Document;
import com.ecm.core.cmis.CmisObjectFactory;
import com.ecm.core.service.CheckOutCheckInService;
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
    private final CmisMutationService mutationService;
    private final CheckOutCheckInService checkOutCheckInService;
    private final CmisObjectFactory objectFactory;
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

    // ---- mutations ----------------------------------------------------------

    @PostMapping(value = "/folder", consumes = "application/json", produces = "application/atom+xml")
    @Operation(summary = "Create folder")
    public ResponseEntity<String> createFolder(@RequestBody CmisModels.MutationRequest request, HttpServletRequest httpRequest) {
        CmisModels.MutationResponse response = mutationService.createFolder(request);
        return ResponseEntity.status(201).contentType(ATOM_XML).body(serializer.serializeMutationResponse(response, resolveBaseUrl(httpRequest)));
    }

    @PostMapping(value = "/document", consumes = "application/json", produces = "application/atom+xml")
    @Operation(summary = "Create document")
    public ResponseEntity<String> createDocument(@RequestBody CmisModels.MutationRequest request, HttpServletRequest httpRequest) {
        CmisModels.MutationResponse response = mutationService.createDocument(request);
        return ResponseEntity.status(201).contentType(ATOM_XML).body(serializer.serializeMutationResponse(response, resolveBaseUrl(httpRequest)));
    }

    @PutMapping(value = "/object", consumes = "application/json", produces = "application/atom+xml")
    @Operation(summary = "Update properties")
    public ResponseEntity<String> updateProperties(@RequestBody CmisModels.MutationRequest request, HttpServletRequest httpRequest) {
        CmisModels.MutationResponse response = mutationService.updateProperties(request);
        return ResponseEntity.ok().contentType(ATOM_XML).body(serializer.serializeMutationResponse(response, resolveBaseUrl(httpRequest)));
    }

    @DeleteMapping(value = "/object", produces = "application/atom+xml")
    @Operation(summary = "Delete object")
    public ResponseEntity<String> deleteObject(
            @RequestParam String objectId,
            HttpServletRequest httpRequest) {
        CmisModels.MutationRequest request = new CmisModels.MutationRequest(objectId, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        CmisModels.MutationResponse response = mutationService.deleteObject(request);
        return ResponseEntity.ok().contentType(ATOM_XML).body(serializer.serializeMutationResponse(response, resolveBaseUrl(httpRequest)));
    }

    @PostMapping(value = "/checkout", produces = "application/atom+xml")
    @Operation(summary = "Check out document (creates working copy)")
    public ResponseEntity<String> checkOut(@RequestParam String objectId, HttpServletRequest httpRequest) {
        java.util.UUID docId = java.util.UUID.fromString(objectId.trim());
        Document wc = checkOutCheckInService.checkout(docId);
        CmisModels.MutationResponse response = new CmisModels.MutationResponse(
            CmisObjectFactory.REPOSITORY_ID, "checkOut", objectFactory.fromNode(wc), null, "Checked out");
        return ResponseEntity.ok().contentType(ATOM_XML).body(serializer.serializeMutationResponse(response, resolveBaseUrl(httpRequest)));
    }

    @PostMapping(value = "/checkin", produces = "application/atom+xml")
    @Operation(summary = "Check in working copy")
    public ResponseEntity<String> checkIn(
            @RequestParam String objectId,
            @RequestParam(defaultValue = "false") boolean keepCheckedOut,
            HttpServletRequest httpRequest) {
        java.util.UUID wcId = java.util.UUID.fromString(objectId.trim());
        Document original = checkOutCheckInService.checkin(wcId, keepCheckedOut);
        CmisModels.MutationResponse response = new CmisModels.MutationResponse(
            CmisObjectFactory.REPOSITORY_ID, "checkIn", objectFactory.fromNode(original), null, "Checked in");
        return ResponseEntity.ok().contentType(ATOM_XML).body(serializer.serializeMutationResponse(response, resolveBaseUrl(httpRequest)));
    }

    @PostMapping(value = "/cancel-checkout", produces = "application/atom+xml")
    @Operation(summary = "Cancel checkout")
    public ResponseEntity<String> cancelCheckOut(@RequestParam String objectId, HttpServletRequest httpRequest) {
        java.util.UUID docId = java.util.UUID.fromString(objectId.trim());
        Document original = checkOutCheckInService.cancelCheckout(docId);
        CmisModels.MutationResponse response = new CmisModels.MutationResponse(
            CmisObjectFactory.REPOSITORY_ID, "cancelCheckOut", objectFactory.fromNode(original), null, "Checkout cancelled");
        return ResponseEntity.ok().contentType(ATOM_XML).body(serializer.serializeMutationResponse(response, resolveBaseUrl(httpRequest)));
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
