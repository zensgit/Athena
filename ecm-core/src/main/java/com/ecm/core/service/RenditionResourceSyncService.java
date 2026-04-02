package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.entity.RenditionResource;
import com.ecm.core.entity.RenditionState;
import com.ecm.core.preview.PreviewFailureClassifier;
import com.ecm.core.preview.PreviewStatusSemantics;
import com.ecm.core.repository.RenditionResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RenditionResourceSyncService {

    public static final String PREVIEW_KEY = "preview";
    public static final String THUMBNAIL_KEY = "thumbnail";

    private final RenditionResourceRepository renditionResourceRepository;
    private final RenditionDefinitionRegistry renditionDefinitionRegistry;

    @Transactional
    public List<RenditionResource> syncDocument(Document document) {
        if (document == null || document.getId() == null) {
            return List.of();
        }

        List<RenditionResourceSnapshot> targetSnapshots = buildSnapshots(document);
        Map<String, RenditionResource> existingByKey = renditionResourceRepository.findByDocumentIdOrderBySortOrderAsc(document.getId()).stream()
            .collect(Collectors.toMap(
                RenditionResource::getRenditionKey,
                Function.identity(),
                (left, right) -> left
            ));

        List<RenditionResource> synced = new ArrayList<>();
        for (RenditionResourceSnapshot snapshot : targetSnapshots) {
            RenditionResource resource = existingByKey.get(snapshot.renditionKey());
            if (resource == null) {
                resource = RenditionResource.builder()
                    .document(document)
                    .renditionKey(snapshot.renditionKey())
                    .build();
            }
            applySnapshot(resource, snapshot);
            synced.add(renditionResourceRepository.save(resource));
        }

        synced.sort(Comparator.comparing(RenditionResource::getSortOrder));
        return synced;
    }

    private List<RenditionResourceSnapshot> buildSnapshots(Document document) {
        String previewStatus = PreviewStatusSemantics.resolveEffectiveStatus(document);
        String failureReason = firstNonBlank(
            PreviewStatusSemantics.resolveEffectiveFailureReason(document),
            document.getPreviewLastFailureReason()
        );
        String failureCategory = PreviewFailureClassifier.classify(previewStatus, document.getMimeType(), failureReason);
        RenditionDefinitionRegistry.RenditionDefinitionEvaluation previewDefinition =
            renditionDefinitionRegistry.evaluate(document, PREVIEW_KEY);
        RenditionDefinitionRegistry.RenditionDefinitionEvaluation thumbnailDefinition =
            renditionDefinitionRegistry.evaluate(document, THUMBNAIL_KEY);
        boolean previewApplicable = previewDefinition != null && previewDefinition.applicable();
        boolean thumbnailApplicable = thumbnailDefinition != null && thumbnailDefinition.applicable();
        RenditionState previewState = derivePreviewState(document);
        RenditionState thumbnailState = deriveThumbnailState(document);

        return List.of(
            new RenditionResourceSnapshot(
                PREVIEW_KEY,
                previewDefinition != null ? previewDefinition.label() : "Preview",
                previewDefinition != null ? previewDefinition.targetMimeType() : "application/json",
                previewState,
                previewState == RenditionState.READY,
                previewDefinition != null && previewDefinition.downloadable(),
                previewApplicable,
                previewDefinition != null ? previewDefinition.applicabilityReason() : null,
                previewDefinition != null ? previewDefinition.generationMode() : null,
                previewDefinition != null ? previewDefinition.dependencyRenditionKey() : null,
                "/api/v1/documents/" + document.getId() + "/preview",
                previewState == RenditionState.FAILED || previewState == RenditionState.UNSUPPORTED ? failureReason : null,
                previewState == RenditionState.FAILED || previewState == RenditionState.UNSUPPORTED ? failureCategory : null,
                previewStatus,
                document.getVersionLabel(),
                document.getPreviewLastUpdated(),
                0
            ),
            new RenditionResourceSnapshot(
                THUMBNAIL_KEY,
                thumbnailDefinition != null ? thumbnailDefinition.label() : "Thumbnail",
                thumbnailDefinition != null ? thumbnailDefinition.targetMimeType() : "image/png",
                thumbnailState,
                thumbnailState == RenditionState.READY,
                thumbnailDefinition != null && thumbnailDefinition.downloadable(),
                thumbnailApplicable,
                thumbnailDefinition != null ? thumbnailDefinition.applicabilityReason() : null,
                thumbnailDefinition != null ? thumbnailDefinition.generationMode() : null,
                thumbnailDefinition != null ? thumbnailDefinition.dependencyRenditionKey() : null,
                "/api/v1/documents/" + document.getId() + "/thumbnail",
                null,
                null,
                previewStatus,
                document.getVersionLabel(),
                document.getPreviewLastUpdated(),
                1
            )
        );
    }

    private void applySnapshot(RenditionResource resource, RenditionResourceSnapshot snapshot) {
        resource.setLabel(snapshot.label());
        resource.setMimeType(snapshot.mimeType());
        resource.setState(snapshot.state());
        resource.setAvailable(snapshot.available());
        resource.setDownloadable(snapshot.downloadable());
        resource.setApplicable(snapshot.applicable());
        resource.setApplicabilityReason(snapshot.applicabilityReason());
        resource.setGenerationMode(snapshot.generationMode());
        resource.setDependencyRenditionKey(snapshot.dependencyRenditionKey());
        resource.setContentUrl(snapshot.contentUrl());
        resource.setErrorReason(snapshot.errorReason());
        resource.setErrorCategory(snapshot.errorCategory());
        resource.setSourceStatus(snapshot.sourceStatus());
        resource.setVersionLabel(snapshot.versionLabel());
        resource.setSourceUpdatedAt(snapshot.sourceUpdatedAt());
        resource.setLastSyncedAt(LocalDateTime.now());
        resource.setSortOrder(snapshot.sortOrder());
    }

    private RenditionState derivePreviewState(Document document) {
        if (document.isPreviewAvailable()) {
            return RenditionState.READY;
        }
        String effectiveStatus = PreviewStatusSemantics.resolveEffectiveStatus(document);
        if (effectiveStatus == null) {
            return RenditionState.REGISTERED;
        }
        return switch (PreviewStatus.valueOf(effectiveStatus)) {
            case READY -> RenditionState.READY;
            case PROCESSING -> RenditionState.PROCESSING;
            case FAILED -> RenditionState.FAILED;
            case UNSUPPORTED -> RenditionState.UNSUPPORTED;
        };
    }

    private RenditionState deriveThumbnailState(Document document) {
        if (document.getThumbnailId() != null && !document.getThumbnailId().isBlank()) {
            return RenditionState.READY;
        }
        if (!(renditionDefinitionRegistry.evaluate(document, THUMBNAIL_KEY).applicable())) {
            return RenditionState.REGISTERED;
        }
        String effectiveStatus = PreviewStatusSemantics.resolveEffectiveStatus(document);
        PreviewStatus previewStatus = effectiveStatus != null ? PreviewStatus.valueOf(effectiveStatus) : null;
        if (previewStatus == PreviewStatus.PROCESSING) {
            return RenditionState.PROCESSING;
        }
        if (previewStatus == PreviewStatus.UNSUPPORTED) {
            return RenditionState.UNSUPPORTED;
        }
        if (previewStatus == PreviewStatus.READY && !document.isPreviewAvailable()) {
            return RenditionState.STALE;
        }
        return RenditionState.REGISTERED;
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

    private record RenditionResourceSnapshot(
        String renditionKey,
        String label,
        String mimeType,
        RenditionState state,
        boolean available,
        boolean downloadable,
        boolean applicable,
        String applicabilityReason,
        String generationMode,
        String dependencyRenditionKey,
        String contentUrl,
        String errorReason,
        String errorCategory,
        String sourceStatus,
        String versionLabel,
        LocalDateTime sourceUpdatedAt,
        int sortOrder
    ) {}
}
