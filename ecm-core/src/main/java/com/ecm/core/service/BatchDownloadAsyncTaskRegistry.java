package com.ecm.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

@Slf4j
@Service
public class BatchDownloadAsyncTaskRegistry {

    private static final int MAX_ASYNC_TASKS = 100;
    private static final Duration TERMINAL_TASK_RETENTION = Duration.ofHours(24);
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private final Map<String, BatchDownloadAsyncTask> asyncTasks = new ConcurrentHashMap<>();
    private final Deque<String> asyncTaskOrder = new ArrayDeque<>();
    private final Object asyncTaskLock = new Object();

    public void register(BatchDownloadAsyncTask task) {
        synchronized (asyncTaskLock) {
            asyncTasks.put(task.taskId(), task);
            asyncTaskOrder.remove(task.taskId());
            asyncTaskOrder.addLast(task.taskId());
            trimTerminalTasksLocked();
        }
    }

    public BatchDownloadAsyncTask get(String taskId) {
        return asyncTasks.get(taskId);
    }

    public BatchDownloadAsyncTask update(String taskId, UnaryOperator<BatchDownloadAsyncTask> updater) {
        BatchDownloadAsyncTask updated = asyncTasks.computeIfPresent(taskId, (key, current) -> updater.apply(current));
        if (updated != null && updated.status().isTerminal()) {
            synchronized (asyncTaskLock) {
                asyncTaskOrder.remove(taskId);
                asyncTaskOrder.addLast(taskId);
            }
        }
        return updated;
    }

    public BatchDownloadSnapshot snapshot(
        int limit,
        int skipCount,
        BatchDownloadAsyncStatus statusFilter,
        String query,
        String ownerFilter
    ) {
        int boundedLimit = Math.max(1, limit);
        int boundedSkipCount = Math.max(0, skipCount);
        String normalizedQuery = normalizeQuery(query);
        String normalizedOwner = normalizeQuery(ownerFilter);
        List<BatchDownloadAsyncTask> items = new ArrayList<>();
        int filteredCount = 0;
        synchronized (asyncTaskLock) {
            Iterator<String> iterator = asyncTaskOrder.descendingIterator();
            while (iterator.hasNext()) {
                BatchDownloadAsyncTask task = asyncTasks.get(iterator.next());
                if (task == null) {
                    continue;
                }
                if (statusFilter != null && task.status() != statusFilter) {
                    continue;
                }
                if (!matchesOwner(task, normalizedOwner)) {
                    continue;
                }
                if (!matchesQuery(task, normalizedQuery)) {
                    continue;
                }
                if (filteredCount++ < boundedSkipCount) {
                    continue;
                }
                if (items.size() < boundedLimit) {
                    items.add(task);
                }
            }
        }

        long activeCount = asyncTasks.values().stream()
            .filter(task -> task.status().isActive())
            .count();

        return new BatchDownloadSnapshot(
            items,
            asyncTasks.size(),
            filteredCount,
            activeCount,
            boundedSkipCount,
            boundedLimit
        );
    }

