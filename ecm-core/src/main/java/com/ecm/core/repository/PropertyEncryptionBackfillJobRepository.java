package com.ecm.core.repository;

import com.ecm.core.entity.PropertyEncryptionBackfillJob;
import com.ecm.core.entity.PropertyEncryptionBackfillJob.BackfillJobStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PropertyEncryptionBackfillJobRepository extends JpaRepository<PropertyEncryptionBackfillJob, UUID> {

    List<PropertyEncryptionBackfillJob> findAllByOrderByRequestedAtDesc(Pageable pageable);

    long countByStatus(BackfillJobStatus status);
}
