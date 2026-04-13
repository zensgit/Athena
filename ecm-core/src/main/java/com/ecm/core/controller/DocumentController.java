package com.ecm.core.controller;

import com.ecm.core.dto.CheckoutInfoDto;
import com.ecm.core.dto.CheckoutLineageDto;
import com.ecm.core.dto.NodeDto;
import com.ecm.core.dto.PdfAnnotationSaveRequest;
import com.ecm.core.dto.PdfAnnotationStateDto;
import com.ecm.core.dto.VersionCompareResultDto;
import com.ecm.core.dto.VersionDto;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Version;
import com.ecm.core.preview.PreviewFailureClassifier;
import com.ecm.core.preview.PreviewStatusSemantics;
import com.ecm.core.service.CheckOutCheckInService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.PdfAnnotationService;
import com.ecm.core.service.RenditionResourceService;
import com.ecm.core.service.VersionService;
import com.ecm.core.service.ContentService;
import com.ecm.core.service.TenantQuotaService;
import com.ecm.core.preview.PreviewService;
import com.ecm.core.preview.PreviewResult;
import com.ecm.core.preview.PreviewQueueService;
import com.ecm.core.ocr.OcrQueueService;
import com.ecm.core.conversion.ConversionService;
import com.ecm.core.conversion.ConversionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping({"/api/documents", "/api/v1/documents"})
@RequiredArgsConstructor
@Tag(name = "Document Management", description = "APIs for managing documents")
public class DocumentController {
    
    private final NodeService nodeService;
    private final VersionService versionService;
    private final ContentService contentService;
    private final TenantQuotaService tenantQuotaService;
    private final PreviewService previewService;
    private final PreviewQueueService previewQueueService;
    private final OcrQueueService ocrQueueService;
    private final ConversionService conversionService;
    private final PdfAnnotationService pdfAnnotationService;
    private final RenditionResourceService renditionResourceService;
    private final CheckOutCheckInService checkOutCheckInService;

    @Value("${ecm.preview.read.hash-enforce.enabled:true}")
    private boolean previewReadHashEnforceEnabled;

    @Value("${ecm.preview.read.auto-repair-on-stale:true}")
    private boolean previewReadAutoRepairOnStale;
    
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

        // Best-effort quota preflight using declared file size
        tenantQuotaService.assertQuotaAvailable(file.getSize());

        if (documentId != null) {
            // Create new version
            Version version = versionService.createVersion(documentId, file, comment, majorVersion);
            Document document = (Document) nodeService.getNode(documentId);
            return ResponseEntity.ok(toNodeDto(document));
        } else {
            // Create new document
            Document document = new Document();
            document.setName(file.getOriginalFilename());
            document.setFileSize(file.getSize());
            
            // Store content
            String contentId = contentService.storeContent(file);
            document.setContentId(contentId);
            document.setMimeType(contentService.detectMimeType(contentId, file.getOriginalFilename()));
            
            UUID effectiveParentId = parentId != null ? parentId : folderId;
            Document created = (Document) nodeService.createNode(document, effectiveParentId);
            
            // Create initial version
            versionService.createVersion(created.getId(), file, "Initial version", true);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(toNodeDto(created));
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
        return ResponseEntity.ok(toNodeDto(document));
    }
    
    @PostMapping("/{documentId}/checkout")
    @Operation(summary = "Check out document", description = "Check out a document for editing")
    public ResponseEntity<NodeDto> checkoutDocument(
            @Parameter(description = "Document ID") @PathVariable UUID documentId) {
        Document document = nodeService.checkoutDocument(documentId);
        return ResponseEntity.ok(toNodeDto(document));
    }

    @GetMapping("/{documentId}/checkout-info")
    @Operation(summary = "Get checkout info", description = "Retrieve caller-relative checkout status and available actions for a document")
    public ResponseEntity<CheckoutInfoDto> getCheckoutInfo(
            @Parameter(description = "Document ID") @PathVariable UUID documentId) {
        return ResponseEntity.ok(nodeService.getCheckoutInfo(documentId));
    }

