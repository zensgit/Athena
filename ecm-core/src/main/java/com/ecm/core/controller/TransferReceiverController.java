package com.ecm.core.controller;

import com.ecm.core.entity.ReplicationDefinition;
import com.ecm.core.service.transfer.TransferReceiverHeaders;
import com.ecm.core.service.transfer.TransferReceiverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/transfer/receiver")
@Tag(name = "Transfer Receiver", description = "Dedicated remote transfer receiver/auth seam")
public class TransferReceiverController {

    private final TransferReceiverService transferReceiverService;

    @GetMapping("/verify")
    @Operation(summary = "Verify transfer receiver target folder")
    public ResponseEntity<TransferReceiverService.VerifyFolderResponse> verifyFolder(
        @RequestParam UUID folderId,
        @RequestHeader(value = TransferReceiverHeaders.USER_HEADER, required = false) String authUsername,
        @RequestHeader(value = TransferReceiverHeaders.SECRET_HEADER, required = false) String authSecret
    ) {
        return ResponseEntity.ok(transferReceiverService.verifyFolder(folderId, authUsername, authSecret));
    }

    @PostMapping("/folders")
    @Operation(summary = "Create a folder via the transfer receiver seam")
    public ResponseEntity<TransferReceiverService.CreateFolderResponse> createFolder(
        @RequestBody TransferReceiverService.CreateFolderRequest request,
        @RequestHeader(value = TransferReceiverHeaders.USER_HEADER, required = false) String authUsername,
        @RequestHeader(value = TransferReceiverHeaders.SECRET_HEADER, required = false) String authSecret
    ) {
        return ResponseEntity.status(201).body(transferReceiverService.createFolder(request, authUsername, authSecret));
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document via the transfer receiver seam")
    public ResponseEntity<TransferReceiverService.UploadDocumentResponse> uploadDocument(
        @RequestParam("file") MultipartFile file,
        @RequestParam UUID parentFolderId,
        @RequestParam(required = false) String description,
        @RequestParam(defaultValue = "RENAME") ReplicationDefinition.ConflictPolicy conflictPolicy,
        @RequestParam(required = false) String sourceRepositoryId,
        @RequestParam(required = false) UUID sourceNodeId,
        @RequestParam(required = false) UUID sourceParentNodeId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime sourceLastModifiedAt,
        @RequestHeader(value = TransferReceiverHeaders.USER_HEADER, required = false) String authUsername,
        @RequestHeader(value = TransferReceiverHeaders.SECRET_HEADER, required = false) String authSecret
    ) throws IOException {
        return ResponseEntity.status(201)
            .body(transferReceiverService.uploadDocument(
                file,
                parentFolderId,
                description,
                conflictPolicy,
                sourceRepositoryId,
                sourceNodeId,
                sourceParentNodeId,
                sourceLastModifiedAt,
                authUsername,
                authSecret
            ));
    }
}
