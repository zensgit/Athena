package com.ecm.core.preview;

import lombok.Data;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Data
public class PreviewResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private UUID documentId;
    private String traceRequestId;
    private String mimeType;
    private boolean supported;
    private String status;
    private String message;
    private boolean retryNeeded;
    private String retryHint;
    private String failureReason;
    private String failureCategory;
    private List<PreviewPage> pages;
    private int pageCount;
}

@Data
class PreviewPage implements Serializable {
    private static final long serialVersionUID = 1L;

    private int pageNumber;
    private int width;
    private int height;
    private String format;
    private byte[] content;
    private String textContent;
    private String url;
}
