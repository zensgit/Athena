package com.ecm.core.util;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Small, deterministic MIME normalization helper.
 *
 * Motivation: some uploads arrive with a generic MIME type (e.g. application/octet-stream).
 * We normalize to a best-effort "effective" MIME type using:
 * 1) content magic bytes for a small set of common previewable formats
 * 2) filename extension as a last resort (only for the same small set)
 *
 * This avoids widening behavior for random ASCII blobs (do NOT infer text/*).
 */
public final class MimeTypeNormalizer {

    private static final String MIME_OCTET_STREAM = "application/octet-stream";
    private static final String MIME_BINARY_OCTET_STREAM = "binary/octet-stream";
    private static final String MIME_EMPTY = "application/x-empty";

    private MimeTypeNormalizer() {}

    public static String normalize(String detectedMimeType, String filename, byte[] headerBytes) {
        String normalized = normalizeMimeType(detectedMimeType);
        if (!isGenericMimeType(normalized)) {
            return normalized;
        }

        String sniffed = sniffMimeTypeByMagic(headerBytes);
        if (!sniffed.isBlank()) {
            return sniffed;
        }

        String inferred = inferMimeTypeFromName(filename);
        if (!inferred.isBlank()) {
            return inferred;
        }

        return normalized;
    }

    public static String normalizeMimeType(String mimeType) {
        if (mimeType == null) {
            return MIME_OCTET_STREAM;
        }
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return MIME_OCTET_STREAM;
        }
        int separator = normalized.indexOf(';');
        if (separator >= 0) {
            normalized = normalized.substring(0, separator).trim();
        }
        return normalized.isBlank() ? MIME_OCTET_STREAM : normalized;
    }

    public static boolean isGenericMimeType(String mimeType) {
        String normalized = normalizeMimeType(mimeType);
        return normalized.isBlank()
            || normalized.equals(MIME_OCTET_STREAM)
            || normalized.equals(MIME_BINARY_OCTET_STREAM)
            || normalized.equals(MIME_EMPTY);
    }

    /**
     * Infer MIME type from filename extension (very small whitelist).
     */
    public static String inferMimeTypeFromName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        String normalized = filename.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".pdf")) return "application/pdf";
        if (normalized.endsWith(".png")) return "image/png";
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) return "image/jpeg";
        if (normalized.endsWith(".gif")) return "image/gif";
        if (normalized.endsWith(".webp")) return "image/webp";
        return "";
    }

    /**
     * Detect MIME type by magic bytes (very small whitelist).
     *
     * Note: intentionally strict (only checks the exact leading bytes) to avoid over-matching.
     */
    public static String sniffMimeTypeByMagic(byte[] headerBytes) {
        if (headerBytes == null || headerBytes.length == 0) {
            return "";
        }

        // PDF: "%PDF-"
        if (startsWith(headerBytes, new byte[] {0x25, 0x50, 0x44, 0x46, 0x2d})) {
            return "application/pdf";
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (startsWith(headerBytes, new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) {
            return "image/png";
        }

        // JPEG: FF D8 FF
        if (startsWith(headerBytes, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
            return "image/jpeg";
        }

        // GIF: "GIF87a" / "GIF89a"
        if (headerBytes.length >= 6) {
            String prefix = new String(headerBytes, 0, 6, StandardCharsets.US_ASCII);
            if ("GIF87a".equals(prefix) || "GIF89a".equals(prefix)) {
                return "image/gif";
            }
        }

        // WebP: "RIFF....WEBP"
        if (headerBytes.length >= 12) {
            String riff = new String(headerBytes, 0, 4, StandardCharsets.US_ASCII);
            String webp = new String(headerBytes, 8, 4, StandardCharsets.US_ASCII);
            if ("RIFF".equals(riff) && "WEBP".equals(webp)) {
                return "image/webp";
            }
        }

        return "";
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}

