package com.ecm.core.integration.mail.repository;

import com.ecm.core.integration.mail.model.ProcessedMail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProcessedMailRepository extends JpaRepository<ProcessedMail, UUID>, JpaSpecificationExecutor<ProcessedMail> {
    boolean existsByAccountIdAndFolderAndUid(UUID accountId, String folder, String uid);

    long countByProcessedAtBefore(java.time.LocalDateTime threshold);

    void deleteByProcessedAtBefore(java.time.LocalDateTime threshold);

    @Query(
        value = """
            SELECT account_id as accountId,
                   COALESCE(SUM(CASE WHEN status = 'PROCESSED' THEN 1 ELSE 0 END), 0) as processedCount,
                   COALESCE(SUM(CASE WHEN status = 'ERROR' THEN 1 ELSE 0 END), 0) as errorCount,
                   MAX(processed_at) as lastProcessedAt,
                   MAX(CASE WHEN status = 'ERROR' THEN processed_at ELSE NULL END) as lastErrorAt
            FROM mail_processed_messages
            WHERE processed_at >= :start
              AND processed_at < :end
              AND (CAST(:accountId AS uuid) IS NULL OR account_id = CAST(:accountId AS uuid))
            GROUP BY account_id
            ORDER BY account_id
            """,
        nativeQuery = true
    )
    List<MailAccountAggregateRow> aggregateByAccount(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end,
        @Param("accountId") UUID accountId
    );

    @Query(
        value = """
            SELECT rule_id as ruleId,
                   account_id as accountId,
                   COALESCE(SUM(CASE WHEN status = 'PROCESSED' THEN 1 ELSE 0 END), 0) as processedCount,
                   COALESCE(SUM(CASE WHEN status = 'ERROR' THEN 1 ELSE 0 END), 0) as errorCount,
                   MAX(processed_at) as lastProcessedAt,
                   MAX(CASE WHEN status = 'ERROR' THEN processed_at ELSE NULL END) as lastErrorAt
            FROM mail_processed_messages
            WHERE processed_at >= :start
              AND processed_at < :end
              AND rule_id IS NOT NULL
              AND (CAST(:accountId AS uuid) IS NULL OR account_id = CAST(:accountId AS uuid))
              AND (CAST(:ruleId AS uuid) IS NULL OR rule_id = CAST(:ruleId AS uuid))
            GROUP BY rule_id, account_id
            ORDER BY rule_id
            """,
        nativeQuery = true
    )
    List<MailRuleAggregateRow> aggregateByRule(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end,
        @Param("accountId") UUID accountId,
        @Param("ruleId") UUID ruleId
    );

    @Query(
        value = """
            SELECT DATE(processed_at) as day,
                   COALESCE(SUM(CASE WHEN status = 'PROCESSED' THEN 1 ELSE 0 END), 0) as processedCount,
                   COALESCE(SUM(CASE WHEN status = 'ERROR' THEN 1 ELSE 0 END), 0) as errorCount
            FROM mail_processed_messages
            WHERE processed_at >= :start
              AND processed_at < :end
              AND (CAST(:accountId AS uuid) IS NULL OR account_id = CAST(:accountId AS uuid))
              AND (CAST(:ruleId AS uuid) IS NULL OR rule_id = CAST(:ruleId AS uuid))
            GROUP BY DATE(processed_at)
            ORDER BY DATE(processed_at)
            """,
        nativeQuery = true
    )
    List<MailTrendAggregateRow> aggregateTrend(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end,
        @Param("accountId") UUID accountId,
        @Param("ruleId") UUID ruleId
    );

    interface MailAccountAggregateRow {
        UUID getAccountId();
        Long getProcessedCount();
        Long getErrorCount();
        LocalDateTime getLastProcessedAt();
        LocalDateTime getLastErrorAt();
    }

    interface MailRuleAggregateRow {
        UUID getRuleId();
        UUID getAccountId();
        Long getProcessedCount();
        Long getErrorCount();
        LocalDateTime getLastProcessedAt();
        LocalDateTime getLastErrorAt();
    }

    interface MailTrendAggregateRow {
        LocalDate getDay();
        Long getProcessedCount();
        Long getErrorCount();
    }
}
