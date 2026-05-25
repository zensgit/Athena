package com.ecm.core.service;

import com.ecm.core.entity.ShareLink;
import com.ecm.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Orchestrates bulk creation of share links with per-row partial success.
 *
 * <p>Each row is created by delegating to the <b>proxied</b> {@link ShareLinkService}
 * (constructor-injected), so {@code createShareLink}'s {@code @Transactional} boundary engages
 * per row — a same-bean {@code this.createShareLink(...)} self-call would bypass the proxy.
 * This orchestrator is {@code NOT_SUPPORTED} so a row failure cannot mark an enclosing
 * transaction rollback-only; rows are isolated and the run continues after a failure.
 *
 * <p>Permission contract is unchanged: bulk reuses {@code createShareLink}, which enforces
 * {@code READ} on the node (gate ruling D1). FAILED rows carry a fixed, sanitized message —
 * never {@code ex.getMessage()}, and the raw throwable is never passed to SLF4J.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BulkShareLinkService {

    private final ShareLinkService shareLinkService;

    public enum Status { CREATED, FAILED }

    /** Closed set of per-row error categories; only ever set on a FAILED row. */
    public enum ErrorCategory { NODE_NOT_FOUND, NO_PERMISSION, VALIDATION_ERROR, INTERNAL_ERROR }

    public record RowResult(
        UUID nodeId,
        Status status,
        ShareLink shareLink,
        ErrorCategory errorCategory,
        String message
    ) {
        static RowResult created(UUID nodeId, ShareLink shareLink) {
            return new RowResult(nodeId, Status.CREATED, shareLink, null, null);
        }

        static RowResult failed(UUID nodeId, ErrorCategory category, String message) {
            return new RowResult(nodeId, Status.FAILED, null, category, message);
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<RowResult> createShareLinksBulk(
        List<UUID> nodeIds,
        ShareLinkService.CreateShareLinkRequest request
    ) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            throw new IllegalArgumentException("nodeIds must contain at least one entry");
        }
        // Drop nulls, dedupe first-seen (D3): a duplicate pasted ID must not create two links.
        LinkedHashSet<UUID> deduped = new LinkedHashSet<>();
        for (UUID nodeId : nodeIds) {
            if (nodeId != null) {
                deduped.add(nodeId);
            }
        }
        if (deduped.isEmpty()) {
            throw new IllegalArgumentException("nodeIds must contain at least one non-null entry");
        }

        List<RowResult> rows = new ArrayList<>(deduped.size());
        for (UUID nodeId : deduped) {
            rows.add(createOne(nodeId, request));
        }
        return rows;
    }

    private RowResult createOne(UUID nodeId, ShareLinkService.CreateShareLinkRequest request) {
        try {
            // Proxied call — engages createShareLink's @Transactional per row.
            ShareLink shareLink = shareLinkService.createShareLink(nodeId, request);
            return RowResult.created(nodeId, shareLink);
        } catch (NoSuchElementException | ResourceNotFoundException e) {
            return RowResult.failed(nodeId, ErrorCategory.NODE_NOT_FOUND, "The target node was not found.");
        } catch (SecurityException e) {
            return RowResult.failed(nodeId, ErrorCategory.NO_PERMISSION,
                "No permission to share the target node.");
        } catch (IllegalArgumentException e) {
            return RowResult.failed(nodeId, ErrorCategory.VALIDATION_ERROR,
                "The share-link request was invalid.");
        } catch (RuntimeException e) {
            // Sanitized: fixed copy + exception class only; never raw message, never raw Throwable to SLF4J.
            log.warn("Bulk share-link create failed for node {}: {}", nodeId, e.getClass().getSimpleName());
            return RowResult.failed(nodeId, ErrorCategory.INTERNAL_ERROR,
                "Failed to create the share link. (" + e.getClass().getSimpleName() + ")");
        }
    }
}
