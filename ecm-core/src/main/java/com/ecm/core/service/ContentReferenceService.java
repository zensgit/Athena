package com.ecm.core.service;

import com.ecm.core.entity.ContentReference;
import com.ecm.core.entity.ContentReference.OwnerType;
import com.ecm.core.repository.ContentReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative content binary reference tracking.
 * <p>
 * Business services must call {@link #attach} when a binary is assigned to a document,
 * version, working copy, or rendition, and {@link #detach} when that assignment ends.
 * Physical deletion is deferred to the scheduled orphan cleanup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ContentReferenceService {

    private final ContentReferenceRepository contentReferenceRepository;
    private final ContentService contentService;

    @Value("${ecm.storage.reference-ledger.enabled:true}")
    private boolean ledgerEnabled;

    @Value("${ecm.storage.orphan-cleanup.enabled:false}")
    private boolean orphanCleanupEnabled;

    @Value("${ecm.storage.orphan-cleanup.grace-hours:24}")
    private int orphanCleanupGraceHours;

    /**
     * Register a content binary as owned by a specific entity.
     * Idempotent: if the same (contentId, ownerType, ownerId) already exists and is active, this is a no-op.
     * If it was previously deactivated, it is reactivated.
     */
    public ContentReference attach(String contentId, OwnerType ownerType, UUID ownerId) {
        if (!ledgerEnabled || contentId == null || contentId.isBlank()) {
            return null;
        }

        var existing = contentReferenceRepository
                .findByContentIdAndOwnerTypeAndOwnerId(contentId, ownerType, ownerId);

        if (existing.isPresent()) {
            ContentReference ref = existing.get();
            if (!ref.isActive()) {
                ref.setActive(true);
                return contentReferenceRepository.save(ref);
            }
            return ref; // already active, idempotent
        }

        ContentReference ref = ContentReference.builder()
                .contentId(contentId)
                .ownerType(ownerType)
                .ownerId(ownerId)
                .active(true)
                .build();

        return contentReferenceRepository.save(ref);
    }

    /**
     * Mark a content reference as inactive. Does NOT physically delete the binary.
     * Returns the number of rows deactivated (0 or 1).
     */
    public int detach(String contentId, OwnerType ownerType, UUID ownerId) {
        if (!ledgerEnabled || contentId == null || contentId.isBlank()) {
            return 0;
        }

        int count = contentReferenceRepository.deactivate(contentId, ownerType, ownerId);
        if (count > 0) {
            log.debug("Detached content reference: contentId={}, ownerType={}, ownerId={}",
                    contentId, ownerType, ownerId);
        }
        return count;
    }

    /**
     * Keep ownership in sync when an entity switches from one content binary to another.
     * If content remains the same, ensures the active reference exists.
     */
    public void syncOwnerReference(String previousContentId, String currentContentId,
                                   OwnerType ownerType, UUID ownerId) {
        if (!ledgerEnabled || ownerId == null) {
            return;
        }

        String previous = normalizeContentId(previousContentId);
        String current = normalizeContentId(currentContentId);

        if (Objects.equals(previous, current)) {
            if (current != null) {
                attach(current, ownerType, ownerId);
            }
            return;
        }

        if (previous != null) {
            detach(previous, ownerType, ownerId);
        }
        if (current != null) {
            attach(current, ownerType, ownerId);
        }
    }

    /**
     * Check whether any active reference exists for a content binary.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveReferences(String contentId) {
        if (contentId == null || contentId.isBlank()) {
            return false;
        }
        return contentReferenceRepository.countByContentIdAndActiveTrue(contentId) > 0;
    }

    /**
     * Count active references for a content binary.
     */
    @Transactional(readOnly = true)
    public long countActiveReferences(String contentId) {
        if (contentId == null || contentId.isBlank()) {
            return 0;
        }
        return contentReferenceRepository.countByContentIdAndActiveTrue(contentId);
    }

    /**
     * List all active references for a content binary.
     */
    @Transactional(readOnly = true)
    public List<ContentReference> getActiveReferences(String contentId) {
        return contentReferenceRepository.findByContentIdAndActiveTrue(contentId);
    }

    /**
     * Scheduled orphan cleanup. Finds content IDs with zero active references
     * and physically deletes the binary after verifying no new references appeared.
     * <p>
     * Guarded by {@code ecm.storage.orphan-cleanup.enabled} (default: false).
     * Must only be enabled after backfill migration (073) is verified.
     */
    @Scheduled(cron = "${ecm.storage.orphan-cleanup.cron:0 0 3 * * *}")
    @Transactional
    public void cleanupOrphanedContent() {
        if (!orphanCleanupEnabled) {
            return;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusHours(Math.max(orphanCleanupGraceHours, 0));
        List<String> orphanCandidates = contentReferenceRepository.findEligibleOrphanContentIds(cutoff);
        if (orphanCandidates.isEmpty()) {
            return;
        }

        log.info("Orphan cleanup: found {} candidates", orphanCandidates.size());
        int deletedCount = 0;

        for (String contentId : orphanCandidates) {
            // Double-check: no active references appeared since the query
            if (contentReferenceRepository.countByContentIdAndActiveTrue(contentId) > 0) {
                log.debug("Orphan candidate {} now has active references, skipping", contentId);
                continue;
            }

            try {
                contentService.deleteContent(contentId);
                contentReferenceRepository.purgeInactiveReferences(contentId);
                deletedCount++;
                log.debug("Orphan cleanup: deleted content {}", contentId);
            } catch (IOException e) {
                log.warn("Orphan cleanup: failed to delete content {}: {}", contentId, e.getMessage());
            }
        }

        if (deletedCount > 0) {
            log.info("Orphan cleanup: deleted {} orphaned binaries", deletedCount);
        }
    }

    private String normalizeContentId(String contentId) {
        if (contentId == null || contentId.isBlank()) {
            return null;
        }
        return contentId;
    }
}
