package com.ecm.core.service;

import com.ecm.core.entity.Document;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class RenditionDefinitionRegistry {

    public static final String GENERATION_MODE_PREVIEW_PIPELINE = "PREVIEW_PIPELINE";
    public static final String GENERATION_MODE_PREVIEW_DERIVED = "PREVIEW_DERIVED";

    private final List<RenditionDefinition> definitions = List.of(
        new PreviewRenditionDefinition(),
        new ThumbnailRenditionDefinition()
    );

    public List<RenditionDefinitionEvaluation> evaluate(Document document) {
        return definitions.stream()
            .map(definition -> definition.evaluate(document, this))
            .toList();
    }

    public RenditionDefinitionEvaluation evaluate(Document document, String renditionKey) {
        String normalizedKey = renditionKey == null ? "" : renditionKey.trim().toLowerCase(Locale.ROOT);
        return definitions.stream()
            .filter(definition -> definition.getRenditionKey().equals(normalizedKey))
            .findFirst()
            .map(definition -> definition.evaluate(document, this))
            .orElse(null);
    }

    boolean isPreviewApplicable(Document document) {
        if (document == null) {
            return false;
        }
        String mimeType = document.getMimeType();
        if (mimeType == null || mimeType.isBlank()) {
            return false;
        }
        if (document.getFileSize() != null && document.getFileSize() <= 0) {
            return false;
        }
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        return !normalized.equals("application/octet-stream")
            && !normalized.equals("binary/octet-stream")
            && !normalized.equals("application/x-empty");
    }

    private abstract static class RenditionDefinition {
        @Getter
        private final String renditionKey;
        @Getter
        private final String label;
        @Getter
        private final String targetMimeType;
        @Getter
        private final String generationMode;
        @Getter
        private final boolean downloadable;
        @Getter
        private final int sortOrder;
        @Getter
        private final String dependencyRenditionKey;

        protected RenditionDefinition(
            String renditionKey,
            String label,
            String targetMimeType,
            String generationMode,
            boolean downloadable,
            int sortOrder,
            String dependencyRenditionKey
        ) {
            this.renditionKey = renditionKey;
            this.label = label;
            this.targetMimeType = targetMimeType;
            this.generationMode = generationMode;
            this.downloadable = downloadable;
            this.sortOrder = sortOrder;
            this.dependencyRenditionKey = dependencyRenditionKey;
        }

        protected abstract ApplicabilityResult computeApplicability(Document document, RenditionDefinitionRegistry registry);

        RenditionDefinitionEvaluation evaluate(Document document, RenditionDefinitionRegistry registry) {
            ApplicabilityResult applicability = computeApplicability(document, registry);
            return new RenditionDefinitionEvaluation(
                renditionKey,
                label,
                targetMimeType,
                generationMode,
                downloadable,
                sortOrder,
                dependencyRenditionKey,
                true,
                applicability.applicable(),
                applicability.reason()
            );
        }
    }

    private static final class PreviewRenditionDefinition extends RenditionDefinition {
        private PreviewRenditionDefinition() {
            super(
                RenditionResourceSyncService.PREVIEW_KEY,
                "Preview",
                "application/json",
                GENERATION_MODE_PREVIEW_PIPELINE,
                false,
                0,
                null
            );
        }

        @Override
        protected ApplicabilityResult computeApplicability(Document document, RenditionDefinitionRegistry registry) {
            if (document == null) {
                return new ApplicabilityResult(false, "Preview definition requires a document source");
            }
            if (document.getMimeType() == null || document.getMimeType().isBlank()) {
                return new ApplicabilityResult(false, "Preview definition requires a known source mime type");
            }
            if (document.getFileSize() != null && document.getFileSize() <= 0) {
                return new ApplicabilityResult(false, "Preview definition is not applicable to empty source files");
            }
            if (!registry.isPreviewApplicable(document)) {
                return new ApplicabilityResult(false, "Preview definition is not registered for generic binary sources");
            }
            return new ApplicabilityResult(true, null);
        }
    }

    private static final class ThumbnailRenditionDefinition extends RenditionDefinition {
        private ThumbnailRenditionDefinition() {
            super(
                RenditionResourceSyncService.THUMBNAIL_KEY,
                "Thumbnail",
                "image/png",
                GENERATION_MODE_PREVIEW_DERIVED,
                true,
                1,
                RenditionResourceSyncService.PREVIEW_KEY
            );
        }

        @Override
        protected ApplicabilityResult computeApplicability(Document document, RenditionDefinitionRegistry registry) {
            if (document == null) {
                return new ApplicabilityResult(false, "Thumbnail definition requires a document source");
            }
            if (document.getThumbnailId() != null && !document.getThumbnailId().isBlank()) {
                return new ApplicabilityResult(true, null);
            }
            if (!registry.isPreviewApplicable(document)) {
                return new ApplicabilityResult(false, "Thumbnail definition depends on a preview-eligible source rendition");
            }
            return new ApplicabilityResult(true, null);
        }
    }

    private record ApplicabilityResult(
        boolean applicable,
        String reason
    ) {}

    public record RenditionDefinitionEvaluation(
        String renditionKey,
        String label,
        String targetMimeType,
        String generationMode,
        boolean downloadable,
        int sortOrder,
        String dependencyRenditionKey,
        boolean registered,
        boolean applicable,
        String applicabilityReason
    ) {}
}
