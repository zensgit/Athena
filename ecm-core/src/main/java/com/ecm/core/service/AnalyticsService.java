package com.ecm.core.service;

import com.ecm.core.entity.AuditLog;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.repository.AuditLogRepository;
import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    public record DailyActivityStats(
        LocalDate date,
        long eventCount
    ) {}

    public record UserActivityStats(
        String username,
        long activityCount
    ) {}
}