    @GetMapping("/{documentId}/checkout-lineage")
    @Operation(summary = "Get checkout lineage", description = "Retrieve caller-relative checkout info plus baseline/current version references for active checkout lineage.")
    public ResponseEntity<CheckoutLineageDto> getCheckoutLineage(
            @Parameter(description = "Document ID") @PathVariable UUID documentId) {
        Document document = (Document) nodeService.getNode(documentId);
        CheckoutInfoDto checkoutInfo = nodeService.getCheckoutInfo(documentId);

        VersionDto baselineVersion = null;
        if (document.getCheckoutBaselineVersionId() != null && !document.getCheckoutBaselineVersionId().isBlank()) {
            try {
                baselineVersion = VersionDto.from(versionService.getVersion(UUID.fromString(document.getCheckoutBaselineVersionId())));
            } catch (IllegalArgumentException ignored) {
                baselineVersion = null;
            }
        }

        VersionDto currentVersion = document.getCurrentVersion() != null
            ? VersionDto.from(document.getCurrentVersion())
            : null;

        return ResponseEntity.ok(new CheckoutLineageDto(
            documentId,
            checkoutInfo,
            baselineVersion,
            currentVersion
        ));
    }
    
    @PostMapping("/{documentId}/checkin")
    @Operation(summary = "Check in document", description = "Check in a document after editing")
    public ResponseEntity<NodeDto> checkinDocument(
            @Parameter(description = "Document ID") @PathVariable UUID documentId,
            @Parameter(description = "New version file") @RequestParam(required = false) MultipartFile file,
            @Parameter(description = "Version comment") @RequestParam(required = false) String comment,
            @Parameter(description = "Major version") @RequestParam(defaultValue = "false") boolean majorVersion,
            @Parameter(description = "Keep document checked out after check-in") @RequestParam(defaultValue = "false") boolean keepCheckedOut)
            throws IOException {

        if (keepCheckedOut && file == null) {
            throw new IllegalArgumentException("keepCheckedOut requires a new version file");
        }
        if (file != null) {
            versionService.createVersion(documentId, file, comment, majorVersion);
        }
        Document document = nodeService.checkinDocument(documentId, keepCheckedOut);
        return ResponseEntity.ok(toNodeDto(document));
    }
    
    @PostMapping("/{documentId}/cancel-checkout")
    @Operation(summary = "Cancel checkout", description = "Cancel document checkout")
    public ResponseEntity<NodeDto> cancelCheckout(
            @Parameter(description = "Document ID") @PathVariable UUID documentId) {
        Document document = nodeService.cancelCheckoutDocument(documentId);
        return ResponseEntity.ok(toNodeDto(document));
    }

    // ---- persisted working-copy endpoints --------------------------------

    @PostMapping("/{documentId}/checkout-wc")
    @Operation(summary = "Check out with working copy",
               description = "Check out a document creating a persisted working copy. "
                   + "Optionally specify a destination folder for the working copy.")
    public ResponseEntity<NodeDto> checkoutWithWorkingCopy(
            @Parameter(description = "Document ID") @PathVariable UUID documentId,
            @Parameter(description = "Destination folder ID (defaults to same parent)")
            @RequestParam(required = false) UUID destination) {
        Document wc = checkOutCheckInService.checkout(documentId, destination);
        return ResponseEntity.ok(toNodeDto(wc));
    }

    @PostMapping("/{workingCopyId}/checkin-wc")
    @Operation(summary = "Check in working copy",
               description = "Check in a persisted working copy. Optionally upload a new file "
                   + "and/or keep the document checked out.")
    public ResponseEntity<NodeDto> checkinWorkingCopy(
            @Parameter(description = "Working copy ID") @PathVariable UUID workingCopyId,
            @Parameter(description = "New version file") @RequestParam(required = false) MultipartFile file,
            @Parameter(description = "Version comment") @RequestParam(required = false) String comment,
            @Parameter(description = "Major version") @RequestParam(defaultValue = "false") boolean majorVersion,
            @Parameter(description = "Keep checked out") @RequestParam(defaultValue = "false") boolean keepCheckedOut)
            throws IOException {
        Document result = checkOutCheckInService.checkin(
                workingCopyId, keepCheckedOut, comment, majorVersion, file);
        return ResponseEntity.ok(toNodeDto(result));
    }

