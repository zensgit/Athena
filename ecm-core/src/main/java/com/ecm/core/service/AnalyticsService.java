package com.ecm.core.service;

import com.ecm.core.entity.AuditLog;
import com.ecm.core.entity.AuditCategory;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.repository.AuditLogRepository;
import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analytics Service
 *
 * Provides system usage statistics and reports for dashboards.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private final AuditLogRepository auditLogRepository;
    private final NodeRepository nodeRepository;

    private static final List<String> RULE_EVENT_TYPES = List.of(
        "RULE_EXECUTED",
        "RULE_EXECUTION_FAILED",
        "SCHEDULED_RULE_BATCH_COMPLETED",
        "SCHEDULED_RULE_BATCH_PARTIAL"
    );

    @Value("${ecm.audit.retention-days:365}")
    private int auditRetentionDays;

    /**
     * Get overall system summary stats
     */
    public SystemSummaryStats getSystemSummary() {
        long totalDocs = nodeRepository.countDocuments();
        long totalFolders = nodeRepository.countFolders();
        
        // Calculate total size directly if possible, or iterate (less efficient)
        // Using a custom query on NodeRepository would be better
        long totalSize = nodeRepository.getTotalSize();

        return new SystemSummaryStats(totalDocs, totalFolders, totalSize);
    }

    /**
     * Get storage usage breakdown by MIME type
     */
    public List<MimeTypeStats> getStorageByMimeType() {
        // This requires a custom query on NodeRepository
        List<Object[]> results = nodeRepository.countByMimeType();
        
        return results.stream()
            .map(row -> new MimeTypeStats(
                (String) row[0], // mimeType
                (Long) row[1],   // count
                row.length > 2 ? (Long) row[2] : 0L // size (if query returns it)
            ))
            .sorted(Comparator.comparingLong(MimeTypeStats::count).reversed())
            .limit(10)
            .collect(Collectors.toList());
    }

    /**
     * Get daily activity stats (last 30 days)
     */
    public List<DailyActivityStats> getDailyActivity(int days) {
        LocalDateTime startTime = LocalDateTime.now().minusDays(days);
        List<Object[]> results = auditLogRepository.getDailyActivityStats(startTime);
        
        // Convert to DTOs and fill gaps
        Map<LocalDate, Long> activityMap = results.stream()
            .collect(Collectors.toMap(
                row -> {
                    java.sql.Date date = (java.sql.Date) row[0];
                    return date.toLocalDate();
                },
                row -> (Long) row[1]
            ));
            
        List<DailyActivityStats> stats = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = LocalDate.now().minusDays(days - 1 - i);
            stats.add(new DailyActivityStats(
                date,
                activityMap.getOrDefault(date, 0L)
            ));
        }
        
        return stats;
    }

    /**
     * Get top active users
     */
    public List<UserActivityStats> getTopUsers(int limit) {
        List<Object[]> results = auditLogRepository.findTopActiveUsers(PageRequest.of(0, limit));
        
        return results.stream()
            .map(row -> new UserActivityStats(
                (String) row[0], // username
                (Long) row[1]    // activity count
            ))
            .collect(Collectors.toList());
    }

    /**
     * Get recent audit logs
     */
    public List<AuditLog> getRecentActivity(int limit) {
        return auditLogRepository.findAll(PageRequest.of(0, limit,
            org.springframework.data.domain.Sort.by("eventTime").descending())).getContent();
    }

    /**
     * Get recent rule execution audit logs
     */
    public List<AuditLog> getRecentRuleActivity(int limit) {
        return auditLogRepository.findByEventTypeInOrderByEventTimeDesc(
            RULE_EVENT_TYPES,
            PageRequest.of(0, limit)
        ).getContent();
    }

    /**
     * Get summary stats for rule execution audit logs
     */
    public RuleExecutionSummary getRuleExecutionSummary(int days) {
        LocalDateTime startTime = LocalDateTime.now().minusDays(days);
        List<Object[]> results = auditLogRepository.countByEventTypeSince(startTime, RULE_EVENT_TYPES);
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : results) {
            counts.put((String) row[0], (Long) row[1]);
        }

        long executed = counts.getOrDefault("RULE_EXECUTED", 0L)
            + counts.getOrDefault("RULE_EXECUTION_FAILED", 0L);
        long failed = counts.getOrDefault("RULE_EXECUTION_FAILED", 0L);
        long scheduledBatches = counts.getOrDefault("SCHEDULED_RULE_BATCH_COMPLETED", 0L)
            + counts.getOrDefault("SCHEDULED_RULE_BATCH_PARTIAL", 0L);
        long scheduledFailures = counts.getOrDefault("SCHEDULED_RULE_BATCH_PARTIAL", 0L);
        double successRate = executed > 0 ? (double) (executed - failed) / executed * 100 : 0;

        return new RuleExecutionSummary(
            days,
            executed,
            failed,
            successRate,
            scheduledBatches,
            scheduledFailures,
            counts
        );
    }

    /**
     * Export audit logs as CSV within a time range
     */
    public AuditExportResult exportAuditLogsCsv(LocalDateTime from, LocalDateTime to) {
        List<AuditLog> logs = auditLogRepository.findByTimeRangeForExport(from, to);
        return new AuditExportResult(generateCsv(logs), logs.size());
    }

    public AuditExportResult exportAuditLogsCsv(LocalDateTime from,
                                                LocalDateTime to,
                                                String username,
                                                String eventType) {
        return exportAuditLogsCsv(from, to, username, eventType, null);
    }

    public AuditExportResult exportAuditLogsCsv(LocalDateTime from,
                                                LocalDateTime to,
                                                String username,
                                                String eventType,
                                                AuditCategory category) {
        return exportAuditLogsCsv(from, to, username, eventType, null, category);
    }

    public AuditExportResult exportAuditLogsCsv(LocalDateTime from,
                                                LocalDateTime to,
                                                String username,
                                                String eventType,
                                                UUID nodeId,
                                                AuditCategory category) {
        String categoryName = category != null ? category.name() : null;
        List<AuditLog> logs;
        if (nodeId == null) {
            logs = categoryName == null
                ? auditLogRepository.findByFiltersForExportNoNodeId(username, eventType, from, to)
                : auditLogRepository.findByFiltersForExportAndCategoryNoNodeId(username, eventType, categoryName, from, to);
        } else {
            logs = categoryName == null
                ? auditLogRepository.findByFiltersForExport(username, eventType, nodeId, from, to)
                : auditLogRepository.findByFiltersForExportAndCategory(username, eventType, categoryName, nodeId, from, to);
        }
        return new AuditExportResult(generateCsv(logs), logs.size());
    }

    /**
     * Get audit logs within a time range
     */
    public List<AuditLog> getAuditLogsInRange(LocalDateTime from, LocalDateTime to) {
        return auditLogRepository.findByTimeRangeForExport(from, to);
    }

    public Page<AuditLog> searchAuditLogs(String username,
                                          String eventType,
                                          LocalDateTime from,
                                          LocalDateTime to,
                                          Pageable pageable) {
        return searchAuditLogs(username, eventType, null, null, from, to, pageable);
    }

    public Page<AuditLog> searchAuditLogs(String username,
                                          String eventType,
                                          AuditCategory category,
                                          LocalDateTime from,
                                          LocalDateTime to,
                                          Pageable pageable) {
        return searchAuditLogs(username, eventType, category, null, from, to, pageable);
    }

    public Page<AuditLog> searchAuditLogs(String username,
                                          String eventType,
                                          AuditCategory category,
                                          UUID nodeId,
                                          LocalDateTime from,
                                          LocalDateTime to,
                                          Pageable pageable) {
        String categoryName = category != null ? category.name() : null;
        if (nodeId == null) {
            return auditLogRepository.findByFiltersAndCategoryNoNodeId(username, eventType, categoryName, from, to, pageable);
        }
        return auditLogRepository.findByFiltersAndCategory(username, eventType, categoryName, nodeId, from, to, pageable);
    }

    public List<AuditEventTypeCount> getAuditEventTypes(int limit) {
        List<Object[]> counts = auditLogRepository.countByEventType();
        int safeLimit = Math.max(1, limit);
        return counts.stream()
            .filter(item -> item != null && item.length >= 2 && item[0] != null)
            .map(item -> new AuditEventTypeCount(String.valueOf(item[0]), ((Number) item[1]).longValue()))
            .sorted(java.util.Comparator
                .comparingLong(AuditEventTypeCount::count)
                .reversed()
                .thenComparing(AuditEventTypeCount::eventType))
            .limit(safeLimit)
            .toList();
    }

    /**
     * Get audit report summary for a time window.
     */
    public AuditReportSummary getAuditReportSummary(int days) {
        int safeDays = Math.max(1, days);
        LocalDateTime startTime = LocalDateTime.now().minusDays(safeDays);
        List<Object[]> counts = auditLogRepository.countByEventTypeSince(startTime);
        EnumMap<AuditCategory, Long> categoryCounts = new EnumMap<>(AuditCategory.class);
        long total = 0L;
        if (counts != null) {
            for (Object[] row : counts) {
                if (row == null || row.length < 2 || row[0] == null) {
                    continue;
                }
                String eventType = String.valueOf(row[0]);
                long count = ((Number) row[1]).longValue();
                AuditCategory category = resolveCategory(eventType);
                categoryCounts.merge(category, count, Long::sum);
                total += count;
            }
        }
        return new AuditReportSummary(safeDays, total, categoryCounts);
    }

    /**
     * Generate CSV content from audit logs
     */
    private String generateCsv(List<AuditLog> logs) {
        StringBuilder csv = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // CSV header
        csv.append("ID,Event Type,Node ID,Node Name,Username,Event Time,Details,Client IP,User Agent\n");

        // CSV data rows
        for (AuditLog log : logs) {
            csv.append(escapeCsv(log.getId() != null ? log.getId().toString() : "")).append(",");
            csv.append(escapeCsv(log.getEventType())).append(",");
            csv.append(escapeCsv(log.getNodeId() != null ? log.getNodeId().toString() : "")).append(",");
            csv.append(escapeCsv(log.getNodeName())).append(",");
            csv.append(escapeCsv(log.getUsername())).append(",");
            csv.append(escapeCsv(log.getEventTime() != null ? log.getEventTime().format(formatter) : "")).append(",");
            csv.append(escapeCsv(log.getDetails())).append(",");
            csv.append(escapeCsv(log.getClientIp())).append(",");
            csv.append(escapeCsv(log.getUserAgent())).append("\n");
        }

        return csv.toString();
    }

    /**
     * Escape CSV field value
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // Escape double quotes and wrap in quotes if contains special characters
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private AuditCategory resolveCategory(String eventType) {
        if (eventType == null) {
            return AuditCategory.OTHER;
        }
        String upper = eventType.toUpperCase(Locale.ROOT);
        if (upper.startsWith("NODE_")) {
            return AuditCategory.NODE;
        }
        if (upper.startsWith("VERSION_")) {
            return AuditCategory.VERSION;
        }
        if (upper.startsWith("RULE_") || upper.startsWith("SCHEDULED_RULE")) {
            return AuditCategory.RULE;
        }
        if (upper.startsWith("WORKFLOW_") || upper.startsWith("STATUS_")) {
            return AuditCategory.WORKFLOW;
        }
        if (upper.startsWith("MAIL_")) {
            return AuditCategory.MAIL;
        }
        if (upper.startsWith("WOPI_")) {
            return AuditCategory.INTEGRATION;
        }
        if (upper.startsWith("SECURITY_")) {
            return AuditCategory.SECURITY;
        }
        if (upper.startsWith("PDF_")) {
            return AuditCategory.PDF;
        }
        return AuditCategory.OTHER;
    }

    /**
     * Get audit retention configuration
     */
    public int getAuditRetentionDays() {
        return auditRetentionDays;
    }

    /**
     * Get count of logs that would be cleaned up based on retention policy
     */
    public long getExpiredAuditLogCount() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(auditRetentionDays);
        return auditLogRepository.countByEventTimeBefore(threshold);
    }

    /**
     * Scheduled task to clean up old audit logs based on retention policy
     * Runs daily at 2:00 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredAuditLogs() {
        if (auditRetentionDays <= 0) {
            log.info("Audit log retention is disabled (retention-days={})", auditRetentionDays);
            return;
        }

        LocalDateTime threshold = LocalDateTime.now().minusDays(auditRetentionDays);
        long count = auditLogRepository.countByEventTimeBefore(threshold);

        if (count > 0) {
            log.info("Cleaning up {} audit logs older than {} days (before {})",
                count, auditRetentionDays, threshold);
            auditLogRepository.deleteByEventTimeBefore(threshold);
            log.info("Audit log cleanup completed");
        } else {
            log.debug("No audit logs to clean up (retention-days={})", auditRetentionDays);
        }
    }

    /**
     * Manual trigger for audit log cleanup (admin only)
     */
    @Transactional
    public long manualCleanupExpiredAuditLogs() {
        if (auditRetentionDays <= 0) {
            log.warn("Audit log retention is disabled, manual cleanup skipped");
            return 0;
        }

        LocalDateTime threshold = LocalDateTime.now().minusDays(auditRetentionDays);
        long count = auditLogRepository.countByEventTimeBefore(threshold);

        if (count > 0) {
            log.info("Manual cleanup: removing {} audit logs older than {}", count, threshold);
            auditLogRepository.deleteByEventTimeBefore(threshold);
        }

        return count;
    }

    // ==================== DTOs ====================

    public record SystemSummaryStats(
        long totalDocuments,
        long totalFolders,
        long totalSizeBytes
    ) {
        public String formattedTotalSize() {
            if (totalSizeBytes < 1024) return totalSizeBytes + " B";
            if (totalSizeBytes < 1024 * 1024) return String.format("%.1f KB", totalSizeBytes / 1024.0);
            if (totalSizeBytes < 1024 * 1024 * 1024) return String.format("%.1f MB", totalSizeBytes / (1024.0 * 1024));
            return String.format("%.1f GB", totalSizeBytes / (1024.0 * 1024 * 1024));
        }
    }

    public record MimeTypeStats(
        String mimeType,
        long count,
        long sizeBytes
    ) {}

    public record RuleExecutionSummary(
        int windowDays,
        long executions,
        long failures,
        double successRate,
        long scheduledBatches,
        long scheduledFailures,
        Map<String, Long> countsByType
    ) {}

    public record DailyActivityStats(
        LocalDate date,
        long eventCount
    ) {}

    public record UserActivityStats(
        String username,
        long activityCount
    ) {}

    public record AuditExportResult(
        String csvContent,
        long rowCount
    ) {}

    public record AuditEventTypeCount(
        String eventType,
        long count
    ) {}

    public record AuditReportSummary(
        int windowDays,
        long totalEvents,
        Map<AuditCategory, Long> countsByCategory
    ) {}
}
