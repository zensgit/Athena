package com.ecm.core.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuditExportAsyncTaskRegistry {

    private static final int MAX_ASYNC_TASKS = 200;

    private final Map<String, AuditExportAsyncTask> asyncTasks = new ConcurrentHashMap<>();
    private final Deque<String> asyncTaskOrder = new ArrayDeque<>();
    private final Object asyncTaskLock = new Object();

    public AuditExportAsyncTask createTask() {
        String taskId = UUID.randomUUID().toString();
        AuditExportAsyncTask task = new AuditExportAsyncTask(
            taskId,
            LocalDateTime.now(),
            AuditExportAsyncStatus.QUEUED,
            null,
            null,
            null,
            null,
            null
        );

        synchronized (asyncTaskLock) {
            asyncTasks.put(taskId, task);
            asyncTaskOrder.addLast(taskId);
            trimTerminalTasksLocked();
        }
        return task;
    }

    public AuditExportAsyncTask get(String taskId) {
        return asyncTasks.get(taskId);
    }

    public AuditExportAsyncTask markRunning(String taskId) {
        return asyncTasks.computeIfPresent(taskId, (key, current) ->
            current.status() == AuditExportAsyncStatus.QUEUED ? current.running() : current
        );
    }

    public AuditExportAsyncTask complete(String taskId, String filename, byte[] payload, long rowCount) {
        return asyncTasks.computeIfPresent(taskId, (key, current) ->
            current.status() == AuditExportAsyncStatus.CANCELLED ? current : current.complete(filename, payload, rowCount)
        );
    }

    public AuditExportAsyncTask fail(String taskId, String error) {
        return asyncTasks.computeIfPresent(taskId, (key, current) ->
            current.status() == AuditExportAsyncStatus.CANCELLED ? current : current.fail(error)
        );
    }

    public AuditExportAsyncTask cancel(String taskId, String reason) {
        return asyncTasks.computeIfPresent(taskId, (key, current) ->
            current.isTerminal() ? current : current.cancel(reason)
        );
    }

    public List<AuditExportAsyncTask> list(int limit, AuditExportAsyncStatus statusFilter) {
        List<AuditExportAsyncTask> items = new ArrayList<>();
        synchronized (asyncTaskLock) {
            Iterator<String> iterator = asyncTaskOrder.descendingIterator();
            while (iterator.hasNext() && items.size() < limit) {
                AuditExportAsyncTask task = asyncTasks.get(iterator.next());
                if (task == null) {
                    continue;
                }
                if (statusFilter != null && task.status() != statusFilter) {
                    continue;
                }
                items.add(task);
            }
        }
        return items;
    }

    public AuditExportAsyncSummary summary(AuditExportAsyncStatus statusFilter) {
        long queuedCount = 0L;
        long runningCount = 0L;
        long completedCount = 0L;
        long cancelledCount = 0L;
        long failedCount = 0L;

        synchronized (asyncTaskLock) {
            for (AuditExportAsyncTask task : asyncTasks.values()) {
                if (task == null) {
                    continue;
                }
                if (statusFilter != null && task.status() != statusFilter) {
                    continue;
                }
                switch (task.status()) {
                    case QUEUED -> queuedCount += 1;
                    case RUNNING -> runningCount += 1;
                    case COMPLETED -> completedCount += 1;
                    case CANCELLED -> cancelledCount += 1;
                    case FAILED -> failedCount += 1;
                }
            }
        }

        long totalCount = queuedCount + runningCount + completedCount + cancelledCount + failedCount;
        long activeCount = queuedCount + runningCount;
        long terminalCount = completedCount + cancelledCount + failedCount;
        return new AuditExportAsyncSummary(
            totalCount,
            queuedCount,
            runningCount,
            completedCount,
            cancelledCount,
            failedCount,
            activeCount,
            terminalCount
        );
    }

    public long cancelActive(AuditExportAsyncStatus statusFilter, String reason) {
        long cancelledCount = 0L;
        synchronized (asyncTaskLock) {
            for (Map.Entry<String, AuditExportAsyncTask> entry : asyncTasks.entrySet()) {
                AuditExportAsyncTask task = entry.getValue();
                if (task == null || task.isTerminal()) {
                    continue;
                }
                if (statusFilter != null && task.status() != statusFilter) {
                    continue;
                }

                AuditExportAsyncTask cancelled = task.cancel(reason);
                if (asyncTasks.replace(entry.getKey(), task, cancelled)) {
                    cancelledCount += 1;
                }
            }
        }
        return cancelledCount;
    }

    public long activeCount() {
        long activeCount = 0L;
        synchronized (asyncTaskLock) {
            for (AuditExportAsyncTask task : asyncTasks.values()) {
                if (task == null || task.isTerminal()) {
                    continue;
                }
                activeCount += 1;
            }
        }
        return activeCount;
    }

    public long cleanupTerminal(AuditExportAsyncStatus statusFilter) {
        Set<String> taskIdsToDelete = new java.util.HashSet<>();
        synchronized (asyncTaskLock) {
            for (Map.Entry<String, AuditExportAsyncTask> entry : asyncTasks.entrySet()) {
                AuditExportAsyncTask task = entry.getValue();
                if (task == null) {
                    taskIdsToDelete.add(entry.getKey());
                    continue;
                }
                if (statusFilter == null) {
                    if (task.isTerminal()) {
                        taskIdsToDelete.add(entry.getKey());
                    }
                } else if (task.status() == statusFilter) {
                    taskIdsToDelete.add(entry.getKey());
                }
            }

            if (taskIdsToDelete.isEmpty()) {
                asyncTaskOrder.removeIf(taskId -> !asyncTasks.containsKey(taskId));
                return 0L;
            }

            taskIdsToDelete.forEach(asyncTasks::remove);
            asyncTaskOrder.removeIf(taskId ->
                taskIdsToDelete.contains(taskId) || !asyncTasks.containsKey(taskId)
            );
            return taskIdsToDelete.size();
        }
    }

    public int size() {
        return asyncTasks.size();
    }

    private void trimTerminalTasksLocked() {
        if (asyncTasks.size() <= MAX_ASYNC_TASKS) {
            return;
        }
        Iterator<String> iterator = asyncTaskOrder.iterator();
        while (asyncTasks.size() > MAX_ASYNC_TASKS && iterator.hasNext()) {
            String candidateTaskId = iterator.next();
            AuditExportAsyncTask candidate = asyncTasks.get(candidateTaskId);
            if (candidate == null) {
                iterator.remove();
                continue;
            }
            if (!candidate.isTerminal()) {
                continue;
            }
            if (asyncTasks.remove(candidateTaskId, candidate)) {
                iterator.remove();
            }
        }
    }

    public enum AuditExportAsyncStatus {
        QUEUED,
        RUNNING,
        COMPLETED,
        CANCELLED,
        FAILED;

        public boolean isTerminal() {
            return this == COMPLETED || this == CANCELLED || this == FAILED;
        }
    }

    public record AuditExportAsyncSummary(
        long totalCount,
        long queuedCount,
        long runningCount,
        long completedCount,
        long cancelledCount,
        long failedCount,
        long activeCount,
        long terminalCount
    ) {}

    public record AuditExportAsyncTask(
        String taskId,
        LocalDateTime createdAt,
        AuditExportAsyncStatus status,
        String error,
        LocalDateTime finishedAt,
        String filename,
        byte[] csvContent,
        Long rowCount
    ) {
        public AuditExportAsyncTask running() {
            return new AuditExportAsyncTask(
                taskId,
                createdAt,
                AuditExportAsyncStatus.RUNNING,
                null,
                null,
                null,
                null,
                null
            );
        }

        public AuditExportAsyncTask complete(String completedFilename, byte[] payload, long completedCount) {
            return new AuditExportAsyncTask(
                taskId,
                createdAt,
                AuditExportAsyncStatus.COMPLETED,
                null,
                LocalDateTime.now(),
                completedFilename,
                payload != null ? payload.clone() : null,
                completedCount
            );
        }

        public AuditExportAsyncTask fail(String errorMessage) {
            return new AuditExportAsyncTask(
                taskId,
                createdAt,
                AuditExportAsyncStatus.FAILED,
                errorMessage,
                LocalDateTime.now(),
                null,
                null,
                null
            );
        }

        public AuditExportAsyncTask cancel(String reason) {
            return new AuditExportAsyncTask(
                taskId,
                createdAt,
                AuditExportAsyncStatus.CANCELLED,
                reason,
                LocalDateTime.now(),
                null,
                null,
                null
            );
        }

        public boolean isTerminal() {
            return status != null && status.isTerminal();
        }
    }
}
