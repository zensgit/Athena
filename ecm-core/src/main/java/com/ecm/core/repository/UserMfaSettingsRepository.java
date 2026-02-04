package com.ecm.core.repository;

import com.ecm.core.entity.UserMfaSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserMfaSettingsRepository extends JpaRepository<UserMfaSettings, UUID> {
    Optional<UserMfaSettings> findByUsername(String username);
}
