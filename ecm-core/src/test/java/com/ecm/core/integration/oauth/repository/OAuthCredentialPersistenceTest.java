package com.ecm.core.integration.oauth.repository;

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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    }
}
