package com.ecm.core.preview;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewDeadLetterRegistryRedisBackendTest {

    @Test
    void usesRedisBackendWhenAvailable() throws Exception {
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

            PreviewDeadLetterRegistry registry = new PreviewDeadLetterRegistry();
            registry.setEnabled(true);
            registry.setRedisEnabled(true);
            registry.setMaxEntries(5000);
            registry.setTtlMs(600000L);
            ReflectionTestUtils.setField(registry, "redisTemplate", template);

            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();

            registry.record(first, "timeout", "TEMPORARY", "pdf", "QUEUE_RETRY_EXHAUSTED", 1);
            Thread.sleep(3L);
            registry.record(second, "unsupported", "UNSUPPORTED", "default", "QUEUE_TERMINAL", 1);
            Thread.sleep(3L);
            registry.record(first, "timeout again", "TEMPORARY", "pdf", "QUEUE_RETRY_EXHAUSTED", 3);
            registry.markReplayAttempt(first, java.time.Instant.parse("2026-03-06T14:50:00Z"));

            List<PreviewDeadLetterRegistry.DeadLetterEntry> items = registry.list(20);
            assertEquals(2, items.size());
            assertEquals(first, items.get(0).documentId());
            assertEquals("preview", items.get(0).renditionKey());
            assertEquals(3, items.get(0).attempts());
            assertEquals(2L, items.get(0).occurrences());
            assertEquals(1L, items.get(0).replayCount());
            assertEquals("2026-03-06T14:50:00Z", String.valueOf(items.get(0).lastReplayAt()));
            assertTrue(items.get(0).reason().contains("timeout"));

            assertEquals(2, registry.getItemCount());

            registry.remove(first, "preview");
            assertEquals(1, registry.getItemCount());
            List<PreviewDeadLetterRegistry.DeadLetterEntry> afterRemove = registry.list(20);
            assertEquals(1, afterRemove.size());
            assertEquals(second, afterRemove.get(0).documentId());

            factory.destroy();
        }
    }
}
