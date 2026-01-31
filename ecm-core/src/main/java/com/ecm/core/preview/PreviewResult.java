package com.ecm.core.preview;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class PreviewResult {
    private UUID documentId;
    private String mimeType;
    private boolean supported;
    private String status;
    private String message;
    private String failureReason;
    private List<PreviewPage> pages;
    private int pageCount;
}

@Data
class PreviewPage {
    private int pageNumber;
    private int width;
    private int height;
    private String format;
    private byte[] content;
    private String textContent;
    private String url;
}
