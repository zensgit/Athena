package com.ecm.core.repository;

import com.ecm.core.entity.TransferReceiverRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TransferReceiverRegistrationRepository extends JpaRepository<TransferReceiverRegistration, UUID> {

    boolean existsByNameIgnoreCase(String name);
}
