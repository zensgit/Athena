package com.ecm.core.asynctask;

import com.ecm.core.entity.PropertyEncryptionBackfillJob;
import com.ecm.core.entity.PropertyEncryptionBackfillJob.BackfillJobStatus;
import com.ecm.core.entity.PropertyEncryptionRewrapJob;
import com.ecm.core.entity.PropertyEncryptionRewrapJob.RewrapJobStatus;
import com.ecm.core.repository.PropertyEncryptionBackfillJobRepository;
import com.ecm.core.repository.PropertyEncryptionRewrapJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PropertyEncryptionAsyncTaskService {

    static final String DOMAIN_KEY = "propertyEncryption";
    static final String DOMAIN_LABEL = "Property Encryption";

    private final PropertyEncryptionBackfillJobRepository backfillJobRepository;
    private final PropertyEncryptionRewrapJobRepository rewrapJobRepository;

    public AsyncTaskSummarySnapshot summary(String statusFilter) {
        StatusSelection selection = selectStatuses(statusFilter);
        if (selection != null) {
            return AsyncTaskSummarySnapshot.ofBreakdown(
                countBackfill(selection.backfillStatuses(), BackfillJobStatus.PLANNED)
                    + countRewrap(selection.rewrapStatuses(), RewrapJobStatus.PLANNED),
                countBackfill(selection.backfillStatuses(), BackfillJobStatus.RUNNING)
                    + countBackfill(selection.backfillStatuses(), BackfillJobStatus.CANCEL_REQUESTED)
                    + countRewrap(selection.rewrapStatuses(), RewrapJobStatus.RUNNING)
                    + countRewrap(selection.rewrapStatuses(), RewrapJobStatus.CANCEL_REQUESTED),
                countBackfill(selection.backfillStatuses(), BackfillJobStatus.SUCCEEDED)
                    + countRewrap(selection.rewrapStatuses(), RewrapJobStatus.SUCCEEDED),
                countBackfill(selection.backfillStatuses(), BackfillJobStatus.CANCELLED)
                    + countRewrap(selection.rewrapStatuses(), RewrapJobStatus.CANCELLED),
                countBackfill(selection.backfillStatuses(), BackfillJobStatus.FAILED)
                    + countRewrap(selection.rewrapStatuses(), RewrapJobStatus.FAILED),
                0,
                0
            );
        }

        return AsyncTaskSummarySnapshot.ofBreakdown(
            backfillJobRepository.countByStatus(BackfillJobStatus.PLANNED)
                + rewrapJobRepository.countByStatus(RewrapJobStatus.PLANNED),
            backfillJobRepository.countByStatus(BackfillJobStatus.RUNNING)
                + backfillJobRepository.countByStatus(BackfillJobStatus.CANCEL_REQUESTED)
                + rewrapJobRepository.countByStatus(RewrapJobStatus.RUNNING)
                + rewrapJobRepository.countByStatus(RewrapJobStatus.CANCEL_REQUESTED),
            backfillJobRepository.countByStatus(BackfillJobStatus.SUCCEEDED)
                + rewrapJobRepository.countByStatus(RewrapJobStatus.SUCCEEDED),
            backfillJobRepository.countByStatus(BackfillJobStatus.CANCELLED)
                + rewrapJobRepository.countByStatus(RewrapJobStatus.CANCELLED),
            backfillJobRepository.countByStatus(BackfillJobStatus.FAILED)
                + rewrapJobRepository.countByStatus(RewrapJobStatus.FAILED),
            0,
            0
        );
    }

    public List<AsyncTaskStatusSnapshot> listRecent(int limit, String statusFilter) {
        int boundedLimit = Math.max(1, limit);
        PageRequest page = PageRequest.of(0, boundedLimit);
        StatusSelection selection = selectStatuses(statusFilter);

        List<PropertyEncryptionBackfillJob> backfillJobs = selection == null
            ? backfillJobRepository.findAllByOrderByRequestedAtDesc(page)
            : backfillJobRepository.findByStatusInOrderByRequestedAtDesc(selection.backfillStatuses(), page);
        List<PropertyEncryptionRewrapJob> rewrapJobs = selection == null
            ? rewrapJobRepository.findAllByOrderByRequestedAtDesc(page)
            : rewrapJobRepository.findByStatusInOrderByRequestedAtDesc(selection.rewrapStatuses(), page);

        List<AsyncTaskStatusSnapshot> items = new ArrayList<>(backfillJobs.size() + rewrapJobs.size());
        items.addAll(backfillJobs.stream()
            .map(job -> AsyncTaskLifecycleAdapters.fromPropertyEncryptionBackfill(DOMAIN_KEY, DOMAIN_LABEL, job))
            .toList());
        items.addAll(rewrapJobs.stream()
            .map(job -> AsyncTaskLifecycleAdapters.fromPropertyEncryptionRewrap(DOMAIN_KEY, DOMAIN_LABEL, job))
            .toList());
        return items;
    }

    private StatusSelection selectStatuses(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) {
            return null;
        }
        String normalized = statusFilter.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "queued", "planned" -> new StatusSelection(
                List.of(BackfillJobStatus.PLANNED),
                List.of(RewrapJobStatus.PLANNED)
            );
            case "running", "cancel_requested", "cancel-requested" -> new StatusSelection(
                List.of(BackfillJobStatus.RUNNING, BackfillJobStatus.CANCEL_REQUESTED),
                List.of(RewrapJobStatus.RUNNING, RewrapJobStatus.CANCEL_REQUESTED)
            );
            case "completed", "succeeded", "success" -> new StatusSelection(
                List.of(BackfillJobStatus.SUCCEEDED),
                List.of(RewrapJobStatus.SUCCEEDED)
            );
            case "cancelled", "canceled" -> new StatusSelection(
                List.of(BackfillJobStatus.CANCELLED),
                List.of(RewrapJobStatus.CANCELLED)
            );
            case "failed" -> new StatusSelection(
                List.of(BackfillJobStatus.FAILED),
                List.of(RewrapJobStatus.FAILED)
            );
            default -> throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unknown property encryption async status: " + statusFilter
            );
        };
    }

    private long countBackfill(List<BackfillJobStatus> statuses, BackfillJobStatus status) {
        return statuses.contains(status) ? backfillJobRepository.countByStatus(status) : 0;
    }

    private long countRewrap(List<RewrapJobStatus> statuses, RewrapJobStatus status) {
        return statuses.contains(status) ? rewrapJobRepository.countByStatus(status) : 0;
    }

    private record StatusSelection(
        List<BackfillJobStatus> backfillStatuses,
        List<RewrapJobStatus> rewrapStatuses
    ) {
    }
}
