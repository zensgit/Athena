package com.ecm.core.queue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RedisScheduledQueueStoreTest {

    @Test
    void persistsJobsAndLocksPreventDoubleClaim() {
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

            String keyPrefix = "test:" + UUID.randomUUID();
            RedisScheduledQueueStore store = new RedisScheduledQueueStore(
                template,
                keyPrefix + ":schedule",
                keyPrefix + ":attempts",
                keyPrefix + ":lock:",
                Duration.ofSeconds(30)
            );

            UUID docId = UUID.randomUUID();
            Instant now = Instant.now();

            RedisScheduledQueueStore.Entry first = store.enqueueIfAbsent(docId, now);
            assertEquals(docId, first.documentId());
            assertEquals(0, first.attempts());

            // De-dup: second enqueue returns the same scheduled job.
            RedisScheduledQueueStore.Entry second = store.enqueueIfAbsent(docId, now.plusSeconds(60));
            assertEquals(docId, second.documentId());
            assertEquals(0, second.attempts());
            assertNotNull(second.nextAttemptAt());

            // Claim due once.
            List<RedisScheduledQueueStore.Entry> claimed = store.claimDue(5, now.plusSeconds(1));
            assertEquals(1, claimed.size());
            assertEquals(docId, claimed.get(0).documentId());

            // Lock prevents double-claim until released.
            List<RedisScheduledQueueStore.Entry> claimedAgain = store.claimDue(5, now.plusSeconds(1));
            assertTrue(claimedAgain.isEmpty());

            // Schedule retry and release lock; should not be due until later.
            Instant next = now.plusSeconds(2);
            store.scheduleRetry(docId, 1, next);
            store.release(docId);

            assertTrue(store.claimDue(5, now.plusSeconds(1)).isEmpty());
            List<RedisScheduledQueueStore.Entry> afterDelay = store.claimDue(5, now.plusSeconds(3));
            assertEquals(1, afterDelay.size());
            assertEquals(1, afterDelay.get(0).attempts());

            store.complete(docId);
            assertNull(store.getOrNull(docId));

            factory.destroy();
        }
    }
}
