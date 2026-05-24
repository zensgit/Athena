package com.ecm.core.service;

import com.ecm.core.entity.LegalHold;
import com.ecm.core.entity.LegalHoldItem;
import com.ecm.core.entity.Node;
import com.ecm.core.exception.IllegalOperationException;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.repository.LegalHoldItemRepository;
import com.ecm.core.repository.LegalHoldRepository;
import com.ecm.core.repository.NodeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Service
public class LegalHoldService {

    // Stable fixed copies for BulkApplyResult.errorMessage. Phase 2 logging
    // audit + bulk-site-invitation precedent: never echo ex.getMessage() or
    // the input UUID back through errorMessage.
    static final String BULK_APPLY_ERROR_NODE_NOT_FOUND =
        "Requested node was not found.";
    static final String BULK_APPLY_ERROR_NODE_NOT_VISIBLE =
        "Requested node is not visible in the current tenant workspace.";
    static final String BULK_APPLY_ERROR_INTERNAL =
        "Unexpected error during bulk apply";
    static final String BULK_APPLY_ERROR_HOLD_NOT_ACTIVE =
        "Parent hold is no longer active.";

    private final LegalHoldRepository legalHoldRepository;
    private final LegalHoldItemRepository legalHoldItemRepository;
    private final NodeRepository nodeRepository;
    private final SecurityService securityService;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;

    // Two TransactionTemplate fields built from the same PlatformTransactionManager.
    // Both are necessary for the bulk-apply orchestration pattern (gate-corrected
    // 2026-05-24): the createHold orchestrator runs OUTSIDE any transaction
    // (@Transactional(NOT_SUPPORTED) below), so each step opens its own
    // transaction explicitly. See
    // docs/LEGAL_HOLD_BULK_APPLY_AND_RELEASE_REASON_DESIGN_20260524.md
    // §"Transactional strategy".
    private final TransactionTemplate parentHoldTransactionTemplate;
    private final TransactionTemplate bulkRowTransactionTemplate;

    /**
     * Explicit constructor (replacing the previous Lombok
     * {@code @RequiredArgsConstructor}) so the two {@link TransactionTemplate}
     * fields can be built deterministically from the injected
     * {@link PlatformTransactionManager}. The previous class-level
     * {@code @Transactional} annotation has also been removed; each public
     * method that needs a transaction now annotates explicitly. The
     * {@link #createHold(CreateLegalHoldRequest)} orchestrator method is
     * explicitly NOT transactional ({@code @Transactional(NOT_SUPPORTED)})
     * so its inner per-row REQUIRES_NEW transactions can see the
     * already-committed parent hold (see gate review 2026-05-24).
     */
    public LegalHoldService(
        LegalHoldRepository legalHoldRepository,
        LegalHoldItemRepository legalHoldItemRepository,
        NodeRepository nodeRepository,
        SecurityService securityService,
        TenantWorkspaceScopeService tenantWorkspaceScopeService,
        PlatformTransactionManager transactionManager
    ) {
        this.legalHoldRepository = legalHoldRepository;
        this.legalHoldItemRepository = legalHoldItemRepository;
        this.nodeRepository = nodeRepository;
        this.securityService = securityService;
        this.tenantWorkspaceScopeService = tenantWorkspaceScopeService;
        this.parentHoldTransactionTemplate = new TransactionTemplate(transactionManager);
        TransactionTemplate rowTemplate = new TransactionTemplate(transactionManager);
        rowTemplate.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.bulkRowTransactionTemplate = rowTemplate;
    }

