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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class PreviewDeadLetterRegistry {

    private static final int MAX_LIST_LIMIT = 500;
    private static final int REDIS_SCAN_FACTOR = 4;
    private static final int MAX_REASON_LENGTH = 512;
    private static final int MAX_POLICY_KEY_LENGTH = 64;
    private static final int MAX_STAGE_LENGTH = 64;
    private static final int MAX_RENDITION_KEY_LENGTH = 64;
    private static final String DEFAULT_RENDITION_KEY = "preview";
    private static final String ENTRY_KEY_SEPARATOR = "|";
    private static final String REDIS_INDEX_KEY = "ecm:preview:deadletter:index";
    private static final String REDIS_ENTRY_KEY_PREFIX = "ecm:preview:deadletter:entry:";
    private static final Base64.Encoder REASON_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder REASON_DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder ENTRY_KEY_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final ConcurrentHashMap<String, MutableDeadLetterEntry> entries = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> entryOrder = new ConcurrentLinkedDeque<>();

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private volatile boolean enabled = true;
    private volatile boolean redisEnabled = true;
    private volatile int maxEntries = 5000;
    private volatile long ttlMs = 7L * 24L * 60L * 60L * 1000L;

    @Value("${ecm.preview.dead-letter.enabled:true}")
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Value("${ecm.preview.dead-letter.redis.enabled:true}")
    public void setRedisEnabled(boolean redisEnabled) {
        this.redisEnabled = redisEnabled;
    }

    @Value("${ecm.preview.dead-letter.max-entries:5000}")
    public void setMaxEntries(int maxEntries) {
        this.maxEntries = Math.max(100, maxEntries);
    }

    @Value("${ecm.preview.dead-letter.ttl-ms:604800000}")
    public void setTtlMs(long ttlMs) {
        this.ttlMs = Math.max(60000L, ttlMs);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isRedisEnabled() {
        return redisEnabled;
    }

    public boolean isRedisActive() {
        return useRedisBackend();
    }

    public long getTtlMs() {
        return ttlMs;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public int getItemCount() {
        if (useRedisBackend()) {
            try {
                Long size = redisTemplate.opsForZSet().size(REDIS_INDEX_KEY);
                if (size != null) {
                    return (int) Math.min(size, Integer.MAX_VALUE);
                }
            } catch (Exception e) {
                log.debug("Failed to read dead-letter count from redis: {}", e.getMessage());
            }
        }
        return entries.size();
    }

    public static String defaultRenditionKey() {
        return DEFAULT_RENDITION_KEY;
    }

    public static String buildEntryKey(UUID documentId, String renditionKey) {
        if (documentId == null) {
            return null;
        }
        return documentId + ENTRY_KEY_SEPARATOR + normalizeRenditionKey(renditionKey);
    }

    public void record(
        UUID documentId,
        String reason,
        String category,
        String policyKey,
        String sourceStage,
        int attempts
    ) {
        record(documentId, DEFAULT_RENDITION_KEY, reason, category, policyKey, sourceStage, attempts);
    }

    public void record(
        UUID documentId,
        String renditionKey,
        String reason,
        String category,
        String policyKey,
        String sourceStage,
        int attempts
    ) {
        if (!enabled || documentId == null) {
            return;
        }
        String safeReason = sanitizeReason(reason);
        String safeCategory = normalizeCategory(category);
        String safePolicyKey = normalizePolicyKey(policyKey);
        String safeStage = normalizeStage(sourceStage);
        String safeRenditionKey = normalizeRenditionKey(renditionKey);
        String entryKey = buildEntryKey(documentId, safeRenditionKey);
        int safeAttempts = Math.max(0, attempts);
        Instant now = Instant.now();

        if (useRedisBackend() && recordRedis(
            documentId,
            safeRenditionKey,
            safeReason,
            safeCategory,
            safePolicyKey,
            safeStage,
            safeAttempts,
            now
        )) {
            return;
        }

        MutableDeadLetterEntry entry = entries.computeIfAbsent(
            entryKey,
            ignored -> new MutableDeadLetterEntry(
                entryKey,
                documentId,
                safeRenditionKey,
                safeReason,
                safeCategory,
                safePolicyKey,
                safeStage,
                now,
                safeAttempts
            )
        );
        entry.update(safeReason, safeCategory, safePolicyKey, safeStage, now, safeAttempts);
        entryOrder.remove(entryKey);
        entryOrder.addFirst(entryKey);
        trimOverflowMemory();
    }

    public void remove(UUID documentId) {
        if (documentId == null) {
            return;
        }
        removeByEntryKeys(findEntryKeysByDocumentId(documentId));
    }

    public void remove(UUID documentId, String renditionKey) {
        String entryKey = buildEntryKey(documentId, renditionKey);
        if (entryKey == null) {
            return;
        }
        removeByEntryKey(entryKey);
    }

    public void removeByEntryKey(String entryKey) {
        if (entryKey == null || entryKey.isBlank()) {
            return;
        }
        if (useRedisBackend()) {
            try {
                redisTemplate.delete(redisEntryKey(entryKey));
                redisTemplate.opsForZSet().remove(REDIS_INDEX_KEY, entryKey);
            } catch (Exception e) {
                log.debug("Failed to remove dead-letter entry {} from redis: {}", entryKey, e.getMessage());
            }
        }
        entries.remove(entryKey);
        entryOrder.remove(entryKey);
    }

    public DeadLetterEntry findByEntryKey(String entryKey) {
        if (!enabled || entryKey == null || entryKey.isBlank()) {
            return null;
        }
        String normalizedEntryKey = normalizeEntryKey(entryKey);
        if (normalizedEntryKey == null) {
            return null;
        }
        if (useRedisBackend()) {
            DeadLetterEntry redisEntry = readRedisEntry(resolveRedisEntryRef(normalizedEntryKey));
            if (redisEntry != null) {
                return redisEntry;
            }
        }
        MutableDeadLetterEntry entry = entries.get(normalizedEntryKey);
        return entry != null ? entry.snapshot() : null;
    }

    public List<DeadLetterEntry> list(int limit) {
        if (!enabled) {
            return List.of();
        }
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIST_LIMIT);
        if (useRedisBackend()) {
            List<DeadLetterEntry> redisEntries = listRedis(safeLimit);
            if (!redisEntries.isEmpty()) {
                return redisEntries;
            }
        }

        List<DeadLetterEntry> output = new ArrayList<>();
        for (String entryKey : entryOrder) {
            MutableDeadLetterEntry entry = entries.get(entryKey);
            if (entry == null) {
                continue;
            }
            output.add(entry.snapshot());
            if (output.size() >= safeLimit) {
                break;
            }
        }
        return output;
    }

    public void markReplayAttempt(UUID documentId, Instant replayedAt) {
        if (!enabled || documentId == null) {
            return;
        }
        Instant safeReplayAt = replayedAt != null ? replayedAt : Instant.now();
        for (String entryKey : findEntryKeysByDocumentId(documentId)) {
            markReplayAttemptByEntryKey(entryKey, safeReplayAt);
        }
    }

    public void markReplayAttempt(UUID documentId, String renditionKey, Instant replayedAt) {
        String entryKey = buildEntryKey(documentId, renditionKey);
        if (entryKey == null) {
            return;
        }
        markReplayAttemptByEntryKey(entryKey, replayedAt);
    }

    public void markReplayAttemptByEntryKey(String entryKey, Instant replayedAt) {
        if (!enabled || entryKey == null || entryKey.isBlank()) {
            return;
        }
        String normalizedEntryKey = normalizeEntryKey(entryKey);
        if (normalizedEntryKey == null) {
            return;
        }
        Instant safeReplayAt = replayedAt != null ? replayedAt : Instant.now();
        if (useRedisBackend()) {
            try {
                DeadLetterEntry existing = readRedisEntry(resolveRedisEntryRef(normalizedEntryKey));
                if (existing != null) {
                    DeadLetterEntry updated = new DeadLetterEntry(
                        existing.entryKey(),
                        existing.documentId(),
                        existing.renditionKey(),
                        existing.reason(),
                        existing.category(),
                        existing.policyKey(),
                        existing.sourceStage(),
                        existing.failedAt(),
                        existing.attempts(),
                        existing.occurrences(),
                        safeReplayAt,
                        existing.replayCount() + 1L
                    );
                    if (writeRedisEntry(updated)) {
                        return;
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to mark dead-letter replay attempt {} in redis: {}", normalizedEntryKey, e.getMessage());
            }
        }
        MutableDeadLetterEntry entry = entries.get(normalizedEntryKey);
        if (entry != null) {
            entry.markReplayAttempt(safeReplayAt);
        }
    }

    private boolean recordRedis(
        UUID documentId,
        String renditionKey,
        String reason,
        String category,
        String policyKey,
        String sourceStage,
        int attempts,
        Instant failedAt
    ) {
        String entryKey = buildEntryKey(documentId, renditionKey);
        if (entryKey == null) {
            return false;
        }
        try {
            DeadLetterEntry existing = readRedisEntry(resolveRedisEntryRef(entryKey));
            long occurrences = existing != null ? existing.occurrences() + 1L : 1L;
            DeadLetterEntry next = new DeadLetterEntry(
                entryKey,
                documentId,
                renditionKey,
                reason,
                category,
                policyKey,
                sourceStage,
                failedAt,
                attempts,
                occurrences,
                existing != null ? existing.lastReplayAt() : null,
                existing != null ? existing.replayCount() : 0L
            );
            if (!writeRedisEntry(next)) {
                return false;
            }
            trimOverflowRedis();
            return true;
        } catch (Exception e) {
            log.debug("Failed to record dead-letter entry {} in redis: {}", entryKey, e.getMessage());
            return false;
        }
    }

    private DeadLetterEntry readRedisEntry(RedisEntryRef ref) {
        if (!useRedisBackend() || ref == null || ref.entryKey() == null) {
            return null;
        }
        try {
            String raw = redisTemplate.opsForValue().get(redisEntryKey(ref.entryKey()));
            if ((raw == null || raw.isBlank()) && ref.legacyDocumentKey() != null) {
                raw = redisTemplate.opsForValue().get(redisLegacyEntryKey(ref.legacyDocumentKey()));
            }
            if (raw == null || raw.isBlank()) {
                redisTemplate.opsForZSet().remove(REDIS_INDEX_KEY, ref.indexMember());
                return null;
            }
            DeadLetterEntry entry = deserialize(ref.entryKey(), ref.documentId(), ref.renditionKey(), raw);
            if (entry == null) {
                redisTemplate.delete(redisEntryKey(ref.entryKey()));
                if (ref.legacyDocumentKey() != null) {
                    redisTemplate.delete(redisLegacyEntryKey(ref.legacyDocumentKey()));
                }
                redisTemplate.opsForZSet().remove(REDIS_INDEX_KEY, ref.indexMember());
            }
            return entry;
        } catch (Exception e) {
            log.debug("Failed to read dead-letter entry {} from redis: {}", ref.entryKey(), e.getMessage());
            return null;
        }
    }

    private boolean writeRedisEntry(DeadLetterEntry entry) {
        if (!useRedisBackend() || entry == null) {
            return false;
        }
        try {
            redisTemplate.opsForValue().set(
                redisEntryKey(entry.entryKey()),
                serialize(entry),
                Duration.ofMillis(ttlMs)
            );
            redisTemplate.opsForZSet().add(
                REDIS_INDEX_KEY,
                entry.entryKey(),
                entry.failedAt().toEpochMilli()
            );
            return true;
        } catch (Exception e) {
            log.debug("Failed to write dead-letter entry {} to redis: {}", entry.entryKey(), e.getMessage());
            return false;
        }
    }

    private List<DeadLetterEntry> listRedis(int limit) {
        try {
            int scanLimit = Math.max(limit * REDIS_SCAN_FACTOR, 50);
            var members = redisTemplate.opsForZSet().reverseRange(REDIS_INDEX_KEY, 0, scanLimit - 1L);
            if (members == null || members.isEmpty()) {
                return List.of();
            }
            List<DeadLetterEntry> output = new ArrayList<>();
            for (String member : members) {
                RedisEntryRef ref = resolveRedisEntryRef(member);
                if (ref == null) {
                    if (member != null && !member.isBlank()) {
                        redisTemplate.opsForZSet().remove(REDIS_INDEX_KEY, member);
                    }
                    continue;
                }
                DeadLetterEntry entry = readRedisEntry(ref);
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
            log.debug("Failed to list dead-letter entries from redis: {}", e.getMessage());
            return List.of();
        }
    }

    private Set<String> findEntryKeysByDocumentId(UUID documentId) {
        if (!enabled || documentId == null) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        if (useRedisBackend()) {
            keys.addAll(findEntryKeysByDocumentIdRedis(documentId));
        }
        keys.addAll(findEntryKeysByDocumentIdMemory(documentId));
        return keys;
    }

    private Set<String> findEntryKeysByDocumentIdMemory(UUID documentId) {
        String docPrefix = documentId + ENTRY_KEY_SEPARATOR;
        Set<String> keys = new LinkedHashSet<>();
        for (MutableDeadLetterEntry entry : entries.values()) {
            if (entry != null && documentId.equals(entry.documentId)) {
                keys.add(entry.entryKey);
            }
        }
        // Safety for legacy/inconsistent in-memory keys.
        for (String entryKey : entryOrder) {
            if (entryKey != null && entryKey.startsWith(docPrefix)) {
                keys.add(entryKey);
            }
        }
        return keys;
    }

    private Set<String> findEntryKeysByDocumentIdRedis(UUID documentId) {
        try {
            long maxScan = Math.max((long) maxEntries * REDIS_SCAN_FACTOR, 100L);
            var members = redisTemplate.opsForZSet().reverseRange(REDIS_INDEX_KEY, 0, maxScan - 1L);
            if (members == null || members.isEmpty()) {
                return Set.of();
            }
            Set<String> keys = new LinkedHashSet<>();
            for (String member : members) {
                RedisEntryRef ref = resolveRedisEntryRef(member);
                if (ref != null && documentId.equals(ref.documentId())) {
                    keys.add(ref.entryKey());
                }
            }
            return keys;
        } catch (Exception e) {
            log.debug("Failed to list dead-letter redis entry keys for {}: {}", documentId, e.getMessage());
            return Set.of();
        }
    }

    private void removeByEntryKeys(Set<String> entryKeys) {
        if (entryKeys == null || entryKeys.isEmpty()) {
            return;
        }
        for (String entryKey : entryKeys) {
            removeByEntryKey(entryKey);
        }
    }

    private void trimOverflowRedis() {
        if (!useRedisBackend()) {
            return;
        }
        try {
            Long size = redisTemplate.opsForZSet().size(REDIS_INDEX_KEY);
            if (size == null || size <= maxEntries) {
                return;
            }
            long excess = size - maxEntries;
            if (excess <= 0) {
                return;
            }
            var evicted = redisTemplate.opsForZSet().range(REDIS_INDEX_KEY, 0, excess - 1);
            redisTemplate.opsForZSet().removeRange(REDIS_INDEX_KEY, 0, excess - 1);
            if (evicted != null) {
                for (String entryKey : evicted) {
                    if (entryKey == null || entryKey.isBlank()) {
                        continue;
                    }
                    redisTemplate.delete(redisEntryKey(entryKey));
                    RedisEntryRef ref = resolveRedisEntryRef(entryKey);
                    if (ref != null && ref.legacyDocumentKey() != null) {
                        redisTemplate.delete(redisLegacyEntryKey(ref.legacyDocumentKey()));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to trim dead-letter entries in redis: {}", e.getMessage());
        }
    }

    private void trimOverflowMemory() {
        while (entryOrder.size() > maxEntries) {
            String oldest = entryOrder.pollLast();
            if (oldest == null) {
                return;
            }
            entries.remove(oldest);
        }
    }

    private String serialize(DeadLetterEntry entry) {
        String encodedReason = REASON_ENCODER.encodeToString(entry.reason().getBytes(StandardCharsets.UTF_8));
        return entry.failedAt().toEpochMilli()
            + "|"
            + entry.attempts()
            + "|"
            + entry.occurrences()
            + "|"
            + (entry.lastReplayAt() != null ? entry.lastReplayAt().toEpochMilli() : 0L)
            + "|"
            + entry.replayCount()
            + "|"
            + normalizeCategory(entry.category())
            + "|"
            + normalizePolicyKey(entry.policyKey())
            + "|"
            + normalizeStage(entry.sourceStage())
            + "|"
            + normalizeRenditionKey(entry.renditionKey())
            + "|"
            + encodedReason;
    }

    private DeadLetterEntry deserialize(String entryKey, UUID documentId, String renditionKey, String raw) {
        try {
            String[] parts = raw.split("\\|", 10);
            if (parts.length < 7) {
                return null;
            }
            long failedAtEpoch = Math.max(0L, Long.parseLong(parts[0]));
            int attempts = Math.max(0, Integer.parseInt(parts[1]));
            long occurrences = Math.max(0L, Long.parseLong(parts[2]));
            long lastReplayAtEpoch = 0L;
            long replayCount = 0L;
            int baseIndex = 3;
            if (parts.length >= 9) {
                lastReplayAtEpoch = Math.max(0L, Long.parseLong(parts[3]));
                replayCount = Math.max(0L, Long.parseLong(parts[4]));
                baseIndex = 5;
            }
            String category = normalizeCategory(parts[baseIndex]);
            String policyKey = normalizePolicyKey(parts[baseIndex + 1]);
            String sourceStage = normalizeStage(parts[baseIndex + 2]);

            String resolvedRenditionKey = normalizeRenditionKey(renditionKey);
            String reasonRaw;
            if (parts.length >= 10) {
                resolvedRenditionKey = normalizeRenditionKey(parts[baseIndex + 3]);
                reasonRaw = parts[baseIndex + 4];
            } else {
                // Legacy payload without rendition key.
                reasonRaw = parts[baseIndex + 3];
            }
            String reason = new String(REASON_DECODER.decode(reasonRaw), StandardCharsets.UTF_8);
            String normalizedEntryKey = buildEntryKey(documentId, resolvedRenditionKey);
            return new DeadLetterEntry(
                normalizedEntryKey,
                documentId,
                resolvedRenditionKey,
                sanitizeReason(reason),
                category,
                policyKey,
                sourceStage,
                Instant.ofEpochMilli(failedAtEpoch),
                attempts,
                occurrences,
                lastReplayAtEpoch > 0L ? Instant.ofEpochMilli(lastReplayAtEpoch) : null,
                replayCount
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String redisEntryKey(String entryKey) {
        String encoded = ENTRY_KEY_ENCODER.encodeToString(entryKey.getBytes(StandardCharsets.UTF_8));
        return REDIS_ENTRY_KEY_PREFIX + encoded;
    }

    private String redisLegacyEntryKey(String documentId) {
        return REDIS_ENTRY_KEY_PREFIX + documentId;
    }

    private RedisEntryRef resolveRedisEntryRef(String member) {
        if (member == null || member.isBlank()) {
            return null;
        }
        String normalizedMember = normalizeEntryKey(member);
        if (normalizedMember != null) {
            String[] parts = normalizedMember.split("\\|", 2);
            return new RedisEntryRef(
                member,
                normalizedMember,
                UUID.fromString(parts[0]),
                normalizeRenditionKey(parts[1]),
                null
            );
        }
        try {
            UUID legacyDocumentId = UUID.fromString(member.trim());
            String entryKey = buildEntryKey(legacyDocumentId, DEFAULT_RENDITION_KEY);
            return new RedisEntryRef(
                member,
                entryKey,
                legacyDocumentId,
                DEFAULT_RENDITION_KEY,
                legacyDocumentId.toString()
            );
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean useRedisBackend() {
        return enabled && redisEnabled && redisTemplate != null;
    }

    private static String normalizeEntryKey(String entryKey) {
        if (entryKey == null || entryKey.isBlank()) {
            return null;
        }
        String[] parts = entryKey.trim().split("\\|", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            UUID documentId = UUID.fromString(parts[0].trim());
            return buildEntryKey(documentId, parts[1]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String sanitizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Preview failed without explicit reason";
        }
        String normalized = reason.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_REASON_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_REASON_LENGTH - 3) + "...";
    }

    private static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "UNKNOWN";
        }
        return category.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizePolicyKey(String policyKey) {
        if (policyKey == null || policyKey.isBlank()) {
            return "default";
        }
        String normalized = policyKey.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() <= MAX_POLICY_KEY_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_POLICY_KEY_LENGTH);
    }

    private static String normalizeStage(String sourceStage) {
        if (sourceStage == null || sourceStage.isBlank()) {
            return "QUEUE_TERMINAL";
        }
        String normalized = sourceStage.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "_");
        if (normalized.length() <= MAX_STAGE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_STAGE_LENGTH);
    }

    private static String normalizeRenditionKey(String renditionKey) {
        if (renditionKey == null || renditionKey.isBlank()) {
            return DEFAULT_RENDITION_KEY;
        }
        String normalized = renditionKey.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
        if (normalized.isBlank()) {
            return DEFAULT_RENDITION_KEY;
        }
        if (normalized.length() <= MAX_RENDITION_KEY_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_RENDITION_KEY_LENGTH);
    }

    private static final class MutableDeadLetterEntry {
        private final String entryKey;
        private final UUID documentId;
        private final String renditionKey;
        private final AtomicLong occurrences = new AtomicLong(0L);
        private final AtomicLong replayCount = new AtomicLong(0L);

        private volatile String reason;
        private volatile String category;
        private volatile String policyKey;
        private volatile String sourceStage;
        private volatile Instant failedAt;
        private volatile Instant lastReplayAt;
        private volatile int attempts;

        private MutableDeadLetterEntry(
            String entryKey,
            UUID documentId,
            String renditionKey,
            String reason,
            String category,
            String policyKey,
            String sourceStage,
            Instant failedAt,
            int attempts
        ) {
            this.entryKey = entryKey;
            this.documentId = documentId;
            this.renditionKey = renditionKey;
            this.reason = reason;
            this.category = category;
            this.policyKey = policyKey;
            this.sourceStage = sourceStage;
            this.failedAt = failedAt;
            this.attempts = attempts;
            this.lastReplayAt = null;
        }

        private void update(
            String reason,
            String category,
            String policyKey,
            String sourceStage,
            Instant failedAt,
            int attempts
        ) {
            this.reason = reason;
            this.category = category;
            this.policyKey = policyKey;
            this.sourceStage = sourceStage;
            this.failedAt = failedAt;
            this.attempts = attempts;
            occurrences.incrementAndGet();
        }

        private void markReplayAttempt(Instant replayedAt) {
            this.lastReplayAt = replayedAt;
            replayCount.incrementAndGet();
        }

        private DeadLetterEntry snapshot() {
            return new DeadLetterEntry(
                entryKey,
                documentId,
                renditionKey,
                reason,
                category,
                policyKey,
                sourceStage,
                failedAt,
                attempts,
                occurrences.get(),
                lastReplayAt,
                replayCount.get()
            );
        }
    }

    private record RedisEntryRef(
        String indexMember,
        String entryKey,
        UUID documentId,
        String renditionKey,
        String legacyDocumentKey
    ) {}

    public record DeadLetterEntry(
        String entryKey,
        UUID documentId,
        String renditionKey,
        String reason,
        String category,
        String policyKey,
        String sourceStage,
        Instant failedAt,
        int attempts,
        long occurrences,
        Instant lastReplayAt,
        long replayCount
    ) {}
}
