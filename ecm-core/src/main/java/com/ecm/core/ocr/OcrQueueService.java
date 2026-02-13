package com.ecm.core.ocr;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Correspondent;
import com.ecm.core.ml.MLServiceClient;
import com.ecm.core.queue.RedisScheduledQueueStore;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.search.SearchIndexService;
import com.ecm.core.service.ContentService;
import com.ecm.core.service.CorrespondentService;
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
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrQueueService {

    private static final String REDIS_SCHEDULE_KEY = "ecm:queue:ocr:schedule";
    private static final String REDIS_ATTEMPTS_KEY = "ecm:queue:ocr:attempts";
    private static final String REDIS_FORCE_KEY = "ecm:queue:ocr:force";
    private static final String REDIS_LOCK_PREFIX = "ecm:queue:ocr:lock:";
    private static final Duration REDIS_LOCK_TTL = Duration.ofMinutes(10);

    private static final String META_OCR_STATUS = "ocrStatus";
    private static final String META_OCR_FAILURE_REASON = "ocrFailureReason";
    private static final String META_OCR_LAST_UPDATED = "ocrLastUpdated";
    private static final String META_OCR_PROVIDER = "ocrProvider";
    private static final String META_OCR_LANGUAGE = "ocrLanguage";
    private static final String META_OCR_PAGES = "ocrPages";
    private static final String META_OCR_TRUNCATED = "ocrTruncated";
    private static final String META_OCR_CHARS = "ocrChars";

    private final DocumentRepository documentRepository;
    private final ContentService contentService;
    private final CorrespondentService correspondentService;
    private final SearchIndexService searchIndexService;
    private final MLServiceClient mlServiceClient;
    private final StringRedisTemplate redisTemplate;

    @Value("${ecm.ocr.enabled:false}")
    private boolean ocrEnabled;

    @Value("${ecm.ocr.language:eng}")
    private String ocrLanguage;

    @Value("${ecm.ocr.enrich.correspondent.enabled:false}")
    private boolean enrichCorrespondentEnabled;

    @Value("${ecm.ocr.max-pages:3}")
    private int maxPages;

    @Value("${ecm.ocr.max-bytes:20000000}")
    private long maxBytes;

    @Value("${ecm.ocr.max-chars:200000}")
    private int maxChars;

    @Value("${ecm.ocr.queue.enabled:true}")
    private boolean queueEnabled;

    @Value("${ecm.ocr.queue.backend:memory}")
    private String queueBackend;

    @Value("${ecm.ocr.queue.max-attempts:2}")
    private int maxAttempts;

    @Value("${ecm.ocr.queue.retry-delay-ms:60000}")
    private long retryDelayMs;

    @Value("${ecm.ocr.queue.batch-size:1}")
    private int batchSize;

    @Value("${ecm.ocr.queue.run-as-user:admin}")
    private String runAsUser;

    private final Queue<OcrJob> queue = new ConcurrentLinkedQueue<>();
    private final Map<UUID, OcrJob> queuedJobs = new ConcurrentHashMap<>();

    public OcrQueueStatus enqueue(UUID documentId, boolean force) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId is required");
        }
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        if (!ocrEnabled) {
            return new OcrQueueStatus(documentId, getOcrStatus(document), false, 0, null, "OCR disabled");
        }

        if (!force && !isEligible(document, false)) {
            return new OcrQueueStatus(documentId, getOcrStatus(document), false, 0, null, "Not eligible for OCR");
        }

        if (useRedisBackend()) {
            return enqueueRedis(document, force);
        }

        OcrJob existing = queuedJobs.get(documentId);
        if (existing != null) {
            return new OcrQueueStatus(documentId, getOcrStatus(document), true, existing.attempts(), existing.nextAttemptAt(), null);
        }

        OcrJob job = new OcrJob(documentId, 0, Instant.now(), force);
        queuedJobs.put(documentId, job);
        queue.add(job);
        markProcessing(document);

        log.info("Queued OCR extraction for document {}", documentId);
        return new OcrQueueStatus(documentId, getOcrStatus(document), true, 0, job.nextAttemptAt(), null);
    }

    @Scheduled(fixedDelayString = "${ecm.ocr.queue.poll-interval-ms:5000}")
    public void processQueue() {
        if (useRedisBackend()) {
            processRedisQueue();
            return;
        }

        if (!queueEnabled || !ocrEnabled) {
            return;
        }
        int limit = Math.max(1, batchSize);
        int processed = 0;
        Instant now = Instant.now();

        while (processed < limit) {
            OcrJob job = queue.poll();
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

    private void handleJob(OcrJob job) {
        UUID documentId = job.documentId();
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            queuedJobs.remove(documentId);
            return;
        }

        if (!isEligible(document, job.force())) {
            markSkipped(document, "Not eligible for OCR");
            queuedJobs.remove(documentId);
            return;
        }

        try {
            runAsSystem(() -> {
                extractAndPersist(document, job.force());
                return null;
            });
            queuedJobs.remove(documentId);
        } catch (Exception e) {
            if (job.attempts() + 1 < maxAttempts) {
                markRetrying(document, e.getMessage());
                scheduleRetry(job);
                return;
            }
            markFailed(document, e.getMessage());
            queuedJobs.remove(documentId);
            log.warn("OCR failed for {} after {} attempts: {}", documentId, job.attempts() + 1, e.getMessage());
        }
    }

    private OcrQueueStatus enqueueRedis(Document document, boolean force) {
        UUID documentId = document.getId();

        RedisScheduledQueueStore store = redisStore();
        RedisScheduledQueueStore.Entry existing = store.getOrNull(documentId);
        if (existing != null) {
            // Force upgrades are sticky; do not downgrade an existing force job.
            if (force) {
                redisTemplate.opsForHash().put(REDIS_FORCE_KEY, documentId.toString(), "1");
            }
            return new OcrQueueStatus(documentId, getOcrStatus(document), true, existing.attempts(), existing.nextAttemptAt(), null);
        }

        RedisScheduledQueueStore.Entry job = store.enqueueIfAbsent(documentId, Instant.now());
        if (force) {
            redisTemplate.opsForHash().put(REDIS_FORCE_KEY, documentId.toString(), "1");
        }
        markProcessing(document);
        log.info("Queued OCR extraction for document {} (redis)", documentId);
        return new OcrQueueStatus(documentId, getOcrStatus(document), true, job.attempts(), job.nextAttemptAt(), null);
    }

    private void processRedisQueue() {
        if (!queueEnabled || !ocrEnabled) {
            return;
        }
        int limit = Math.max(1, batchSize);
        Instant now = Instant.now();

        RedisScheduledQueueStore store = redisStore();
        List<RedisScheduledQueueStore.Entry> due = store.claimDue(limit, now);
        for (RedisScheduledQueueStore.Entry entry : due) {
            try {
                boolean force = isForceQueued(entry.documentId());
                handleRedisJob(store, entry, force);
            } finally {
                store.release(entry.documentId());
            }
        }
    }

    private void handleRedisJob(RedisScheduledQueueStore store, RedisScheduledQueueStore.Entry entry, boolean force) {
        UUID documentId = entry.documentId();
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            clearRedisJob(store, documentId);
            return;
        }

        if (!isEligible(document, force)) {
            markSkipped(document, "Not eligible for OCR");
            clearRedisJob(store, documentId);
            return;
        }

        try {
            runAsSystem(() -> {
                extractAndPersist(document, force);
                return null;
            });
            clearRedisJob(store, documentId);
        } catch (Exception e) {
            if (entry.attempts() + 1 < maxAttempts) {
                markRetrying(document, e.getMessage());
                int nextAttempts = entry.attempts() + 1;
                Instant nextRunAt = Instant.now().plusMillis(Math.max(1000, retryDelayMs));
                store.scheduleRetry(documentId, nextAttempts, nextRunAt);
                log.info("Scheduled OCR retry {} for document {} at {} (redis)", nextAttempts, documentId, nextRunAt);
                return;
            }
            markFailed(document, e.getMessage());
            clearRedisJob(store, documentId);
            log.warn("OCR failed for {} after {} attempts: {}", documentId, entry.attempts() + 1, e.getMessage());
        }
    }

    private void clearRedisJob(RedisScheduledQueueStore store, UUID documentId) {
        store.complete(documentId);
        redisTemplate.opsForHash().delete(REDIS_FORCE_KEY, documentId.toString());
    }

    private boolean isForceQueued(UUID documentId) {
        if (documentId == null) {
            return false;
        }
        Object val = redisTemplate.opsForHash().get(REDIS_FORCE_KEY, documentId.toString());
        return "1".equals(String.valueOf(val));
    }

    private void extractAndPersist(Document document, boolean force) throws Exception {
        byte[] bytes = readContentBytes(document);
        String language = (ocrLanguage == null || ocrLanguage.isBlank()) ? "eng" : ocrLanguage.trim();
        int pages = Math.max(1, Math.min(maxPages, 20));
        int chars = Math.max(1000, Math.min(maxChars, 2_000_000));

        MLServiceClient.OcrResult result = mlServiceClient.ocr(bytes, document.getName(), document.getMimeType(), language, pages, chars);
        if (!result.isSuccess()) {
            String reason = result.getErrorMessage() != null ? result.getErrorMessage() : "OCR failed";
            throw new IllegalStateException(reason);
        }

        String text = (result.getText() != null ? result.getText() : "").trim();
        if (text.isEmpty()) {
            markSkipped(document, "OCR returned empty text");
            return;
        }

        if (document.getTextContent() == null || document.getTextContent().isBlank()) {
            document.setTextContent(text);
        } else if (force) {
            // Force runs may append; normal runs avoid duplicating existing text.
            document.setTextContent((document.getTextContent() + "\n\n[OCR]\n" + text).trim());
        }

        if (enrichCorrespondentEnabled && document.getCorrespondent() == null) {
            try {
                Correspondent match = correspondentService.matchCorrespondent(text);
                if (match != null) {
                    document.setCorrespondent(match);
                    log.info("OCR enrichment: matched correspondent '{}' for document {}", match.getName(), document.getId());
                }
            } catch (Exception e) {
                log.debug("OCR enrichment correspondent match failed for {}: {}", document.getId(), e.getMessage());
            }
        }

        Map<String, Object> metadata = document.getMetadata();
        metadata.put(META_OCR_STATUS, "READY");
        metadata.remove(META_OCR_FAILURE_REASON);
        metadata.put(META_OCR_LAST_UPDATED, Instant.now().toString());
        metadata.put(META_OCR_PROVIDER, "ml-service");
        metadata.put(META_OCR_LANGUAGE, language);
        metadata.put(META_OCR_PAGES, result.getPages());
        metadata.put(META_OCR_TRUNCATED, result.isTruncated());
        metadata.put(META_OCR_CHARS, text.length());

        documentRepository.save(document);
        try {
            searchIndexService.updateDocument(document);
        } catch (Exception indexError) {
            log.debug("Failed to index OCR update for {}: {}", document.getId(), indexError.getMessage());
        }
    }

    private byte[] readContentBytes(Document document) throws Exception {
        if (document.getContentId() == null) {
            throw new IllegalStateException("Missing contentId for document " + document.getId());
        }

        int limit = (int) Math.min(Integer.MAX_VALUE, Math.max(1024, maxBytes));
        try (InputStream in = contentService.getContent(document.getContentId())) {
            byte[] data = in.readNBytes(limit + 1);
            if (data.length > limit) {
                throw new IllegalArgumentException("OCR input too large (" + data.length + " bytes > " + limit + ")");
            }
            return data;
        }
    }

    private boolean isEligible(Document document, boolean force) {
        if (document == null) {
            return false;
        }

        String mime = (document.getMimeType() != null ? document.getMimeType() : "").toLowerCase(Locale.ROOT);
        if (!(mime.equals("application/pdf") || mime.startsWith("image/"))) {
            return false;
        }

        Long size = document.getFileSize();
        if (size != null && size > Math.max(1024, maxBytes)) {
            return false;
        }

        if (!force) {
            String text = document.getTextContent();
            if (text != null && text.trim().length() >= 50) {
                // Already has meaningful text; avoid OCR by default.
                return false;
            }
        }

        // Eligibility is based on MIME/size/text presence. Queue de-duplication is handled separately.
        return true;
    }

    private String getOcrStatus(Document document) {
        Object status = document.getMetadata() != null ? document.getMetadata().get(META_OCR_STATUS) : null;
        return status instanceof String s ? s : null;
    }

    private void scheduleRetry(OcrJob job) {
        int nextAttempts = job.attempts() + 1;
        Instant nextRunAt = Instant.now().plusMillis(Math.max(1000, retryDelayMs));
        OcrJob nextJob = new OcrJob(job.documentId(), nextAttempts, nextRunAt, job.force());
        queuedJobs.put(job.documentId(), nextJob);
        queue.add(nextJob);
        log.info("Scheduled OCR retry {} for document {} at {}", nextAttempts, job.documentId(), nextRunAt);
    }

    private void markProcessing(Document document) {
        setOcrMetadata(document, "PROCESSING", null);
    }

    private void markRetrying(Document document, String failureReason) {
        setOcrMetadata(document, "PROCESSING", failureReason != null ? ("Retry scheduled: " + failureReason) : "Retry scheduled");
    }

    private void markFailed(Document document, String failureReason) {
        setOcrMetadata(document, "FAILED", failureReason != null ? failureReason : "OCR failed");
    }

    private void markSkipped(Document document, String reason) {
        setOcrMetadata(document, "SKIPPED", reason);
    }

    private void setOcrMetadata(Document document, String status, String failureReason) {
        try {
            Map<String, Object> metadata = document.getMetadata();
            metadata.put(META_OCR_STATUS, status);
            if (failureReason == null || failureReason.isBlank()) {
                metadata.remove(META_OCR_FAILURE_REASON);
            } else {
                metadata.put(META_OCR_FAILURE_REASON, failureReason);
            }
            metadata.put(META_OCR_LAST_UPDATED, Instant.now().toString());
            documentRepository.save(document);
        } catch (Exception e) {
            log.warn("Failed to update OCR status for {}: {}", document.getId(), e.getMessage());
        }
    }

    private <T> T runAsSystem(OcrTask<T> task) throws Exception {
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
    private interface OcrTask<T> {
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

    public record OcrQueueStatus(
        UUID documentId,
        String ocrStatus,
        boolean queued,
        int attempts,
        Instant nextAttemptAt,
        String message
    ) {
    }

    private record OcrJob(UUID documentId, int attempts, Instant nextAttemptAt, boolean force) {
    }
}