    @Transactional(readOnly = true)
    public List<LegalHoldSummaryDto> listHolds() {
        requireAdmin();
        return legalHoldRepository.findByDeletedFalseOrderByCreatedDateDesc().stream()
            .filter(this::isHoldVisible)
            .map(this::toSummaryDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public LegalHoldDto getHold(UUID holdId) {
        requireAdmin();
        LegalHold hold = requireHold(holdId);
        List<LegalHoldItemDto> items = visibleItems(hold);
        if (!items.isEmpty() || !tenantWorkspaceScopeService.hasScopedTenantWorkspace()) {
            return toDto(hold, items, null);
        }
        throw new ResourceNotFoundException("Legal hold not found: " + holdId);
    }

    /**
     * Bulk-aware create. When {@code request.nodeIds()} is null or empty this
     * is functionally identical to the previous single-row create. When
     * present, the parent hold is committed first in its own transaction;
     * each {@code nodeId} is then added via an independent REQUIRES_NEW
     * transaction so partial failures do not roll back rows that succeeded
     * AND inner transactions can see the already-committed parent through the
     * FK (the trap the gate flagged 2026-05-24).
     *
     * <p>Annotated {@code @Transactional(NOT_SUPPORTED)} so the orchestrator
     * runs outside any transaction. The body uses two
     * {@link TransactionTemplate}s explicitly. Spring proxy intercepts this
     * method but the propagation tells it to suspend any incoming
     * transaction, which fits this orchestration use case.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LegalHoldDto createHold(CreateLegalHoldRequest request) {
        requireAdmin();
        if (request == null || !StringUtils.hasText(request.name())) {
            throw new IllegalArgumentException("Legal hold name is required");
        }

        // Step 1: commit the parent hold in its own transaction. Once execute()
        // returns, the parent hold row is committed and visible to other
        // transactions.
        LegalHold saved = parentHoldTransactionTemplate.execute(status -> {
            LegalHold hold = new LegalHold();
            hold.setName(request.name().trim());
            hold.setDescription(trimToNull(request.description()));
            return legalHoldRepository.save(hold);
        });
        log.info("Created legal hold {} ({})", saved.getName(), saved.getId());

        // Step 2: per-row apply, only when nodeIds were supplied. Each row runs
        // in REQUIRES_NEW — the parent hold is now committed and visible.
        BulkApplyResults applyResults = null;
        if (request.nodeIds() != null && !request.nodeIds().isEmpty()) {
            applyResults = applyBulkItems(saved.getId(), request.nodeIds());
        }

        // Step 3: read-only reload with visibility-filtered items + the bulk
        // results attached. Read-only template ensures the projection runs in
        // its own short transaction.
        TransactionTemplate readTemplate = new TransactionTemplate(parentHoldTransactionTemplate.getTransactionManager());
        readTemplate.setReadOnly(true);
        final BulkApplyResults finalApplyResults = applyResults;
        return readTemplate.execute(status -> {
            LegalHold reloaded = requireHold(saved.getId());
            return toDto(reloaded, visibleItems(reloaded), finalApplyResults);
        });
    }

    @Transactional
    public LegalHoldDto addItems(UUID holdId, AddHoldItemsRequest request) {
        requireAdmin();
        LegalHold hold = requireActiveHold(holdId);
        if (request == null || request.nodeIds() == null || request.nodeIds().isEmpty()) {
            throw new IllegalArgumentException("At least one nodeId is required");
        }

        String currentUser = securityService.getCurrentUser();
        for (UUID nodeId : new LinkedHashSet<>(request.nodeIds())) {
            if (legalHoldItemRepository.existsByHoldIdAndNodeId(holdId, nodeId)) {
                continue;
            }
            Node node = requireLiveNode(nodeId);
            LegalHoldItem item = new LegalHoldItem();
            item.setHold(hold);
            item.setNode(node);
            item.setNodeType(node.getNodeType());
            item.setNodePath(node.getPath());
            item.setAddedBy(currentUser);
            legalHoldItemRepository.save(item);
        }

        return getHold(holdId);
    }

    @Transactional
    public LegalHoldDto removeItem(UUID holdId, UUID nodeId) {
        requireAdmin();
        requireActiveHold(holdId);
        LegalHoldItem item = legalHoldItemRepository.findByHoldIdAndNodeId(holdId, nodeId)
            .orElseThrow(() -> new NoSuchElementException("Legal hold item not found for node: " + nodeId));
        legalHoldItemRepository.delete(item);
        return getHold(holdId);
    }

    @Transactional
    public LegalHoldDto releaseHold(UUID holdId, ReleaseLegalHoldRequest request) {
        requireAdmin();
        LegalHold hold = requireActiveHold(holdId);
        if (request == null || request.releaseReason() == null) {
            throw new IllegalArgumentException(
                "releaseReason is required (one of: LITIGATION_ENDED, SCHEDULED_DISPOSITION, "
                    + "REQUEST_BY_REQUESTOR, OTHER)"
            );
        }
        hold.setStatus(LegalHold.HoldStatus.RELEASED);
        hold.setReleasedAt(LocalDateTime.now());
        hold.setReleasedBy(securityService.getCurrentUser());
        hold.setReleaseReason(request.releaseReason());
        hold.setReleaseComment(trimToNull(request.comment()));
        legalHoldRepository.save(hold);
        log.info(
            "Released legal hold {} ({}) with reason {}",
            hold.getName(),
            hold.getId(),
            request.releaseReason()
        );
        return getHold(holdId);
    }

    @Transactional(readOnly = true)
    public void assertOperationAllowed(Node node, String operation) {
        if (node == null) {
            return;
        }
        List<BlockingHoldDto> blockingHolds = findBlockingActiveHolds(node);
        if (blockingHolds.isEmpty()) {
            return;
        }
        String holdNames = blockingHolds.stream()
            .map(BlockingHoldDto::holdName)
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .reduce((left, right) -> left + ", " + right)
            .orElse("unknown");
        throw new IllegalOperationException(
            "Cannot " + operation + " because node '" + node.getName() + "' is under active legal hold(s): " + holdNames
        );
    }

    @Transactional(readOnly = true)
    public List<BlockingHoldDto> findBlockingActiveHolds(Node node) {
        if (node == null) {
            return List.of();
        }

        Map<UUID, BlockingHoldDto> blocking = new LinkedHashMap<>();
        for (LegalHoldItem item : legalHoldItemRepository.findActiveItems(LegalHold.HoldStatus.ACTIVE)) {
            if (intersects(node, item)) {
                LegalHold hold = item.getHold();
                blocking.putIfAbsent(
                    hold.getId(),
                    new BlockingHoldDto(
                        hold.getId(),
                        hold.getName(),
                        item.getNode().getId(),
                        item.getNode().getName(),
                        item.getNodePath()
                    )
                );
            }
        }
        return blocking.values().stream()
            .sorted(Comparator.comparing(BlockingHoldDto::holdName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    /**
     * Per-row bulk-apply driver. Each entry in {@code nodeIds} is applied in
     * an independent REQUIRES_NEW transaction so failures do not affect prior
     * commits. Duplicates (already-held nodes) are recorded as
     * {@link BulkApplyResult.Status#SKIPPED_DUPLICATE}; missing or invisible
     * nodes map to {@link BulkApplyErrorCategory#NODE_NOT_FOUND} /
     * {@link BulkApplyErrorCategory#NODE_NOT_VISIBLE}; any other {@code Throwable}
     * collapses to {@link BulkApplyErrorCategory#INTERNAL_ERROR}.
     *
     * <p>Per {@code feedback_sanitize_throwable_cause_for_log_emission}:
     * the INTERNAL_ERROR branch never echoes {@code ex.getMessage()} into the
     * response and never passes the raw {@link Throwable} to SLF4J. Only the
     * exception class simple name is surfaced.
     */
    private BulkApplyResults applyBulkItems(UUID holdId, List<UUID> nodeIds) {
        List<BulkApplyResult> rows = new ArrayList<>(nodeIds.size());
        // Dedup at the orchestrator level — same UUID twice in the request
        // gets only ONE attempt (and the second appearance does not produce
        // a SKIPPED_DUPLICATE row, only the underlying repository check does
        // for cases already persisted from earlier requests).
        LinkedHashSet<UUID> deduped = new LinkedHashSet<>();
        for (UUID id : nodeIds) {
            if (id != null) {
                deduped.add(id);
            }
        }

        for (UUID nodeId : deduped) {
            try {
                LegalHoldItemDto added = bulkRowTransactionTemplate.execute(status ->
                    applyOneItem(holdId, nodeId)
                );
                if (added == null) {
                    rows.add(BulkApplyResult.skippedDuplicate(nodeId));
                } else {
                    rows.add(BulkApplyResult.added(nodeId, added));
                }
            } catch (NodeNotFoundForHoldException ex) {
                rows.add(BulkApplyResult.failed(
                    nodeId,
                    BulkApplyErrorCategory.NODE_NOT_FOUND,
                    BULK_APPLY_ERROR_NODE_NOT_FOUND
                ));
            } catch (NodeNotVisibleForHoldException ex) {
                rows.add(BulkApplyResult.failed(
                    nodeId,
                    BulkApplyErrorCategory.NODE_NOT_VISIBLE,
                    BULK_APPLY_ERROR_NODE_NOT_VISIBLE
                ));
            } catch (RuntimeException ex) {
                // Phase 2 logging audit follow-up #1: never include
                // ex.getMessage() in the response; never pass the raw ex to
                // SLF4J. Class name only.
                log.debug(
                    "Bulk apply per-row internal error: holdId={} class={}",
                    holdId,
                    ex.getClass().getSimpleName()
                );
                rows.add(BulkApplyResult.failed(
                    nodeId,
                    BulkApplyErrorCategory.INTERNAL_ERROR,
                    BULK_APPLY_ERROR_INTERNAL
                        + " (" + ex.getClass().getSimpleName() + ")."
                ));
            }
        }
        return new BulkApplyResults(rows);
    }

    /**
     * Single-row apply, executed inside a per-row REQUIRES_NEW transaction
     * (driven by {@link #bulkRowTransactionTemplate} in {@link #applyBulkItems}).
     * Returns null to signal SKIPPED_DUPLICATE.
     */
    private LegalHoldItemDto applyOneItem(UUID holdId, UUID nodeId) {
        // The parent hold was committed by the outer orchestrator; this
        // REQUIRES_NEW transaction sees it through the FK without trouble.
        LegalHold hold = legalHoldRepository.findById(holdId)
            .filter(h -> !h.isDeleted())
            .orElseThrow(() -> new IllegalStateException(
                "Parent hold disappeared between commit and per-row apply: " + holdId
            ));
        if (hold.getStatus() != LegalHold.HoldStatus.ACTIVE) {
            // Edge race documented in the design brief: parent was released
            // between create-commit and per-row apply.
            throw new IllegalStateException(BULK_APPLY_ERROR_HOLD_NOT_ACTIVE);
        }
        if (legalHoldItemRepository.existsByHoldIdAndNodeId(holdId, nodeId)) {
            return null;
        }
        Node node = nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nodeId, Node.ArchiveStatus.LIVE)
            .orElseThrow(() -> new NodeNotFoundForHoldException(nodeId));
        if (!tenantWorkspaceScopeService.isPathVisible(node.getPath())) {
            throw new NodeNotVisibleForHoldException(nodeId);
        }
        LegalHoldItem item = new LegalHoldItem();
        item.setHold(hold);
        item.setNode(node);
        item.setNodeType(node.getNodeType());
        item.setNodePath(node.getPath());
        item.setAddedBy(securityService.getCurrentUser());
        LegalHoldItem savedItem = legalHoldItemRepository.save(item);
        return new LegalHoldItemDto(
            node.getId(),
            node.getName(),
            node.getNodeType().name(),
            node.getPath(),
            savedItem.getAddedAt(),
            savedItem.getAddedBy()
        );
    }

    private boolean intersects(Node target, LegalHoldItem item) {
        if (item.getNode() != null && item.getNode().getId() != null && item.getNode().getId().equals(target.getId())) {
            return true;
        }
        String targetPath = normalizePath(target.getPath());
        String heldPath = normalizePath(item.getNodePath());
        if (targetPath == null || heldPath == null) {
            return false;
        }
        return sameOrDescendant(targetPath, heldPath) || sameOrDescendant(heldPath, targetPath);
    }

    private boolean sameOrDescendant(String path, String candidateAncestor) {
        return path.equals(candidateAncestor) || path.startsWith(candidateAncestor + "/");
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String normalized = path.trim();
        if (normalized.endsWith("/") && normalized.length() > 1) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean isHoldVisible(LegalHold hold) {
        if (!tenantWorkspaceScopeService.hasScopedTenantWorkspace()) {
            return true;
        }
        return legalHoldItemRepository.findByHoldIdWithNode(hold.getId()).stream()
            .map(LegalHoldItem::getNodePath)
            .anyMatch(tenantWorkspaceScopeService::isPathVisible);
    }

    private List<LegalHoldItemDto> visibleItems(LegalHold hold) {
        List<LegalHoldItemDto> items = new ArrayList<>();
        boolean scoped = tenantWorkspaceScopeService.hasScopedTenantWorkspace();
        for (LegalHoldItem item : legalHoldItemRepository.findByHoldIdWithNode(hold.getId())) {
            if (scoped && !tenantWorkspaceScopeService.isPathVisible(item.getNodePath())) {
                continue;
            }
            items.add(new LegalHoldItemDto(
                item.getNode().getId(),
                item.getNode().getName(),
                item.getNodeType().name(),
                item.getNodePath(),
                item.getAddedAt(),
                item.getAddedBy()
            ));
        }
        return items;
    }

    private LegalHold requireHold(UUID holdId) {
        return legalHoldRepository.findById(holdId)
            .filter(hold -> !hold.isDeleted())
            .orElseThrow(() -> new NoSuchElementException("Legal hold not found: " + holdId));
    }

    private LegalHold requireActiveHold(UUID holdId) {
        LegalHold hold = requireHold(holdId);
        if (hold.getStatus() != LegalHold.HoldStatus.ACTIVE) {
            throw new IllegalStateException("Legal hold is already released: " + holdId);
        }
        return hold;
    }

    private Node requireLiveNode(UUID nodeId) {
        Node node = nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nodeId, Node.ArchiveStatus.LIVE)
            .orElseThrow(() -> new NoSuchElementException("Node not found: " + nodeId));
        if (!tenantWorkspaceScopeService.isPathVisible(node.getPath())) {
            throw new ResourceNotFoundException("Node not found: " + nodeId);
        }
        return node;
    }

    private void requireAdmin() {
        if (!securityService.hasRole("ROLE_ADMIN")) {
            throw new SecurityException("Admin access required for legal hold operations");
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private LegalHoldSummaryDto toSummaryDto(LegalHold hold) {
        return new LegalHoldSummaryDto(
            hold.getId(),
            hold.getName(),
            hold.getDescription(),
            hold.getStatus(),
            legalHoldItemRepository.countByHoldId(hold.getId()),
            hold.getCreatedBy(),
            hold.getCreatedDate(),
            hold.getReleasedBy(),
            hold.getReleasedAt(),
            hold.getReleaseReason()
        );
    }

    private LegalHoldDto toDto(LegalHold hold, List<LegalHoldItemDto> items, BulkApplyResults bulkApplyResults) {
        return new LegalHoldDto(
            hold.getId(),
            hold.getName(),
            hold.getDescription(),
            hold.getStatus(),
            hold.getCreatedBy(),
            hold.getCreatedDate(),
            hold.getReleasedBy(),
            hold.getReleasedAt(),
            hold.getReleaseComment(),
            hold.getReleaseReason(),
            items.size(),
            items,
            bulkApplyResults
        );
    }

    public record CreateLegalHoldRequest(
        String name,
        String description,
        // Optional bulk apply since 2026-05-24. Empty / null / missing →
        // identical to previous behavior (no items added). When present,
        // each entry is best-effort applied via independent REQUIRES_NEW
        // transactions; failed rows do not roll back the hold or other
        // committed rows. See design doc §"Transactional strategy".
        List<UUID> nodeIds
    ) {
    }

    public record AddHoldItemsRequest(List<UUID> nodeIds) {
    }

    public record ReleaseLegalHoldRequest(
        // Required since 2026-05-24. Null release reason → HTTP 400.
        LegalHold.HoldReleaseReason releaseReason,
        String comment
    ) {
    }

    public record LegalHoldSummaryDto(
        UUID id,
        String name,
        String description,
        LegalHold.HoldStatus status,
        long itemCount,
        String createdBy,
        LocalDateTime createdDate,
        String releasedBy,
        LocalDateTime releasedAt,
        LegalHold.HoldReleaseReason releaseReason
    ) {
    }

    public record LegalHoldItemDto(
        UUID nodeId,
        String nodeName,
        String nodeType,
        String nodePath,
        LocalDateTime addedAt,
        String addedBy
    ) {
    }

    public record LegalHoldDto(
        UUID id,
        String name,
        String description,
        LegalHold.HoldStatus status,
        String createdBy,
        LocalDateTime createdDate,
        String releasedBy,
        LocalDateTime releasedAt,
        String releaseComment,
        LegalHold.HoldReleaseReason releaseReason,
        int itemCount,
        List<LegalHoldItemDto> items,
        // Non-null only when the create request supplied nodeIds. Null on
        // single-row create (back-compat) and on all other endpoints' DTO
        // responses (e.g. getHold, addItems, removeItem, releaseHold).
        BulkApplyResults bulkApplyResults
    ) {
    }

    public record BlockingHoldDto(
        UUID holdId,
        String holdName,
        UUID nodeId,
        String nodeName,
        String nodePath
    ) {
    }

    public record BulkApplyResults(
        List<BulkApplyResult> rows
    ) {
    }

    public record BulkApplyResult(
        UUID requestedNodeId,
        Status status,
        LegalHoldItemDto item,
        BulkApplyErrorCategory errorCategory,
        String errorMessage
    ) {
        public enum Status { ADDED, SKIPPED_DUPLICATE, FAILED }

        public static BulkApplyResult added(UUID nodeId, LegalHoldItemDto item) {
            return new BulkApplyResult(nodeId, Status.ADDED, item, null, null);
        }

        public static BulkApplyResult skippedDuplicate(UUID nodeId) {
            return new BulkApplyResult(nodeId, Status.SKIPPED_DUPLICATE, null, null, null);
        }

        public static BulkApplyResult failed(
            UUID nodeId,
            BulkApplyErrorCategory category,
            String message
        ) {
            return new BulkApplyResult(nodeId, Status.FAILED, null, category, message);
        }
    }

    public enum BulkApplyErrorCategory {
        NODE_NOT_FOUND,
        NODE_NOT_VISIBLE,
        INTERNAL_ERROR
    }

    /**
     * Marker exception thrown from the per-row apply helper. Both subclasses
     * extend {@link RuntimeException} so they bubble through Mockito strict
     * stubs cleanly and let the orchestrator's catch dispatch without
     * inspecting messages. They are deliberately NOT subclasses of
     * {@link ResourceNotFoundException} or {@link NoSuchElementException} —
     * the bulk apply does not want to map these to a controller-level 404;
     * the bulk endpoint returns 200 with per-row error categories instead.
     */
    public static class NodeNotFoundForHoldException extends RuntimeException {
        public NodeNotFoundForHoldException(UUID nodeId) {
            super("Node not found for hold apply: " + nodeId);
        }
    }

    public static class NodeNotVisibleForHoldException extends RuntimeException {
        public NodeNotVisibleForHoldException(UUID nodeId) {
            super("Node not visible for hold apply: " + nodeId);
        }
    }
}
