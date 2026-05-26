package com.ecm.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Config-content verification for P0a-1 (matrix A1/A2/A3). Loads the YAML as a property source
 * (placeholders left unresolved) and reads the touched integration sources as text — no Spring
 * context, DB, ES or Keycloak. Full prod-profile boot is gate item B4 (off-box).
 *
 * <p>Boundary (gate D4): required infra creds (DB/ES/Redis/RabbitMQ) are no-default `${ENV}` →
 * fail-fast; optional integration creds (Odoo/WPS) are NOT force-required by the prod profile —
 * their weak source defaults are merely removed (empty default), so an unused integration does
 * not block startup.
 */
class ProdProfileHardeningTest {

    private static final String NO_DEFAULT_ENV = "\\$\\{[A-Z0-9_]+\\}";

    private PropertySource<?> loadYaml(String resource) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(resource, new ClassPathResource(resource));
        assertFalse(sources.isEmpty(), resource + " should load as a property source");
        return sources.get(0);
    }

    private String prop(PropertySource<?> ps, String key) {
        Object v = ps.getProperty(key);
        return v == null ? null : v.toString();
    }

    private String readSource(String relPath) throws IOException {
        Path p = Path.of(relPath); // surefire/maven basedir is the ecm-core module
        assertTrue(Files.exists(p), "source file must exist (basedir=ecm-core): " + relPath);
        return Files.readString(p);
    }

    @Test
    @DisplayName("prod profile: ddl-auto=validate, required infra creds no-default, jwk/issuer no localhost, no dead jwt key, optional integrations not forced")
    void prodProfileHardened() throws IOException {
        PropertySource<?> prod = loadYaml("application-prod.yml");

        assertEquals("validate", prop(prod, "spring.jpa.hibernate.ddl-auto"), "A1: prod must validate, not mutate, schema");

        // A3 — required infrastructure creds: bare ${ENV}, no ":" default → fail-fast.
        for (String key : List.of(
                "spring.datasource.password",
                "spring.elasticsearch.password",
                "spring.data.redis.password",
                "spring.rabbitmq.password")) {
            String v = prop(prod, key);
            assertNotNull(v, key + " must be set in prod profile");
            assertTrue(v.matches(NO_DEFAULT_ENV), key + " must be a no-default ${ENV} placeholder, was: " + v);
        }

        // A2 — real auth inputs: no localhost/keycloak default.
        for (String key : List.of(
                "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri")) {
            String v = prop(prod, key);
            assertNotNull(v, key + " must be set");
            assertFalse(v.contains("keycloak") || v.contains("localhost"), key + " must not carry a localhost default: " + v);
            assertTrue(v.matches(NO_DEFAULT_ENV), key + " must be a no-default placeholder: " + v);
        }

        // D4 — optional integrations are NOT force-required by the prod profile.
        assertNull(prop(prod, "ecm.odoo.password"), "prod profile must not hard-require ecm.odoo.password");
        assertNull(prop(prod, "ecm.wps.appkey"), "prod profile must not hard-require ecm.wps.appkey");

        // A2 — dead jwt key absent from the prod profile.
        assertNull(prop(prod, "ecm.security.jwt.secret"), "dead ecm.security.jwt.secret must not appear");
    }

    @Test
    @DisplayName("no weak-default credential literals remain in base config or integration code")
    void noWeakDefaultLiterals() throws IOException {
        PropertySource<?> base = loadYaml("application.yml");
        // A2 — dead jwt block removed.
        assertNull(prop(base, "ecm.security.jwt.secret"), "dead ecm.security.jwt.secret must be removed from base");
        // A3 — Odoo password env-sourced, no admin_password literal default.
        String odoo = prop(base, "ecm.odoo.password");
        assertNotNull(odoo, "ecm.odoo.password must be present (env-sourced)");
        assertFalse(odoo.contains("admin_password"), "ecm.odoo.password must not default to admin_password: " + odoo);

        // A3 — integration @Value defaults must carry no weak secret literal.
        String wps = readSource("src/main/java/com/ecm/core/integration/wps/service/WpsIntegrationService.java");
        assertFalse(wps.contains(":secret_key}"), "WpsIntegrationService must not default ecm.wps.appkey to secret_key");
        String odooSvc = readSource("src/main/java/com/ecm/core/integration/odoo/OdooIntegrationService.java");
        assertFalse(odooSvc.contains("ecm.odoo.password:admin}"), "OdooIntegrationService must not default ecm.odoo.password to admin");
    }
}
