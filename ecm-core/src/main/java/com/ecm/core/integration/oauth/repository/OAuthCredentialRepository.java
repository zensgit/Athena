package com.ecm.core.integration.oauth.repository;

import com.ecm.core.integration.oauth.model.OAuthCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthCredentialRepository extends JpaRepository<OAuthCredential, UUID> {

    Optional<OAuthCredential> findByOwnerTypeAndOwnerId(String ownerType, UUID ownerId);

    void deleteByOwnerTypeAndOwnerId(String ownerType, UUID ownerId);
}
