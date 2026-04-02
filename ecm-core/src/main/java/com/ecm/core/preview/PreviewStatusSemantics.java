package com.ecm.core.preview;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.service.RenditionDefinitionRegistry;
import com.ecm.core.service.RenditionResourceSyncService;

public final class PreviewStatusSemantics {

    private static final RenditionDefinitionRegistry RENDITION_DEFINITION_REGISTRY = new RenditionDefinitionRegistry();

    private PreviewStatusSemantics() {}

    public static String resolveEffectiveStatus(Document document) {
        if (document == null) {
            return null;
        }
        if (document.isPreviewAvailable()) {
            return PreviewStatus.READY.name();
        }

        PreviewStatus previewStatus = document.getPreviewStatus();
        if (previewStatus == null) {
            RenditionDefinitionRegistry.RenditionDefinitionEvaluation definition =
                RENDITION_DEFINITION_REGISTRY.evaluate(document, RenditionResourceSyncService.PREVIEW_KEY);
            return definition != null && !definition.applicable()
                ? PreviewStatus.UNSUPPORTED.name()
                : null;
        }

        if (previewStatus == PreviewStatus.FAILED) {
            String failureCategory = PreviewFailureClassifier.classify(
                previewStatus.name(),
                document.getMimeType(),
                document.getPreviewFailureReason()
            );
            if (PreviewFailureClassifier.CATEGORY_UNSUPPORTED.equalsIgnoreCase(failureCategory)) {
                return PreviewStatus.UNSUPPORTED.name();
            }
        }

        return previewStatus.name();
    }

    public static String resolveEffectiveFailureReason(Document document) {
        if (document == null) {
            return null;
        }
        String rawReason = normalize(document.getPreviewFailureReason());
        if (rawReason != null) {
            return rawReason;
        }
        RenditionDefinitionRegistry.RenditionDefinitionEvaluation definition =
            RENDITION_DEFINITION_REGISTRY.evaluate(document, RenditionResourceSyncService.PREVIEW_KEY);
        if (definition == null || definition.applicable()) {
            return null;
        }
        return normalize(definition.applicabilityReason());
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
