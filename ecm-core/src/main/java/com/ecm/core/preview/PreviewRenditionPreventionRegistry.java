package com.ecm.core.preview;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PreviewRenditionPreventionRegistry {

    private static final int MAX_LIST_LIMIT = 500;
    private static final int REDIS_SCAN_FACTOR = 4;
    private static final String REDIS_INDEX_KEY = "ecm:preview:prevention:index";
    private static final String REDIS_ENTRY_KEY_PREFIX = "ecm:preview:prevention:entry:";
    private static final Base64.Encoder REASON_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder REASON_DECODER = Base64.getUrlDecoder();

    private final ConcurrentHashMap<UUID, MutableBlockedEntry> blockedMap = new ConcurrentHashMap<>();
    private final Deque<UUID> blockedOrder = new ConcurrentLinkedDeque<>();

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private volatile boolean enabled = true;
    private volatile boolean redisEnabled = true;
    private volatile int maxBlocked = 5000;
    private volatile long ttlMs = 7L * 24L * 60L * 60L * 1000L;
    private volatile Set<String> autoBlockCategories = Set.of(
        PreviewFailureClassifier.CATEGORY_UNSUPPORTED,
        PreviewFailureClassifier.CATEGORY_PERMANENT
    );

    @Value("${ecm.preview.prevention.enabled:true}")
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Value("${ecm.preview.prevention.redis.enabled:true}")
    public void setRedisEnabled(boolean redisEnabled) {
        this.redisEnabled = redisEnabled;
    }

    @Value("${ecm.preview.prevention.max-blocked:5000}")
    public void setMaxBlocked(int maxBlocked) {
        this.maxBlocked = Math.max(100, maxBlocked);
    }

    @Value("${ecm.preview.prevention.ttl-ms:604800000}")
    public void setTtlMs(long ttlMs) {
        this.ttlMs = Math.max(60000L, ttlMs);
    }

    @Value("${ecm.preview.prevention.auto-block-categories:UNSUPPORTED,PERMANENT}")
    public void setAutoBlockCategories(String rawCategories) {
        if (rawCategories == null || rawCategories.isBlank()) {
            this.autoBlockCategories = Set.of();
            return;
        }
        this.autoBlockCategories = List.of(rawCategories.split(","))
            .stream()
            .map(value -> value == null ? "" : value.trim().toUpperCase(Locale.ROOT))
            .filter(value -> !value.isBlank())
            .collect(Collectors.toSet());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxBlocked() {
        return maxBlocked;
    }

    public int getBlockedCount() {
        if (useRedisBackend()) {
            try {
                Long size = redisTemplate.opsForZSet().size(REDIS_INDEX_KEY);
                return size == null ? 0 : (int) Math.min(size, Integer.MAX_VALUE);
            } catch (Exception e) {
                log.debug("Failed to read prevention blocked count from redis: {}", e.getMessage());
            }
        }
        return blockedMap.size();
    }

    public List<String> listAutoBlockCategories() {
        return autoBlockCategories.stream()
            .sorted()
            .toList();
    }

    public boolean shouldAutoBlock(String failureCategory) {
        if (!enabled || failureCategory == null || failureCategory.isBlank()) {
            return false;
        }
        return autoBlockCategories.contains(failureCategory.trim().toUpperCase(Locale.ROOT));
    }

    public void block(UUID documentId, String reason, String category) {
        if (!enabled || documentId == null) {
            return;
        }
        if (useRedisBackend() && blockRedis(documentId, reason, category)) {
            return;
        }

        MutableBlockedEntry entry = blockedMap.computeIfAbsent(
            documentId,
            id -> new MutableBlockedEntry(id, sanitizeReason(reason), normalizeCategory(category), Instant.now())
        );
        entry.update(sanitizeReason(reason), normalizeCategory(category));
        blockedOrder.remove(documentId);
        blockedOrder.addFirst(documentId);
        trimOverflowMemory();
    }

    public void unblock(UUID documentId) {
        if (documentId == null) {
            return;
        }
        if (useRedisBackend()) {
            try {
                redisTemplate.delete(redisEntryKey(documentId));
                redisTemplate.opsForZSet().remove(REDIS_INDEX_KEY, documentId.toString());
            } catch (Exception e) {
                log.debug("Failed to unblock prevention entry {} from redis: {}", documentId, e.getMessage());
            }
        }
        blockedMap.remove(documentId);
        blockedOrder.remove(documentId);
    }

    public BlockedEntry markBlockedHit(UUID documentId) {
        if (documentId == null) {
            return null;
        }
        if (useRedisBackend()) {
            BlockedEntry existing = readRedisEntry(documentId);
            if (existing != null) {
                BlockedEntry updated = new BlockedEntry(
                    existing.documentId(),
                    existing.reason(),
                    existing.category(),
                    existing.blockedAt(),
                    Instant.now(),
                    existing.hitCount() + 1
                );
                if (writeRedisEntry(updated)) {
                    return updated;
                }
            }
        }

        MutableBlockedEntry entry = blockedMap.get(documentId);
        if (entry == null) {
            return null;
        }
        entry.hit();
        return entry.snapshot();
    }

    public BlockedEntry get(UUID documentId) {
        if (documentId == null) {
            return null;
        }
        if (useRedisBackend()) {
            BlockedEntry redisEntry = readRedisEntry(documentId);
            if (redisEntry != null) {
                return redisEntry;
            }
        }
        MutableBlockedEntry memoryEntry = blockedMap.get(documentId);
        return memoryEntry == null ? null : memoryEntry.snapshot();
    }

    public List<BlockedEntry> list(int limit) {
        if (!enabled) {
            return List.of();
        }
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIST_LIMIT);
        if (useRedisBackend()) {
            List<BlockedEntry> redisEntries = listRedis(safeLimit);
            if (!redisEntries.isEmpty()) {
                return redisEntries;
            }
        }

        List<BlockedEntry> entries = new ArrayList<>();
        for (UUID documentId : blockedOrder) {
            MutableBlockedEntry entry = blockedMap.get(documentId);
            if (entry == null) {
                continue;
            }
            entries.add(entry.snapshot());
            if (entries.size() >= safeLimit) {
                break;
            }
        }
        return entries;
    }

    private boolean blockRedis(UUID documentId, String reason, String category) {
        try {
            Instant now = Instant.now();
            BlockedEntry existing = readRedisEntry(documentId);
            BlockedEntry next = new BlockedEntry(
                documentId,
                sanitizeReason(reason),
                normalizeCategory(category),
                existing != null ? existing.blockedAt() : now,
                now,
                existing != null ? existing.hitCount() : 0L
            );
            if (!writeRedisEntry(next)) {
                return false;
            }
            trimOverflowRedis();
            return true;
        } catch (Exception e) {
            log.debug("Failed to block prevention entry {} in redis: {}", documentId, e.getMessage());
            return false;
        }
    }

    private BlockedEntry readRedisEntry(UUID documentId) {
        if (!useRedisBackend()) {
            return null;
        }
        try {
            String key = redisEntryKey(documentId);
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null || raw.isBlank()) {
                redisTemplate.opsForZSet().remove(REDIS_INDEX_KEY, documentId.toString());
                return null;
            }
            BlockedEntry entry = deserialize(documentId, raw);
            if (entry == null) {
                redisTemplate.delete(key);
                redisTemplate.opsForZSet().remove(REDIS_INDEX_KEY, documentId.toString());
            }
            return entry;
        } catch (Exception e) {
            log.debug("Failed to read prevention entry {} from redis: {}", documentId, e.getMessage());
            return null;
        }
    }

    private boolean writeRedisEntry(BlockedEntry entry) {
        if (!useRedisBackend() || entry == null) {
            return false;
        }
        try {
            redisTemplate.opsForValue().set(
                redisEntryKey(entry.documentId()),
                serialize(entry),
                Duration.ofMillis(ttlMs)
            );
            redisTemplate.opsForZSet().add(
                REDIS_INDEX_KEY,
                entry.documentId().toString(),
                entry.lastHitAt().toEpochMilli()
            );
            return true;
        } catch (Exception e) {
            log.debug("Failed to write prevention entry {} to redis: {}", entry.documentId(), e.getMessage());
            return false;
        }
    }

    private List<BlockedEntry> listRedis(int limit) {
        try {
            int scanLimit = Math.max(limit * REDIS_SCAN_FACTOR, 50);
            var ids = redisTemplate.opsForZSet().reverseRange(REDIS_INDEX_KEY, 0, scanLimit - 1L);
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }
            List<BlockedEntry> output = new ArrayList<>();
            for (String idValue : ids) {
                if (idValue == null || idValue.isBlank()) {
                    continue;
                }
                UUID documentId;
                try {
                    documentId = UUID.fromString(idValue);
                } catch (IllegalArgumentException ignored) {
                    redisTemplate.opsForZSet().remove(REDIS_INDEX_KEY, idValue);
                    continue;
                }
                BlockedEntry entry = readRedisEntry(documentId);
                if (entry == null) {
                    continue;
                }
                output.add(entry);
                if (output.size() >= limit) {
                    break;
                }
            }
            return output;
        } catch (Exception e) {
            log.debug("Failed to list prevention entries from redis: {}", e.getMessage());
            return List.of();
        }
    }

    private void trimOverflowRedis() {
        if (!useRedisBackend()) {
            return;
        }
        try {
            Long size = redisTemplate.opsForZSet().size(REDIS_INDEX_KEY);
            if (size == null || size <= maxBlocked) {
                return;
            }
            long excess = size - maxBlocked;
            if (excess <= 0) {
                return;
            }
            var evicted = redisTemplate.opsForZSet().range(REDIS_INDEX_KEY, 0, excess - 1);
            redisTemplate.opsForZSet().removeRange(REDIS_INDEX_KEY, 0, excess - 1);
            if (evicted != null) {
                for (String id : evicted) {
                    if (id != null && !id.isBlank()) {
                        redisTemplate.delete(REDIS_ENTRY_KEY_PREFIX + id);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to trim prevention entries in redis: {}", e.getMessage());
        }
    }

    private void trimOverflowMemory() {
        while (blockedOrder.size() > maxBlocked) {
            UUID oldest = blockedOrder.pollLast();
            if (oldest == null) {
                return;
            }
            blockedMap.remove(oldest);
        }
    }

    private String serialize(BlockedEntry entry) {
        String encodedReason = REASON_ENCODER.encodeToString(entry.reason().getBytes(StandardCharsets.UTF_8));
        return entry.blockedAt().toEpochMilli()
            + "|"
            + entry.lastHitAt().toEpochMilli()
            + "|"
            + entry.hitCount()
            + "|"
            + normalizeCategory(entry.category())
            + "|"
            + encodedReason;
    }

    private BlockedEntry deserialize(UUID documentId, String raw) {
        try {
            String[] parts = raw.split("\\|", 5);
            if (parts.length < 5) {
                return null;
            }
            long blockedAtEpoch = Math.max(0L, Long.parseLong(parts[0]));
            long lastHitAtEpoch = Math.max(0L, Long.parseLong(parts[1]));
            long hitCount = Math.max(0L, Long.parseLong(parts[2]));
            String category = normalizeCategory(parts[3]);
            String reason = new String(REASON_DECODER.decode(parts[4]), StandardCharsets.UTF_8);
            return new BlockedEntry(
                documentId,
                sanitizeReason(reason),
                category,
                Instant.ofEpochMilli(blockedAtEpoch),
                Instant.ofEpochMilli(lastHitAtEpoch),
                hitCount
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String redisEntryKey(UUID documentId) {
        return REDIS_ENTRY_KEY_PREFIX + documentId;
    }

    private boolean useRedisBackend() {
        return enabled && redisEnabled && redisTemplate != null;
    }

    private static String sanitizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Blocked by prevention policy";
        }
        String normalized = reason.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 512) {
            return normalized;
        }
        return normalized.substring(0, 509) + "...";
    }

    private static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "UNKNOWN";
        }
        return category.trim().toUpperCase(Locale.ROOT);
    }

    private static final class MutableBlockedEntry {
        private final UUID documentId;
        private final Instant blockedAt;
        private final AtomicLong hitCount = new AtomicLong(0L);

        private volatile Instant lastHitAt;
        private volatile String reason;
        private volatile String category;

        private MutableBlockedEntry(UUID documentId, String reason, String category, Instant blockedAt) {
            this.documentId = documentId;
            this.reason = reason;
            this.category = category;
            this.blockedAt = blockedAt;
            this.lastHitAt = blockedAt;
        }

        private void update(String reason, String category) {
            this.reason = reason;
            this.category = category;
            this.lastHitAt = Instant.now();
        }

        private void hit() {
            hitCount.incrementAndGet();
            lastHitAt = Instant.now();
        }

        private BlockedEntry snapshot() {
            return new BlockedEntry(
                documentId,
                reason,
                category,
                blockedAt,
                lastHitAt,
                hitCount.get()
            );
        }
    }

    public record BlockedEntry(
        UUID documentId,
        String reason,
        String category,
        Instant blockedAt,
        Instant lastHitAt,
        long hitCount
    ) {}
}
