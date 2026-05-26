# P0a-2 - Prod Security Exposure Hardening (A4/A5/A6) - Implementation Brief (read-only)

Date: 2026-05-26
Status: **read-only brief - no code/config/`.env`/secret change by this document.** Awaiting gate.
Parent: section 8 Hardening Matrix in `docs/PRODUCTION_READINESS_ASSESSMENT_20260525.md`.
Prerequisite: P0a-1 closed at `7b7b245` (prod profile exists; CI `26429050412` success 7/7).

## 0. Scope (locked)

Implement the next P0a hardening knife: prod-only exposure controls for management, docs, and browser origins.

- **A4** - In prod, `/actuator/health` remains public; other `/actuator/**` routes are no longer anonymous and require `ROLE_ADMIN`.
- **A5** - In prod, Swagger UI and OpenAPI docs are disabled and are not permit-all.
- **A6** - In prod, CORS uses an explicit allowed-origin list from env/config, not `*`.

This slice must preserve current dev/docker/test behavior by default. The tightening is activated through the prod profile / prod-like properties only.

## 1. Evidence from current code

- `SecurityConfig.java:48` currently permits anonymous `/actuator/**`.
- `SecurityConfig.java:49` currently permits anonymous `/swagger-ui/**` and `/v3/api-docs/**`.
- `SecurityConfig.java:82-98` currently constructs CORS with `allowedOrigins = List.of("*")`, ignoring the existing config value at `application.yml:175-176`.
- `application-prod.yml` exists after P0a-1 and is the natural place for prod overrides.
- `SecurityConfigProtocolSecurityTest` already imports the real `SecurityConfig` in a focused `@WebMvcTest`; P0a-2 should follow that pattern rather than booting the full app.

## 2. Proposed implementation

### 2.1 SecurityConfig becomes config-driven, default-compatible

Modify `SecurityConfig.java` only, with default values matching today's behavior:

- Add a boolean property for anonymous actuator exposure, default `true`.
  - Suggested key: `ecm.security.exposure.actuator-permit-all`
  - Default: `true`
  - Prod: `false`
- Add a boolean property for public Swagger/OpenAPI exposure, default `true`.
  - Suggested key: `ecm.security.exposure.swagger-permit-all`
  - Default: `true`
  - Prod: `false`
- Read CORS origins from existing `ecm.security.cors.allowed-origins`, default `*`.
  - Suggested injection: `@Value("${ecm.security.cors.allowed-origins:*}")`
  - Parse comma-separated values, trim blanks, reject empty effective list.
  - Keep allowed methods/headers/exposed headers unchanged in this slice.

Authorization shape:

```java
if (actuatorPermitAll) {
    auth.requestMatchers("/actuator/**").permitAll();
} else {
    auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
    auth.requestMatchers("/actuator/**").hasRole("ADMIN");
}

if (swaggerPermitAll) {
    auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll();
}
```

Rationale: no new filter chain, no profile-specific Java class, no split between dev/test/prod code paths. The same `SecurityConfig` is exercised by tests with different properties.

### 2.2 application-prod.yml extends P0a-1 profile

Modify `ecm-core/src/main/resources/application-prod.yml`:

- `ecm.security.exposure.actuator-permit-all: false`
- `ecm.security.exposure.swagger-permit-all: false`
- `ecm.security.cors.allowed-origins: ${ECM_SECURITY_CORS_ALLOWED_ORIGINS}`
- `springdoc.api-docs.enabled: false`
- `springdoc.swagger-ui.enabled: false`
- `management.endpoints.web.exposure.include: health`
- `management.endpoint.health.show-details: when_authorized`

The `springdoc.*` entries are defense in depth: the security matcher stops anonymous access, and Springdoc also does not publish docs in prod.

### 2.3 application.yml stays default-compatible

Do not remove or alter base defaults in `application.yml` in this slice. Current dev/docker/CI expectations remain intact. If a later cleanup wants config properties in base YAML for discoverability, do that separately.

## 3. Tests

### 3.1 New focused prod exposure test

Create `ecm-core/src/test/java/com/ecm/core/config/SecurityConfigProdExposureTest.java`.

Use the same style as `SecurityConfigProtocolSecurityTest`:

