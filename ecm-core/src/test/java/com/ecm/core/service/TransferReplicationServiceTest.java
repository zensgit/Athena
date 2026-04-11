package com.ecm.core.service;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.ReplicationDefinition;
import com.ecm.core.entity.ReplicationJob;
import com.ecm.core.entity.TransferTarget;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.ReplicationDefinitionRepository;
import com.ecm.core.repository.ReplicationJobRepository;
import com.ecm.core.repository.TransferTargetRepository;
import com.ecm.core.service.transfer.TransferClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferReplicationServiceTest {

    @Mock private TransferTargetRepository transferTargetRepository;
    @Mock private ReplicationDefinitionRepository replicationDefinitionRepository;
    @Mock private ReplicationJobRepository replicationJobRepository;
    @Mock private FolderService folderService;
    @Mock private NodeService nodeService;
    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;
    @Mock private TransferClient loopbackTransferClient;
    @Mock private TransferClient athenaTransferClient;

    private TransferReplicationService service;
    private Map<UUID, TransferTarget> storedTargets;
    private Map<UUID, ReplicationDefinition> storedDefinitions;
    private Map<UUID, ReplicationJob> storedJobs;

    @BeforeEach
    void setUp() {
        lenient().when(loopbackTransferClient.transportType()).thenReturn(TransferTarget.TransportType.LOOPBACK);
        lenient().when(athenaTransferClient.transportType()).thenReturn(TransferTarget.TransportType.ATHENA_HTTP);
        service = new TransferReplicationService(
            transferTargetRepository,
            replicationDefinitionRepository,
            replicationJobRepository,
            folderService,
            nodeService,
            nodeRepository,
            securityService,
            List.of(loopbackTransferClient, athenaTransferClient),
            Runnable::run
        );
        storedTargets = new LinkedHashMap<>();
        storedDefinitions = new LinkedHashMap<>();
        storedJobs = new LinkedHashMap<>();

        lenient().when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        lenient().when(securityService.getCurrentUser()).thenReturn("alice");
        lenient().when(nodeRepository.findByParentIdAndName(any(), anyString())).thenReturn(Optional.empty());

        lenient().when(transferTargetRepository.save(any(TransferTarget.class))).thenAnswer(invocation -> {
            TransferTarget target = invocation.getArgument(0);
            if (target.getId() == null) {
                target.setId(UUID.randomUUID());
                target.setCreatedAt(LocalDateTime.now());
            }
            target.setUpdatedAt(LocalDateTime.now());
            storedTargets.put(target.getId(), target);
            return target;
        });
        lenient().when(replicationDefinitionRepository.save(any(ReplicationDefinition.class))).thenAnswer(invocation -> {
            ReplicationDefinition definition = invocation.getArgument(0);
            if (definition.getId() == null) {
                definition.setId(UUID.randomUUID());
                definition.setCreatedAt(LocalDateTime.now());
            }
            definition.setUpdatedAt(LocalDateTime.now());
            storedDefinitions.put(definition.getId(), definition);
            return definition;
        });
        lenient().when(replicationJobRepository.save(any(ReplicationJob.class))).thenAnswer(invocation -> {
            ReplicationJob job = invocation.getArgument(0);
            if (job.getId() == null) {
                job.setId(UUID.randomUUID());
                job.setCreatedAt(LocalDateTime.now());
            }
            job.setUpdatedAt(LocalDateTime.now());
            storedJobs.put(job.getId(), job);
            return job;
        });

        lenient().when(transferTargetRepository.findById(any(UUID.class))).thenAnswer(invocation ->
            Optional.ofNullable(storedTargets.get(invocation.getArgument(0)))
        );
        lenient().when(replicationDefinitionRepository.findById(any(UUID.class))).thenAnswer(invocation ->
            Optional.ofNullable(storedDefinitions.get(invocation.getArgument(0)))
        );
        lenient().when(replicationJobRepository.findById(any(UUID.class))).thenAnswer(invocation ->
            Optional.ofNullable(storedJobs.get(invocation.getArgument(0)))
        );
        lenient().when(replicationDefinitionRepository.findAll()).thenAnswer(invocation -> List.copyOf(storedDefinitions.values()));
        lenient().when(transferTargetRepository.findAll()).thenAnswer(invocation -> List.copyOf(storedTargets.values()));
        lenient().when(replicationJobRepository.findAll()).thenAnswer(invocation -> List.copyOf(storedJobs.values()));
        lenient().when(replicationJobRepository.existsByDefinitionIdAndStatusIn(any(UUID.class), anyCollection())).thenAnswer(invocation -> {
            UUID defId = invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            java.util.Collection<ReplicationJob.ReplicationJobStatus> statuses = invocation.getArgument(1);
            return storedJobs.values().stream()
                .anyMatch(job -> java.util.Objects.equals(job.getDefinitionId(), defId) && statuses.contains(job.getStatus()));
        });
        lenient().when(replicationDefinitionRepository.existsByTransferTargetId(any(UUID.class))).thenAnswer(invocation -> {
            UUID targetId = invocation.getArgument(0);
            return storedDefinitions.values().stream()
                .anyMatch(def -> java.util.Objects.equals(def.getTransferTargetId(), targetId));
        });
        lenient().when(replicationDefinitionRepository.findByEnabledTrueAndCronExpressionIsNotNullAndNextRunAtIsNotNullAndNextRunAtLessThanEqual(any(LocalDateTime.class)))
            .thenAnswer(invocation -> {
                LocalDateTime cutoff = invocation.getArgument(0);
                return storedDefinitions.values().stream()
                    .filter(ReplicationDefinition::isEnabled)
                    .filter(definition -> definition.getCronExpression() != null && !definition.getCronExpression().isBlank())
                    .filter(definition -> definition.getNextRunAt() != null && !definition.getNextRunAt().isAfter(cutoff))
                    .toList();
            });
        lenient().when(replicationJobRepository.findAllByOrderByCreatedAtDesc(any())).thenAnswer(invocation -> {
            PageRequest pageable = invocation.getArgument(0);
            List<ReplicationJob> jobs = storedJobs.values().stream()
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .toList();
            return new PageImpl<>(jobs, pageable, jobs.size());
        });
        lenient().when(replicationJobRepository.findByStatusAndScheduledForLessThanEqual(any(), any(LocalDateTime.class)))
            .thenAnswer(invocation -> {
                ReplicationJob.ReplicationJobStatus status = invocation.getArgument(0);
                LocalDateTime cutoff = invocation.getArgument(1);
                return storedJobs.values().stream()
                    .filter(job -> job.getStatus() == status)
                    .filter(job -> job.getScheduledFor() != null && !job.getScheduledFor().isAfter(cutoff))
                    .toList();
            });
        lenient().when(replicationJobRepository.findByDefinitionIdAndStatusInAndCompletedAtBefore(any(), any(), any(LocalDateTime.class)))
            .thenAnswer(invocation -> {
                UUID definitionId = invocation.getArgument(0);
                @SuppressWarnings("unchecked")
                java.util.Collection<ReplicationJob.ReplicationJobStatus> statuses = invocation.getArgument(1);
                LocalDateTime cutoff = invocation.getArgument(2);
                return storedJobs.values().stream()
                    .filter(job -> java.util.Objects.equals(job.getDefinitionId(), definitionId))
                    .filter(job -> statuses.contains(job.getStatus()))
                    .filter(job -> job.getCompletedAt() != null && job.getCompletedAt().isBefore(cutoff))
                    .toList();
            });
        lenient().doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.Collection<ReplicationJob> jobs = invocation.getArgument(0);
            jobs.forEach(job -> storedJobs.remove(job.getId()));
            return null;
        }).when(replicationJobRepository).deleteAll(any(java.util.Collection.class));
    }

    @Test
    @DisplayName("createTarget persists folder-backed transfer target")
    void createTargetPersistsFolderBackedTarget() {
        UUID folderId = UUID.randomUUID();
        Folder folder = folder(folderId, "Outbound");
        when(folderService.getFolder(folderId)).thenReturn(folder);
        when(transferTargetRepository.existsByNameIgnoreCase("loopback")).thenReturn(false);

        TransferReplicationService.TransferTargetDto target = service.createTarget(
            new TransferReplicationService.TransferTargetMutationRequest(
                "loopback",
                "Local transfer target",
                TransferTarget.TransportType.LOOPBACK,
                folderId,
                null,
                null,
                null,
                null,
                null,
                true
            )
        );

        assertEquals("loopback", target.name());
        assertEquals(folderId, target.targetFolderId());
        assertEquals("Outbound", target.targetFolderName());
        assertEquals(TransferTarget.TransportType.LOOPBACK, target.transportType());
        assertTrue(target.enabled());
    }

    @Test
    @DisplayName("createTarget rejects duplicate target names")
    void createTargetRejectsDuplicateNames() {
        UUID folderId = UUID.randomUUID();
        when(transferTargetRepository.existsByNameIgnoreCase("loopback")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.createTarget(
            new TransferReplicationService.TransferTargetMutationRequest(
                "loopback",
                null,
                TransferTarget.TransportType.LOOPBACK,
                folderId,
                null,
                null,
                null,
                null,
                null,
                true
            )
        ));

        assertTrue(ex.getMessage().contains("Transfer target already exists"));
    }

    @Test
    @DisplayName("runDefinition executes synchronous loopback replication job")
    void runDefinitionExecutesLoopbackReplicationJob() {
        UUID targetFolderId = UUID.randomUUID();
        UUID sourceNodeId = UUID.randomUUID();

        TransferTarget target = new TransferTarget();
        target.setId(UUID.randomUUID());
        target.setName("loopback");
        target.setTransportType(TransferTarget.TransportType.LOOPBACK);
        target.setTargetFolderId(targetFolderId);
        target.setEnabled(true);
        target.setCreatedAt(LocalDateTime.now());
        storedTargets.put(target.getId(), target);

        ReplicationDefinition definition = new ReplicationDefinition();
        definition.setId(UUID.randomUUID());
        definition.setName("contracts");
        definition.setSourceNodeId(sourceNodeId);
        definition.setTransferTargetId(target.getId());
        definition.setIncludeChildren(true);
        definition.setEnabled(true);
        definition.setCreatedAt(LocalDateTime.now());
        storedDefinitions.put(definition.getId(), definition);

        Node source = node(sourceNodeId, "Contracts");
        UUID copiedNodeId = UUID.randomUUID();
        when(folderService.getFolder(targetFolderId)).thenReturn(folder(targetFolderId, "Outbound"));
        when(nodeService.getNode(sourceNodeId)).thenReturn(source);
        when(loopbackTransferClient.replicate(eq(target), eq(source), eq(true), eq(ReplicationDefinition.ConflictPolicy.RENAME), any()))
            .thenReturn(new TransferClient.TransferExecutionResult(copiedNodeId, "Loopback replication completed"));

        TransferReplicationService.ReplicationJobDto job = service.runDefinition(definition.getId());

        assertEquals(ReplicationJob.ReplicationJobStatus.COMPLETED, job.status());
        assertEquals(ReplicationJob.TransportStatus.SUCCESS, job.transportStatus());
        assertEquals(copiedNodeId, job.copiedNodeId());
        assertEquals(1, job.attemptNumber());
        assertNotNull(storedDefinitions.get(definition.getId()).getLastRunAt());
    }

    @Test
    @DisplayName("createDefinition validates cron and computes next run plus failure policy")
    void createDefinitionStoresScheduleAndFailurePolicy() {
        UUID sourceNodeId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Node source = node(sourceNodeId, "Contracts");
        TransferTarget target = new TransferTarget();
        target.setId(targetId);
        target.setName("remote");
        target.setTransportType(TransferTarget.TransportType.ATHENA_HTTP);
        target.setTargetFolderId(UUID.randomUUID());
        target.setEndpointUrl("https://remote.example");
        target.setEnabled(true);
        storedTargets.put(targetId, target);

        when(replicationDefinitionRepository.existsByNameIgnoreCase("nightly")).thenReturn(false);
        when(nodeService.getNode(sourceNodeId)).thenReturn(source);

        TransferReplicationService.ReplicationDefinitionDto definition = service.createDefinition(
            new TransferReplicationService.ReplicationDefinitionMutationRequest(
                "nightly",
                "Nightly replication",
                sourceNodeId,
                targetId,
                true,
                true,
                "0 0 2 * * *",
                "UTC",
                true,
                3,
                15,
                14,
                ReplicationDefinition.ConflictPolicy.OVERWRITE
            )
        );

        assertEquals("0 0 2 * * *", definition.cronExpression());
        assertEquals("UTC", definition.scheduleTimezone());
        assertNotNull(definition.nextRunAt());
        assertTrue(definition.autoRetryEnabled());
        assertEquals(3, definition.maxRetryAttempts());
        assertEquals(15, definition.retryBackoffMinutes());
        assertEquals(14, definition.jobRetentionDays());
        assertEquals(ReplicationDefinition.ConflictPolicy.OVERWRITE, definition.conflictPolicy());
    }

    @Test
    @DisplayName("createDefinition rejects invalid cron expressions")
    void createDefinitionRejectsInvalidCronExpressions() {
        UUID sourceNodeId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Node source = node(sourceNodeId, "Contracts");
        TransferTarget target = new TransferTarget();
        target.setId(targetId);
        target.setName("remote");
        target.setTransportType(TransferTarget.TransportType.ATHENA_HTTP);
        target.setTargetFolderId(UUID.randomUUID());
        target.setEndpointUrl("https://remote.example");
        target.setEnabled(true);
        storedTargets.put(targetId, target);

        when(replicationDefinitionRepository.existsByNameIgnoreCase("nightly")).thenReturn(false);
        when(nodeService.getNode(sourceNodeId)).thenReturn(source);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.createDefinition(
            new TransferReplicationService.ReplicationDefinitionMutationRequest(
                "nightly",
                "Nightly replication",
                sourceNodeId,
                targetId,
                true,
                true,
                "not-a-cron",
                "UTC",
                true,
                3,
                15,
                14,
                ReplicationDefinition.ConflictPolicy.RENAME
            )
        ));

        assertTrue(ex.getMessage().contains("Invalid replication cron expression"));
    }

    @Test
    @DisplayName("failed replication stores transport diagnostics and retry can requeue it")
    void failedReplicationStoresDiagnosticsAndSupportsRetry() {
        UUID targetFolderId = UUID.randomUUID();
        UUID sourceNodeId = UUID.randomUUID();

        TransferTarget target = new TransferTarget();
        target.setId(UUID.randomUUID());
        target.setName("remote-athena");
        target.setTransportType(TransferTarget.TransportType.ATHENA_HTTP);
        target.setTargetFolderId(targetFolderId);
        target.setEndpointUrl("https://remote.example");
        target.setEndpointPath("/api/v1");
        target.setEnabled(true);
        target.setCreatedAt(LocalDateTime.now());
        storedTargets.put(target.getId(), target);

        ReplicationDefinition definition = new ReplicationDefinition();
        definition.setId(UUID.randomUUID());
        definition.setName("contracts");
        definition.setSourceNodeId(sourceNodeId);
        definition.setTransferTargetId(target.getId());
        definition.setIncludeChildren(true);
        definition.setEnabled(true);
        definition.setCreatedAt(LocalDateTime.now());
        storedDefinitions.put(definition.getId(), definition);

        Node source = node(sourceNodeId, "Contracts");
        UUID copiedNodeId = UUID.randomUUID();
        when(nodeService.getNode(sourceNodeId)).thenReturn(source);
        when(athenaTransferClient.replicate(eq(target), eq(source), eq(true), eq(ReplicationDefinition.ConflictPolicy.RENAME), any()))
            .thenThrow(new IllegalStateException("Remote receiver rejected upload"))
            .thenReturn(new TransferClient.TransferExecutionResult(copiedNodeId, "Remote transfer completed after retry"));

        TransferReplicationService.ReplicationJobDto failedJob = service.runDefinition(definition.getId());

        assertEquals(ReplicationJob.ReplicationJobStatus.FAILED, failedJob.status());
        assertEquals(ReplicationJob.TransportStatus.FAILED, failedJob.transportStatus());
        assertEquals("Remote receiver rejected upload", failedJob.transportMessage());
        assertNotNull(failedJob.lastAttemptedAt());

        TransferReplicationService.ReplicationJobDto retriedJob = service.retryJob(failedJob.id());

        assertEquals(ReplicationJob.ReplicationJobStatus.COMPLETED, retriedJob.status());
        assertEquals(ReplicationJob.TransportStatus.SUCCESS, retriedJob.transportStatus());
        assertEquals(failedJob.id(), retriedJob.retryOfJobId());
        assertEquals(2, retriedJob.attemptNumber());
        assertEquals(copiedNodeId, retriedJob.copiedNodeId());
    }

    @Test
    @DisplayName("failed replication queues automatic retry with backoff when policy allows")
    void failedReplicationQueuesAutomaticRetryWithBackoff() {
        UUID targetFolderId = UUID.randomUUID();
        UUID sourceNodeId = UUID.randomUUID();

        TransferTarget target = new TransferTarget();
        target.setId(UUID.randomUUID());
        target.setName("remote-athena");
        target.setTransportType(TransferTarget.TransportType.ATHENA_HTTP);
        target.setTargetFolderId(targetFolderId);
        target.setEndpointUrl("https://remote.example");
        target.setEndpointPath("/api/v1");
        target.setEnabled(true);
        target.setCreatedAt(LocalDateTime.now());
        storedTargets.put(target.getId(), target);

        ReplicationDefinition definition = new ReplicationDefinition();
        definition.setId(UUID.randomUUID());
        definition.setName("contracts");
        definition.setSourceNodeId(sourceNodeId);
        definition.setTransferTargetId(target.getId());
        definition.setIncludeChildren(true);
        definition.setEnabled(true);
        definition.setAutoRetryEnabled(true);
        definition.setMaxRetryAttempts(2);
        definition.setRetryBackoffMinutes(10);
        definition.setCreatedAt(LocalDateTime.now());
        storedDefinitions.put(definition.getId(), definition);

        Node source = node(sourceNodeId, "Contracts");
        when(nodeService.getNode(sourceNodeId)).thenReturn(source);
        when(athenaTransferClient.replicate(eq(target), eq(source), eq(true), eq(ReplicationDefinition.ConflictPolicy.RENAME), any()))
            .thenThrow(new IllegalStateException("Remote receiver rejected upload"));

        TransferReplicationService.ReplicationJobDto failedJob = service.runDefinition(definition.getId());

        assertEquals(ReplicationJob.ReplicationJobStatus.FAILED, failedJob.status());
        ReplicationJob queuedRetry = storedJobs.values().stream()
            .filter(job -> failedJob.id().equals(job.getRetryOfJobId()))
            .findFirst()
            .orElseThrow();
        assertEquals(ReplicationJob.ReplicationJobStatus.PENDING, queuedRetry.getStatus());
        assertEquals(2, queuedRetry.getAttemptNumber());
        assertNotNull(queuedRetry.getScheduledFor());
    }

    @Test
    @DisplayName("runScheduledDefinitions queues due definitions and skips active jobs")
    void runScheduledDefinitionsQueuesDueDefinitionsAndSkipsActiveJobs() {
        UUID targetId = UUID.randomUUID();
        UUID sourceNodeId = UUID.randomUUID();
        TransferTarget target = new TransferTarget();
        target.setId(targetId);
        target.setName("remote");
        target.setTransportType(TransferTarget.TransportType.ATHENA_HTTP);
        target.setTargetFolderId(UUID.randomUUID());
        target.setEndpointUrl("https://remote.example");
        target.setEnabled(true);
        storedTargets.put(targetId, target);

        ReplicationDefinition dueDefinition = new ReplicationDefinition();
        dueDefinition.setId(UUID.randomUUID());
        dueDefinition.setName("nightly");
        dueDefinition.setSourceNodeId(sourceNodeId);
        dueDefinition.setTransferTargetId(targetId);
        dueDefinition.setEnabled(true);
        dueDefinition.setCronExpression("0 0 2 * * *");
        dueDefinition.setScheduleTimezone("UTC");
        dueDefinition.setNextRunAt(LocalDateTime.now().minusMinutes(1));
        storedDefinitions.put(dueDefinition.getId(), dueDefinition);

        ReplicationJob activeJob = new ReplicationJob();
        activeJob.setId(UUID.randomUUID());
        activeJob.setDefinitionId(dueDefinition.getId());
        activeJob.setTransferTargetId(targetId);
        activeJob.setSourceNodeId(sourceNodeId);
        activeJob.setStatus(ReplicationJob.ReplicationJobStatus.RUNNING);
        activeJob.setCreatedAt(LocalDateTime.now());
        storedJobs.put(activeJob.getId(), activeJob);

        TransferReplicationService.ScheduledReplicationBatchDto result = service.runScheduledDefinitions();

        assertEquals(1, result.dueDefinitions());
        assertEquals(0, result.queuedDefinitions());
        assertEquals(1, result.skippedDefinitions());
    }

    @Test
    @DisplayName("runDefinition passes conflict policy to transfer client")
    void runDefinitionPassesConflictPolicyToTransferClient() {
        UUID targetFolderId = UUID.randomUUID();
        UUID sourceNodeId = UUID.randomUUID();

        TransferTarget target = new TransferTarget();
        target.setId(UUID.randomUUID());
        target.setName("loopback");
        target.setTransportType(TransferTarget.TransportType.LOOPBACK);
        target.setTargetFolderId(targetFolderId);
        target.setEnabled(true);
        target.setCreatedAt(LocalDateTime.now());
        storedTargets.put(target.getId(), target);

        ReplicationDefinition definition = new ReplicationDefinition();
        definition.setId(UUID.randomUUID());
        definition.setName("contracts");
        definition.setSourceNodeId(sourceNodeId);
        definition.setTransferTargetId(target.getId());
        definition.setIncludeChildren(true);
        definition.setEnabled(true);
        definition.setConflictPolicy(ReplicationDefinition.ConflictPolicy.SKIP);
        definition.setCreatedAt(LocalDateTime.now());
        storedDefinitions.put(definition.getId(), definition);

        Node source = node(sourceNodeId, "Contracts");
        Node copied = node(UUID.randomUUID(), "Contracts");
        when(folderService.getFolder(targetFolderId)).thenReturn(folder(targetFolderId, "Outbound"));
        when(nodeService.getNode(sourceNodeId)).thenReturn(source);
        when(loopbackTransferClient.replicate(eq(target), eq(source), eq(true), eq(ReplicationDefinition.ConflictPolicy.SKIP), any()))
            .thenReturn(new TransferClient.TransferExecutionResult(copied.getId(), "Skipped existing"));

        service.runDefinition(definition.getId());

        verify(loopbackTransferClient).replicate(eq(target), eq(source), eq(true), eq(ReplicationDefinition.ConflictPolicy.SKIP), any());
    }

    @Test
    @DisplayName("cleanupExpiredJobs removes terminal jobs older than definition retention")
    void cleanupExpiredJobsRemovesTerminalJobsOlderThanRetention() {
        UUID definitionId = UUID.randomUUID();
        ReplicationDefinition definition = new ReplicationDefinition();
        definition.setId(definitionId);
        definition.setName("nightly");
        definition.setJobRetentionDays(7);
        storedDefinitions.put(definitionId, definition);

        ReplicationJob expired = new ReplicationJob();
        expired.setId(UUID.randomUUID());
        expired.setDefinitionId(definitionId);
        expired.setStatus(ReplicationJob.ReplicationJobStatus.COMPLETED);
        expired.setCompletedAt(LocalDateTime.now().minusDays(10));
        expired.setCreatedAt(LocalDateTime.now().minusDays(10));
        storedJobs.put(expired.getId(), expired);

        ReplicationJob fresh = new ReplicationJob();
        fresh.setId(UUID.randomUUID());
        fresh.setDefinitionId(definitionId);
        fresh.setStatus(ReplicationJob.ReplicationJobStatus.COMPLETED);
        fresh.setCompletedAt(LocalDateTime.now().minusDays(1));
        fresh.setCreatedAt(LocalDateTime.now().minusDays(1));
        storedJobs.put(fresh.getId(), fresh);

        TransferReplicationService.ReplicationJobRetentionCleanupDto cleanup = service.cleanupExpiredJobs();

        assertEquals(1, cleanup.affectedDefinitions());
        assertEquals(1, cleanup.deletedJobs());
        assertFalse(storedJobs.containsKey(expired.getId()));
        assertTrue(storedJobs.containsKey(fresh.getId()));
    }

    @Test
    @DisplayName("verifyTarget stores successful ATHENA_HTTP verification metadata")
    void verifyTargetStoresSuccessfulRemoteVerificationMetadata() {
        TransferTarget target = new TransferTarget();
        target.setId(UUID.randomUUID());
        target.setName("remote-athena");
        target.setTransportType(TransferTarget.TransportType.ATHENA_HTTP);
        target.setTargetFolderId(UUID.randomUUID());
        target.setEndpointUrl("https://remote.example");
        target.setEndpointPath("/api/v1");
        target.setEnabled(true);
        target.setCreatedAt(LocalDateTime.now());
        storedTargets.put(target.getId(), target);

        when(athenaTransferClient.verifyTarget(target))
            .thenReturn(new TransferClient.TransferVerificationResult("Verified remote Athena transfer receiver folder: Contracts", "athena"));

        TransferReplicationService.TransferTargetDto verified = service.verifyTarget(target.getId());

        assertEquals(TransferTarget.VerificationStatus.VERIFIED, verified.verificationStatus());
        assertEquals("Verified remote Athena transfer receiver folder: Contracts", verified.verificationMessage());
        assertEquals("athena", verified.remoteRepositoryId());
        assertNotNull(verified.lastVerifiedAt());
    }

    @Test
    @DisplayName("createTarget supports ATHENA_HTTP transport targets")
    void createTargetSupportsRemoteAthenaTarget() {
        UUID remoteFolderId = UUID.randomUUID();
        when(transferTargetRepository.existsByNameIgnoreCase("remote-athena")).thenReturn(false);

        TransferReplicationService.TransferTargetDto target = service.createTarget(
            new TransferReplicationService.TransferTargetMutationRequest(
                "remote-athena",
                "Remote transfer target",
                TransferTarget.TransportType.ATHENA_HTTP,
                remoteFolderId,
                "https://remote.example",
                "/api/v1",
                TransferTarget.AuthType.BEARER,
                null,
                "secret-token",
                true
            )
        );

        assertEquals(TransferTarget.TransportType.ATHENA_HTTP, target.transportType());
        assertEquals("https://remote.example", target.endpointUrl());
        assertEquals("/api/v1", target.endpointPath());
        assertEquals(TransferTarget.AuthType.BEARER, target.authType());
        assertTrue(target.authSecretConfigured());
        assertNull(target.targetFolderName());
    }

    @Test
    @DisplayName("deleteTarget rejects targets still referenced by a definition")
    void deleteTargetRejectsReferencedTarget() {
        TransferTarget target = new TransferTarget();
        target.setId(UUID.randomUUID());
        target.setName("loopback");
        target.setTransportType(TransferTarget.TransportType.LOOPBACK);
        target.setTargetFolderId(UUID.randomUUID());
        storedTargets.put(target.getId(), target);

        ReplicationDefinition definition = new ReplicationDefinition();
        definition.setId(UUID.randomUUID());
        definition.setTransferTargetId(target.getId());
        storedDefinitions.put(definition.getId(), definition);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.deleteTarget(target.getId()));
        assertTrue(ex.getMessage().contains("still referenced"));
    }

    @Test
    @DisplayName("hasActiveJob uses indexed DB query, not in-memory findAll scan")
    void hasActiveJobUsesDbQueryNotFindAllScan() {
        UUID targetId = UUID.randomUUID();
        UUID sourceNodeId = UUID.randomUUID();
        TransferTarget target = new TransferTarget();
        target.setId(targetId);
        target.setName("remote");
        target.setTransportType(TransferTarget.TransportType.ATHENA_HTTP);
        target.setTargetFolderId(UUID.randomUUID());
        target.setEndpointUrl("https://remote.example");
        target.setEnabled(true);
        storedTargets.put(targetId, target);

        ReplicationDefinition dueDefinition = new ReplicationDefinition();
        dueDefinition.setId(UUID.randomUUID());
        dueDefinition.setName("nightly");
        dueDefinition.setSourceNodeId(sourceNodeId);
        dueDefinition.setTransferTargetId(targetId);
        dueDefinition.setEnabled(true);
        dueDefinition.setCronExpression("0 0 2 * * *");
        dueDefinition.setScheduleTimezone("UTC");
        dueDefinition.setNextRunAt(LocalDateTime.now().minusMinutes(1));
        storedDefinitions.put(dueDefinition.getId(), dueDefinition);

        // Place a RUNNING job for this definition
        ReplicationJob activeJob = new ReplicationJob();
        activeJob.setId(UUID.randomUUID());
        activeJob.setDefinitionId(dueDefinition.getId());
        activeJob.setTransferTargetId(targetId);
        activeJob.setSourceNodeId(sourceNodeId);
        activeJob.setUserId("alice");
        activeJob.setStatus(ReplicationJob.ReplicationJobStatus.RUNNING);
        activeJob.setCreatedAt(LocalDateTime.now());
        storedJobs.put(activeJob.getId(), activeJob);

        // Run the scheduler — it should skip because of the active job
        TransferReplicationService.ScheduledReplicationBatchDto result = service.runScheduledDefinitions();
        assertEquals(1, result.skippedDefinitions());

        // Verify the DB existence query was used
        verify(replicationJobRepository).existsByDefinitionIdAndStatusIn(
            eq(dueDefinition.getId()),
            eq(List.of(ReplicationJob.ReplicationJobStatus.PENDING, ReplicationJob.ReplicationJobStatus.RUNNING))
        );
    }

    @Test
    @DisplayName("deleteTarget uses indexed DB query for reference check, not findAll scan")
    void deleteTargetUsesDbQueryNotFindAllScan() {
        TransferTarget target = new TransferTarget();
        target.setId(UUID.randomUUID());
        target.setName("loopback");
        target.setTransportType(TransferTarget.TransportType.LOOPBACK);
        target.setTargetFolderId(UUID.randomUUID());
        storedTargets.put(target.getId(), target);

        // No definitions reference this target
        service.deleteTarget(target.getId());

        verify(replicationDefinitionRepository).existsByTransferTargetId(target.getId());
        verify(transferTargetRepository).delete(target);
    }

    @Test
    @DisplayName("successful replication sets lastSuccessfulSyncAt on definition")
    void successfulReplicationSetsLastSuccessfulSyncAt() {
        UUID targetFolderId = UUID.randomUUID();
        UUID sourceNodeId = UUID.randomUUID();

        TransferTarget target = new TransferTarget();
        target.setId(UUID.randomUUID());
        target.setName("loopback");
        target.setTransportType(TransferTarget.TransportType.LOOPBACK);
        target.setTargetFolderId(targetFolderId);
        target.setEnabled(true);
        target.setCreatedAt(LocalDateTime.now());
        storedTargets.put(target.getId(), target);

        ReplicationDefinition definition = new ReplicationDefinition();
        definition.setId(UUID.randomUUID());
        definition.setName("contracts");
        definition.setSourceNodeId(sourceNodeId);
        definition.setTransferTargetId(target.getId());
        definition.setIncludeChildren(true);
        definition.setEnabled(true);
        definition.setCreatedAt(LocalDateTime.now());
        storedDefinitions.put(definition.getId(), definition);

        assertNull(definition.getLastSuccessfulSyncAt(), "Should be null before first run");

        Node source = node(sourceNodeId, "Contracts");
        when(folderService.getFolder(targetFolderId)).thenReturn(folder(targetFolderId, "Outbound"));
        when(nodeService.getNode(sourceNodeId)).thenReturn(source);
        when(loopbackTransferClient.replicate(eq(target), eq(source), eq(true), eq(ReplicationDefinition.ConflictPolicy.RENAME), any()))
            .thenReturn(new TransferClient.TransferExecutionResult(UUID.randomUUID(), "OK"));

        service.runDefinition(definition.getId());

        assertNotNull(definition.getLastSuccessfulSyncAt(), "Should be set after successful run");
    }

    @Test
    @DisplayName("failed replication does NOT update lastSuccessfulSyncAt")
    void failedReplicationDoesNotUpdateLastSuccessfulSyncAt() {
        UUID targetFolderId = UUID.randomUUID();
        UUID sourceNodeId = UUID.randomUUID();

        TransferTarget target = new TransferTarget();
        target.setId(UUID.randomUUID());
        target.setName("loopback");
        target.setTransportType(TransferTarget.TransportType.LOOPBACK);
        target.setTargetFolderId(targetFolderId);
        target.setEnabled(true);
        target.setCreatedAt(LocalDateTime.now());
        storedTargets.put(target.getId(), target);

        ReplicationDefinition definition = new ReplicationDefinition();
        definition.setId(UUID.randomUUID());
        definition.setName("contracts");
        definition.setSourceNodeId(sourceNodeId);
        definition.setTransferTargetId(target.getId());
        definition.setIncludeChildren(true);
        definition.setEnabled(true);
        definition.setCreatedAt(LocalDateTime.now());
        storedDefinitions.put(definition.getId(), definition);

        Node source = node(sourceNodeId, "Contracts");
        when(nodeService.getNode(sourceNodeId)).thenReturn(source);
        when(loopbackTransferClient.replicate(eq(target), eq(source), eq(true), eq(ReplicationDefinition.ConflictPolicy.RENAME), any()))
            .thenThrow(new RuntimeException("Connection refused"));

        service.runDefinition(definition.getId());

        assertNull(definition.getLastSuccessfulSyncAt(), "Should remain null after failed run");
    }

    @Test
    @DisplayName("processJob passes lastSuccessfulSyncAt watermark to transfer client")
    void processJobPassesWatermarkToTransferClient() {
        UUID targetFolderId = UUID.randomUUID();
        UUID sourceNodeId = UUID.randomUUID();
        LocalDateTime previousSync = LocalDateTime.now().minusHours(6);

        TransferTarget target = new TransferTarget();
        target.setId(UUID.randomUUID());
        target.setName("loopback");
        target.setTransportType(TransferTarget.TransportType.LOOPBACK);
        target.setTargetFolderId(targetFolderId);
        target.setEnabled(true);
        target.setCreatedAt(LocalDateTime.now());
        storedTargets.put(target.getId(), target);

        ReplicationDefinition definition = new ReplicationDefinition();
        definition.setId(UUID.randomUUID());
        definition.setName("contracts");
        definition.setSourceNodeId(sourceNodeId);
        definition.setTransferTargetId(target.getId());
        definition.setIncludeChildren(true);
        definition.setEnabled(true);
        definition.setLastSuccessfulSyncAt(previousSync);
        definition.setCreatedAt(LocalDateTime.now());
        storedDefinitions.put(definition.getId(), definition);

        Node source = node(sourceNodeId, "Contracts");
        when(folderService.getFolder(targetFolderId)).thenReturn(folder(targetFolderId, "Outbound"));
        when(nodeService.getNode(sourceNodeId)).thenReturn(source);
        when(loopbackTransferClient.replicate(eq(target), eq(source), eq(true), eq(ReplicationDefinition.ConflictPolicy.RENAME), eq(previousSync)))
            .thenReturn(new TransferClient.TransferExecutionResult(UUID.randomUUID(), "Incremental sync"));

        TransferReplicationService.ReplicationJobDto job = service.runDefinition(definition.getId());

        assertEquals(ReplicationJob.ReplicationJobStatus.COMPLETED, job.status());
        verify(loopbackTransferClient).replicate(eq(target), eq(source), eq(true), eq(ReplicationDefinition.ConflictPolicy.RENAME), eq(previousSync));
    }

    private Folder folder(UUID id, String name) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setPath("/" + name);
        return folder;
    }

    private Node node(UUID id, String name) {
        Node node = new Folder();
        node.setId(id);
        node.setName(name);
        node.setPath("/" + name);
        return node;
    }
}
