package com.ecm.core.service;

import com.ecm.core.entity.Node;
import com.ecm.core.repository.NodeRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for handling bulk operations on nodes.
 * Handles partial successes and reports detailed results.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkOperationService {

    private final NodeService nodeService;
    private final NodeRepository nodeRepository;
    private final TrashService trashService;
    private final AuditService auditService;
    private final SecurityService securityService;

    /**
     * Bulk move nodes to a target folder
     */
    @Transactional
    public BulkOperationResult bulkMove(List<UUID> nodeIds, UUID targetFolderId) {
        return executeBulk("MOVE", nodeIds, (id) -> nodeService.moveNode(id, targetFolderId));
    }

    /**
     * Bulk copy nodes to a target folder
     */
    @Transactional
    public BulkOperationResult bulkCopy(List<UUID> nodeIds, UUID targetFolderId) {
        return executeBulk("COPY", nodeIds, (id) -> nodeService.copyNode(id, targetFolderId, null, true));
    }

    /**
     * Bulk delete nodes (move to trash)
     */
    @Transactional
    public BulkOperationResult bulkDelete(List<UUID> nodeIds) {
        return executeBulk("DELETE", nodeIds, (id) -> {
            trashService.moveToTrash(id);
            return null; // Return value ignored
        });
    }

    /**
     * Bulk restore nodes from trash
     */
    @Transactional
    public BulkOperationResult bulkRestore(List<UUID> nodeIds) {
        return executeBulk("RESTORE", nodeIds, (id) -> {
            trashService.restore(id);
            return null;
        });
    }

    /**
     * Execute generic bulk operation
     */
    private BulkOperationResult executeBulk(String operation, List<UUID> ids, OperationExecutor executor) {
        List<UUID> safeIds = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).toList();
        log.info("Starting bulk {} operation on {} items", operation, safeIds.size());

        List<String> successes = new ArrayList<>();
        Map<String, String> failures = new HashMap<>();
        AtomicInteger processed = new AtomicInteger(0);

        for (UUID id : safeIds) {
            try {
                // Determine name for logging (try to fetch, might fail if not found)
                String name = nodeRepository.findById(id)
                    .map(Node::getName)
                    .orElse("Unknown ID: " + id);

                executor.execute(id);
                successes.add(id.toString());
                log.debug("Bulk {}: Success for {}", operation, name);
                
            } catch (Exception e) {
                log.warn("Bulk {}: Failed for ID {}: {}", operation, id, e.getMessage());
                failures.put(id.toString(), e.getMessage());
            } finally {
                processed.incrementAndGet();
            }
        }

        BulkOperationResult result = BulkOperationResult.builder()
            .operation(operation)
            .totalRequested(safeIds.size())
            .successCount(successes.size())
            .failureCount(failures.size())
            .successfulIds(successes)
            .failures(failures)
            .build();
        auditBulkOperation(operation, result);
        return result;
    }

    private void auditBulkOperation(String operation, BulkOperationResult result) {
        if (operation == null || result == null) {
            return;
        }
        String normalizedOperation = operation.trim().toUpperCase(Locale.ROOT);
        int failureCount = result.getFailureCount();
        int successCount = result.getSuccessCount();
        String eventType = "BULK_" + normalizedOperation + (
            failureCount == 0 ? "_COMPLETED" : (successCount == 0 ? "_FAILED" : "_PARTIAL")
        );
        String details = String.format(
            "Bulk %s requested=%d success=%d failed=%d",
            normalizedOperation,
            result.getTotalRequested(),
            successCount,
            failureCount
        );
        if (failureCount > 0 && result.getFailures() != null && !result.getFailures().isEmpty()) {
            List<String> failedIds = result.getFailures().keySet().stream().limit(5).toList();
            details += ", failedSample=" + String.join("|", failedIds);
        }
        auditService.logEvent(
            eventType,
            null,
            "BULK_OPERATIONS",
            resolveAuditActor(),
            details
        );
    }

    private String resolveAuditActor() {
        try {
            String username = securityService.getCurrentUser();
            if (username != null && !username.isBlank()) {
                return username;
            }
        } catch (Exception ex) {
            log.debug("Failed to resolve audit actor for bulk operation: {}", ex.getMessage());
        }
        return "system";
    }

    @FunctionalInterface
    interface OperationExecutor {
        Object execute(UUID id) throws Exception;
    }

    @Data
    @Builder
    public static class BulkOperationResult {
        private String operation;
        private int totalRequested;
        private int successCount;
        private int failureCount;
        private List<String> successfulIds;
        private Map<String, String> failures;
    }
}
