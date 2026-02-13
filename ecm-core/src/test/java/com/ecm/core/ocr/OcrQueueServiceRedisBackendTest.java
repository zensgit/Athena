package com.ecm.core.ocr;

import com.ecm.core.entity.Document;
import com.ecm.core.ml.MLServiceClient;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.search.SearchIndexService;
import com.ecm.core.service.ContentService;
import com.ecm.core.service.CorrespondentService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class OcrQueueServiceRedisBackendTest {

    @Test
    void processesQueuedOcrFromRedisAndClearsSchedule() throws Exception {
        DockerImageName image = DockerImageName.parse("redis:7-alpine");
        try (GenericContainer<?> redis = new GenericContainer<>(image).withExposedPorts(6379)) {
            try {
                redis.start();
            } catch (IllegalStateException e) {
                Assumptions.assumeTrue(false, "Docker not available for Testcontainers: " + e.getMessage());
            }

            LettuceConnectionFactory factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            factory.afterPropertiesSet();
            StringRedisTemplate template = new StringRedisTemplate(factory);
            template.afterPropertiesSet();

            DocumentRepository documentRepository = mock(DocumentRepository.class);
            ContentService contentService = mock(ContentService.class);
            CorrespondentService correspondentService = mock(CorrespondentService.class);
            SearchIndexService searchIndexService = mock(SearchIndexService.class);
            MLServiceClient mlServiceClient = mock(MLServiceClient.class);

            OcrQueueService service = new OcrQueueService(
                documentRepository,
                contentService,
                correspondentService,
                searchIndexService,
                mlServiceClient,
                template
            );

            ReflectionTestUtils.setField(service, "ocrEnabled", true);
            ReflectionTestUtils.setField(service, "queueEnabled", true);
            ReflectionTestUtils.setField(service, "batchSize", 1);
            ReflectionTestUtils.setField(service, "maxAttempts", 1);
            ReflectionTestUtils.setField(service, "retryDelayMs", 10L);
            ReflectionTestUtils.setField(service, "maxBytes", 1024L * 1024L);
            ReflectionTestUtils.setField(service, "maxPages", 2);
            ReflectionTestUtils.setField(service, "maxChars", 5000);
            ReflectionTestUtils.setField(service, "ocrLanguage", "eng");
            ReflectionTestUtils.setField(service, "queueBackend", "redis");

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

            OcrQueueService.OcrQueueStatus enqueued = service.enqueue(docId, false);
            assertTrue(enqueued.queued());

            service.processQueue();

            assertEquals("hello ocr", doc.getTextContent());
            assertEquals("READY", doc.getMetadata().get("ocrStatus"));

            assertNull(template.opsForZSet().score("ecm:queue:ocr:schedule", docId.toString()));
            assertNull(template.opsForHash().get("ecm:queue:ocr:attempts", docId.toString()));
            assertNull(template.opsForHash().get("ecm:queue:ocr:force", docId.toString()));

            factory.destroy();
        }
    }
}
