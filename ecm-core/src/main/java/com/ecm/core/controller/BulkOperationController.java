package com.ecm.core.controller;

import com.ecm.core.entity.AuditLog;
import com.ecm.core.repository.AuditLogRepository;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.BulkMetadataService;
import com.ecm.core.service.BulkOperationService;
import com.ecm.core.service.BulkOperationService.BulkOperationResult;
import com.ecm.core.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bulk")
@RequiredArgsConstructor
@Tag(name = "Bulk Operations", description = "APIs for batch processing nodes")
public class BulkOperationController {
    private static final int HISTORY_TREND_PAGE_SIZE = 500;
    private static final int MAX_HISTORY_TREND_SCAN = 20_000;

    private final BulkOperationService bulkService;
    private final BulkMetadataService bulkMetadataService;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final SecurityService securityService;

    @PostMapping("/move")
    @Operation(summary = "Bulk move", description = "Move multiple nodes to a target folder")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<BulkOperationResult> bulkMove(@RequestBody BulkRequest request) {
        if (request.targetId() == null) {
            throw new IllegalArgumentException("Target folder ID is required for move");
        }
        return ResponseEntity.ok(bulkService.bulkMove(request.ids(), request.targetId()));
    }

    @PostMapping("/copy")
    @Operation(summary = "Bulk copy", description = "Copy multiple nodes to a target folder")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<BulkOperationResult> bulkCopy(@RequestBody BulkRequest request) {
        if (request.targetId() == null) {
            throw new IllegalArgumentException("Target folder ID is required for copy");
        }
        return ResponseEntity.ok(bulkService.bulkCopy(request.ids(), request.targetId()));
    }

    @PostMapping("/delete")
    @Operation(summary = "Bulk delete", description = "Move multiple nodes to trash")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<BulkOperationResult> bulkDelete(@RequestBody BulkRequest request) {
        return ResponseEntity.ok(bulkService.bulkDelete(request.ids()));
    }

    @PostMapping("/restore")
    @Operation(summary = "Bulk restore", description = "Restore multiple nodes from trash")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<BulkOperationResult> bulkRestore(@RequestBody BulkRequest request) {
        return ResponseEntity.ok(bulkService.bulkRestore(request.ids()));
    }

    @PostMapping("/metadata")
    @Operation(summary = "Bulk metadata update", description = "Apply tags, categories, or correspondent updates to nodes")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<BulkMetadataService.BulkMetadataResult> bulkMetadataUpdate(
            @RequestBody BulkMetadataService.BulkMetadataRequest request) {
        return ResponseEntity.ok(bulkMetadataService.applyMetadata(request));
    }

    @GetMapping("/history")
    @Operation(
        summary = "List bulk governance timeline",
        description = "Returns recent bulk operation audit events (BULK_*)."
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<BulkHistoryResponse> listBulkHistory(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) UUID nodeId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(size, 200));
        LocalDateTime fromAt = parseDateTimeFilter("from", from);
        LocalDateTime toAt = parseDateTimeFilter("to", to);
        Page<AuditLog> historyPage = nodeId == null
            ? auditLogRepository.findBulkOperationTimelineNoNodeId(
                eventType, actor, fromAt, toAt, PageRequest.of(boundedPage, boundedSize))
            : auditLogRepository.findBulkOperationTimeline(
                eventType, actor, nodeId, fromAt, toAt, PageRequest.of(boundedPage, boundedSize));

        List<BulkHistoryItemResponse> items = historyPage.getContent().stream()
            .map(this::toBulkHistoryItem)
            .toList();

