package com.ecm.core.conversion;

import lombok.Data;
import java.util.UUID;

@Data
public class ConversionResult {
    private UUID sourceDocumentId;
    private String sourceFormat;
    private String targetFormat;
    private boolean success;
    private String errorMessage;
    private byte[] content;
    private String mimeType;
    private long fileSize;
    private long conversionTime;
}