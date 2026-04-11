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
import com.ecm.core.service.transfer.TransferClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplicationJobEntryReportTest {

    @Mock private TransferTargetRepository transferTargetRepository;
    @Mock private ReplicationDefinitionRepository replicationDefinitionRepository;
    @Mock private ReplicationJobRepository replicationJobRepository;
    @Mock private FolderService folderService;
    @Mock private NodeService nodeService;
    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;
    @Mock private TransferClient loopbackClient;

    private TransferReplicationService service;
    private Map<UUID, TransferTarget> storedTargets;
    private Map<UUID, ReplicationDefinition> storedDefinitions;
    private Map<UUID, ReplicationJob> storedJobs;

    @BeforeEach
    void setUp() {
        lenient().when(loopbackClient.transportType()).thenReturn(TransferTarget.TransportType.LOOPBACK);
        service = new TransferReplicationService(
            transferTargetRepository,
            replicationDefinitionRepository,
            replicationJobRepository,
            folderService,
            nodeService,
            nodeRepository,
            securityService,
            List.of(loopbackClient),
            Runnable::run
        );
        storedTargets = new LinkedHashMap<>();
        storedDefinitions = new LinkedHashMap<>();
        storedJobs = new LinkedHashMap<>();

        lenient().when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        lenient().when(securityService.getCurrentUser()).thenReturn("alice");

        lenient().when(transferTargetRepository.save(any(TransferTarget.class))).thenAnswer(inv -> {
            TransferTarget t = inv.getArgument(0);
            if (t.getId() == null) { t.setId(UUID.randomUUID()); t.setCreatedAt(LocalDateTime.now()); }
            t.setUpdatedAt(LocalDateTime.now());
            storedTargets.put(t.getId(), t);
            return t;
        });
        lenient().when(replicationDefinitionRepository.save(any(ReplicationDefinition.class))).thenAnswer(inv -> {
            ReplicationDefinition d = inv.getArgument(0);
            if (d.getId() == null) { d.setId(UUID.randomUUID()); d.setCreatedAt(LocalDateTime.now()); }
            d.setUpdatedAt(LocalDateTime.now());
            storedDefinitions.put(d.getId(), d);
            return d;
        });
        lenient().when(replicationJobRepository.save(any(ReplicationJob.class))).thenAnswer(inv -> {
            ReplicationJob j = inv.getArgument(0);
            if (j.getId() == null) { j.setId(UUID.randomUUID()); j.setCreatedAt(LocalDateTime.now()); }
            j.setUpdatedAt(LocalDateTime.now());
            storedJobs.put(j.getId(), j);
            return j;
        });
        lenient().when(transferTargetRepository.findById(any(UUID.class))).thenAnswer(inv ->
            Optional.ofNullable(storedTargets.get(inv.getArgument(0))));
        lenient().when(replicationDefinitionRepository.findById(any(UUID.class))).thenAnswer(inv ->
            Optional.ofNullable(storedDefinitions.get(inv.getArgument(0))));
        lenient().when(replicationJobRepository.findById(any(UUID.class))).thenAnswer(inv ->
            Optional.ofNullable(storedJobs.get(inv.getArgument(0))));
        lenient().when(replicationJobRepository.existsByDefinitionIdAndStatusIn(any(UUID.class), anyCollection()))
            .thenReturn(false);
        lenient().when(nodeRepository.findByParentIdAndName(any(), anyString())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("small batch: entryReport stores all entries with correct summary")
    void smallBatchStoresAllEntriesWithCorrectSummary() {
        setupTargetAndDefinition();
        ReplicationDefinition definition = storedDefinitions.values().iterator().next();
        TransferTarget target = storedTargets.values().iterator().next();
        UUID sourceNodeId = definition.getSourceNodeId();
        Node source = folder(sourceNodeId, "Contracts");

        List<TransferClient.TransferExecutionEntry> entries = List.of(
            entry(UUID.randomUUID(), "CREATED", "Created folder A"),
            entry(UUID.randomUUID(), "CREATED", "Created doc B"),
            entry(UUID.randomUUID(), "SKIPPED", "Skipped doc C")
        );

        when(nodeService.getNode(sourceNodeId)).thenReturn(source);
        when(folderService.getFolder(target.getTargetFolderId())).thenReturn(folder(target.getTargetFolderId(), "Outbound"));
        when(loopbackClient.replicate(eq(target), eq(source), eq(true), eq(ReplicationDefinition.ConflictPolicy.RENAME), any()))
            .thenReturn(new TransferClient.TransferExecutionResult(UUID.randomUUID(), "OK", entries));

        TransferReplicationService.ReplicationJobDto job = service.runDefinition(definition.getId());

        assertNotNull(job.entryReport());
        assertEquals(3, job.entryReport().get("totalEntries"));
        assertEquals(3L, job.entryReport().get("successCount"));
        assertEquals(0L, job.entryReport().get("failureCount"));
        assertFalse(job.reportTruncated());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> storedEntries = (List<Map<String, Object>>) job.entryReport().get("entries");
        assertEquals(3, storedEntries.size());
    }

    @Test
    @DisplayName("report truncates at 5000 entries and sets reportTruncated flag")
    void reportTruncatesAtLimitAndSetsFlag() {
        setupTargetAndDefinition();
        ReplicationDefinition definition = storedDefinitions.values().iterator().next();
        TransferTarget target = storedTargets.values().iterator().next();
        UUID sourceNodeId = definition.getSourceNodeId();
        Node source = folder(sourceNodeId, "BigFolder");

        List<TransferClient.TransferExecutionEntry> entries = IntStream.range(0, 5500)
            .mapToObj(i -> entry(UUID.randomUUID(), "CREATED", "Entry " + i))
            .toList();

        when(nodeService.getNode(sourceNodeId)).thenReturn(source);
        when(folderService.getFolder(target.getTargetFolderId())).thenReturn(folder(target.getTargetFolderId(), "Outbound"));
        when(loopbackClient.replicate(eq(target), eq(source), eq(true), eq(ReplicationDefinition.ConflictPolicy.RENAME), any()))
            .thenReturn(new TransferClient.TransferExecutionResult(UUID.randomUUID(), "OK", entries));

        TransferReplicationService.ReplicationJobDto job = service.runDefinition(definition.getId());

        assertTrue(job.reportTruncated(), "reportTruncated must be true when entries exceed 5000");
        assertEquals(5500, job.entryReport().get("totalEntries"), "totalEntries reflects actual count, not truncated");
        assertEquals(5500L, job.entryReport().get("successCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> storedEntries = (List<Map<String, Object>>) job.entryReport().get("entries");
        assertEquals(5000, storedEntries.size(), "Stored entries capped at 5000");
    }

    @Test
    @DisplayName("failure report records correct failure count in summary")
    void failureReportRecordsCorrectFailureCount() {
        setupTargetAndDefinition();
        ReplicationDefinition definition = storedDefinitions.values().iterator().next();
        TransferTarget target = storedTargets.values().iterator().next();
        UUID sourceNodeId = definition.getSourceNodeId();
        Node source = folder(sourceNodeId, "Contracts");

        when(nodeService.getNode(sourceNodeId)).thenReturn(source);
        when(loopbackClient.replicate(eq(target), eq(source), eq(true), eq(ReplicationDefinition.ConflictPolicy.RENAME), any()))
            .thenThrow(new RuntimeException("Connection refused"));

        TransferReplicationService.ReplicationJobDto job = service.runDefinition(definition.getId());

        assertEquals(ReplicationJobStatus.FAILED, job.status());
        assertNotNull(job.entryReport());
        assertEquals(1, job.entryReport().get("totalEntries"));
        assertEquals(0L, job.entryReport().get("successCount"));
        assertEquals(1L, job.entryReport().get("failureCount"));
        assertFalse(job.reportTruncated());
    }

    private void setupTargetAndDefinition() {
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
    }

    private TransferClient.TransferExecutionEntry entry(UUID targetNodeId, String action, String message) {
        LocalDateTime now = LocalDateTime.now();
        return new TransferClient.TransferExecutionEntry(
            UUID.randomUUID(), "/source/path", "FOLDER", targetNodeId, action, message, now, now
        );
    }

    private Folder folder(UUID id, String name) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setPath("/" + name);
        return folder;
    }
}
