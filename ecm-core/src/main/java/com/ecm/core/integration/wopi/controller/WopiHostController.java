package com.ecm.core.integration.wopi.controller;

import com.ecm.core.integration.wopi.model.WopiCheckFileInfoResponse;
import com.ecm.core.integration.wopi.service.WopiAccessTokenService;
import com.ecm.core.integration.wopi.service.WopiService;
import com.ecm.core.integration.wopi.service.WopiLockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
    private final WopiLockService wopiLockService;
    private final WopiAccessTokenService accessTokenService;

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
        
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(new InputStreamResource(wopiService.getFileContent(id, accessToken)));
    }

    @PostMapping("/{id}/contents")
    @Operation(summary = "PutFile", description = "WOPI PutFile operation")
    public ResponseEntity<Void> putFile(
            @PathVariable UUID id,
            @RequestParam("access_token") String accessToken,
            @RequestHeader(value = "X-WOPI-Lock", required = false) String lock,
            HttpServletRequest request) throws IOException {
        
        // WOPI sends content in body
        if (lock != null && !lock.isBlank()) {
            String current = wopiLockService.getLock(id);
            if (current != null && !current.equals(lock)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .header("X-WOPI-Lock", current)
                    .build();
            }
        }
        wopiService.putFile(id, accessToken, request.getInputStream(), request.getContentLengthLong());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}")
    @Operation(summary = "WOPI lock operations", description = "Handle WOPI LOCK/UNLOCK/REFRESH_LOCK/GET_LOCK requests")
    public ResponseEntity<Void> lockOperations(
        @PathVariable UUID id,
        @RequestParam("access_token") String accessToken,
        @RequestHeader(value = "X-WOPI-Override", required = false) String override,
        @RequestHeader(value = "X-WOPI-Lock", required = false) String lock,
        @RequestHeader(value = "X-WOPI-OldLock", required = false) String oldLock
    ) {
        WopiAccessTokenService.TokenInfo tokenInfo = accessTokenService.validate(id, accessToken);

        if (override == null || override.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }

        String op = override.trim().toUpperCase();
        if (!tokenInfo.canWrite() && !"GET_LOCK".equals(op)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return switch (op) {
            case "LOCK" -> {
                WopiLockService.LockResult result = wopiLockService.lock(id, lock);
                if (result.ok()) {
                    yield ResponseEntity.ok().build();
                }
                yield ResponseEntity.status(HttpStatus.CONFLICT)
                    .header("X-WOPI-Lock", result.currentLock() != null ? result.currentLock() : "")
                    .build();
            }
            case "UNLOCK" -> {
                WopiLockService.LockResult result = wopiLockService.unlock(id, lock);
                if (result.ok()) {
                    yield ResponseEntity.ok().build();
                }
                yield ResponseEntity.status(HttpStatus.CONFLICT)
                    .header("X-WOPI-Lock", result.currentLock() != null ? result.currentLock() : "")
                    .build();
            }
            case "REFRESH_LOCK" -> {
                WopiLockService.LockResult result = wopiLockService.refresh(id, lock);
                if (result.ok()) {
                    yield ResponseEntity.ok().build();
                }
                yield ResponseEntity.status(HttpStatus.CONFLICT)
                    .header("X-WOPI-Lock", result.currentLock() != null ? result.currentLock() : "")
                    .build();
            }
            case "GET_LOCK" -> {
                String current = wopiLockService.getLock(id);
                yield ResponseEntity.ok()
                    .header("X-WOPI-Lock", current != null ? current : "")
                    .build();
            }
            case "UNLOCK_AND_RELOCK" -> {
                WopiLockService.LockResult result = wopiLockService.lock(id, lock, oldLock);
                if (result.ok()) {
                    yield ResponseEntity.ok().build();
                }
                yield ResponseEntity.status(HttpStatus.CONFLICT)
                    .header("X-WOPI-Lock", result.currentLock() != null ? result.currentLock() : "")
                    .build();
            }
            default -> ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        };
    }
}
