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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/preview/diagnostics")
@RequiredArgsConstructor
@Tag(name = "Preview Diagnostics", description = "Admin-only diagnostics endpoints for preview generation")
@PreAuthorize("hasRole('ADMIN')")
public class PreviewDiagnosticsController {

    private final DocumentRepository documentRepository;
    private static final int MAX_FAILURE_LIST_LIMIT = 200;
    private static final int DEFAULT_SUMMARY_SAMPLE_LIMIT = 500;
    private static final int MAX_SUMMARY_SAMPLE_LIMIT = 2000;

    @GetMapping("/failures")
    @Operation(summary = "Recent preview failures", description = "List recent preview failures (FAILED/UNSUPPORTED) with derived categories.")
    public ResponseEntity<List<PreviewFailureSampleDto>> getRecentFailures(
        @RequestParam(defaultValue = "50") int limit
    ) {
        int safeLimit = clamp(limit, 1, MAX_FAILURE_LIST_LIMIT);
        Pageable pageable = PageRequest.of(0, safeLimit);

        var statuses = failureStatuses();
        var page = documentRepository.findRecentPreviewFailures(statuses, pageable);

        List<PreviewFailureSampleDto> payload = page.getContent().stream()
            .map(PreviewFailureSampleDto::from)
            .toList();

        return ResponseEntity.ok(payload);
    }

    @GetMapping("/failures/summary")
    @Operation(
        summary = "Preview failure summary",
        description = "Summarize recent preview failures with sample confidence and top failure reasons."
    )
    public ResponseEntity<PreviewFailureSummaryDto> getFailureSummary(
        @RequestParam(defaultValue = "500") int sampleLimit
    ) {
        int requestedLimit = sampleLimit <= 0 ? DEFAULT_SUMMARY_SAMPLE_LIMIT : sampleLimit;
        int safeLimit = clamp(requestedLimit, 1, MAX_SUMMARY_SAMPLE_LIMIT);
        Pageable pageable = PageRequest.of(0, safeLimit);
        var statuses = failureStatuses();

        long totalFailures = documentRepository.countByDeletedFalseAndPreviewStatusIn(statuses);
        List<Document> samples = documentRepository.findRecentPreviewFailures(statuses, pageable).getContent();

        List<PreviewFailureSampleDto> normalizedSamples = samples.stream()
            .map(PreviewFailureSampleDto::from)
            .toList();

        Map<String, Long> statusCounts = new HashMap<>();
        Map<String, Long> categoryCounts = new HashMap<>();
        Map<ReasonKey, Long> reasonCounts = new HashMap<>();

        for (PreviewFailureSampleDto sample : normalizedSamples) {
            String status = normalizedUpperOrDefault(sample.previewStatus(), "UNKNOWN");
            String category = normalizedUpperOrDefault(sample.previewFailureCategory(), "UNKNOWN");
            String reason = normalizeReason(sample.previewFailureReason());
            boolean retryable = PreviewFailureClassifier.CATEGORY_TEMPORARY.equals(category);

            statusCounts.merge(status, 1L, Long::sum);
            categoryCounts.merge(category, 1L, Long::sum);
            reasonCounts.merge(new ReasonKey(reason, category, retryable), 1L, Long::sum);
        }

        int sampledFailures = normalizedSamples.size();
        boolean sampleTruncated = totalFailures > sampledFailures;

        List<PreviewFailureStatusCountDto> statusPayload = statusCounts.entrySet().stream()
            .map(entry -> new PreviewFailureStatusCountDto(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingLong(PreviewFailureStatusCountDto::count).reversed()
                .thenComparing(PreviewFailureStatusCountDto::status))
            .toList();

        List<PreviewFailureCategoryCountDto> categoryPayload = categoryCounts.entrySet().stream()
            .map(entry -> new PreviewFailureCategoryCountDto(
                entry.getKey(),
                PreviewFailureClassifier.CATEGORY_TEMPORARY.equals(entry.getKey()),
                entry.getValue()))
            .sorted(Comparator.comparingLong(PreviewFailureCategoryCountDto::count).reversed()
                .thenComparing(PreviewFailureCategoryCountDto::category))
            .toList();

        List<PreviewFailureReasonCountDto> reasonPayload = reasonCounts.entrySet().stream()
            .sorted(
                Comparator.<Map.Entry<ReasonKey, Long>>comparingLong(Map.Entry::getValue)
                    .reversed()
                    .thenComparing(entry -> entry.getKey().reason())
            )
            .limit(10)
            .map(entry -> new PreviewFailureReasonCountDto(
                entry.getKey().reason(),
                entry.getKey().category(),
                entry.getKey().retryable(),
                entry.getValue()))
            .toList();

        PreviewFailureSummaryDto payload = new PreviewFailureSummaryDto(
            totalFailures,
            sampledFailures,
            safeLimit,
            sampleTruncated,
            sampleTruncated ? "LOW" : "HIGH",
            sampleTruncated ? "sample_truncated" : "sample_complete",
            statusPayload,
            categoryPayload,
            reasonPayload
        );
        return ResponseEntity.ok(payload);
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private static List<PreviewStatus> failureStatuses() {
        List<PreviewStatus> statuses = new ArrayList<>();
        statuses.add(PreviewStatus.FAILED);
        statuses.add(PreviewStatus.UNSUPPORTED);
        return statuses;
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "UNSPECIFIED";
        }
        return reason.replaceAll("\\s+", " ").trim();
    }

    private static String normalizedUpperOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private record ReasonKey(String reason, String category, boolean retryable) {}

    public record PreviewFailureSummaryDto(
        long totalFailures,
        int sampledFailures,
        int sampleLimit,
        boolean sampleTruncated,
        String confidenceLevel,
        String confidenceReason,
        List<PreviewFailureStatusCountDto> statusCounts,
        List<PreviewFailureCategoryCountDto> categoryCounts,
        List<PreviewFailureReasonCountDto> topReasons
    ) {}

    public record PreviewFailureStatusCountDto(
        String status,
        long count
    ) {}

    public record PreviewFailureCategoryCountDto(
        String category,
        boolean retryable,
        long count
    ) {}

    public record PreviewFailureReasonCountDto(
        String reason,
        String category,
        boolean retryable,
        long count
    ) {}

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
