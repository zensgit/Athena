package com.ecm.core.preview;

import java.util.Locale;
import java.util.Set;

public final class PreviewFailureClassifier {
    public static final String CATEGORY_UNSUPPORTED = "UNSUPPORTED";
    public static final String CATEGORY_TEMPORARY = "TEMPORARY";
    public static final String CATEGORY_PERMANENT = "PERMANENT";

    private static final Set<String> UNSUPPORTED_MIME_TYPES = Set.of(
        "application/octet-stream",
        "binary/octet-stream",
        "application/x-empty"
    );

    private PreviewFailureClassifier() {}

    public static String classify(String previewStatus, String mimeType, String failureReason) {
        if (!isFailedStatus(previewStatus)) {
            return null;
        }

        if (isUnsupportedMimeType(mimeType) || isUnsupportedReason(failureReason)) {
            return CATEGORY_UNSUPPORTED;
        }

        if (isTemporaryReason(failureReason)) {
            return CATEGORY_TEMPORARY;
        }

        return CATEGORY_PERMANENT;
    }

    public static boolean isUnsupportedMimeType(String mimeType) {
        String normalized = normalizeMimeType(mimeType);
        return normalized != null && UNSUPPORTED_MIME_TYPES.contains(normalized);
    }

    private static boolean isFailedStatus(String previewStatus) {
        return previewStatus != null && "FAILED".equalsIgnoreCase(previewStatus.trim());
    }

    private static boolean isUnsupportedReason(String failureReason) {
        String normalized = normalizeMessage(failureReason);
        if (normalized == null) {
            return false;
        }
        return normalized.startsWith("preview not supported")
            || normalized.contains("not supported for mime type")
            || normalized.contains("not available for empty pdf content");
    }

    private static boolean isTemporaryReason(String failureReason) {
        String normalized = normalizeMessage(failureReason);
        if (normalized == null) {
            return false;
        }
        return normalized.startsWith("error generating preview")
            || normalized.startsWith("cad preview failed")
            || normalized.contains("timeout")
            || normalized.contains("timed out")
            || normalized.contains("temporar")
            || normalized.contains("connection reset")
            || normalized.contains("connection refused")
            || normalized.contains("service unavailable")
            || normalized.contains("502")
            || normalized.contains("503")
            || normalized.contains("504");
    }

    private static String normalizeMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return null;
        }
        return mimeType.split(";")[0].trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeMessage(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            return null;
        }
        return failureReason.trim().toLowerCase(Locale.ROOT);
    }
}
