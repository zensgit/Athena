package com.ecm.core.controller;

import com.ecm.core.dto.NodeDto;
import com.ecm.core.dto.PdfAnnotationSaveRequest;
import com.ecm.core.dto.PdfAnnotationStateDto;
import com.ecm.core.dto.VersionCompareResultDto;
import com.ecm.core.dto.VersionDto;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Version;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.PdfAnnotationService;
import com.ecm.core.service.VersionService;
import com.ecm.core.service.ContentService;
import com.ecm.core.preview.PreviewService;
import com.ecm.core.preview.PreviewResult;
import com.ecm.core.preview.PreviewQueueService;
import com.ecm.core.conversion.ConversionService;
import com.ecm.core.conversion.ConversionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.Objects;

@RestController
@RequestMapping({"/api/documents", "/api/v1/documents"})
@RequiredArgsConstructor
@Tag(name = "Document Management", description = "APIs for managing documents")
public class DocumentController {
    
    private final NodeService nodeService;
    private final VersionService versionService;
    private final ContentService contentService;
    private final PreviewService previewService;
    private final PreviewQueueService previewQueueService;
    private final ConversionService conversionService;
    private final PdfAnnotationService pdfAnnotationService;
    
    @PostMapping("/upload-legacy")
    @Operation(summary = "Upload document (legacy)", description = "Legacy endpoint; prefer /api/v1/documents/upload pipeline API")
    public ResponseEntity<NodeDto> uploadDocumentLegacy(
            @Parameter(description = "File to upload") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Parent folder ID") @RequestParam(required = false) UUID parentId,
            @Parameter(description = "Parent folder ID (alias of parentId)") @RequestParam(required = false) UUID folderId,
            @Parameter(description = "Document ID for versioning") @RequestParam(required = false) UUID documentId,
            @Parameter(description = "Version comment") @RequestParam(required = false) String comment,
            @Parameter(description = "Major version") @RequestParam(defaultValue = "false") boolean majorVersion) 
            throws IOException {
        
        if (documentId != null) {
            // Create new version
            Version version = versionService.createVersion(documentId, file, comment, majorVersion);
            Document document = (Document) nodeService.getNode(documentId);
            return ResponseEntity.ok(NodeDto.from(document));
        } else {
            // Create new document
            Document document = new Document();
            document.setName(file.getOriginalFilename());
            document.setMimeType(file.getContentType());
            document.setFileSize(file.getSize());
            
            // Store content
            String contentId = contentService.storeContent(file);
            document.setContentId(contentId);
            
            UUID effectiveParentId = parentId != null ? parentId : folderId;
            Document created = (Document) nodeService.createNode(document, effectiveParentId);
            
            // Create initial version
            versionService.createVersion(created.getId(), file, "Initial version", true);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(NodeDto.from(created));
        }
    }
    
