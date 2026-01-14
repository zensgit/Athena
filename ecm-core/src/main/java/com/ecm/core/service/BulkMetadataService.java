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

    @Transactional
    public BulkMetadataResult applyMetadata(BulkMetadataRequest request) {
        List<UUID> ids = request.ids() != null ? request.ids() : Collections.emptyList();
        List<String> tagNames = normalizeTags(request.tagNames());
        List<String> categoryIds = normalizeCategoryIds(request.categoryIds());

        if (ids.isEmpty()) {
            return BulkMetadataResult.builder()
                .operation("METADATA_UPDATE")
                .totalRequested(0)
                .successCount(0)
                .failureCount(0)
                .successfulIds(List.of())
                .failures(Map.of())
                .build();
        }

        List<String> successes = new ArrayList<>();
        Map<String, String> failures = new HashMap<>();

        for (UUID id : ids) {
            try {
                applyToNode(id, tagNames, categoryIds, request);
                successes.add(id.toString());
            } catch (Exception e) {
                log.warn("Bulk metadata update failed for {}: {}", id, e.getMessage());
                failures.put(id.toString(), e.getMessage());
            }
        }

        return BulkMetadataResult.builder()
            .operation("METADATA_UPDATE")
            .totalRequested(ids.size())
            .successCount(successes.size())
            .failureCount(failures.size())
            .successfulIds(successes)
            .failures(failures)
            .build();
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