    public BatchDownloadSnapshot snapshot(int limit, int skipCount, BatchDownloadAsyncStatus statusFilter, String query) {
        return snapshot(limit, skipCount, statusFilter, query, null);
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String normalized = query.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean matchesQuery(BatchDownloadAsyncTask task, String normalizedQuery) {
        if (normalizedQuery == null) {
            return true;
        }
        return containsIgnoreCase(task.taskId(), normalizedQuery)
            || containsIgnoreCase(task.name(), normalizedQuery)
            || containsIgnoreCase(task.filename(), normalizedQuery)
            || containsIgnoreCase(task.status().name(), normalizedQuery)
            || containsIgnoreCase(task.createdBy(), normalizedQuery);
    }

    private boolean matchesOwner(BatchDownloadAsyncTask task, String normalizedOwner) {
        if (normalizedOwner == null) {
            return true;
        }
        return containsIgnoreCase(task.createdBy(), normalizedOwner);
    }

    private boolean containsIgnoreCase(String value, String normalizedQuery) {
        return value != null && value.toLowerCase().contains(normalizedQuery);
    }

    public BatchDownloadAsyncSummary summary() {
        return summary(null);
    }

    public BatchDownloadAsyncSummary summary(BatchDownloadAsyncStatus statusFilter) {
        long queuedCount = 0L;
        long runningCount = 0L;
        long cancelRequestedCount = 0L;
        long cancelledCount = 0L;
        long completedCount = 0L;
        long failedCount = 0L;

        for (BatchDownloadAsyncTask task : asyncTasks.values()) {
            if (task == null) {
                continue;
            }
            if (statusFilter != null && task.status() != statusFilter) {
                continue;
            }
            switch (task.status()) {
                case QUEUED -> queuedCount += 1;
                case RUNNING -> runningCount += 1;
                case CANCEL_REQUESTED -> cancelRequestedCount += 1;
                case CANCELLED -> cancelledCount += 1;
                case COMPLETED -> completedCount += 1;
                case FAILED -> failedCount += 1;
            }
        }

        long totalCount = queuedCount + runningCount + cancelRequestedCount + cancelledCount + completedCount + failedCount;
        long activeCount = queuedCount + runningCount + cancelRequestedCount;
        long terminalCount = cancelledCount + completedCount + failedCount;

        return new BatchDownloadAsyncSummary(
            (int) totalCount,
            activeCount,
            terminalCount,
            queuedCount,
            runningCount,
            cancelRequestedCount,
            cancelledCount,
            completedCount,
            failedCount
        );
    }

    public int cleanupTerminalTasks(BatchDownloadAsyncStatus statusFilter) {
        return cleanupTasks(task -> task.status().isTerminal()
            && (statusFilter == null || task.status() == statusFilter));
    }

    public int cleanupTask(String taskId) {
        return cleanupTasks(task -> task.taskId().equals(taskId) && task.status().isTerminal());
    }

    @Scheduled(fixedDelayString = "${ecm.batch.download.async.cleanup-interval-ms:3600000}")
    public void cleanupExpiredTerminalTasks() {
        cleanupExpiredTerminalTasks(Instant.now());
    }

    public int cleanupExpiredTerminalTasks(Instant now) {
        LocalDateTime threshold = LocalDateTime.ofInstant(now, SYSTEM_ZONE).minus(TERMINAL_TASK_RETENTION);
        int removedCount = cleanupTasks(task -> task.status().isTerminal() && task.isExpired(threshold));
        if (removedCount > 0) {
            log.info("Cleaned up {} expired batch download tasks", removedCount);
        }
        return removedCount;
    }

    public int size() {
        return asyncTasks.size();
    }

    public long activeCount() {
        return asyncTasks.values().stream()
            .filter(task -> task.status().isActive())
            .count();
    }

    public int cancelActiveTasks(BatchDownloadAsyncStatus statusFilter) {
        List<String> taskIds = getTaskIds(task ->
            task.status().isActive()
                && (statusFilter == null || task.status() == statusFilter)
        );

        int affectedCount = 0;
        LocalDateTime now = LocalDateTime.now();
        for (String taskId : taskIds) {
            BatchDownloadAsyncTask current = asyncTasks.get(taskId);
            if (current == null || !current.status().isActive()) {
                continue;
            }
            if (statusFilter != null && current.status() != statusFilter) {
                continue;
            }

            BatchDownloadAsyncTask updated = update(taskId, task -> switch (task.status()) {
                case QUEUED -> task.cancelled("Cancelled by admin", now);
                case RUNNING -> task.cancelRequested("Cancellation requested by admin");
                case CANCEL_REQUESTED -> task;
                default -> task;
            });

            if (updated != null && updated.status() != current.status()) {
                affectedCount += 1;
                if (updated.status() == BatchDownloadAsyncStatus.CANCELLED) {
                    deleteArchiveIfPresent(updated.archivePath());
                }
            }
        }
        return affectedCount;
    }

    public void deleteArchiveIfPresent(Path archivePath) {
        if (archivePath == null) {
            return;
        }
        try {
            Files.deleteIfExists(archivePath);
        } catch (Exception ex) {
            log.warn("Failed to delete batch download artifact {}", archivePath, ex);
        }
    }

    private int cleanupTasks(Predicate<BatchDownloadAsyncTask> predicate) {
        int removedCount = 0;
        synchronized (asyncTaskLock) {
            Iterator<String> iterator = asyncTaskOrder.iterator();
            while (iterator.hasNext()) {
                String taskId = iterator.next();
                BatchDownloadAsyncTask task = asyncTasks.get(taskId);
                if (task == null) {
                    iterator.remove();
                    continue;
                }
                if (!predicate.test(task)) {
                    continue;
                }
                deleteArchiveIfPresent(task.archivePath());
                if (asyncTasks.remove(taskId, task)) {
                    iterator.remove();
                    removedCount += 1;
                }
            }
        }
        return removedCount;
    }

    private List<String> getTaskIds(Predicate<BatchDownloadAsyncTask> predicate) {
        List<String> taskIds = new ArrayList<>();
        synchronized (asyncTaskLock) {
            Iterator<String> iterator = asyncTaskOrder.iterator();
            while (iterator.hasNext()) {
                String taskId = iterator.next();
                BatchDownloadAsyncTask task = asyncTasks.get(taskId);
                if (task != null && predicate.test(task)) {
                    taskIds.add(taskId);
                }
            }
        }
        return taskIds;
    }

    public record BatchDownloadAsyncSummary(
        int totalCount,
        long activeCount,
        long terminalCount,
        long queuedCount,
        long runningCount,
        long cancelRequestedCount,
        long cancelledCount,
        long completedCount,
        long failedCount
    ) {}

    private long countByStatus(BatchDownloadAsyncStatus status) {
        return asyncTasks.values().stream()
            .filter(task -> task.status() == status)
            .count();
    }

    private void trimTerminalTasksLocked() {
        if (asyncTasks.size() <= MAX_ASYNC_TASKS) {
            return;
        }

        Iterator<String> iterator = asyncTaskOrder.iterator();
        while (asyncTasks.size() > MAX_ASYNC_TASKS && iterator.hasNext()) {
            String taskId = iterator.next();
            BatchDownloadAsyncTask candidate = asyncTasks.get(taskId);
            if (candidate == null) {
                iterator.remove();
                continue;
            }
            if (candidate.status().isActive()) {
                continue;
            }
            deleteArchiveIfPresent(candidate.archivePath());
            if (asyncTasks.remove(taskId, candidate)) {
                iterator.remove();
            }
        }
    }

    public record BatchDownloadSnapshot(
        List<BatchDownloadAsyncTask> items,
        int totalCount,
        int filteredCount,
        long activeCount,
        int skipCount,
        int maxItems
    ) {}

    public enum BatchDownloadAsyncStatus {
        QUEUED,
        RUNNING,
        CANCEL_REQUESTED,
        CANCELLED,
        COMPLETED,
        FAILED;

        public boolean isActive() {
            return this == QUEUED || this == RUNNING || this == CANCEL_REQUESTED;
        }

        public boolean isTerminal() {
            return !isActive();
        }
    }

    public record BatchDownloadAsyncTask(
        String taskId,
        List<java.util.UUID> nodeIds,
        String name,
        String createdBy,
        String filename,
        BatchDownloadAsyncStatus status,
        int totalFiles,
        int filesAdded,
        long totalBytes,
        long bytesAdded,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String errorMessage,
        Path archivePath
    ) {
        public BatchDownloadAsyncTask started(LocalDateTime startedAt) {
            return new BatchDownloadAsyncTask(
                taskId,
                nodeIds,
                name,
                createdBy,
                filename,
                BatchDownloadAsyncStatus.RUNNING,
                totalFiles,
                filesAdded,
                totalBytes,
                bytesAdded,
                createdAt,
                startedAt,
                completedAt,
                errorMessage,
                archivePath
            );
        }

        public BatchDownloadAsyncTask progress(int filesAdded, long bytesAdded) {
            return new BatchDownloadAsyncTask(
                taskId,
                nodeIds,
                name,
                createdBy,
                filename,
                status,
                totalFiles,
                filesAdded,
                totalBytes,
                bytesAdded,
                createdAt,
                startedAt,
                completedAt,
                errorMessage,
                archivePath
            );
        }

        public BatchDownloadAsyncTask completed(int filesAdded, long bytesAdded, Path archivePath, LocalDateTime completedAt) {
            return new BatchDownloadAsyncTask(
                taskId,
                nodeIds,
                name,
                createdBy,
                filename,
                BatchDownloadAsyncStatus.COMPLETED,
                totalFiles,
                filesAdded,
                totalBytes,
                bytesAdded,
                createdAt,
                startedAt,
                completedAt,
                null,
                archivePath
            );
        }

        public BatchDownloadAsyncTask cancelRequested(String reason) {
            return new BatchDownloadAsyncTask(
                taskId,
                nodeIds,
                name,
                createdBy,
                filename,
                BatchDownloadAsyncStatus.CANCEL_REQUESTED,
                totalFiles,
                filesAdded,
                totalBytes,
                bytesAdded,
                createdAt,
                startedAt,
                completedAt,
                reason,
                archivePath
            );
        }

        public BatchDownloadAsyncTask cancelled(String reason, LocalDateTime completedAt) {
            return new BatchDownloadAsyncTask(
                taskId,
                nodeIds,
                name,
                createdBy,
                filename,
                BatchDownloadAsyncStatus.CANCELLED,
                totalFiles,
                filesAdded,
                totalBytes,
                bytesAdded,
                createdAt,
                startedAt,
                completedAt,
                reason,
                null
            );
        }

        public BatchDownloadAsyncTask failed(String reason, LocalDateTime completedAt) {
            return new BatchDownloadAsyncTask(
                taskId,
                nodeIds,
                name,
                createdBy,
                filename,
                BatchDownloadAsyncStatus.FAILED,
                totalFiles,
                filesAdded,
                totalBytes,
                bytesAdded,
                createdAt,
                startedAt,
                completedAt,
                reason,
                null
            );
        }

        public boolean cleanupEligible() {
            return status.isTerminal();
        }

        public LocalDateTime retentionExpiresAt() {
            if (!status.isTerminal()) {
                return null;
            }
            LocalDateTime terminalAt = completedAt != null ? completedAt : createdAt;
            return terminalAt != null ? terminalAt.plus(TERMINAL_TASK_RETENTION) : null;
        }

        public boolean artifactPresent() {
            return archivePath != null && Files.exists(archivePath);
        }

        public Long archiveSizeBytes() {
            if (archivePath == null || !Files.exists(archivePath)) {
                return null;
            }
            try {
                return Files.size(archivePath);
            } catch (Exception ex) {
                log.warn("Failed to inspect batch download artifact size {}", archivePath, ex);
                return null;
            }
        }

        public boolean isExpired(LocalDateTime threshold) {
            if (status.isActive()) {
                return false;
            }
            LocalDateTime terminalAt = completedAt != null ? completedAt : createdAt;
            return terminalAt != null && terminalAt.isBefore(threshold);
        }
    }
}
