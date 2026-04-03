package com.ecm.core.repository;

import com.ecm.core.entity.ImportJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

    Page<ImportJob> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<ImportJob> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
