package com.ecm.core.integration.mail.repository;

import com.ecm.core.integration.mail.model.ProcessedMail;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProcessedMailRepository extends JpaRepository<ProcessedMail, UUID> {
    boolean existsByAccountIdAndFolderAndUid(UUID accountId, String folder, String uid);

    List<ProcessedMail> findAllByOrderByProcessedAtDesc(Pageable pageable);

    @Query("SELECT p FROM ProcessedMail p " +
           "WHERE (:accountId IS NULL OR p.accountId = :accountId) " +
           "AND (:ruleId IS NULL OR p.ruleId = :ruleId) " +
           "ORDER BY p.processedAt DESC")
    List<ProcessedMail> findRecentByFilters(
        @Param("accountId") UUID accountId,
        @Param("ruleId") UUID ruleId,
        Pageable pageable
    );
}