    @GetMapping("/{documentId}/download")
    @Operation(summary = "Download document", description = "Download the current version of a document")
    public ResponseEntity<InputStreamResource> downloadDocument(
            @Parameter(description = "Document ID") @PathVariable UUID documentId) throws IOException {
        
        Document document = (Document) nodeService.getNode(documentId);
        InputStream content = contentService.getContent(document.getContentId());
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(document.getMimeType()));
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(document.getName(), StandardCharsets.UTF_8)
            .build());
        headers.setContentLength(document.getFileSize());
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(new InputStreamResource(content));
    }
    
    @GetMapping("/{documentId}/versions")
    @Operation(summary = "Get version history", description = "Retrieve version history of a document")
    public ResponseEntity<List<VersionDto>> getVersionHistory(
            @Parameter(description = "Document ID") @PathVariable UUID documentId,
            @RequestParam(defaultValue = "false") boolean majorOnly) {
        List<Version> versions = versionService.getVersionHistory(documentId, majorOnly);
        return ResponseEntity.ok(versions.stream().map(VersionDto::from).toList());
    }

    @GetMapping("/{documentId}/versions/paged")
    @Operation(summary = "Get paged version history", description = "Retrieve paged version history of a document")
    public ResponseEntity<org.springframework.data.domain.Page<VersionDto>> getVersionHistoryPaged(
            @Parameter(description = "Document ID") @PathVariable UUID documentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean majorOnly) {
        var pageable = org.springframework.data.domain.PageRequest.of(page, size);
        var versions = versionService.getVersionHistory(documentId, pageable, majorOnly);
        return ResponseEntity.ok(versions.map(VersionDto::from));
    }

    @GetMapping("/{documentId}/versions/compare")
    @Operation(summary = "Compare versions", description = "Compare two specific versions (optionally including a small text diff).")
    public ResponseEntity<VersionCompareResultDto> compareVersions(
        @Parameter(description = "Document ID") @PathVariable UUID documentId,
        @Parameter(description = "From version id") @RequestParam UUID fromVersionId,
        @Parameter(description = "To version id") @RequestParam UUID toVersionId,
        @RequestParam(defaultValue = "false") boolean includeTextDiff,
        @RequestParam(defaultValue = "200000") int maxBytes,
        @RequestParam(defaultValue = "2000") int maxLines
    ) throws IOException {
        VersionCompareResultDto result = versionService.compareVersionsDetailed(
            documentId,
            fromVersionId,
            toVersionId,
            includeTextDiff,
            maxBytes,
            maxLines
        );
        // Defensive: ensure client isn't comparing across documents even if they pass mismatched ids.
        if (result == null || result.from() == null || !Objects.equals(documentId, result.from().documentId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{documentId}/versions/{versionId}/download")
    @Operation(summary = "Download specific version", description = "Download a specific version of a document")
    public ResponseEntity<InputStreamResource> downloadVersion(
            @Parameter(description = "Document ID") @PathVariable UUID documentId,
            @Parameter(description = "Version ID") @PathVariable UUID versionId) throws IOException {
        
        InputStream content = versionService.getVersionContent(versionId);
        Version version = versionService.getVersion(versionId);
        Document document = (Document) nodeService.getNode(documentId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(version.getMimeType()));
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(document.getName() + "_v" + version.getVersionLabel(), StandardCharsets.UTF_8)
            .build());
        headers.setContentLength(version.getFileSize());
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(new InputStreamResource(content));
    }
    
    @PostMapping("/{documentId}/versions/{versionId}/revert")
    @Operation(summary = "Revert to version", description = "Revert document to a specific version")
    public ResponseEntity<NodeDto> revertToVersion(
            @Parameter(description = "Document ID") @PathVariable UUID documentId,
            @Parameter(description = "Version ID") @PathVariable UUID versionId) throws IOException {
        
        versionService.revertToVersion(documentId, versionId);
        Document document = (Document) nodeService.getNode(documentId);
        return ResponseEntity.ok(NodeDto.from(document));
    }
    
    @PostMapping("/{documentId}/checkout")
    @Operation(summary = "Check out document", description = "Check out a document for editing")
    public ResponseEntity<NodeDto> checkoutDocument(
            @Parameter(description = "Document ID") @PathVariable UUID documentId) {
        
        Document document = (Document) nodeService.getNode(documentId);
        document.checkout(document.getLastModifiedBy());
        return ResponseEntity.ok(NodeDto.from(document));
    }
    
    @PostMapping("/{documentId}/checkin")
    @Operation(summary = "Check in document", description = "Check in a document after editing")
    public ResponseEntity<NodeDto> checkinDocument(
            @Parameter(description = "Document ID") @PathVariable UUID documentId,
            @Parameter(description = "New version file") @RequestParam(required = false) MultipartFile file,
            @Parameter(description = "Version comment") @RequestParam(required = false) String comment,
            @Parameter(description = "Major version") @RequestParam(defaultValue = "false") boolean majorVersion) 
            throws IOException {
        
        Document document = (Document) nodeService.getNode(documentId);
        
        if (file != null) {
            versionService.createVersion(documentId, file, comment, majorVersion);
        }
        
        document.checkin();
        return ResponseEntity.ok(NodeDto.from(document));
    }
    
    @PostMapping("/{documentId}/cancel-checkout")
    @Operation(summary = "Cancel checkout", description = "Cancel document checkout")
    public ResponseEntity<NodeDto> cancelCheckout(
            @Parameter(description = "Document ID") @PathVariable UUID documentId) {
        
        Document document = (Document) nodeService.getNode(documentId);
        document.checkin();
        return ResponseEntity.ok(NodeDto.from(document));
    }
    
    @GetMapping("/{documentId}/preview")
    @Operation(summary = "Preview document", description = "Generate document preview")
    public ResponseEntity<PreviewResult> previewDocument(
            @Parameter(description = "Document ID") @PathVariable UUID documentId) throws IOException {
        
        Document document = (Document) nodeService.getNode(documentId);
        PreviewResult preview = previewService.generatePreview(document);
        return ResponseEntity.ok(preview);
    }

    @PostMapping("/{documentId}/preview/queue")
    @Operation(summary = "Queue preview generation", description = "Enqueue document preview generation in the background")
    public ResponseEntity<PreviewQueueService.PreviewQueueStatus> queuePreview(
            @Parameter(description = "Document ID") @PathVariable UUID documentId,
            @RequestParam(defaultValue = "false") boolean force) {
        return ResponseEntity.ok(previewQueueService.enqueue(documentId, force));
    }

    @GetMapping("/{documentId}/annotations")
    @Operation(summary = "Get PDF annotations", description = "Retrieve PDF annotations for a document")
    public ResponseEntity<PdfAnnotationStateDto> getPdfAnnotations(
            @Parameter(description = "Document ID") @PathVariable UUID documentId) {
        return ResponseEntity.ok(pdfAnnotationService.getAnnotations(documentId));
    }

    @PostMapping("/{documentId}/annotations")
    @Operation(summary = "Save PDF annotations", description = "Save PDF annotations for a document")
    public ResponseEntity<PdfAnnotationStateDto> savePdfAnnotations(
            @Parameter(description = "Document ID") @PathVariable UUID documentId,
            @RequestBody PdfAnnotationSaveRequest request) {
        return ResponseEntity.ok(pdfAnnotationService.saveAnnotations(documentId, request));
    }
    
    @GetMapping("/{documentId}/thumbnail")
    @Operation(summary = "Get thumbnail", description = "Get document thumbnail")
    public ResponseEntity<byte[]> getThumbnail(
            @Parameter(description = "Document ID") @PathVariable UUID documentId) throws IOException {
        
        Document document = (Document) nodeService.getNode(documentId);
        byte[] thumbnail = previewService.generateThumbnail(document);
        
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(thumbnail);
    }
    
    @PostMapping("/{documentId}/convert")
    @Operation(summary = "Convert document", description = "Convert document to another format")
    public ResponseEntity<byte[]> convertDocument(
            @Parameter(description = "Document ID") @PathVariable UUID documentId,
            @Parameter(description = "Target format") @RequestParam String format) throws IOException {
        
        Document document = (Document) nodeService.getNode(documentId);
        ConversionResult result = conversionService.convertDocument(document, format);
        
        if (!result.isSuccess()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(result.getMimeType()));
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(document.getName() + "." + format, StandardCharsets.UTF_8)
            .build());
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(result.getContent());
    }
    
    @GetMapping("/{documentId}/conversions")
    @Operation(summary = "Get supported conversions", description = "Get list of supported format conversions")
    public ResponseEntity<List<String>> getSupportedConversions(
            @Parameter(description = "Document ID") @PathVariable UUID documentId) {
        
        Document document = (Document) nodeService.getNode(documentId);
        List<String> conversions = conversionService.getSupportedConversions(document.getMimeType());
        return ResponseEntity.ok(conversions);
    }
}
