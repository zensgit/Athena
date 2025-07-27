package com.ecm.core.preview;

import com.ecm.core.entity.Document;
import com.ecm.core.service.ContentService;
import com.ecm.core.service.SecurityService;
import com.ecm.core.entity.PermissionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
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
    
    @Value("${ecm.preview.cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${ecm.preview.max-pages:50}")
    private int maxPages;
    
    @Value("${ecm.preview.thumbnail.width:200}")
    private int thumbnailWidth;
    
    @Value("${ecm.preview.thumbnail.height:200}")
    private int thumbnailHeight;
    
    @Value("${ecm.storage.temp-path}")
    private String tempPath;
    
    @Cacheable(value = "previews", condition = "#root.target.cacheEnabled")
    public PreviewResult generatePreview(Document document) throws IOException {
        // Check permissions
        if (!securityService.hasPermission(document, PermissionType.READ)) {
            throw new SecurityException("No permission to preview document");
        }
        
        String mimeType = document.getMimeType();
        PreviewResult result = new PreviewResult();
        result.setDocumentId(document.getId());
        result.setMimeType(mimeType);
        
        try (InputStream content = contentService.getContent(document.getContentId())) {
            if (mimeType.startsWith("image/")) {
                result = generateImagePreview(content, document);
            } else if (mimeType.equals("application/pdf")) {
                result = generatePdfPreview(content, document);
            } else if (isOfficeDocument(mimeType)) {
                result = generateOfficePreview(content, document, mimeType);
            } else if (mimeType.startsWith("text/")) {
                result = generateTextPreview(content, document);
            } else if (isCadDocument(mimeType)) {
                result = generateCadPreview(content, document);
            } else {
                result.setSupported(false);
                result.setMessage("Preview not supported for mime type: " + mimeType);
            }
        } catch (Exception e) {
            log.error("Error generating preview for document: {}", document.getId(), e);
            result.setSupported(false);
            result.setMessage("Error generating preview: " + e.getMessage());
        }
        
        return result;
    }
    
    @Cacheable(value = "thumbnails", condition = "#root.target.cacheEnabled")
    public byte[] generateThumbnail(Document document) throws IOException {
        // Check permissions
        if (!securityService.hasPermission(document, PermissionType.READ)) {
            throw new SecurityException("No permission to view thumbnail");
        }
        
        String mimeType = document.getMimeType();
        
        try (InputStream content = contentService.getContent(document.getContentId())) {
            if (mimeType.startsWith("image/")) {
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
        result.setSupported(true);
        
        List<PreviewPage> pages = new ArrayList<>();
        
        try (PDDocument pdf = PDDocument.load(content)) {
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
        
        // CAD preview would require specialized libraries
        // For now, we'll mark it as unsupported
        result.setSupported(false);
        result.setMessage("CAD preview requires specialized processing");
        
        return result;
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
        try (PDDocument pdf = PDDocument.load(content)) {
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
    
    private boolean isCadDocument(String mimeType) {
        return mimeType.contains("dwg") || 
               mimeType.contains("dxf") ||
               mimeType.contains("autocad") ||
               mimeType.contains("cad");
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
        return "FILE";
    }
    
    private byte[] imageToBytes(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }
}