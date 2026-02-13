package com.ecm.core.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Minimal Redis-backed delayed queue store:
 * - schedules jobs in a ZSET by nextAttemptAt epoch millis (member=docId)
 * - tracks attempts in a HASH (field=docId)
 * - uses a per-doc lock key with TTL so jobs aren't lost on consumer crash
 *
 * This is intentionally simple (not a full distributed job system).
 */
@RequiredArgsConstructor
public class RedisScheduledQueueStore {

    private final StringRedisTemplate redis;
    private final String scheduleKey;
    private final String attemptsKey;
    private final String lockKeyPrefix;
    private final Duration lockTtl;

    public record Entry(UUID documentId, int attempts, Instant nextAttemptAt) {
    }

    public Entry getOrNull(UUID documentId) {
        if (documentId == null) {
            return null;
        }
        String member = documentId.toString();
        Double score = redis.opsForZSet().score(scheduleKey, member);
        if (score == null) {
            return null;
        }
        long epochMs = Math.round(score);
        int attempts = parseAttempts(redis.opsForHash().get(attemptsKey, member));
        return new Entry(documentId, attempts, Instant.ofEpochMilli(epochMs));
    }

    public Entry enqueueIfAbsent(UUID documentId, Instant nextAttemptAt) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");

        Entry existing = getOrNull(documentId);
        if (existing != null) {
            return existing;
        }

        String member = documentId.toString();
        redis.opsForZSet().add(scheduleKey, member, nextAttemptAt.toEpochMilli());
        // Don't overwrite existing attempts if a race creates it between getOrNull and add().
        redis.opsForHash().putIfAbsent(attemptsKey, member, "0");
        return new Entry(documentId, 0, nextAttemptAt);
    }

    public List<Entry> claimDue(int limit, Instant now) {
        int batch = Math.max(0, limit);
        if (batch == 0) {
            return List.of();
        }
        long nowMs = Objects.requireNonNull(now, "now").toEpochMilli();

        Set<ZSetOperations.TypedTuple<String>> due = redis.opsForZSet()
            .rangeByScoreWithScores(scheduleKey, 0, nowMs, 0, batch);

        if (due == null || due.isEmpty()) {
            return List.of();
        }

        List<Entry> claimed = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : due) {
            String member = tuple != null ? tuple.getValue() : null;
            Double score = tuple != null ? tuple.getScore() : null;
            if (member == null || score == null) {
                continue;
            }
            UUID documentId;
            try {
                documentId = UUID.fromString(member);
            } catch (IllegalArgumentException badId) {
                // Defensive cleanup: don't let invalid members block processing.
                redis.opsForZSet().remove(scheduleKey, member);
                redis.opsForHash().delete(attemptsKey, member);
                continue;
            }

            if (!acquireLock(member)) {
                continue;
            }

            int attempts = parseAttempts(redis.opsForHash().get(attemptsKey, member));
            claimed.add(new Entry(documentId, attempts, Instant.ofEpochMilli(Math.round(score))));
        }
        return claimed;
    }

    public void scheduleRetry(UUID documentId, int attempts, Instant nextAttemptAt) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        String member = documentId.toString();
        redis.opsForHash().put(attemptsKey, member, String.valueOf(Math.max(0, attempts)));
        redis.opsForZSet().add(scheduleKey, member, nextAttemptAt.toEpochMilli());
    }

    public void complete(UUID documentId) {
        if (documentId == null) {
            return;
        }
        String member = documentId.toString();
        redis.opsForZSet().remove(scheduleKey, member);
        redis.opsForHash().delete(attemptsKey, member);
        release(documentId);
    }

    public void release(UUID documentId) {
        if (documentId == null) {
            return;
        }
        redis.delete(lockKeyPrefix + documentId);
    }

    private boolean acquireLock(String member) {
        // Value is not used; TTL ensures crash-safe unlock.
        Boolean locked = redis.opsForValue().setIfAbsent(lockKeyPrefix + member, "1", lockTtl);
        return Boolean.TRUE.equals(locked);
    }

    private static int parseAttempts(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.toString().trim()));
        } catch (Exception ignored) {
            return 0;
        }
    }
}

