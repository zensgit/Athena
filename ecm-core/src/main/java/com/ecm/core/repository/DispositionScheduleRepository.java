package com.ecm.core.repository;

import com.ecm.core.entity.DispositionSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispositionScheduleRepository extends JpaRepository<DispositionSchedule, UUID> {

    Optional<DispositionSchedule> findByFolderId(UUID folderId);

    List<DispositionSchedule> findByEnabledTrueAndDeletedFalseOrderByCreatedDateAsc();

    List<DispositionSchedule> findByDeletedFalseOrderByCreatedDateDesc();
}
