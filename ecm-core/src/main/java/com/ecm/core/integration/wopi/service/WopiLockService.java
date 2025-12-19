package com.ecm.core.integration.wopi.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory lock store for WOPI.
 *
 * For production and multi-instance deployments, back this with Redis.
 */
@Service
public class WopiLockService {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private final Map<UUID, LockInfo> locks = new ConcurrentHashMap<>();

    public String getLock(UUID fileId) {
        LockInfo info = locks.get(fileId);
        if (info == null) {
            return null;
        }
        if (info.expiresAt().isBefore(Instant.now())) {
            locks.remove(fileId, info);
            return null;
        }
        return info.lock();
    }

    public LockResult lock(UUID fileId, String lock) {
        return lock(fileId, lock, null);
    }

    public LockResult lock(UUID fileId, String lock, String expectedCurrent) {
        if (lock == null || lock.isBlank()) {
            return LockResult.conflict(getLock(fileId));
        }

        LockInfo existing = locks.get(fileId);
        String current = existing != null ? existing.lock() : null;
        if (current != null && existing.expiresAt().isBefore(Instant.now())) {
            locks.remove(fileId, existing);
            current = null;
        }

        if (expectedCurrent != null && current != null && !current.equals(expectedCurrent)) {
            return LockResult.conflict(current);
        }

        if (current == null || current.equals(lock)) {
            locks.put(fileId, new LockInfo(lock, Instant.now().plus(DEFAULT_TTL)));
            return LockResult.ok(lock);
        }

        return LockResult.conflict(current);
    }

    public LockResult refresh(UUID fileId, String lock) {
        String current = getLock(fileId);
        if (current == null) {
            return LockResult.conflict(null);
        }
        if (!current.equals(lock)) {
            return LockResult.conflict(current);
        }
        locks.put(fileId, new LockInfo(lock, Instant.now().plus(DEFAULT_TTL)));
        return LockResult.ok(lock);
    }

    public LockResult unlock(UUID fileId, String lock) {
        String current = getLock(fileId);
        if (current == null) {
            return LockResult.ok(null);
        }
        if (lock == null || !current.equals(lock)) {
            return LockResult.conflict(current);
        }
        locks.remove(fileId);
        return LockResult.ok(null);
    }

    private record LockInfo(String lock, Instant expiresAt) {}

    public record LockResult(boolean ok, String currentLock) {
        public static LockResult ok(String currentLock) {
            return new LockResult(true, currentLock);
        }

        public static LockResult conflict(String currentLock) {
            return new LockResult(false, currentLock);
        }
    }
}

