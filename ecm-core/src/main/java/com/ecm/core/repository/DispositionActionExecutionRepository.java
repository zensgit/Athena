package com.ecm.core.repository;

import com.ecm.core.entity.DispositionActionExecution;
import com.ecm.core.entity.DispositionActionExecution.ExecutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DispositionActionExecutionRepository extends JpaRepository<DispositionActionExecution, UUID> {

    Page<DispositionActionExecution> findByScheduleIdOrderByExecutedAtDesc(UUID scheduleId, Pageable pageable);

    List<DispositionActionExecution> findByScheduleIdAndStatusOrderByExecutedAtDesc(UUID scheduleId, ExecutionStatus status);
}
