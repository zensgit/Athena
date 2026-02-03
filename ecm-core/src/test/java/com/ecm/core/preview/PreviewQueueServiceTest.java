package com.ecm.core.preview;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PreviewQueueServiceTest {

    @Test
    void marksFailedWhenPreviewThrowsAndNoRetry() throws Exception {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        PreviewQueueService service = new PreviewQueueService(documentRepository, previewService);

        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "maxAttempts", 1);
        ReflectionTestUtils.setField(service, "retryDelayMs", 1000L);
        ReflectionTestUtils.setField(service, "batchSize", 1);
        ReflectionTestUtils.setField(service, "runAsUser", "admin");

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(previewService.generatePreview(document)).thenThrow(new RuntimeException("boom"));

        service.enqueue(documentId, true);
        service.processQueue();

        assertEquals(PreviewStatus.FAILED, document.getPreviewStatus());
        assertEquals("boom", document.getPreviewFailureReason());
        verify(documentRepository, atLeastOnce()).save(document);
    }
}
