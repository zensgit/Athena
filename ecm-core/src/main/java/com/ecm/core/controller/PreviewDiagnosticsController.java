package com.ecm.core.controller;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.preview.PreviewFailureClassifier;
import com.ecm.core.repository.DocumentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/preview/diagnostics")
@RequiredArgsConstructor
@Tag(name = "Preview Diagnostics", description = "Admin-only diagnostics endpoints for preview generation")
@PreAuthorize("hasRole('ADMIN')")
public class PreviewDiagnosticsController {

    private final DocumentRepository documentRepository;

    @GetMapping("/failures")
    @Operation(summary = "Recent preview failures", description = "List recent preview failures (FAILED/UNSUPPORTED) with derived categories.")
    public ResponseEntity<List<PreviewFailureSampleDto>> getRecentFailures(
        @RequestParam(defaultValue = "50") int limit
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        Pageable pageable = PageRequest.of(0, safeLimit);

        var statuses = List.of(PreviewStatus.FAILED, PreviewStatus.UNSUPPORTED);
        var page = documentRepository.findRecentPreviewFailures(statuses, pageable);

        List<PreviewFailureSampleDto> payload = page.getContent().stream()
            .map(PreviewFailureSampleDto::from)
            .toList();

        return ResponseEntity.ok(payload);
    }

    public record PreviewFailureSampleDto(
        UUID id,
        String name,
        String path,
        String mimeType,
        String previewStatus,
        String previewFailureReason,
        String previewFailureCategory,
        LocalDateTime previewLastUpdated
    ) {
        static PreviewFailureSampleDto from(Document document) {
            if (document == null) {
                return null;
            }
            String status = document.getPreviewStatus() != null ? document.getPreviewStatus().name() : null;
            String mimeType = normalizeMimeType(document.getMimeType());
            String reason = document.getPreviewFailureReason();
            String category = PreviewFailureClassifier.classify(status, mimeType, reason);
            return new PreviewFailureSampleDto(
                document.getId(),
                document.getName(),
                document.getPath(),
                mimeType,
                status,
                reason,
                category,
                document.getPreviewLastUpdated()
            );
        }

        private static String normalizeMimeType(String mimeType) {
            if (mimeType == null || mimeType.isBlank()) {
                return "";
            }
            String normalized = mimeType.split(";")[0].trim().toLowerCase(Locale.ROOT);
            return normalized;
        }
    }
}

