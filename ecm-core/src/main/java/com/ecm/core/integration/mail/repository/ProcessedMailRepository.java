package com.ecm.core.integration.mail.repository;

import com.ecm.core.integration.mail.model.ProcessedMail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProcessedMailRepository extends JpaRepository<ProcessedMail, UUID>, JpaSpecificationExecutor<ProcessedMail> {
    boolean existsByAccountIdAndFolderAndUid(UUID accountId, String folder, String uid);

    long countByProcessedAtBefore(java.time.LocalDateTime threshold);

    void deleteByProcessedAtBefore(java.time.LocalDateTime threshold);
}
