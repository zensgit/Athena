package com.ecm.core.repository;

import com.ecm.core.entity.TransferTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TransferTargetRepository extends JpaRepository<TransferTarget, UUID> {

    boolean existsByNameIgnoreCase(String name);
}
