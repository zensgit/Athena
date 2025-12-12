package com.ecm.core.pipeline.processor;

import com.ecm.core.pipeline.DocumentContext;
import com.ecm.core.pipeline.ProcessingResult;
import com.ecm.core.service.ContentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TikaTextExtractor processor.
 */
@ExtendWith(MockitoExtension.class)
class TikaTextExtractorTest {

    @Mock
    private ContentService contentService;

    @InjectMocks
    private TikaTextExtractor extractor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(extractor, "maxTextLength", 10485760);
    }

    @Test
    @DisplayName("Extract text from plain text file")
    void extractTextFromPlainText() throws IOException {
        String testContent = "This is a test document with some content.";
        String contentId = "test-content-id";

        DocumentContext context = DocumentContext.builder()
            .originalFilename("test.txt")
            .contentId(contentId)
            .mimeType("text/plain")
            .build();

        when(contentService.getContent(contentId))
            .thenReturn(new ByteArrayInputStream(testContent.getBytes(StandardCharsets.UTF_8)));

        ProcessingResult result = extractor.process(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(context.getExtractedText()).contains("test document");
    }

    @Test
    @DisplayName("Skip image files")
    void skipImageFiles() {
        DocumentContext context = DocumentContext.builder()
            .originalFilename("photo.jpg")
            .contentId("test-content-id")
            .mimeType("image/jpeg")
            .build();

        boolean supports = extractor.supports(context);

        assertThat(supports).isFalse();
    }

    @Test
    @DisplayName("Skip video files")
    void skipVideoFiles() {
        DocumentContext context = DocumentContext.builder()
            .originalFilename("video.mp4")
            .contentId("test-content-id")
            .mimeType("video/mp4")
            .build();

        boolean supports = extractor.supports(context);

        assertThat(supports).isFalse();
    }

    @Test
    @DisplayName("Extract metadata from document")
    void extractMetadata() throws IOException {
        // Create a simple HTML document with title
        String htmlContent = "<html><head><title>Test Title</title></head><body>Content here</body></html>";
        String contentId = "test-content-id";

        DocumentContext context = DocumentContext.builder()
            .originalFilename("test.html")
            .contentId(contentId)
            .mimeType("text/html")
            .build();

        when(contentService.getContent(contentId))
            .thenReturn(new ByteArrayInputStream(htmlContent.getBytes(StandardCharsets.UTF_8)));

        ProcessingResult result = extractor.process(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(context.getExtractedText()).contains("Content here");
    }

    @Test
    @DisplayName("Handle missing content gracefully")
    void handleMissingContent() {
        DocumentContext context = DocumentContext.builder()
            .originalFilename("test.pdf")
            .contentId(null)
            .mimeType("application/pdf")
            .build();

        ProcessingResult result = extractor.process(context);

        assertThat(result.getStatus()).isEqualTo(ProcessingResult.Status.SKIPPED);
    }

    @Test
    @DisplayName("Processor order is correct")
    void processorOrderIsCorrect() {
        assertThat(extractor.getOrder()).isEqualTo(200);
    }

    @Test
    @DisplayName("Support PDF files")
    void supportPdfFiles() {
        DocumentContext context = DocumentContext.builder()
            .originalFilename("document.pdf")
            .contentId("test-content-id")
            .mimeType("application/pdf")
            .build();

        boolean supports = extractor.supports(context);

        assertThat(supports).isTrue();
    }

    @Test
    @DisplayName("Support Office documents")
    void supportOfficeDocuments() {
        DocumentContext context = DocumentContext.builder()
            .originalFilename("document.docx")
            .contentId("test-content-id")
            .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            .build();

        boolean supports = extractor.supports(context);

        assertThat(supports).isTrue();
    }
}
