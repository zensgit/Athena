package com.ecm.core.service;

import com.ecm.core.preview.PreviewFailurePolicyRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class OpsPolicyService {

    private static final String DOMAIN_PREVIEW = "PREVIEW";

    private final PreviewFailurePolicyRegistry previewFailurePolicyRegistry;
    private final AtomicLong versionSequence = new AtomicLong(0);
    private final Map<String, List<DomainPolicySnapshot>> historyByDomain = new ConcurrentHashMap<>();

    @PostConstruct
    void initialize() {
        ensureInitialized(DOMAIN_PREVIEW);
    }

    public DomainPolicyState getState(String domain) {
        String normalizedDomain = normalizeDomain(domain);
        ensureInitialized(normalizedDomain);
        DomainPolicySnapshot latest = latestSnapshot(normalizedDomain);
        return new DomainPolicyState(
            normalizedDomain,
            latest.version(),
            latest.createdAt(),
            latest.actor(),
            latest.reason(),
            latest.policies()
        );
    }

    public DomainPolicyUpdateResult updatePolicy(
        String domain,
        String profileKey,
        PreviewFailurePolicyRegistry.PreviewFailurePolicyUpdate update,
        String actor,
        String reason
    ) {
        String normalizedDomain = normalizeDomain(domain);
        ensureInitialized(normalizedDomain);
        if (!DOMAIN_PREVIEW.equals(normalizedDomain)) {
            throw new IllegalArgumentException("Unsupported policy domain: " + normalizedDomain);
        }

        PreviewFailurePolicyRegistry.PreviewFailurePolicy updated = previewFailurePolicyRegistry.upsert(profileKey, update);
        DomainPolicySnapshot latest = appendSnapshot(
            normalizedDomain,
            actor,
            reason != null && !reason.isBlank() ? reason.trim() : "policy_update:" + updated.key()
        );
        return new DomainPolicyUpdateResult(
            normalizedDomain,
            latest.version(),
            latest.createdAt(),
            latest.actor(),
            latest.reason(),
            updated,
            latest.policies()
        );
    }

    public DomainPolicyRollbackResult rollback(String domain, Long targetVersion, String actor, String reason) {
        String normalizedDomain = normalizeDomain(domain);
        ensureInitialized(normalizedDomain);
        if (!DOMAIN_PREVIEW.equals(normalizedDomain)) {
            throw new IllegalArgumentException("Unsupported policy domain: " + normalizedDomain);
        }

        List<DomainPolicySnapshot> snapshots = historyByDomain.get(normalizedDomain);
        if (snapshots == null || snapshots.isEmpty()) {
            throw new IllegalArgumentException("No policy history found for domain: " + normalizedDomain);
        }
        DomainPolicySnapshot current = snapshots.get(snapshots.size() - 1);
        DomainPolicySnapshot target = resolveRollbackTarget(snapshots, targetVersion);
        if (target == null) {
            throw new IllegalArgumentException("Rollback target version not found: " + targetVersion);
        }
        previewFailurePolicyRegistry.replaceAll(target.policies());
        String rollbackReason = reason != null && !reason.isBlank()
            ? reason.trim()
            : String.format(Locale.ROOT, "rollback:%d->%d", current.version(), target.version());
        DomainPolicySnapshot afterRollback = appendSnapshot(normalizedDomain, actor, rollbackReason);
        return new DomainPolicyRollbackResult(
            normalizedDomain,
            current.version(),
            target.version(),
            afterRollback.version(),
            afterRollback.createdAt(),
            afterRollback.actor(),
            afterRollback.reason(),
            afterRollback.policies()
        );
    }

    public List<DomainPolicyHistoryEntry> listHistory(String domain, Integer limit) {
        String normalizedDomain = normalizeDomain(domain);
        ensureInitialized(normalizedDomain);
        int maxEntries = clamp(limit != null ? limit : 20, 1, 200);
        List<DomainPolicySnapshot> snapshots = historyByDomain.getOrDefault(normalizedDomain, List.of());
        return snapshots.stream()
            .sorted(Comparator.comparingLong(DomainPolicySnapshot::version).reversed())
            .limit(maxEntries)
            .map(snapshot -> new DomainPolicyHistoryEntry(
                snapshot.version(),
                snapshot.createdAt(),
                snapshot.actor(),
                snapshot.reason()
            ))
            .toList();
    }

    private synchronized void ensureInitialized(String normalizedDomain) {
        historyByDomain.computeIfAbsent(normalizedDomain, ignored -> {
            long version = versionSequence.incrementAndGet();
            DomainPolicySnapshot snapshot = new DomainPolicySnapshot(
                version,
                Instant.now(),
                "system",
                "bootstrap",
                capturePolicies(normalizedDomain)
            );
            List<DomainPolicySnapshot> history = new ArrayList<>();
            history.add(snapshot);
            return history;
        });
    }

    private synchronized DomainPolicySnapshot appendSnapshot(String normalizedDomain, String actor, String reason) {
        long version = versionSequence.incrementAndGet();
        DomainPolicySnapshot snapshot = new DomainPolicySnapshot(
            version,
            Instant.now(),
            actor != null && !actor.isBlank() ? actor : "system",
            reason != null && !reason.isBlank() ? reason : "update",
            capturePolicies(normalizedDomain)
        );
        historyByDomain.computeIfAbsent(normalizedDomain, ignored -> new ArrayList<>()).add(snapshot);
        return snapshot;
    }

    private DomainPolicySnapshot latestSnapshot(String normalizedDomain) {
        List<DomainPolicySnapshot> snapshots = historyByDomain.get(normalizedDomain);
        if (snapshots == null || snapshots.isEmpty()) {
            return new DomainPolicySnapshot(0, Instant.now(), "system", "empty", List.of());
        }
        return snapshots.get(snapshots.size() - 1);
    }

    private List<PreviewFailurePolicyRegistry.PreviewFailurePolicy> capturePolicies(String normalizedDomain) {
        if (DOMAIN_PREVIEW.equals(normalizedDomain)) {
            return previewFailurePolicyRegistry.listPolicies();
        }
        return List.of();
    }

    private DomainPolicySnapshot resolveRollbackTarget(List<DomainPolicySnapshot> snapshots, Long targetVersion) {
        if (snapshots == null || snapshots.isEmpty()) {
            return null;
        }
        if (targetVersion != null) {
            return snapshots.stream()
                .filter(snapshot -> snapshot.version() == targetVersion)
                .findFirst()
                .orElse(null);
        }
        if (snapshots.size() < 2) {
            return null;
        }
        return snapshots.get(snapshots.size() - 2);
    }

    private static String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return DOMAIN_PREVIEW;
        }
        return domain.trim().toUpperCase(Locale.ROOT);
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    public record DomainPolicySnapshot(
        long version,
        Instant createdAt,
        String actor,
        String reason,
        List<PreviewFailurePolicyRegistry.PreviewFailurePolicy> policies
    ) {}

    public record DomainPolicyState(
        String domain,
        long currentVersion,
        Instant updatedAt,
        String actor,
        String reason,
        List<PreviewFailurePolicyRegistry.PreviewFailurePolicy> policies
    ) {}

    public record DomainPolicyUpdateResult(
        String domain,
        long currentVersion,
        Instant updatedAt,
        String actor,
        String reason,
        PreviewFailurePolicyRegistry.PreviewFailurePolicy updatedPolicy,
        List<PreviewFailurePolicyRegistry.PreviewFailurePolicy> policies
    ) {}

    public record DomainPolicyRollbackResult(
        String domain,
        long previousVersion,
        long rolledBackToVersion,
        long currentVersion,
        Instant updatedAt,
        String actor,
        String reason,
        List<PreviewFailurePolicyRegistry.PreviewFailurePolicy> policies
    ) {}

    public record DomainPolicyHistoryEntry(
        long version,
        Instant updatedAt,
        String actor,
        String reason
    ) {}
}
