package com.ecm.core.pipeline.processor;

import com.ecm.core.pipeline.DocumentContext;
import com.ecm.core.pipeline.DocumentProcessor;
import com.ecm.core.pipeline.ProcessingResult;
import com.ecm.core.service.ContentService;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Barcode Extraction Processor
 * 
 * Scans document (PDF first page or Images) for Barcodes/QR Codes.
 * Adds detected values to metadata.
 * 
 * Execution Order: 150 (After content storage, before text extraction/persistence)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BarcodeProcessor implements DocumentProcessor {

    private final ContentService contentService;

    private final MultiFormatReader reader = new MultiFormatReader();

    @Override
    public ProcessingResult process(DocumentContext context) {
        String mimeType = context.getMimeType();

        if (context.getContentId() == null || mimeType == null) {
            return ProcessingResult.skipped("Content or mime type not available for barcode scan");
        }

        List<String> barcodes = new ArrayList<>();

        try (InputStream contentStream = contentService.getContent(context.getContentId())) {
            if ("application/pdf".equals(mimeType)) {
                barcodes.addAll(scanPdf(contentStream));
            } else if (mimeType.startsWith("image/")) {
                barcodes.addAll(scanImage(contentStream));
            }

            if (!barcodes.isEmpty()) {
                context.addMetadata("barcodes", barcodes.toString());
                // Add distinct tags for found barcodes
                List<String> tags = new ArrayList<>();
                for (String code : barcodes) {
                    if (code.startsWith("TAG:")) { // Custom convention e.g. TAG:INVOICE
                        tags.add(code.substring(4));
                    }
                }
                if (!tags.isEmpty()) {
                    context.setSuggestedTags(tags);
                }
                
                log.info("Detected {} barcodes for content {}", barcodes.size(), context.getContentId());
                return ProcessingResult.success()
                    .withData("barcodesFound", true)
                    .withData("barcodeCount", barcodes.size())
                    .withData("values", barcodes);
            }

            return ProcessingResult.success().withData("barcodesFound", false);

        } catch (Exception e) {
            log.warn("Barcode scanning failed for content {}: {}", context.getContentId(), e.getMessage());
            // Don't fail the pipeline for barcode errors
            return ProcessingResult.success().withData("error", e.getMessage());
        }
    }

    private List<String> scanPdf(InputStream input) throws Exception {
        // Reset stream if possible, or assume context handles new stream (ideally context.getContentStream() returns fresh stream)
        // For now, we assume input is at start.
        
        List<String> results = new ArrayList<>();
        try (PDDocument document = PDDocument.load(input)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            // Scan only first page for performance
            if (document.getNumberOfPages() > 0) {
                BufferedImage image = pdfRenderer.renderImageWithDPI(0, 150); // 150 DPI is usually enough
                results.addAll(decode(image));
            }
        }
        return results;
    }

    private List<String> scanImage(InputStream input) throws Exception {
        BufferedImage image = ImageIO.read(input);
        if (image == null) return Collections.emptyList();
        return decode(image);
    }

    private List<String> decode(BufferedImage image) {
        List<String> results = new ArrayList<>();
        try {
            LuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            
            // Try to find multiple barcodes
            GenericMultipleBarcodeReader multiReader = new GenericMultipleBarcodeReader(reader);
            Result[] findings = multiReader.decodeMultiple(bitmap);
            
            if (findings != null) {
                for (Result result : findings) {
                    if (!results.contains(result.getText())) {
                        results.add(result.getText());
                    }
                }
            }
        } catch (NotFoundException e) {
            // No barcode found, normal case
        } catch (Exception e) {
            log.debug("Barcode decode error: {}", e.getMessage());
        }
        return results;
    }

    @Override
    public int getOrder() {
        return 150;
    }

    @Override
    public boolean supports(DocumentContext context) {
        String mime = context.getMimeType();
        return "application/pdf".equals(mime) || (mime != null && mime.startsWith("image/"));
    }
}
