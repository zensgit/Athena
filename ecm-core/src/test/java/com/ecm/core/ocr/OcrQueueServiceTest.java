package com.ecm.core.ocr;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Correspondent;
import com.ecm.core.ml.MLServiceClient;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.search.SearchIndexService;
import com.ecm.core.service.ContentService;
import com.ecm.core.service.CorrespondentService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OcrQueueServiceTest {

    @Test
    void enqueueReturnsNotQueuedWhenDisabled() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        ContentService contentService = mock(ContentService.class);
        CorrespondentService correspondentService = mock(CorrespondentService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        MLServiceClient mlServiceClient = mock(MLServiceClient.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        OcrQueueService service = new OcrQueueService(documentRepository, contentService, correspondentService, searchIndexService, mlServiceClient, redisTemplate);
        ReflectionTestUtils.setField(service, "ocrEnabled", false);

        UUID docId = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(docId);
        doc.setMetadata(new HashMap<>());
        doc.setMimeType("application/pdf");
        doc.setFileSize(123L);

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        OcrQueueService.OcrQueueStatus status = service.enqueue(docId, false);
        assertFalse(status.queued());
        assertEquals(docId, status.documentId());
    }

    @Test
    void processQueueUpdatesTextAndMetadataOnSuccess() throws Exception {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        ContentService contentService = mock(ContentService.class);
        CorrespondentService correspondentService = mock(CorrespondentService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        MLServiceClient mlServiceClient = mock(MLServiceClient.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        OcrQueueService service = new OcrQueueService(documentRepository, contentService, correspondentService, searchIndexService, mlServiceClient, redisTemplate);
        ReflectionTestUtils.setField(service, "ocrEnabled", true);
        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 1);
        ReflectionTestUtils.setField(service, "maxAttempts", 1);
        ReflectionTestUtils.setField(service, "retryDelayMs", 10L);
        ReflectionTestUtils.setField(service, "maxBytes", 1024L * 1024L);
        ReflectionTestUtils.setField(service, "maxPages", 2);
        ReflectionTestUtils.setField(service, "maxChars", 5000);
        ReflectionTestUtils.setField(service, "ocrLanguage", "eng");

        UUID docId = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(docId);
        doc.setMetadata(new HashMap<>());
        doc.setName("sample.pdf");
        doc.setMimeType("application/pdf");
        doc.setFileSize(100L);
        doc.setContentId("content-1");
        doc.setTextContent("");

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contentService.getContent("content-1")).thenReturn(new ByteArrayInputStream("pdf".getBytes()));

        MLServiceClient.OcrResult ocrResult = MLServiceClient.OcrResult.builder()
            .success(true)
            .text("hello ocr")
            .pages(1)
            .language("eng")
            .truncated(false)
            .build();
        when(mlServiceClient.ocr(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(ocrResult);

        service.enqueue(docId, false);
        service.processQueue();

        assertEquals("hello ocr", doc.getTextContent());
        assertEquals("READY", doc.getMetadata().get("ocrStatus"));
        assertEquals("ml-service", doc.getMetadata().get("ocrProvider"));
        assertEquals("eng", doc.getMetadata().get("ocrLanguage"));
        assertEquals(1, doc.getMetadata().get("ocrPages"));
        assertEquals(false, doc.getMetadata().get("ocrTruncated"));
        assertEquals("hello ocr".length(), doc.getMetadata().get("ocrChars"));

        Object lastUpdated = doc.getMetadata().get("ocrLastUpdated");
        assertNotNull(lastUpdated);
        assertDoesNotThrow(() -> Instant.parse(lastUpdated.toString()));

        verify(searchIndexService, atLeastOnce()).updateDocument(any(Document.class));
    }

    @Test
    void processQueueEnrichesCorrespondentWhenEnabled() throws Exception {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        ContentService contentService = mock(ContentService.class);
        CorrespondentService correspondentService = mock(CorrespondentService.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        MLServiceClient mlServiceClient = mock(MLServiceClient.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        OcrQueueService service = new OcrQueueService(documentRepository, contentService, correspondentService, searchIndexService, mlServiceClient, redisTemplate);
        ReflectionTestUtils.setField(service, "ocrEnabled", true);
        ReflectionTestUtils.setField(service, "queueEnabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 1);
        ReflectionTestUtils.setField(service, "maxAttempts", 1);
        ReflectionTestUtils.setField(service, "retryDelayMs", 10L);
        ReflectionTestUtils.setField(service, "maxBytes", 1024L * 1024L);
        ReflectionTestUtils.setField(service, "maxPages", 2);
        ReflectionTestUtils.setField(service, "maxChars", 5000);
        ReflectionTestUtils.setField(service, "ocrLanguage", "eng");
        ReflectionTestUtils.setField(service, "enrichCorrespondentEnabled", true);

        UUID docId = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(docId);
        doc.setMetadata(new HashMap<>());
        doc.setName("sample.pdf");
        doc.setMimeType("application/pdf");
        doc.setFileSize(100L);
        doc.setContentId("content-1");
        doc.setTextContent("");
        doc.setCorrespondent(null);

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contentService.getContent("content-1")).thenReturn(new ByteArrayInputStream("pdf".getBytes()));

        MLServiceClient.OcrResult ocrResult = MLServiceClient.OcrResult.builder()
            .success(true)
            .text("hello ocr")
            .pages(1)
            .language("eng")
            .truncated(false)
            .build();
        when(mlServiceClient.ocr(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(ocrResult);

        Correspondent correspondent = new Correspondent();
        correspondent.setName("ACME");
        when(correspondentService.matchCorrespondent("hello ocr")).thenReturn(correspondent);

        service.enqueue(docId, false);
        service.processQueue();

        assertNotNull(doc.getCorrespondent());
        assertEquals("ACME", doc.getCorrespondent().getName());
    }
}
