package com.ecm.core.controller;

import com.ecm.core.entity.Document;
import com.ecm.core.service.PdfManipulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tools/pdf")
@RequiredArgsConstructor
@Tag(name = "PDF Tools", description = "PDF manipulation utilities")
public class PdfManipulationController {

    private final PdfManipulationService pdfService;

    @PostMapping("/merge")
    @Operation(summary = "Merge PDFs", description = "Merge multiple PDF documents into one")
    public ResponseEntity<Document> mergePdfs(@RequestBody MergeRequest request) throws IOException {
        Document merged = pdfService.mergePdfs(
            request.documentIds(), 
            request.newName(), 
            request.targetFolderId()
        );
        return ResponseEntity.ok(merged);
    }

    @PostMapping("/{documentId}/split")
    @Operation(summary = "Split PDF", description = "Split a PDF document into single pages")
    public ResponseEntity<List<Document>> splitPdf(
            @PathVariable UUID documentId,
            @RequestParam(required = false) UUID targetFolderId) throws IOException {
        
        List<Document> result = pdfService.splitPdf(documentId, targetFolderId);
        return ResponseEntity.ok(result);
    }

    // DTOs
    public record MergeRequest(List<UUID> documentIds, String newName, UUID targetFolderId) {}
}