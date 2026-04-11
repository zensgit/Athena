package com.ecm.core.service;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.ReplicationDefinition;
import com.ecm.core.entity.ReplicationJob;
import com.ecm.core.entity.ReplicationJob.ReplicationJobStatus;
import com.ecm.core.entity.TransferTarget;
import com.ecm.core.service.transfer.TransferClient;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.ReplicationDefinitionRepository;
import com.ecm.core.repository.ReplicationJobRepository;
import com.ecm.core.repository.TransferTargetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TransferReplicationService {

    private final TransferTargetRepository transferTargetRepository;
    private final ReplicationDefinitionRepository replicationDefinitionRepository;
    private final ReplicationJobRepository replicationJobRepository;
    private final FolderService folderService;
    private final NodeService nodeService;
    private final NodeRepository nodeRepository;
    private final SecurityService securityService;
    private final Executor executor;
    private final Map<TransferTarget.TransportType, TransferClient> transferClients;

    public TransferReplicationService(
        TransferTargetRepository transferTargetRepository,
        ReplicationDefinitionRepository replicationDefinitionRepository,
        ReplicationJobRepository replicationJobRepository,
        FolderService folderService,
        NodeService nodeService,
        NodeRepository nodeRepository,
        SecurityService securityService,
        List<TransferClient> transferClients
    ) {
        this(
            transferTargetRepository,
            replicationDefinitionRepository,
            replicationJobRepository,
            folderService,
            nodeService,
            nodeRepository,
            securityService,
            transferClients,
            Executors.newCachedThreadPool()
        );
    }

    TransferReplicationService(
        TransferTargetRepository transferTargetRepository,
        ReplicationDefinitionRepository replicationDefinitionRepository,
        ReplicationJobRepository replicationJobRepository,
        FolderService folderService,
        NodeService nodeService,
        NodeRepository nodeRepository,
        SecurityService securityService,
        List<TransferClient> transferClients,
        Executor executor
    ) {
        this.transferTargetRepository = transferTargetRepository;
        this.replicationDefinitionRepository = replicationDefinitionRepository;
        this.replicationJobRepository = replicationJobRepository;
        this.folderService = folderService;
        this.nodeService = nodeService;
        this.nodeRepository = nodeRepository;
        this.securityService = securityService;
        this.executor = executor;
        this.transferClients = transferClients.stream()
            .collect(Collectors.toMap(TransferClient::transportType, Function.identity()));
    }

    public java.util.List<TransferTargetDto> listTargets() {
        requireAdmin();
        return transferTargetRepository.findAll().stream()
            .map(this::toDto)
            .toList();
    }

    public TransferTargetDto getTarget(UUID targetId) {
        requireAdmin();
        return toDto(requireTarget(targetId));
    }

    public TransferTargetDto createTarget(TransferTargetMutationRequest request) {
        requireAdmin();
        String name = normalizeRequired(request.name(), "Target name is required");
        if (transferTargetRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Transfer target already exists: " + name);
        }
        TransferTarget target = new TransferTarget();
        target.setName(name);
        target.setDescription(normalizeOptional(request.description()));
        applyTargetConfiguration(target, request, true);
        return toDto(transferTargetRepository.save(target));
    }

    public TransferTargetDto updateTarget(UUID targetId, TransferTargetMutationRequest request) {
        requireAdmin();
        TransferTarget target = requireTarget(targetId);
        String nextName = normalizeRequired(request.name(), "Target name is required");
        boolean renamed = !target.getName().equalsIgnoreCase(nextName);
        if (renamed && transferTargetRepository.existsByNameIgnoreCase(nextName)) {
            throw new IllegalArgumentException("Transfer target already exists: " + nextName);
        }
        target.setName(nextName);
        target.setDescription(normalizeOptional(request.description()));
        applyTargetConfiguration(target, request, false);
        return toDto(transferTargetRepository.save(target));
    }

    public TransferTargetDto verifyTarget(UUID targetId) {
        requireAdmin();
        TransferTarget target = requireTarget(targetId);
        try {
            TransferClient.TransferVerificationResult verification = clientFor(target).verifyTarget(target);
            target.setVerificationStatus(TransferTarget.VerificationStatus.VERIFIED);
            target.setVerificationMessage(verification.message());
        } catch (RuntimeException ex) {
            target.setVerificationStatus(TransferTarget.VerificationStatus.FAILED);
            target.setVerificationMessage(ex.getMessage());
            target.setLastVerifiedAt(LocalDateTime.now());
            transferTargetRepository.save(target);
            throw ex;
        }
        target.setLastVerifiedAt(LocalDateTime.now());
        return toDto(transferTargetRepository.save(target));
    }

    public void deleteTarget(UUID targetId) {
        requireAdmin();
        TransferTarget target = requireTarget(targetId);
        boolean inUse = replicationDefinitionRepository.findAll().stream()
            .anyMatch(definition -> Objects.equals(definition.getTransferTargetId(), targetId));
        if (inUse) {
            throw new IllegalStateException("Transfer target is still referenced by a replication definition");
        }
        transferTargetRepository.delete(target);
    }

    public java.util.List<ReplicationDefinitionDto> listDefinitions() {
        requireAdmin();
        return replicationDefinitionRepository.findAll().stream()
            .map(this::toDto)
            .toList();
    }

    public ReplicationDefinitionDto getDefinition(UUID definitionId) {
        requireAdmin();
        return toDto(requireDefinition(definitionId));
    }

    public ReplicationDefinitionDto createDefinition(ReplicationDefinitionMutationRequest request) {
        requireAdmin();
        String name = normalizeRequired(request.name(), "Definition name is required");
        if (replicationDefinitionRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Replication definition already exists: " + name);
        }
        Node source = nodeService.getNode(requiredId(request.sourceNodeId(), "sourceNodeId"));
        TransferTarget target = requireEnabledTarget(requiredId(request.transferTargetId(), "transferTargetId"), false);
        ReplicationDefinition definition = new ReplicationDefinition();
        definition.setName(name);
        definition.setDescription(normalizeOptional(request.description()));
        definition.setSourceNodeId(source.getId());
        definition.setTransferTargetId(target.getId());
        definition.setIncludeChildren(request.includeChildren() == null || request.includeChildren());
        definition.setEnabled(request.enabled() == null || request.enabled());
        applyDefinitionScheduleAndFailurePolicy(definition, request);
        return toDto(replicationDefinitionRepository.save(definition));
    }

    public ReplicationDefinitionDto updateDefinition(UUID definitionId, ReplicationDefinitionMutationRequest request) {
        requireAdmin();
        ReplicationDefinition definition = requireDefinition(definitionId);
        String nextName = normalizeRequired(request.name(), "Definition name is required");
        boolean renamed = !definition.getName().equalsIgnoreCase(nextName);
        if (renamed && replicationDefinitionRepository.existsByNameIgnoreCase(nextName)) {
            throw new IllegalArgumentException("Replication definition already exists: " + nextName);
        }
        Node source = nodeService.getNode(requiredId(request.sourceNodeId(), "sourceNodeId"));
        TransferTarget target = requireEnabledTarget(requiredId(request.transferTargetId(), "transferTargetId"), false);
        definition.setName(nextName);
        definition.setDescription(normalizeOptional(request.description()));
        definition.setSourceNodeId(source.getId());
        definition.setTransferTargetId(target.getId());
        definition.setIncludeChildren(request.includeChildren() == null || request.includeChildren());
        definition.setEnabled(request.enabled() == null || request.enabled());
        applyDefinitionScheduleAndFailurePolicy(definition, request);
        return toDto(replicationDefinitionRepository.save(definition));
    }

    public void deleteDefinition(UUID definitionId) {
        requireAdmin();
        replicationDefinitionRepository.delete(requireDefinition(definitionId));
    }

    public ReplicationJobDto runDefinition(UUID definitionId) {
        requireAdmin();
        ReplicationDefinition definition = requireDefinition(definitionId);
        if (!definition.isEnabled()) {
            throw new IllegalStateException("Replication definition is disabled");
        }
        TransferTarget target = requireEnabledTarget(definition.getTransferTargetId(), true);
        Node source = nodeService.getNode(definition.getSourceNodeId());
        return queueJob(definition, target, source, null, 1, "Queued replication");
    }

    public Page<ReplicationJobDto> listJobs(Pageable pageable) {
        requireAdmin();
        return replicationJobRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDto);
    }

    public ReplicationJobDto getJob(UUID jobId) {
        requireAdmin();
        return toDto(requireJob(jobId));
    }

    public ReplicationJobDto retryJob(UUID jobId) {
        requireAdmin();
        return toDto(queueRetryJob(requireJob(jobId), false));
    }

    public ScheduledReplicationBatchDto runScheduledDefinitions() {
        List<ReplicationDefinition> dueDefinitions = replicationDefinitionRepository
            .findByEnabledTrueAndCronExpressionIsNotNullAndNextRunAtIsNotNullAndNextRunAtLessThanEqual(LocalDateTime.now());
        int queuedCount = 0;
        int skippedCount = 0;
        for (ReplicationDefinition definition : dueDefinitions) {
            try {
                if (hasActiveJob(definition.getId())) {
                    skippedCount++;
                    continue;
                }
                TransferTarget target = requireEnabledTarget(definition.getTransferTargetId(), true);
                Node source = nodeService.getNode(definition.getSourceNodeId());
                queueJob(definition, target, source, null, 1, "Queued scheduled replication", true, null);
                definition.setNextRunAt(computeNextRunAt(definition, LocalDateTime.now()));
                replicationDefinitionRepository.save(definition);
                queuedCount++;
            } catch (RuntimeException ex) {
                log.warn("Scheduled replication definition {} skipped: {}", definition.getId(), ex.getMessage());
                skippedCount++;
            }
        }
        return new ScheduledReplicationBatchDto(dueDefinitions.size(), queuedCount, skippedCount);
    }

    public RetriedReplicationBatchDto runDueRetries() {
        List<ReplicationJob> dueRetries = replicationJobRepository.findByStatusAndScheduledForLessThanEqual(
            ReplicationJobStatus.PENDING,
            LocalDateTime.now()
        );
        int startedCount = 0;
        int skippedCount = 0;
        for (ReplicationJob job : dueRetries) {
            if (job.getRetryOfJobId() == null) {
                continue;
            }
            try {
                executor.execute(() -> processJob(job.getId()));
                startedCount++;
            } catch (RuntimeException ex) {
                log.warn("Retry job {} could not be started: {}", job.getId(), ex.getMessage());
                skippedCount++;
            }
        }
        return new RetriedReplicationBatchDto(dueRetries.size(), startedCount, skippedCount);
    }

    public ReplicationJobRetentionCleanupDto cleanupExpiredJobs() {
        LocalDateTime now = LocalDateTime.now();
        int deletedJobs = 0;
        int affectedDefinitions = 0;
        for (ReplicationDefinition definition : replicationDefinitionRepository.findAll()) {
            int retentionDays = normalizePositive(definition.getJobRetentionDays(), 30);
            LocalDateTime cutoff = now.minusDays(retentionDays);
            List<ReplicationJob> expiredJobs = replicationJobRepository.findByDefinitionIdAndStatusInAndCompletedAtBefore(
                definition.getId(),
                List.of(ReplicationJobStatus.COMPLETED, ReplicationJobStatus.FAILED, ReplicationJobStatus.CANCELED),
                cutoff
            );
            if (!expiredJobs.isEmpty()) {
                replicationJobRepository.deleteAll(expiredJobs);
                deletedJobs += expiredJobs.size();
                affectedDefinitions++;
            }
        }
        return new ReplicationJobRetentionCleanupDto(affectedDefinitions, deletedJobs);
    }

    void processJob(UUID jobId) {
        ReplicationJob job = requireJob(jobId);
        job.setStatus(ReplicationJobStatus.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        job.setLastAttemptedAt(LocalDateTime.now());
        job.setTransportStatus(ReplicationJob.TransportStatus.RUNNING);
        job.setTransportMessage("Transport replication started");
        job.setLastMessage("Replication started");
        job = replicationJobRepository.save(job);

        try {
            ReplicationDefinition definition = requireDefinition(job.getDefinitionId());
            TransferTarget target = requireEnabledTarget(job.getTransferTargetId(), true);
            Node source = nodeService.getNode(job.getSourceNodeId());
            TransferClient.TransferExecutionResult result = clientFor(target)
                .replicate(
                    target,
                    source,
                    definition.isIncludeChildren(),
                    definition.getConflictPolicy() != null
                        ? definition.getConflictPolicy()
                        : ReplicationDefinition.ConflictPolicy.RENAME
                );

            definition.setLastRunAt(LocalDateTime.now());
            replicationDefinitionRepository.save(definition);

            job.setCopiedNodeId(result.copiedNodeId());
            job.setStatus(ReplicationJobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            job.setLastMessage(result.message());
            job.setTransportStatus(ReplicationJob.TransportStatus.SUCCESS);
            job.setTransportMessage(result.message());
            job.setErrorLog(null);
            replicationJobRepository.save(job);
        } catch (RuntimeException ex) {
            log.warn("Replication job {} failed: {}", jobId, ex.getMessage());
            job.setStatus(ReplicationJobStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            job.setLastMessage("Replication failed");
            job.setTransportStatus(ReplicationJob.TransportStatus.FAILED);
            job.setTransportMessage(ex.getMessage());
            job.setErrorLog(ex.getMessage());
            replicationJobRepository.save(job);
            tryQueueAutomaticRetry(job);
        }
    }

    private ReplicationJobDto queueJob(
        ReplicationDefinition definition,
        TransferTarget target,
        Node source,
        UUID retryOfJobId,
        int attemptNumber,
        String queuedMessage
    ) {
        return toDto(queueJob(definition, target, source, retryOfJobId, attemptNumber, queuedMessage, true, null));
    }

    private ReplicationJob queueRetryJob(ReplicationJob originalJob, boolean automatic) {
        if (!automatic
            && originalJob.getStatus() != ReplicationJobStatus.FAILED
            && originalJob.getStatus() != ReplicationJobStatus.CANCELED) {
            throw new IllegalStateException("Only failed or canceled replication jobs can be retried");
        }
        ReplicationDefinition definition = requireDefinition(originalJob.getDefinitionId());
        TransferTarget target = requireEnabledTarget(originalJob.getTransferTargetId(), true);
        Node source = nodeService.getNode(originalJob.getSourceNodeId());
        String queuedMessage = automatic
            ? "Queued automatic retry for job " + originalJob.getId()
            : "Queued manual retry for job " + originalJob.getId();
        return queueJob(
            definition,
            target,
            source,
            originalJob.getId(),
            originalJob.getAttemptNumber() + 1,
            queuedMessage,
            true,
            null
        );
    }

    private ReplicationJob queueJob(
        ReplicationDefinition definition,
        TransferTarget target,
        Node source,
        UUID retryOfJobId,
        int attemptNumber,
        String queuedMessage,
        boolean autoStart,
        LocalDateTime scheduledFor
    ) {
        ReplicationJob job = new ReplicationJob();
        job.setDefinitionId(definition.getId());
        job.setTransferTargetId(target.getId());
        job.setSourceNodeId(source.getId());
        job.setRetryOfJobId(retryOfJobId);
        job.setAttemptNumber(attemptNumber);
        job.setScheduledFor(scheduledFor);
        job.setUserId(securityService.getCurrentUser());
        job.setStatus(ReplicationJobStatus.PENDING);
        job.setTransportStatus(ReplicationJob.TransportStatus.NEVER_RUN);
        job.setLastMessage(queuedMessage);
        ReplicationJob saved = replicationJobRepository.save(job);
        if (autoStart) {
            executor.execute(() -> processJob(saved.getId()));
        }
        return saved;
    }

    private void tryQueueAutomaticRetry(ReplicationJob failedJob) {
        ReplicationDefinition definition = requireDefinition(failedJob.getDefinitionId());
        if (!definition.isAutoRetryEnabled()) {
            return;
        }
        int maxAttempts = normalizeNonNegative(definition.getMaxRetryAttempts());
        if (maxAttempts <= 0 || failedJob.getAttemptNumber() > maxAttempts) {
            return;
        }
        TransferTarget target = requireEnabledTarget(failedJob.getTransferTargetId(), true);
        Node source = nodeService.getNode(failedJob.getSourceNodeId());
        int backoffMinutes = normalizeNonNegative(definition.getRetryBackoffMinutes());
        LocalDateTime scheduledFor = LocalDateTime.now().plusMinutes(backoffMinutes);
        ReplicationJob retryJob = queueJob(
            definition,
            target,
            source,
            failedJob.getId(),
            failedJob.getAttemptNumber() + 1,
            "Queued automatic retry for job " + failedJob.getId(),
            backoffMinutes == 0,
            backoffMinutes == 0 ? null : scheduledFor
        );
        log.info(
            "Queued automatic replication retry {} for failed job {} at {}",
            retryJob.getId(),
            failedJob.getId(),
            scheduledFor
        );
    }

    private void requireAdmin() {
        if (!securityService.hasRole("ROLE_ADMIN")) {
            throw new SecurityException("Only administrators can manage transfer and replication");
        }
    }

    private TransferTarget requireTarget(UUID targetId) {
        return transferTargetRepository.findById(targetId)
            .orElseThrow(() -> new java.util.NoSuchElementException("Transfer target not found: " + targetId));
    }

    private TransferTarget requireEnabledTarget(UUID targetId, boolean requireEnabled) {
        TransferTarget target = requireTarget(targetId);
        if (requireEnabled && !target.isEnabled()) {
            throw new IllegalStateException("Transfer target is disabled");
        }
        if (target.getTransportType() == TransferTarget.TransportType.LOOPBACK) {
            folderService.getFolder(target.getTargetFolderId());
        } else {
            requiredId(target.getTargetFolderId(), "targetFolderId");
            normalizeRequired(target.getEndpointUrl(), "endpointUrl is required for ATHENA_HTTP targets");
        }
        return target;
    }

    private ReplicationDefinition requireDefinition(UUID definitionId) {
        return replicationDefinitionRepository.findById(definitionId)
            .orElseThrow(() -> new java.util.NoSuchElementException("Replication definition not found: " + definitionId));
    }

    private ReplicationJob requireJob(UUID jobId) {
        return replicationJobRepository.findById(jobId)
            .orElseThrow(() -> new java.util.NoSuchElementException("Replication job not found: " + jobId));
    }

    private TransferTargetDto toDto(TransferTarget target) {
        String folderName = null;
        if (target.getTransportType() == TransferTarget.TransportType.LOOPBACK) {
            try {
                folderName = folderService.getFolder(target.getTargetFolderId()).getName();
            } catch (RuntimeException ignored) {
                // Keep DTO stable even if the folder became unavailable.
            }
        }
        return new TransferTargetDto(
            target.getId(),
            target.getName(),
            target.getDescription(),
            target.getTransportType(),
            target.getTargetFolderId(),
            folderName,
            target.getEndpointUrl(),
            target.getEndpointPath(),
            target.getAuthType(),
            target.getAuthUsername(),
            target.getAuthSecret() != null && !target.getAuthSecret().isBlank(),
            target.isEnabled(),
            target.getVerificationStatus(),
            target.getVerificationMessage(),
            target.getLastVerifiedAt(),
            target.getCreatedAt(),
            target.getUpdatedAt()
        );
    }

    private ReplicationDefinitionDto toDto(ReplicationDefinition definition) {
        String sourceNodeName = null;
        String targetName = null;
        try {
            sourceNodeName = nodeService.getNode(definition.getSourceNodeId()).getName();
        } catch (RuntimeException ignored) {
        }
        try {
            targetName = requireTarget(definition.getTransferTargetId()).getName();
        } catch (RuntimeException ignored) {
        }
        return new ReplicationDefinitionDto(
            definition.getId(),
            definition.getName(),
            definition.getDescription(),
            definition.getSourceNodeId(),
            sourceNodeName,
            definition.getTransferTargetId(),
            targetName,
            definition.isIncludeChildren(),
            definition.isEnabled(),
            definition.getCronExpression(),
            definition.getScheduleTimezone(),
            definition.getNextRunAt(),
            definition.isAutoRetryEnabled(),
            normalizeNonNegative(definition.getMaxRetryAttempts()),
            normalizeNonNegative(definition.getRetryBackoffMinutes()),
            normalizePositive(definition.getJobRetentionDays(), 30),
            definition.getConflictPolicy() != null
                ? definition.getConflictPolicy()
                : ReplicationDefinition.ConflictPolicy.RENAME,
            definition.getLastRunAt(),
            definition.getCreatedAt(),
            definition.getUpdatedAt()
        );
    }

    private ReplicationJobDto toDto(ReplicationJob job) {
        return new ReplicationJobDto(
            job.getId(),
            job.getDefinitionId(),
            job.getTransferTargetId(),
            job.getSourceNodeId(),
            job.getRetryOfJobId(),
            job.getAttemptNumber(),
            job.getScheduledFor(),
            job.getCopiedNodeId(),
            job.getUserId(),
            job.getStatus(),
            job.getLastMessage(),
            job.getTransportStatus(),
            job.getTransportMessage(),
            job.getErrorLog(),
            job.getLastAttemptedAt(),
            job.getStartedAt(),
            job.getCompletedAt(),
            job.getCreatedAt(),
            job.getUpdatedAt()
        );
    }

    private void applyDefinitionScheduleAndFailurePolicy(
        ReplicationDefinition definition,
        ReplicationDefinitionMutationRequest request
    ) {
        String cronExpression = normalizeOptional(request.cronExpression());
        String scheduleTimezone = normalizeOptional(request.scheduleTimezone());
        if (scheduleTimezone == null) {
            scheduleTimezone = "UTC";
        }
        if (cronExpression != null) {
            validateCronExpression(cronExpression, scheduleTimezone);
            definition.setCronExpression(cronExpression);
            definition.setScheduleTimezone(scheduleTimezone);
            definition.setNextRunAt(computeNextRunAt(cronExpression, scheduleTimezone, LocalDateTime.now()));
        } else {
            definition.setCronExpression(null);
            definition.setScheduleTimezone(scheduleTimezone);
            definition.setNextRunAt(null);
        }

        boolean autoRetryEnabled = request.autoRetryEnabled() != null && request.autoRetryEnabled();
        definition.setAutoRetryEnabled(autoRetryEnabled);
        definition.setMaxRetryAttempts(autoRetryEnabled ? normalizeNonNegative(request.maxRetryAttempts()) : 0);
        definition.setRetryBackoffMinutes(autoRetryEnabled ? normalizeNonNegative(request.retryBackoffMinutes()) : 0);
        definition.setJobRetentionDays(normalizePositive(request.jobRetentionDays(), 30));
        definition.setConflictPolicy(request.conflictPolicy() != null
            ? request.conflictPolicy()
            : ReplicationDefinition.ConflictPolicy.RENAME);
    }

    private void validateCronExpression(String cronExpression, String timezone) {
        try {
            CronExpression.parse(cronExpression);
            ZoneId.of(timezone);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid replication cron expression: " + cronExpression, ex);
        }
    }

    private LocalDateTime computeNextRunAt(ReplicationDefinition definition, LocalDateTime reference) {
        if (definition.getCronExpression() == null || definition.getCronExpression().isBlank()) {
            return null;
        }
        return computeNextRunAt(definition.getCronExpression(), definition.getScheduleTimezone(), reference);
    }

    private LocalDateTime computeNextRunAt(String cronExpression, String timezone, LocalDateTime reference) {
        CronExpression cron = CronExpression.parse(cronExpression);
        ZoneId scheduleZone = ZoneId.of(timezone != null ? timezone : "UTC");
        ZoneId systemZone = ZoneId.systemDefault();
        ZonedDateTime base = reference.atZone(systemZone).withZoneSameInstant(scheduleZone);
        ZonedDateTime next = cron.next(base);
        return next == null ? null : next.withZoneSameInstant(systemZone).toLocalDateTime();
    }

    private boolean hasActiveJob(UUID definitionId) {
        return replicationJobRepository.findAll().stream()
            .anyMatch(job -> Objects.equals(job.getDefinitionId(), definitionId)
                && (job.getStatus() == ReplicationJobStatus.PENDING || job.getStatus() == ReplicationJobStatus.RUNNING));
    }

    private int normalizeNonNegative(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    private int normalizePositive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private UUID requiredId(UUID value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private void applyTargetConfiguration(TransferTarget target, TransferTargetMutationRequest request, boolean creating) {
        TransferTarget.TransportType transportType = request.transportType() != null
            ? request.transportType()
            : TransferTarget.TransportType.LOOPBACK;
        UUID targetFolderId = requiredId(request.targetFolderId(), "targetFolderId");
        TransferTarget.AuthType authType = request.authType() != null
            ? request.authType()
            : TransferTarget.AuthType.NONE;

        TransferTarget.TransportType previousTransportType = target.getTransportType();
        UUID previousTargetFolderId = target.getTargetFolderId();
        String previousEndpointUrl = target.getEndpointUrl();
        String previousEndpointPath = target.getEndpointPath();
        TransferTarget.AuthType previousAuthType = target.getAuthType();
        String previousAuthUsername = target.getAuthUsername();
        String previousAuthSecret = target.getAuthSecret();

        target.setTransportType(transportType);
        target.setTargetFolderId(targetFolderId);
        target.setEnabled(request.enabled() == null || request.enabled());

        if (transportType == TransferTarget.TransportType.LOOPBACK) {
            Folder folder = folderService.getFolder(targetFolderId);
            target.setTargetFolderId(folder.getId());
            target.setEndpointUrl(null);
            target.setEndpointPath("/api/v1");
            target.setAuthType(TransferTarget.AuthType.NONE);
            target.setAuthUsername(null);
            target.setAuthSecret(null);
        } else {
            target.setEndpointUrl(normalizeEndpointUrl(request.endpointUrl()));
            target.setEndpointPath(normalizeEndpointPath(request.endpointPath()));
            target.setAuthType(authType);
            String username = request.authUsername() != null
                ? normalizeOptional(request.authUsername())
                : target.getAuthUsername();
            String secret = request.authSecret() != null
                ? normalizeOptional(request.authSecret())
                : target.getAuthSecret();
            if (authType == TransferTarget.AuthType.NONE) {
                username = null;
                secret = null;
            } else if (authType == TransferTarget.AuthType.BASIC) {
                if (username == null) {
                    throw new IllegalArgumentException("authUsername is required for BASIC auth");
                }
                if (secret == null) {
                    throw new IllegalArgumentException("authSecret is required for BASIC auth");
                }
            } else if (secret == null) {
                throw new IllegalArgumentException("authSecret is required for BEARER auth");
            }
            target.setAuthUsername(username);
            target.setAuthSecret(secret);
        }

        boolean verificationInputsChanged = creating
            || !Objects.equals(previousTransportType, target.getTransportType())
            || !Objects.equals(previousTargetFolderId, target.getTargetFolderId())
            || !Objects.equals(previousEndpointUrl, target.getEndpointUrl())
            || !Objects.equals(previousEndpointPath, target.getEndpointPath())
            || !Objects.equals(previousAuthType, target.getAuthType())
            || !Objects.equals(previousAuthUsername, target.getAuthUsername())
            || !Objects.equals(previousAuthSecret, target.getAuthSecret());

        if (verificationInputsChanged) {
            target.setVerificationStatus(TransferTarget.VerificationStatus.NEVER_VERIFIED);
            target.setVerificationMessage(null);
            target.setLastVerifiedAt(null);
        }
    }

    private String normalizeEndpointUrl(String endpointUrl) {
        String normalized = normalizeRequired(endpointUrl, "endpointUrl is required for ATHENA_HTTP targets");
        return normalized.replaceAll("/+$", "");
    }

    private String normalizeEndpointPath(String endpointPath) {
        String normalized = normalizeOptional(endpointPath);
        if (normalized == null) {
            return "/api/v1";
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        normalized = normalized.replaceAll("/+$", "");
        return normalized.isBlank() ? "/api/v1" : normalized;
    }

    private TransferClient clientFor(TransferTarget target) {
        TransferTarget.TransportType type = target.getTransportType() != null
            ? target.getTransportType()
            : TransferTarget.TransportType.LOOPBACK;
        TransferClient client = transferClients.get(type);
        if (client == null) {
            throw new IllegalStateException("No transfer client registered for transport type: " + type);
        }
        return client;
    }

    public record TransferTargetMutationRequest(
        String name,
        String description,
        TransferTarget.TransportType transportType,
        UUID targetFolderId,
        String endpointUrl,
        String endpointPath,
        TransferTarget.AuthType authType,
        String authUsername,
        String authSecret,
        Boolean enabled
    ) {}

    public record TransferTargetDto(
        UUID id,
        String name,
        String description,
        TransferTarget.TransportType transportType,
        UUID targetFolderId,
        String targetFolderName,
        String endpointUrl,
        String endpointPath,
        TransferTarget.AuthType authType,
        String authUsername,
        boolean authSecretConfigured,
        boolean enabled,
        TransferTarget.VerificationStatus verificationStatus,
        String verificationMessage,
        LocalDateTime lastVerifiedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    public record ReplicationDefinitionMutationRequest(
        String name,
        String description,
        UUID sourceNodeId,
        UUID transferTargetId,
        Boolean includeChildren,
        Boolean enabled,
        String cronExpression,
        String scheduleTimezone,
        Boolean autoRetryEnabled,
        Integer maxRetryAttempts,
        Integer retryBackoffMinutes,
        Integer jobRetentionDays,
        ReplicationDefinition.ConflictPolicy conflictPolicy
    ) {}

    public record ReplicationDefinitionDto(
        UUID id,
        String name,
        String description,
        UUID sourceNodeId,
        String sourceNodeName,
        UUID transferTargetId,
        String transferTargetName,
        boolean includeChildren,
        boolean enabled,
        String cronExpression,
        String scheduleTimezone,
        LocalDateTime nextRunAt,
        boolean autoRetryEnabled,
        int maxRetryAttempts,
        int retryBackoffMinutes,
        int jobRetentionDays,
        ReplicationDefinition.ConflictPolicy conflictPolicy,
        LocalDateTime lastRunAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    public record ReplicationJobDto(
        UUID id,
        UUID definitionId,
        UUID transferTargetId,
        UUID sourceNodeId,
        UUID retryOfJobId,
        int attemptNumber,
        LocalDateTime scheduledFor,
        UUID copiedNodeId,
        String userId,
        ReplicationJobStatus status,
        String lastMessage,
        ReplicationJob.TransportStatus transportStatus,
        String transportMessage,
        String errorLog,
        LocalDateTime lastAttemptedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    public record ScheduledReplicationBatchDto(
        int dueDefinitions,
        int queuedDefinitions,
        int skippedDefinitions
    ) {}

    public record RetriedReplicationBatchDto(
        int dueRetryJobs,
        int startedRetryJobs,
        int skippedRetryJobs
    ) {}

    public record ReplicationJobRetentionCleanupDto(
        int affectedDefinitions,
        int deletedJobs
    ) {}
}