        return ResponseEntity.ok(new BulkHistoryResponse(
            boundedPage,
            boundedSize,
            historyPage.getTotalPages(),
            historyPage.getTotalElements(),
            eventType,
            actor,
            nodeId,
            fromAt,
            toAt,
            items
        ));
    }

    @GetMapping(value = "/history/export", produces = "text/csv")
    @Operation(
        summary = "Export bulk governance timeline",
        description = "Export filtered bulk operation audit events to CSV."
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<byte[]> exportBulkHistory(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) UUID nodeId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "500") int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 2000));
        LocalDateTime fromAt = parseDateTimeFilter("from", from);
        LocalDateTime toAt = parseDateTimeFilter("to", to);
        List<BulkHistoryItemResponse> rows = (nodeId == null
            ? auditLogRepository.findBulkOperationTimelineNoNodeId(
                eventType, actor, fromAt, toAt, PageRequest.of(0, boundedLimit))
            : auditLogRepository.findBulkOperationTimeline(
                eventType, actor, nodeId, fromAt, toAt, PageRequest.of(0, boundedLimit))
        ).getContent().stream()
            .map(this::toBulkHistoryItem)
            .toList();

        String username = securityService.getCurrentUser();
        auditService.logEvent(
            "BULK_HISTORY_EXPORTED",
            nodeId,
            "bulk-history",
            username != null && !username.isBlank() ? username : "system",
            String.format(
                "Exported %d bulk history rows (eventType=%s, actor=%s, nodeId=%s, from=%s, to=%s)",
                rows.size(),
                eventType,
                actor,
                nodeId,
                fromAt,
                toAt
            )
        );

        byte[] csv = buildBulkHistoryCsv(rows).getBytes(StandardCharsets.UTF_8);
        String filename = "bulk-history-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(csv.length);
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    @GetMapping("/history/summary")
    @Operation(
        summary = "Summarize bulk governance timeline",
        description = "Return total + top event type/actor counters for bulk audit events."
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<BulkHistorySummaryResponse> summarizeBulkHistory(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) UUID nodeId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "10") int topN) {
        int boundedTopN = Math.max(1, Math.min(topN, 100));
        LocalDateTime fromAt = parseDateTimeFilter("from", from);
        LocalDateTime toAt = parseDateTimeFilter("to", to);
        BulkHistorySummaryResponse summary = computeBulkHistorySummary(
            eventType,
            actor,
            nodeId,
            fromAt,
            toAt,
            boundedTopN
        );
        return ResponseEntity.ok(summary);
    }

    @GetMapping(value = "/history/summary/export", produces = "text/csv")
    @Operation(
        summary = "Export bulk governance summary",
        description = "Export summary counters (event type/actor) for filtered bulk audit events."
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<byte[]> exportBulkHistorySummary(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) UUID nodeId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "20") int topN) {
        int boundedTopN = Math.max(1, Math.min(topN, 200));
        LocalDateTime fromAt = parseDateTimeFilter("from", from);
        LocalDateTime toAt = parseDateTimeFilter("to", to);
        BulkHistorySummaryResponse summary = computeBulkHistorySummary(
            eventType,
            actor,
            nodeId,
            fromAt,
            toAt,
            boundedTopN
        );

        String username = securityService.getCurrentUser();
        auditService.logEvent(
            "BULK_HISTORY_SUMMARY_EXPORTED",
            nodeId,
            "bulk-history-summary",
            username != null && !username.isBlank() ? username : "system",
            String.format(
                Locale.ROOT,
                "Exported bulk history summary total=%d eventTypeRows=%d actorRows=%d (eventType=%s, actor=%s, nodeId=%s, topN=%d)",
                summary.total(),
                summary.eventTypeItems().size(),
                summary.actorItems().size(),
                eventType,
                actor,
                nodeId,
                boundedTopN
            )
        );

        byte[] csv = buildBulkHistorySummaryCsv(summary).getBytes(StandardCharsets.UTF_8);
        String filename = "bulk-history-summary-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(csv.length);
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    @GetMapping("/history/summary/trend")
    @Operation(
        summary = "Trend bulk governance summary by day",
        description = "Return daily grouped counts for filtered bulk audit events."
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<BulkHistoryTrendResponse> trendBulkHistorySummary(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) UUID nodeId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime fromAt = parseDateTimeFilter("from", from);
        LocalDateTime toAt = parseDateTimeFilter("to", to);
        return ResponseEntity.ok(computeBulkHistoryTrend(eventType, actor, nodeId, fromAt, toAt));
    }

    @GetMapping(value = "/history/summary/trend/export", produces = "text/csv")
    @Operation(
        summary = "Export bulk governance trend",
        description = "Export daily grouped trend for filtered bulk audit events."
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<byte[]> exportBulkHistoryTrend(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) UUID nodeId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime fromAt = parseDateTimeFilter("from", from);
        LocalDateTime toAt = parseDateTimeFilter("to", to);
        BulkHistoryTrendResponse trend = computeBulkHistoryTrend(eventType, actor, nodeId, fromAt, toAt);

        String username = securityService.getCurrentUser();
        auditService.logEvent(
            "BULK_HISTORY_TREND_EXPORTED",
            nodeId,
            "bulk-history-trend",
            username != null && !username.isBlank() ? username : "system",
            String.format(
                Locale.ROOT,
                "Exported bulk history trend rows=%d truncated=%s (eventType=%s, actor=%s, nodeId=%s, from=%s, to=%s)",
                trend.items().size(),
                trend.truncated(),
                eventType,
                actor,
                nodeId,
                fromAt,
                toAt
            )
        );

        byte[] csv = buildBulkHistoryTrendCsv(trend).getBytes(StandardCharsets.UTF_8);
        String filename = "bulk-history-trend-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(csv.length);
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    private BulkHistoryItemResponse toBulkHistoryItem(AuditLog auditLog) {
        return new BulkHistoryItemResponse(
            auditLog.getId(),
            auditLog.getEventType(),
            auditLog.getNodeId(),
            auditLog.getNodeName(),
            auditLog.getUsername(),
            auditLog.getEventTime(),
            auditLog.getDetails()
        );
    }

    private BulkHistorySummaryResponse computeBulkHistorySummary(
            String eventType,
            String actor,
            UUID nodeId,
            LocalDateTime fromAt,
            LocalDateTime toAt,
            int topN) {
        long total = (nodeId == null
            ? auditLogRepository.findBulkOperationTimelineNoNodeId(
                eventType, actor, fromAt, toAt, PageRequest.of(0, 1))
            : auditLogRepository.findBulkOperationTimeline(
                eventType, actor, nodeId, fromAt, toAt, PageRequest.of(0, 1))
        ).getTotalElements();

        List<BulkHistorySummaryCounterItem> eventTypeItems = auditLogRepository
            .countBulkByEventTypeWithFilters(eventType, actor, nodeId, fromAt, toAt).stream()
            .limit(topN)
            .map(row -> new BulkHistorySummaryCounterItem(
                row[0] != null ? row[0].toString() : "UNKNOWN",
                row[1] instanceof Number number ? number.longValue() : 0L
            ))
            .toList();

        List<BulkHistorySummaryCounterItem> actorItems = auditLogRepository
            .countBulkByUsernameWithFilters(eventType, actor, nodeId, fromAt, toAt).stream()
            .limit(topN)
            .map(row -> new BulkHistorySummaryCounterItem(
                row[0] != null ? row[0].toString() : "UNKNOWN",
                row[1] instanceof Number number ? number.longValue() : 0L
            ))
            .toList();

        return new BulkHistorySummaryResponse(
            total,
            topN,
            eventType,
            actor,
            nodeId,
            fromAt,
            toAt,
            eventTypeItems,
            actorItems
        );
    }

    private BulkHistoryTrendResponse computeBulkHistoryTrend(
            String eventType,
            String actor,
            UUID nodeId,
            LocalDateTime fromAt,
            LocalDateTime toAt) {
        int page = 0;
        int scanned = 0;
        boolean truncated = false;
        Map<String, Long> groupedByDate = new TreeMap<>();

        while (scanned < MAX_HISTORY_TREND_SCAN) {
            int requestSize = Math.min(HISTORY_TREND_PAGE_SIZE, MAX_HISTORY_TREND_SCAN - scanned);
            if (requestSize <= 0) {
                truncated = true;
                break;
            }
            PageRequest pageRequest = PageRequest.of(page, requestSize);
            Page<AuditLog> historyPage = nodeId == null
                ? auditLogRepository.findBulkOperationTimelineNoNodeId(
                    eventType, actor, fromAt, toAt, pageRequest)
                : auditLogRepository.findBulkOperationTimeline(
                    eventType, actor, nodeId, fromAt, toAt, pageRequest);

            List<AuditLog> content = historyPage.getContent();
            if (content.isEmpty()) {
                break;
            }
            scanned += content.size();
            for (AuditLog auditLog : content) {
                if (auditLog.getEventTime() == null) {
                    continue;
                }
                String dateKey = auditLog.getEventTime().toLocalDate().toString();
                groupedByDate.merge(dateKey, 1L, Long::sum);
            }

            if (!historyPage.hasNext()) {
                break;
            }
            if (scanned >= MAX_HISTORY_TREND_SCAN) {
                truncated = true;
                break;
            }
            page += 1;
        }

        List<BulkHistoryTrendItem> items = groupedByDate.entrySet().stream()
            .map(entry -> new BulkHistoryTrendItem(entry.getKey(), entry.getValue()))
            .toList();

        return new BulkHistoryTrendResponse(
            eventType,
            actor,
            nodeId,
            fromAt,
            toAt,
            MAX_HISTORY_TREND_SCAN,
            truncated,
            items
        );
    }

    private LocalDateTime parseDateTimeFilter(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException ex) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid datetime for " + fieldName + ": " + value
                );
            }
        }
    }

    private String buildBulkHistoryCsv(List<BulkHistoryItemResponse> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,eventType,nodeId,nodeName,username,eventTime,details").append('\n');
        for (BulkHistoryItemResponse row : rows) {
            sb.append(csvEscape(row.id())).append(',')
                .append(csvEscape(row.eventType())).append(',')
                .append(csvEscape(row.nodeId())).append(',')
                .append(csvEscape(row.nodeName())).append(',')
                .append(csvEscape(row.username())).append(',')
                .append(csvEscape(row.eventTime())).append(',')
                .append(csvEscape(row.details()))
                .append('\n');
        }
        return sb.toString();
    }

    private String buildBulkHistorySummaryCsv(BulkHistorySummaryResponse summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("section,key,count").append('\n');
        sb.append("meta,total,").append(summary.total()).append('\n');
        for (BulkHistorySummaryCounterItem item : summary.eventTypeItems()) {
            sb.append("eventType,")
                .append(csvEscape(item.key())).append(',')
                .append(item.count())
                .append('\n');
        }
        for (BulkHistorySummaryCounterItem item : summary.actorItems()) {
            sb.append("actor,")
                .append(csvEscape(item.key())).append(',')
                .append(item.count())
                .append('\n');
        }
        return sb.toString();
    }

    private String buildBulkHistoryTrendCsv(BulkHistoryTrendResponse trend) {
        StringBuilder sb = new StringBuilder();
        sb.append("date,count").append('\n');
        for (BulkHistoryTrendItem item : trend.items()) {
            sb.append(csvEscape(item.date())).append(',')
                .append(item.count())
                .append('\n');
        }
        return sb.toString();
    }

    private String csvEscape(Object value) {
        if (value == null) {
            return "";
        }
        String raw = value.toString();
        if (raw.contains(",") || raw.contains("\"") || raw.contains("\n") || raw.contains("\r")) {
            return "\"" + raw.replace("\"", "\"\"") + "\"";
        }
        return raw;
    }

    // DTO
    public record BulkRequest(List<UUID> ids, UUID targetId) {}

    public record BulkHistoryResponse(
        int page,
        int size,
        int totalPages,
        long total,
        String eventTypeFilter,
        String actorFilter,
        UUID nodeIdFilter,
        LocalDateTime from,
        LocalDateTime to,
        List<BulkHistoryItemResponse> items
    ) {}

    public record BulkHistoryItemResponse(
        UUID id,
        String eventType,
        UUID nodeId,
        String nodeName,
        String username,
        LocalDateTime eventTime,
        String details
    ) {}

    public record BulkHistorySummaryResponse(
        long total,
        int topN,
        String eventTypeFilter,
        String actorFilter,
        UUID nodeIdFilter,
        LocalDateTime from,
        LocalDateTime to,
        List<BulkHistorySummaryCounterItem> eventTypeItems,
        List<BulkHistorySummaryCounterItem> actorItems
    ) {}

    public record BulkHistorySummaryCounterItem(
        String key,
        long count
    ) {}

    public record BulkHistoryTrendResponse(
        String eventTypeFilter,
        String actorFilter,
        UUID nodeIdFilter,
        LocalDateTime from,
        LocalDateTime to,
        int scanLimit,
        boolean truncated,
        List<BulkHistoryTrendItem> items
    ) {}

    public record BulkHistoryTrendItem(
        String date,
        long count
    ) {}
}
