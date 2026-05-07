package com.ecm.core.preview;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.queue.RedisScheduledQueueStore;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.search.SearchIndexService;
import com.ecm.core.service.RenditionResourceSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreviewQueueService {

    private static final String REDIS_SCHEDULE_KEY = "ecm:queue:preview:schedule";
    private static final String REDIS_ATTEMPTS_KEY = "ecm:queue:preview:attempts";
    private static final String REDIS_GOVERNANCE_KEY = "ecm:queue:preview:governance";
    private static final String REDIS_CANCEL_REQUEST_KEY = "ecm:queue:preview:cancel";
    private static final String REDIS_LOCK_PREFIX = "ecm:queue:preview:lock:";
    private static final Duration REDIS_LOCK_TTL = Duration.ofMinutes(10);
    private static final int REDIS_DIAGNOSTICS_SCAN_LIMIT = 2000;
    private static final int MAX_DECLINED_HISTORY = 2000;
    private static final int MAX_DECLINED_SNAPSHOT_LIMIT = 500;
    private static final String PREVIEW_RENDITION_KEY = PreviewDeadLetterRegistry.defaultRenditionKey();

    private final DocumentRepository documentRepository;
    private final PreviewService previewService;
    private final SearchIndexService searchIndexService;
    private final RenditionResourceSyncService renditionResourceSyncService;
    private final StringRedisTemplate redisTemplate;
    private final PreviewTransformTraceBuffer previewTransformTraceBuffer;
    private final PreviewFailurePolicyRegistry previewFailurePolicyRegistry;
    private final PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry;
    private final PreviewDeadLetterRegistry previewDeadLetterRegistry;

    @Value("${ecm.preview.queue.enabled:true}")
    private boolean queueEnabled;

    @Value("${ecm.preview.queue.backend:memory}")
    private String queueBackend;

    @Value("${ecm.preview.queue.max-attempts:3}")
    private int maxAttempts;

    @Value("${ecm.preview.queue.retry-delay-ms:60000}")
    private long retryDelayMs;

    @Value("${ecm.preview.queue.batch-size:2}")
    private int batchSize;

    @Value("${ecm.preview.queue.run-as-user:admin}")
    private String runAsUser;

    @Value("${ecm.preview.queue.require-content-hash-match-for-ready:true}")
    private boolean requireContentHashMatchForReady;

    @Value("${ecm.preview.dead-letter.auto-replay.enabled:false}")
    private boolean deadLetterAutoReplayEnabled;

    @Value("${ecm.preview.dead-letter.auto-replay.max-items:20}")
    private int deadLetterAutoReplayMaxItems;

    @Value("${ecm.preview.dead-letter.auto-replay.cooldown-ms:300000}")
    private long deadLetterAutoReplayCooldownMs;

    @Value("${ecm.preview.dead-letter.auto-replay.force:false}")
    private boolean deadLetterAutoReplayForce;

    @Value("${ecm.preview.dead-letter.auto-replay.categories:TEMPORARY}")
    private String deadLetterAutoReplayCategories;

    private final Queue<PreviewJob> queue = new ConcurrentLinkedQueue<>();
    private final Map<String, PreviewJob> queuedJobs = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeGovernanceByDocument = new ConcurrentHashMap<>();
    private final Set<UUID> activeRunningByDocument = ConcurrentHashMap.newKeySet();
    private final Set<UUID> cancelRequestedByDocument = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PreviewQueueDeclinedItem> declinedItemsByDocument = new ConcurrentHashMap<>();
    private final Queue<UUID> declinedItemOrder = new ConcurrentLinkedQueue<>();

    public PreviewQueueStatus enqueue(UUID documentId, boolean force) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId is required");
        }
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        clearFailureLedgerIfStaleContent(document);
        String governanceKey = buildGovernanceKey(document);

        PreviewStatus status = resolveEffectivePreviewStatusForEvaluation(document);
        PreviewFailurePolicyRegistry.PreviewFailurePolicy policy = resolvePolicy(document);
        if (!queueEnabled) {
            return declineEnqueue(
                document,
                status,
                "QUEUE_DISABLED",
                false,
                null,
                "Preview queue disabled by configuration"
            );
        }
        PreviewRenditionPreventionRegistry.BlockedEntry blockedEntry = previewRenditionPreventionRegistry.get(documentId);
        if (!force && blockedEntry != null) {
            PreviewRenditionPreventionRegistry.BlockedEntry hit = previewRenditionPreventionRegistry.markBlockedHit(documentId);
            long hitCount = hit != null ? hit.hitCount() : blockedEntry.hitCount();
            return declineEnqueue(
                document,
                status,
                blockedEntry.category(),
                true,
                null,
                "Rendition prevented (" + blockedEntry.category() + ", hits=" + hitCount + "): " + blockedEntry.reason()
            );
        }
        if (force && blockedEntry != null) {
            previewRenditionPreventionRegistry.unblock(documentId);
        }
        if (!force) {
            if (status == PreviewStatus.READY && isReadyRenditionUpToDate(document)) {
                return declineEnqueue(document, status, "UP_TO_DATE", false, null, "Preview already up to date");
            }
            if (status == PreviewStatus.UNSUPPORTED) {
                return declineEnqueue(document, status, "UNSUPPORTED", false, null, "Preview unsupported");
            }
            if (status == PreviewStatus.FAILED) {
                String category = resolveEffectiveFailureCategoryForEvaluation(document);
                if (PreviewFailureClassifier.CATEGORY_UNSUPPORTED.equalsIgnoreCase(category)) {
                    return declineEnqueue(document, status, "UNSUPPORTED", false, null, "Preview unsupported");
                }
                if (PreviewFailureClassifier.CATEGORY_PERMANENT.equalsIgnoreCase(category)) {
                    return declineEnqueue(
                        document,
                        status,
                        "PERMANENT_FAILURE",
                        true,
                        null,
                        "Preview failed permanently; use force=true to rebuild"
                    );
                }
            }
            if (status == PreviewStatus.FAILED && isWithinQuietPeriod(document, policy)) {
                Instant nextAllowedAt = document.getPreviewLastUpdated()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .plusMillis(policy.quietPeriodMs());
                return declineEnqueue(
                    document,
                    status,
                    "QUIET_PERIOD",
                    false,
                    nextAllowedAt,
                    "Within quiet period for policy: " + policy.key()
                );
            }
        }

        if (useRedisBackend()) {
            clearRedisCancellationRequest(documentId);
            clearDeclinedEntry(documentId);
            return enqueueRedis(document, force, governanceKey);
        }

        PreviewJob existing = getActiveMemoryJob(documentId);
        if (existing != null && existing.governanceKey().equals(governanceKey)) {
            previewDeadLetterRegistry.remove(documentId, PREVIEW_RENDITION_KEY);
            clearDeclinedEntry(documentId);
            return buildQueueStatus(document, status, true, existing.attempts(), existing.nextAttemptAt(), "Preview already queued");
        }
        if (existing != null) {
            removeMemoryJob(existing);
        }

        PreviewJob job = new PreviewJob(documentId, governanceKey, 0, Instant.now(), force);
        cancelRequestedByDocument.remove(documentId);
        activeGovernanceByDocument.put(documentId, governanceKey);
        queuedJobs.put(governanceKey, job);
        queue.add(job);
        markProcessing(document);
        previewDeadLetterRegistry.remove(documentId, PREVIEW_RENDITION_KEY);
        clearDeclinedEntry(documentId);

        log.info("Queued preview generation for document {}", documentId);
        return buildQueueStatus(document, PreviewStatus.PROCESSING, true, 0, job.nextAttemptAt(), "Preview queued");
    }

    public PreviewQueueStatus evaluateEnqueue(UUID documentId, boolean force) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId is required");
        }
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        PreviewStatus status = resolveEffectivePreviewStatusForEvaluation(document);
        String effectiveFailureReason = resolveEffectiveFailureReasonForEvaluation(document);
        String effectiveFailureCategory = resolveEffectiveFailureCategoryForEvaluation(document);
        String governanceKey = buildGovernanceKey(document);
        PreviewFailurePolicyRegistry.PreviewFailurePolicy policy = resolvePolicy(document);

        if (!queueEnabled) {
            return buildQueueStatus(document, status, false, 0, null, "Preview queue disabled by configuration");
        }

        PreviewRenditionPreventionRegistry.BlockedEntry blockedEntry = previewRenditionPreventionRegistry.get(documentId);
        if (!force && blockedEntry != null) {
            long hitCount = blockedEntry.hitCount();
            return buildQueueStatus(
                document,
                status,
                false,
                0,
                null,
                "Rendition prevented (" + blockedEntry.category() + ", hits=" + hitCount + "): " + blockedEntry.reason()
            );
        }

        if (!force) {
            if (status == PreviewStatus.READY && isReadyRenditionUpToDate(document)) {
                return buildQueueStatus(document, status, false, 0, null, "Preview already up to date");
            }
            if (status == PreviewStatus.UNSUPPORTED) {
                return buildQueueStatus(document, status, false, 0, null, "Preview unsupported");
            }
            if (status == PreviewStatus.FAILED) {
                if (PreviewFailureClassifier.CATEGORY_UNSUPPORTED.equalsIgnoreCase(effectiveFailureCategory)) {
                    return buildQueueStatus(document, status, false, 0, null, "Preview unsupported");
                }
                if (PreviewFailureClassifier.CATEGORY_PERMANENT.equalsIgnoreCase(effectiveFailureCategory)) {
                    return buildQueueStatus(
                        document,
                        status,
                        false,
                        0,
                        null,
                        "Preview failed permanently; use force=true to rebuild"
                    );
                }
            }
            if (status == PreviewStatus.FAILED && isWithinQuietPeriod(document, policy)) {
                Instant nextAllowedAt = document.getPreviewLastUpdated()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .plusMillis(policy.quietPeriodMs());
                return buildQueueStatus(
                    document,
                    status,
                    false,
                    0,
                    nextAllowedAt,
                    "Within quiet period for policy: " + policy.key()
                );
            }
        }

        if (useRedisBackend()) {
            RedisScheduledQueueStore.Entry existing = redisStore().getOrNull(documentId);
            String existingGovernanceKey = getRedisGovernanceKey(documentId);
            if (existing != null && governanceKey.equals(existingGovernanceKey)) {
                return buildQueueStatus(document, status, true, existing.attempts(), existing.nextAttemptAt(), "Preview already queued");
            }
            return buildQueueStatus(document, PreviewStatus.PROCESSING, true, 0, Instant.now(), "Preview queued");
        }

        PreviewJob existing = getActiveMemoryJob(documentId);
        if (existing != null && existing.governanceKey().equals(governanceKey)) {
            return buildQueueStatus(document, status, true, existing.attempts(), existing.nextAttemptAt(), "Preview already queued");
        }
        return buildQueueStatus(document, PreviewStatus.PROCESSING, true, 0, Instant.now(), "Preview queued");
    }

    public PreviewQueueCancellationStatus cancel(UUID documentId) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId is required");
        }

        if (useRedisBackend()) {
            return cancelRedis(documentId);
        }

        PreviewJob queuedJob = getActiveMemoryJob(documentId);
        boolean running = activeRunningByDocument.contains(documentId);
        if (running) {
            cancelRequestedByDocument.add(documentId);
            return new PreviewQueueCancellationStatus(
                documentId,
                "CANCEL_REQUESTED",
                true,
                true,
                true,
                "Cancellation requested for running preview task"
            );
        }
        if (queuedJob != null) {
            removeMemoryJob(queuedJob);
            cancelRequestedByDocument.remove(documentId);
            return new PreviewQueueCancellationStatus(
                documentId,
                "CANCELLED",
                true,
                true,
                false,
                "Cancelled queued preview task"
            );
        }
        return new PreviewQueueCancellationStatus(
            documentId,
            "IDLE",
            false,
            false,
            false,
            "No active preview queue task"
        );
    }

    public PreviewQueueDiagnosticsSnapshot diagnosticsSnapshot(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        if (useRedisBackend()) {
            return buildRedisDiagnosticsSnapshot(safeLimit);
        }
        return buildMemoryDiagnosticsSnapshot(safeLimit);
    }

    public PreviewQueueDeclinedSnapshot declinedSnapshot(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_DECLINED_SNAPSHOT_LIMIT));
        List<PreviewQueueDeclinedItem> sampledItems = declinedItemsByDocument.values().stream()
            .sorted(
                Comparator.comparing(
                        PreviewQueueDeclinedItem::declinedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                    )
                    .thenComparing(item -> item.documentId() != null ? item.documentId().toString() : "")
            )
            .limit(safeLimit)
            .toList();
        long totalDeclined = declinedItemsByDocument.size();
        return new PreviewQueueDeclinedSnapshot(
            queueEnabled,
            totalDeclined,
            safeLimit,
            totalDeclined > sampledItems.size(),
            sampledItems
        );
    }

    public boolean clearDeclined(UUID documentId) {
        if (documentId == null) {
            return false;
        }
        PreviewQueueDeclinedItem removed = declinedItemsByDocument.remove(documentId);
        declinedItemOrder.remove(documentId);
        return removed != null;
    }

    @Scheduled(fixedDelayString = "${ecm.preview.queue.poll-interval-ms:2000}")
    public void processQueue() {
        if (useRedisBackend()) {
            processRedisQueue();
            return;
        }

        if (!queueEnabled) {
            return;
        }
        int limit = Math.max(1, batchSize);
        int processed = 0;
        Instant now = Instant.now();

        while (processed < limit) {
            PreviewJob job = queue.poll();
            if (job == null) {
                break;
            }
            if (!isMemoryJobActive(job)) {
                continue;
            }

            if (isMemoryCancelRequested(job.documentId())) {
                removeMemoryJob(job);
                clearMemoryCancelRequested(job.documentId());
                processed++;
                continue;
            }

            if (job.nextAttemptAt().isAfter(now)) {
                queue.add(job);
                processed++;
                continue;
            }

            handleJob(job);
            processed++;
        }
    }

    @Scheduled(fixedDelayString = "${ecm.preview.dead-letter.auto-replay.poll-interval-ms:30000}")
    public void processDeadLetterAutoReplay() {
        if (!queueEnabled || !deadLetterAutoReplayEnabled || !previewDeadLetterRegistry.isEnabled()) {
            return;
        }
        int maxItems = Math.max(1, deadLetterAutoReplayMaxItems);
        int scanLimit = Math.max(50, Math.min(maxItems * 5, 500));
        long cooldownMs = Math.max(1000L, deadLetterAutoReplayCooldownMs);
        Set<String> allowedCategories = resolveAutoReplayCategories();
        Instant now = Instant.now();

        List<PreviewDeadLetterRegistry.DeadLetterEntry> candidates = previewDeadLetterRegistry.list(scanLimit);
        int attempted = 0;
        int queued = 0;
        int skipped = 0;
        int failed = 0;

        for (PreviewDeadLetterRegistry.DeadLetterEntry entry : candidates) {
            if (entry == null || attempted >= maxItems) {
                break;
            }
            String category = normalizeDeadLetterCategory(entry.category());
            if (!allowedCategories.isEmpty() && !allowedCategories.contains(category)) {
                continue;
            }
            Instant gateAt = entry.lastReplayAt() != null ? entry.lastReplayAt() : entry.failedAt();
            if (gateAt != null && gateAt.plusMillis(cooldownMs).isAfter(now)) {
                continue;
            }

            attempted += 1;
            UUID documentId = entry.documentId();
            try {
                PreviewQueueStatus status = enqueue(documentId, deadLetterAutoReplayForce);
                if (status.queued()) {
                    queued += 1;
                } else {
                    skipped += 1;
                    previewDeadLetterRegistry.markReplayAttempt(documentId, entry.renditionKey(), now);
                }
            } catch (Exception e) {
                failed += 1;
                previewDeadLetterRegistry.markReplayAttempt(documentId, entry.renditionKey(), now);
                log.warn("Dead-letter auto replay failed for {}: {}", documentId, e.getMessage());
            }
        }

        if (attempted > 0) {
            log.info(
                "Dead-letter auto replay run: attempted={}, queued={}, skipped={}, failed={}, force={}, categories={}",
                attempted,
                queued,
                skipped,
                failed,
                deadLetterAutoReplayForce,
                allowedCategories
            );
        }
    }

    private void handleJob(PreviewJob job) {
        UUID documentId = job.documentId();
        activeRunningByDocument.add(documentId);
        Document document = documentRepository.findById(documentId).orElse(null);
        try {
            if (isMemoryCancelRequested(documentId)) {
                return;
            }
            if (document == null) {
                removeMemoryJob(job);
                return;
            }
            String currentGovernanceKey = buildGovernanceKey(document);
            if (!job.governanceKey().equals(currentGovernanceKey)) {
                log.info(
                    "Skipped stale preview queue job for {} (queued={}, current={})",
                    documentId,
                    job.governanceKey(),
                    currentGovernanceKey
                );
                removeMemoryJob(job);
                return;
            }
            if (shouldSkipSatisfiedMemoryJob(document, job.force())) {
                log.info(
                    "Skipped satisfied preview queue job for {} (status={})",
                    documentId,
                    resolveEffectivePreviewStatusForEvaluation(document)
                );
                removeMemoryJob(job);
                return;
            }
            PreviewFailurePolicyRegistry.PreviewFailurePolicy policy = resolvePolicy(document);
            int effectiveMaxAttempts = resolveMaxAttempts(policy);

            try {
                PreviewResult result = runAsSystem(() -> previewService.generatePreview(document));
                appendQueueTrace(result, "QUEUE_ATTEMPT", "attempt=" + (job.attempts() + 1));
                String resultFailureReason = resolveResultFailureReason(result, result != null ? result.getMessage() : null);
                boolean retry = shouldRetry(result);
                if (retry && job.attempts() + 1 < effectiveMaxAttempts) {
                    if (isMemoryCancelRequested(documentId)) {
                        appendQueueTrace(result, "QUEUE_CANCELLED", "documentId=" + documentId);
                        return;
                    }
                    markRetrying(document, resultFailureReason != null ? resultFailureReason : "Preview retry scheduled");
                    appendQueueTrace(result, "QUEUE_RETRY_SCHEDULED", "nextAttempt=" + (job.attempts() + 2));
                    scheduleRetry(job, policy);
                    return;
                }
                if (retry) {
                    markFailed(document, resultFailureReason != null ? resultFailureReason : "Preview failed after retries");
                    appendQueueTrace(result, "QUEUE_RETRY_EXHAUSTED", "attempt=" + (job.attempts() + 1));
                    maybeAutoBlock(document, result, resultFailureReason);
                    markDeadLetter(
                        document,
                        policy,
                        result,
                        resultFailureReason,
                        "QUEUE_RETRY_EXHAUSTED",
                        job.attempts() + 1
                    );
                } else {
                    appendQueueTrace(result, "QUEUE_DONE", "status=" + firstNonBlank(resolveResultStatus(result), "READY_OR_UNSUPPORTED"));
                    if (result != null && result.isSupported()) {
                        previewRenditionPreventionRegistry.unblock(documentId);
                        previewDeadLetterRegistry.remove(documentId, PREVIEW_RENDITION_KEY);
                    } else {
                        maybeAutoBlock(document, result, resultFailureReason);
                        markDeadLetter(
                            document,
                            policy,
                            result,
                            resultFailureReason,
                            "QUEUE_TERMINAL",
                            job.attempts() + 1
                        );
                    }
                }
            } catch (Exception e) {
                if (job.attempts() + 1 < effectiveMaxAttempts) {
                    if (isMemoryCancelRequested(documentId)) {
                        return;
                    }
                    markRetrying(document, e.getMessage());
                    scheduleRetry(job, policy);
                    return;
                }
                markFailed(document, e.getMessage());
                maybeAutoBlock(document, null, e.getMessage());
                markDeadLetter(document, policy, null, e.getMessage(), "QUEUE_EXCEPTION_EXHAUSTED", job.attempts() + 1);
                log.warn(
                    "Preview generation failed for {} after {} attempts (policy={}): {}",
                    documentId,
                    job.attempts() + 1,
                    policy.key(),
                    e.getMessage()
                );
            }
        } finally {
            activeRunningByDocument.remove(documentId);
            removeMemoryJob(job);
            clearMemoryCancelRequested(documentId);
        }
    }

    private PreviewQueueStatus enqueueRedis(Document document, boolean force, String governanceKey) {
        UUID documentId = document.getId();
        PreviewStatus status = document.getPreviewStatus();

        RedisScheduledQueueStore store = redisStore();
        RedisScheduledQueueStore.Entry existing = store.getOrNull(documentId);
        String existingGovernanceKey = getRedisGovernanceKey(documentId);
        if (existing != null && governanceKey.equals(existingGovernanceKey)) {
            previewDeadLetterRegistry.remove(documentId, PREVIEW_RENDITION_KEY);
            clearDeclinedEntry(documentId);
            return buildQueueStatus(document, status, true, existing.attempts(), existing.nextAttemptAt(), "Preview already queued");
        }
        if (existing != null) {
            store.complete(documentId);
        }

        RedisScheduledQueueStore.Entry job = store.enqueueIfAbsent(documentId, Instant.now());
        setRedisGovernanceKey(documentId, governanceKey);
        markProcessing(document);
        previewDeadLetterRegistry.remove(documentId, PREVIEW_RENDITION_KEY);
        clearDeclinedEntry(documentId);
        log.info("Queued preview generation for document {} (redis)", documentId);
        return buildQueueStatus(document, PreviewStatus.PROCESSING, true, job.attempts(), job.nextAttemptAt(), "Preview queued");
    }

    private void processRedisQueue() {
        if (!queueEnabled) {
            return;
        }
        int limit = Math.max(1, batchSize);
        Instant now = Instant.now();

        RedisScheduledQueueStore store = redisStore();
        List<RedisScheduledQueueStore.Entry> due = store.claimDue(limit, now);
        for (RedisScheduledQueueStore.Entry entry : due) {
            try {
                if (isRedisCancellationRequested(entry.documentId())) {
                    store.complete(entry.documentId());
                    clearRedisGovernanceKey(entry.documentId());
                    clearRedisCancellationRequest(entry.documentId());
                    continue;
                }
                handleRedisJob(store, entry);
            } finally {
                // Release is idempotent; complete() also releases.
                store.release(entry.documentId());
            }
        }
    }

    private void handleRedisJob(RedisScheduledQueueStore store, RedisScheduledQueueStore.Entry entry) {
        UUID documentId = entry.documentId();
        if (isRedisCancellationRequested(documentId)) {
            store.complete(documentId);
            clearRedisGovernanceKey(documentId);
            clearRedisCancellationRequest(documentId);
            return;
        }
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            store.complete(documentId);
            clearRedisGovernanceKey(documentId);
            return;
        }
        String queuedGovernanceKey = getRedisGovernanceKey(documentId);
        String currentGovernanceKey = buildGovernanceKey(document);
        if (queuedGovernanceKey != null && !queuedGovernanceKey.equals(currentGovernanceKey)) {
            log.info(
                "Skipped stale redis preview queue job for {} (queued={}, current={})",
                documentId,
                queuedGovernanceKey,
                currentGovernanceKey
            );
            store.complete(documentId);
            clearRedisGovernanceKey(documentId);
            return;
        }
        PreviewFailurePolicyRegistry.PreviewFailurePolicy policy = resolvePolicy(document);
        int effectiveMaxAttempts = resolveMaxAttempts(policy);

        try {
            PreviewResult result = runAsSystem(() -> previewService.generatePreview(document));
            appendQueueTrace(result, "QUEUE_ATTEMPT", "attempt=" + (entry.attempts() + 1));
            String resultFailureReason = resolveResultFailureReason(result, result != null ? result.getMessage() : null);
            boolean retry = shouldRetry(result);
            if (retry && entry.attempts() + 1 < effectiveMaxAttempts) {
                if (isRedisCancellationRequested(documentId)) {
                    store.complete(documentId);
                    clearRedisGovernanceKey(documentId);
                    clearRedisCancellationRequest(documentId);
                    return;
                }
                markRetrying(document, resultFailureReason != null ? resultFailureReason : "Preview retry scheduled");
                int nextAttempts = entry.attempts() + 1;
                Instant nextRunAt = Instant.now().plusMillis(computeRetryDelayMs(policy, nextAttempts));
                store.scheduleRetry(documentId, nextAttempts, nextRunAt);
                log.info("Scheduled preview retry {} for document {} at {} (redis)", nextAttempts, documentId, nextRunAt);
                appendQueueTrace(result, "QUEUE_RETRY_SCHEDULED", "nextAttempt=" + (entry.attempts() + 2));
                return;
            }
            if (retry) {
                markFailed(document, resultFailureReason != null ? resultFailureReason : "Preview failed after retries");
                appendQueueTrace(result, "QUEUE_RETRY_EXHAUSTED", "attempt=" + (entry.attempts() + 1));
                maybeAutoBlock(document, result, resultFailureReason);
                markDeadLetter(
                    document,
                    policy,
                    result,
                    resultFailureReason,
                    "QUEUE_RETRY_EXHAUSTED",
                    entry.attempts() + 1
                );
            } else {
                appendQueueTrace(result, "QUEUE_DONE", "status=" + firstNonBlank(resolveResultStatus(result), "READY_OR_UNSUPPORTED"));
                if (result != null && result.isSupported()) {
                    previewRenditionPreventionRegistry.unblock(documentId);
                    previewDeadLetterRegistry.remove(documentId, PREVIEW_RENDITION_KEY);
                } else {
                    maybeAutoBlock(document, result, resultFailureReason);
                    markDeadLetter(
                        document,
                        policy,
                        result,
                        resultFailureReason,
                        "QUEUE_TERMINAL",
                        entry.attempts() + 1
                    );
                }
            }
        } catch (Exception e) {
            if (entry.attempts() + 1 < effectiveMaxAttempts) {
                if (isRedisCancellationRequested(documentId)) {
                    store.complete(documentId);
                    clearRedisGovernanceKey(documentId);
                    clearRedisCancellationRequest(documentId);
                    return;
                }
                markRetrying(document, e.getMessage());
                int nextAttempts = entry.attempts() + 1;
                Instant nextRunAt = Instant.now().plusMillis(computeRetryDelayMs(policy, nextAttempts));
                store.scheduleRetry(documentId, nextAttempts, nextRunAt);
                log.info("Scheduled preview retry {} for document {} at {} (redis)", nextAttempts, documentId, nextRunAt);
                return;
            }
            markFailed(document, e.getMessage());
            maybeAutoBlock(document, null, e.getMessage());
            markDeadLetter(document, policy, null, e.getMessage(), "QUEUE_EXCEPTION_EXHAUSTED", entry.attempts() + 1);
            log.warn(
                "Preview generation failed for {} after {} attempts (policy={}): {}",
                documentId,
                entry.attempts() + 1,
                policy.key(),
                e.getMessage()
            );
        }

        store.complete(documentId);
        clearRedisGovernanceKey(documentId);
        clearRedisCancellationRequest(documentId);
    }

    private void scheduleRetry(PreviewJob job, PreviewFailurePolicyRegistry.PreviewFailurePolicy policy) {
        int nextAttempts = job.attempts() + 1;
        Instant nextRunAt = Instant.now().plusMillis(computeRetryDelayMs(policy, nextAttempts));
        PreviewJob nextJob = new PreviewJob(job.documentId(), job.governanceKey(), nextAttempts, nextRunAt, job.force());
        activeGovernanceByDocument.put(job.documentId(), job.governanceKey());
        queuedJobs.put(job.governanceKey(), nextJob);
        queue.add(nextJob);
        log.info(
            "Scheduled preview retry {} for document {} at {} (policy={})",
            nextAttempts,
            job.documentId(),
            nextRunAt,
            policy.key()
        );
    }

    private void markProcessing(Document document) {
        try {
            document.setPreviewStatus(PreviewStatus.PROCESSING);
            document.setPreviewFailureReason(null);
            document.setPreviewLastUpdated(LocalDateTime.now());
            document.setPreviewAvailable(false);
            document.setPreviewContentHash(null);
            documentRepository.save(document);
            syncRenditionResources(document);
            try {
                searchIndexService.updateDocument(document);
            } catch (Exception indexError) {
                log.debug("Failed to index preview processing status for {}: {}", document.getId(), indexError.getMessage());
            }
        } catch (Exception e) {
            log.warn("Failed to mark preview as processing for {}: {}", document.getId(), e.getMessage());
        }
    }

    private void markRetrying(Document document, String failureReason) {
        try {
            document.setPreviewStatus(PreviewStatus.PROCESSING);
            document.setPreviewFailureReason(failureReason);
            document.setPreviewLastUpdated(LocalDateTime.now());
            document.setPreviewAvailable(false);
            document.setPreviewContentHash(null);
            documentRepository.save(document);
            syncRenditionResources(document);
            try {
                searchIndexService.updateDocument(document);
            } catch (Exception indexError) {
                log.debug("Failed to index preview retry status for {}: {}", document.getId(), indexError.getMessage());
            }
        } catch (Exception e) {
            log.warn("Failed to mark preview retry for {}: {}", document.getId(), e.getMessage());
        }
    }

    private void markFailed(Document document, String failureReason) {
        try {
            LocalDateTime now = LocalDateTime.now();
            document.setPreviewStatus(PreviewStatus.FAILED);
            document.setPreviewFailureReason(failureReason);
            document.setPreviewLastUpdated(now);
            document.setPreviewAvailable(false);
            document.setPreviewContentHash(null);
            int failureCount = document.getPreviewFailureCount() != null ? document.getPreviewFailureCount() : 0;
            document.setPreviewFailureCount(failureCount + 1);
            document.setPreviewFailedAt(now);
            document.setPreviewLastFailureReason(normalizeFailureReason(failureReason));
            document.setPreviewFailureContentHash(normalizeOptionalContentHash(document.getContentHash()));
            documentRepository.save(document);
            syncRenditionResources(document);
            try {
                searchIndexService.updateDocument(document);
            } catch (Exception indexError) {
                log.debug("Failed to index preview failed status for {}: {}", document.getId(), indexError.getMessage());
            }
        } catch (Exception e) {
            log.warn("Failed to mark preview failed for {}: {}", document.getId(), e.getMessage());
        }
    }

    private boolean shouldRetry(PreviewResult result) {
        if (result == null) {
            return true;
        }
        if (result.isRetryNeeded()) {
            return true;
        }
        String resultStatus = resolveResultStatus(result);
        if (PreviewStatus.UNSUPPORTED.name().equalsIgnoreCase(resultStatus)) {
            return false;
        }
        String explicitCategory = normalizeFailureCategory(result.getFailureCategory());
        if (PreviewFailureClassifier.CATEGORY_TEMPORARY.equalsIgnoreCase(explicitCategory)) {
            return true;
        }
        if (PreviewFailureClassifier.CATEGORY_UNSUPPORTED.equalsIgnoreCase(explicitCategory)
            || PreviewFailureClassifier.CATEGORY_PERMANENT.equalsIgnoreCase(explicitCategory)) {
            return false;
        }
        if (result.isSupported()) {
            return false;
        }
        String category = resolveResultFailureCategory(result, result.getMessage());
        if (category != null) {
            return PreviewFailureClassifier.CATEGORY_TEMPORARY.equalsIgnoreCase(category);
        }
        String failureReason = resolveResultFailureReason(result, result.getMessage());
        if (failureReason == null || failureReason.isBlank()) {
            return false;
        }
        return PreviewFailureClassifier.CATEGORY_TEMPORARY.equalsIgnoreCase(
            PreviewFailureClassifier.classify(
                PreviewStatus.FAILED.name(),
                result.getMimeType(),
                failureReason
            )
        );
    }

    private String resolveResultStatus(PreviewResult result) {
        if (result == null) {
            return null;
        }
        if (result.getStatus() != null && !result.getStatus().isBlank()) {
            return result.getStatus().trim().toUpperCase(Locale.ROOT);
        }
        if (result.isSupported()) {
            return PreviewStatus.READY.name();
        }
        String category = normalizeFailureCategory(result.getFailureCategory());
        if (category == null) {
            category = normalizeFailureCategory(
                PreviewFailureClassifier.classify(
                    PreviewStatus.FAILED.name(),
                    result.getMimeType(),
                    resolveResultFailureReason(result, result.getMessage())
                )
            );
        }
        if (PreviewFailureClassifier.CATEGORY_UNSUPPORTED.equalsIgnoreCase(category)) {
            return PreviewStatus.UNSUPPORTED.name();
        }
        return PreviewStatus.FAILED.name();
    }

    private String resolveResultFailureReason(PreviewResult result, String fallbackReason) {
        if (result == null) {
            return normalizeFailureReason(fallbackReason);
        }
        return firstNonBlank(
            normalizeFailureReason(result.getFailureReason()),
            normalizeFailureReason(fallbackReason),
            normalizeFailureReason(result.getMessage())
        );
    }

    private String resolveResultFailureCategory(PreviewResult result, String fallbackReason) {
        if (result == null) {
            return normalizeFailureCategory(
                PreviewFailureClassifier.classify(
                    PreviewStatus.FAILED.name(),
                    null,
                    normalizeFailureReason(fallbackReason)
                )
            );
        }
        String explicitCategory = normalizeFailureCategory(result.getFailureCategory());
        if (explicitCategory != null) {
            return explicitCategory;
        }
        String failureReason = resolveResultFailureReason(result, fallbackReason);
        String status = resolveResultStatus(result);
        String failureStatus = PreviewStatus.READY.name().equalsIgnoreCase(status) ? null : status;
        return normalizeFailureCategory(
            PreviewFailureClassifier.classify(
                failureStatus,
                result.getMimeType(),
                failureReason
            )
        );
    }

    private <T> T runAsSystem(PreviewTask<T> task) throws Exception {
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing != null && existing.isAuthenticated()) {
            return task.run();
        }
        Authentication auth = new UsernamePasswordAuthenticationToken(
            runAsUser,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        try {
            return task.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @FunctionalInterface
    private interface PreviewTask<T> {
        T run() throws Exception;
    }

    private boolean useRedisBackend() {
        return "redis".equalsIgnoreCase(queueBackend);
    }

    private RedisScheduledQueueStore redisStore() {
        return new RedisScheduledQueueStore(
            redisTemplate,
            REDIS_SCHEDULE_KEY,
            REDIS_ATTEMPTS_KEY,
            REDIS_LOCK_PREFIX,
            REDIS_LOCK_TTL
        );
    }

    private PreviewJob getActiveMemoryJob(UUID documentId) {
        if (documentId == null) {
            return null;
        }
        String governanceKey = activeGovernanceByDocument.get(documentId);
        if (governanceKey == null) {
            return null;
        }
        PreviewJob job = queuedJobs.get(governanceKey);
        if (job == null) {
            activeGovernanceByDocument.remove(documentId, governanceKey);
            return null;
        }
        return job;
    }

    private boolean isMemoryJobActive(PreviewJob job) {
        if (job == null) {
            return false;
        }
        String activeKey = activeGovernanceByDocument.get(job.documentId());
        if (!job.governanceKey().equals(activeKey)) {
            return false;
        }
        return queuedJobs.containsKey(job.governanceKey());
    }

    private void removeMemoryJob(PreviewJob job) {
        if (job == null) {
            return;
        }
        queuedJobs.remove(job.governanceKey(), job);
        activeGovernanceByDocument.remove(job.documentId(), job.governanceKey());
        queue.remove(job);
    }

    private boolean shouldSkipSatisfiedMemoryJob(Document document, boolean force) {
        if (force || document == null) {
            return false;
        }
        PreviewStatus status = resolveEffectivePreviewStatusForEvaluation(document);
        if (status == PreviewStatus.UNSUPPORTED) {
            return true;
        }
        return status == PreviewStatus.READY && isReadyRenditionUpToDate(document);
    }

    private boolean isMemoryCancelRequested(UUID documentId) {
        return documentId != null && cancelRequestedByDocument.contains(documentId);
    }

    private void clearMemoryCancelRequested(UUID documentId) {
        if (documentId != null) {
            cancelRequestedByDocument.remove(documentId);
        }
    }

    private PreviewQueueStatus declineEnqueue(
        Document document,
        PreviewStatus status,
        String category,
        boolean forceRequired,
        Instant nextEligibleAt,
        String message
    ) {
        UUID documentId = document != null ? document.getId() : null;
        if (documentId != null) {
            recordDeclined(document, status, category, forceRequired, nextEligibleAt, message);
        }
        return buildQueueStatus(document, status, false, 0, nextEligibleAt, message);
    }

    private PreviewQueueStatus buildQueueStatus(
        Document document,
        PreviewStatus fallbackStatus,
        boolean queued,
        int attempts,
        Instant nextAttemptAt,
        String message
    ) {
        UUID documentId = document != null ? document.getId() : null;
        PreviewStatus effectiveStatus = resolveEffectivePreviewStatusForEvaluation(document);
        String failureReason = resolveEffectiveFailureReasonForEvaluation(document);
        String failureCategory = resolveEffectiveFailureCategoryForEvaluation(document);
        LocalDateTime previewLastUpdated = document != null ? document.getPreviewLastUpdated() : null;
        return new PreviewQueueStatus(
            documentId,
            effectiveStatus != null ? effectiveStatus : fallbackStatus,
            queued,
            attempts,
            nextAttemptAt,
            message,
            failureReason,
            failureCategory,
            previewLastUpdated
        );
    }

    private void recordDeclined(
        Document document,
        PreviewStatus previewStatus,
        String category,
        boolean forceRequired,
        Instant nextEligibleAt,
        String message
    ) {
        if (document == null || document.getId() == null) {
            return;
        }
        UUID documentId = document.getId();
        String reason = normalizeFailureReason(message);
        if (reason == null) {
            reason = "Preview queue request declined";
        }
        PreviewQueueDeclinedItem declined = new PreviewQueueDeclinedItem(
            documentId,
            buildGovernanceKey(document),
            previewStatus,
            reason,
            normalizeDeclinedCategory(category),
            Instant.now(),
            nextEligibleAt,
            forceRequired
        );
        declinedItemsByDocument.put(documentId, declined);
        declinedItemOrder.remove(documentId);
        declinedItemOrder.add(documentId);
        trimDeclinedHistory();
    }

    private void clearDeclinedEntry(UUID documentId) {
        if (documentId == null) {
            return;
        }
        declinedItemsByDocument.remove(documentId);
        declinedItemOrder.remove(documentId);
    }

    private void trimDeclinedHistory() {
        while (declinedItemsByDocument.size() > MAX_DECLINED_HISTORY) {
            UUID oldest = declinedItemOrder.poll();
            if (oldest == null) {
                break;
            }
            declinedItemsByDocument.remove(oldest);
        }
    }

    private String buildGovernanceKey(Document document) {
        if (document == null || document.getId() == null) {
            return UUID.randomUUID() + "|preview|unknown";
        }
        String contentHash = normalizeContentHash(document.getContentHash());
        return document.getId() + "|preview|" + contentHash;
    }

    private PreviewStatus resolveEffectivePreviewStatusForEvaluation(Document document) {
        if (document == null) {
            return null;
        }
        String effectiveStatus = PreviewStatusSemantics.resolveEffectiveStatus(document);
        if (effectiveStatus == null) {
            return null;
        }
        PreviewStatus status = PreviewStatus.valueOf(effectiveStatus);
        if (!hasStaleFailureLedger(document)) {
            return status;
        }
        if (status == PreviewStatus.FAILED || status == PreviewStatus.UNSUPPORTED) {
            return null;
        }
        return status;
    }

    private String resolveEffectiveFailureReasonForEvaluation(Document document) {
        if (document == null) {
            return null;
        }
        if (hasStaleFailureLedger(document)) {
            PreviewStatus status = resolveEffectivePreviewStatusForEvaluation(document);
            if (status == PreviewStatus.FAILED || status == PreviewStatus.UNSUPPORTED) {
                return null;
            }
        }
        return PreviewStatusSemantics.resolveEffectiveFailureReason(document);
    }

    private String resolveEffectiveFailureCategoryForEvaluation(Document document) {
        return PreviewFailureClassifier.classify(
            resolveEffectivePreviewStatusForEvaluation(document) != null
                ? resolveEffectivePreviewStatusForEvaluation(document).name()
                : null,
            document != null ? document.getMimeType() : null,
            resolveEffectiveFailureReasonForEvaluation(document)
        );
    }

    private boolean hasStaleFailureLedger(Document document) {
        if (document == null) {
            return false;
        }
        String failureHash = normalizeOptionalContentHash(document.getPreviewFailureContentHash());
        String contentHash = normalizeOptionalContentHash(document.getContentHash());
        return failureHash != null && contentHash != null && !failureHash.equals(contentHash);
    }

    private void clearFailureLedgerIfStaleContent(Document document) {
        if (!hasStaleFailureLedger(document)) {
            return;
        }

        document.setPreviewFailureCount(0);
        document.setPreviewFailedAt(null);
        document.setPreviewLastFailureReason(null);
        document.setPreviewFailureContentHash(null);
        if (document.getPreviewStatus() == PreviewStatus.FAILED || document.getPreviewStatus() == PreviewStatus.UNSUPPORTED) {
            document.setPreviewStatus(null);
            document.setPreviewFailureReason(null);
            document.setPreviewLastUpdated(LocalDateTime.now());
            document.setPreviewAvailable(false);
            document.setPreviewContentHash(null);
        }
        try {
            documentRepository.save(document);
            syncRenditionResources(document);
            try {
                searchIndexService.updateDocument(document);
            } catch (Exception indexError) {
                log.debug("Failed to index preview stale-ledger clear for {}: {}", document.getId(), indexError.getMessage());
            }
        } catch (Exception e) {
            log.warn("Failed to clear stale preview failure ledger for {}: {}", document.getId(), e.getMessage());
        }
    }

    private boolean isReadyRenditionUpToDate(Document document) {
        if (document == null || document.getPreviewStatus() != PreviewStatus.READY) {
            return false;
        }
        if (!requireContentHashMatchForReady) {
            return true;
        }
        String contentHash = normalizeContentHash(document.getContentHash());
        if ("unknown".equals(contentHash)) {
            return true;
        }
        String previewContentHash = normalizeContentHash(document.getPreviewContentHash());
        return contentHash.equals(previewContentHash);
    }

    private static String normalizeContentHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return "unknown";
        }
        return hash.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeOptionalContentHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return null;
        }
        return hash.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeFailureReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.replaceAll("\\s+", " ").trim();
    }

    private void syncRenditionResources(Document document) {
        try {
            renditionResourceSyncService.syncDocument(document);
        } catch (Exception syncError) {
            log.warn("Failed to sync rendition resources for {}: {}", document.getId(), syncError.getMessage());
        }
    }

    private static String normalizeDeclinedCategory(String category) {
        if (category == null || category.isBlank()) {
            return "DECLINED";
        }
        String normalized = category.trim()
            .replaceAll("[^A-Za-z0-9]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+", "")
            .replaceAll("_+$", "")
            .toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? "DECLINED" : normalized;
    }

    private String getRedisGovernanceKey(UUID documentId) {
        if (documentId == null || redisTemplate == null) {
            return null;
        }
        try {
            Object value = redisTemplate.opsForHash().get(REDIS_GOVERNANCE_KEY, documentId.toString());
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.debug("Failed to read redis governance key for {}: {}", documentId, e.getMessage());
            return null;
        }
    }

    private void setRedisGovernanceKey(UUID documentId, String governanceKey) {
        if (documentId == null || governanceKey == null || redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForHash().put(REDIS_GOVERNANCE_KEY, documentId.toString(), governanceKey);
        } catch (Exception e) {
            log.debug("Failed to write redis governance key for {}: {}", documentId, e.getMessage());
        }
    }

    private void clearRedisGovernanceKey(UUID documentId) {
        if (documentId == null || redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForHash().delete(REDIS_GOVERNANCE_KEY, documentId.toString());
        } catch (Exception e) {
            log.debug("Failed to clear redis governance key for {}: {}", documentId, e.getMessage());
        }
    }

    private boolean hasRedisActiveLock(UUID documentId) {
        if (documentId == null || redisTemplate == null) {
            return false;
        }
        try {
            Boolean exists = redisTemplate.hasKey(REDIS_LOCK_PREFIX + documentId);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.debug("Failed to check redis queue lock for {}: {}", documentId, e.getMessage());
            return false;
        }
    }

    private boolean isRedisCancellationRequested(UUID documentId) {
        if (documentId == null || redisTemplate == null) {
            return false;
        }
        try {
            Object value = redisTemplate.opsForHash().get(REDIS_CANCEL_REQUEST_KEY, documentId.toString());
            return value != null && "1".equals(value.toString());
        } catch (Exception e) {
            log.debug("Failed to read redis cancel request for {}: {}", documentId, e.getMessage());
            return false;
        }
    }

    private void setRedisCancellationRequest(UUID documentId) {
        if (documentId == null || redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForHash().put(REDIS_CANCEL_REQUEST_KEY, documentId.toString(), "1");
        } catch (Exception e) {
            log.debug("Failed to write redis cancel request for {}: {}", documentId, e.getMessage());
        }
    }

    private void clearRedisCancellationRequest(UUID documentId) {
        if (documentId == null || redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForHash().delete(REDIS_CANCEL_REQUEST_KEY, documentId.toString());
        } catch (Exception e) {
            log.debug("Failed to clear redis cancel request for {}: {}", documentId, e.getMessage());
        }
    }

    private PreviewQueueCancellationStatus cancelRedis(UUID documentId) {
        RedisScheduledQueueStore store = redisStore();
        if (hasRedisActiveLock(documentId)) {
            setRedisCancellationRequest(documentId);
            return new PreviewQueueCancellationStatus(
                documentId,
                "CANCEL_REQUESTED",
                true,
                true,
                true,
                "Cancellation requested for running preview task"
            );
        }
        RedisScheduledQueueStore.Entry existing = store.getOrNull(documentId);
        if (existing != null) {
            store.complete(documentId);
            clearRedisGovernanceKey(documentId);
            clearRedisCancellationRequest(documentId);
            return new PreviewQueueCancellationStatus(
                documentId,
                "CANCELLED",
                true,
                true,
                false,
                "Cancelled queued preview task"
            );
        }

        return new PreviewQueueCancellationStatus(
            documentId,
            "IDLE",
            false,
            false,
            false,
            "No active preview queue task"
        );
    }

    public record PreviewQueueStatus(
        UUID documentId,
        PreviewStatus previewStatus,
        boolean queued,
        int attempts,
        Instant nextAttemptAt,
        String message,
        String previewFailureReason,
        String previewFailureCategory,
        LocalDateTime previewLastUpdated
    ) {
        public PreviewQueueStatus(
            UUID documentId,
            PreviewStatus previewStatus,
            boolean queued,
            int attempts,
            Instant nextAttemptAt,
            String message
        ) {
            this(documentId, previewStatus, queued, attempts, nextAttemptAt, message, null, null, null);
        }
    }

    public record PreviewQueueCancellationStatus(
        UUID documentId,
        String queueState,
        boolean cancelled,
        boolean hadActiveTask,
        boolean running,
        String message
    ) {
    }

    public record PreviewQueueDiagnosticsSnapshot(
        String backend,
        boolean queueEnabled,
        long scheduledCount,
        long governanceCount,
        long runningCount,
        boolean runningCountAccurate,
        long cancellationRequestedCount,
        int sampleLimit,
        boolean sampleTruncated,
        List<PreviewQueueDiagnosticsItem> items
    ) {
    }

    public record PreviewQueueDiagnosticsItem(
        UUID documentId,
        String governanceKey,
        int attempts,
        Instant nextAttemptAt,
        boolean running,
        boolean cancelRequested
    ) {
    }

    public record PreviewQueueDeclinedSnapshot(
        boolean queueEnabled,
        long totalDeclined,
        int sampleLimit,
        boolean sampleTruncated,
        List<PreviewQueueDeclinedItem> items
    ) {
    }

    public record PreviewQueueDeclinedItem(
        UUID documentId,
        String governanceKey,
        PreviewStatus previewStatus,
        String reason,
        String category,
        Instant declinedAt,
        Instant nextEligibleAt,
        boolean forceRequired
    ) {
    }

    private record PreviewJob(UUID documentId, String governanceKey, int attempts, Instant nextAttemptAt, boolean force) {
    }

    private void appendQueueTrace(PreviewResult result, String stage, String message) {
        if (result == null || result.getTraceRequestId() == null || result.getTraceRequestId().isBlank()) {
            return;
        }
        previewTransformTraceBuffer.append(result.getTraceRequestId(), stage, message);
    }

    private PreviewFailurePolicyRegistry.PreviewFailurePolicy resolvePolicy(Document document) {
        return previewFailurePolicyRegistry.resolve(
            document != null ? document.getMimeType() : null,
            document != null ? document.getName() : null
        );
    }

    private int resolveMaxAttempts(PreviewFailurePolicyRegistry.PreviewFailurePolicy policy) {
        return Math.max(1, policy.maxAttempts());
    }

    private long computeRetryDelayMs(PreviewFailurePolicyRegistry.PreviewFailurePolicy policy, int nextAttempts) {
        long baseDelay = Math.max(1000L, policy.retryDelayMs());
        double slope = Math.max(1.0d, policy.backoffMultiplier());
        int exponent = Math.max(0, nextAttempts - 1);
        double rawDelay = baseDelay * Math.pow(slope, exponent);
        if (!Double.isFinite(rawDelay)) {
            return Math.max(1000L, retryDelayMs);
        }
        long delay = (long) rawDelay;
        return Math.min(Math.max(delay, 1000L), 3600000L);
    }

    private boolean isWithinQuietPeriod(Document document, PreviewFailurePolicyRegistry.PreviewFailurePolicy policy) {
        if (document == null || document.getPreviewLastUpdated() == null || policy.quietPeriodMs() <= 0) {
            return false;
        }
        Instant lastUpdated = document.getPreviewLastUpdated().atZone(ZoneId.systemDefault()).toInstant();
        Instant nextAllowedAt = lastUpdated.plusMillis(policy.quietPeriodMs());
        return nextAllowedAt.isAfter(Instant.now());
    }

    private PreviewQueueDiagnosticsSnapshot buildMemoryDiagnosticsSnapshot(int limit) {
        List<PreviewQueueDiagnosticsItem> items = queuedJobs.values().stream()
            .sorted(Comparator.comparing(PreviewJob::nextAttemptAt).thenComparing(PreviewJob::documentId))
            .limit(limit)
            .map(job -> new PreviewQueueDiagnosticsItem(
                job.documentId(),
                job.governanceKey(),
                job.attempts(),
                job.nextAttemptAt(),
                activeRunningByDocument.contains(job.documentId()),
                cancelRequestedByDocument.contains(job.documentId())
            ))
            .toList();
        return new PreviewQueueDiagnosticsSnapshot(
            "MEMORY",
            queueEnabled,
            queuedJobs.size(),
            activeGovernanceByDocument.size(),
            activeRunningByDocument.size(),
            true,
            cancelRequestedByDocument.size(),
            limit,
            queuedJobs.size() > items.size(),
            items
        );
    }

    private PreviewQueueDiagnosticsSnapshot buildRedisDiagnosticsSnapshot(int limit) {
        RedisScheduledQueueStore store = redisStore();
        long scheduledCount = store.scheduledCount();
        long governanceCount = readRedisHashLength(REDIS_GOVERNANCE_KEY);
        long cancellationRequestedCount = readRedisHashLength(REDIS_CANCEL_REQUEST_KEY);

        Set<String> governanceKeys = readRedisHashKeys(REDIS_GOVERNANCE_KEY);
        int scanned = 0;
        long runningCount = 0L;
        boolean runningCountAccurate = true;
        for (String key : governanceKeys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            scanned += 1;
            if (scanned > REDIS_DIAGNOSTICS_SCAN_LIMIT) {
                runningCountAccurate = false;
                break;
            }
            try {
                UUID documentId = UUID.fromString(key);
                if (hasRedisActiveLock(documentId)) {
                    runningCount += 1L;
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed hash key.
            }
        }

        List<PreviewQueueDiagnosticsItem> items = new ArrayList<>();
        List<RedisScheduledQueueStore.Entry> queuedEntries = store.peek(limit);
        for (RedisScheduledQueueStore.Entry entry : queuedEntries) {
            UUID documentId = entry.documentId();
            items.add(new PreviewQueueDiagnosticsItem(
                documentId,
                getRedisGovernanceKey(documentId),
                Math.max(0, entry.attempts()),
                entry.nextAttemptAt(),
                hasRedisActiveLock(documentId),
                isRedisCancellationRequested(documentId)
            ));
        }

        return new PreviewQueueDiagnosticsSnapshot(
            "REDIS",
            queueEnabled,
            scheduledCount,
            governanceCount,
            runningCount,
            runningCountAccurate,
            cancellationRequestedCount,
            limit,
            scheduledCount > items.size(),
            items
        );
    }

    private long readRedisHashLength(String key) {
        if (redisTemplate == null || key == null || key.isBlank()) {
            return 0L;
        }
        try {
            Long size = redisTemplate.opsForHash().size(key);
            return size != null ? Math.max(0L, size) : 0L;
        } catch (Exception e) {
            log.debug("Failed to read redis hash size for {}: {}", key, e.getMessage());
            return 0L;
        }
    }

    private Set<String> readRedisHashKeys(String key) {
        if (redisTemplate == null || key == null || key.isBlank()) {
            return Set.of();
        }
        try {
            Set<Object> raw = redisTemplate.opsForHash().keys(key);
            if (raw == null || raw.isEmpty()) {
                return Set.of();
            }
            Set<String> normalized = new HashSet<>();
            for (Object item : raw) {
                if (item == null) {
                    continue;
                }
                normalized.add(item.toString());
            }
            return normalized;
        } catch (Exception e) {
            log.debug("Failed to read redis hash keys for {}: {}", key, e.getMessage());
            return Set.of();
        }
    }

    private void maybeAutoBlock(Document document, PreviewResult result, String fallbackReason) {
        if (document == null) {
            return;
        }
        String reason = resolveResultFailureReason(result, fallbackReason);
        String category = resolveResultFailureCategory(result, reason);
        if (category == null || category.isBlank()) {
            category = normalizeFailureCategory(PreviewFailureClassifier.classify(
                PreviewStatus.FAILED.name(),
                document.getMimeType(),
                reason
            ));
        }
        if (!previewRenditionPreventionRegistry.shouldAutoBlock(category)) {
            return;
        }
        previewRenditionPreventionRegistry.block(document.getId(), reason, category);
        if (result != null) {
            appendQueueTrace(result, "PREVENTION_BLOCKED", "category=" + category);
        }
    }

    private void markDeadLetter(
        Document document,
        PreviewFailurePolicyRegistry.PreviewFailurePolicy policy,
        PreviewResult result,
        String fallbackReason,
        String sourceStage,
        int attempts
    ) {
        if (document == null) {
            return;
        }
        String reason = resolveResultFailureReason(result, fallbackReason);
        String category = resolveResultFailureCategory(result, reason);
        if (category == null || category.isBlank()) {
            category = normalizeFailureCategory(PreviewFailureClassifier.classify(
                PreviewStatus.FAILED.name(),
                document.getMimeType(),
                reason
            ));
        }
        previewDeadLetterRegistry.record(
            document.getId(),
            PREVIEW_RENDITION_KEY,
            reason,
            category,
            policy != null ? policy.key() : "default",
            sourceStage,
            attempts
        );
        if (result != null) {
            appendQueueTrace(result, "DEAD_LETTER", sourceStage + ", category=" + category + ", attempts=" + attempts);
        }
    }

    private Set<String> resolveAutoReplayCategories() {
        String raw = deadLetterAutoReplayCategories;
        if (raw == null || raw.isBlank()) {
            return Set.of(PreviewFailureClassifier.CATEGORY_TEMPORARY);
        }
        Set<String> categories = new HashSet<>();
        for (String value : raw.split(",")) {
            String normalized = normalizeDeadLetterCategory(value);
            if (!normalized.isBlank()) {
                categories.add(normalized);
            }
        }
        if (categories.isEmpty()) {
            categories.add(PreviewFailureClassifier.CATEGORY_TEMPORARY);
        }
        return categories;
    }

    private static String normalizeDeadLetterCategory(String category) {
        if (category == null || category.isBlank()) {
            return "UNKNOWN";
        }
        return category.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeFailureCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        return category.trim().toUpperCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
