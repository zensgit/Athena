package com.ecm.core.integration.wopi.controller;

import com.ecm.core.integration.wopi.model.WopiCheckFileInfoResponse;
import com.ecm.core.integration.wopi.service.WopiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;

/**
 * WOPI Host Controller
 * 
 * Standard endpoints defined by the WOPI protocol.
 * URL Pattern: /wopi/files/{id}
 */
@Slf4j
@RestController
@RequestMapping("/wopi/files")
@RequiredArgsConstructor
@Tag(name = "Integration: WOPI", description = "WOPI Host endpoints for Office Online / Collabora")
public class WopiHostController {

    private final WopiService wopiService;

    @GetMapping("/{id}")
    @Operation(summary = "CheckFileInfo", description = "WOPI CheckFileInfo operation")
    public ResponseEntity<WopiCheckFileInfoResponse> checkFileInfo(
            @PathVariable UUID id,
            @RequestParam("access_token") String accessToken) {
        
        return ResponseEntity.ok(wopiService.checkFileInfo(id, accessToken));
    }

    @GetMapping("/{id}/contents")
    @Operation(summary = "GetFile", description = "WOPI GetFile operation")
    public ResponseEntity<InputStreamResource> getFile(
            @PathVariable UUID id,
            @RequestParam("access_token") String accessToken) throws IOException {
        
        // Verify token (skipped for MVP)
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(new InputStreamResource(wopiService.getFileContent(id)));
    }

    @PostMapping("/{id}/contents")
    @Operation(summary = "PutFile", description = "WOPI PutFile operation")
    public ResponseEntity<Void> putFile(
            @PathVariable UUID id,
            @RequestParam("access_token") String accessToken,
            HttpServletRequest request) throws IOException {
        
        // WOPI sends content in body
        wopiService.putFile(id, request.getInputStream(), request.getContentLengthLong());
        return ResponseEntity.ok().build();
    }
}
