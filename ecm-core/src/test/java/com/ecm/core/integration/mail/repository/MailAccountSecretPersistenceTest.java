package com.ecm.core.integration.mail.repository;

import com.ecm.core.integration.mail.model.MailAccount;
import com.ecm.core.security.secret.SecretCryptoProperties;
import com.ecm.core.security.secret.SecretCryptoService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.liquibase.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:mailsecret;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "ecm.security.secret.enabled=true",
    "ecm.security.secret.active-key-version=v1",
    "ecm.security.secret.keys.v1=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@ContextConfiguration(classes = MailAccountSecretPersistenceTest.JpaTestConfig.class)
@Import({SecretCryptoProperties.class, SecretCryptoService.class})
class MailAccountSecretPersistenceTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = MailAccount.class)
    @EnableJpaRepositories(basePackageClasses = MailAccountRepository.class)
    static class JpaTestConfig {
    }

    @Autowired
    private MailAccountRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Mail account secret columns are stored encrypted and loaded decrypted")
    void storesEncryptedColumnsAndLoadsDecryptedValues() {
        MailAccount account = new MailAccount();
        account.setCreatedBy("test");
        account.setCreatedDate(LocalDateTime.now());
        account.setName("gmail");
        account.setHost("imap.gmail.com");
        account.setPort(993);
        account.setUsername("user@gmail.com");
        account.setPassword("plain-password");
        account.setSecurity(MailAccount.SecurityType.OAUTH2);
        account.setOauthProvider(MailAccount.OAuthProvider.GOOGLE);
        account.setOauthClientSecret("client-secret");
        account.setOauthAccessToken("access-token");
        account.setOauthRefreshToken("refresh-token");

        MailAccount saved = repository.saveAndFlush(account);
        entityManager.clear();

        String storedPassword = jdbcTemplate.queryForObject(
            "select password from mail_accounts where id = ?",
            String.class,
            saved.getId()
        );
        String storedRefreshToken = jdbcTemplate.queryForObject(
            "select oauth_refresh_token from mail_accounts where id = ?",
            String.class,
            saved.getId()
        );

        assertTrue(storedPassword.startsWith("enc:v1:"));
        assertTrue(storedRefreshToken.startsWith("enc:v1:"));
        assertNotEquals("plain-password", storedPassword);
        assertNotEquals("refresh-token", storedRefreshToken);

        MailAccount reloaded = repository.findById(saved.getId()).orElseThrow();
        assertEquals("plain-password", reloaded.getPassword());
        assertEquals("client-secret", reloaded.getOauthClientSecret());
        assertEquals("access-token", reloaded.getOauthAccessToken());
        assertEquals("refresh-token", reloaded.getOauthRefreshToken());
    }
}
