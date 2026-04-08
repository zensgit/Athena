package com.ecm.core.service;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.ReplicationDefinition;
import com.ecm.core.entity.ReplicationJob;
import com.ecm.core.entity.ReplicationJob.ReplicationJobStatus;
import com.ecm.core.entity.TransferTarget;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.ReplicationDefinitionRepository;
import com.ecm.core.repository.ReplicationJobRepository;
import com.ecm.core.repository.TransferTargetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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

    public TransferReplicationService(
        TransferTargetRepository transferTargetRepository,
        ReplicationDefinitionRepository replicationDefinitionRepository,
        ReplicationJobRepository replicationJobRepository,
        FolderService folderService,
        NodeService nodeService,
        NodeRepository nodeRepository,
        SecurityService securityService
    ) {
        this(
            transferTargetRepository,
            replicationDefinitionRepository,
            replicationJobRepository,
            folderService,
            nodeService,
            nodeRepository,
            securityService,
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
        Folder folder = folderService.getFolder(requiredId(request.targetFolderId(), "targetFolderId"));
        TransferTarget target = new TransferTarget();
        target.setName(name);
        target.setDescription(normalizeOptional(request.description()));
        target.setTargetFolderId(folder.getId());
        target.setEnabled(request.enabled() == null || request.enabled());
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
        Folder folder = folderService.getFolder(requiredId(request.targetFolderId(), "targetFolderId"));
        target.setName(nextName);
        target.setDescription(normalizeOptional(request.description()));
        target.setTargetFolderId(folder.getId());
        target.setEnabled(request.enabled() == null || request.enabled());
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

        ReplicationJob job = new ReplicationJob();
        job.setDefinitionId(definition.getId());
        job.setTransferTargetId(target.getId());
        job.setSourceNodeId(source.getId());
        job.setUserId(securityService.getCurrentUser());
        job.setStatus(ReplicationJobStatus.PENDING);
        job.setLastMessage("Queued replication");
        ReplicationJob saved = replicationJobRepository.save(job);

        executor.execute(() -> processJob(saved.getId()));
        return toDto(saved);
    }

    public Page<ReplicationJobDto> listJobs(Pageable pageable) {
        requireAdmin();
        return replicationJobRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDto);
    }

    public ReplicationJobDto getJob(UUID jobId) {
        requireAdmin();
        return toDto(requireJob(jobId));
    }

    void processJob(UUID jobId) {
        ReplicationJob job = requireJob(jobId);
        job.setStatus(ReplicationJobStatus.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        job.setLastMessage("Replication started");
        job = replicationJobRepository.save(job);

        try {
            ReplicationDefinition definition = requireDefinition(job.getDefinitionId());
            TransferTarget target = requireEnabledTarget(job.getTransferTargetId(), true);
            Node source = nodeService.getNode(job.getSourceNodeId());
            folderService.getFolder(target.getTargetFolderId());

            String replicaName = resolveReplicaName(target.getTargetFolderId(), source.getName());
            Node copied = nodeService.copyNode(source.getId(), target.getTargetFolderId(), replicaName, definition.isIncludeChildren());

            definition.setLastRunAt(LocalDateTime.now());
            replicationDefinitionRepository.save(definition);

            job.setCopiedNodeId(copied.getId());
            job.setStatus(ReplicationJobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            job.setLastMessage("Replication completed");
            replicationJobRepository.save(job);
        } catch (RuntimeException ex) {
            log.warn("Replication job {} failed: {}", jobId, ex.getMessage());
            job.setStatus(ReplicationJobStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            job.setLastMessage("Replication failed");
            job.setErrorLog(ex.getMessage());
            replicationJobRepository.save(job);
        }
    }

    private String resolveReplicaName(UUID targetFolderId, String requestedName) {
        String baseName = requestedName;
        String extension = "";
        int dotIndex = requestedName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = requestedName.substring(0, dotIndex);
            extension = requestedName.substring(dotIndex);
        }
        String candidate = requestedName;
        int attempt = 1;
        while (nodeRepository.findByParentIdAndName(targetFolderId, candidate).isPresent()) {
            candidate = baseName + " (Replica " + attempt + ")" + extension;
            attempt++;
        }
        return candidate;
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
        folderService.getFolder(target.getTargetFolderId());
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
        try {
            folderName = folderService.getFolder(target.getTargetFolderId()).getName();
        } catch (RuntimeException ignored) {
            // Keep DTO stable even if the folder became unavailable.
        }
        return new TransferTargetDto(
            target.getId(),
            target.getName(),
            target.getDescription(),
            target.getTargetFolderId(),
            folderName,
            target.isEnabled(),
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
            job.getCopiedNodeId(),
            job.getUserId(),
            job.getStatus(),
            job.getLastMessage(),
            job.getErrorLog(),
            job.getStartedAt(),
            job.getCompletedAt(),
            job.getCreatedAt(),
            job.getUpdatedAt()
        );
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

    public record TransferTargetMutationRequest(
        String name,
        String description,
        UUID targetFolderId,
        Boolean enabled
    ) {}

    public record TransferTargetDto(
        UUID id,
        String name,
        String description,
        UUID targetFolderId,
        String targetFolderName,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    public record ReplicationDefinitionMutationRequest(
        String name,
        String description,
        UUID sourceNodeId,
        UUID transferTargetId,
        Boolean includeChildren,
        Boolean enabled
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
        LocalDateTime lastRunAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    public record ReplicationJobDto(
        UUID id,
        UUID definitionId,
        UUID transferTargetId,
        UUID sourceNodeId,
        UUID copiedNodeId,
        String userId,
        ReplicationJobStatus status,
        String lastMessage,
        String errorLog,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}
}
