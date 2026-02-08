package com.ecm.core.preview;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.search.SearchIndexService;
import com.ecm.core.service.ContentService;
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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreviewService {
    
    private final ContentService contentService;
    private final SecurityService securityService;
    private final MeterRegistry meterRegistry;
    private final DocumentRepository documentRepository;
    private final SearchIndexService searchIndexService;
    
    @Value("${ecm.preview.cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${ecm.preview.max-pages:50}")
    private int maxPages;
    
    @Value("${ecm.preview.thumbnail.width:200}")
    private int thumbnailWidth;
    
    @Value("${ecm.preview.thumbnail.height:200}")
    private int thumbnailHeight;

    @Value("${ecm.preview.cad.enabled:true}")
    private boolean cadPreviewEnabled;

    @Value("${ecm.preview.cad.render-url:}")
    private String cadRenderUrl;

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

        updatePreviewStatus(document, PreviewStatus.PROCESSING, null);
        
        String mimeType = normalizeMimeType(document.getMimeType(), document.getName());
        PreviewResult result = new PreviewResult();
        result.setDocumentId(document.getId());
        result.setMimeType(mimeType);
        
        try (InputStream content = contentService.getContent(document.getContentId())) {
            if (isCadDocument(mimeType, document.getName())) {
                result = generateCadPreview(content, document);
            } else if (mimeType.startsWith("image/")) {
                result = generateImagePreview(content, document);
            } else if (mimeType.equals("application/pdf")) {
                result = generatePdfPreview(content, document);
            } else if (isOfficeDocument(mimeType)) {
                result = generateOfficePreview(content, document, mimeType);
            } else if (mimeType.startsWith("text/")) {
                result = generateTextPreview(content, document);
            } else {
                result.setSupported(false);
                result.setMessage("Preview not supported for mime type: " + mimeType);
            }
        } catch (Exception e) {
            log.error("Error generating preview for document: {}", document.getId(), e);
            result.setSupported(false);
            result.setMessage("Error generating preview: " + e.getMessage());
        }
        
        result.setDocumentId(document.getId());
        result.setMimeType(mimeType);
        applyPreviewOutcome(document, result);
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
    
    private PreviewResult generateCadPreview(InputStream content, Document document) 
            throws IOException {
        PreviewResult result = new PreviewResult();

        if (!cadPreviewEnabled) {
            result.setSupported(false);
            result.setMessage("CAD preview disabled");
            meterRegistry.counter("cad_preview_total", "status", "error", "reason", "disabled").increment();
            return result;
        }
        if (cadRenderUrl == null || cadRenderUrl.isBlank()) {
            result.setSupported(false);
            result.setMessage("CAD preview service not configured");
            meterRegistry.counter("cad_preview_total", "status", "error", "reason", "missing_render_url").increment();
            return result;
        }

        try {
            CadRenderResult renderResult = renderCadToPng(content, document);
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
            return result;
        } catch (Exception e) {
            result.setSupported(false);
            result.setMessage("CAD preview failed: " + e.getMessage());
            log.warn("CAD preview failed for document: {}", document.getId(), e);
            meterRegistry.counter("cad_preview_total", "status", "error", "reason", "render_failed").increment();
            return result;
        }
    }

    private byte[] generateCadThumbnail(InputStream content, Document document) throws IOException {
        if (!cadPreviewEnabled || cadRenderUrl == null || cadRenderUrl.isBlank()) {
            return generateDefaultThumbnail("cad");
        }
        try {
            CadRenderResult renderResult = renderCadToPng(content, document);
            try (ByteArrayInputStream pngStream = new ByteArrayInputStream(renderResult.pngBytes())) {
                return generateImageThumbnail(pngStream);
            }
        } catch (Exception e) {
            log.warn("CAD thumbnail generation failed, using default thumbnail", e);
            return generateDefaultThumbnail("cad");
        }
    }

    private CadRenderResult renderCadToPng(InputStream content, Document document) throws IOException {
        byte[] cadBytes = content.readAllBytes();
        if (cadBytes.length == 0) {
            throw new IOException("CAD content is empty");
        }
        String fileName = document.getName() != null ? document.getName() : "drawing.dwg";
        String contentType = normalizeMimeType(document.getMimeType(), document.getName());

        byte[] pngBytes = requestCadRender(cadBytes, fileName, contentType);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        if (image == null) {
            throw new IOException("CAD render returned invalid image");
        }
        return new CadRenderResult(pngBytes, image.getWidth(), image.getHeight());
    }

    private byte[] requestCadRender(byte[] cadBytes, String fileName, String contentType) throws IOException {
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
        ResponseEntity<byte[]> response = restTemplate.postForEntity(cadRenderUrl, request, byte[].class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IOException("CAD render service returned " + response.getStatusCode());
        }
        return response.getBody();
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

    private void updatePreviewStatus(Document document, PreviewStatus status, String failureReason) {
        if (document == null || status == null) {
            return;
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Document target = documentRepository.findById(document.getId()).orElse(document);
                target.setPreviewStatus(status);
                target.setPreviewFailureReason(failureReason);
                target.setPreviewLastUpdated(LocalDateTime.now());
                target.setPreviewAvailable(status == PreviewStatus.READY);
                documentRepository.save(target);
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
        PreviewStatus status = result.isSupported() ? PreviewStatus.READY : PreviewStatus.FAILED;
        String failureReason = result.isSupported() ? null : result.getMessage();
        String failureCategory = PreviewFailureClassifier.classify(
            status.name(),
            result.getMimeType(),
            failureReason
        );

        if (document != null) {
            if (result.getPageCount() > 0) {
                document.setPageCount(result.getPageCount());
            } else if (result.getPages() != null && !result.getPages().isEmpty()) {
                document.setPageCount(result.getPages().size());
            }
            updatePreviewStatus(document, status, failureReason);
        }

        result.setStatus(status.name());
        result.setFailureReason(failureReason);
        result.setFailureCategory(failureCategory);
    }

    private record CadRenderResult(byte[] pngBytes, int width, int height) {}

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }
}
