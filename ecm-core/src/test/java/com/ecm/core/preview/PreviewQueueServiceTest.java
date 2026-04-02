package com.ecm.core.preview;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.search.SearchIndexService;
import com.ecm.core.service.RenditionResourceSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PreviewQueueServiceTest {

    private PreviewQueueService newPreviewQueueService(
        DocumentRepository documentRepository,
        PreviewService previewService,
        SearchIndexService searchIndexService,
        StringRedisTemplate redisTemplate,
        PreviewTransformTraceBuffer previewTransformTraceBuffer,
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry,
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry,
        PreviewDeadLetterRegistry previewDeadLetterRegistry
    ) {
        return new PreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            mock(RenditionResourceSyncService.class),
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );
    }

    @Test
    void cancelQueuedPreviewRemovesPendingMemoryJob() throws Exception {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = mock(PreviewRenditionPreventionRegistry.class);
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 3, 60000L, 1.6d, 0L, true));

        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );

        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 1);

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("timeout");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PreviewQueueService.PreviewQueueStatus queued = service.enqueue(documentId, true);
        assertTrue(queued.queued());

        PreviewQueueService.PreviewQueueCancellationStatus cancelled = service.cancel(documentId);
        assertTrue(cancelled.cancelled());
        assertEquals("CANCELLED", cancelled.queueState());
        assertTrue(cancelled.hadActiveTask());
        assertFalse(cancelled.running());

        service.processQueue();
        verifyNoInteractions(previewService);
    }

    @Test
    void cancelRunningPreviewReturnsCancelRequestedAndPreventsRetry() throws Exception {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = mock(PreviewRenditionPreventionRegistry.class);
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 2, 1000L, 1.0d, 0L, true));

        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );

        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 1);
        ReflectionTestUtils.setField(service, "runAsUser", "admin");
        ReflectionTestUtils.setField(service, "maxAttempts", 2);

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("timeout");

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch allowFinish = new CountDownLatch(1);

        PreviewResult retryResult = new PreviewResult();
        retryResult.setSupported(false);
        retryResult.setRetryNeeded(true);
        retryResult.setMessage("temporary failure");
        retryResult.setMimeType("application/pdf");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(previewService.generatePreview(document)).thenAnswer(invocation -> {
            started.countDown();
            allowFinish.await(3, TimeUnit.SECONDS);
            return retryResult;
        });

        service.enqueue(documentId, true);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(service::processQueue);
            assertTrue(started.await(1, TimeUnit.SECONDS));

            PreviewQueueService.PreviewQueueCancellationStatus cancellation = service.cancel(documentId);
            assertEquals("CANCEL_REQUESTED", cancellation.queueState());
            assertTrue(cancellation.cancelled());
            assertTrue(cancellation.running());

            allowFinish.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        } finally {
            allowFinish.countDown();
            executor.shutdownNow();
        }

        service.processQueue();
        verify(previewService, times(1)).generatePreview(document);
    }

    @Test
    void marksFailedWhenPreviewThrowsAndNoRetry() throws Exception {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = mock(PreviewRenditionPreventionRegistry.class);
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 3, 60000L, 1.6d, 0L, true));
        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 1, 1000L, 1.0d, 0L, true));

        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "maxAttempts", 1);
        ReflectionTestUtils.setField(service, "retryDelayMs", 1000L);
        ReflectionTestUtils.setField(service, "batchSize", 1);
        ReflectionTestUtils.setField(service, "runAsUser", "admin");

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setContentHash("abc123");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(previewService.generatePreview(document)).thenThrow(new RuntimeException("boom"));

        service.enqueue(documentId, true);
        service.processQueue();

        assertEquals(PreviewStatus.FAILED, document.getPreviewStatus());
        assertEquals("boom", document.getPreviewFailureReason());
        assertEquals(1, document.getPreviewFailureCount());
        assertNotNull(document.getPreviewFailedAt());
        assertEquals("boom", document.getPreviewLastFailureReason());
        assertEquals("abc123", document.getPreviewFailureContentHash());
        verify(documentRepository, atLeastOnce()).save(document);
        verify(searchIndexService, atLeastOnce()).updateDocument(document);
    }

    @Test
    void enqueueClearsStaleFailureLedgerWhenContentHashChanged() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = mock(PreviewRenditionPreventionRegistry.class);
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 3, 60000L, 1.6d, 0L, true));
        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );

        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 1);

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("Error generating preview: Missing root object specification in trailer.");
        document.setPreviewFailureCount(4);
        document.setPreviewLastFailureReason("Error generating preview: Missing root object specification in trailer.");
        document.setPreviewFailureContentHash("oldhash");
        document.setContentHash("newhash");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PreviewQueueService.PreviewQueueStatus status = service.enqueue(documentId, false);

        assertTrue(status.queued());
        assertEquals(PreviewStatus.PROCESSING, status.previewStatus());
        assertNull(status.previewFailureReason());
        assertNull(status.previewFailureCategory());
        assertNotNull(status.previewLastUpdated());
        assertEquals(PreviewStatus.PROCESSING, document.getPreviewStatus());
        assertEquals(0, document.getPreviewFailureCount());
        assertNull(document.getPreviewFailedAt());
        assertNull(document.getPreviewLastFailureReason());
        assertNull(document.getPreviewFailureContentHash());
        verify(searchIndexService, atLeastOnce()).updateDocument(document);
    }

    @Test
    void skipsEnqueueForUnsupportedWhenNotForced() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = mock(PreviewRenditionPreventionRegistry.class);
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 3, 60000L, 1.6d, 0L, true));
        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );
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
        assertEquals("UNSUPPORTED", status.previewFailureCategory());
        verify(documentRepository, never()).save(any());
        verifyNoInteractions(searchIndexService);

        service.processQueue();
        verifyNoInteractions(previewService);
    }

    @Test
    void skipsEnqueueForEffectiveUnsupportedWhenStatusMissing() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = mock(PreviewRenditionPreventionRegistry.class);
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 3, 60000L, 1.6d, 0L, true));
        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );
        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 1);

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setMimeType("application/octet-stream");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        PreviewQueueService.PreviewQueueStatus status = service.enqueue(documentId, false);

        assertEquals(PreviewStatus.UNSUPPORTED, status.previewStatus());
        assertFalse(status.queued());
        assertEquals("Preview unsupported", status.message());
        assertEquals("UNSUPPORTED", status.previewFailureCategory());

        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = service.declinedSnapshot(20);
        assertEquals(1L, snapshot.totalDeclined());
        assertEquals(PreviewStatus.UNSUPPORTED, snapshot.items().get(0).previewStatus());
        assertEquals("UNSUPPORTED", snapshot.items().get(0).category());

        verify(documentRepository, never()).save(any());
        verifyNoInteractions(searchIndexService);
        verifyNoInteractions(previewService);
    }

    @Test
    void blocksEnqueueForPermanentFailureWhenNotForced() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = mock(PreviewRenditionPreventionRegistry.class);
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 3, 60000L, 1.6d, 0L, true));
        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 2, 1000L, 1.0d, 0L, true));

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
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = mock(PreviewRenditionPreventionRegistry.class);
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 3, 60000L, 1.6d, 0L, true));
        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );

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

    @Test
    void retriesWhenPreviewResultExplicitlyRequestsRetry() throws Exception {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = mock(PreviewRenditionPreventionRegistry.class);
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 3, 60000L, 1.6d, 0L, true));
        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );

        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "maxAttempts", 2);
        ReflectionTestUtils.setField(service, "retryDelayMs", 1000L);
        ReflectionTestUtils.setField(service, "batchSize", 1);
        ReflectionTestUtils.setField(service, "runAsUser", "admin");

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setMimeType("application/dwg");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("CAD render transient issue");

        PreviewResult retryHintResult = new PreviewResult();
        retryHintResult.setSupported(false);
        retryHintResult.setRetryNeeded(true);
        retryHintResult.setMessage("CAD render service requested retry");
        retryHintResult.setMimeType("application/dwg");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(previewService.generatePreview(document)).thenReturn(retryHintResult);

        service.enqueue(documentId, true);
        service.processQueue();

        assertEquals(PreviewStatus.PROCESSING, document.getPreviewStatus());
        assertEquals("CAD render service requested retry", document.getPreviewFailureReason());
        verify(previewService, times(1)).generatePreview(document);
        verify(documentRepository, atLeast(2)).save(document);
        verify(searchIndexService, atLeast(2)).updateDocument(document);
    }

    @Test
    void skipsEnqueueWithinQuietPeriodForFailedDocument() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = mock(PreviewRenditionPreventionRegistry.class);
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("cad", "CAD", 5, 60000L, 2.0d, 120000L, true));
        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );

        ReflectionTestUtils.setField(service, "queueEnabled", true);

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setName("drawing.dwg");
        document.setMimeType("application/dwg");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("Timeout contacting preview service");
        document.setPreviewLastUpdated(LocalDateTime.now().minusSeconds(10));

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        PreviewQueueService.PreviewQueueStatus status = service.enqueue(documentId, false);

        assertEquals(false, status.queued());
        assertTrue(status.message().contains("quiet period"));
        verify(documentRepository, never()).save(any());
        verifyNoInteractions(previewService);
    }

    @Test
    void skipsBlockedDocumentAndAccumulatesBlockedHits() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = new PreviewRenditionPreventionRegistry();
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 3, 60000L, 1.6d, 0L, true));
        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );

        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 1);

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setMimeType("application/octet-stream");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("Preview not supported for mime type application/octet-stream");

        previewRenditionPreventionRegistry.block(
            documentId,
            "Preview not supported for mime type application/octet-stream",
            PreviewFailureClassifier.CATEGORY_UNSUPPORTED
        );
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        PreviewQueueService.PreviewQueueStatus first = service.enqueue(documentId, false);
        PreviewQueueService.PreviewQueueStatus second = service.enqueue(documentId, false);

        assertFalse(first.queued());
        assertFalse(second.queued());
        assertTrue(first.message().contains("Rendition prevented"));
        PreviewRenditionPreventionRegistry.BlockedEntry blockedEntry = previewRenditionPreventionRegistry.get(documentId);
        assertNotNull(blockedEntry);
        assertEquals(2L, blockedEntry.hitCount());
        verify(documentRepository, never()).save(any());
        verifyNoInteractions(previewService);
    }

    @Test
    void forceEnqueueUnblocksDocumentFromPreventionRegistry() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = new PreviewRenditionPreventionRegistry();
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 3, 60000L, 1.6d, 0L, true));
        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );

        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 1);

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("Timeout contacting preview service");

        previewRenditionPreventionRegistry.block(documentId, "manual block", PreviewFailureClassifier.CATEGORY_PERMANENT);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PreviewQueueService.PreviewQueueStatus status = service.enqueue(documentId, true);

        assertTrue(status.queued());
        assertNull(previewRenditionPreventionRegistry.get(documentId));
        verify(documentRepository, atLeastOnce()).save(document);
        verify(searchIndexService, atLeastOnce()).updateDocument(document);
    }

    @Test
    void autoBlocksUnsupportedPreviewOutcomeAfterQueueProcessing() throws Exception {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = new PreviewRenditionPreventionRegistry();
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 1, 1000L, 1.0d, 0L, true));
        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );

        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 1);
        ReflectionTestUtils.setField(service, "maxAttempts", 1);
        ReflectionTestUtils.setField(service, "runAsUser", "admin");

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setName("blocked.bin");
        document.setMimeType("application/octet-stream");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("Preview not supported for mime type application/octet-stream");

        PreviewResult unsupportedResult = new PreviewResult();
        unsupportedResult.setSupported(false);
        unsupportedResult.setMimeType("application/octet-stream");
        unsupportedResult.setMessage("Preview not supported for mime type application/octet-stream");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(previewService.generatePreview(document)).thenReturn(unsupportedResult);

        service.enqueue(documentId, true);
        service.processQueue();

        PreviewRenditionPreventionRegistry.BlockedEntry blockedEntry = previewRenditionPreventionRegistry.get(documentId);
        assertNotNull(blockedEntry);
        assertEquals(PreviewFailureClassifier.CATEGORY_UNSUPPORTED, blockedEntry.category());
        verify(previewService, times(1)).generatePreview(document);
    }

    @Test
    void retrySchedulingPrefersExplicitTemporaryFailureCategoryOverMisleadingMessage() throws Exception {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = new PreviewRenditionPreventionRegistry();
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 2, 1000L, 1.0d, 0L, true));
        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );

        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 1);
        ReflectionTestUtils.setField(service, "maxAttempts", 2);
        ReflectionTestUtils.setField(service, "runAsUser", "admin");

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setName("temporary.pdf");
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("stale failure");

        PreviewResult temporaryResult = new PreviewResult();
        temporaryResult.setSupported(false);
        temporaryResult.setStatus(PreviewStatus.FAILED.name());
        temporaryResult.setFailureCategory(PreviewFailureClassifier.CATEGORY_TEMPORARY);
        temporaryResult.setFailureReason("service unavailable");
        temporaryResult.setMessage("Preview not supported for mime type application/pdf");
        temporaryResult.setMimeType("application/pdf");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(previewService.generatePreview(document)).thenReturn(temporaryResult);

        service.enqueue(documentId, true);
        service.processQueue();

        assertEquals(PreviewStatus.PROCESSING, document.getPreviewStatus());
        assertEquals("service unavailable", document.getPreviewFailureReason());
        assertNull(previewRenditionPreventionRegistry.get(documentId));
        verify(previewDeadLetterRegistry, never()).record(eq(documentId), eq("preview"), anyString(), anyString(), anyString(), anyString(), anyInt());

        PreviewQueueService.PreviewQueueDiagnosticsSnapshot diagnostics = service.diagnosticsSnapshot(20);
        assertEquals(1L, diagnostics.scheduledCount());
        assertEquals(1, diagnostics.items().size());
        assertEquals(documentId, diagnostics.items().get(0).documentId());
    }

    @Test
    void terminalUnsupportedResultPrefersExplicitStatusOverRetryLookingMessage() throws Exception {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = new PreviewRenditionPreventionRegistry();
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 2, 1000L, 1.0d, 0L, true));
        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );

        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 1);
        ReflectionTestUtils.setField(service, "maxAttempts", 2);
        ReflectionTestUtils.setField(service, "runAsUser", "admin");

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setName("unsupported.bin");
        document.setMimeType("application/octet-stream");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("legacy timeout");

        PreviewResult unsupportedResult = new PreviewResult();
        unsupportedResult.setSupported(false);
        unsupportedResult.setStatus(PreviewStatus.UNSUPPORTED.name());
        unsupportedResult.setFailureReason("Preview not supported for mime type application/octet-stream");
        unsupportedResult.setMessage("timeout contacting preview service");
        unsupportedResult.setMimeType("application/octet-stream");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(previewService.generatePreview(document)).thenReturn(unsupportedResult);

        service.enqueue(documentId, true);
        service.processQueue();

        PreviewRenditionPreventionRegistry.BlockedEntry blockedEntry = previewRenditionPreventionRegistry.get(documentId);
        assertNotNull(blockedEntry);
        assertEquals(PreviewFailureClassifier.CATEGORY_UNSUPPORTED, blockedEntry.category());
        verify(previewDeadLetterRegistry, atLeastOnce()).record(
            eq(documentId),
            eq("preview"),
            contains("not supported"),
            eq(PreviewFailureClassifier.CATEGORY_UNSUPPORTED),
            eq("default"),
            eq("QUEUE_TERMINAL"),
            eq(1)
        );
        PreviewQueueService.PreviewQueueDiagnosticsSnapshot diagnostics = service.diagnosticsSnapshot(20);
        assertEquals(0L, diagnostics.scheduledCount());
    }

    @Test
    void deadLetterAndAutoBlockPreferExplicitPermanentCategoryOverTemporaryMessage() throws Exception {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = new PreviewRenditionPreventionRegistry();
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 1, 1000L, 1.0d, 0L, true));
        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );

        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 1);
        ReflectionTestUtils.setField(service, "maxAttempts", 1);
        ReflectionTestUtils.setField(service, "runAsUser", "admin");

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setName("permanent.pdf");
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("legacy timeout");

        PreviewResult permanentResult = new PreviewResult();
        permanentResult.setSupported(false);
        permanentResult.setStatus(PreviewStatus.FAILED.name());
        permanentResult.setFailureCategory(PreviewFailureClassifier.CATEGORY_PERMANENT);
        permanentResult.setFailureReason("irreversible parse error");
        permanentResult.setMessage("timeout contacting preview service");
        permanentResult.setMimeType("application/pdf");
        permanentResult.setRetryNeeded(true);

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(previewService.generatePreview(document)).thenReturn(permanentResult);

        service.enqueue(documentId, true);
        service.processQueue();

        assertEquals(PreviewStatus.FAILED, document.getPreviewStatus());
        assertEquals("irreversible parse error", document.getPreviewFailureReason());
        PreviewRenditionPreventionRegistry.BlockedEntry blockedEntry = previewRenditionPreventionRegistry.get(documentId);
        assertNotNull(blockedEntry);
        assertEquals(PreviewFailureClassifier.CATEGORY_PERMANENT, blockedEntry.category());
        verify(previewDeadLetterRegistry, atLeastOnce()).record(
            eq(documentId),
            eq("preview"),
            eq("irreversible parse error"),
            eq(PreviewFailureClassifier.CATEGORY_PERMANENT),
            eq("default"),
            eq("QUEUE_RETRY_EXHAUSTED"),
            eq(1)
        );
    }

    @Test
    void recordsDeadLetterWhenRetryExhausted() throws Exception {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = mock(PreviewRenditionPreventionRegistry.class);
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 1, 1000L, 1.0d, 0L, true));
        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );

        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 1);
        ReflectionTestUtils.setField(service, "maxAttempts", 1);
        ReflectionTestUtils.setField(service, "runAsUser", "admin");

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("timeout");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(previewService.generatePreview(document)).thenThrow(new RuntimeException("boom"));

        service.enqueue(documentId, true);
        service.processQueue();

        verify(previewDeadLetterRegistry, atLeastOnce()).record(
            eq(documentId),
            eq("preview"),
            contains("boom"),
            anyString(),
            eq("default"),
            anyString(),
            eq(1)
        );
    }

    @Test
    void clearsDeadLetterWhenPreviewBecomesSupported() throws Exception {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = mock(PreviewRenditionPreventionRegistry.class);
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 2, 1000L, 1.0d, 0L, true));
        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );

        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 1);
        ReflectionTestUtils.setField(service, "maxAttempts", 2);
        ReflectionTestUtils.setField(service, "runAsUser", "admin");

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("temporary");

        PreviewResult ready = new PreviewResult();
        ready.setSupported(true);
        ready.setMessage("ok");
        ready.setMimeType("application/pdf");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(previewService.generatePreview(document)).thenReturn(ready);

        service.enqueue(documentId, true);
        service.processQueue();

        verify(previewDeadLetterRegistry, atLeastOnce()).remove(documentId, "preview");
    }

    @Test
    void autoReplayQueuesEligibleTemporaryDeadLettersOnly() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = mock(PreviewRenditionPreventionRegistry.class);
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 2, 1000L, 1.0d, 0L, true));

        PreviewQueueService service = spy(newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        ));

        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "deadLetterAutoReplayEnabled", true);
        ReflectionTestUtils.setField(service, "deadLetterAutoReplayMaxItems", 5);
        ReflectionTestUtils.setField(service, "deadLetterAutoReplayCooldownMs", 120000L);
        ReflectionTestUtils.setField(service, "deadLetterAutoReplayForce", false);
        ReflectionTestUtils.setField(service, "deadLetterAutoReplayCategories", "TEMPORARY");

        UUID eligibleId = UUID.randomUUID();
        UUID permanentId = UUID.randomUUID();
        UUID coolingId = UUID.randomUUID();
        Instant now = Instant.now();
        when(previewDeadLetterRegistry.isEnabled()).thenReturn(true);
        when(previewDeadLetterRegistry.list(anyInt())).thenReturn(List.of(
            new PreviewDeadLetterRegistry.DeadLetterEntry(
                PreviewDeadLetterRegistry.buildEntryKey(eligibleId, "preview"),
                eligibleId,
                "preview",
                "temporary timeout",
                "TEMPORARY",
                "default",
                "QUEUE_RETRY_EXHAUSTED",
                now.minusSeconds(600),
                3,
                2,
                null,
                0
            ),
            new PreviewDeadLetterRegistry.DeadLetterEntry(
                PreviewDeadLetterRegistry.buildEntryKey(permanentId, "preview"),
                permanentId,
                "preview",
                "permanent broken file",
                "PERMANENT",
                "default",
                "QUEUE_TERMINAL",
                now.minusSeconds(600),
                1,
                1,
                null,
                0
            ),
            new PreviewDeadLetterRegistry.DeadLetterEntry(
                PreviewDeadLetterRegistry.buildEntryKey(coolingId, "preview"),
                coolingId,
                "preview",
                "temporary busy",
                "TEMPORARY",
                "default",
                "QUEUE_RETRY_EXHAUSTED",
                now.minusSeconds(600),
                2,
                1,
                now.minusSeconds(30),
                1
            )
        ));

        doReturn(new PreviewQueueService.PreviewQueueStatus(
            eligibleId,
            PreviewStatus.FAILED,
            true,
            0,
            null,
            "Preview queued"
        )).when(service).enqueue(eligibleId, false);

        service.processDeadLetterAutoReplay();

        verify(service, times(1)).enqueue(eligibleId, false);
        verify(service, never()).enqueue(eq(permanentId), anyBoolean());
        verify(service, never()).enqueue(eq(coolingId), anyBoolean());
        verify(previewDeadLetterRegistry, never()).markReplayAttempt(eq(eligibleId), eq("preview"), any());
    }

    @Test
    void autoReplayMarksReplayAttemptWhenQueueSkipsItem() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = mock(PreviewRenditionPreventionRegistry.class);
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 2, 1000L, 1.0d, 0L, true));

        PreviewQueueService service = spy(newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        ));

        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "deadLetterAutoReplayEnabled", true);
        ReflectionTestUtils.setField(service, "deadLetterAutoReplayMaxItems", 1);
        ReflectionTestUtils.setField(service, "deadLetterAutoReplayCooldownMs", 1000L);
        ReflectionTestUtils.setField(service, "deadLetterAutoReplayForce", false);
        ReflectionTestUtils.setField(service, "deadLetterAutoReplayCategories", "TEMPORARY");

        UUID skippedId = UUID.randomUUID();
        Instant now = Instant.now();
        when(previewDeadLetterRegistry.isEnabled()).thenReturn(true);
        when(previewDeadLetterRegistry.list(anyInt())).thenReturn(List.of(
            new PreviewDeadLetterRegistry.DeadLetterEntry(
                PreviewDeadLetterRegistry.buildEntryKey(skippedId, "preview"),
                skippedId,
                "preview",
                "temporary timeout",
                "TEMPORARY",
                "default",
                "QUEUE_RETRY_EXHAUSTED",
                now.minusSeconds(600),
                2,
                1,
                null,
                0
            )
        ));

        doReturn(new PreviewQueueService.PreviewQueueStatus(
            skippedId,
            PreviewStatus.UNSUPPORTED,
            false,
            0,
            null,
            "Preview unsupported"
        )).when(service).enqueue(skippedId, false);

        service.processDeadLetterAutoReplay();

        verify(service, times(1)).enqueue(skippedId, false);
        verify(previewDeadLetterRegistry, atLeastOnce()).markReplayAttempt(eq(skippedId), eq("preview"), any());
    }

    @Test
    void queueDiagnosticsSnapshotReportsMemoryQueueState() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = mock(PreviewRenditionPreventionRegistry.class);
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 3, 60000L, 1.6d, 0L, true));

        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );

        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "queueBackend", "memory");

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("Timeout contacting preview service");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PreviewQueueService.PreviewQueueStatus queued = service.enqueue(documentId, true);
        assertTrue(queued.queued());

        PreviewQueueService.PreviewQueueDiagnosticsSnapshot snapshot = service.diagnosticsSnapshot(20);
        assertEquals("MEMORY", snapshot.backend());
        assertTrue(snapshot.queueEnabled());
        assertEquals(1L, snapshot.scheduledCount());
        assertEquals(1L, snapshot.governanceCount());
        assertEquals(0L, snapshot.runningCount());
        assertTrue(snapshot.runningCountAccurate());
        assertEquals(0L, snapshot.cancellationRequestedCount());
        assertEquals(20, snapshot.sampleLimit());
        assertFalse(snapshot.sampleTruncated());
        assertEquals(1, snapshot.items().size());
        PreviewQueueService.PreviewQueueDiagnosticsItem item = snapshot.items().get(0);
        assertEquals(documentId, item.documentId());
        assertNotNull(item.governanceKey());
        assertFalse(item.running());
        assertFalse(item.cancelRequested());
    }

    @Test
    void enqueueDeclinedWhenQueueDisabledIsTrackedAndClearable() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = mock(PreviewRenditionPreventionRegistry.class);
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 3, 60000L, 1.6d, 0L, true));

        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );
        ReflectionTestUtils.setField(service, "queueEnabled", false);

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("Timeout contacting preview service");
        document.setContentHash("hash-disabled");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        PreviewQueueService.PreviewQueueStatus declined = service.enqueue(documentId, false);
        assertFalse(declined.queued());
        assertEquals("Preview queue disabled by configuration", declined.message());

        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = service.declinedSnapshot(20);
        assertTrue(snapshot.sampleLimit() > 0);
        assertEquals(1L, snapshot.totalDeclined());
        assertEquals(1, snapshot.items().size());
        PreviewQueueService.PreviewQueueDeclinedItem declinedItem = snapshot.items().get(0);
        assertEquals(documentId, declinedItem.documentId());
        assertEquals("QUEUE_DISABLED", declinedItem.category());
        assertEquals("Preview queue disabled by configuration", declinedItem.reason());
        assertFalse(declinedItem.forceRequired());
        assertNotNull(declinedItem.declinedAt());

        assertTrue(service.clearDeclined(documentId));
        PreviewQueueService.PreviewQueueDeclinedSnapshot afterClear = service.declinedSnapshot(20);
        assertEquals(0L, afterClear.totalDeclined());
        assertTrue(afterClear.items().isEmpty());
    }

    @Test
    void enqueueDeclinedQuietPeriodIncludesNextEligibleAt() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = mock(PreviewRenditionPreventionRegistry.class);
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 3, 60000L, 1.2d, 120000L, true));

        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );
        ReflectionTestUtils.setField(service, "queueEnabled", true);

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("Timeout contacting preview service");
        document.setPreviewLastUpdated(LocalDateTime.now().minusSeconds(30));
        document.setContentHash("hash-quiet");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        PreviewQueueService.PreviewQueueStatus declined = service.enqueue(documentId, false);
        assertFalse(declined.queued());
        assertTrue(declined.message().contains("Within quiet period"));
        assertNotNull(declined.nextAttemptAt());

        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = service.declinedSnapshot(20);
        assertEquals(1L, snapshot.totalDeclined());
        PreviewQueueService.PreviewQueueDeclinedItem item = snapshot.items().get(0);
        assertEquals("QUIET_PERIOD", item.category());
        assertEquals(declined.nextAttemptAt(), item.nextEligibleAt());
        assertFalse(item.forceRequired());
    }

    @Test
    void evaluateEnqueueReturnsDryRunDecisionWithoutMutatingDeclinedHistory() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = mock(PreviewRenditionPreventionRegistry.class);
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 3, 60000L, 1.2d, 120000L, true));

        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );
        ReflectionTestUtils.setField(service, "queueEnabled", true);

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setMimeType("application/pdf");
        document.setPreviewStatus(PreviewStatus.FAILED);
        document.setPreviewFailureReason("Timeout contacting preview service");
        document.setPreviewLastUpdated(LocalDateTime.now().minusSeconds(30));

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        PreviewQueueService.PreviewQueueStatus decision = service.evaluateEnqueue(documentId, false);
        assertFalse(decision.queued());
        assertTrue(decision.message().contains("Within quiet period"));
        assertNotNull(decision.nextAttemptAt());

        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = service.declinedSnapshot(20);
        assertEquals(0L, snapshot.totalDeclined());
        assertTrue(snapshot.items().isEmpty());
        verifyNoInteractions(previewService);
    }

    @Test
    void evaluateEnqueueTreatsEffectiveUnsupportedAsUnsupportedWhenStatusMissing() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        PreviewService previewService = mock(PreviewService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreviewTransformTraceBuffer previewTransformTraceBuffer = mock(PreviewTransformTraceBuffer.class);
        PreviewFailurePolicyRegistry previewFailurePolicyRegistry = mock(PreviewFailurePolicyRegistry.class);
        PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry = mock(PreviewRenditionPreventionRegistry.class);
        PreviewDeadLetterRegistry previewDeadLetterRegistry = mock(PreviewDeadLetterRegistry.class);
        when(previewFailurePolicyRegistry.resolve(any(), any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy("default", "Default", 3, 60000L, 1.2d, 120000L, true));

        PreviewQueueService service = newPreviewQueueService(
            documentRepository,
            previewService,
            searchIndexService,
            redisTemplate,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );
        ReflectionTestUtils.setField(service, "queueEnabled", true);

        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setMimeType("application/octet-stream");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        PreviewQueueService.PreviewQueueStatus decision = service.evaluateEnqueue(documentId, false);
        assertFalse(decision.queued());
        assertEquals(PreviewStatus.UNSUPPORTED, decision.previewStatus());
        assertEquals("Preview unsupported", decision.message());

        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = service.declinedSnapshot(20);
        assertEquals(0L, snapshot.totalDeclined());
        assertTrue(snapshot.items().isEmpty());
        verifyNoInteractions(previewService);
    }
}
