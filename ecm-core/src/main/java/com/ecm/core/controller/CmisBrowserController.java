package com.ecm.core.controller;

import com.ecm.core.cmis.CmisBrowserService;
import com.ecm.core.cmis.CmisMutationService;
import com.ecm.core.cmis.CmisModels;
import com.ecm.core.cmis.CmisQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.NoSuchElementException;

@RestController
@RequestMapping({"/api/cmis/browser", "/api/v1/cmis/browser"})
@RequiredArgsConstructor
@Tag(name = "CMIS Browser Binding", description = "Minimal CMIS browser binding backbone")
public class CmisBrowserController {

    private final CmisBrowserService cmisBrowserService;
    private final CmisQueryService cmisQueryService;
    private final CmisMutationService cmisMutationService;

    @GetMapping
    @Operation(summary = "CMIS browser binding read entrypoint")
    public ResponseEntity<?> browser(
        @Parameter(description = "CMIS selector")
        @RequestParam(name = "cmisselector", defaultValue = "repositoryInfo") String selector,
        @RequestParam(required = false) String objectId,
        @RequestParam(required = false) String path,
        @RequestParam(required = false) String statement,
        @RequestParam(defaultValue = "0") int skipCount,
        @RequestParam(defaultValue = "50") int maxItems
    ) {
        return switch (selector) {
            case "repositoryInfo" -> ResponseEntity.ok(cmisBrowserService.getRepositoryInfo());
            case "typeChildren" -> ResponseEntity.ok(cmisBrowserService.getTypeChildren());
            case "object" -> ResponseEntity.ok(cmisBrowserService.getObject(objectId, path));
            case "children" -> ResponseEntity.ok(cmisBrowserService.getChildren(objectId, path, skipCount, maxItems));
            case "query" -> ResponseEntity.ok(cmisQueryService.query(statement, skipCount, maxItems));
            default -> throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unsupported cmisselector: " + selector
            );
        };
    }

    @PostMapping
    @Operation(summary = "CMIS browser binding mutation entrypoint")
    public ResponseEntity<?> mutate(
        @Parameter(description = "CMIS action")
        @RequestParam(name = "cmisaction") String action,
        @RequestBody(required = false) CmisModels.MutationRequest request
    ) {
        CmisModels.MutationRequest effectiveRequest = request != null
            ? request
            : new CmisModels.MutationRequest(null, null, null, null, null, null, null, null, null, null);
        try {
            return ResponseEntity.ok(dispatchMutation(action, effectiveRequest));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (SecurityException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        }
    }

    private CmisModels.MutationResponse dispatchMutation(String action, CmisModels.MutationRequest request) {
        return switch (action) {
            case "createFolder" -> cmisMutationService.createFolder(request);
            case "createDocument" -> cmisMutationService.createDocument(request);
            case "updateProperties" -> cmisMutationService.updateProperties(request);
            case "deleteObject" -> cmisMutationService.deleteObject(request);
            default -> throw new IllegalArgumentException("Unsupported cmisaction: " + action);
        };
    }
}
