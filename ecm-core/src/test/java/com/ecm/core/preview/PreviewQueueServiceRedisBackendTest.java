package com.ecm.core.preview;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.search.SearchIndexService;
import com.ecm.core.service.RenditionResourceSyncService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PreviewQueueServiceRedisBackendTest {

    private PreviewQueueService newPreviewQueueService(
        DocumentRepository documentRepository,
        PreviewService previewService,
        SearchIndexService searchIndexService,
        StringRedisTemplate template,
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
            template,
            previewTransformTraceBuffer,
            previewFailurePolicyRegistry,
            previewRenditionPreventionRegistry,
            previewDeadLetterRegistry
        );
    }

    @Test
    void processesQueuedPreviewFromRedisAndClearsSchedule() throws Exception {
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
            PreviewService previewService = mock(PreviewService.class);
            SearchIndexService searchIndexService = mock(SearchIndexService.class);
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
                template,
                previewTransformTraceBuffer,
                previewFailurePolicyRegistry,
                previewRenditionPreventionRegistry,
                previewDeadLetterRegistry
            );

            ReflectionTestUtils.setField(service, "queueEnabled", true);
            ReflectionTestUtils.setField(service, "batchSize", 1);
            ReflectionTestUtils.setField(service, "maxAttempts", 1);
            ReflectionTestUtils.setField(service, "retryDelayMs", 10L);
            ReflectionTestUtils.setField(service, "runAsUser", "admin");
            ReflectionTestUtils.setField(service, "queueBackend", "redis");

            UUID docId = UUID.randomUUID();
            Document document = new Document();
            document.setId(docId);
            document.setPreviewStatus(PreviewStatus.FAILED);

            when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
            when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

            PreviewResult ok = new PreviewResult();
            ok.setSupported(true);
            ok.setMessage("ok");
            when(previewService.generatePreview(any(Document.class))).thenReturn(ok);

            PreviewQueueService.PreviewQueueStatus enqueued = service.enqueue(docId, true);
            assertTrue(enqueued.queued());

            service.processQueue();

            verify(previewService, atLeastOnce()).generatePreview(any(Document.class));

            assertNull(template.opsForZSet().score("ecm:queue:preview:schedule", docId.toString()));
            assertNull(template.opsForHash().get("ecm:queue:preview:attempts", docId.toString()));

            factory.destroy();
        }
    }

    @Test
    void concurrentRedisEnqueueKeepsSingleScheduledEntry() throws Exception {
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
            PreviewService previewService = mock(PreviewService.class);
            SearchIndexService searchIndexService = mock(SearchIndexService.class);
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
                template,
                previewTransformTraceBuffer,
                previewFailurePolicyRegistry,
                previewRenditionPreventionRegistry,
                previewDeadLetterRegistry
            );

            ReflectionTestUtils.setField(service, "queueEnabled", true);
            ReflectionTestUtils.setField(service, "batchSize", 2);
            ReflectionTestUtils.setField(service, "maxAttempts", 1);
            ReflectionTestUtils.setField(service, "retryDelayMs", 10L);
            ReflectionTestUtils.setField(service, "runAsUser", "admin");
            ReflectionTestUtils.setField(service, "queueBackend", "redis");

            UUID docId = UUID.randomUUID();
            Document document = new Document();
            document.setId(docId);
            document.setPreviewStatus(PreviewStatus.FAILED);

            when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
            when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

            int threadCount = 16;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<PreviewQueueService.PreviewQueueStatus>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> service.enqueue(docId, true)));
            }
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

            int queuedResponses = 0;
            for (Future<PreviewQueueService.PreviewQueueStatus> future : futures) {
                PreviewQueueService.PreviewQueueStatus status = future.get(10, TimeUnit.SECONDS);
                if (status != null && status.queued()) {
                    queuedResponses += 1;
                }
            }
            assertEquals(threadCount, queuedResponses);

            assertNotNull(template.opsForZSet().score("ecm:queue:preview:schedule", docId.toString()));
            Long zCard = template.opsForZSet().zCard("ecm:queue:preview:schedule");
            assertNotNull(zCard);
            assertEquals(1L, zCard.longValue());

            factory.destroy();
        }
    }
}
