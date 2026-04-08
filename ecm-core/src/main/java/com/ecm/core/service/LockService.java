package com.ecm.core.service;

import com.ecm.core.dto.LockInfoDto;
import com.ecm.core.entity.*;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.event.NodeLockedEvent;
import com.ecm.core.event.NodeUnlockedEvent;
import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import org.springframework.context.ApplicationEventPublisher;

/**
 * Enhanced lock service following Alfresco LockService semantics.
 * Supports lock types, recursive (deep) locking, batch operations,
 * lock suspension, and additional info metadata.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LockService {

    private final NodeRepository nodeRepository;
    private final SecurityService securityService;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;

    private static final int DEFAULT_EPHEMERAL_MINUTES = 30;

    /** Thread-local flag for lock suspension (bypass lock checks). */
    private static final ThreadLocal<Boolean> LOCKS_SUSPENDED = ThreadLocal.withInitial(() -> false);

    // ------------------------------------------------------------------ lock

    /**
     * Lock a node with default settings (WRITE_LOCK, PERSISTENT, not deep).
     */
    public void lock(UUID nodeId, LockType lockType) {
        lock(nodeId, lockType, LockLifetime.PERSISTENT, null, false, null);
    }

    /**
     * Lock a node with expiry (seconds).
     */
    public void lock(UUID nodeId, LockType lockType, int timeToExpireSeconds) {
        lock(nodeId, lockType, LockLifetime.EPHEMERAL, timeToExpireSeconds, false, null);
    }

    /**
     * Lock with lifetime control.
     */
    public void lock(UUID nodeId, LockType lockType, int timeToExpireSeconds, LockLifetime lifetime) {
        lock(nodeId, lockType, lifetime, timeToExpireSeconds, false, null);
    }

    /**
     * Lock with deep (recursive) flag.
     */
    public void lock(UUID nodeId, LockType lockType, int timeToExpireSeconds, LockLifetime lifetime, boolean lockChildren) {
        lock(nodeId, lockType, lifetime, timeToExpireSeconds, lockChildren, null);
    }

    /**
     * Full lock method with all parameters.
     */
    public void lock(UUID nodeId, LockType lockType, LockLifetime lifetime,
                     Integer timeToExpireSeconds, boolean lockChildren, String additionalInfo) {
        Node node = loadNode(nodeId);

        if (!securityService.hasPermission(node, PermissionType.WRITE)) {
            throw new SecurityException("No permission to lock node: " + node.getName());
        }

        normalizeExpiredLock(node);

        if (node.isEffectivelyLocked(LocalDateTime.now())) {
            throw new IllegalStateException("Node is already " + node.describeActiveLock(LocalDateTime.now()));
        }

        LockType effectiveType = lockType != null ? lockType : LockType.WRITE_LOCK;
        LockLifetime effectiveLifetime = lifetime != null ? lifetime : LockLifetime.PERSISTENT;

        if (timeToExpireSeconds != null && timeToExpireSeconds <= 0) {
            throw new IllegalArgumentException("Lock duration must be positive");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = effectiveLifetime == LockLifetime.EPHEMERAL
            ? now.plusSeconds(timeToExpireSeconds != null ? timeToExpireSeconds : DEFAULT_EPHEMERAL_MINUTES * 60L)
            : null;

        String currentUser = securityService.getCurrentUser();
        node.applyLock(currentUser, now, effectiveLifetime, expiresAt, effectiveType, additionalInfo, lockChildren);
        nodeRepository.save(node);
        eventPublisher.publishEvent(new NodeLockedEvent(node, currentUser));

        if (lockChildren) {
            lockChildrenRecursive(node, currentUser, now, effectiveLifetime, expiresAt, effectiveType, additionalInfo);
        }

        log.info("Locked node {} type={} lifetime={} deep={}", nodeId, effectiveType, effectiveLifetime, lockChildren);
    }

    // ------------------------------------------------------------------ batch lock

    /**
     * Lock multiple nodes in a single transaction.
     */
    public void batchLock(Collection<UUID> nodeIds, LockType lockType, int timeToExpireSeconds) {
        for (UUID nodeId : nodeIds) {
            lock(nodeId, lockType, timeToExpireSeconds);
        }
    }

    // ------------------------------------------------------------------ unlock

    /**
     * Unlock a node.
     */
    public void unlock(UUID nodeId) {
        unlock(nodeId, false, false);
    }

    /**
     * Unlock with optional recursive unlock.
     */
    public void unlock(UUID nodeId, boolean unlockChildren) {
        unlock(nodeId, unlockChildren, false);
    }

    /**
     * Full unlock with options.
     *
     * @param unlockChildren  if true, also unlock all descendant nodes
     * @param allowCheckedOut if true, allow unlock even if document is checked out
     */
    public void unlock(UUID nodeId, boolean unlockChildren, boolean allowCheckedOut) {
        Node node = loadNode(nodeId);
        normalizeExpiredLock(node);

        if (!node.isEffectivelyLocked(LocalDateTime.now())) {
            return;
        }

        String currentUser = securityService.getCurrentUser();
        boolean isOwner = Objects.equals(node.getLockedBy(), currentUser);
        boolean isAdmin = securityService.hasRole("ROLE_ADMIN");

        if (!isOwner && !isAdmin) {
            throw new SecurityException("Only lock owner or admin can unlock node");
        }

        if (!allowCheckedOut && node instanceof Document doc && doc.isCheckedOut()) {
            throw new IllegalStateException("Cannot unlock a checked-out document; cancel checkout first");
        }

        node.clearLock();
        nodeRepository.save(node);
        eventPublisher.publishEvent(new NodeUnlockedEvent(node, currentUser));

        if (unlockChildren) {
            unlockChildrenRecursive(node, currentUser);
        }

        log.info("Unlocked node {} deep={}", nodeId, unlockChildren);
    }

    /**
     * Batch unlock multiple nodes.
     */
    public void batchUnlock(Collection<UUID> nodeIds) {
        for (UUID nodeId : nodeIds) {
            unlock(nodeId);
        }
    }

    // ------------------------------------------------------------------ status / info

    /**
     * Get caller-relative lock status.
     */
    public LockStatus getLockStatus(UUID nodeId) {
        return getLockInfo(nodeId).status();
    }

    /**
     * Get full lock info for a node.
     */
    public LockInfoDto getLockInfo(UUID nodeId) {
        Node node = loadNode(nodeId);
        if (!securityService.hasPermission(node, PermissionType.READ)) {
            throw new SecurityException("No permission to read node: " + node.getName());
        }
        return buildLockInfo(node);
    }

    /**
     * Check if a node is locked (regardless of expiry).
     */
    public boolean isLocked(UUID nodeId) {
        Node node = loadNode(nodeId);
        return node.isEffectivelyLocked(LocalDateTime.now());
    }

    /**
     * Check if a node is locked and read-only (READ_ONLY_LOCK or NODE_LOCK).
     */
    public boolean isLockedAndReadOnly(UUID nodeId) {
        Node node = loadNode(nodeId);
        if (!node.isEffectivelyLocked(LocalDateTime.now())) {
            return false;
        }
        return node.getLockType() == LockType.READ_ONLY_LOCK || node.getLockType() == LockType.NODE_LOCK;
    }

    /**
     * Throws SecurityException if node is locked by another user.
     * Called before write operations.
     */
    public void checkForLock(UUID nodeId) {
        if (areLocksSuspended()) {
            return;
        }
        Node node = loadNode(nodeId);
        normalizeExpiredLock(node);
        String currentUser = securityService.getCurrentUser();
        if (!node.isWriteAllowed(currentUser, LocalDateTime.now())) {
            throw new SecurityException("Node is " + node.describeActiveLock(LocalDateTime.now()));
        }
    }

    /**
     * Get additional info string stored with the lock.
     */
    public String getAdditionalInfo(UUID nodeId) {
        Node node = loadNode(nodeId);
        return node.getLockAdditionalInfo();
    }

    // ------------------------------------------------------------------ suspension

    /**
     * Suspend lock checks for the current thread/transaction.
     * Used for system operations that must bypass locks.
     */
    public void suspendLocks() {
        LOCKS_SUSPENDED.set(true);
        log.debug("Locks suspended for current thread");
    }

    /**
     * Re-enable lock checks for the current thread.
     */
    public void enableLocks() {
        LOCKS_SUSPENDED.set(false);
        log.debug("Locks re-enabled for current thread");
    }

    /**
     * Check whether locks are currently suspended.
     */
    public boolean areLocksSuspended() {
        return Boolean.TRUE.equals(LOCKS_SUSPENDED.get());
    }

    // ------------------------------------------------------------------ internals

    private Node loadNode(UUID nodeId) {
        return nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nodeId, Node.ArchiveStatus.LIVE)
            .filter(this::isNodeVisible)
            .orElseThrow(() -> new NoSuchElementException("Node not found: " + nodeId));
    }

    private boolean isNodeVisible(Node node) {
        if (node == null) {
            return false;
        }
        if (!tenantWorkspaceScopeService.hasScopedTenantWorkspace()) {
            return true;
        }
        return tenantWorkspaceScopeService.isPathVisible(node.getPath());
    }

    private void normalizeExpiredLock(Node node) {
        if (node.isLockExpired(LocalDateTime.now())) {
            node.clearLock();
            nodeRepository.save(node);
        }
    }

    private void lockChildrenRecursive(Node parent, String user, LocalDateTime now,
                                       LockLifetime lifetime, LocalDateTime expiresAt,
                                       LockType type, String additionalInfo) {
        for (Node child : parent.getChildren()) {
            if (!child.isDeleted() && !child.isEffectivelyLocked(now)) {
                child.applyLock(user, now, lifetime, expiresAt, type, additionalInfo, true);
                nodeRepository.save(child);
                eventPublisher.publishEvent(new NodeLockedEvent(child, user));
                lockChildrenRecursive(child, user, now, lifetime, expiresAt, type, additionalInfo);
            }
        }
    }

    private void unlockChildrenRecursive(Node parent, String currentUser) {
        for (Node child : parent.getChildren()) {
            if (!child.isDeleted() && child.isEffectivelyLocked(LocalDateTime.now())) {
                if (Objects.equals(child.getLockedBy(), currentUser)
                        || securityService.hasRole("ROLE_ADMIN")) {
                    child.clearLock();
                    nodeRepository.save(child);
                    eventPublisher.publishEvent(new NodeUnlockedEvent(child, currentUser));
                    unlockChildrenRecursive(child, currentUser);
                }
            }
        }
    }

    LockInfoDto buildLockInfo(Node node) {
        LocalDateTime now = LocalDateTime.now();
        String currentUser = securityService.getCurrentUser();
        boolean isAdmin = securityService.hasRole("ROLE_ADMIN");

        if (node.isLockExpired(now)) {
            return new LockInfoDto(
                LockStatus.LOCK_EXPIRED, node.getLockedBy(), node.getLockedDate(),
                node.getLockLifetime(), node.getLockExpiresAt(), node.getLockType(),
                node.getLockAdditionalInfo(), node.isLockDeep(),
                0L, ageSeconds(node.getLockedDate(), now), false
            );
        }
        if (!node.isEffectivelyLocked(now)) {
            return new LockInfoDto(
                LockStatus.NO_LOCK, null, null, null, null, null, null, false,
                null, null, false
            );
        }

        boolean isOwner = Objects.equals(currentUser, node.getLockedBy());
        Long remainingSeconds = node.getLockExpiresAt() != null
            ? Math.max(0L, Duration.between(now, node.getLockExpiresAt()).getSeconds())
            : null;

        return new LockInfoDto(
            isOwner ? LockStatus.LOCK_OWNER : LockStatus.LOCKED_BY_OTHER,
            node.getLockedBy(), node.getLockedDate(),
            node.getLockLifetime(), node.getLockExpiresAt(), node.getLockType(),
            node.getLockAdditionalInfo(), node.isLockDeep(),
            remainingSeconds, ageSeconds(node.getLockedDate(), now),
            isOwner || isAdmin
        );
    }

    private Long ageSeconds(LocalDateTime from, LocalDateTime now) {
        if (from == null) return null;
        return Math.max(0L, Duration.between(from, now).getSeconds());
    }
}