    @PostMapping("/{documentId}/cancel-checkout-wc")
    @Operation(summary = "Cancel working-copy checkout",
               description = "Cancel checkout and delete the persisted working copy. "
                   + "Accepts either the original document ID or the working copy ID.")
    public ResponseEntity<NodeDto> cancelCheckoutWorkingCopy(
            @Parameter(description = "Document or working copy ID") @PathVariable UUID documentId) {
        Document original = checkOutCheckInService.cancelCheckout(documentId);
        return ResponseEntity.ok(toNodeDto(original));
    }

    @GetMapping("/{documentId}/working-copy")
    @Operation(summary = "Get working copy",
               description = "Retrieve the persisted working copy of a checked-out document.")
    public ResponseEntity<NodeDto> getWorkingCopy(
            @Parameter(description = "Original document ID") @PathVariable UUID documentId) {
        return checkOutCheckInService.getWorkingCopy(documentId)
            .map(wc -> ResponseEntity.ok(toNodeDto(wc)))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{workingCopyId}/original")
    @Operation(summary = "Get original from working copy",
               description = "Retrieve the original document from a working copy ID.")
    public ResponseEntity<NodeDto> getOriginal(
            @Parameter(description = "Working copy ID") @PathVariable UUID workingCopyId) {
        return checkOutCheckInService.getOriginal(workingCopyId)
            .map(orig -> ResponseEntity.ok(toNodeDto(orig)))
            .orElse(ResponseEntity.notFound().build());
    }

    private NodeDto toNodeDto(Document document) {
        NodeDto base = NodeDto.from(document);
        RenditionResourceService.RenditionSummary renditionSummary = renditionResourceService.summarizeDocument(document);
        if (renditionSummary == null || !renditionSummary.document()) {
            return base;
        }
        return base.withPreviewSemantics(
            renditionSummary.previewStatus(),
            renditionSummary.previewFailureReason(),
            renditionSummary.previewFailureCategory(),
            renditionSummary.previewLastUpdated()
        );
    }
    
    @GetMapping("/{documentId}/preview")
    @Operation(summary = "Preview document", description = "Generate document preview")
    public ResponseEntity<PreviewResult> previewDocument(
            @Parameter(description = "Document ID") @PathVariable UUID documentId,
            @RequestParam(required = false) Boolean enforceHashCheck,
            @RequestParam(required = false) Boolean autoRepair) throws IOException {

        Document document = (Document) nodeService.getNode(documentId);
        boolean effectiveHashEnforce = enforceHashCheck != null ? enforceHashCheck : previewReadHashEnforceEnabled;
        boolean effectiveAutoRepair = autoRepair != null ? autoRepair : previewReadAutoRepairOnStale;
        PreviewService.PreviewReadiness readiness = previewService.evaluateReadiness(document);

        if (effectiveHashEnforce && (readiness.staleHash() || readiness.zeroSource())) {
            String reason = readiness.reason() != null
                ? readiness.reason()
                : (readiness.zeroSource() ? "SOURCE_EMPTY" : "HASH_ENFORCE_DECLINED");
            PreviewService.PreviewRepairResult repair = previewService.invalidateRendition(document, reason);
            PreviewQueueService.PreviewQueueStatus queueStatus = null;
            if (effectiveAutoRepair && !readiness.zeroSource()) {
                try {
                    queueStatus = previewQueueService.enqueue(documentId, true);
                } catch (Exception e) {
                    queueStatus = new PreviewQueueService.PreviewQueueStatus(
                        documentId,
                        document.getPreviewStatus(),
                        false,
                        0,
                        null,
                        "Auto-repair queue failed: " + e.getMessage()
                    );
                }
            }
            return ResponseEntity.ok(buildHashEnforcedDeclinedResult(document, readiness, repair, queueStatus));
        }

        PreviewResult preview = previewService.generatePreview(document);
        return ResponseEntity.ok(preview);
    }

    @PostMapping("/{documentId}/preview/repair")
    @Operation(summary = "Repair preview rendition", description = "Invalidate stale preview state and optionally queue repair.")
    public ResponseEntity<PreviewRepairResponse> repairPreview(
            @Parameter(description = "Document ID") @PathVariable UUID documentId,
            @RequestParam(defaultValue = "true") boolean forceInvalidate,
            @RequestParam(defaultValue = "true") boolean requeue,
            @RequestParam(defaultValue = "true") boolean forceQueue) {
        Document document = (Document) nodeService.getNode(documentId);
        PreviewService.PreviewReadiness readiness = previewService.evaluateReadiness(document);

        boolean shouldInvalidate = forceInvalidate || readiness.staleHash() || readiness.zeroSource();
        PreviewService.PreviewRepairResult repairResult = shouldInvalidate
            ? previewService.invalidateRendition(
                document,
                readiness.reason() != null ? readiness.reason() : "MANUAL_REPAIR"
            )
            : new PreviewService.PreviewRepairResult(
                documentId,
                document.getPreviewStatus() != null ? document.getPreviewStatus().name() : null,
                false,
                "No invalidation required",
                document.getPreviewContentHash(),
                document.getContentHash()
            );

        PreviewQueueService.PreviewQueueStatus queueStatus = null;
        if (requeue && !readiness.zeroSource()) {
            queueStatus = previewQueueService.enqueue(documentId, forceQueue);
        }

        RenditionResourceService.PreviewMutationStatus mutationStatus =
            renditionResourceService.resolvePreviewMutationStatus(document, queueStatus);

        return ResponseEntity.ok(new PreviewRepairResponse(
            documentId,
            readiness.state(),
            readiness.reason(),
            repairResult.invalidated(),
            repairResult.reason(),
            mutationStatus.queued(),
            mutationStatus.message(),
            mutationStatus.previewStatus(),
            mutationStatus.previewFailureReason(),
            mutationStatus.previewFailureCategory(),
            mutationStatus.previewLastUpdated()
        ));
    }

    @PostMapping("/{documentId}/preview/queue")
    @Operation(summary = "Queue preview generation", description = "Enqueue document preview generation in the background")
    public ResponseEntity<PreviewQueueResponse> queuePreview(
            @Parameter(description = "Document ID") @PathVariable UUID documentId,
            @RequestParam(defaultValue = "false") boolean force) {
        PreviewQueueService.PreviewQueueStatus queueStatus = previewQueueService.enqueue(documentId, force);
        Document document = (Document) nodeService.getNode(documentId);
        RenditionResourceService.PreviewMutationStatus mutationStatus =
            renditionResourceService.resolvePreviewMutationStatus(document, queueStatus);
        return ResponseEntity.ok(new PreviewQueueResponse(
            mutationStatus.documentId(),
            mutationStatus.previewStatus(),
            mutationStatus.previewFailureReason(),
            mutationStatus.previewFailureCategory(),
            mutationStatus.previewLastUpdated(),
            mutationStatus.queued(),
            mutationStatus.attempts(),
            mutationStatus.nextAttemptAt(),
            mutationStatus.message()
        ));
    }

    @PostMapping("/{documentId}/preview/queue/cancel")
    @Operation(summary = "Cancel preview queue task", description = "Cancel queued/running preview task for a document.")
    public ResponseEntity<PreviewQueueCancelResponse> cancelQueuedPreview(
            @Parameter(description = "Document ID") @PathVariable UUID documentId) {
        PreviewQueueService.PreviewQueueCancellationStatus status = previewQueueService.cancel(documentId);
        return ResponseEntity.ok(new PreviewQueueCancelResponse(
            status.documentId(),
            status.queueState(),
            status.cancelled(),
            status.hadActiveTask(),
            status.running(),
            status.message()
        ));
    }

    @PostMapping("/{documentId}/ocr/queue")
    @Operation(summary = "Queue OCR extraction", description = "Enqueue OCR text extraction in the background (if enabled).")
    public ResponseEntity<OcrQueueService.OcrQueueStatus> queueOcr(
            @Parameter(description = "Document ID") @PathVariable UUID documentId,
            @RequestParam(defaultValue = "false") boolean force) {
        return ResponseEntity.ok(ocrQueueService.enqueue(documentId, force));
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

    private PreviewResult buildHashEnforcedDeclinedResult(
        Document document,
        PreviewService.PreviewReadiness readiness,
        PreviewService.PreviewRepairResult repairResult,
        PreviewQueueService.PreviewQueueStatus queueStatus
    ) {
        PreviewResult result = new PreviewResult();
        RenditionResourceService.RenditionSummary renditionSummary =
            renditionResourceService.resolvePreviewMutationSummary(document, queueStatus);
        String effectiveStatus = renditionSummary.previewStatus();
        String failureReason = firstNonBlank(
            renditionSummary.previewFailureReason(),
            repairResult != null ? repairResult.reason() : null,
            readiness.reason(),
            PreviewStatusSemantics.resolveEffectiveFailureReason(document)
        );
        String failureCategory = firstNonBlank(
            renditionSummary.previewFailureCategory(),
            PreviewFailureClassifier.classify(
                firstNonBlank(effectiveStatus, "FAILED"),
                document != null ? document.getMimeType() : null,
                failureReason
            )
        );
        if (failureCategory == null || failureCategory.isBlank()) {
            failureCategory = readiness.zeroSource()
                ? PreviewFailureClassifier.CATEGORY_UNSUPPORTED
                : PreviewFailureClassifier.CATEGORY_TEMPORARY;
        }
        String status = firstNonBlank(
            effectiveStatus,
            PreviewFailureClassifier.CATEGORY_UNSUPPORTED.equalsIgnoreCase(failureCategory)
                ? "UNSUPPORTED"
                : "FAILED"
        );
        result.setDocumentId(document != null ? document.getId() : null);
        result.setMimeType(document != null ? document.getMimeType() : null);
        result.setSupported(false);
        result.setStatus(status);
        result.setFailureReason(failureReason);
        result.setFailureCategory(failureCategory);
        result.setRetryNeeded(!readiness.zeroSource());
        if (queueStatus != null) {
            result.setRetryHint(queueStatus.message());
            result.setMessage(queueStatus.queued()
                ? "Preview withheld by hash enforcement; auto-repair queued"
                : "Preview withheld by hash enforcement; auto-repair not queued");
        } else {
            result.setRetryHint(readiness.reason());
            result.setMessage(readiness.zeroSource()
                ? "Preview withheld: source content is empty"
                : "Preview withheld by hash enforcement");
        }
        return result;
    }

    public record PreviewRepairResponse(
        UUID documentId,
        String readinessState,
        String readinessReason,
        boolean invalidated,
        String invalidationReason,
        boolean queued,
        String queueMessage,
        String previewStatus,
        String previewFailureReason,
        String previewFailureCategory,
        java.time.LocalDateTime previewLastUpdated
    ) {
    }

    public record PreviewQueueCancelResponse(
        UUID documentId,
        String queueState,
        boolean cancelled,
        boolean hadActiveTask,
        boolean running,
        String message
    ) {
    }

    public record PreviewQueueResponse(
        UUID documentId,
        String previewStatus,
        String previewFailureReason,
        String previewFailureCategory,
        java.time.LocalDateTime previewLastUpdated,
        boolean queued,
        int attempts,
        java.time.Instant nextAttemptAt,
        String message
    ) {
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }
}
