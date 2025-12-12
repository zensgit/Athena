package com.ecm.core.pipeline.processor;

import com.ecm.core.pipeline.DocumentContext;
import com.ecm.core.pipeline.DocumentProcessor;
import com.ecm.core.pipeline.ProcessingResult;
import com.ecm.core.service.ContentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * Tika Text Extractor Processor (Order: 200)
 *
 * Extracts text content and metadata from documents using Apache Tika.
 * Supports PDF, Office documents, text files, and many other formats.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TikaTextExtractor implements DocumentProcessor {

    private final ContentService contentService;

    /** Maximum characters to extract (default: 10MB of text) */
    @Value("${ecm.tika.max-text-length:10485760}")
    private int maxTextLength;

    /** MIME types to skip text extraction */
    private static final Set<String> SKIP_MIME_TYPES = Set.of(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/bmp",
        "image/tiff",
        "video/mp4",
        "video/avi",
        "audio/mpeg",
        "audio/wav",
        "application/zip",
        "application/x-rar-compressed",
        "application/x-7z-compressed"
    );

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public boolean supports(DocumentContext context) {
        String mimeType = context.getMimeType();
        if (mimeType == null) {
            return true; // Will be determined after storage
        }
        return !SKIP_MIME_TYPES.contains(mimeType);
    }

    @Override
    public ProcessingResult process(DocumentContext context) {
        long startTime = System.currentTimeMillis();

        // Skip if content not stored yet
        if (context.getContentId() == null) {
            return ProcessingResult.skipped("Content not stored yet");
        }

        // Skip unsupported MIME types
        if (SKIP_MIME_TYPES.contains(context.getMimeType())) {
            log.debug("Skipping text extraction for MIME type: {}", context.getMimeType());
            return ProcessingResult.skipped("MIME type not supported for text extraction");
        }

        try (InputStream inputStream = contentService.getContent(context.getContentId())) {
            // Configure Tika parser
            Parser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(maxTextLength);
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, context.getOriginalFilename());

            ParseContext parseContext = new ParseContext();
            parseContext.set(Parser.class, parser);

            // Parse content
            parser.parse(inputStream, handler, metadata, parseContext);

            // Extract text
            String extractedText = handler.toString();
            if (extractedText != null && !extractedText.trim().isEmpty()) {
                context.setExtractedText(extractedText.trim());
            }

            // Extract metadata
            extractMetadata(metadata, context);

            long processingTime = System.currentTimeMillis() - startTime;
            int textLength = extractedText != null ? extractedText.length() : 0;

            log.info("Extracted {} chars from {} in {}ms",
                textLength, context.getOriginalFilename(), processingTime);

            return ProcessingResult.builder()
                .status(ProcessingResult.Status.SUCCESS)
                .processingTimeMs(processingTime)
                .itemsProcessed(textLength)
                .message("Extracted " + textLength + " characters")
                .build();

        } catch (IOException e) {
            log.error("IO error during text extraction: {}", e.getMessage());
            context.addError(getName(), "IO error: " + e.getMessage());
            return ProcessingResult.failed("IO error: " + e.getMessage());

        } catch (SAXException | TikaException e) {
            log.warn("Tika parsing error for {}: {}", context.getOriginalFilename(), e.getMessage());
            context.addError(getName(), "Parse error: " + e.getMessage());
            // Non-fatal - continue pipeline even if text extraction fails
            return ProcessingResult.failed("Parse error: " + e.getMessage());
        }
    }

    private void extractMetadata(Metadata metadata, DocumentContext context) {
        // Standard metadata fields
        extractIfPresent(metadata, TikaCoreProperties.TITLE, "title", context);
        extractIfPresent(metadata, TikaCoreProperties.CREATOR, "author", context);
        extractIfPresent(metadata, TikaCoreProperties.CREATED, "createdDate", context);
        extractIfPresent(metadata, TikaCoreProperties.MODIFIED, "modifiedDate", context);
        extractIfPresent(metadata, TikaCoreProperties.DESCRIPTION, "description", context);

        // Document-specific metadata
        String pageCount = metadata.get("xmpTPg:NPages");
        if (pageCount == null) {
            pageCount = metadata.get("meta:page-count");
        }
        if (pageCount != null) {
            context.addMetadata("pageCount", Integer.parseInt(pageCount));
        }

        String wordCount = metadata.get("meta:word-count");
        if (wordCount != null) {
            context.addMetadata("wordCount", Integer.parseInt(wordCount));
        }

        // Content type from Tika
        String contentType = metadata.get(Metadata.CONTENT_TYPE);
        if (contentType != null && context.getMimeType() == null) {
            context.setMimeType(contentType);
        }
    }

    private void extractIfPresent(Metadata metadata, org.apache.tika.metadata.Property property,
                                   String contextKey, DocumentContext context) {
        String value = metadata.get(property);
        if (value != null && !value.trim().isEmpty()) {
            context.addMetadata(contextKey, value.trim());
        }
    }
}
