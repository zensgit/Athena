package com.ecm.core.preview;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.queue.RedisScheduledQueueStore;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.search.SearchIndexService;
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
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreviewQueueService {

    private static final String REDIS_SCHEDULE_KEY = "ecm:queue:preview:schedule";
    private static final String REDIS_ATTEMPTS_KEY = "ecm:queue:preview:attempts";
    private static final String REDIS_LOCK_PREFIX = "ecm:queue:preview:lock:";
    private static final Duration REDIS_LOCK_TTL = Duration.ofMinutes(10);

    private final DocumentRepository documentRepository;
    private final PreviewService previewService;
    private final SearchIndexService searchIndexService;
    private final StringRedisTemplate redisTemplate;

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

    private final Queue<PreviewJob> queue = new ConcurrentLinkedQueue<>();
    private final Map<UUID, PreviewJob> queuedJobs = new ConcurrentHashMap<>();

    public PreviewQueueStatus enqueue(UUID documentId, boolean force) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId is required");
        }
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        PreviewStatus status = document.getPreviewStatus();
        if (!force && (status == PreviewStatus.READY || status == PreviewStatus.UNSUPPORTED)) {
            return new PreviewQueueStatus(documentId, status, false, 0, null);
        }

        if (useRedisBackend()) {
            return enqueueRedis(document, force);
        }

        PreviewJob existing = queuedJobs.get(documentId);
        if (existing != null) {
            return new PreviewQueueStatus(documentId, status, true, existing.attempts(), existing.nextAttemptAt());
        }

        PreviewJob job = new PreviewJob(documentId, 0, Instant.now());
        queuedJobs.put(documentId, job);
        queue.add(job);
        markProcessing(document);

        log.info("Queued preview generation for document {}", documentId);
        return new PreviewQueueStatus(documentId, status, true, 0, job.nextAttemptAt());
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

            if (job.nextAttemptAt().isAfter(now)) {
                queue.add(job);
                processed++;
                continue;
            }

            handleJob(job);
            processed++;
        }
    }

    private void handleJob(PreviewJob job) {
        UUID documentId = job.documentId();
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            queuedJobs.remove(documentId);
            return;
        }

        try {
            PreviewResult result = runAsSystem(() -> previewService.generatePreview(document));
            boolean retry = shouldRetry(result);
            if (retry && job.attempts() + 1 < maxAttempts) {
                markRetrying(document, result != null ? result.getMessage() : "Preview retry scheduled");
                scheduleRetry(job);
                return;
            }
            if (retry) {
                markFailed(document, result != null ? result.getMessage() : "Preview failed after retries");
            }
        } catch (Exception e) {
            if (job.attempts() + 1 < maxAttempts) {
                markRetrying(document, e.getMessage());
                scheduleRetry(job);
                return;
            }
            markFailed(document, e.getMessage());
            log.warn("Preview generation failed for {} after {} attempts: {}", documentId, job.attempts() + 1, e.getMessage());
        }

        queuedJobs.remove(documentId);
    }

    private PreviewQueueStatus enqueueRedis(Document document, boolean force) {
        UUID documentId = document.getId();
        PreviewStatus status = document.getPreviewStatus();

        RedisScheduledQueueStore store = redisStore();
        RedisScheduledQueueStore.Entry existing = store.getOrNull(documentId);
        if (existing != null) {
            return new PreviewQueueStatus(documentId, status, true, existing.attempts(), existing.nextAttemptAt());
        }

        RedisScheduledQueueStore.Entry job = store.enqueueIfAbsent(documentId, Instant.now());
        markProcessing(document);
        log.info("Queued preview generation for document {} (redis)", documentId);
        return new PreviewQueueStatus(documentId, status, true, job.attempts(), job.nextAttemptAt());
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
                handleRedisJob(store, entry);
            } finally {
                // Release is idempotent; complete() also releases.
                store.release(entry.documentId());
            }
        }
    }

    private void handleRedisJob(RedisScheduledQueueStore store, RedisScheduledQueueStore.Entry entry) {
        UUID documentId = entry.documentId();
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            store.complete(documentId);
            return;
        }

        try {
            PreviewResult result = runAsSystem(() -> previewService.generatePreview(document));
            boolean retry = shouldRetry(result);
            if (retry && entry.attempts() + 1 < maxAttempts) {
                markRetrying(document, result != null ? result.getMessage() : "Preview retry scheduled");
                int nextAttempts = entry.attempts() + 1;
                Instant nextRunAt = Instant.now().plusMillis(Math.max(1000, retryDelayMs));
                store.scheduleRetry(documentId, nextAttempts, nextRunAt);
                log.info("Scheduled preview retry {} for document {} at {} (redis)", nextAttempts, documentId, nextRunAt);
                return;
            }
            if (retry) {
                markFailed(document, result != null ? result.getMessage() : "Preview failed after retries");
            }
        } catch (Exception e) {
            if (entry.attempts() + 1 < maxAttempts) {
                markRetrying(document, e.getMessage());
                int nextAttempts = entry.attempts() + 1;
                Instant nextRunAt = Instant.now().plusMillis(Math.max(1000, retryDelayMs));
                store.scheduleRetry(documentId, nextAttempts, nextRunAt);
                log.info("Scheduled preview retry {} for document {} at {} (redis)", nextAttempts, documentId, nextRunAt);
                return;
            }
            markFailed(document, e.getMessage());
            log.warn("Preview generation failed for {} after {} attempts: {}", documentId, entry.attempts() + 1, e.getMessage());
        }

        store.complete(documentId);
    }

    private void scheduleRetry(PreviewJob job) {
        int nextAttempts = job.attempts() + 1;
        Instant nextRunAt = Instant.now().plusMillis(Math.max(1000, retryDelayMs));
        PreviewJob nextJob = new PreviewJob(job.documentId(), nextAttempts, nextRunAt);
        queuedJobs.put(job.documentId(), nextJob);
        queue.add(nextJob);
        log.info("Scheduled preview retry {} for document {} at {}", nextAttempts, job.documentId(), nextRunAt);
    }

    private void markProcessing(Document document) {
        try {
            document.setPreviewStatus(PreviewStatus.PROCESSING);
            document.setPreviewFailureReason(null);
            document.setPreviewLastUpdated(LocalDateTime.now());
            document.setPreviewAvailable(false);
            documentRepository.save(document);
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
            documentRepository.save(document);
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
            document.setPreviewStatus(PreviewStatus.FAILED);
            document.setPreviewFailureReason(failureReason);
            document.setPreviewLastUpdated(LocalDateTime.now());
            document.setPreviewAvailable(false);
            documentRepository.save(document);
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
        if (result.isSupported()) {
            return false;
        }
        String message = result.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }
        String category = PreviewFailureClassifier.classify(
            PreviewStatus.FAILED.name(),
            result.getMimeType(),
            message
        );
        return PreviewFailureClassifier.CATEGORY_TEMPORARY.equalsIgnoreCase(category);
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

    public record PreviewQueueStatus(
        UUID documentId,
        PreviewStatus previewStatus,
        boolean queued,
        int attempts,
        Instant nextAttemptAt
    ) {
    }

    private record PreviewJob(UUID documentId, int attempts, Instant nextAttemptAt) {
    }
}
