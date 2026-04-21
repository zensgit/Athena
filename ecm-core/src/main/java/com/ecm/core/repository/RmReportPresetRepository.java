package com.ecm.core.repository;

import com.ecm.core.entity.RmReportPreset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

@Repository
public interface RmReportPresetRepository extends JpaRepository<RmReportPreset, UUID> {

    List<RmReportPreset> findByOwnerAndDeletedFalseOrderByName(String owner);

    Optional<RmReportPreset> findByIdAndDeletedFalse(UUID id);

    boolean existsByOwnerAndNameAndDeletedFalse(String owner, String name);

    List<RmReportPreset> findByScheduleEnabledTrueAndDeletedFalseAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
        LocalDateTime now
    );
}
