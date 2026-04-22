package com.ecm.core.controller;

import com.ecm.core.entity.RmReportPreset;
import com.ecm.core.service.RmReportPresetService;
import com.ecm.core.service.RecordsManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Records Management", description = "Declare records and enforce record immutability")
@PreAuthorize("hasRole('ADMIN')")
public class RecordsManagementController {

    private final RecordsManagementService recordsManagementService;
    private final RmReportPresetService rmReportPresetService;

    @GetMapping("/records")
    @Operation(summary = "List declared records")
    public ResponseEntity<List<RecordsManagementService.RecordDeclarationDto>> listRecords() {
        return ResponseEntity.ok(recordsManagementService.listRecords());
    }

    @GetMapping("/records/summary")
    @Operation(summary = "Summarize records management state")
    public ResponseEntity<RecordsManagementService.RecordsSummaryDto> getSummary() {
        return ResponseEntity.ok(recordsManagementService.getSummary());
    }

    @GetMapping("/records/audit")
    @Operation(summary = "List records management audit events")
    public ResponseEntity<Page<RecordsManagementService.RecordAuditEntryDto>> listAudit(
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) String username,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
        @RequestParam(required = false) RecordsManagementService.RmEventFamily family,
        Pageable pageable
    ) {
        return ResponseEntity.ok(recordsManagementService.listAudit(eventType, username, from, to, family, pageable));
    }

    @GetMapping("/records/operations")
    @Operation(summary = "Summarize RM-governed import and transfer operations")
    public ResponseEntity<RecordsManagementService.RecordsOperationsTelemetryDto> getOperationsTelemetry(
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(recordsManagementService.getOperationsTelemetry(limit));
    }

    @GetMapping("/records/activity-timeline")
    @Operation(summary = "Summarize records management activity timeline")
    public ResponseEntity<RecordsManagementService.RecordsActivityTimelineDto> getActivityTimeline(
        @RequestParam(required = false) Integer days
    ) {
        return ResponseEntity.ok(recordsManagementService.getActivityTimeline(days));
    }

    @GetMapping("/records/activity-highlights")
    @Operation(summary = "Summarize records management activity highlights")
    public ResponseEntity<RecordsManagementService.RecordsActivityHighlightsDto> getActivityHighlights(
        @RequestParam(required = false) Integer windowDays
    ) {
        return ResponseEntity.ok(recordsManagementService.getActivityHighlights(windowDays));
    }

    @GetMapping("/records/activity-contributors")
    @Operation(summary = "Top RM activity contributors over a recent window")
    public ResponseEntity<RecordsManagementService.ActivityContributorsDto> getActivityContributors(
        @RequestParam(required = false) Integer days,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(recordsManagementService.getActivityContributors(days, limit));
    }

    @GetMapping("/records/activity-contributor-highlights")
    @Operation(summary = "Compare RM activity contributors across recent windows")
    public ResponseEntity<RecordsManagementService.ActivityContributorHighlightsDto> getActivityContributorHighlights(
        @RequestParam(required = false) Integer windowDays,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(recordsManagementService.getActivityContributorHighlights(windowDays, limit));
    }

    @GetMapping("/records/activity-contributor-event-type-highlights")
    @Operation(summary = "Compare RM activity contributor event types across recent windows")
    public ResponseEntity<RecordsManagementService.ActivityContributorEventTypeHighlightsDto> getActivityContributorEventTypeHighlights(
        @RequestParam(required = false) Integer windowDays,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Integer eventTypeLimit
    ) {
        return ResponseEntity.ok(recordsManagementService.getActivityContributorEventTypeHighlights(windowDays, limit, eventTypeLimit));
    }

    @GetMapping("/records/activity-contributor-family-highlights")
    @Operation(summary = "Compare RM activity contributor families across recent windows")
    public ResponseEntity<RecordsManagementService.ActivityContributorFamilyHighlightsDto> getActivityContributorFamilyHighlights(
        @RequestParam(required = false) Integer windowDays,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(recordsManagementService.getActivityContributorFamilyHighlights(windowDays, limit));
    }

    @PostMapping("/records/report-presets/{presetId}/execute")
    @Operation(summary = "Execute a saved RM report preset")
    public ResponseEntity<?> executeReportPreset(
        @PathVariable UUID presetId,
        @RequestParam(required = false) String format
    ) {
        RmReportPreset preset = rmReportPresetService.getOwned(presetId);
        String normalizedFormat = normalizeReportFormat(format);
        return switch (preset.getKind()) {
            case ACTIVITY_FAMILY_REPORT -> getActivityFamilyReport(
                requirePresetDateTimeParam(preset, "from"),
                requirePresetDateTimeParam(preset, "to"),
                optionalPresetIntegerParam(preset, "eventTypeLimit"),
                optionalPresetIntegerParam(preset, "contributorLimit"),
                normalizedFormat
            );
            case ACTIVITY_EVENT_TYPE_REPORT -> getActivityEventTypeReport(
                requirePresetDateTimeParam(preset, "from"),
                requirePresetDateTimeParam(preset, "to"),
                optionalPresetIntegerParam(preset, "limit"),
                normalizedFormat
            );
            case ACTIVITY_CONTRIBUTOR_REPORT -> getActivityContributorReport(
                requirePresetDateTimeParam(preset, "from"),
                requirePresetDateTimeParam(preset, "to"),
                optionalPresetIntegerParam(preset, "limit"),
                optionalPresetIntegerParam(preset, "eventTypeLimit"),
                normalizedFormat
            );
            case ACTIVITY_CONTRIBUTOR_FAMILY_REPORT -> getActivityContributorFamilyReport(
                requirePresetDateTimeParam(preset, "from"),
                requirePresetDateTimeParam(preset, "to"),
                optionalPresetIntegerParam(preset, "limit"),
                normalizedFormat
            );
            case ACTIVITY_CONTRIBUTOR_EVENT_TYPE_REPORT -> getActivityContributorEventTypeReport(
                requirePresetDateTimeParam(preset, "from"),
                requirePresetDateTimeParam(preset, "to"),
                optionalPresetIntegerParam(preset, "limit"),
                optionalPresetIntegerParam(preset, "eventTypeLimit"),
                normalizedFormat
            );
            case ACTIVITY_FAMILY_HIGHLIGHTS -> {
                if ("csv".equals(normalizedFormat)) {
                    ResolvedPresetDateTimeRange range = requirePresetRollingDateTimeRange(preset, "windowDays");
                    yield getActivityFamilyReport(range.from(), range.to(), null, null, normalizedFormat);
                }
                yield ResponseEntity.ok(recordsManagementService.getActivityFamilyHighlights(
                    resolvePresetRollingDays(preset, "windowDays")
                ));
            }
            case ACTIVITY_FAMILY_MIX -> {
                if ("csv".equals(normalizedFormat)) {
                    ResolvedPresetDateTimeRange range = requirePresetRollingDateTimeRange(preset, "days");
                    yield getActivityFamilyReport(range.from(), range.to(), null, null, normalizedFormat);
                }
                yield ResponseEntity.ok(recordsManagementService.getActivityFamilies(
                    resolvePresetRollingDays(preset, "days")
                ));
            }
        };
    }

    @GetMapping("/records/activity-contributor-report")
    @Operation(summary = "Build an RM activity contributor report for a closed audit range")
    public ResponseEntity<?> getActivityContributorReport(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Integer eventTypeLimit,
        @RequestParam(required = false) String format
    ) {
        RecordsManagementService.ActivityContributorReportDto report =
            recordsManagementService.getActivityContributorReport(from, to, limit, eventTypeLimit);
        String normalizedFormat = normalizeReportFormat(format);
        if ("csv".equals(normalizedFormat)) {
            byte[] csv = buildActivityContributorReportCsv(report).getBytes(StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment()
                .filename(
                    "rm-activity-contributor-report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv",
                    StandardCharsets.UTF_8
                )
                .build());
            headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
            headers.setContentLength(csv.length);
            return ResponseEntity.ok().headers(headers).body(csv);
        }
        return ResponseEntity.ok(report);
    }

    @GetMapping("/records/activity-contributor-event-type-report")
    @Operation(summary = "Build an RM activity contributor event-type report for a closed audit range")
    public ResponseEntity<?> getActivityContributorEventTypeReport(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Integer eventTypeLimit,
        @RequestParam(required = false) String format
    ) {
        RecordsManagementService.ActivityContributorEventTypeReportDto report =
            recordsManagementService.getActivityContributorEventTypeReport(from, to, limit, eventTypeLimit);
        String normalizedFormat = normalizeReportFormat(format);
        if ("csv".equals(normalizedFormat)) {
            byte[] csv = buildActivityContributorEventTypeReportCsv(report).getBytes(StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment()
                .filename(
                    "rm-activity-contributor-event-type-report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv",
                    StandardCharsets.UTF_8
                )
                .build());
            headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
            headers.setContentLength(csv.length);
            return ResponseEntity.ok().headers(headers).body(csv);
        }
        return ResponseEntity.ok(report);
    }

    @GetMapping("/records/activity-contributor-event-type-trend")
    @Operation(summary = "Build an RM activity contributor event-type trend over a recent window")
    public ResponseEntity<RecordsManagementService.ActivityContributorEventTypeTrendDto> getActivityContributorEventTypeTrend(
        @RequestParam(required = false) Integer days,
        @RequestParam(required = false) Integer bucketDays,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Integer eventTypeLimit
    ) {
        return ResponseEntity.ok(
            recordsManagementService.getActivityContributorEventTypeTrend(days, bucketDays, limit, eventTypeLimit)
        );
    }

    @GetMapping("/records/activity-contributor-family-trend")
    @Operation(summary = "Bucket RM activity by contributor and family over a recent window")
    public ResponseEntity<RecordsManagementService.ActivityContributorFamilyTrendDto> getActivityContributorFamilyTrend(
        @RequestParam(required = false) Integer days,
        @RequestParam(required = false) Integer bucketDays,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(recordsManagementService.getActivityContributorFamilyTrend(days, bucketDays, limit));
    }

    @GetMapping("/records/activity-contributor-family-report")
    @Operation(summary = "Build an RM activity contributor family report for a closed audit range")
    public ResponseEntity<?> getActivityContributorFamilyReport(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) String format
    ) {
        RecordsManagementService.ActivityContributorFamilyReportDto report =
            recordsManagementService.getActivityContributorFamilyReport(from, to, limit);
        String normalizedFormat = normalizeReportFormat(format);
        if ("csv".equals(normalizedFormat)) {
            byte[] csv = buildActivityContributorFamilyReportCsv(report).getBytes(StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment()
                .filename(
                    "rm-activity-contributor-family-report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv",
                    StandardCharsets.UTF_8
                )
                .build());
            headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
            headers.setContentLength(csv.length);
            return ResponseEntity.ok().headers(headers).body(csv);
        }
        return ResponseEntity.ok(report);
    }

    @GetMapping("/records/activity-contributor-trend")
    @Operation(summary = "Bucket RM activity by contributor over a recent window")
    public ResponseEntity<RecordsManagementService.ActivityContributorTrendDto> getActivityContributorTrend(
        @RequestParam(required = false) Integer days,
        @RequestParam(required = false) Integer bucketDays,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(recordsManagementService.getActivityContributorTrend(days, bucketDays, limit));
    }

    @GetMapping("/records/activity-event-type-trend")
    @Operation(summary = "Bucket RM activity by exact event type over a recent window")
    public ResponseEntity<RecordsManagementService.ActivityEventTypeTrendDto> getActivityEventTypeTrend(
        @RequestParam(required = false) Integer days,
        @RequestParam(required = false) Integer bucketDays,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(recordsManagementService.getActivityEventTypeTrend(days, bucketDays, limit));
    }

    @GetMapping("/records/activity-event-types")
    @Operation(summary = "Top RM activity event types over a recent window")
    public ResponseEntity<RecordsManagementService.ActivityEventTypesDto> getActivityEventTypes(
        @RequestParam(required = false) Integer days,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(recordsManagementService.getActivityEventTypes(days, limit));
    }

    @GetMapping("/records/activity-families")
    @Operation(summary = "RM activity family mix over a recent window")
    public ResponseEntity<RecordsManagementService.ActivityFamiliesDto> getActivityFamilies(
        @RequestParam(required = false) Integer days
    ) {
        return ResponseEntity.ok(recordsManagementService.getActivityFamilies(days));
    }

    @GetMapping("/records/activity-family-highlights")
    @Operation(summary = "Compare RM activity family windows")
    public ResponseEntity<RecordsManagementService.ActivityFamilyHighlightsDto> getActivityFamilyHighlights(
        @RequestParam(required = false) Integer windowDays
    ) {
        return ResponseEntity.ok(recordsManagementService.getActivityFamilyHighlights(windowDays));
    }

    @GetMapping("/records/activity-family-trend")
    @Operation(summary = "Summarize RM activity family trend across contiguous buckets")
    public ResponseEntity<RecordsManagementService.ActivityFamilyTrendDto> getActivityFamilyTrend(
        @RequestParam(required = false) Integer days,
        @RequestParam(required = false) Integer bucketDays
    ) {
        return ResponseEntity.ok(recordsManagementService.getActivityFamilyTrend(days, bucketDays));
    }

    @GetMapping("/records/activity-event-type-report")
    @Operation(summary = "Build an RM activity event-type report for a closed audit range")
    public ResponseEntity<?> getActivityEventTypeReport(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) String format
    ) {
        RecordsManagementService.ActivityEventTypeReportDto report =
            recordsManagementService.getActivityEventTypeReport(from, to, limit);
        String normalizedFormat = normalizeReportFormat(format);
        if ("csv".equals(normalizedFormat)) {
            byte[] csv = buildActivityEventTypeReportCsv(report).getBytes(StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment()
                .filename(
                    "rm-activity-event-type-report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv",
                    StandardCharsets.UTF_8
                )
                .build());
            headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
            headers.setContentLength(csv.length);
            return ResponseEntity.ok().headers(headers).body(csv);
        }
        return ResponseEntity.ok(report);
    }

    @GetMapping("/records/activity-family-report")
    @Operation(summary = "Build an RM activity family report for a closed audit range")
    public ResponseEntity<?> getActivityFamilyReport(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
        @RequestParam(required = false) Integer eventTypeLimit,
        @RequestParam(required = false) Integer contributorLimit,
        @RequestParam(required = false) String format
    ) {
        RecordsManagementService.ActivityFamilyReportDto report =
            recordsManagementService.getActivityFamilyReport(from, to, eventTypeLimit, contributorLimit);
        String normalizedFormat = normalizeReportFormat(format);
        if ("csv".equals(normalizedFormat)) {
            byte[] csv = buildActivityFamilyReportCsv(report).getBytes(StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment()
                .filename(
                    "rm-activity-family-report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv",
                    StandardCharsets.UTF_8
                )
                .build());
            headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
            headers.setContentLength(csv.length);
            return ResponseEntity.ok().headers(headers).body(csv);
        }
        return ResponseEntity.ok(report);
    }

    @GetMapping("/records/activity-breakdown")
    @Operation(summary = "Summarize records management activity breakdown")
    public ResponseEntity<RecordsManagementService.RecordsActivityBreakdownDto> getActivityBreakdown(
        @RequestParam(required = false) Integer days,
        @RequestParam(required = false) Integer bucketDays
    ) {
        return ResponseEntity.ok(recordsManagementService.getActivityBreakdown(days, bucketDays));
    }

    @GetMapping("/records/file-plans")
    @Operation(summary = "List file plan folders")
    public ResponseEntity<List<RecordsManagementService.FilePlanDto>> listFilePlans() {
        return ResponseEntity.ok(recordsManagementService.listFilePlans());
    }

    @PostMapping("/records/file-plans")
    @Operation(summary = "Create a file plan folder")
    public ResponseEntity<RecordsManagementService.FilePlanDto> createFilePlan(
        @RequestBody RecordsManagementService.CreateFilePlanRequest request
    ) {
        return ResponseEntity.ok(recordsManagementService.createFilePlan(request));
    }

    @PutMapping("/records/file-plans/{folderId}")
    @Operation(summary = "Update a file plan")
    public ResponseEntity<RecordsManagementService.FilePlanDto> updateFilePlan(
        @PathVariable UUID folderId,
        @RequestBody(required = false) RecordsManagementService.UpdateFilePlanRequest request
    ) {
        return ResponseEntity.ok(recordsManagementService.updateFilePlan(folderId, request));
    }

    @PutMapping("/records/file-plans/{folderId}/rename")
    @Operation(summary = "Rename a file plan")
    public ResponseEntity<RecordsManagementService.FilePlanDto> renameFilePlan(
        @PathVariable UUID folderId,
        @RequestBody(required = false) RecordsManagementService.RenameFilePlanRequest request
    ) {
        return ResponseEntity.ok(recordsManagementService.renameFilePlan(folderId, request));
    }

    @PutMapping("/records/file-plans/{folderId}/move")
    @Operation(summary = "Move a file plan under another allowed RM parent")
    public ResponseEntity<RecordsManagementService.FilePlanDto> moveFilePlan(
        @PathVariable UUID folderId,
        @RequestBody(required = false) RecordsManagementService.MoveFilePlanRequest request
    ) {
        return ResponseEntity.ok(recordsManagementService.moveFilePlan(folderId, request));
    }

    @DeleteMapping("/records/file-plans/{folderId}")
    @Operation(summary = "Delete an empty file plan")
    public ResponseEntity<Void> deleteFilePlan(@PathVariable UUID folderId) {
        recordsManagementService.deleteFilePlan(folderId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/records/categories")
    @Operation(summary = "List record categories")
    public ResponseEntity<List<RecordsManagementService.RecordCategoryDto>> listRecordCategories() {
        return ResponseEntity.ok(recordsManagementService.listRecordCategories());
    }

    @PostMapping("/records/categories")
    @Operation(summary = "Create a record category")
    public ResponseEntity<RecordsManagementService.RecordCategoryDto> createRecordCategory(
        @RequestBody RecordsManagementService.CreateRecordCategoryRequest request
    ) {
        return ResponseEntity.ok(recordsManagementService.createRecordCategory(request));
    }

    @PutMapping("/records/categories/{categoryId}")
    @Operation(summary = "Update a record category")
    public ResponseEntity<RecordsManagementService.RecordCategoryDto> updateRecordCategory(
        @PathVariable UUID categoryId,
        @RequestBody(required = false) RecordsManagementService.UpdateRecordCategoryRequest request
    ) {
        return ResponseEntity.ok(recordsManagementService.updateRecordCategory(categoryId, request));
    }

    @PutMapping("/records/categories/{categoryId}/rename")
    @Operation(summary = "Rename a record category")
    public ResponseEntity<RecordsManagementService.RecordCategoryDto> renameRecordCategory(
        @PathVariable UUID categoryId,
        @RequestBody(required = false) RecordsManagementService.RenameRecordCategoryRequest request
    ) {
        return ResponseEntity.ok(recordsManagementService.renameRecordCategory(categoryId, request));
    }

    @PutMapping("/records/categories/{categoryId}/move")
    @Operation(summary = "Move a record category within the RM tree")
    public ResponseEntity<RecordsManagementService.RecordCategoryDto> moveRecordCategory(
        @PathVariable UUID categoryId,
        @RequestBody(required = false) RecordsManagementService.MoveRecordCategoryRequest request
    ) {
        return ResponseEntity.ok(recordsManagementService.moveRecordCategory(categoryId, request));
    }

    @DeleteMapping("/records/categories/{categoryId}")
    @Operation(summary = "Delete an unused leaf record category")
    public ResponseEntity<Void> deleteRecordCategory(@PathVariable UUID categoryId) {
        recordsManagementService.deleteRecordCategory(categoryId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/nodes/{nodeId}/record")
    @Operation(summary = "Get record declaration for a node")
    public ResponseEntity<RecordsManagementService.RecordDeclarationDto> getRecord(@PathVariable UUID nodeId) {
        return recordsManagementService.getRecord(nodeId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/nodes/{nodeId}/record")
    @Operation(summary = "Declare a document as a record")
    public ResponseEntity<RecordsManagementService.RecordDeclarationDto> declareRecord(
        @PathVariable UUID nodeId,
        @RequestBody(required = false) RecordsManagementService.DeclareRecordRequest request
    ) {
        return ResponseEntity.ok(recordsManagementService.declareRecord(nodeId, request));
    }

    @PostMapping("/nodes/{nodeId}/record/undeclare")
    @Operation(summary = "Undeclare a document as a record")
    public ResponseEntity<Void> undeclareRecord(
        @PathVariable UUID nodeId,
        @RequestBody(required = false) RecordsManagementService.UndeclareRecordRequest request
    ) {
        recordsManagementService.undeclareRecord(nodeId, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/nodes/{nodeId}/record/category")
    @Operation(summary = "Assign a record category to a declared record")
    public ResponseEntity<RecordsManagementService.RecordDeclarationDto> assignRecordCategory(
        @PathVariable UUID nodeId,
        @RequestBody RecordsManagementService.RecordCategoryAssignmentRequest request
    ) {
        return ResponseEntity.ok(recordsManagementService.assignRecordCategory(nodeId, request.categoryId()));
    }

    private String normalizeReportFormat(String format) {
        if (format == null || format.isBlank()) {
            return "json";
        }
        String normalized = format.trim().toLowerCase();
        if ("json".equals(normalized) || "csv".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Unsupported format: " + format);
    }

    private LocalDateTime requirePresetDateTimeParam(RmReportPreset preset, String key) {
        String value = requirePresetStringParam(preset, key);
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                "Preset param \"" + key + "\" must be an ISO-8601 datetime for " + preset.getKind()
            );
        }
    }

    private String requirePresetStringParam(RmReportPreset preset, String key) {
        Object value = presetParam(preset, key);
        if (value == null) {
            throw new IllegalArgumentException(
                "Preset param \"" + key + "\" is required for " + preset.getKind()
            );
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue.trim();
        }
        throw new IllegalArgumentException(
            "Preset param \"" + key + "\" must be a non-blank string for " + preset.getKind()
        );
    }

    private Integer optionalPresetIntegerParam(RmReportPreset preset, String key) {
        Object value = presetParam(preset, key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number numberValue) {
            double doubleValue = numberValue.doubleValue();
            if (Double.isFinite(doubleValue) && Math.rint(doubleValue) == doubleValue) {
                return numberValue.intValue();
            }
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Integer.valueOf(stringValue.trim());
            } catch (NumberFormatException ignored) {
                // Fall through to the consistent bad-request below.
            }
        }
        throw new IllegalArgumentException(
            "Preset param \"" + key + "\" must be an integer for " + preset.getKind()
        );
    }

    private LocalDateTime optionalPresetDateTimeParam(RmReportPreset preset, String key) {
        Object value = presetParam(preset, key);
        if (value == null) {
            return null;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return LocalDateTime.parse(stringValue.trim());
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException(
                    "Preset param \"" + key + "\" must be an ISO-8601 datetime for " + preset.getKind()
                );
            }
        }
        throw new IllegalArgumentException(
            "Preset param \"" + key + "\" must be an ISO-8601 datetime for " + preset.getKind()
        );
    }

    private Integer resolvePresetRollingDays(RmReportPreset preset, String key) {
        Integer explicitDays = optionalPresetIntegerParam(preset, key);
        if (explicitDays != null) {
            return explicitDays;
        }
        LocalDateTime from = optionalPresetDateTimeParam(preset, "from");
        LocalDateTime to = optionalPresetDateTimeParam(preset, "to");
        if (from != null && to != null) {
            long dayCount = java.time.temporal.ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate()) + 1L;
            return (int) Math.max(dayCount, 1L);
        }
        return null;
    }

    private ResolvedPresetDateTimeRange requirePresetRollingDateTimeRange(RmReportPreset preset, String dayKey) {
        LocalDateTime from = optionalPresetDateTimeParam(preset, "from");
        LocalDateTime to = optionalPresetDateTimeParam(preset, "to");
        if (from != null && to != null) {
            return new ResolvedPresetDateTimeRange(from, to);
        }
        Integer days = resolvePresetRollingDays(preset, dayKey);
        if (days == null || days < 1) {
            throw new IllegalArgumentException(
                "Preset param \"" + dayKey + "\" is required for CSV export on " + preset.getKind()
            );
        }
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1L);
        return new ResolvedPresetDateTimeRange(startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
    }

    private Object presetParam(RmReportPreset preset, String key) {
        Map<String, Object> params = preset.getParams();
        return params == null ? null : params.get(key);
    }

    private record ResolvedPresetDateTimeRange(
        LocalDateTime from,
        LocalDateTime to
    ) {
    }

    private String buildActivityContributorFamilyReportCsv(RecordsManagementService.ActivityContributorFamilyReportDto report) {
        StringBuilder sb = new StringBuilder();
        sb.append("username,label,family,currentCount,previousCount,delta,lastEventTime,currentFrom,currentTo,previousFrom,previousTo\n");
        for (RecordsManagementService.ActivityContributorFamilyReportEntryDto contributor : report.contributors()) {
            for (RecordsManagementService.ActivityContributorFamilyReportFamilyDto family : contributor.families()) {
                sb.append(csvEscape(contributor.username())).append(',')
                    .append(csvEscape(contributor.label())).append(',')
                    .append(csvEscape(family.family())).append(',')
                    .append(family.currentCount()).append(',')
                    .append(family.previousCount()).append(',')
                    .append(family.delta()).append(',')
                    .append(csvEscape(family.lastEventTime())).append(',')
                    .append(csvEscape(report.currentWindow().from())).append(',')
                    .append(csvEscape(report.currentWindow().to())).append(',')
                    .append(csvEscape(report.previousWindow().from())).append(',')
                    .append(csvEscape(report.previousWindow().to()))
                    .append('\n');
            }
        }
        return sb.toString();
    }

    private String buildActivityContributorReportCsv(RecordsManagementService.ActivityContributorReportDto report) {
        StringBuilder sb = new StringBuilder();
        sb.append("username,label,currentCount,previousCount,delta,lastEventTime,currentTopEventTypes,currentFrom,currentTo,previousFrom,previousTo\n");
        for (RecordsManagementService.ActivityContributorReportEntryDto entry : report.contributors()) {
            sb.append(csvEscape(entry.username())).append(',')
                .append(csvEscape(entry.label())).append(',')
                .append(entry.currentCount()).append(',')
                .append(entry.previousCount()).append(',')
                .append(entry.delta()).append(',')
                .append(csvEscape(entry.lastEventTime())).append(',')
                .append(csvEscape(joinContributorReportEventTypes(entry.currentTopEventTypes()))).append(',')
                .append(csvEscape(report.currentWindow().from())).append(',')
                .append(csvEscape(report.currentWindow().to())).append(',')
                .append(csvEscape(report.previousWindow().from())).append(',')
                .append(csvEscape(report.previousWindow().to()))
                .append('\n');
        }
        return sb.toString();
    }

    private String buildActivityContributorEventTypeReportCsv(RecordsManagementService.ActivityContributorEventTypeReportDto report) {
        StringBuilder sb = new StringBuilder();
        sb.append("username,label,eventType,family,currentCount,previousCount,delta,lastEventTime,currentFrom,currentTo,previousFrom,previousTo\n");
        for (RecordsManagementService.ActivityContributorEventTypeReportEntryDto contributor : report.contributors()) {
            for (RecordsManagementService.ActivityContributorEventTypeReportEventTypeDto eventType : contributor.eventTypes()) {
                sb.append(csvEscape(contributor.username())).append(',')
                    .append(csvEscape(contributor.label())).append(',')
                    .append(csvEscape(eventType.eventType())).append(',')
                    .append(csvEscape(eventType.family())).append(',')
                    .append(eventType.currentCount()).append(',')
                    .append(eventType.previousCount()).append(',')
                    .append(eventType.delta()).append(',')
                    .append(csvEscape(eventType.lastEventTime())).append(',')
                    .append(csvEscape(report.currentWindow().from())).append(',')
                    .append(csvEscape(report.currentWindow().to())).append(',')
                    .append(csvEscape(report.previousWindow().from())).append(',')
                    .append(csvEscape(report.previousWindow().to()))
                    .append('\n');
            }
        }
        return sb.toString();
    }

    private String buildActivityEventTypeReportCsv(RecordsManagementService.ActivityEventTypeReportDto report) {
        StringBuilder sb = new StringBuilder();
        sb.append("eventType,family,currentCount,previousCount,delta,lastEventTime,currentFrom,currentTo,previousFrom,previousTo\n");
        for (RecordsManagementService.ActivityEventTypeReportEntryDto entry : report.eventTypes()) {
            sb.append(csvEscape(entry.eventType())).append(',')
                .append(csvEscape(entry.family())).append(',')
                .append(entry.currentCount()).append(',')
                .append(entry.previousCount()).append(',')
                .append(entry.delta()).append(',')
                .append(csvEscape(entry.lastEventTime())).append(',')
                .append(csvEscape(report.currentWindow().from())).append(',')
                .append(csvEscape(report.currentWindow().to())).append(',')
                .append(csvEscape(report.previousWindow().from())).append(',')
                .append(csvEscape(report.previousWindow().to()))
                .append('\n');
        }
        return sb.toString();
    }

    private String buildActivityFamilyReportCsv(RecordsManagementService.ActivityFamilyReportDto report) {
        StringBuilder sb = new StringBuilder();
        sb.append("family,currentCount,previousCount,delta,lastEventTime,topEventTypes,topContributors,currentFrom,currentTo,previousFrom,previousTo\n");
        for (RecordsManagementService.ActivityFamilyReportEntryDto entry : report.families()) {
            sb.append(csvEscape(entry.family())).append(',')
                .append(entry.currentCount()).append(',')
                .append(entry.previousCount()).append(',')
                .append(entry.delta()).append(',')
                .append(csvEscape(entry.lastEventTime())).append(',')
                .append(csvEscape(joinReportEventTypes(entry.topEventTypes()))).append(',')
                .append(csvEscape(joinReportContributors(entry.topContributors()))).append(',')
                .append(csvEscape(report.currentWindow().from())).append(',')
                .append(csvEscape(report.currentWindow().to())).append(',')
                .append(csvEscape(report.previousWindow().from())).append(',')
                .append(csvEscape(report.previousWindow().to()))
                .append('\n');
        }
        return sb.toString();
    }

    private String joinReportEventTypes(List<RecordsManagementService.ActivityFamilyReportEventTypeDto> eventTypes) {
        return eventTypes.stream()
            .map(item -> item.eventType() + " (" + item.count() + ")")
            .reduce((left, right) -> left + "; " + right)
            .orElse("");
    }

    private String joinReportContributors(List<RecordsManagementService.ActivityFamilyReportContributorDto> contributors) {
        return contributors.stream()
            .map(item -> item.label() + " (" + item.count() + ")")
            .reduce((left, right) -> left + "; " + right)
            .orElse("");
    }

    private String joinContributorReportEventTypes(List<RecordsManagementService.ActivityContributorReportEventTypeDto> eventTypes) {
        return eventTypes.stream()
            .map(item -> item.eventType() + " [" + item.family() + "] (" + item.count() + ")")
            .reduce((left, right) -> left + "; " + right)
            .orElse("");
    }

    private String csvEscape(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString().replace("\"", "\"\"");
        if (text.contains(",") || text.contains("\n") || text.contains("\r") || text.contains("\"")) {
            return "\"" + text + "\"";
        }
        return text;
    }
}
