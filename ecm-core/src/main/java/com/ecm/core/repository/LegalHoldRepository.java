package com.ecm.core.repository;

import com.ecm.core.entity.LegalHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LegalHoldRepository extends JpaRepository<LegalHold, UUID> {

    List<LegalHold> findByDeletedFalseOrderByCreatedDateDesc();

    List<LegalHold> findByStatusAndDeletedFalseOrderByCreatedDateDesc(LegalHold.HoldStatus status);
}
