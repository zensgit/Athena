package com.ecm.core.service;

import com.ecm.core.entity.DispositionActionExecution;
import com.ecm.core.entity.DispositionActionExecution.ActionType;
import com.ecm.core.entity.DispositionActionExecution.ExecutionStatus;
import com.ecm.core.entity.DispositionSchedule;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.exception.IllegalOperationException;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.repository.ArchivePolicyRepository;
import com.ecm.core.repository.DispositionActionExecutionRepository;
import com.ecm.core.repository.DispositionScheduleRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DispositionScheduleService {

    private static final String SYSTEM_ACTOR = "system:disposition-schedule";

    private final DispositionScheduleRepository dispositionScheduleRepository;
    private final DispositionActionExecutionRepository dispositionActionExecutionRepository;
    private final FolderRepository folderRepository;
    private final NodeRepository nodeRepository;
    private final ArchivePolicyRepository archivePolicyRepository;
    private final SecurityService securityService;
    private final ContentArchiveService contentArchiveService;
    private final DispositionActionExecutorService dispositionActionExecutorService;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;
    private final LegalHoldService legalHoldService;
    private final RecordsManagementService recordsManagementService;

    @Transactional(readOnly = true)
    public Optional<DispositionScheduleDto> getSchedule(UUID folderId) {
        requireAdmin();
        loadLiveFolder(folderId);
        return dispositionScheduleRepository.findByFolderId(folderId)
            .filter(schedule -> !schedule.isDeleted())
            .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<DispositionScheduleDto> listSchedules() {
        requireAdmin();
        String tenantRootPath = tenantWorkspaceScopeService.resolveCurrentTenantRootPath();
        return dispositionScheduleRepository.findByDeletedFalseOrderByCreatedDateDesc().stream()
            .filter(schedule -> tenantRootPath == null
                || (schedule.getFolder() != null
                && tenantWorkspaceScopeService.isPathVisible(schedule.getFolder().getPath(), tenantRootPath)))
            .filter(schedule -> schedule.getFolder() != null && recordsManagementService.isFilePlanFolder(schedule.getFolder()))
            .map(this::toDto)
            .toList();
    }

    public DispositionScheduleDto upsertSchedule(UUID folderId, DispositionScheduleUpsertRequest request) {
        requireAdmin();
        Folder folder = loadLiveFolder(folderId);
        validateRequest(request);
        boolean enabled = request.enabled() == null || request.enabled();
        enforceArchivePolicyCompatibility(folderId, enabled);

        DispositionSchedule schedule = dispositionScheduleRepository.findByFolderId(folderId)
            .orElseGet(DispositionSchedule::new);

        schedule.setFolder(folder);
        schedule.setEnabled(enabled);
        schedule.setIncludeSubfolders(request.includeSubfolders() == null || request.includeSubfolders());
        schedule.setCutoffAfterDays(request.cutoffAfterDays());
        schedule.setArchiveAfterCutoffDays(request.archiveAfterCutoffDays());
        schedule.setDestroyAfterArchiveDays(request.destroyAfterArchiveDays());
        schedule.setArchiveStorageTier(request.archiveStorageTier() != null ? request.archiveStorageTier() : Node.ArchiveStoreTier.COLD);
        schedule.setMaxCandidatesPerAction(request.maxCandidatesPerAction() != null ? request.maxCandidatesPerAction() : 100);
        schedule.setDeleted(false);
        schedule.setDeletedAt(null);
        schedule.setDeletedBy(null);
        schedule.setLastError(null);

        return toDto(dispositionScheduleRepository.save(schedule));
    }

    public void deleteSchedule(UUID folderId) {
        requireAdmin();
        DispositionSchedule schedule = dispositionScheduleRepository.findByFolderId(folderId)
            .filter(existing -> !existing.isDeleted())
            .orElseThrow(() -> new NoSuchElementException("Disposition schedule not found for folder: " + folderId));
        schedule.setEnabled(false);
        schedule.setDeleted(true);
        schedule.setDeletedAt(LocalDateTime.now());
        schedule.setDeletedBy(securityService.getCurrentUser());
        dispositionScheduleRepository.save(schedule);
    }

    public DispositionDryRunDto dryRunSchedule(UUID folderId, DispositionScheduleUpsertRequest request) {
        requireAdmin();
        Folder folder = loadLiveFolder(folderId);
        ScheduleSnapshot snapshot = resolveSnapshot(folder, request, true);
        DispositionDryRunDto result = buildDryRun(snapshot);

        dispositionScheduleRepository.findByFolderId(folderId)
            .filter(existing -> !existing.isDeleted())
            .ifPresent(existing -> {
                existing.setLastDryRunAt(LocalDateTime.now());
                existing.setLastError(null);
                dispositionScheduleRepository.save(existing);
            });

        return result;
    }

    public DispositionExecutionDto executeSchedule(UUID folderId) {
        requireAdmin();
        Folder folder = loadLiveFolder(folderId);
        DispositionSchedule schedule = dispositionScheduleRepository.findByFolderId(folderId)
            .filter(existing -> !existing.isDeleted())
            .orElseThrow(() -> new NoSuchElementException("Disposition schedule not found for folder: " + folderId));
        return executeSchedule(schedule, folder, securityService.getCurrentUser());
    }

    public DispositionBatchExecutionDto runScheduledSchedules() {
        List<DispositionExecutionDto> results = new ArrayList<>();
        for (DispositionSchedule schedule : dispositionScheduleRepository.findByEnabledTrueAndDeletedFalseOrderByCreatedDateAsc()) {
            Folder folder = schedule.getFolder();
            if (folder == null || folder.isDeleted() || folder.getArchiveStatus() != Node.ArchiveStatus.LIVE) {
                continue;
            }
            if (!tenantWorkspaceScopeService.isPathVisible(folder.getPath())) {
                continue;
            }
            try {
                results.add(executeSchedule(schedule, folder, SYSTEM_ACTOR));
            } catch (Exception ex) {
                log.warn("Disposition schedule run failed for folder {}: {}", folder.getId(), ex.getMessage());
                schedule.setLastExecutedAt(LocalDateTime.now());
                schedule.setLastError(ex.getMessage());
                dispositionScheduleRepository.save(schedule);
                results.add(new DispositionExecutionDto(
                    folder.getId(),
                    folder.getName(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    1,
                    0,
                    List.of(ex.getMessage()),
                    ex.getMessage()
                ));
            }
        }

        int executedSchedules = results.size();
        int cutoffCount = results.stream().mapToInt(DispositionExecutionDto::cutoffCount).sum();
        int archivedNodeCount = results.stream().mapToInt(DispositionExecutionDto::archivedNodeCount).sum();
        int destroyedNodeCount = results.stream().mapToInt(DispositionExecutionDto::destroyedNodeCount).sum();
        int blockedCount = results.stream().mapToInt(DispositionExecutionDto::blockedCount).sum();
        int failureCount = results.stream().mapToInt(DispositionExecutionDto::failureCount).sum();

        return new DispositionBatchExecutionDto(
            executedSchedules,
            cutoffCount,
            archivedNodeCount,
            destroyedNodeCount,
            blockedCount,
            failureCount,
            results
        );
    }

    @Transactional(readOnly = true)
    public Page<DispositionActionExecutionDto> listExecutions(UUID folderId, Pageable pageable) {
        requireAdmin();
        DispositionSchedule schedule = dispositionScheduleRepository.findByFolderId(folderId)
            .filter(existing -> !existing.isDeleted())
            .orElseThrow(() -> new NoSuchElementException("Disposition schedule not found for folder: " + folderId));
        loadLiveFolder(folderId);
        return dispositionActionExecutionRepository.findByScheduleIdOrderByExecutedAtDesc(schedule.getId(), pageable)
            .map(this::toExecutionDto);
    }

    private DispositionExecutionDto executeSchedule(DispositionSchedule schedule, Folder folder, String actor) {
        ScheduleSnapshot snapshot = resolveSnapshot(folder, null, false);
        ActionLedger ledger = loadActionLedger(schedule.getId());
        List<String> failures = new ArrayList<>();

        int cutoffCount = executeCutoff(snapshot, ledger, actor, failures);
        ArchiveRunSummary archiveSummary = executeArchive(snapshot, ledger, actor, failures);
        DestroyRunSummary destroySummary = executeDestroy(snapshot, ledger, actor, failures);

        schedule.setLastExecutedAt(LocalDateTime.now());
        schedule.setLastError(failures.isEmpty() ? null : String.join("; ", failures));
        dispositionScheduleRepository.save(schedule);

        return new DispositionExecutionDto(
            folder.getId(),
            folder.getName(),
            cutoffCount,
            archiveSummary.archiveCandidateCount(),
            archiveSummary.archivedNodeCount(),
            destroySummary.destroyCandidateCount(),
            destroySummary.destroyedNodeCount(),
            failures.size(),
            destroySummary.blockedCount(),
            failures,
            null
        );
    }

    private int executeCutoff(ScheduleSnapshot snapshot, ActionLedger ledger, String actor, List<String> failures) {
        int processed = 0;
        for (DispositionCandidate candidate : selectCandidates(snapshot, ledger, ActionType.CUTOFF, false)) {
            try {
                LocalDateTime executedAt = LocalDateTime.now();
                recordExecution(snapshot.schedule(), candidate, ActionType.CUTOFF, ExecutionStatus.SUCCESS, actor, 1, null, executedAt);
                ledger.latestSuccessByAction().get(ActionType.CUTOFF).put(candidate.nodeId(), executedAt);
                processed++;
            } catch (Exception ex) {
                failures.add(candidate.nodeName() + " cutoff: " + ex.getMessage());
                recordExecution(snapshot.schedule(), candidate, ActionType.CUTOFF, ExecutionStatus.FAILED, actor, 0, ex.getMessage(), LocalDateTime.now());
            }
        }
        return processed;
    }

    private ArchiveRunSummary executeArchive(ScheduleSnapshot snapshot, ActionLedger ledger, String actor, List<String> failures) {
        int archivedNodeCount = 0;
        List<DispositionCandidate> archiveCandidates = selectCandidates(snapshot, ledger, ActionType.ARCHIVE, false);
        for (DispositionCandidate candidate : archiveCandidates) {
            try {
                ContentArchiveService.ArchiveMutationDto result = contentArchiveService.archiveNodeByPolicy(
                    candidate.nodeId(),
                    snapshot.archiveStorageTier(),
                    actor
                );
                LocalDateTime executedAt = LocalDateTime.now();
                recordExecution(
                    snapshot.schedule(),
                    candidate,
                    ActionType.ARCHIVE,
                    ExecutionStatus.SUCCESS,
                    actor,
                    result.affectedNodeCount(),
                    "archiveStoreTier=" + snapshot.archiveStorageTier().name(),
                    executedAt
                );
                ledger.latestSuccessByAction().get(ActionType.ARCHIVE).put(candidate.nodeId(), executedAt);
                archivedNodeCount += result.affectedNodeCount();
            } catch (Exception ex) {
                failures.add(candidate.nodeName() + " archive: " + ex.getMessage());
                recordExecution(snapshot.schedule(), candidate, ActionType.ARCHIVE, ExecutionStatus.FAILED, actor, 0, ex.getMessage(), LocalDateTime.now());
            }
        }
        return new ArchiveRunSummary(archiveCandidates.size(), archivedNodeCount);
    }

    private DestroyRunSummary executeDestroy(ScheduleSnapshot snapshot, ActionLedger ledger, String actor, List<String> failures) {
        int blockedCount = 0;
        int destroyedNodeCount = 0;
        List<DispositionCandidate> destroyCandidates = selectCandidates(snapshot, ledger, ActionType.DESTROY, true);
        for (DispositionCandidate candidate : destroyCandidates) {
            try {
                DispositionActionExecutorService.DestroyMutationDto result = dispositionActionExecutorService
                    .destroyNodeByDisposition(candidate.nodeId(), actor);
                LocalDateTime executedAt = LocalDateTime.now();
                recordExecution(
                    snapshot.schedule(),
                    candidate,
                    ActionType.DESTROY,
                    ExecutionStatus.SUCCESS,
                    actor,
                    result.affectedNodeCount(),
                    null,
                    executedAt
                );
                ledger.latestSuccessByAction().get(ActionType.DESTROY).put(candidate.nodeId(), executedAt);
                destroyedNodeCount += result.affectedNodeCount();
            } catch (IllegalOperationException ex) {
                blockedCount++;
                recordExecution(snapshot.schedule(), candidate, ActionType.DESTROY, ExecutionStatus.BLOCKED, actor, 0, ex.getMessage(), LocalDateTime.now());
            } catch (Exception ex) {
                failures.add(candidate.nodeName() + " destroy: " + ex.getMessage());
                recordExecution(snapshot.schedule(), candidate, ActionType.DESTROY, ExecutionStatus.FAILED, actor, 0, ex.getMessage(), LocalDateTime.now());
            }
        }
        return new DestroyRunSummary(destroyCandidates.size(), destroyedNodeCount, blockedCount);
    }

    private DispositionDryRunDto buildDryRun(ScheduleSnapshot snapshot) {
        ActionLedger ledger = loadActionLedger(snapshot.schedule().getId());
        List<DispositionCandidateDto> candidates = new ArrayList<>();
        candidates.addAll(selectCandidates(snapshot, ledger, ActionType.CUTOFF, false).stream().map(this::toCandidateDto).toList());
        candidates.addAll(selectCandidates(snapshot, ledger, ActionType.ARCHIVE, false).stream().map(this::toCandidateDto).toList());
        candidates.addAll(selectCandidates(snapshot, ledger, ActionType.DESTROY, true).stream().map(this::toCandidateDto).toList());
        candidates.sort(Comparator
            .comparing((DispositionCandidateDto candidate) -> actionOrder(ActionType.valueOf(candidate.actionType())))
            .thenComparing(DispositionCandidateDto::path, Comparator.nullsLast(String::compareToIgnoreCase)));

        int cutoffCount = (int) candidates.stream().filter(candidate -> candidate.actionType().equals(ActionType.CUTOFF.name())).count();
        int archiveCount = (int) candidates.stream().filter(candidate -> candidate.actionType().equals(ActionType.ARCHIVE.name())).count();
        int destroyCount = (int) candidates.stream().filter(candidate -> candidate.actionType().equals(ActionType.DESTROY.name())).count();

        return new DispositionDryRunDto(
            snapshot.folderId(),
            snapshot.folderName(),
            snapshot.includeSubfolders(),
            snapshot.archiveStorageTier(),
            snapshot.maxCandidatesPerAction(),
            cutoffCount,
            archiveCount,
            destroyCount,
            candidates
        );
    }

    private List<DispositionCandidate> selectCandidates(
        ScheduleSnapshot snapshot,
        ActionLedger ledger,
        ActionType actionType,
        boolean evaluateDestroyHolds
    ) {
        List<Node> rawCandidates = snapshot.includeSubfolders()
            ? nodeRepository.findByPathPrefix(snapshot.folderPath() + "/")
            : nodeRepository.findByParentIdAndDeletedFalse(snapshot.folderId());

        List<DispositionCandidate> eligible = rawCandidates.stream()
            .filter(node -> !node.isDeleted())
            .filter(Document.class::isInstance)
            .filter(recordsManagementService::isDeclaredRecord)
            .filter(node -> isCandidateVisible(node))
            .filter(node -> isEligibleForAction(node, actionType, snapshot, ledger))
            .map(node -> toCandidate(node, actionType, snapshot, ledger, evaluateDestroyHolds))
            .sorted(Comparator
                .comparingInt((DispositionCandidate candidate) -> depth(candidate.path()))
                .thenComparing(DispositionCandidate::path, Comparator.nullsLast(String::compareToIgnoreCase)))
            .toList();

        List<DispositionCandidate> reduced = new ArrayList<>();
        for (DispositionCandidate candidate : eligible) {
            boolean nestedUnderSelectedFolder = reduced.stream()
                .filter(existing -> "FOLDER".equals(existing.nodeType()))
                .map(DispositionCandidate::path)
                .filter(path -> path != null && !path.isBlank())
                .anyMatch(path -> candidate.path() != null
                    && (candidate.path().equals(path) || candidate.path().startsWith(path + "/")));
            if (!nestedUnderSelectedFolder) {
                reduced.add(candidate);
            }
            if (reduced.size() >= snapshot.maxCandidatesPerAction()) {
                break;
            }
        }
        return reduced;
    }

    private boolean isEligibleForAction(Node node, ActionType actionType, ScheduleSnapshot snapshot, ActionLedger ledger) {
        return switch (actionType) {
            case CUTOFF -> isEligibleForCutoff(node, snapshot, ledger);
            case ARCHIVE -> isEligibleForArchive(node, snapshot, ledger);
            case DESTROY -> isEligibleForDestroy(node, snapshot, ledger);
        };
    }

    private boolean isEligibleForCutoff(Node node, ScheduleSnapshot snapshot, ActionLedger ledger) {
        if (node.getArchiveStatus() != Node.ArchiveStatus.LIVE) {
            return false;
        }
        if (ledger.latestSuccessByAction().get(ActionType.CUTOFF).containsKey(node.getId())) {
            return false;
        }
        LocalDateTime activityDate = resolveActivityDate(node);
        return activityDate != null && !activityDate.plusDays(snapshot.cutoffAfterDays()).isAfter(LocalDateTime.now());
    }

    private boolean isEligibleForArchive(Node node, ScheduleSnapshot snapshot, ActionLedger ledger) {
        if (snapshot.archiveAfterCutoffDays() == null || node.getArchiveStatus() != Node.ArchiveStatus.LIVE) {
            return false;
        }
        if (ledger.latestSuccessByAction().get(ActionType.ARCHIVE).containsKey(node.getId())) {
            return false;
        }
        LocalDateTime cutoffAt = ledger.latestSuccessByAction().get(ActionType.CUTOFF).get(node.getId());
        return cutoffAt != null && !cutoffAt.plusDays(snapshot.archiveAfterCutoffDays()).isAfter(LocalDateTime.now());
    }

    private boolean isEligibleForDestroy(Node node, ScheduleSnapshot snapshot, ActionLedger ledger) {
        if (snapshot.destroyAfterArchiveDays() == null || node.getArchiveStatus() != Node.ArchiveStatus.ARCHIVED) {
            return false;
        }
        if (ledger.latestSuccessByAction().get(ActionType.DESTROY).containsKey(node.getId())) {
            return false;
        }
        LocalDateTime archiveAt = ledger.latestSuccessByAction().get(ActionType.ARCHIVE).get(node.getId());
        return archiveAt != null && !archiveAt.plusDays(snapshot.destroyAfterArchiveDays()).isAfter(LocalDateTime.now());
    }

    private DispositionCandidate toCandidate(
        Node node,
        ActionType actionType,
        ScheduleSnapshot snapshot,
        ActionLedger ledger,
        boolean evaluateDestroyHolds
    ) {
        LocalDateTime eligibleAt = switch (actionType) {
            case CUTOFF -> resolveActivityDate(node).plusDays(snapshot.cutoffAfterDays());
            case ARCHIVE -> ledger.latestSuccessByAction().get(ActionType.CUTOFF).get(node.getId())
                .plusDays(snapshot.archiveAfterCutoffDays());
            case DESTROY -> ledger.latestSuccessByAction().get(ActionType.ARCHIVE).get(node.getId())
                .plusDays(snapshot.destroyAfterArchiveDays());
        };
        String holdReason = null;
        if (evaluateDestroyHolds) {
            var blockingHolds = legalHoldService.findBlockingActiveHolds(node);
            if (!blockingHolds.isEmpty()) {
                holdReason = blockingHolds.stream().map(LegalHoldService.BlockingHoldDto::holdName).distinct().sorted().reduce((l, r) -> l + ", " + r).orElse(null);
            }
        }
        return new DispositionCandidate(
            node.getId(),
            node.getName(),
            node.getNodeType().name(),
            node.getPath(),
            actionType,
            eligibleAt,
            holdReason
        );
    }

    private ActionLedger loadActionLedger(UUID scheduleId) {
        Map<ActionType, Map<UUID, LocalDateTime>> latestSuccessByAction = new EnumMap<>(ActionType.class);
        for (ActionType actionType : ActionType.values()) {
            latestSuccessByAction.put(actionType, new LinkedHashMap<>());
        }

        for (DispositionActionExecution execution : dispositionActionExecutionRepository
            .findByScheduleIdAndStatusOrderByExecutedAtDesc(scheduleId, ExecutionStatus.SUCCESS)) {
            latestSuccessByAction.get(execution.getActionType())
                .putIfAbsent(execution.getNodeId(), execution.getExecutedAt());
        }

        return new ActionLedger(latestSuccessByAction);
    }

    private void recordExecution(
        DispositionSchedule schedule,
        DispositionCandidate candidate,
        ActionType actionType,
        ExecutionStatus status,
        String actor,
        int affectedNodeCount,
        String details,
        LocalDateTime executedAt
    ) {
        DispositionActionExecution execution = new DispositionActionExecution();
        execution.setSchedule(schedule);
        execution.setActionType(actionType);
        execution.setStatus(status);
        execution.setNodeId(candidate.nodeId());
        execution.setNodeName(candidate.nodeName());
        execution.setNodeType(candidate.nodeType());
        execution.setNodePath(candidate.path());
        execution.setAffectedNodeCount(affectedNodeCount);
        execution.setDetails(details);
        execution.setActor(actor);
        execution.setExecutedAt(executedAt);
        dispositionActionExecutionRepository.save(execution);
    }

    private ScheduleSnapshot resolveSnapshot(Folder folder, DispositionScheduleUpsertRequest request, boolean requestOverride) {
        if (request != null) {
            validateRequest(request);
            boolean enabled = request.enabled() == null || request.enabled();
            enforceArchivePolicyCompatibility(folder.getId(), enabled);
            DispositionSchedule transientSchedule = new DispositionSchedule();
            transientSchedule.setFolder(folder);
            transientSchedule.setEnabled(enabled);
            transientSchedule.setIncludeSubfolders(request.includeSubfolders() == null || request.includeSubfolders());
            transientSchedule.setCutoffAfterDays(request.cutoffAfterDays());
            transientSchedule.setArchiveAfterCutoffDays(request.archiveAfterCutoffDays());
            transientSchedule.setDestroyAfterArchiveDays(request.destroyAfterArchiveDays());
            transientSchedule.setArchiveStorageTier(request.archiveStorageTier() != null ? request.archiveStorageTier() : Node.ArchiveStoreTier.COLD);
            transientSchedule.setMaxCandidatesPerAction(request.maxCandidatesPerAction() != null ? request.maxCandidatesPerAction() : 100);
            if (requestOverride) {
                transientSchedule.setId(UUID.randomUUID());
            }
            return toSnapshot(transientSchedule);
        }

        DispositionSchedule schedule = dispositionScheduleRepository.findByFolderId(folder.getId())
            .filter(existing -> !existing.isDeleted())
            .orElseThrow(() -> new NoSuchElementException("Disposition schedule not found for folder: " + folder.getId()));
        return toSnapshot(schedule);
    }

    private ScheduleSnapshot toSnapshot(DispositionSchedule schedule) {
        return new ScheduleSnapshot(
            schedule,
            schedule.getFolder().getId(),
            schedule.getFolder().getName(),
            schedule.getFolder().getPath(),
            schedule.isIncludeSubfolders(),
            schedule.getCutoffAfterDays(),
            schedule.getArchiveAfterCutoffDays(),
            schedule.getDestroyAfterArchiveDays(),
            schedule.getArchiveStorageTier(),
            schedule.getMaxCandidatesPerAction()
        );
    }

    private Folder loadLiveFolder(UUID folderId) {
        Folder folder = folderRepository.findById(folderId)
            .orElseThrow(() -> new NoSuchElementException("Folder not found: " + folderId));
        if (folder.isDeleted() || folder.getArchiveStatus() != Node.ArchiveStatus.LIVE) {
            throw new NoSuchElementException("Folder not found: " + folderId);
        }
        if (!tenantWorkspaceScopeService.isPathVisible(folder.getPath())) {
            throw new ResourceNotFoundException("Folder not found: " + folderId);
        }
        if (!recordsManagementService.isFilePlanFolder(folder)) {
            throw new IllegalOperationException("Disposition schedules require a FILE_PLAN folder: " + folder.getName());
        }
        return folder;
    }

    private boolean isCandidateVisible(Node node) {
        return tenantWorkspaceScopeService.isPathVisible(node.getPath());
    }

    private LocalDateTime resolveActivityDate(Node node) {
        return node.getLastModifiedDate() != null ? node.getLastModifiedDate() : node.getCreatedDate();
    }

    private int depth(String path) {
        if (path == null || path.isBlank()) {
            return 0;
        }
        return (int) path.chars().filter(ch -> ch == '/').count();
    }

    private void validateRequest(DispositionScheduleUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Disposition schedule request is required");
        }
        if (request.cutoffAfterDays() == null || request.cutoffAfterDays() < 0) {
            throw new IllegalArgumentException("cutoffAfterDays must be >= 0");
        }
        if (request.archiveAfterCutoffDays() != null && request.archiveAfterCutoffDays() < 0) {
            throw new IllegalArgumentException("archiveAfterCutoffDays must be >= 0");
        }
        if (request.destroyAfterArchiveDays() != null && request.destroyAfterArchiveDays() < 0) {
            throw new IllegalArgumentException("destroyAfterArchiveDays must be >= 0");
        }
        if (request.destroyAfterArchiveDays() != null && request.archiveAfterCutoffDays() == null) {
            throw new IllegalArgumentException("destroyAfterArchiveDays requires archiveAfterCutoffDays");
        }
        if (request.maxCandidatesPerAction() != null && request.maxCandidatesPerAction() <= 0) {
            throw new IllegalArgumentException("maxCandidatesPerAction must be > 0");
        }
    }

    private void enforceArchivePolicyCompatibility(UUID folderId, Boolean enabled) {
        if (!Boolean.TRUE.equals(enabled)) {
            return;
        }
        archivePolicyRepository.findByFolderId(folderId)
            .filter(policy -> !policy.isDeleted() && policy.isEnabled())
            .ifPresent(policy -> {
                throw new IllegalOperationException(
                    "Disposition schedule cannot be enabled while archive policy is enabled for folder: " + folderId
                );
            });
    }

    private void requireAdmin() {
        if (!securityService.hasRole("ROLE_ADMIN")) {
            throw new SecurityException("Admin access required for disposition schedule operations");
        }
    }

    private DispositionScheduleDto toDto(DispositionSchedule schedule) {
        return new DispositionScheduleDto(
            schedule.getId(),
            schedule.getFolder().getId(),
            schedule.getFolder().getName(),
            schedule.getFolder().getPath(),
            schedule.isEnabled(),
            schedule.isIncludeSubfolders(),
            schedule.getCutoffAfterDays(),
            schedule.getArchiveAfterCutoffDays(),
            schedule.getDestroyAfterArchiveDays(),
            schedule.getArchiveStorageTier(),
            schedule.getMaxCandidatesPerAction(),
            schedule.getLastDryRunAt(),
            schedule.getLastExecutedAt(),
            schedule.getLastError()
        );
    }

    private DispositionCandidateDto toCandidateDto(DispositionCandidate candidate) {
        return new DispositionCandidateDto(
            candidate.nodeId(),
            candidate.nodeName(),
            candidate.nodeType(),
            candidate.path(),
            candidate.actionType().name(),
            candidate.eligibleAt(),
            candidate.blockedByHoldNames()
        );
    }

    private DispositionActionExecutionDto toExecutionDto(DispositionActionExecution execution) {
        return new DispositionActionExecutionDto(
            execution.getId(),
            execution.getActionType(),
            execution.getStatus(),
            execution.getNodeId(),
            execution.getNodeName(),
            execution.getNodeType(),
            execution.getNodePath(),
            execution.getAffectedNodeCount(),
            execution.getDetails(),
            execution.getActor(),
            execution.getExecutedAt()
        );
    }

    private int actionOrder(ActionType actionType) {
        return switch (actionType) {
            case CUTOFF -> 0;
            case ARCHIVE -> 1;
            case DESTROY -> 2;
        };
    }

    private record ScheduleSnapshot(
        DispositionSchedule schedule,
        UUID folderId,
        String folderName,
        String folderPath,
        boolean includeSubfolders,
        Integer cutoffAfterDays,
        Integer archiveAfterCutoffDays,
        Integer destroyAfterArchiveDays,
        Node.ArchiveStoreTier archiveStorageTier,
        Integer maxCandidatesPerAction
    ) {
    }

    private record ActionLedger(
        Map<ActionType, Map<UUID, LocalDateTime>> latestSuccessByAction
    ) {
    }

    private record DispositionCandidate(
        UUID nodeId,
        String nodeName,
        String nodeType,
        String path,
        ActionType actionType,
        LocalDateTime eligibleAt,
        String blockedByHoldNames
    ) {
    }

    private record DestroyRunSummary(
        int destroyCandidateCount,
        int destroyedNodeCount,
        int blockedCount
    ) {
    }

    private record ArchiveRunSummary(
        int archiveCandidateCount,
        int archivedNodeCount
    ) {
    }

    public record DispositionScheduleUpsertRequest(
        Boolean enabled,
        Boolean includeSubfolders,
        Integer cutoffAfterDays,
        Integer archiveAfterCutoffDays,
        Integer destroyAfterArchiveDays,
        Node.ArchiveStoreTier archiveStorageTier,
        Integer maxCandidatesPerAction
    ) {
    }

    public record DispositionScheduleDto(
        UUID id,
        UUID folderId,
        String folderName,
        String folderPath,
        boolean enabled,
        boolean includeSubfolders,
        Integer cutoffAfterDays,
        Integer archiveAfterCutoffDays,
        Integer destroyAfterArchiveDays,
        Node.ArchiveStoreTier archiveStorageTier,
        Integer maxCandidatesPerAction,
        LocalDateTime lastDryRunAt,
        LocalDateTime lastExecutedAt,
        String lastError
    ) {
    }

    public record DispositionCandidateDto(
        UUID nodeId,
        String name,
        String nodeType,
        String path,
        String actionType,
        LocalDateTime eligibleAt,
        String blockedByHoldNames
    ) {
    }

    public record DispositionDryRunDto(
        UUID folderId,
        String folderName,
        boolean includeSubfolders,
        Node.ArchiveStoreTier archiveStorageTier,
        Integer maxCandidatesPerAction,
        int cutoffCount,
        int archiveCount,
        int destroyCount,
        List<DispositionCandidateDto> candidates
    ) {
    }

    public record DispositionExecutionDto(
        UUID folderId,
        String folderName,
        int cutoffCount,
        int archiveCandidateCount,
        int archivedNodeCount,
        int destroyCandidateCount,
        int destroyedNodeCount,
        int failureCount,
        int blockedCount,
        List<String> failures,
        String error
    ) {
    }

    public record DispositionBatchExecutionDto(
        int executedSchedules,
        int cutoffCount,
        int archivedNodeCount,
        int destroyedNodeCount,
        int blockedCount,
        int failureCount,
        List<DispositionExecutionDto> results
    ) {
    }

    public record DispositionActionExecutionDto(
        UUID id,
        ActionType actionType,
        ExecutionStatus status,
        UUID nodeId,
        String nodeName,
        String nodeType,
        String nodePath,
        Integer affectedNodeCount,
        String details,
        String actor,
        LocalDateTime executedAt
    ) {
    }
}
