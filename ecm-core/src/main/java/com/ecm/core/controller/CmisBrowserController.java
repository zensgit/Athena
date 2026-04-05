package com.ecm.core.controller;

import com.ecm.core.cmis.CmisBrowserService;
import com.ecm.core.cmis.CmisModels;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping({"/api/cmis/browser", "/api/v1/cmis/browser"})
@RequiredArgsConstructor
@Tag(name = "CMIS Browser Binding", description = "Read-only CMIS browser binding backbone")
public class CmisBrowserController {

    private final CmisBrowserService cmisBrowserService;

    @GetMapping
    @Operation(summary = "CMIS browser binding entrypoint")
    public ResponseEntity<?> browser(
        @Parameter(description = "CMIS selector")
        @RequestParam(name = "cmisselector", defaultValue = "repositoryInfo") String selector,
        @RequestParam(required = false) String objectId,
        @RequestParam(required = false) String path,
        @RequestParam(defaultValue = "0") int skipCount,
        @RequestParam(defaultValue = "50") int maxItems
    ) {
        return switch (selector) {
            case "repositoryInfo" -> ResponseEntity.ok(cmisBrowserService.getRepositoryInfo());
            case "typeChildren" -> ResponseEntity.ok(cmisBrowserService.getTypeChildren());
            case "object" -> ResponseEntity.ok(cmisBrowserService.getObject(objectId, path));
            case "children" -> ResponseEntity.ok(cmisBrowserService.getChildren(objectId, path, skipCount, maxItems));
            default -> throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unsupported cmisselector: " + selector
            );
        };
    }
}
