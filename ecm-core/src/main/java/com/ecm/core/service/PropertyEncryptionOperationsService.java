package com.ecm.core.service;

import com.ecm.core.entity.AspectDefinition;
import com.ecm.core.entity.PropertyEncryptionBackfillJob;
import com.ecm.core.entity.PropertyEncryptionBackfillJob.BackfillDefinitionCountSnapshot;
import com.ecm.core.entity.PropertyEncryptionBackfillJob.BackfillJobStatus;
import com.ecm.core.entity.PropertyDataType;
import com.ecm.core.entity.PropertyDefinition;
import com.ecm.core.entity.TypeDefinition;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.NodeRepository.PropertyBackfillCandidateRow;
import com.ecm.core.repository.PropertyEncryptionBackfillJobRepository;
import com.ecm.core.repository.PropertyDefinitionRepository;
import com.ecm.core.security.secret.SecretCryptoProperties;
import com.ecm.core.security.secret.SecretCryptoService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PropertyEncryptionOperationsService {

    private final PropertyDefinitionRepository propertyDefinitionRepository;
    private final NodeRepository nodeRepository;
    private final PropertyEncryptionBackfillJobRepository backfillJobRepository;
    private final SecretCryptoService secretCryptoService;
    private final SecretCryptoProperties secretCryptoProperties;

    private static final int DEFAULT_BACKFILL_JOB_LIMIT = 20;
    private static final int MAX_BACKFILL_JOB_LIMIT = 100;
    private static final int DEFAULT_BACKFILL_CANDIDATE_LIMIT = 100;
    private static final int MAX_BACKFILL_CANDIDATE_LIMIT = 1000;
    private static final int DEFAULT_BACKFILL_EXECUTOR_BATCH_SIZE = 100;
    private static final int MAX_BACKFILL_EXECUTOR_BATCH_SIZE = 500;
    private static final String DEFAULT_BACKFILL_ACTOR = "property-encryption-backfill";
    private static final int MAX_BACKFILL_ERROR_LENGTH = 2000;

    @Transactional(readOnly = true)
    public PropertyEncryptionStatus getStatus() {
        List<PropertyDefinition> encryptedDefinitions = propertyDefinitionRepository.findByEncryptedTrue();
        long typeDefinitionCount = encryptedDefinitions.stream()
            .filter(definition -> definition.getTypeDefinition() != null)
            .count();
        long aspectDefinitionCount = encryptedDefinitions.stream()
            .filter(definition -> definition.getAspectDefinition() != null)
            .count();
        long nodesWithEncryptedProperties = nodeRepository.countNodesWithEncryptedPropertiesAndDeletedFalse();
        long encryptedPropertyValueCount = nodeRepository.countEncryptedPropertyValuesAndDeletedFalse();

        boolean secretCryptoEnabled = secretCryptoService.isEnabled();
        String activeKeyVersion = secretCryptoProperties.getActiveKeyVersion();
        List<String> configuredKeyVersions = configuredKeyVersions();
        boolean activeKeyConfigured = activeKeyVersion != null && configuredKeyVersions.contains(activeKeyVersion);

        List<String> warnings = new ArrayList<>();
        if (!secretCryptoEnabled && !encryptedDefinitions.isEmpty()) {
            warnings.add("encrypted_property_definitions_require_secret_crypto");
        }
        if (!secretCryptoEnabled && nodesWithEncryptedProperties > 0) {
            warnings.add("encrypted_node_payloads_require_secret_crypto");
        }
        if (secretCryptoEnabled && !activeKeyConfigured) {
            warnings.add("active_secret_key_version_is_not_configured");
        }

        return new PropertyEncryptionStatus(
            secretCryptoEnabled,
            activeKeyVersion,
            activeKeyConfigured,
            configuredKeyVersions,
            encryptedDefinitions.size(),
            typeDefinitionCount,
            aspectDefinitionCount,
            nodesWithEncryptedProperties,
            encryptedPropertyValueCount,
            List.copyOf(warnings)
        );
    }

    @Transactional(readOnly = true)
    public List<EncryptedPropertyDefinitionSummary> listEncryptedDefinitions() {
        return propertyDefinitionRepository.findByEncryptedTrue().stream()
            .map(this::toDefinitionSummary)
            .sorted(Comparator.comparing(EncryptedPropertyDefinitionSummary::qualifiedName))
            .toList();
    }

    @Transactional(readOnly = true)
    public PropertyEncryptionRewrapDryRunResult dryRunRewrap(PropertyEncryptionRewrapDryRunRequest request) {
        String targetKeyVersion = resolveTargetKeyVersion(request != null ? request.targetKeyVersion() : null);
        boolean secretCryptoEnabled = secretCryptoService.isEnabled();
        List<String> configuredKeyVersions = configuredKeyVersions();
        boolean targetKeyConfigured = targetKeyVersion != null && configuredKeyVersions.contains(targetKeyVersion);

        long candidateNodeCount = nodeRepository.countNodesWithEncryptedPropertiesAndDeletedFalse();
        long encryptedPropertyValueCount = nodeRepository.countEncryptedPropertyValuesAndDeletedFalse();
        List<KeyVersionValueCount> keyVersionCounts = keyVersionCounts();
        long versionedValueCount = keyVersionCounts.stream()
            .mapToLong(KeyVersionValueCount::encryptedPropertyValueCount)
            .sum();
        long alreadyOnTargetKeyCount = keyVersionCounts.stream()
            .filter(count -> count.keyVersion().equals(targetKeyVersion))
            .mapToLong(KeyVersionValueCount::encryptedPropertyValueCount)
            .sum();
        List<String> missingSourceKeyVersions = keyVersionCounts.stream()
            .map(KeyVersionValueCount::keyVersion)
            .filter(keyVersion -> !configuredKeyVersions.contains(keyVersion))
            .sorted()
            .toList();
        long unversionedOrMalformedValueCount = Math.max(0, encryptedPropertyValueCount - versionedValueCount);
        long valuesRequiringRewrapCount = Math.max(0, encryptedPropertyValueCount - alreadyOnTargetKeyCount);

        List<String> warnings = new ArrayList<>();
        if (!secretCryptoEnabled) {
            warnings.add("secret_crypto_disabled");
        }
        if (!hasText(targetKeyVersion)) {
            warnings.add("target_key_version_required");
        } else if (!targetKeyConfigured) {
            warnings.add("target_key_version_not_configured");
        }
        if (unversionedOrMalformedValueCount > 0) {
            warnings.add("encrypted_payloads_without_key_version");
        }
        if (!missingSourceKeyVersions.isEmpty()) {
            warnings.add("source_key_versions_not_configured");
        }

        boolean executable = secretCryptoEnabled
            && targetKeyConfigured
            && missingSourceKeyVersions.isEmpty()
            && unversionedOrMalformedValueCount == 0
            && valuesRequiringRewrapCount > 0;

        return new PropertyEncryptionRewrapDryRunResult(
            targetKeyVersion,
            targetKeyConfigured,
            secretCryptoEnabled,
            candidateNodeCount,
            encryptedPropertyValueCount,
            alreadyOnTargetKeyCount,
            valuesRequiringRewrapCount,
            unversionedOrMalformedValueCount,
            keyVersionCounts,
            missingSourceKeyVersions,
            List.copyOf(warnings),
            executable
        );
    }

    @Transactional(readOnly = true)
    public PropertyEncryptionBackfillDryRunResult dryRunBackfill(PropertyEncryptionBackfillDryRunRequest request) {
        String targetKeyVersion = resolveTargetKeyVersion(request != null ? request.targetKeyVersion() : null);
        boolean secretCryptoEnabled = secretCryptoService.isEnabled();
        List<String> configuredKeyVersions = configuredKeyVersions();
        boolean targetKeyConfigured = targetKeyVersion != null && configuredKeyVersions.contains(targetKeyVersion);

        List<EncryptedPropertyDefinitionSummary> definitions = listEncryptedDefinitions();
        List<PropertyBackfillCount> definitionCounts = definitions.stream()
            .map(this::toBackfillCount)
            .toList();

        long plaintextValueCount = definitionCounts.stream()
            .mapToLong(PropertyBackfillCount::plaintextValueCount)
            .sum();
        long alreadyEncryptedValueCount = definitionCounts.stream()
            .mapToLong(PropertyBackfillCount::alreadyEncryptedValueCount)
            .sum();
        long dualStorageConflictValueCount = definitionCounts.stream()
            .mapToLong(PropertyBackfillCount::dualStorageConflictValueCount)
            .sum();
        long readyValueCount = definitionCounts.stream()
            .mapToLong(PropertyBackfillCount::readyValueCount)
            .sum();
        long totalEncryptedPropertyValueCount = nodeRepository.countEncryptedPropertyValuesAndDeletedFalse();
        long orphanEncryptedValueCount = Math.max(0, totalEncryptedPropertyValueCount - alreadyEncryptedValueCount);

        List<String> warnings = new ArrayList<>();
        if (!secretCryptoEnabled) {
            warnings.add("secret_crypto_disabled");
        }
        if (!hasText(targetKeyVersion)) {
            warnings.add("target_key_version_required");
        } else if (!targetKeyConfigured) {
            warnings.add("target_key_version_not_configured");
        }
        if (definitions.isEmpty()) {
            warnings.add("no_encrypted_property_definitions");
        }
        if (dualStorageConflictValueCount > 0) {
            warnings.add("dual_storage_conflicts_detected");
        }
        if (orphanEncryptedValueCount > 0) {
            warnings.add("orphan_encrypted_payloads_detected");
        }

        boolean executable = secretCryptoEnabled
            && targetKeyConfigured
            && !definitions.isEmpty()
            && dualStorageConflictValueCount == 0
            && readyValueCount > 0;

        return new PropertyEncryptionBackfillDryRunResult(
            targetKeyVersion,
            targetKeyConfigured,
            secretCryptoEnabled,
            definitions.size(),
            plaintextValueCount,
            alreadyEncryptedValueCount,
            dualStorageConflictValueCount,
            readyValueCount,
            orphanEncryptedValueCount,
            definitionCounts,
            List.copyOf(warnings),
            executable
        );
    }

    @Transactional
    public PropertyEncryptionBackfillJobDto planBackfillJob(
        PropertyEncryptionBackfillJobPlanRequest request,
        String requestedBy
    ) {
        PropertyEncryptionBackfillDryRunResult dryRun = dryRunBackfill(
            new PropertyEncryptionBackfillDryRunRequest(request != null ? request.targetKeyVersion() : null)
        );
        if (!dryRun.executable()) {
            throw new IllegalArgumentException("Backfill dry-run is not executable: " + String.join(",", dryRun.warnings()));
        }

        PropertyEncryptionBackfillJob job = new PropertyEncryptionBackfillJob();
        LocalDateTime now = LocalDateTime.now();
        job.setStatus(BackfillJobStatus.PLANNED);
        job.setTargetKeyVersion(dryRun.targetKeyVersion());
        job.setRequestedBy(hasText(requestedBy) ? requestedBy.trim() : "system");
        job.setRequestedAt(now);
        job.setCreatedAt(now);
        job.setEncryptedPropertyDefinitionCount(dryRun.encryptedPropertyDefinitionCount());
        job.setPlaintextValueCount(dryRun.plaintextValueCount());
        job.setAlreadyEncryptedValueCount(dryRun.alreadyEncryptedValueCount());
        job.setDualStorageConflictValueCount(dryRun.dualStorageConflictValueCount());
        job.setReadyValueCount(dryRun.readyValueCount());
        job.setOrphanEncryptedValueCount(dryRun.orphanEncryptedValueCount());
        job.setWarnings(new ArrayList<>(dryRun.warnings()));
        job.setDefinitionCounts(dryRun.definitionCounts().stream()
            .map(this::toBackfillDefinitionCountSnapshot)
            .toList());

        return toBackfillJobDto(backfillJobRepository.save(job));
    }

    @Transactional(readOnly = true)
    public List<PropertyEncryptionBackfillJobDto> listBackfillJobs(Integer limit) {
        Pageable pageable = PageRequest.of(0, clamp(limit != null ? limit : DEFAULT_BACKFILL_JOB_LIMIT, 1, MAX_BACKFILL_JOB_LIMIT));
        return backfillJobRepository.findAllByOrderByRequestedAtDesc(pageable).stream()
            .map(this::toBackfillJobDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public PropertyEncryptionBackfillJobDto getBackfillJob(UUID jobId) {
        if (jobId == null) {
            throw new IllegalArgumentException("Backfill job id is required");
        }
        return backfillJobRepository.findById(jobId)
            .map(this::toBackfillJobDto)
            .orElseThrow(() -> new ResourceNotFoundException("Backfill job not found: " + jobId));
    }

    @Transactional(readOnly = true)
    public PropertyEncryptionBackfillCandidateBatch previewBackfillCandidates(
        PropertyEncryptionBackfillCandidatePreviewRequest request
    ) {
        String qualifiedName = request != null ? request.qualifiedName() : null;
        if (!hasText(qualifiedName)) {
            throw new IllegalArgumentException("Encrypted property qualifiedName is required");
        }

        int limit = clamp(
            request != null && request.limit() != null ? request.limit() : DEFAULT_BACKFILL_CANDIDATE_LIMIT,
            1,
            MAX_BACKFILL_CANDIDATE_LIMIT
        );
        List<PropertyBackfillCandidateRow> candidates = nodeRepository
            .findBackfillCandidatesByPropertyKeyAndDeletedFalse(qualifiedName.trim(), limit);
        return new PropertyEncryptionBackfillCandidateBatch(
            qualifiedName.trim(),
            limit,
            candidates.size(),
            candidates.stream()
                .map(row -> new PropertyEncryptionBackfillCandidateRef(row.getNodeId(), row.getPropertyKey()))
                .toList()
        );
    }

    @Transactional
    public PropertyEncryptionBackfillCandidateUpdateResult applyBackfillCandidateUpdate(
        PropertyBackfillCandidateRow candidate,
        String modifiedBy
    ) {
        if (candidate == null) {
            throw new IllegalArgumentException("Backfill candidate is required");
        }
        if (candidate.getNodeId() == null) {
            throw new IllegalArgumentException("Backfill candidate nodeId is required");
        }
        if (!hasText(candidate.getPropertyKey())) {
            throw new IllegalArgumentException("Backfill candidate qualifiedName is required");
        }
        if (candidate.getPlaintextJson() == null) {
            throw new IllegalArgumentException("Backfill candidate plaintextJson is required");
        }
        if (candidate.getEntityVersion() == null) {
            throw new IllegalArgumentException("Backfill candidate entityVersion is required");
        }
        if (!secretCryptoService.isEnabled()) {
            throw new IllegalStateException("Secret crypto must be enabled before backfill candidate updates");
        }
        String protectedValue = secretCryptoService.protect(candidate.getPlaintextJson());
        if (!hasText(protectedValue) || !secretCryptoService.isEncrypted(protectedValue)) {
            throw new IllegalStateException("Backfill candidate encryption did not produce an encrypted payload");
        }

        String propertyKey = candidate.getPropertyKey().trim();
        int updated = nodeRepository.backfillEncryptedPropertyIfUnchanged(
            candidate.getNodeId(),
            propertyKey,
            candidate.getPlaintextJson(),
            candidate.getEntityVersion(),
            protectedValue,
            LocalDateTime.now(),
            hasText(modifiedBy) ? modifiedBy.trim() : DEFAULT_BACKFILL_ACTOR
        );
        if (updated > 1) {
            throw new IllegalStateException("Backfill candidate CAS update affected multiple rows");
        }
        return new PropertyEncryptionBackfillCandidateUpdateResult(
            candidate.getNodeId(),
            propertyKey,
            updated == 1
        );
    }

    public PropertyEncryptionBackfillJobDto runBackfillJob(
        UUID jobId,
        Integer batchSize,
        String requestedBy
    ) {
        if (jobId == null) {
            throw new IllegalArgumentException("Backfill job id is required");
        }
        LocalDateTime startedAt = LocalDateTime.now();
        if (backfillJobRepository.claimPlannedJob(jobId, startedAt) != 1) {
            PropertyEncryptionBackfillJob current = backfillJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Backfill job not found: " + jobId));
            throw new IllegalStateException("Backfill job must be PLANNED before execution; current status is " + current.getStatus());
        }

        PropertyEncryptionBackfillJob job = backfillJobRepository.findById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("Backfill job not found: " + jobId));
        String actor = hasText(requestedBy) ? requestedBy.trim() : DEFAULT_BACKFILL_ACTOR;
        job.setStatus(BackfillJobStatus.RUNNING);
        job.setStartedAt(startedAt);
        job.setFinishedAt(null);
        job.setLastError(null);
        job.setUpdatedAt(startedAt);

        BackfillRunCounters counters = new BackfillRunCounters(0, 0, 0, 0, null);
        BackfillJobStatus terminalStatus = BackfillJobStatus.FAILED;
        String lastError = null;
        try {
            validateBackfillExecutorPreconditions(job);

            counters = runBackfillDefinitions(
                job.getDefinitionCounts() != null ? job.getDefinitionCounts() : List.of(),
                clamp(
                    batchSize != null ? batchSize : DEFAULT_BACKFILL_EXECUTOR_BATCH_SIZE,
                    1,
                    MAX_BACKFILL_EXECUTOR_BATCH_SIZE
                ),
                actor
            );

            lastError = counters.lastError();
            if (counters.failedValueCount() > 0) {
                terminalStatus = BackfillJobStatus.FAILED;
            } else {
                BackfillRemainingCounts remaining = remainingBackfillCounts(job.getDefinitionCounts());
                if (remaining.dualStorageConflictValueCount() > 0) {
                    terminalStatus = BackfillJobStatus.FAILED;
                    lastError = "Backfill job ended with dual-storage conflicts";
                } else if (remaining.readyValueCount() > 0) {
                    terminalStatus = BackfillJobStatus.FAILED;
                    lastError = "Backfill job ended with remaining ready values";
                } else {
                    terminalStatus = BackfillJobStatus.SUCCEEDED;
                }
            }
        } catch (Exception ex) {
            terminalStatus = BackfillJobStatus.FAILED;
            lastError = toBackfillError(ex);
        }

        LocalDateTime finishedAt = LocalDateTime.now();
        applyTerminalState(job, terminalStatus, counters, finishedAt, lastError);
        if (backfillJobRepository.markTerminalIfRunning(
            job.getId(),
            terminalStatus,
            finishedAt,
            job.getProcessedValueCount(),
            job.getMigratedValueCount(),
            job.getSkippedValueCount(),
            job.getFailedValueCount(),
            lastError
        ) != 1) {
            throw new IllegalStateException("Backfill job was no longer RUNNING during terminal update");
        }
        return toBackfillJobDto(job);
    }

    private EncryptedPropertyDefinitionSummary toDefinitionSummary(PropertyDefinition definition) {
        TypeDefinition typeDefinition = definition.getTypeDefinition();
        AspectDefinition aspectDefinition = definition.getAspectDefinition();

        String ownerKind = "UNASSIGNED";
        String ownerQName = null;
        if (typeDefinition != null) {
            ownerKind = "TYPE";
            ownerQName = typeDefinition.qualifiedName();
        } else if (aspectDefinition != null) {
            ownerKind = "ASPECT";
            ownerQName = aspectDefinition.qualifiedName();
        }

        return new EncryptedPropertyDefinitionSummary(
            definition.getId(),
            definition.qualifiedName(),
            definition.getName(),
            definition.getTitle(),
            ownerKind,
            ownerQName,
            definition.getDataType(),
            definition.isMandatory(),
            definition.isMultiValued(),
            definition.isIndexed()
        );
    }

    private List<String> configuredKeyVersions() {
        Map<String, String> keys = secretCryptoProperties.getKeys();
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return keys.keySet().stream().sorted().toList();
    }

    private String resolveTargetKeyVersion(String requestedTargetKeyVersion) {
        return hasText(requestedTargetKeyVersion)
            ? requestedTargetKeyVersion.trim()
            : secretCryptoProperties.getActiveKeyVersion();
    }

    private List<KeyVersionValueCount> keyVersionCounts() {
        return nodeRepository.countEncryptedPropertyValuesByKeyVersionAndDeletedFalse().stream()
            .map(this::toKeyVersionValueCount)
            .sorted(Comparator.comparing(KeyVersionValueCount::keyVersion))
            .toList();
    }

    private KeyVersionValueCount toKeyVersionValueCount(Object[] row) {
        String keyVersion = row[0] != null ? row[0].toString() : "";
        return new KeyVersionValueCount(keyVersion, toLong(row[1]));
    }

    private PropertyBackfillCount toBackfillCount(EncryptedPropertyDefinitionSummary definition) {
        String qualifiedName = definition.qualifiedName();
        long plaintextValueCount = nodeRepository.countByPropertyKeyAndDeletedFalse(qualifiedName);
        long alreadyEncryptedValueCount = nodeRepository.countByEncryptedPropertyKeyAndDeletedFalse(qualifiedName);
        long dualStorageConflictValueCount = nodeRepository.countByPropertyKeyInBothStorageAndDeletedFalse(qualifiedName);
        long readyValueCount = nodeRepository.countBackfillReadyByPropertyKeyAndDeletedFalse(qualifiedName);
        return new PropertyBackfillCount(
            qualifiedName,
            definition.ownerKind(),
            definition.ownerQName(),
            plaintextValueCount,
            alreadyEncryptedValueCount,
            dualStorageConflictValueCount,
            readyValueCount
        );
    }

    private BackfillDefinitionCountSnapshot toBackfillDefinitionCountSnapshot(PropertyBackfillCount count) {
        return new BackfillDefinitionCountSnapshot(
            count.qualifiedName(),
            count.ownerKind(),
            count.ownerQName(),
            count.plaintextValueCount(),
            count.alreadyEncryptedValueCount(),
            count.dualStorageConflictValueCount(),
            count.readyValueCount()
        );
    }

    private void validateBackfillExecutorPreconditions(PropertyEncryptionBackfillJob job) {
        if (!secretCryptoService.isEnabled()) {
            throw new IllegalStateException("Secret crypto must be enabled before backfill job execution");
        }
        String activeKeyVersion = secretCryptoProperties.getActiveKeyVersion();
        if (!hasText(job.getTargetKeyVersion())) {
            throw new IllegalStateException("Backfill job target key version is required");
        }
        if (!job.getTargetKeyVersion().equals(activeKeyVersion)) {
            throw new IllegalStateException("Backfill job target key version must match the active key version");
        }
        if (!configuredKeyVersions().contains(activeKeyVersion)) {
            throw new IllegalStateException("Active secret key version is not configured");
        }
        BackfillRemainingCounts remaining = remainingBackfillCounts(job.getDefinitionCounts());
        if (remaining.dualStorageConflictValueCount() > 0) {
            throw new IllegalStateException("Backfill job cannot execute while dual-storage conflicts exist");
        }
    }

    private BackfillRunCounters runBackfillDefinitions(
        List<BackfillDefinitionCountSnapshot> definitions,
        int batchSize,
        String actor
    ) {
        long processed = 0;
        long migrated = 0;
        long skipped = 0;
        long failed = 0;
        String lastError = null;
        Set<String> attemptedCandidates = new HashSet<>();

        for (BackfillDefinitionCountSnapshot definition : definitions) {
            if (definition == null || !hasText(definition.qualifiedName())) {
                continue;
            }
            long remainingForDefinition = Math.max(0, definition.readyValueCount());
            while (remainingForDefinition > 0) {
                int limit = (int) Math.min(batchSize, remainingForDefinition);
                List<PropertyBackfillCandidateRow> candidates = nodeRepository
                    .findBackfillCandidatesByPropertyKeyAndDeletedFalse(definition.qualifiedName(), limit);
                if (candidates.isEmpty()) {
                    break;
                }
                boolean attemptedAnyCandidate = false;
                for (PropertyBackfillCandidateRow candidate : candidates) {
                    if (!attemptedCandidates.add(candidateKey(candidate))) {
                        continue;
                    }
                    attemptedAnyCandidate = true;
                    processed++;
                    remainingForDefinition--;
                    try {
                        PropertyEncryptionBackfillCandidateUpdateResult result =
                            applyBackfillCandidateUpdate(candidate, actor);
                        if (result.migrated()) {
                            migrated++;
                        } else {
                            skipped++;
                        }
                    } catch (Exception ex) {
                        failed++;
                        lastError = toBackfillError(ex);
                        return new BackfillRunCounters(processed, migrated, skipped, failed, lastError);
                    }
                    if (remainingForDefinition <= 0) {
                        break;
                    }
                }
                if (!attemptedAnyCandidate) {
                    break;
                }
            }
        }

        return new BackfillRunCounters(processed, migrated, skipped, failed, lastError);
    }

    private String candidateKey(PropertyBackfillCandidateRow candidate) {
        return candidate.getNodeId() + ":" + candidate.getPropertyKey();
    }

    private BackfillRemainingCounts remainingBackfillCounts(List<BackfillDefinitionCountSnapshot> definitions) {
        long ready = 0;
        long conflicts = 0;
        if (definitions == null) {
            return new BackfillRemainingCounts(0, 0);
        }
        for (BackfillDefinitionCountSnapshot definition : definitions) {
            if (definition == null || !hasText(definition.qualifiedName())) {
                continue;
            }
            ready += nodeRepository.countBackfillReadyByPropertyKeyAndDeletedFalse(definition.qualifiedName());
            conflicts += nodeRepository.countByPropertyKeyInBothStorageAndDeletedFalse(definition.qualifiedName());
        }
        return new BackfillRemainingCounts(ready, conflicts);
    }

    private void applyTerminalState(
        PropertyEncryptionBackfillJob job,
        BackfillJobStatus terminalStatus,
        BackfillRunCounters counters,
        LocalDateTime finishedAt,
        String lastError
    ) {
        job.setStatus(terminalStatus);
        job.setFinishedAt(finishedAt);
        job.setUpdatedAt(finishedAt);
        job.setProcessedValueCount(job.getProcessedValueCount() + counters.processedValueCount());
        job.setMigratedValueCount(job.getMigratedValueCount() + counters.migratedValueCount());
        job.setSkippedValueCount(job.getSkippedValueCount() + counters.skippedValueCount());
        job.setFailedValueCount(job.getFailedValueCount() + counters.failedValueCount());
        job.setLastError(lastError);
    }

    private PropertyEncryptionBackfillJobDto toBackfillJobDto(PropertyEncryptionBackfillJob job) {
        return new PropertyEncryptionBackfillJobDto(
            job.getId(),
            job.getStatus(),
            job.getTargetKeyVersion(),
            job.getRequestedBy(),
            job.getRequestedAt(),
            job.getStartedAt(),
            job.getFinishedAt(),
            job.getEncryptedPropertyDefinitionCount(),
            job.getPlaintextValueCount(),
            job.getAlreadyEncryptedValueCount(),
            job.getDualStorageConflictValueCount(),
            job.getReadyValueCount(),
            job.getOrphanEncryptedValueCount(),
            job.getProcessedValueCount(),
            job.getMigratedValueCount(),
            job.getSkippedValueCount(),
            job.getFailedValueCount(),
            List.copyOf(job.getWarnings() != null ? job.getWarnings() : List.of()),
            List.copyOf(job.getDefinitionCounts() != null ? job.getDefinitionCounts() : List.of()),
            job.getLastError(),
            job.getCreatedAt(),
            job.getUpdatedAt()
        );
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(value.toString());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String toBackfillError(Exception ex) {
        String message = ex.getMessage();
        if (!hasText(message)) {
            message = ex.getClass().getSimpleName();
        }
        return message.length() <= MAX_BACKFILL_ERROR_LENGTH
            ? message
            : message.substring(0, MAX_BACKFILL_ERROR_LENGTH);
    }

    public record PropertyEncryptionStatus(
        boolean secretCryptoEnabled,
        String activeKeyVersion,
        boolean activeKeyConfigured,
        List<String> configuredKeyVersions,
        long encryptedPropertyDefinitionCount,
        long encryptedTypePropertyDefinitionCount,
        long encryptedAspectPropertyDefinitionCount,
        long nodesWithEncryptedPropertiesCount,
        long encryptedPropertyValueCount,
        List<String> warnings
    ) {
    }

    public record EncryptedPropertyDefinitionSummary(
        UUID id,
        String qualifiedName,
        String name,
        String title,
        String ownerKind,
        String ownerQName,
        PropertyDataType dataType,
        boolean mandatory,
        boolean multiValued,
        boolean indexed
    ) {
    }

    public record PropertyEncryptionRewrapDryRunRequest(
        String targetKeyVersion
    ) {
    }

    public record PropertyEncryptionBackfillDryRunRequest(
        String targetKeyVersion
    ) {
    }

    public record PropertyEncryptionBackfillJobPlanRequest(
        String targetKeyVersion
    ) {
    }

    public record PropertyEncryptionBackfillCandidatePreviewRequest(
        String qualifiedName,
        Integer limit
    ) {
    }

    public record PropertyEncryptionRewrapDryRunResult(
        String targetKeyVersion,
        boolean targetKeyConfigured,
        boolean secretCryptoEnabled,
        long candidateNodeCount,
        long encryptedPropertyValueCount,
        long valuesAlreadyOnTargetKeyCount,
        long valuesRequiringRewrapCount,
        long unversionedOrMalformedValueCount,
        List<KeyVersionValueCount> keyVersionCounts,
        List<String> missingSourceKeyVersions,
        List<String> warnings,
        boolean executable
    ) {
    }

    public record KeyVersionValueCount(
        String keyVersion,
        long encryptedPropertyValueCount
    ) {
    }

    public record PropertyEncryptionBackfillDryRunResult(
        String targetKeyVersion,
        boolean targetKeyConfigured,
        boolean secretCryptoEnabled,
        long encryptedPropertyDefinitionCount,
        long plaintextValueCount,
        long alreadyEncryptedValueCount,
        long dualStorageConflictValueCount,
        long readyValueCount,
        long orphanEncryptedValueCount,
        List<PropertyBackfillCount> definitionCounts,
        List<String> warnings,
        boolean executable
    ) {
    }

    public record PropertyBackfillCount(
        String qualifiedName,
        String ownerKind,
        String ownerQName,
        long plaintextValueCount,
        long alreadyEncryptedValueCount,
        long dualStorageConflictValueCount,
        long readyValueCount
    ) {
    }

    public record PropertyEncryptionBackfillJobDto(
        UUID id,
        BackfillJobStatus status,
        String targetKeyVersion,
        String requestedBy,
        LocalDateTime requestedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        long encryptedPropertyDefinitionCount,
        long plaintextValueCount,
        long alreadyEncryptedValueCount,
        long dualStorageConflictValueCount,
        long readyValueCount,
        long orphanEncryptedValueCount,
        long processedValueCount,
        long migratedValueCount,
        long skippedValueCount,
        long failedValueCount,
        List<String> warnings,
        List<BackfillDefinitionCountSnapshot> definitionCounts,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
    }

    public record PropertyEncryptionBackfillCandidateBatch(
        String qualifiedName,
        int limit,
        int candidateCount,
        List<PropertyEncryptionBackfillCandidateRef> candidates
    ) {
    }

    public record PropertyEncryptionBackfillCandidateRef(
        UUID nodeId,
        String qualifiedName
    ) {
    }

    public record PropertyEncryptionBackfillCandidateUpdateResult(
        UUID nodeId,
        String qualifiedName,
        boolean migrated
    ) {
    }

    private record BackfillRunCounters(
        long processedValueCount,
        long migratedValueCount,
        long skippedValueCount,
        long failedValueCount,
        String lastError
    ) {
    }

    private record BackfillRemainingCounts(
        long readyValueCount,
        long dualStorageConflictValueCount
    ) {
    }
}
