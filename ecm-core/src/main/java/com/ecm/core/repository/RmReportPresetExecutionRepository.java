package com.ecm.core.repository;

import com.ecm.core.entity.RmReportPresetExecution;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RmReportPresetExecutionRepository extends JpaRepository<RmReportPresetExecution, UUID>,
    JpaSpecificationExecutor<RmReportPresetExecution> {

    List<RmReportPresetExecution> findByPresetIdOrderByStartedAtDesc(UUID presetId, Pageable pageable);

    Optional<RmReportPresetExecution> findFirstByPresetIdOrderByStartedAtDesc(UUID presetId);
}
