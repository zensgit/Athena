package com.ecm.core.repository;

import com.ecm.core.entity.AsyncTaskAcknowledgement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AsyncTaskAcknowledgementRepository extends JpaRepository<AsyncTaskAcknowledgement, UUID> {

    List<AsyncTaskAcknowledgement> findByUserIdAndTaskFingerprintIn(String userId, Collection<String> taskFingerprints);

    Optional<AsyncTaskAcknowledgement> findByUserIdAndTaskFingerprint(String userId, String taskFingerprint);

    void deleteByUserIdAndTaskFingerprint(String userId, String taskFingerprint);
}
