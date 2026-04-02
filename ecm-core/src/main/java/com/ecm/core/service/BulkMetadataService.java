package com.ecm.core.service;

import com.ecm.core.entity.Node;
import com.ecm.core.event.NodeUpdatedEvent;
import com.ecm.core.repository.NodeRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkMetadataService {

    private final TagService tagService;
    private final CategoryService categoryService;
    private final NodeService nodeService;
    private final NodeRepository nodeRepository;
    private final SecurityService securityService;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;

    @Transactional
    public BulkMetadataResult applyMetadata(BulkMetadataRequest request) {
        BulkMetadataRequest safeRequest = request != null
            ? request
            : new BulkMetadataRequest(List.of(), List.of(), List.of(), null, false);
        List<UUID> ids = safeRequest.ids() != null ? safeRequest.ids() : Collections.emptyList();
        List<String> tagNames = normalizeTags(safeRequest.tagNames());
        List<String> categoryIds = normalizeCategoryIds(safeRequest.categoryIds());

        if (ids.isEmpty()) {
            BulkMetadataResult emptyResult = BulkMetadataResult.builder()
                .operation("METADATA_UPDATE")
                .totalRequested(0)
                .successCount(0)
                .failureCount(0)
                .successfulIds(List.of())
                .failures(Map.of())
                .build();
            auditBulkMetadataUpdate(safeRequest, emptyResult);
            return emptyResult;
        }

        List<String> successes = new ArrayList<>();
        Map<String, String> failures = new HashMap<>();

        for (UUID id : ids) {
            try {
                applyToNode(id, tagNames, categoryIds, safeRequest);
                successes.add(id.toString());
            } catch (Exception e) {
                log.warn("Bulk metadata update failed for {}: {}", id, e.getMessage());
                failures.put(id.toString(), e.getMessage());
            }
        }

        BulkMetadataResult result = BulkMetadataResult.builder()
            .operation("METADATA_UPDATE")
            .totalRequested(ids.size())
            .successCount(successes.size())
            .failureCount(failures.size())
            .successfulIds(successes)
            .failures(failures)
            .build();
        auditBulkMetadataUpdate(safeRequest, result);
        return result;
    }

    private void applyToNode(UUID nodeId, List<String> tagNames, List<String> categoryIds, BulkMetadataRequest request) {
        boolean changed = false;

        if (!tagNames.isEmpty()) {
            tagService.addTagsToNode(nodeId.toString(), tagNames);
            changed = true;
        }

        if (!categoryIds.isEmpty()) {
            categoryService.addCategoriesToNode(nodeId.toString(), categoryIds);
            changed = true;
        }

        if (request.correspondentId() != null || request.clearCorrespondent()) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("correspondentId", request.correspondentId());
            nodeService.updateNode(nodeId, updates);
            changed = true;
        }

        if (changed && request.correspondentId() == null && !request.clearCorrespondent()) {
            Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeId));
            eventPublisher.publishEvent(new NodeUpdatedEvent(node, securityService.getCurrentUser()));
        }
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
            .map(String::trim)
            .filter(tag -> !tag.isBlank())
            .toList();
    }

    private List<String> normalizeCategoryIds(List<String> categories) {
        if (categories == null) {
            return List.of();
        }
        return categories.stream()
            .map(String::trim)
            .filter(id -> !id.isBlank())
            .toList();
    }

    private void auditBulkMetadataUpdate(BulkMetadataRequest request, BulkMetadataResult result) {
        if (request == null || result == null) {
            return;
        }
        int failures = result.getFailureCount();
        int successes = result.getSuccessCount();
        String eventType = "BULK_METADATA_UPDATE" + (
            failures == 0 ? "_COMPLETED" : (successes == 0 ? "_FAILED" : "_PARTIAL")
        );
        String actor = resolveAuditActor();
        int tagCount = request.tagNames() != null ? request.tagNames().size() : 0;
        int categoryCount = request.categoryIds() != null ? request.categoryIds().size() : 0;
        String details = String.format(
            Locale.ROOT,
            "Bulk metadata update requested=%d success=%d failed=%d tags=%d categories=%d correspondentId=%s clearCorrespondent=%s",
            result.getTotalRequested(),
            successes,
            failures,
            tagCount,
            categoryCount,
            request.correspondentId(),
            request.clearCorrespondent()
        );
        if (failures > 0 && result.getFailures() != null && !result.getFailures().isEmpty()) {
            List<String> failedSample = result.getFailures().keySet().stream().limit(5).toList();
            details += ", failedSample=" + String.join("|", failedSample);
        }
        auditService.logEvent(eventType, null, "BULK_METADATA", actor, details);
    }

    private String resolveAuditActor() {
        try {
            String username = securityService.getCurrentUser();
            if (username != null && !username.isBlank()) {
                return username;
            }
        } catch (Exception ex) {
            log.debug("Failed to resolve bulk metadata actor: {}", ex.getMessage());
        }
        return "system";
    }

    public record BulkMetadataRequest(
        List<UUID> ids,
        List<String> tagNames,
        List<String> categoryIds,
        UUID correspondentId,
        boolean clearCorrespondent
    ) {}

    @Data
    @Builder
    public static class BulkMetadataResult {
        private String operation;
        private int totalRequested;
        private int successCount;
        private int failureCount;
        private List<String> successfulIds;
        private Map<String, String> failures;
    }
}
