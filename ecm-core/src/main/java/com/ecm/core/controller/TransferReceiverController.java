package com.ecm.core.controller;

import com.ecm.core.service.transfer.TransferReceiverHeaders;
import com.ecm.core.service.transfer.TransferReceiverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
        @RequestHeader(value = TransferReceiverHeaders.USER_HEADER, required = false) String authUsername,
        @RequestHeader(value = TransferReceiverHeaders.SECRET_HEADER, required = false) String authSecret
    ) throws IOException {
        return ResponseEntity.status(201)
            .body(transferReceiverService.uploadDocument(file, parentFolderId, description, authUsername, authSecret));
    }
}
