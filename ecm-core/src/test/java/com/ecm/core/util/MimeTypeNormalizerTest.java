package com.ecm.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MimeTypeNormalizerTest {

    @Test
    void normalizesMimeTypeParamsAndCase() {
        assertEquals("application/pdf", MimeTypeNormalizer.normalizeMimeType("Application/PDF; charset=utf-8"));
    }

    @Test
    void infersPdfFromFilenameWhenDetectedIsGeneric() {
        assertEquals(
            "application/pdf",
            MimeTypeNormalizer.normalize("application/octet-stream", "file.pdf", new byte[] {1, 2, 3})
        );
    }

    @Test
    void sniffsPdfFromMagicBytesWhenDetectedIsGeneric() {
        assertEquals(
            "application/pdf",
            MimeTypeNormalizer.normalize("application/octet-stream", "file.bin", new byte[] {0x25, 0x50, 0x44, 0x46, 0x2d, 0x00})
        );
    }

    @Test
    void doesNotInferTextForGenericAsciiContent() {
        assertEquals(
            "application/octet-stream",
            MimeTypeNormalizer.normalize("application/octet-stream", "file.bin", "hello".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
        );
    }

    @Test
    void sniffsPngFromMagicBytes() {
        assertEquals(
            "image/png",
            MimeTypeNormalizer.normalize("application/octet-stream", "file.bin", new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00})
        );
    }

    @Test
    void keepsNonGenericDetectedMimeType() {
        assertEquals(
            "application/pdf",
            MimeTypeNormalizer.normalize("application/pdf", "file.bin", new byte[] {1, 2, 3})
        );
    }
}

