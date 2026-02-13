package com.ecm.core.preview;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.search.SearchIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
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
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewQueueService service = new PreviewQueueService(documentRepository, previewService, searchIndexService, redisTemplate);

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
        verify(searchIndexService, atLeastOnce()).updateDocument(document);
    }

    @Test
    void skipsEnqueueForUnsupportedWhenNotForced() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewQueueService service = new PreviewQueueService(documentRepository, previewService, searchIndexService, redisTemplate);

        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 1);

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setPreviewStatus(PreviewStatus.UNSUPPORTED);

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        PreviewQueueService.PreviewQueueStatus status = service.enqueue(documentId, false);

        assertEquals(PreviewStatus.UNSUPPORTED, status.previewStatus());
        assertEquals(false, status.queued());
        assertEquals("Preview unsupported", status.message());
        verify(documentRepository, never()).save(any());
        verifyNoInteractions(searchIndexService);

        service.processQueue();
        verifyNoInteractions(previewService);
    }

    @Test
    void blocksEnqueueForPermanentFailureWhenNotForced() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewQueueService service = new PreviewQueueService(documentRepository, previewService, searchIndexService, redisTemplate);

        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 1);

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("Error generating preview: Missing root object specification in trailer.");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        PreviewQueueService.PreviewQueueStatus status = service.enqueue(documentId, false);

        assertEquals(PreviewStatus.FAILED, status.previewStatus());
        assertEquals(false, status.queued());
        assertEquals("Preview failed permanently; use force=true to rebuild", status.message());
        verify(documentRepository, never()).save(any());
        verifyNoInteractions(searchIndexService);
        verifyNoInteractions(previewService);
    }

    @Test
    void allowsEnqueueForTemporaryFailureWhenNotForced() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewQueueService service = new PreviewQueueService(documentRepository, previewService, searchIndexService, redisTemplate);

        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 1);

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("Timeout contacting preview service");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PreviewQueueService.PreviewQueueStatus status = service.enqueue(documentId, false);

        assertEquals(true, status.queued());
        assertEquals("Preview queued", status.message());
        assertEquals(PreviewStatus.PROCESSING, document.getPreviewStatus());
        assertEquals(null, document.getPreviewFailureReason());
        verify(documentRepository, atLeastOnce()).save(document);
        verify(searchIndexService, atLeastOnce()).updateDocument(document);
    }
}
