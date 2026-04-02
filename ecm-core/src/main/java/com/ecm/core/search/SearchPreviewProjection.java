package com.ecm.core.search;

import com.ecm.core.entity.Document;
import com.ecm.core.preview.PreviewFailureClassifier;
import com.ecm.core.preview.PreviewStatusSemantics;

final class SearchPreviewProjection {

    private SearchPreviewProjection() {}

    static String projectPreviewStatus(Document document) {
        return PreviewStatusSemantics.resolveEffectiveStatus(document);
    }

    static String projectPreviewFailureReason(Document document) {
        return PreviewStatusSemantics.resolveEffectiveFailureReason(document);
    }

    static String projectPreviewFailureCategory(Document document) {
        String previewStatus = projectPreviewStatus(document);
        String previewFailureReason = projectPreviewFailureReason(document);
        return PreviewFailureClassifier.classify(previewStatus, document != null ? document.getMimeType() : null, previewFailureReason);
    }
}
