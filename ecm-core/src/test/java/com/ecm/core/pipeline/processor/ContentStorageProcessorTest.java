package com.ecm.core.pipeline.processor;

import com.ecm.core.pipeline.DocumentContext;
import com.ecm.core.pipeline.ProcessingResult;
import com.ecm.core.service.ContentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentStorageProcessorTest {

    @Mock
    private ContentService contentService;

    @InjectMocks
    private ContentStorageProcessor processor;

    @Test
    @DisplayName("Detect MIME type using filename-aware detection for pipeline uploads")
    void detectsMimeTypeWithFilename() throws Exception {
        String filename = "test.pdf";
        byte[] payload = "dummy".getBytes(StandardCharsets.UTF_8);
        InputStream input = new ByteArrayInputStream(payload);

        DocumentContext context = DocumentContext.builder()
            .originalFilename(filename)
            .inputStream(input)
            .build();

        when(contentService.storeContent(any(InputStream.class), eq(filename))).thenReturn("cid");
        when(contentService.getContentSize("cid")).thenReturn((long) payload.length);
        when(contentService.detectMimeType("cid", filename)).thenReturn("application/pdf");

        ProcessingResult result = processor.process(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(context.getContentId()).isEqualTo("cid");
        assertThat(context.getFileSize()).isEqualTo(payload.length);
        assertThat(context.getMimeType()).isEqualTo("application/pdf");
        verify(contentService).detectMimeType("cid", filename);
    }
}

