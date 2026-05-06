package com.ecm.core.integration.oauth.repository;

import com.ecm.core.integration.oauth.OAuthCredentialInventoryItem;
import com.ecm.core.integration.oauth.OAuthCredentialOwnerReference;
import com.ecm.core.integration.oauth.OAuthProviderType;
import com.ecm.core.integration.oauth.model.OAuthCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthCredentialRepository extends JpaRepository<OAuthCredential, UUID> {

    Optional<OAuthCredential> findByOwnerTypeAndOwnerId(String ownerType, UUID ownerId);

    void deleteByOwnerTypeAndOwnerId(String ownerType, UUID ownerId);

    @Query("""
        SELECT new com.ecm.core.integration.oauth.OAuthCredentialInventoryItem(
            c.id,
            c.ownerType,
            c.ownerId,
            c.provider,
            CASE WHEN c.tokenEndpoint IS NOT NULL AND c.tokenEndpoint <> '' THEN true ELSE false END,
            CASE WHEN c.tenantId IS NOT NULL AND c.tenantId <> '' THEN true ELSE false END,
            CASE WHEN c.scope IS NOT NULL AND c.scope <> '' THEN true ELSE false END,
            CASE WHEN c.credentialKey IS NOT NULL AND c.credentialKey <> '' THEN true ELSE false END,
            CASE WHEN c.accessToken IS NOT NULL AND c.accessToken <> '' THEN true ELSE false END,
            CASE WHEN c.refreshToken IS NOT NULL AND c.refreshToken <> '' THEN true ELSE false END,
            CASE WHEN (
                (c.accessToken IS NOT NULL AND c.accessToken <> '')
                OR (c.refreshToken IS NOT NULL AND c.refreshToken <> '')
            ) THEN true ELSE false END,
            c.tokenExpiresAt,
            c.createdAt,
            c.updatedAt
        )
        FROM OAuthCredential c
        WHERE (:ownerType IS NULL OR c.ownerType = :ownerType)
          AND (:provider IS NULL OR c.provider = :provider)
        ORDER BY c.updatedAt DESC, c.createdAt DESC
        """)
    List<OAuthCredentialInventoryItem> findInventoryItems(
        @Param("ownerType") String ownerType,
        @Param("provider") OAuthProviderType provider
    );

    @Query("""
        SELECT new com.ecm.core.integration.oauth.OAuthCredentialOwnerReference(
            c.id,
            c.ownerType,
            c.ownerId
        )
        FROM OAuthCredential c
        WHERE c.id = :id
        """)
    Optional<OAuthCredentialOwnerReference> findOwnerReferenceById(@Param("id") UUID id);

    @Query("""
        SELECT new com.ecm.core.integration.oauth.OAuthCredentialInventoryItem(
            c.id,
            c.ownerType,
            c.ownerId,
            c.provider,
            CASE WHEN c.tokenEndpoint IS NOT NULL AND c.tokenEndpoint <> '' THEN true ELSE false END,
            CASE WHEN c.tenantId IS NOT NULL AND c.tenantId <> '' THEN true ELSE false END,
            CASE WHEN c.scope IS NOT NULL AND c.scope <> '' THEN true ELSE false END,
            CASE WHEN c.credentialKey IS NOT NULL AND c.credentialKey <> '' THEN true ELSE false END,
            CASE WHEN c.accessToken IS NOT NULL AND c.accessToken <> '' THEN true ELSE false END,
            CASE WHEN c.refreshToken IS NOT NULL AND c.refreshToken <> '' THEN true ELSE false END,
            CASE WHEN (
                (c.accessToken IS NOT NULL AND c.accessToken <> '')
                OR (c.refreshToken IS NOT NULL AND c.refreshToken <> '')
            ) THEN true ELSE false END,
            c.tokenExpiresAt,
            c.createdAt,
            c.updatedAt
        )
        FROM OAuthCredential c
        WHERE c.id = :id
        """)
    Optional<OAuthCredentialInventoryItem> findInventoryItemById(@Param("id") UUID id);
}
