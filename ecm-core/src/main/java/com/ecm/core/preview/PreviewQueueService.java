package com.ecm.core.preview;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
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
public class PreviewQueueService {

    private final DocumentRepository documentRepository;
    private final PreviewService previewService;

    @Value("${ecm.preview.queue.enabled:true}")
    private boolean queueEnabled;

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
        if (!force && status == PreviewStatus.READY) {
            return new PreviewQueueStatus(documentId, status, false, 0, null);
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
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.startsWith("error generating preview") || lower.startsWith("cad preview failed");
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
