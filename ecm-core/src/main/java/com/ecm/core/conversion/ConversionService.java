package com.ecm.core.conversion;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.PermissionType;
import com.ecm.core.service.ContentService;
import com.ecm.core.service.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.document.DefaultDocumentFormatRegistry;
import org.jodconverter.core.document.DocumentFormat;
import org.jodconverter.core.office.OfficeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversionService {
    
    private final ContentService contentService;
    private final SecurityService securityService;
    private final DocumentConverter documentConverter;
    
    @Value("${ecm.conversion.output-formats}")
    private List<String> supportedOutputFormats;
    
    @Value("${ecm.conversion.max-file-size}")
    private long maxFileSize;
    
    @Value("${ecm.storage.temp-path}")
    private String tempPath;
    
    public ConversionResult convertDocument(Document document, String targetFormat) throws IOException {
        // Check permissions
        if (!securityService.hasPermission(document, PermissionType.READ)) {
            throw new SecurityException("No permission to convert document");
        }
        
        // Check file size
        if (document.getFileSize() > maxFileSize) {
            throw new IllegalArgumentException("File too large for conversion: " + document.getFileSize());
        }
        
        // Check if format is supported
        if (!supportedOutputFormats.contains(targetFormat.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported output format: " + targetFormat);
        }
        
        ConversionResult result = new ConversionResult();
        result.setSourceDocumentId(document.getId());
        result.setSourceFormat(document.getMimeType());
        result.setTargetFormat(targetFormat);
        
        try {
            byte[] convertedContent = performConversion(document, targetFormat);
            result.setContent(convertedContent);
            result.setSuccess(true);
            result.setFileSize(convertedContent.length);
            
            // Detect mime type of converted content
            String mimeType = detectMimeType(convertedContent, targetFormat);
            result.setMimeType(mimeType);
            
        } catch (Exception e) {
            log.error("Conversion failed for document: {}", document.getId(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }
        
        return result;
    }
    
    public List<String> getSupportedConversions(String sourceMimeType) {
        List<String> conversions = new ArrayList<>();
        
        if (sourceMimeType.startsWith("image/")) {
            conversions.addAll(Arrays.asList("pdf", "png", "jpg", "tiff"));
        } else if (sourceMimeType.equals("application/pdf")) {
            conversions.addAll(Arrays.asList("txt", "html", "png", "jpg", "docx"));
        } else if (isOfficeDocument(sourceMimeType)) {
            conversions.addAll(Arrays.asList("pdf", "html", "txt", "rtf"));
        } else if (sourceMimeType.startsWith("text/")) {
            conversions.addAll(Arrays.asList("pdf", "html", "docx"));
        }
        
        // Filter by configured supported formats
        conversions.retainAll(supportedOutputFormats);
        
        return conversions;
    }
    
    public ConversionResult convertToText(Document document) throws IOException {
        // Special method for text extraction
        ConversionResult result = new ConversionResult();
        result.setSourceDocumentId(document.getId());
        result.setTargetFormat("txt");
        
        try (InputStream content = contentService.getContent(document.getContentId())) {
            String text = extractText(content, document.getMimeType());
            result.setContent(text.getBytes("UTF-8"));
            result.setSuccess(true);
            result.setMimeType("text/plain");
            result.setFileSize(result.getContent().length);
        } catch (Exception e) {
            log.error("Text extraction failed for document: {}", document.getId(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }
        
        return result;
    }
    
    private byte[] performConversion(Document document, String targetFormat) throws IOException {
        String sourceMimeType = document.getMimeType();
        
        // Use appropriate conversion method based on source and target
        if (sourceMimeType.startsWith("image/") && targetFormat.equals("pdf")) {
            return convertImageToPdf(document);
        } else if (sourceMimeType.equals("application/pdf") && targetFormat.equals("txt")) {
            return extractTextFromPdf(document);
        } else if (sourceMimeType.equals("application/pdf") && isImageFormat(targetFormat)) {
            return convertPdfToImage(document, targetFormat);
        } else if (isOfficeDocument(sourceMimeType)) {
            return convertOfficeDocument(document, targetFormat);
        } else if (sourceMimeType.startsWith("text/") && targetFormat.equals("pdf")) {
            return convertTextToPdf(document);
        } else {
            // Use JODConverter for other conversions
            return convertWithJodConverter(document, targetFormat);
        }
    }
    
    private byte[] convertImageToPdf(Document document) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (InputStream content = contentService.getContent(document.getContentId());
             PDDocument pdf = new PDDocument()) {
            
            BufferedImage image = ImageIO.read(content);
            if (image == null) {
                throw new IOException("Failed to read image");
            }
            
            // Create PDF page with image dimensions
            PDPage page = new PDPage(new PDRectangle(image.getWidth(), image.getHeight()));
            pdf.addPage(page);
            
            // Add image to page
            PDImageXObject pdImage = PDImageXObject.createFromByteArray(
                pdf, imageToBytes(image), document.getName());
            
            try (PDPageContentStream contentStream = new PDPageContentStream(pdf, page)) {
                contentStream.drawImage(pdImage, 0, 0);
            }
            
            pdf.save(baos);
        }
        
        return baos.toByteArray();
    }
    
    private byte[] extractTextFromPdf(Document document) throws IOException {
        try (InputStream content = contentService.getContent(document.getContentId());
             PDDocument pdf = PDDocument.load(content)) {
            
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(pdf);
            
            return text.getBytes("UTF-8");
        }
    }
    
    private byte[] convertPdfToImage(Document document, String format) throws IOException {
        // This would convert first page of PDF to image
        // Full implementation would handle multiple pages
        try (InputStream content = contentService.getContent(document.getContentId());
             PDDocument pdf = PDDocument.load(content)) {
            
            org.apache.pdfbox.rendering.PDFRenderer renderer = 
                new org.apache.pdfbox.rendering.PDFRenderer(pdf);
            BufferedImage image = renderer.renderImageWithDPI(0, 300);
            
            return imageToBytes(image, format);
        }
    }
    
    private byte[] convertOfficeDocument(Document document, String targetFormat) 
            throws IOException {
        // Use JODConverter for Office conversions
        return convertWithJodConverter(document, targetFormat);
    }
    
    private byte[] convertTextToPdf(Document document) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (InputStream content = contentService.getContent(document.getContentId());
             PDDocument pdf = new PDDocument()) {
            
            String text = new String(content.readAllBytes(), "UTF-8");
            
            PDPage page = new PDPage();
            pdf.addPage(page);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(pdf, page)) {
                contentStream.beginText();
                contentStream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12);
                contentStream.setLeading(14.5f);
                contentStream.newLineAtOffset(25, 725);
                
                // Split text into lines
                String[] lines = text.split("\n");
                for (String line : lines) {
                    contentStream.showText(line);
                    contentStream.newLine();
                }
                
                contentStream.endText();
            }
            
            pdf.save(baos);
        }
        
        return baos.toByteArray();
    }
    
    private byte[] convertWithJodConverter(Document document, String targetFormat) 
            throws IOException {
        File inputFile = null;
        File outputFile = null;
        
        try {
            // Create temp files
            inputFile = File.createTempFile("convert_", "_" + document.getName(), 
                new File(tempPath));
            outputFile = File.createTempFile("output_", "." + targetFormat, 
                new File(tempPath));
            
            // Write content to input file
            try (InputStream is = contentService.getContent(document.getContentId());
                 OutputStream os = new FileOutputStream(inputFile)) {
                is.transferTo(os);
            }
            
            // Perform conversion
            DocumentFormat sourceFormat = DefaultDocumentFormatRegistry.getFormatByExtension(
                getExtension(document.getName()));
            DocumentFormat targetFormatObj = DefaultDocumentFormatRegistry.getFormatByExtension(
                targetFormat);
            
            documentConverter.convert(inputFile).as(sourceFormat)
                .to(outputFile).as(targetFormatObj).execute();
            
            // Read converted content
            return Files.readAllBytes(outputFile.toPath());
            
        } catch (OfficeException e) {
            throw new IOException("JODConverter conversion failed", e);
        } finally {
            // Clean up temp files
            if (inputFile != null) inputFile.delete();
            if (outputFile != null) outputFile.delete();
        }
    }
    
    private String extractText(InputStream content, String mimeType) throws IOException {
        if (mimeType.equals("application/pdf")) {
            try (PDDocument pdf = PDDocument.load(content)) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(pdf);
            }
        } else if (mimeType.contains("word")) {
            try (XWPFDocument doc = new XWPFDocument(content)) {
                StringBuilder text = new StringBuilder();
                for (XWPFParagraph para : doc.getParagraphs()) {
                    text.append(para.getText()).append("\n");
                }
                return text.toString();
            }
        } else if (mimeType.startsWith("text/")) {
            return new String(content.readAllBytes(), "UTF-8");
        } else {
            throw new UnsupportedOperationException("Text extraction not supported for: " + mimeType);
        }
    }
    
    private boolean isOfficeDocument(String mimeType) {
        return mimeType.contains("officedocument") || 
               mimeType.contains("msword") ||
               mimeType.contains("ms-excel") ||
               mimeType.contains("ms-powerpoint") ||
               mimeType.contains("opendocument");
    }
    
    private boolean isImageFormat(String format) {
        return Arrays.asList("png", "jpg", "jpeg", "gif", "tiff", "bmp")
            .contains(format.toLowerCase());
    }
    
    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "";
    }
    
    private String detectMimeType(byte[] content, String format) {
        // Simple mime type detection based on format
        switch (format.toLowerCase()) {
            case "pdf": return "application/pdf";
            case "txt": return "text/plain";
            case "html": return "text/html";
            case "png": return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc": return "application/msword";
            case "rtf": return "application/rtf";
            default: return "application/octet-stream";
        }
    }
    
    private byte[] imageToBytes(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }
}