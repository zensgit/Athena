package com.ecm.core.integration.oauth.repository;

import com.ecm.core.integration.oauth.OAuthCredentialInventoryItem;
import com.ecm.core.integration.oauth.OAuthCredentialOwnerReference;
import com.ecm.core.integration.oauth.OAuthProviderType;
import com.ecm.core.integration.oauth.model.OAuthCredential;
import com.ecm.core.security.secret.SecretCryptoProperties;
import com.ecm.core.security.secret.SecretCryptoService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.liquibase.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:oauthcredential;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "ecm.security.secret.enabled=true",
    "ecm.security.secret.active-key-version=v1",
    "ecm.security.secret.keys.v1=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@ContextConfiguration(classes = OAuthCredentialPersistenceTest.JpaTestConfig.class)
@Import({SecretCryptoProperties.class, SecretCryptoService.class})
class OAuthCredentialPersistenceTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = OAuthCredential.class)
    @EnableJpaRepositories(basePackageClasses = OAuthCredentialRepository.class)
    static class JpaTestConfig {
    }

    @Autowired
    private OAuthCredentialRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("OAuth credential token columns are stored encrypted and loaded decrypted")
    void storesEncryptedTokensAndLoadsDecryptedValues() {
        OAuthCredential credential = new OAuthCredential();
        credential.setOwnerType("MAIL_ACCOUNT");
        credential.setOwnerId(UUID.randomUUID());
        credential.setProvider(OAuthProviderType.GOOGLE);
        credential.setCredentialKey("GMAIL");
        credential.setRevokeEndpoint("https://custom.example/revoke");
        credential.setAccessToken("plain-access");
        credential.setRefreshToken("plain-refresh");
        credential.setTokenExpiresAt(LocalDateTime.now().plusHours(1));

        OAuthCredential saved = repository.saveAndFlush(credential);
        entityManager.clear();

        String storedAccess = jdbcTemplate.queryForObject(
            "select access_token from oauth_credentials where id = ?",
            String.class,
            saved.getId()
        );
        String storedRefresh = jdbcTemplate.queryForObject(
            "select refresh_token from oauth_credentials where id = ?",
            String.class,
            saved.getId()
        );

        assertTrue(storedAccess.startsWith("enc:v1:"));
        assertTrue(storedRefresh.startsWith("enc:v1:"));
        assertNotEquals("plain-access", storedAccess);
        assertNotEquals("plain-refresh", storedRefresh);

        OAuthCredential reloaded = repository.findById(saved.getId()).orElseThrow();
        assertEquals("plain-access", reloaded.getAccessToken());
        assertEquals("plain-refresh", reloaded.getRefreshToken());
        assertEquals("https://custom.example/revoke", reloaded.getRevokeEndpoint());
    }

    @Test
    @DisplayName("OAuth credential inventory projection returns redacted status flags")
    void inventoryProjectionReturnsRedactedStatusFlags() {
        OAuthCredential credential = new OAuthCredential();
        credential.setOwnerType("MAIL_ACCOUNT");
        credential.setOwnerId(UUID.randomUUID());
        credential.setProvider(OAuthProviderType.GOOGLE);
        credential.setTokenEndpoint("https://oauth2.googleapis.com/token");
        credential.setScope("https://mail.google.com/");
        credential.setCredentialKey("GMAIL");
        credential.setAccessToken("plain-access");
        credential.setRefreshToken("plain-refresh");
        credential.setTokenExpiresAt(LocalDateTime.now().plusHours(1));

        repository.saveAndFlush(credential);
        entityManager.clear();

        List<OAuthCredentialInventoryItem> inventory = repository.findInventoryItems(
            "MAIL_ACCOUNT",
            OAuthProviderType.GOOGLE
        );

        assertEquals(1, inventory.size());
        OAuthCredentialInventoryItem item = inventory.get(0);
        assertEquals("MAIL_ACCOUNT", item.ownerType());
        assertEquals(OAuthProviderType.GOOGLE, item.provider());
        assertTrue(item.tokenEndpointConfigured());
        assertFalse(item.tenantIdConfigured());
        assertTrue(item.scopeConfigured());
        assertTrue(item.credentialKeyConfigured());
        assertTrue(item.accessTokenStored());
        assertTrue(item.refreshTokenStored());
        assertTrue(item.connected());
        // Capability metadata is computed by the admin-service enrichment hook, never by JPQL.
        // The repository projection MUST return the defaults (false, null) so the service is
        // the single source of truth for revoke supportability.
        assertFalse(item.providerRevokeSupported());
        assertNull(item.providerRevokeUnsupportedReason());

        OAuthCredentialOwnerReference ownerReference = repository.findOwnerReferenceById(item.id()).orElseThrow();
        assertEquals(item.id(), ownerReference.id());
        assertEquals("MAIL_ACCOUNT", ownerReference.ownerType());
        assertEquals(item.ownerId(), ownerReference.ownerId());

        OAuthCredentialInventoryItem byId = repository.findInventoryItemById(item.id()).orElseThrow();
        assertEquals(item.id(), byId.id());
        assertTrue(byId.connected());
        assertFalse(byId.providerRevokeSupported());
        assertNull(byId.providerRevokeUnsupportedReason());
    }
}