- `@WebMvcTest(controllers = SecurityConfigProdExposureTest.ProdExposureProbeController.class)`
- `@ContextConfiguration(classes = { SecurityConfig.class, ProdExposureProbeController.class })`
- `@TestPropertySource` with:
  - `spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/.well-known/jwks.json`
  - `ecm.security.exposure.actuator-permit-all=false`
  - `ecm.security.exposure.swagger-permit-all=false`
  - `ecm.security.cors.allowed-origins=https://athena.example.com,https://admin.example.com`

Probe endpoints:

- `/actuator/health`
- `/actuator/metrics`
- `/swagger-ui/index.html`
- `/v3/api-docs`
- `/api/v1/protected/probe`

Assertions:

- anonymous `GET /actuator/health` -> 200
- anonymous `GET /actuator/metrics` -> 401
- `@WithMockUser(roles = "USER") GET /actuator/metrics` -> 403
- `@WithMockUser(roles = "ADMIN") GET /actuator/metrics` -> 200
- anonymous `GET /swagger-ui/index.html` -> 401
- anonymous `GET /v3/api-docs` -> 401
- ordinary `/api/**` stays protected -> 401 anonymous
- CORS request from `https://athena.example.com` gets `Access-Control-Allow-Origin: https://athena.example.com`
- CORS request from `https://evil.example.com` does not get `Access-Control-Allow-Origin`

### 3.2 Existing protocol test must remain green

`SecurityConfigProtocolSecurityTest` remains a default-compatible guard:

- transfer receiver and WOPI opaque-token paths still permit anonymous access
- CMIS and ordinary API paths remain authenticated

If P0a-2 accidentally changes default behavior, this test should fail.

### 3.3 Prod profile config-content extension

Extend `ProdProfileHardeningTest` rather than creating a second YAML parser test:

- `application-prod.yml` has `ecm.security.exposure.actuator-permit-all=false`
- `application-prod.yml` has `ecm.security.exposure.swagger-permit-all=false`
- `application-prod.yml` has `ecm.security.cors.allowed-origins=${ECM_SECURITY_CORS_ALLOWED_ORIGINS}` with no default
- `springdoc.api-docs.enabled=false`
- `springdoc.swagger-ui.enabled=false`
- `management.endpoints.web.exposure.include=health`
- `management.endpoint.health.show-details=when_authorized`

## 4. Verification plan

- `scripts/backend-preflight.sh -Dtest=SecurityConfigProdExposureTest,ProdProfileHardeningTest,SecurityConfigProtocolSecurityTest test`
- `scripts/backend-preflight.sh`
- `git diff --check -- . ':!.env'`
- CI 7/7 via `gh run view` authority.

## 5. Out of scope

- No `.env`, `ecm-frontend/.env`, secret rotation, or credential custody (S1/S2).
- No `docker-compose.prod.yml`, port publishing, image pinning, ES/MinIO/Grafana runtime security, or ml-service non-root (P0a-3).
- No Keycloak prod realm, TLS, backup/restore, or hardened full-stack smoke (P0b/B4).
- No changes to transfer receiver or WOPI permit-all protocol seams.
- No frontend code.
- No business-controller security policy changes beyond the global management/docs/CORS exposure controls.

## 6. Gate decisions

- **D1 - property names:** approve `ecm.security.exposure.actuator-permit-all`, `ecm.security.exposure.swagger-permit-all`, and reuse `ecm.security.cors.allowed-origins`.
- **D2 - actuator policy:** approve public health only; non-health actuator requires `ROLE_ADMIN` in prod-like properties.
- **D3 - Swagger policy:** approve both security-side non-permitAll and `springdoc.*.enabled=false` in prod.
- **D4 - CORS policy:** approve prod fail-fast for `ECM_SECURITY_CORS_ALLOWED_ORIGINS` (no default) while base/dev default remains `*`.

## 7. Commit cadence (after gate)

1. `feat(core): harden prod security exposure controls (actuator swagger cors)` with code + tests.
2. `docs(core): record P0a-2 prod security exposure verification`.
3. Push, gate CI via `gh run view`; on 7/7, `docs(core): record CI for P0a-2 prod security exposure [skip ci]`.

## 8. Verification (this brief)

```bash
git status --short                              # M .env + this brief only
git diff --stat -- 'ecm-core/' 'ecm-frontend/'  # empty before implementation
```
