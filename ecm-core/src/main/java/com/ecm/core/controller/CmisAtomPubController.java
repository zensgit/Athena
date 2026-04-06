package com.ecm.core.controller;

import com.ecm.core.cmis.CmisAtomPubSerializer;
import com.ecm.core.cmis.CmisBrowserService;
import com.ecm.core.cmis.CmisContentVersioningService;
import com.ecm.core.cmis.CmisModels;
import com.ecm.core.cmis.CmisMutationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.NoSuchElementException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * CMIS 1.1 AtomPub binding.
 * Reuses browser-side CMIS services so AtomPub and browser binding stay on the same contracts.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/cmis/atom", "/api/v1/cmis/atom"})
@Tag(name = "CMIS AtomPub", description = "CMIS 1.1 AtomPub binding")
public class CmisAtomPubController {

    private static final MediaType ATOM_XML = MediaType.valueOf("application/atom+xml;charset=UTF-8");
    private static final MediaType ATOM_SERVICE = MediaType.valueOf("application/atomsvc+xml;charset=UTF-8");

    private final CmisBrowserService browserService;
    private final CmisMutationService mutationService;
    private final CmisContentVersioningService contentVersioningService;
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
        try {
            CmisModels.ObjectEntry entry = browserService.getObject(objectId, path);
            String baseUrl = resolveBaseUrl(request);
            return ResponseEntity.ok()
                .contentType(ATOM_XML)
                .body(serializer.serializeObjectEntry(entry, baseUrl));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage(), ex);
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(NOT_FOUND, ex.getMessage(), ex);
        } catch (SecurityException ex) {
            throw new ResponseStatusException(FORBIDDEN, ex.getMessage(), ex);
        }
    }

    @GetMapping(value = "/children", produces = "application/atom+xml")
    @Operation(summary = "Get children feed", description = "List children of a folder as an Atom feed")
    public ResponseEntity<String> getChildren(
            @RequestParam(required = false) String objectId,
            @RequestParam(required = false) String path,
            @RequestParam(defaultValue = "0") int skipCount,
            @RequestParam(defaultValue = "25") int maxItems,
            HttpServletRequest request) {
        try {
            CmisModels.ChildrenResponse children = browserService.getChildren(objectId, path, skipCount, maxItems);
            String baseUrl = resolveBaseUrl(request);
            return ResponseEntity.ok()
                .contentType(ATOM_XML)
                .body(serializer.serializeChildrenFeed(children, baseUrl));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage(), ex);
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(NOT_FOUND, ex.getMessage(), ex);
        } catch (SecurityException ex) {
            throw new ResponseStatusException(FORBIDDEN, ex.getMessage(), ex);
        }
    }

    // ---- mutations ----------------------------------------------------------

    @PostMapping(value = "/folder", consumes = "application/json", produces = "application/atom+xml")
    @Operation(summary = "Create folder")
    public ResponseEntity<String> createFolder(@RequestBody CmisModels.MutationRequest request, HttpServletRequest httpRequest) {
        return atomMutationResponse(() -> mutationService.createFolder(request), httpRequest, 201);
    }

    @PostMapping(value = "/document", consumes = "application/json", produces = "application/atom+xml")
    @Operation(summary = "Create document")
    public ResponseEntity<String> createDocument(@RequestBody CmisModels.MutationRequest request, HttpServletRequest httpRequest) {
        return atomMutationResponse(() -> mutationService.createDocument(request), httpRequest, 201);
    }

    @PutMapping(value = "/object", consumes = "application/json", produces = "application/atom+xml")
    @Operation(summary = "Update properties")
    public ResponseEntity<String> updateProperties(@RequestBody CmisModels.MutationRequest request, HttpServletRequest httpRequest) {
        return atomMutationResponse(() -> mutationService.updateProperties(request), httpRequest, 200);
    }

    @DeleteMapping(value = "/object", produces = "application/atom+xml")
    @Operation(summary = "Delete object")
    public ResponseEntity<String> deleteObject(
            @RequestParam String objectId,
            HttpServletRequest httpRequest) {
        return atomMutationResponse(() -> mutationService.deleteObject(objectRequest(objectId)), httpRequest, 200);
    }

    @PostMapping(value = "/checkout", produces = "application/atom+xml")
    @Operation(summary = "Check out document (creates working copy)")
    public ResponseEntity<String> checkOut(@RequestParam String objectId, HttpServletRequest httpRequest) {
        return atomMutationResponse(() -> contentVersioningService.checkOutWorkingCopy(objectRequest(objectId)), httpRequest, 200);
    }

    @PostMapping(value = "/checkin", produces = "application/atom+xml")
    @Operation(summary = "Check in working copy")
    public ResponseEntity<String> checkIn(
            @RequestParam String objectId,
            @RequestParam(defaultValue = "false") boolean keepCheckedOut,
            HttpServletRequest httpRequest) {
        return atomMutationResponse(() -> contentVersioningService.checkInWorkingCopy(checkInRequest(objectId, keepCheckedOut)), httpRequest, 200);
    }

    @PostMapping(value = "/cancel-checkout", produces = "application/atom+xml")
    @Operation(summary = "Cancel checkout")
    public ResponseEntity<String> cancelCheckOut(@RequestParam String objectId, HttpServletRequest httpRequest) {
        return atomMutationResponse(() -> contentVersioningService.cancelWorkingCopyCheckout(objectRequest(objectId)), httpRequest, 200);
    }

    private ResponseEntity<String> atomMutationResponse(ThrowingMutationSupplier supplier, HttpServletRequest request, int statusCode) {
        try {
            String baseUrl = resolveBaseUrl(request);
            CmisModels.MutationResponse response = supplier.get();
            return ResponseEntity.status(statusCode)
                .contentType(ATOM_XML)
                .body(serializer.serializeMutationResponse(response, baseUrl));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage(), ex);
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(NOT_FOUND, ex.getMessage(), ex);
        } catch (SecurityException ex) {
            throw new ResponseStatusException(FORBIDDEN, ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "CMIS AtomPub mutation failed", ex);
        }
    }

    private CmisModels.MutationRequest objectRequest(String objectId) {
        return new CmisModels.MutationRequest(
            objectId,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    private CmisModels.MutationRequest checkInRequest(String objectId, boolean keepCheckedOut) {
        return new CmisModels.MutationRequest(
            objectId,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            keepCheckedOut
        );
    }

    @FunctionalInterface
    private interface ThrowingMutationSupplier {
        CmisModels.MutationResponse get() throws IOException;
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
