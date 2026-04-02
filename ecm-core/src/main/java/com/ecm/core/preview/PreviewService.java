package com.ecm.core.preview;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.search.SearchIndexService;
import com.ecm.core.service.ContentService;
import com.ecm.core.service.RenditionResourceSyncService;
import com.ecm.core.service.SecurityService;
import com.ecm.core.entity.Permission.PermissionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreviewService {

    private static final String X_ALFRESCO_RETRY_NEEDED = "X-Alfresco-Retry-Needed";
    private static final String X_ECM_RETRY_NEEDED = "X-Ecm-Retry-Needed";
    
    private final ContentService contentService;
    private final SecurityService securityService;
    private final MeterRegistry meterRegistry;
    private final DocumentRepository documentRepository;
    private final SearchIndexService searchIndexService;
    private final RenditionResourceSyncService renditionResourceSyncService;
    private final CadRenderEndpointRegistry cadRenderEndpointRegistry;
    private final CadRenderFailoverTracker cadRenderFailoverTracker;
    private final PreviewTransformTraceBuffer previewTransformTraceBuffer;
    
    @Value("${ecm.preview.cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${ecm.preview.max-pages:50}")
    private int maxPages;
    
    @Value("${ecm.preview.thumbnail.width:200}")
    private int thumbnailWidth;
    
    @Value("${ecm.preview.thumbnail.height:200}")
    private int thumbnailHeight;

    @Value("${ecm.preview.cad.auth-token:}")
    private String cadRenderAuthToken;

    @Value("${ecm.preview.cad.timeout-ms:30000}")
    private int cadRenderTimeoutMs;
    
    @Value("${ecm.storage.temp-path}")
    private String tempPath;
    
    @Cacheable(value = "previews", condition = "#root.target.isCacheEnabled()")
    public PreviewResult generatePreview(Document document) throws IOException {
        // Check permissions
        if (!securityService.hasPermission(document, PermissionType.READ)) {
            throw new SecurityException("No permission to preview document");
        }

        updatePreviewStatus(document, PreviewStatus.PROCESSING, null, null);
        
        String mimeType = normalizeMimeType(document.getMimeType(), document.getName());
        String traceRequestId = previewTransformTraceBuffer.start(document.getId(), mimeType, "preview");
        traceEvent(traceRequestId, "PREVIEW_START", "documentId=" + document.getId());
        traceEvent(traceRequestId, "MIME_RESOLVED", "mimeType=" + mimeType);
        PreviewResult result = new PreviewResult();
        result.setDocumentId(document.getId());
        result.setTraceRequestId(traceRequestId);
        result.setMimeType(mimeType);
        
        try (InputStream content = contentService.getContent(document.getContentId())) {
            if (isCadDocument(mimeType, document.getName())) {
                traceEvent(traceRequestId, "ROUTE", "cad");
                result = generateCadPreview(content, document, traceRequestId);
            } else if (mimeType.startsWith("image/")) {
                traceEvent(traceRequestId, "ROUTE", "image");
                result = generateImagePreview(content, document);
            } else if (mimeType.equals("application/pdf")) {
                traceEvent(traceRequestId, "ROUTE", "pdf");
                result = generatePdfPreview(content, document);
            } else if (isOfficeDocument(mimeType)) {
                traceEvent(traceRequestId, "ROUTE", "office");
                result = generateOfficePreview(content, document, mimeType);
            } else if (mimeType.startsWith("text/")) {
                traceEvent(traceRequestId, "ROUTE", "text");
                result = generateTextPreview(content, document);
            } else {
                result.setSupported(false);
                result.setMessage("Preview not supported for mime type: " + mimeType);
                traceEvent(traceRequestId, "UNSUPPORTED", result.getMessage());
            }
        } catch (Exception e) {
            log.error("Error generating preview for document: {}", document.getId(), e);
            result.setSupported(false);
            result.setMessage("Error generating preview: " + e.getMessage());
            traceEvent(traceRequestId, "ERROR", result.getMessage());
        }
        
        result.setDocumentId(document.getId());
        result.setTraceRequestId(traceRequestId);
        result.setMimeType(mimeType);
        applyPreviewOutcome(document, result);
        traceEvent(
            traceRequestId,
            "OUTCOME",
            "status=" + result.getStatus() + ", supported=" + result.isSupported() + ", retryNeeded=" + result.isRetryNeeded()
        );
        previewTransformTraceBuffer.complete(
            traceRequestId,
            result.getStatus(),
            result.isRetryNeeded(),
            result.getFailureReason()
        );
        return result;
    }
    
    @Cacheable(value = "thumbnails", condition = "#root.target.isCacheEnabled()")
    public byte[] generateThumbnail(Document document) throws IOException {
        // Check permissions
        if (!securityService.hasPermission(document, PermissionType.READ)) {
            throw new SecurityException("No permission to view thumbnail");
        }
        
        String mimeType = normalizeMimeType(document.getMimeType(), document.getName());
        
        try (InputStream content = contentService.getContent(document.getContentId())) {
            if (isCadDocument(mimeType, document.getName())) {
                return generateCadThumbnail(content, document);
            } else if (mimeType.startsWith("image/")) {
                return generateImageThumbnail(content);
            } else if (mimeType.equals("application/pdf")) {
                return generatePdfThumbnail(content);
            } else if (isOfficeDocument(mimeType)) {
                return generateOfficeThumbnail(content, mimeType);
            } else {
                return generateDefaultThumbnail(mimeType);
            }
        }
    }

    public PreviewReadiness evaluateReadiness(Document document) {
        if (document == null) {
            return new PreviewReadiness("INVALID", false, false, "MISSING_DOCUMENT");
        }

        Long fileSize = document.getFileSize();
        if (fileSize != null && fileSize <= 0L) {
            return new PreviewReadiness("ZERO_SOURCE", false, false, "SOURCE_EMPTY");
        }

        if (document.getPreviewStatus() != PreviewStatus.READY) {
            return new PreviewReadiness("NOT_READY", false, false, null);
        }

        String contentHash = normalizeContentHash(document.getContentHash());
        String previewContentHash = normalizeContentHash(document.getPreviewContentHash());
        if (contentHash == null || previewContentHash == null) {
            return new PreviewReadiness("READY_HASH_UNKNOWN", true, false, null);
        }
        if (!contentHash.equals(previewContentHash)) {
            return new PreviewReadiness("READY_STALE_HASH", false, true, "STALE_HASH_MISMATCH");
        }
        return new PreviewReadiness("READY_HASH_MATCH", true, false, null);
    }

    public PreviewRepairResult invalidateRendition(Document document, String reason) {
        if (document == null) {
            return new PreviewRepairResult(null, null, false, "MISSING_DOCUMENT", null, null);
        }
        String effectiveReason = reason == null || reason.isBlank() ? "Preview invalidated" : reason.trim();
        PreviewStatus previousStatus = document.getPreviewStatus();
        String previousPreviewHash = document.getPreviewContentHash();
        String currentContentHash = document.getContentHash();
        updatePreviewStatus(document, PreviewStatus.FAILED, effectiveReason, null);
        return new PreviewRepairResult(
            document.getId(),
            previousStatus != null ? previousStatus.name() : null,
            true,
            effectiveReason,
            previousPreviewHash,
            currentContentHash
        );
    }
    
    private PreviewResult generateImagePreview(InputStream content, Document document) 
            throws IOException {
        PreviewResult result = new PreviewResult();
        result.setSupported(true);
        
        BufferedImage image = ImageIO.read(content);
        if (image == null) {
            throw new IOException("Failed to read image");
        }
        
        // Generate preview pages
        List<PreviewPage> pages = new ArrayList<>();
        
        // Original size page
        PreviewPage originalPage = new PreviewPage();
        originalPage.setPageNumber(1);
        originalPage.setWidth(image.getWidth());
        originalPage.setHeight(image.getHeight());
        originalPage.setFormat("original");
        pages.add(originalPage);
        
        // Generate different sizes
        int[] widths = {800, 1200, 1600};
        for (int width : widths) {
            if (width < image.getWidth()) {
                BufferedImage resized = Thumbnails.of(image)
                    .size(width, Integer.MAX_VALUE)
                    .keepAspectRatio(true)
                    .asBufferedImage();
                
                PreviewPage page = new PreviewPage();
                page.setPageNumber(1);
                page.setWidth(resized.getWidth());
                page.setHeight(resized.getHeight());
                page.setFormat("png");
                page.setContent(imageToBytes(resized, "png"));
                pages.add(page);
            }
        }
        
        result.setPages(pages);
        result.setPageCount(1);
        
        return result;
    }
    
    private PreviewResult generatePdfPreview(InputStream content, Document document) 
            throws IOException {
        PreviewResult result = new PreviewResult();
        byte[] pdfBytes = content.readAllBytes();
        if (pdfBytes.length == 0) {
            log.warn("Preview skipped for empty PDF content. documentId={}, name={}", document.getId(), document.getName());
            result.setSupported(false);
            result.setMessage("Preview not available for empty PDF content");
            return result;
        }

        result.setSupported(true);
        
        List<PreviewPage> pages = new ArrayList<>();
        try (PDDocument pdf = PDDocument.load(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(pdf);
            int pageCount = Math.min(pdf.getNumberOfPages(), maxPages);
            
            for (int i = 0; i < pageCount; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 150);
                
                PreviewPage page = new PreviewPage();
                page.setPageNumber(i + 1);
                page.setWidth(image.getWidth());
                page.setHeight(image.getHeight());
                page.setFormat("png");
                page.setContent(imageToBytes(image, "png"));
                pages.add(page);
            }
            
            result.setPages(pages);
            result.setPageCount(pdf.getNumberOfPages());
        }
        
        return result;
    }
    
    private PreviewResult generateOfficePreview(InputStream content, Document document, 
                                                String mimeType) throws IOException {
        PreviewResult result = new PreviewResult();
        result.setSupported(true);
        
        List<PreviewPage> pages = new ArrayList<>();
        
        if (mimeType.contains("word") || mimeType.contains("document")) {
            // Word document preview
            try (XWPFDocument doc = new XWPFDocument(content)) {
                // For Word docs, we'll extract text for preview
                StringBuilder text = new StringBuilder();
                doc.getParagraphs().forEach(p -> text.append(p.getText()).append("\n"));
                
                PreviewPage page = new PreviewPage();
                page.setPageNumber(1);
                page.setFormat("text");
                page.setTextContent(text.toString());
                pages.add(page);
                
                result.setPageCount(1);
            }
        } else if (mimeType.contains("presentation") || mimeType.contains("powerpoint")) {
            // PowerPoint preview
            try (XMLSlideShow ppt = new XMLSlideShow(content)) {
                List<XSLFSlide> slides = ppt.getSlides();
                int slideCount = Math.min(slides.size(), maxPages);
                
                for (int i = 0; i < slideCount; i++) {
                    XSLFSlide slide = slides.get(i);
                    
                    Dimension pgsize = ppt.getPageSize();
                    BufferedImage img = new BufferedImage(pgsize.width, pgsize.height, 
                        BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = img.createGraphics();
                    
                    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                        RenderingHints.VALUE_ANTIALIAS_ON);
                    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, 
                        RenderingHints.VALUE_RENDER_QUALITY);
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, 
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    
                    graphics.setPaint(Color.white);
                    graphics.fill(new Rectangle2D.Float(0, 0, pgsize.width, pgsize.height));
                    
                    slide.draw(graphics);
                    graphics.dispose();
                    
                    PreviewPage page = new PreviewPage();
                    page.setPageNumber(i + 1);
                    page.setWidth(img.getWidth());
                    page.setHeight(img.getHeight());
                    page.setFormat("png");
                    page.setContent(imageToBytes(img, "png"));
                    pages.add(page);
                }
                
                result.setPageCount(slides.size());
            }
        } else if (mimeType.contains("spreadsheet") || mimeType.contains("excel")) {
            // Excel preview - render as HTML table
            try (XSSFWorkbook workbook = new XSSFWorkbook(content)) {
                // For simplicity, preview first sheet only
                var sheet = workbook.getSheetAt(0);
                
                StringBuilder html = new StringBuilder("<table border='1'>");
                sheet.forEach(row -> {
                    html.append("<tr>");
                    row.forEach(cell -> {
                        html.append("<td>").append(cell.toString()).append("</td>");
                    });
                    html.append("</tr>");
                });
                html.append("</table>");
                
                PreviewPage page = new PreviewPage();
                page.setPageNumber(1);
                page.setFormat("html");
                page.setTextContent(html.toString());
                pages.add(page);
                
                result.setPageCount(workbook.getNumberOfSheets());
            }
        }
        
        result.setPages(pages);
        return result;
    }
    
    private PreviewResult generateTextPreview(InputStream content, Document document) 
            throws IOException {
        PreviewResult result = new PreviewResult();
        result.setSupported(true);
        
        String text = new String(content.readAllBytes());
        
        PreviewPage page = new PreviewPage();
        page.setPageNumber(1);
        page.setFormat("text");
        page.setTextContent(text);
        
        result.setPages(List.of(page));
        result.setPageCount(1);
        
        return result;
    }
    
    private PreviewResult generateCadPreview(InputStream content, Document document, String traceRequestId)
            throws IOException {
        PreviewResult result = new PreviewResult();

        if (!cadRenderEndpointRegistry.isCadPreviewEnabled()) {
            result.setSupported(false);
            result.setMessage("CAD preview disabled");
            meterRegistry.counter("cad_preview_total", "status", "error", "reason", "disabled").increment();
            traceEvent(traceRequestId, "CAD_DISABLED", result.getMessage());
            return result;
        }
        if (!cadRenderEndpointRegistry.isConfigured()) {
            result.setSupported(false);
            result.setMessage("CAD preview service not configured");
            meterRegistry.counter("cad_preview_total", "status", "error", "reason", "missing_render_url").increment();
            traceEvent(traceRequestId, "CAD_NOT_CONFIGURED", result.getMessage());
            return result;
        }

        try {
            CadRenderResult renderResult = renderCadToPng(content, document, traceRequestId);
            if (renderResult.retryNeeded()) {
                result.setSupported(false);
                result.setRetryNeeded(true);
                result.setRetryHint(renderResult.retryHint());
                result.setMessage(renderResult.retryHint() != null && !renderResult.retryHint().isBlank()
                    ? renderResult.retryHint()
                    : "CAD preview retry needed");
                meterRegistry.counter("cad_preview_total", "status", "error", "reason", "retry_needed").increment();
                traceEvent(traceRequestId, "CAD_RETRY_NEEDED", result.getMessage());
                return result;
            }

            PreviewPage page = new PreviewPage();
            page.setPageNumber(1);
            page.setWidth(renderResult.width());
            page.setHeight(renderResult.height());
            page.setFormat("png");
            page.setContent(renderResult.pngBytes());

            result.setSupported(true);
            result.setPages(List.of(page));
            result.setPageCount(1);
            meterRegistry.counter("cad_preview_total", "status", "ok", "reason", "rendered").increment();
            traceEvent(
                traceRequestId,
                "CAD_RENDERED",
                "width=" + renderResult.width() + ", height=" + renderResult.height()
            );
            return result;
        } catch (Exception e) {
            result.setSupported(false);
            result.setMessage("CAD preview failed: " + e.getMessage());
            log.warn("CAD preview failed for document: {}", document.getId(), e);
            meterRegistry.counter("cad_preview_total", "status", "error", "reason", "render_failed").increment();
            traceEvent(traceRequestId, "CAD_RENDER_FAILED", result.getMessage());
            return result;
        }
    }

    private byte[] generateCadThumbnail(InputStream content, Document document) throws IOException {
        if (!cadRenderEndpointRegistry.isCadPreviewEnabled() || !cadRenderEndpointRegistry.isConfigured()) {
            return generateDefaultThumbnail("cad");
        }
        try {
            CadRenderResult renderResult = renderCadToPng(content, document, null);
            if (renderResult.retryNeeded() || renderResult.pngBytes() == null || renderResult.pngBytes().length == 0) {
                return generateDefaultThumbnail("cad");
            }
            try (ByteArrayInputStream pngStream = new ByteArrayInputStream(renderResult.pngBytes())) {
                return generateImageThumbnail(pngStream);
            }
        } catch (Exception e) {
            log.warn("CAD thumbnail generation failed, using default thumbnail", e);
            return generateDefaultThumbnail("cad");
        }
    }

    private CadRenderResult renderCadToPng(InputStream content, Document document, String traceRequestId) throws IOException {
        byte[] cadBytes = content.readAllBytes();
        if (cadBytes.length == 0) {
            traceEvent(traceRequestId, "CAD_EMPTY", "CAD content is empty");
            throw new IOException("CAD content is empty");
        }
        String fileName = document.getName() != null ? document.getName() : "drawing.dwg";
        String contentType = normalizeMimeType(document.getMimeType(), document.getName());

        CadRenderRequestResult renderResponse = requestCadRender(cadBytes, fileName, contentType, traceRequestId);
        if (renderResponse.retryNeeded()) {
            return new CadRenderResult(null, 0, 0, true, renderResponse.retryHint());
        }

        byte[] pngBytes = renderResponse.pngBytes();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        if (image == null) {
            throw new IOException("CAD render returned invalid image");
        }
        return new CadRenderResult(pngBytes, image.getWidth(), image.getHeight(), false, null);
    }

    private CadRenderRequestResult requestCadRender(
        byte[] cadBytes,
        String fileName,
        String contentType,
        String traceRequestId
    ) throws IOException {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(cadRenderTimeoutMs);
        requestFactory.setReadTimeout(cadRenderTimeoutMs);

        RestTemplate restTemplate = new RestTemplate(requestFactory);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        ByteArrayResource resource = new ByteArrayResource(cadBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        if (contentType != null && !contentType.isBlank()) {
            partHeaders.setContentType(MediaType.parseMediaType(contentType));
        }
        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, partHeaders);
        body.add("file", filePart);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(List.of(MediaType.IMAGE_PNG));
        if (cadRenderAuthToken != null && !cadRenderAuthToken.isBlank()) {
            headers.setBearerAuth(cadRenderAuthToken);
        }

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        IOException firstException = null;
        boolean attempted = false;
        int skippedByCircuit = 0;
        for (String renderUrl : cadRenderEndpointRegistry.resolveEndpoints()) {
            CadRenderFailoverTracker.EndpointAttemptDecision decision = cadRenderFailoverTracker.beforeAttempt(renderUrl);
            if (!decision.allowed()) {
                skippedByCircuit += 1;
                traceEvent(
                    traceRequestId,
                    "CAD_ENDPOINT_SKIPPED",
                    renderUrl + " :: " + decision.state() + (decision.reopenAt() != null ? " until " + decision.reopenAt() : "")
                );
                continue;
            }
            attempted = true;
            if (decision.halfOpen()) {
                traceEvent(traceRequestId, "CAD_ENDPOINT_HALF_OPEN", renderUrl);
            }
            try {
                traceEvent(traceRequestId, "CAD_ENDPOINT_ATTEMPT", renderUrl);
                ResponseEntity<byte[]> response = restTemplate.exchange(renderUrl, HttpMethod.POST, request, byte[].class);

                boolean retryNeeded = isRetryNeededHeader(response.getHeaders());
                if (retryNeeded) {
                    String retryHint = response.getHeaders().getFirst(X_ECM_RETRY_NEEDED);
                    if (retryHint == null || retryHint.isBlank()) {
                        retryHint = response.getHeaders().getFirst(X_ALFRESCO_RETRY_NEEDED);
                    }
                    if (retryHint == null || retryHint.isBlank() || isTruthyRetryHeaderValue(retryHint)) {
                        retryHint = "CAD render service requested retry";
                    }
                    cadRenderFailoverTracker.recordFailure(renderUrl, retryHint);
                    traceEvent(traceRequestId, "CAD_ENDPOINT_RETRY_HINT", renderUrl + " :: " + retryHint);
                    return new CadRenderRequestResult(null, true, retryHint);
                }

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    throw new IOException("CAD render service returned " + response.getStatusCode());
                }
                cadRenderFailoverTracker.recordSuccess(renderUrl);
                traceEvent(traceRequestId, "CAD_ENDPOINT_SUCCESS", renderUrl);
                return new CadRenderRequestResult(response.getBody(), false, null);
            } catch (Exception ex) {
                IOException ioEx = ex instanceof IOException
                    ? (IOException) ex
                    : new IOException("CAD render call failed: " + ex.getMessage(), ex);
                if (firstException == null) {
                    firstException = ioEx;
                }
                cadRenderFailoverTracker.recordFailure(renderUrl, ioEx.getMessage());
                log.warn("CAD render endpoint attempt failed url={} reason={}", renderUrl, ioEx.getMessage());
                traceEvent(traceRequestId, "CAD_ENDPOINT_FAILURE", renderUrl + " :: " + ioEx.getMessage());
            }
        }

        if (!attempted && skippedByCircuit > 0) {
            throw new IOException("CAD render circuit is open for all endpoints");
        }
        if (firstException != null) {
            throw firstException;
        }
        throw new IOException("CAD render service not configured");
    }
    
    private byte[] generateImageThumbnail(InputStream content) throws IOException {
        BufferedImage image = ImageIO.read(content);
        if (image == null) {
            throw new IOException("Failed to read image");
        }
        
        BufferedImage thumbnail = Thumbnails.of(image)
            .size(thumbnailWidth, thumbnailHeight)
            .keepAspectRatio(true)
            .asBufferedImage();
        
        return imageToBytes(thumbnail, "png");
    }
    
    private byte[] generatePdfThumbnail(InputStream content) throws IOException {
        byte[] pdfBytes = content.readAllBytes();
        if (pdfBytes.length == 0) {
            return generateDefaultThumbnail("application/pdf");
        }
        try (PDDocument pdf = PDDocument.load(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(pdf);
            BufferedImage image = renderer.renderImageWithDPI(0, 72);
            
            BufferedImage thumbnail = Thumbnails.of(image)
                .size(thumbnailWidth, thumbnailHeight)
                .keepAspectRatio(true)
                .asBufferedImage();
            
            return imageToBytes(thumbnail, "png");
        }
    }
    
    private byte[] generateOfficeThumbnail(InputStream content, String mimeType) 
            throws IOException {
        // For Office documents, generate a generic thumbnail with file type
        return generateDefaultThumbnail(mimeType);
    }
    
    private byte[] generateDefaultThumbnail(String mimeType) throws IOException {
        BufferedImage image = new BufferedImage(thumbnailWidth, thumbnailHeight, 
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        
        // Draw background
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, thumbnailWidth, thumbnailHeight);
        
        // Draw file type
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font("Arial", Font.BOLD, 20));
        
        String fileType = getFileTypeFromMimeType(mimeType);
        FontMetrics metrics = g.getFontMetrics();
        int x = (thumbnailWidth - metrics.stringWidth(fileType)) / 2;
        int y = ((thumbnailHeight - metrics.getHeight()) / 2) + metrics.getAscent();
        g.drawString(fileType, x, y);
        
        g.dispose();
        
        return imageToBytes(image, "png");
    }
    
    private boolean isOfficeDocument(String mimeType) {
        return mimeType.contains("officedocument") || 
               mimeType.contains("msword") ||
               mimeType.contains("ms-excel") ||
               mimeType.contains("ms-powerpoint") ||
               mimeType.contains("opendocument");
    }
    
    private boolean isCadDocument(String mimeType, String fileName) {
        if (mimeType != null && (
            mimeType.contains("dwg") ||
            mimeType.contains("dxf") ||
            mimeType.contains("autocad") ||
            mimeType.contains("cad")
        )) {
            return true;
        }
        String name = fileName == null ? "" : fileName.toLowerCase();
        return name.endsWith(".dwg") || name.endsWith(".dxf");
    }

    private String normalizeMimeType(String mimeType, String fileName) {
        String normalized = mimeType == null ? "" : mimeType.trim().toLowerCase();
        int separator = normalized.indexOf(';');
        if (separator >= 0) {
            normalized = normalized.substring(0, separator).trim();
        }
        if (isGenericMimeType(normalized)) {
            String inferred = inferMimeTypeFromName(fileName);
            if (inferred != null && !inferred.isBlank()) {
                return inferred;
            }
        }
        return normalized;
    }

    private boolean isGenericMimeType(String mimeType) {
        return mimeType == null
            || mimeType.isBlank()
            || mimeType.equals("application/octet-stream")
            || mimeType.equals("binary/octet-stream")
            || mimeType.equals("application/x-empty");
    }

    private String inferMimeTypeFromName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        String normalized = fileName.toLowerCase();
        if (normalized.endsWith(".pdf")) return "application/pdf";
        if (normalized.endsWith(".png")) return "image/png";
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) return "image/jpeg";
        if (normalized.endsWith(".gif")) return "image/gif";
        if (normalized.endsWith(".webp")) return "image/webp";
        if (normalized.endsWith(".txt") || normalized.endsWith(".md") || normalized.endsWith(".csv")) {
            return "text/plain";
        }
        if (normalized.endsWith(".doc")) return "application/msword";
        if (normalized.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (normalized.endsWith(".xls")) return "application/vnd.ms-excel";
        if (normalized.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (normalized.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (normalized.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }
        if (normalized.endsWith(".odt")) return "application/vnd.oasis.opendocument.text";
        if (normalized.endsWith(".ods")) return "application/vnd.oasis.opendocument.spreadsheet";
        if (normalized.endsWith(".odp")) return "application/vnd.oasis.opendocument.presentation";
        if (normalized.endsWith(".rtf")) return "application/rtf";
        if (normalized.endsWith(".dwg")) return "application/dwg";
        if (normalized.endsWith(".dxf")) return "application/dxf";
        return "";
    }
    
    private String getFileTypeFromMimeType(String mimeType) {
        if (mimeType.contains("pdf")) return "PDF";
        if (mimeType.contains("word")) return "DOC";
        if (mimeType.contains("excel")) return "XLS";
        if (mimeType.contains("powerpoint")) return "PPT";
        if (mimeType.contains("text")) return "TXT";
        if (mimeType.contains("image")) return "IMG";
        if (mimeType.contains("video")) return "VID";
        if (mimeType.contains("audio")) return "AUD";
        if (mimeType.contains("zip") || mimeType.contains("archive")) return "ZIP";
        if (mimeType.contains("cad") || mimeType.contains("dwg") || mimeType.contains("dxf")) return "CAD";
        return "FILE";
    }
    
    private byte[] imageToBytes(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }

    private void updatePreviewStatus(Document document, PreviewStatus status, String failureReason, String previewContentHash) {
        if (document == null || status == null) {
            return;
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Document target = documentRepository.findById(document.getId()).orElse(document);
                LocalDateTime now = LocalDateTime.now();
                target.setPreviewStatus(status);
                target.setPreviewFailureReason(failureReason);
                target.setPreviewLastUpdated(now);
                target.setPreviewAvailable(status == PreviewStatus.READY);
                target.setPreviewContentHash(previewContentHash);
                if (status == PreviewStatus.READY) {
                    clearFailureLedger(target);
                } else if (status == PreviewStatus.FAILED || status == PreviewStatus.UNSUPPORTED) {
                    int failureCount = target.getPreviewFailureCount() != null ? target.getPreviewFailureCount() : 0;
                    target.setPreviewFailureCount(failureCount + 1);
                    target.setPreviewFailedAt(now);
                    target.setPreviewLastFailureReason(normalizeFailureReason(failureReason));
                    target.setPreviewFailureContentHash(normalizeContentHash(target.getContentHash()));
                }
                documentRepository.save(target);
                syncRenditionResources(target);
                try {
                    searchIndexService.updateDocument(target);
                } catch (Exception indexError) {
                    log.warn("Failed to reindex preview status for {}: {}", target.getId(), indexError.getMessage());
                }
                return;
            } catch (OptimisticLockingFailureException e) {
                if (attempt == 0) {
                    log.warn("Preview status update raced for {}. Retrying.", document.getId());
                    continue;
                }
                log.warn("Failed to persist preview status for {} after retry: {}", document.getId(), e.getMessage());
            } catch (Exception e) {
                log.warn("Failed to persist preview status for {}: {}", document.getId(), e.getMessage());
            }
            return;
        }
    }

    private void applyPreviewOutcome(Document document, PreviewResult result) {
        if (result == null) {
            return;
        }
        PreviewStatus status = PreviewStatus.READY;
        String failureReason = null;
        String failureCategory = null;

        if (!result.isSupported()) {
            failureReason = result.getMessage();
            // Classify as if it were FAILED first, then potentially upgrade the status to UNSUPPORTED.
            failureCategory = PreviewFailureClassifier.classify(
                PreviewStatus.FAILED.name(),
                result.getMimeType(),
                failureReason
            );
            status = PreviewFailureClassifier.CATEGORY_UNSUPPORTED.equalsIgnoreCase(failureCategory)
                ? PreviewStatus.UNSUPPORTED
                : PreviewStatus.FAILED;
        }

        recordPreviewMetrics(result.getMimeType(), status, failureCategory);

        if (status == PreviewStatus.UNSUPPORTED) {
            String categoryTag = failureCategory == null || failureCategory.isBlank()
                ? "NONE"
                : failureCategory.trim().toUpperCase(Locale.ROOT);
            log.info(
                "Preview generation outcome: documentId={} status={} category={} mimeType={} reason={}",
                document != null ? document.getId() : null,
                status.name(),
                categoryTag,
                result.getMimeType(),
                failureReason
            );
        } else if (status == PreviewStatus.FAILED) {
            String categoryTag = failureCategory == null || failureCategory.isBlank()
                ? "NONE"
                : failureCategory.trim().toUpperCase(Locale.ROOT);
            log.warn(
                "Preview generation outcome: documentId={} status={} category={} mimeType={} reason={}",
                document != null ? document.getId() : null,
                status.name(),
                categoryTag,
                result.getMimeType(),
                failureReason
            );
        }

        if (document != null) {
            if (result.getPageCount() > 0) {
                document.setPageCount(result.getPageCount());
            } else if (result.getPages() != null && !result.getPages().isEmpty()) {
                document.setPageCount(result.getPages().size());
            }
            String previewContentHash = status == PreviewStatus.READY ? normalizeContentHash(document.getContentHash()) : null;
            updatePreviewStatus(document, status, failureReason, previewContentHash);
        }

        result.setStatus(status.name());
        result.setFailureReason(failureReason);
        result.setFailureCategory(failureCategory);
    }

    private void recordPreviewMetrics(String mimeType, PreviewStatus status, String failureCategory) {
        String normalizedMime = mimeType == null || mimeType.isBlank()
            ? "unknown"
            : mimeType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (normalizedMime.isBlank()) {
            normalizedMime = "unknown";
        }
        String categoryTag = failureCategory == null || failureCategory.isBlank()
            ? "NONE"
            : failureCategory.trim().toUpperCase(Locale.ROOT);

        meterRegistry.counter(
            "preview_generation_total",
            "status", status.name(),
            "category", categoryTag,
            "mimeType", normalizedMime
        ).increment();
    }

    private String normalizeContentHash(String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            return null;
        }
        return contentHash.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeFailureReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.replaceAll("\\s+", " ").trim();
    }

    private static void clearFailureLedger(Document document) {
        if (document == null) {
            return;
        }
        document.setPreviewFailureCount(0);
        document.setPreviewFailedAt(null);
        document.setPreviewLastFailureReason(null);
        document.setPreviewFailureContentHash(null);
    }

    public record PreviewReadiness(
        String state,
        boolean valid,
        boolean staleHash,
        String reason
    ) {
        public boolean zeroSource() {
            return "ZERO_SOURCE".equals(state);
        }
    }

    public record PreviewRepairResult(
        UUID documentId,
        String previousStatus,
        boolean invalidated,
        String reason,
        String previousPreviewContentHash,
        String currentContentHash
    ) {
    }

    private boolean isRetryNeededHeader(HttpHeaders headers) {
        if (headers == null) {
            return false;
        }
        if (headers.containsKey(X_ECM_RETRY_NEEDED)) {
            String ecmHeader = headers.getFirst(X_ECM_RETRY_NEEDED);
            return !isExplicitlyFalseRetryHeaderValue(ecmHeader);
        }
        if (headers.containsKey(X_ALFRESCO_RETRY_NEEDED)) {
            String alfrescoHeader = headers.getFirst(X_ALFRESCO_RETRY_NEEDED);
            return !isExplicitlyFalseRetryHeaderValue(alfrescoHeader);
        }
        return false;
    }

    private boolean isTruthyRetryHeaderValue(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("true")
            || normalized.equals("1")
            || normalized.equals("yes")
            || normalized.equals("retry")
            || normalized.equals("needed");
    }

    private boolean isExplicitlyFalseRetryHeaderValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("false")
            || normalized.equals("0")
            || normalized.equals("no")
            || normalized.equals("off");
    }

    private record CadRenderRequestResult(byte[] pngBytes, boolean retryNeeded, String retryHint) {}

    private record CadRenderResult(byte[] pngBytes, int width, int height, boolean retryNeeded, String retryHint) {}

    private void traceEvent(String requestId, String stage, String message) {
        previewTransformTraceBuffer.append(requestId, stage, message);
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    private void syncRenditionResources(Document document) {
        try {
            renditionResourceSyncService.syncDocument(document);
        } catch (Exception syncError) {
            log.warn("Failed to sync rendition resources for {}: {}", document.getId(), syncError.getMessage());
        }
    }
}
