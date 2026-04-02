package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.RenditionResource;
import com.ecm.core.preview.PreviewFailureClassifier;
import com.ecm.core.preview.PreviewQueueService;
import com.ecm.core.preview.PreviewService;
import com.ecm.core.preview.PreviewStatusSemantics;
import com.ecm.core.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class RenditionResourceService {

    private final NodeService nodeService;
    private final DocumentRepository documentRepository;
    private final RenditionResourceSyncService renditionResourceSyncService;
    private final RenditionDefinitionRegistry renditionDefinitionRegistry;
    private final PreviewService previewService;
    private final PreviewQueueService previewQueueService;

    @Transactional
    public List<RenditionResource> listForNode(UUID nodeId) {
        return listForNode(nodeId, null);
    }

    @Transactional
    public List<RenditionResource> listForNode(UUID nodeId, String statusFilter) {
        return listForNode(nodeId, statusFilter, null);
    }

    @Transactional
    public List<RenditionResource> listForNode(UUID nodeId, String statusFilter, String stateFilter) {
        Node node = nodeService.getNode(nodeId);
        if (!(node instanceof Document document)) {
            return List.of();
        }
        return listForDocument(document, statusFilter, stateFilter);
    }

    @Transactional
    public List<RenditionResource> listForDocument(Document document) {
        return listForDocument(document, null);
    }

    @Transactional
    public List<RenditionResource> listForDocument(Document document, String statusFilter) {
        return listForDocument(document, statusFilter, null);
    }

    @Transactional
    public List<RenditionResource> listForDocument(Document document, String statusFilter, String stateFilter) {
        if (document == null || document.getId() == null) {
            return List.of();
        }
        RenditionCollectionFilter filter = RenditionCollectionFilter.from(statusFilter, stateFilter);
        return renditionResourceSyncService.syncDocument(document).stream()
            .filter(resource -> filter.matches(resource))
            .toList();
    }

    @Transactional
    public List<RenditionDefinitionStatus> listDefinitionsForNode(UUID nodeId) {
        Node node = nodeService.getNode(nodeId);
        if (!(node instanceof Document document)) {
            return List.of();
        }
        return listDefinitionsForDocument(document);
    }

    @Transactional
    public List<RenditionDefinitionStatus> listDefinitionsForDocument(Document document) {
        if (document == null || document.getId() == null) {
            return List.of();
        }
        List<RenditionResource> resources = listForDocument(document);
        java.util.Map<String, RenditionResource> resourcesByKey = resources.stream()
            .collect(java.util.stream.Collectors.toMap(
                RenditionResource::getRenditionKey,
                resource -> resource,
                (left, right) -> left
            ));
        return renditionDefinitionRegistry.evaluate(document).stream()
            .map(definition -> {
                RenditionResource resource = resourcesByKey.get(definition.renditionKey());
                return new RenditionDefinitionStatus(
                    document.getId(),
                    definition.renditionKey(),
                    definition.label(),
                    definition.targetMimeType(),
                    definition.generationMode(),
                    definition.downloadable(),
                    definition.sortOrder(),
                    definition.dependencyRenditionKey(),
                    definition.registered(),
                    definition.applicable(),
                    definition.applicabilityReason(),
                    resource != null && resource.getState() != null ? resource.getState().name() : null,
                    resource != null && resource.isAvailable(),
                    resource != null ? resource.getContentUrl() : null,
                    canMutateDefinition(definition),
                    canMutateDefinition(definition),
                    resolveMutationBlockedReason(definition)
                );
            })
            .toList();
    }

    @Transactional
    public RenditionSummary summarizeDocument(Document document) {
        if (document == null || document.getId() == null) {
            return RenditionSummary.empty(null);
        }
        RenditionResource previewResource = listForDocument(document).stream()
            .filter(resource -> RenditionResourceSyncService.PREVIEW_KEY.equals(resource.getRenditionKey()))
            .findFirst()
            .orElse(null);
        if (previewResource == null) {
            return RenditionSummary.empty(document.getId());
        }
        return new RenditionSummary(
            document.getId(),
            true,
            firstNonBlank(
                previewResource.getSourceStatus(),
                previewResource.getState() != null ? previewResource.getState().name() : null
            ),
            previewResource.isAvailable(),
            previewResource.getErrorReason(),
            previewResource.getErrorCategory(),
            previewResource.getSourceUpdatedAt(),
            previewResource.getVersionLabel()
        );
    }

    @Transactional
    public RenditionSummary resolvePreviewMutationSummary(
        Document document,
        PreviewQueueService.PreviewQueueStatus queueStatus
    ) {
        RenditionSummary summary = summarizeDocument(document);
        if (summary != null && summary.document()) {
            return summary;
        }
        UUID nodeId = document != null ? document.getId() : (queueStatus != null ? queueStatus.documentId() : null);
        if (nodeId == null) {
            return RenditionSummary.empty(null);
        }
        return new RenditionSummary(
            nodeId,
            true,
            queueStatus != null && queueStatus.previewStatus() != null ? queueStatus.previewStatus().name() : null,
            false,
            queueStatus != null ? queueStatus.previewFailureReason() : null,
            queueStatus != null ? queueStatus.previewFailureCategory() : null,
            queueStatus != null ? queueStatus.previewLastUpdated() : null,
            resolveCurrentVersionLabel(document)
        );
    }

    @Transactional(readOnly = true)
    public PreviewMutationStatus resolvePreviewMutationStatus(
        Document document,
        PreviewQueueService.PreviewQueueStatus queueStatus
    ) {
        return resolvePreviewMutationStatus(resolvePreviewMutationSummary(document, queueStatus), queueStatus);
    }

    @Transactional(readOnly = true)
    public PreviewMutationStatus resolvePreviewMutationStatus(
        RenditionSummary previewSummary,
        PreviewQueueService.PreviewQueueStatus queueStatus
    ) {
        UUID documentId = previewSummary != null && previewSummary.nodeId() != null
            ? previewSummary.nodeId()
            : (queueStatus != null ? queueStatus.documentId() : null);
        return new PreviewMutationStatus(
            documentId,
            firstNonBlank(
                previewSummary != null ? previewSummary.previewStatus() : null,
                queueStatus != null && queueStatus.previewStatus() != null ? queueStatus.previewStatus().name() : null
            ),
            firstNonBlank(
                previewSummary != null ? previewSummary.previewFailureReason() : null,
                queueStatus != null ? queueStatus.previewFailureReason() : null
            ),
            firstNonBlank(
                previewSummary != null ? previewSummary.previewFailureCategory() : null,
                queueStatus != null ? queueStatus.previewFailureCategory() : null
            ),
            previewSummary != null && previewSummary.previewLastUpdated() != null
                ? previewSummary.previewLastUpdated()
                : (queueStatus != null ? queueStatus.previewLastUpdated() : null),
            queueStatus != null && queueStatus.queued(),
            queueStatus != null ? queueStatus.attempts() : 0,
            queueStatus != null ? queueStatus.nextAttemptAt() : null,
            queueStatus != null ? queueStatus.message() : null
        );
    }

    @Transactional(readOnly = true)
    public EffectivePreviewSnapshot resolveEffectivePreviewSnapshot(
        Document document,
        String fallbackPreviewStatus,
        String fallbackFailureReason,
        String fallbackFailureCategory,
        LocalDateTime fallbackPreviewLastUpdated
    ) {
        RenditionSummary summary = document != null ? summarizeDocument(document) : RenditionSummary.empty(null);
        if (summary != null && summary.document()) {
            return new EffectivePreviewSnapshot(
                normalizeUpperOrNull(summary.previewStatus()),
                normalizeReasonOrNull(summary.previewFailureReason()),
                normalizeUpperOrNull(summary.previewFailureCategory()),
                summary.previewLastUpdated()
            );
        }
        if (document != null) {
            String status = normalizeUpperOrNull(PreviewStatusSemantics.resolveEffectiveStatus(document));
            String failureReason = normalizeReasonOrNull(PreviewStatusSemantics.resolveEffectiveFailureReason(document));
            return new EffectivePreviewSnapshot(
                status,
                failureReason,
                normalizeUpperOrNull(PreviewFailureClassifier.classify(status, document.getMimeType(), failureReason)),
                document.getPreviewLastUpdated()
            );
        }
        return new EffectivePreviewSnapshot(
            normalizeUpperOrNull(fallbackPreviewStatus),
            normalizeReasonOrNull(fallbackFailureReason),
            normalizeUpperOrNull(fallbackFailureCategory),
            fallbackPreviewLastUpdated
        );
    }

    @Transactional
    public RenditionResource getForNode(UUID nodeId, String renditionKey) {
        Node node = nodeService.getNode(nodeId);
        if (!(node instanceof Document document)) {
            throw new ResponseStatusException(NOT_FOUND, "Rendition resource not found for non-document node");
        }
        String normalizedKey = normalizeKey(renditionKey);
        return renditionResourceSyncService.syncDocument(document).stream()
            .filter(resource -> normalizedKey.equals(resource.getRenditionKey()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(
                NOT_FOUND,
                "Rendition resource not found: " + normalizedKey
            ));
    }

    @Transactional
    public RenditionMutationResult requeueForNode(UUID nodeId, String renditionKey, boolean force) {
        MutableRenditionTarget target = resolveMutableTarget(nodeId, renditionKey);
        PreviewQueueService.PreviewQueueStatus queueStatus = previewQueueService.enqueue(target.document().getId(), force);
        RenditionResource resource = getForNode(target.document().getId(), target.renditionKey());
        RenditionSummary previewSummary = resolvePreviewMutationSummary(target.document(), queueStatus);
        return new RenditionMutationResult(
            target.renditionKey(),
            "REQUEUE",
            false,
            target.previewLinked(),
            target.previewLinked()
                ? "Queued preview-linked rendition pipeline"
                : "Queued rendition pipeline",
            queueStatus,
            resource,
            previewSummary
        );
    }

    @Transactional
    public RenditionMutationResult invalidateForNode(
        UUID nodeId,
        String renditionKey,
        String reason,
        boolean requeue,
        boolean forceQueue
    ) {
        MutableRenditionTarget target = resolveMutableTarget(nodeId, renditionKey);
        boolean invalidated;
        String effectiveReason = normalizeInvalidateReason(reason);

        if (RenditionResourceSyncService.THUMBNAIL_KEY.equals(target.renditionKey())) {
            invalidated = clearThumbnailMarker(target.document());
        } else {
            clearThumbnailMarker(target.document());
            PreviewService.PreviewRepairResult repairResult = previewService.invalidateRendition(target.document(), effectiveReason);
            invalidated = repairResult.invalidated();
        }

        PreviewQueueService.PreviewQueueStatus queueStatus = null;
        if (requeue) {
            queueStatus = previewQueueService.enqueue(target.document().getId(), forceQueue);
        }

        RenditionResource resource = getForNode(target.document().getId(), target.renditionKey());
        RenditionSummary previewSummary = resolvePreviewMutationSummary(target.document(), queueStatus);
        return new RenditionMutationResult(
            target.renditionKey(),
            "INVALIDATE",
            invalidated,
            target.previewLinked(),
            target.previewLinked()
                ? "Invalidated preview-linked rendition state"
                : "Invalidated rendition state",
            queueStatus,
            resource,
            previewSummary
        );
    }

    private MutableRenditionTarget resolveMutableTarget(UUID nodeId, String renditionKey) {
        Node node = nodeService.getNode(nodeId);
        if (!(node instanceof Document document)) {
            throw new ResponseStatusException(NOT_FOUND, "Rendition resource not found for non-document node");
        }
        String normalizedKey = normalizeKey(renditionKey);
        if (!RenditionResourceSyncService.PREVIEW_KEY.equals(normalizedKey)
            && !RenditionResourceSyncService.THUMBNAIL_KEY.equals(normalizedKey)) {
            throw new ResponseStatusException(NOT_FOUND, "Rendition resource not found: " + normalizedKey);
        }
        return new MutableRenditionTarget(document, normalizedKey, true);
    }

    private String normalizeKey(String renditionKey) {
        if (renditionKey == null || renditionKey.isBlank()) {
            return "";
        }
        return renditionKey.trim().toLowerCase(Locale.ROOT);
    }

    private boolean clearThumbnailMarker(Document document) {
        if (document.getThumbnailId() == null || document.getThumbnailId().isBlank()) {
            return false;
        }
        Document persisted = documentRepository.findById(document.getId()).orElse(document);
        if (persisted.getThumbnailId() == null || persisted.getThumbnailId().isBlank()) {
            return false;
        }
        persisted.setThumbnailId(null);
        documentRepository.save(persisted);
        document.setThumbnailId(null);
        return true;
    }

    private String normalizeInvalidateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Manual rendition invalidation";
        }
        return reason.trim();
    }

    private boolean canMutateDefinition(RenditionDefinitionRegistry.RenditionDefinitionEvaluation definition) {
        return resolveMutationBlockedReason(definition) == null;
    }

    private String resolveMutationBlockedReason(RenditionDefinitionRegistry.RenditionDefinitionEvaluation definition) {
        if (definition == null) {
            return "Rendition definition is unavailable";
        }
        if (!definition.registered()) {
            return "Rendition definition is not registered";
        }
        if (!definition.applicable()) {
            return firstNonBlank(
                definition.applicabilityReason(),
                "Rendition definition is not applicable to this node"
            );
        }
        if (!isMutableRenditionKey(definition.renditionKey())) {
            return "Mutation actions are not supported for this rendition";
        }
        return null;
    }

    private boolean isMutableRenditionKey(String renditionKey) {
        String normalizedKey = normalizeKey(renditionKey);
        return RenditionResourceSyncService.PREVIEW_KEY.equals(normalizedKey)
            || RenditionResourceSyncService.THUMBNAIL_KEY.equals(normalizedKey);
    }

    private String firstNonBlank(String left, String right) {
        if (left != null && !left.isBlank()) {
            return left;
        }
        if (right != null && !right.isBlank()) {
            return right;
        }
        return null;
    }

    private String normalizeReasonOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeUpperOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveCurrentVersionLabel(Document document) {
        if (document == null) {
            return null;
        }
        if (document.getVersionLabel() != null && !document.getVersionLabel().isBlank()) {
            return document.getVersionLabel();
        }
        if (document.getCurrentVersion() != null) {
            return document.getCurrentVersion().getVersionLabel();
        }
        return null;
    }

    private record MutableRenditionTarget(
        Document document,
        String renditionKey,
        boolean previewLinked
    ) {}

    public record RenditionMutationResult(
        String renditionKey,
        String action,
        boolean invalidated,
        boolean previewLinked,
        String message,
        PreviewQueueService.PreviewQueueStatus queueStatus,
        RenditionResource resource,
        RenditionSummary previewSummary
    ) {}

    public record RenditionSummary(
        UUID nodeId,
        boolean document,
        String previewStatus,
        boolean renditionAvailable,
        String previewFailureReason,
        String previewFailureCategory,
        LocalDateTime previewLastUpdated,
        String currentVersionLabel
    ) {
        public static RenditionSummary empty(UUID nodeId) {
            return new RenditionSummary(
                nodeId,
                false,
                null,
                false,
                null,
                null,
                null,
                null
            );
        }
    }

    public record EffectivePreviewSnapshot(
        String previewStatus,
        String previewFailureReason,
        String previewFailureCategory,
        LocalDateTime previewLastUpdated
    ) {}

    public record PreviewMutationStatus(
        UUID documentId,
        String previewStatus,
        String previewFailureReason,
        String previewFailureCategory,
        LocalDateTime previewLastUpdated,
        boolean queued,
        int attempts,
        java.time.Instant nextAttemptAt,
        String message
    ) {}

    public record RenditionDefinitionStatus(
        UUID nodeId,
        String renditionKey,
        String label,
        String targetMimeType,
        String generationMode,
        boolean downloadable,
        int sortOrder,
        String dependencyRenditionKey,
        boolean registered,
        boolean applicable,
        String applicabilityReason,
        String currentState,
        boolean available,
        String contentUrl,
        boolean canRequeue,
        boolean canInvalidate,
        String mutationBlockedReason
    ) {}

    private record RenditionCollectionFilter(
        RenditionCollectionStatusAlias statusAlias,
        List<com.ecm.core.entity.RenditionState> states
    ) {
        boolean matches(RenditionResource resource) {
            if (!statusAlias.matches(resource)) {
                return false;
            }
            return states.isEmpty() || (resource.getState() != null && states.contains(resource.getState()));
        }

        static RenditionCollectionFilter from(String statusFilter, String stateFilter) {
            RenditionCollectionStatusAlias alias = RenditionCollectionStatusAlias.from(statusFilter);
            List<com.ecm.core.entity.RenditionState> states = RenditionCollectionStates.parse(stateFilter);
            return new RenditionCollectionFilter(alias, states);
        }
    }

    private enum RenditionCollectionStatusAlias {
        ALL {
            @Override
            boolean matches(RenditionResource resource) {
                return true;
            }
        },
        CREATED {
            @Override
            boolean matches(RenditionResource resource) {
                return resource.isApplicable()
                    && resource.getState() != com.ecm.core.entity.RenditionState.REGISTERED;
            }
        },
        NOT_CREATED {
            @Override
            boolean matches(RenditionResource resource) {
                return resource.isApplicable()
                    && resource.getState() == com.ecm.core.entity.RenditionState.REGISTERED;
            }
        };

        abstract boolean matches(RenditionResource resource);

        static RenditionCollectionStatusAlias from(String raw) {
            if (raw == null || raw.isBlank()) {
                return ALL;
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "ALL" -> ALL;
                case "CREATED" -> CREATED;
                case "NOT_CREATED" -> NOT_CREATED;
                default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported rendition status filter: " + raw);
            };
        }
    }

    private static final class RenditionCollectionStates {
        private RenditionCollectionStates() {}

        static List<com.ecm.core.entity.RenditionState> parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> {
                    try {
                        return com.ecm.core.entity.RenditionState.valueOf(value.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        throw new ResponseStatusException(BAD_REQUEST, "Unsupported rendition state filter: " + value);
                    }
                })
                .toList();
        }
    }
}
