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

    List<ProcessedMail> findAllByAccountIdOrderByProcessedAtDesc(UUID accountId, Pageable pageable);

    List<ProcessedMail> findAllByRuleIdOrderByProcessedAtDesc(UUID ruleId, Pageable pageable);

    List<ProcessedMail> findAllByAccountIdAndRuleIdOrderByProcessedAtDesc(
        UUID accountId,
        UUID ruleId,
        Pageable pageable
    );

    @Query("SELECT p FROM ProcessedMail p " +
           "WHERE (:accountId IS NULL OR p.accountId = :accountId) " +
           "AND (:ruleId IS NULL OR p.ruleId = :ruleId) " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (:subject IS NULL OR LOWER(p.subject) LIKE LOWER(CONCAT('%', :subject, '%'))) " +
           "AND (:processedFrom IS NULL OR p.processedAt >= :processedFrom) " +
           "AND (:processedTo IS NULL OR p.processedAt <= :processedTo) " +
           "ORDER BY p.processedAt DESC")
    List<ProcessedMail> findRecentByFilters(
        @Param("accountId") UUID accountId,
        @Param("ruleId") UUID ruleId,
        @Param("status") ProcessedMail.Status status,
        @Param("subject") String subject,
        @Param("processedFrom") java.time.LocalDateTime processedFrom,
        @Param("processedTo") java.time.LocalDateTime processedTo,
        Pageable pageable
    );

    long countByProcessedAtBefore(java.time.LocalDateTime threshold);

    void deleteByProcessedAtBefore(java.time.LocalDateTime threshold);
}
