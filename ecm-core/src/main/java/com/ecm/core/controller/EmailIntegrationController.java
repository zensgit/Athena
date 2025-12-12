package com.ecm.core.controller;

import com.ecm.core.entity.Document;
import com.ecm.core.integration.email.EmailIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integration/email")
@RequiredArgsConstructor
@Tag(name = "Integration: Email", description = "Email archiving endpoints")
public class EmailIntegrationController {

    private final EmailIngestionService emailIngestionService;

    @PostMapping(value = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Ingest Email", description = "Upload and archive an email file (.eml)")
    public ResponseEntity<Document> ingestEmail(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) UUID folderId) {
        
        Document document = emailIngestionService.ingestEmail(file, folderId);
        return ResponseEntity.ok(document);
    }
}
