package com.ecm.core.integration.mail.repository;

import com.ecm.core.integration.mail.model.ProcessedMail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import java.util.UUID;
import java.util.List;

@Repository
public interface ProcessedMailRepository extends JpaRepository<ProcessedMail, UUID> {
    boolean existsByAccountIdAndFolderAndUid(UUID accountId, String folder, String uid);

    List<ProcessedMail> findAllByOrderByProcessedAtDesc(Pageable pageable);
}
