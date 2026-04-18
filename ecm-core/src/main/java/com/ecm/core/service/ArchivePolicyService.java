package com.ecm.core.service;

import com.ecm.core.entity.ArchivePolicy;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.exception.IllegalOperationException;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.repository.ArchivePolicyRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ArchivePolicyService {

    private static final String SYSTEM_ACTOR = "system:archive-policy";

    private final ArchivePolicyRepository archivePolicyRepository;
    private final FolderRepository folderRepository;
    private final NodeRepository nodeRepository;
    private final SecurityService securityService;
    private final ContentArchiveService contentArchiveService;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;
    private final RecordsManagementService recordsManagementService;

    @Autowired
    @Lazy
    private com.ecm.core.repository.DispositionScheduleRepository dispositionScheduleRepository;

    @Transactional(readOnly = true)
    public Optional<ArchivePolicyDto> getPolicy(UUID folderId) {
        requireAdmin();
        loadLiveFolder(folderId);
        return archivePolicyRepository.findByFolderId(folderId)
            .filter(policy -> !policy.isDeleted())
            .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<ArchivePolicyDto> listPolicies() {
        requireAdmin();
        String tenantRootPath = tenantWorkspaceScopeService.resolveCurrentTenantRootPath();
        return archivePolicyRepository.findByDeletedFalseOrderByCreatedDateDesc().stream()
            .filter(policy -> tenantRootPath == null
                || (policy.getFolder() != null
                && tenantWorkspaceScopeService.isPathVisible(policy.getFolder().getPath(), tenantRootPath)))
            .map(this::toDto)
            .toList();
    }

    public ArchivePolicyDto upsertPolicy(UUID folderId, ArchivePolicyUpsertRequest request) {
        requireAdmin();
        Folder folder = loadLiveFolder(folderId);
        assertArchivePolicyFolderAllowed(folder);
        validateRequest(request);
        assertNoActiveDispositionSchedule(folderId, request.enabled());

        ArchivePolicy policy = archivePolicyRepository.findByFolderId(folderId)
            .filter(existing -> !existing.isDeleted())
            .orElseGet(ArchivePolicy::new);

        policy.setFolder(folder);
        policy.setEnabled(Boolean.TRUE.equals(request.enabled()));
        policy.setInactivityDays(request.inactivityDays());
        policy.setStorageTier(request.storageTier());
        policy.setIncludeSubfolders(Boolean.TRUE.equals(request.includeSubfolders()));
        policy.setMaxCandidatesPerRun(request.maxCandidatesPerRun());
        policy.setLastError(null);

        return toDto(archivePolicyRepository.save(policy));
    }

    public void deletePolicy(UUID folderId) {
        requireAdmin();
        ArchivePolicy policy = archivePolicyRepository.findByFolderId(folderId)
            .filter(existing -> !existing.isDeleted())
            .orElseThrow(() -> new NoSuchElementException("Archive policy not found for folder: " + folderId));
        archivePolicyRepository.delete(policy);
    }

    public ArchivePolicyDryRunDto dryRunPolicy(UUID folderId, ArchivePolicyUpsertRequest request) {
        requireAdmin();
        Folder folder = loadLiveFolder(folderId);
        assertArchivePolicyFolderAllowed(folder);
        PolicySnapshot snapshot = resolveSnapshot(folder, request, true);
        ArchivePolicyDryRunDto result = buildDryRun(snapshot);

        archivePolicyRepository.findByFolderId(folderId)
            .filter(existing -> !existing.isDeleted())
            .ifPresent(existing -> {
                existing.setLastDryRunAt(LocalDateTime.now());
                existing.setLastCandidateCount(result.candidateCount());
                existing.setLastError(null);
                archivePolicyRepository.save(existing);
            });

        return result;
    }

    public ArchivePolicyExecutionDto executePolicy(UUID folderId) {
        requireAdmin();
        Folder folder = loadLiveFolder(folderId);
        assertArchivePolicyFolderAllowed(folder);
        ArchivePolicy policy = archivePolicyRepository.findByFolderId(folderId)
            .filter(existing -> !existing.isDeleted())
            .orElseThrow(() -> new NoSuchElementException("Archive policy not found for folder: " + folderId));

        return executePolicy(policy, folder, securityService.getCurrentUser());
    }

    public ArchivePolicyBatchExecutionDto runScheduledPolicies() {
        List<ArchivePolicyExecutionDto> results = new ArrayList<>();
        for (ArchivePolicy policy : archivePolicyRepository.findByEnabledTrueAndDeletedFalseOrderByCreatedDateAsc()) {
            Folder folder = policy.getFolder();
            if (folder == null || folder.isDeleted() || folder.getArchiveStatus() != Node.ArchiveStatus.LIVE) {
                continue;
            }
            if (!tenantWorkspaceScopeService.isPathVisible(folder.getPath())) {
                continue;
            }
            try {
                assertArchivePolicyFolderAllowed(folder);
                results.add(executePolicy(policy, folder, SYSTEM_ACTOR));
            } catch (Exception ex) {
                log.warn("Archive policy run failed for folder {}: {}", folder.getId(), ex.getMessage());
                policy.setLastExecutedAt(LocalDateTime.now());
                policy.setLastError(ex.getMessage());
                archivePolicyRepository.save(policy);
                results.add(new ArchivePolicyExecutionDto(
                    folder.getId(),
                    folder.getName(),
                    0,
                    0,
                    0,
                    List.of(),
                    ex.getMessage()
                ));
            }
        }

        int executedPolicies = results.size();
        int totalCandidates = results.stream().mapToInt(ArchivePolicyExecutionDto::candidateCount).sum();
        int archivedNodeCount = results.stream().mapToInt(ArchivePolicyExecutionDto::archivedNodeCount).sum();
        int failureCount = (int) results.stream().filter(result -> result.error() != null || !result.failures().isEmpty()).count();
        return new ArchivePolicyBatchExecutionDto(executedPolicies, totalCandidates, archivedNodeCount, failureCount, results);
    }

    private ArchivePolicyExecutionDto executePolicy(ArchivePolicy policy, Folder folder, String actor) {
        PolicySnapshot snapshot = resolveSnapshot(folder, null, false);
        List<ArchivePolicyCandidateDto> candidates = selectCandidates(snapshot);
        List<String> failures = new ArrayList<>();
        int archivedNodeCount = 0;

        for (ArchivePolicyCandidateDto candidate : candidates) {
            try {
                archivedNodeCount += contentArchiveService.archiveNodeByPolicy(
                    candidate.nodeId(),
                    snapshot.storageTier(),
                    actor
                ).affectedNodeCount();
            } catch (Exception ex) {
                failures.add(candidate.name() + ": " + ex.getMessage());
            }
        }

        policy.setLastExecutedAt(LocalDateTime.now());
        policy.setLastCandidateCount(candidates.size());
        policy.setLastArchivedNodeCount(archivedNodeCount);
        policy.setLastError(failures.isEmpty() ? null : String.join("; ", failures));
        archivePolicyRepository.save(policy);

        return new ArchivePolicyExecutionDto(
            folder.getId(),
            folder.getName(),
            candidates.size(),
            archivedNodeCount,
            failures.size(),
            failures,
            null
        );
    }

    private ArchivePolicyDryRunDto buildDryRun(PolicySnapshot snapshot) {
        List<ArchivePolicyCandidateDto> candidates = selectCandidates(snapshot);
        return new ArchivePolicyDryRunDto(
            snapshot.folderId(),
            snapshot.folderName(),
            snapshot.cutoffDate(),
            snapshot.storageTier(),
            snapshot.includeSubfolders(),
            snapshot.maxCandidatesPerRun(),
            candidates.size(),
            candidates
        );
    }

    private List<ArchivePolicyCandidateDto> selectCandidates(PolicySnapshot snapshot) {
        List<Node> rawCandidates = snapshot.includeSubfolders()
            ? nodeRepository.findByPathPrefix(snapshot.folderPath() + "/")
            : nodeRepository.findByParentIdAndDeletedFalseAndArchiveStatus(
                snapshot.folderId(),
                Node.ArchiveStatus.LIVE
            );

        List<Node> eligible = rawCandidates.stream()
            .filter(node -> !node.isDeleted())
            .filter(node -> node.getArchiveStatus() == Node.ArchiveStatus.LIVE)
            .filter(node -> !recordsManagementService.isDeclaredRecord(node))
            .filter(node -> !recordsManagementService.isGovernedByFilePlan(node))
            .filter(node -> isOlderThanCutoff(node, snapshot.cutoffDate()))
            .sorted(Comparator
                .comparingInt((Node node) -> depth(node.getPath()))
                .thenComparing(Node::getPath, Comparator.nullsLast(String::compareToIgnoreCase)))
            .toList();

        List<Node> reduced = new ArrayList<>();
        for (Node candidate : eligible) {
            boolean nestedUnderSelectedFolder = reduced.stream()
                .filter(Node::isFolder)
                .map(Node::getPath)
                .filter(path -> path != null && !path.isBlank())
                .anyMatch(path -> candidate.getPath() != null
                    && (candidate.getPath().equals(path) || candidate.getPath().startsWith(path + "/")));
            if (!nestedUnderSelectedFolder) {
                reduced.add(candidate);
            }
            if (reduced.size() >= snapshot.maxCandidatesPerRun()) {
                break;
            }
        }

        return reduced.stream()
            .map(this::toCandidateDto)
            .toList();
    }

    private boolean isOlderThanCutoff(Node node, LocalDateTime cutoffDate) {
        LocalDateTime activityDate = node.getLastModifiedDate() != null ? node.getLastModifiedDate() : node.getCreatedDate();
        return activityDate != null && activityDate.isBefore(cutoffDate);
    }

    private int depth(String path) {
        if (path == null || path.isBlank()) {
            return 0;
        }
        return (int) path.chars().filter(ch -> ch == '/').count();
    }

    private ArchivePolicyCandidateDto toCandidateDto(Node node) {
        LocalDateTime activityDate = node.getLastModifiedDate() != null ? node.getLastModifiedDate() : node.getCreatedDate();
        return new ArchivePolicyCandidateDto(
            node.getId(),
            node.getName(),
            node.getNodeType().name(),
            node.getPath(),
            activityDate
        );
    }

    private PolicySnapshot resolveSnapshot(Folder folder, ArchivePolicyUpsertRequest request, boolean allowUnsavedRequest) {
        if (request != null) {
            validateRequest(request);
            return new PolicySnapshot(
                folder.getId(),
                folder.getName(),
                folder.getPath(),
                request.storageTier(),
                Boolean.TRUE.equals(request.includeSubfolders()),
                request.maxCandidatesPerRun(),
                LocalDateTime.now().minusDays(request.inactivityDays())
            );
        }

        ArchivePolicy policy = archivePolicyRepository.findByFolderId(folder.getId())
            .filter(existing -> !existing.isDeleted())
            .orElseThrow(() -> new NoSuchElementException("Archive policy not found for folder: " + folder.getId()));

        if (allowUnsavedRequest || policy.isEnabled()) {
            return new PolicySnapshot(
                folder.getId(),
                folder.getName(),
                folder.getPath(),
                policy.getStorageTier(),
                policy.isIncludeSubfolders(),
                policy.getMaxCandidatesPerRun(),
                LocalDateTime.now().minusDays(policy.getInactivityDays())
            );
        }

        throw new IllegalStateException("Archive policy is disabled for folder: " + folder.getId());
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
        return folder;
    }

    private void validateRequest(ArchivePolicyUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Archive policy request is required");
        }
        if (request.inactivityDays() == null || request.inactivityDays() < 1) {
            throw new IllegalArgumentException("inactivityDays must be at least 1");
        }
        if (request.maxCandidatesPerRun() == null || request.maxCandidatesPerRun() < 1 || request.maxCandidatesPerRun() > 1000) {
            throw new IllegalArgumentException("maxCandidatesPerRun must be between 1 and 1000");
        }
        if (request.storageTier() == null || request.storageTier() == Node.ArchiveStoreTier.HOT) {
            throw new IllegalArgumentException("storageTier must be WARM, COLD, or GLACIER");
        }
    }

    private void requireAdmin() {
        if (!securityService.hasRole("ROLE_ADMIN")) {
            throw new SecurityException("Admin access required for archive policy operations");
        }
    }

    private void assertArchivePolicyFolderAllowed(Folder folder) {
        if (recordsManagementService.isFilePlanFolder(folder)) {
            throw new IllegalOperationException(
                "Archive policy cannot be attached to file plan folder: " + folder.getName()
            );
        }
    }

    private void assertNoActiveDispositionSchedule(UUID folderId, boolean enabled) {
        if (!enabled || dispositionScheduleRepository == null) {
            return;
        }
        dispositionScheduleRepository.findByFolderId(folderId)
            .filter(schedule -> !schedule.isDeleted() && schedule.isEnabled())
            .ifPresent(schedule -> {
                throw new IllegalOperationException(
                    "Archive policy cannot be enabled while disposition schedule is enabled for folder: " + folderId
                );
            });
    }

    private ArchivePolicyDto toDto(ArchivePolicy policy) {
        Folder folder = policy.getFolder();
        return new ArchivePolicyDto(
            policy.getId(),
            folder.getId(),
            folder.getName(),
            folder.getPath(),
            policy.isEnabled(),
            policy.getInactivityDays(),
            policy.getStorageTier(),
            policy.isIncludeSubfolders(),
            policy.getMaxCandidatesPerRun(),
            policy.getLastDryRunAt(),
            policy.getLastExecutedAt(),
            policy.getLastCandidateCount(),
            policy.getLastArchivedNodeCount(),
            policy.getLastError()
        );
    }

    private record PolicySnapshot(
        UUID folderId,
        String folderName,
        String folderPath,
        Node.ArchiveStoreTier storageTier,
        boolean includeSubfolders,
        int maxCandidatesPerRun,
        LocalDateTime cutoffDate
    ) {}

    public record ArchivePolicyUpsertRequest(
        Boolean enabled,
        Integer inactivityDays,
        Node.ArchiveStoreTier storageTier,
        Boolean includeSubfolders,
        Integer maxCandidatesPerRun
    ) {}

    public record ArchivePolicyDto(
        UUID policyId,
        UUID folderId,
        String folderName,
        String folderPath,
        boolean enabled,
        Integer inactivityDays,
        Node.ArchiveStoreTier storageTier,
        boolean includeSubfolders,
        Integer maxCandidatesPerRun,
        LocalDateTime lastDryRunAt,
        LocalDateTime lastExecutedAt,
        Integer lastCandidateCount,
        Integer lastArchivedNodeCount,
        String lastError
    ) {}

    public record ArchivePolicyCandidateDto(
        UUID nodeId,
        String name,
        String nodeType,
        String path,
        LocalDateTime activityDate
    ) {}

    public record ArchivePolicyDryRunDto(
        UUID folderId,
        String folderName,
        LocalDateTime cutoffDate,
        Node.ArchiveStoreTier storageTier,
        boolean includeSubfolders,
        Integer maxCandidatesPerRun,
        int candidateCount,
        List<ArchivePolicyCandidateDto> candidates
    ) {}

    public record ArchivePolicyExecutionDto(
        UUID folderId,
        String folderName,
        int candidateCount,
        int archivedNodeCount,
        int failureCount,
        List<String> failures,
        String error
    ) {}

    public record ArchivePolicyBatchExecutionDto(
        int executedPolicies,
        int totalCandidates,
        int archivedNodeCount,
        int failureCount,
        List<ArchivePolicyExecutionDto> results
    ) {}
}
