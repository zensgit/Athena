package com.ecm.core.repository;

import com.ecm.core.entity.PropertyEncryptionRewrapJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PropertyEncryptionRewrapJobRepository extends JpaRepository<PropertyEncryptionRewrapJob, UUID> {

    List<PropertyEncryptionRewrapJob> findAllByOrderByRequestedAtDesc(Pageable pageable);
}
