package com.ecm.core.asynctask;

import com.ecm.core.entity.AsyncTaskAcknowledgement;
import com.ecm.core.repository.AsyncTaskAcknowledgementRepository;
import com.ecm.core.service.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AsyncTaskAcknowledgementService {

    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter FINGERPRINT_TIME_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final AsyncTaskAcknowledgementRepository asyncTaskAcknowledgementRepository;
    private final SecurityService securityService;

    public String fingerprint(AsyncTaskStatusSnapshot task) {
        Objects.requireNonNull(task, "task must not be null");
        String domainKey = safeToken(task.domainKey());
        String taskId = safeToken(task.taskId());
        String status = safeToken(task.status()).toUpperCase(Locale.ROOT);
        String sortTimestamp = formatInstant(task.sortTimestamp());
        return String.join("|", domainKey, taskId, status, sortTimestamp);
    }

    @Transactional(readOnly = true)
    public Map<String, AsyncTaskAcknowledgement> findAcknowledgements(Collection<String> fingerprints) {
        if (fingerprints == null || fingerprints.isEmpty()) {
            return Map.of();
        }
        String userId = securityService.getCurrentUser();
        if (userId == null || userId.isBlank()) {
            return Map.of();
        }

        Set<String> uniqueFingerprints = fingerprints.stream()
            .filter(value -> value != null && !value.isBlank())
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (uniqueFingerprints.isEmpty()) {
            return Map.of();
        }

        return asyncTaskAcknowledgementRepository.findByUserIdAndTaskFingerprintIn(userId, uniqueFingerprints).stream()
            .collect(Collectors.toMap(
                AsyncTaskAcknowledgement::getTaskFingerprint,
                acknowledgement -> acknowledgement,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    @Transactional
    public AsyncTaskAcknowledgement acknowledge(AsyncTaskStatusSnapshot task) {
        String userId = requireCurrentUser();
        String fingerprint = fingerprint(task);
        return asyncTaskAcknowledgementRepository.findByUserIdAndTaskFingerprint(userId, fingerprint)
            .orElseGet(() -> asyncTaskAcknowledgementRepository.save(
                AsyncTaskAcknowledgement.builder()
                    .userId(userId)
                    .domainKey(task.domainKey())
                    .taskId(task.taskId())
                    .taskStatus(task.status())
                    .taskFingerprint(fingerprint)
                    .taskTimestamp(toLocalDateTime(task.sortTimestamp()))
                    .acknowledgedAt(LocalDateTime.now())
                    .build()
            ));
    }

    @Transactional
    public boolean unacknowledge(String fingerprint) {
        String userId = requireCurrentUser();
        boolean existed = asyncTaskAcknowledgementRepository.findByUserIdAndTaskFingerprint(userId, fingerprint).isPresent();
        if (existed) {
            asyncTaskAcknowledgementRepository.deleteByUserIdAndTaskFingerprint(userId, fingerprint);
        }
        return existed;
    }

    public AsyncTaskStatusSnapshot applyAcknowledgement(
        AsyncTaskStatusSnapshot task,
        AsyncTaskAcknowledgement acknowledgement
    ) {
        String fingerprint = fingerprint(task);
        boolean acknowledged = acknowledgement != null;
        Instant acknowledgedAt = acknowledgement != null && acknowledgement.getAcknowledgedAt() != null
            ? acknowledgement.getAcknowledgedAt().atZone(SYSTEM_ZONE).toInstant()
            : null;
        return task.withAcknowledgement(fingerprint, acknowledged, acknowledgedAt);
    }

    public List<AsyncTaskStatusSnapshot> applyAcknowledgements(
        List<AsyncTaskStatusSnapshot> tasks,
        boolean includeAcknowledged
    ) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }

        Map<String, AsyncTaskAcknowledgement> acknowledgements = findAcknowledgements(
            tasks.stream().map(this::fingerprint).toList()
        );

        return tasks.stream()
            .map(task -> applyAcknowledgement(task, acknowledgements.get(fingerprint(task))))
            .filter(task -> includeAcknowledged || !task.acknowledged())
            .toList();
    }

    private String requireCurrentUser() {
        String userId = securityService.getCurrentUser();
        if (userId == null || userId.isBlank() || "anonymous".equalsIgnoreCase(userId)) {
            throw new IllegalStateException("Authenticated user required for async task acknowledgement");
        }
        return userId;
    }

    private String safeToken(String value) {
        if (value == null || value.isBlank()) {
            return "_";
        }
        return value.trim();
    }

    private String formatInstant(Instant value) {
        return value != null ? FINGERPRINT_TIME_FORMATTER.format(value) : "_";
    }

    private LocalDateTime toLocalDateTime(Instant value) {
        return value != null ? LocalDateTime.ofInstant(value, SYSTEM_ZONE) : null;
    }
}
