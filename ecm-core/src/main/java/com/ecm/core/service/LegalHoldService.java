package com.ecm.core.service;

import com.ecm.core.entity.LegalHold;
import com.ecm.core.entity.LegalHoldItem;
import com.ecm.core.entity.Node;
import com.ecm.core.exception.IllegalOperationException;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.repository.LegalHoldItemRepository;
import com.ecm.core.repository.LegalHoldRepository;
import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
@RequiredArgsConstructor
@Transactional
public class LegalHoldService {

    private final LegalHoldRepository legalHoldRepository;
    private final LegalHoldItemRepository legalHoldItemRepository;
    private final NodeRepository nodeRepository;
    private final SecurityService securityService;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;

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
            return toDto(hold, items);
        }
        throw new ResourceNotFoundException("Legal hold not found: " + holdId);
    }

    public LegalHoldDto createHold(CreateLegalHoldRequest request) {
        requireAdmin();
        if (request == null || !StringUtils.hasText(request.name())) {
            throw new IllegalArgumentException("Legal hold name is required");
        }

        LegalHold hold = new LegalHold();
        hold.setName(request.name().trim());
        hold.setDescription(trimToNull(request.description()));
        LegalHold saved = legalHoldRepository.save(hold);
        log.info("Created legal hold {} ({})", saved.getName(), saved.getId());
        return toDto(saved, List.of());
    }

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

    public LegalHoldDto removeItem(UUID holdId, UUID nodeId) {
        requireAdmin();
        requireActiveHold(holdId);
        LegalHoldItem item = legalHoldItemRepository.findByHoldIdAndNodeId(holdId, nodeId)
            .orElseThrow(() -> new NoSuchElementException("Legal hold item not found for node: " + nodeId));
        legalHoldItemRepository.delete(item);
        return getHold(holdId);
    }

    public LegalHoldDto releaseHold(UUID holdId, ReleaseLegalHoldRequest request) {
        requireAdmin();
        LegalHold hold = requireActiveHold(holdId);
        hold.setStatus(LegalHold.HoldStatus.RELEASED);
        hold.setReleasedAt(LocalDateTime.now());
        hold.setReleasedBy(securityService.getCurrentUser());
        hold.setReleaseComment(request != null ? trimToNull(request.comment()) : null);
        legalHoldRepository.save(hold);
        log.info("Released legal hold {} ({})", hold.getName(), hold.getId());
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
            hold.getReleasedAt()
        );
    }

    private LegalHoldDto toDto(LegalHold hold, List<LegalHoldItemDto> items) {
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
            items.size(),
            items
        );
    }

    public record CreateLegalHoldRequest(String name, String description) {
    }

    public record AddHoldItemsRequest(List<UUID> nodeIds) {
    }

    public record ReleaseLegalHoldRequest(String comment) {
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
        LocalDateTime releasedAt
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
        int itemCount,
        List<LegalHoldItemDto> items
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
}
