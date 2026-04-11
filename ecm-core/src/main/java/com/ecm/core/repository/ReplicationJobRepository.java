package com.ecm.core.repository;

import com.ecm.core.entity.ReplicationJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReplicationJobRepository extends JpaRepository<ReplicationJob, UUID> {

    Page<ReplicationJob> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<ReplicationJob> findByStatusAndScheduledForLessThanEqual(ReplicationJob.ReplicationJobStatus status, LocalDateTime scheduledFor);

    List<ReplicationJob> findByDefinitionIdAndStatusInAndCompletedAtBefore(
        UUID definitionId,
        Collection<ReplicationJob.ReplicationJobStatus> statuses,
        LocalDateTime completedAt
    );

    boolean existsByDefinitionIdAndStatusIn(UUID definitionId, Collection<ReplicationJob.ReplicationJobStatus> statuses);

    boolean existsByTransferTargetId(UUID transferTargetId);
}
