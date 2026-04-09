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
        lenient().when(replicationJobRepository.findAllByOrderByCreatedAtDesc(any())).thenAnswer(invocation -> {
            PageRequest pageable = invocation.getArgument(0);
            List<ReplicationJob> jobs = storedJobs.values().stream()
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .toList();
            return new PageImpl<>(jobs, pageable, jobs.size());
        });
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
        when(loopbackTransferClient.replicate(target, source, true))
            .thenReturn(new TransferClient.TransferExecutionResult(copiedNodeId, "Loopback replication completed"));

        TransferReplicationService.ReplicationJobDto job = service.runDefinition(definition.getId());

        assertEquals(ReplicationJob.ReplicationJobStatus.COMPLETED, job.status());
        assertEquals(copiedNodeId, job.copiedNodeId());
        assertNotNull(storedDefinitions.get(definition.getId()).getLastRunAt());
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
            .thenReturn(new TransferClient.TransferVerificationResult("Verified remote Athena transfer receiver folder: Contracts"));

        TransferReplicationService.TransferTargetDto verified = service.verifyTarget(target.getId());

        assertEquals(TransferTarget.VerificationStatus.VERIFIED, verified.verificationStatus());
        assertEquals("Verified remote Athena transfer receiver folder: Contracts", verified.verificationMessage());
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
