# P0a-1 — Prod Profile Hardening (A1/A2/A3/A12) — Verification

Date: 2026-05-25
Brief: `docs/HARDENING_P0A1_PROD_PROFILE_BRIEF_20260525.md` (v2, gate-approved; D1/D2/D3 + D4-with-boundary).
Parent matrix: §8 of `docs/PRODUCTION_READINESS_ASSESSMENT_20260525.md`.

## Changes shipped (commit `e4f1b4c`)

- **`application-prod.yml` (new, inert unless `SPRING_PROFILES_ACTIVE=prod`):**
  - **A1** `spring.jpa.hibernate.ddl-auto: validate` (Liquibase owns schema; vs the `docker` profile's `update`).
  - **A3 (required infra)** `spring.datasource` / `spring.elasticsearch` / `spring.data.redis` / `spring.rabbitmq` creds = bare `${ENV}` with **no default** → fail-fast on missing.
  - **A2** `spring.security.oauth2.resourceserver.jwt.issuer-uri` + `jwk-set-uri` = `${ENV}` with **no localhost/keycloak default** (the real `NimbusJwtDecoder` inputs).
- **Base `application.yml`:** deleted the dead `ecm.security.jwt` block (`secret`+`expiration`; no Java consumer — verified) [A2]; Odoo `password: admin_password` → `${ECM_ODOO_PASSWORD:}` (env, empty default; url/db/username also env-defaulted) [A3].
- **`WpsIntegrationService.java:51`** `${ecm.wps.appkey:secret_key}` → `${ecm.wps.appkey:}` [A3].
- **`OdooIntegrationService.java:32`** `${ecm.odoo.password:admin}` → `${ecm.odoo.password:}` [A3].
- **`README.md`** status → honest delivery posture, linking the readiness assessment [A12].

## Gate D4 boundary honored

- **Required infra creds (DB/ES/Redis/RabbitMQ):** no-default `${ENV}` → fail-fast.
- **Optional integration creds (Odoo/WPS):** weak source defaults removed (empty default); the prod profile does **not** force `ECM_ODOO_PASSWORD`/`ECM_WPS_APPKEY`, so an unused integration does not crash startup. `ProdProfileHardeningTest` asserts these keys are **absent** from the prod profile.
- Test focus = "no weak-default literal remains," not "all optional integrations configured."

## Tests

`ProdProfileHardeningTest` (config-content; `YamlPropertySourceLoader` + source-text reads; **no Spring context / DB / ES / Keycloak**):
1. prod profile: `ddl-auto=validate`; the 4 infra-cred passwords are no-default `${ENV}`; issuer/jwk no localhost default; `ecm.odoo.password`/`ecm.wps.appkey` **not** present (D4); dead `ecm.security.jwt.secret` absent.
2. no weak-default literals: base `application.yml` has no `ecm.security.jwt.secret` and no `admin_password`; `WpsIntegrationService` has no `:secret_key}`; `OdooIntegrationService` has no `ecm.odoo.password:admin}`.

## Local verification (via E1 `scripts/backend-preflight.sh`)

```
backend-preflight.sh -Dtest=ProdProfileHardeningTest test ... Tests run: 2, Failures: 0 — BUILD SUCCESS (~12s)
backend-preflight.sh   (full test-compile) ................. BUILD SUCCESS (~10s; @Value/base-yaml edits compile clean)
git diff --check -- . ':!.env' ............................. clean
```

(First slice dogfooding the E1 helper for local pre-CI backend verification — the gap that caused earlier CI churn.)

## Scope / non-goals

- No `SecurityConfig.java` (A4/A5/A6 = P0a-2), no `docker-compose*.yml` (A7–A11 = P0a-3), no `.env`/secret (S1/S2), no `OdooService.java` (already no-default), no business logic.
- **Runtime proof of the hardened profile booting (real Keycloak/DB/ES) stays gate item B4** (off-box) — this slice proves config content, not a live boot.

## CI Follow-Up

```
Run id:        26429050412
Head SHA:      3b13d99e
Conclusion:    success (7/7 — gh run view authority per feedback_gh_run_watch_unreliable)
URL:           https://github.com/zensgit/Athena/actions/runs/26429050412

Jobs (7/7 green — incl. the Docker-backed gates that boot the `docker` profile, confirming the
base application.yml + @Value edits caused no regression; the inert `prod` profile is untouched by CI):
  ✓ Backend Verify
  ✓ Frontend Build & Test
  ✓ Phase C Security Verification
  ✓ Phase 5 Mocked Regression Gate
  ✓ Frontend E2E Core Gate
  ✓ Property Encryption Closeout Gate
  ✓ Acceptance Smoke (3 admin pages)
```
