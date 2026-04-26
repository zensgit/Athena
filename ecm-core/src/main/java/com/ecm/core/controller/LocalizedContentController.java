package com.ecm.core.controller;

import com.ecm.core.service.LocalizedContentService;
import com.ecm.core.service.LocalizedContentService.LocalizedContentDto;
import com.ecm.core.service.LocalizedContentService.LocalizedContentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/nodes/{nodeId}")
@Tag(name = "Multilingual Content", description = "Per-node locale-keyed title and description overrides")
public class LocalizedContentController {

    private final LocalizedContentService localizedContentService;

    @GetMapping("/localizations")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List all localizations for a node", description = "Returns all locale entries for the given node, ordered by locale")
    public ResponseEntity<List<LocalizedContentDto>> listLocalizations(@PathVariable UUID nodeId) {
        return ResponseEntity.ok(localizedContentService.listForNode(nodeId));
    }

    @PutMapping("/localizations/{locale}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Upsert a localization for a node", description = "Creates or updates the title/description for the given locale. The path locale is authoritative; any locale field in the request body is ignored.")
    public ResponseEntity<LocalizedContentDto> upsertLocalization(
        @PathVariable UUID nodeId,
        @PathVariable String locale,
        @RequestBody LocalizedContentRequest request
    ) {
        return ResponseEntity.ok(localizedContentService.upsert(nodeId, locale, request));
    }

    @DeleteMapping("/localizations/{locale}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete a localization for a node", description = "Removes the locale entry; no-op if it does not exist")
    public ResponseEntity<Void> deleteLocalization(
        @PathVariable UUID nodeId,
        @PathVariable String locale
    ) {
        localizedContentService.delete(nodeId, locale);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/localization")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Resolve the best-match localization for a node", description = "Parses the Accept-Language header and returns the best matching locale entry; 404 if no localizations exist")
    public ResponseEntity<LocalizedContentDto> resolveLocalization(
        @PathVariable UUID nodeId,
        HttpServletRequest httpRequest
    ) {
        String acceptLanguage = httpRequest.getHeader("Accept-Language");
        return localizedContentService.resolve(nodeId, acceptLanguage)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
