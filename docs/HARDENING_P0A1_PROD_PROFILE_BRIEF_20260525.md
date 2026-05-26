# P0a-1 — Prod Profile Hardening (A1/A2/A3/A12) — Implementation Brief (read-only)

Date: 2026-05-25
Status: **read-only brief — no code/config/`.env`/secret change by this document.** Revision: **v2** (gate findings folded in). Awaiting re-gate.
Parent: §8 Hardening Matrix in `docs/PRODUCTION_READINESS_ASSESSMENT_20260525.md`. Covers matrix rows **A1, A2, A3, A12** (the highest-value, lowest-coupling P0a knife).

## Revision history

- **v1 → v2** (gate round 1, all findings verified directly):
  - **Blocker — A2 was wrong:** `ecm.security.jwt.secret`/`JWT_SECRET` has **zero Java consumers** (verified: real auth is `jwk-set-uri` → `NimbusJwtDecoder.withJwkSetUri`, `SecurityConfig.java:37,78`). The whole `ecm.security.jwt` block (`secret`+`expiration`, `application.yml:166-169`) is dead. **A2 reframed:** fail-fast on the *real* path (`issuer-uri`/`jwk-set-uri`, no localhost default) + **delete the dead jwt block** as legacy cleanup. No `JWT_SECRET` env requirement.
  - **Medium + D3 — A3 must reach code/base literals, not just the prod profile:** credential-like defaults live in `application.yml:290` (`odoo … password: admin_password`), `WpsIntegrationService.java:51` (`${ecm.wps.appkey:secret_key}`), `OdooIntegrationService.java:32` (`${ecm.odoo.password:admin}`). A prod profile only masks these at runtime. **A3 expanded** to env-source Odoo+WPS in the prod profile **and** remove the credential-like literal defaults at source. Verified no test depends on them; `@Value` empty default does not break bean creation.

## 0. Scope (locked)

Create a **hardened `prod` Spring profile** that fixes the schema-mutation and weak-default-secret risks, and correct the README delivery posture. Nothing else.

- **A1** — prod profile sets `spring.jpa.hibernate.ddl-auto: validate` (Liquibase owns schema).
- **A2 (reframed)** — fail-fast on the **real** auth inputs: prod profile sets `spring.security.oauth2.resourceserver.jwt.issuer-uri` + `jwk-set-uri` to `${...}` with **no localhost/keycloak default** (this is what `NimbusJwtDecoder` actually uses). **Legacy cleanup:** delete the dead `ecm.security.jwt` block (`secret`+`expiration`) from base `application.yml` — verified zero consumers. **No `JWT_SECRET` env var** (it was dead).
- **A3 (expanded)** — eliminate weak/secret credential defaults two ways: (a) prod profile env-sources **every** credential with no in-file default (datasource / ES / Redis / RabbitMQ / **Odoo** / **WPS**); (b) remove the credential-like literal defaults at source so no profile ships a secret literal: base `application.yml:290` Odoo password, `WpsIntegrationService.java:51` (`secret_key`), `OdooIntegrationService.java:32` (`admin`).
- **A12** — `README.md` "Production Ready" → honest delivery posture matching the assessment verdict.

## 1. Coupling resolution (gate ratifies here)

A1/A2/A3 are "*the prod profile* sets X" → they need `application-prod.yml`, but matrix **A7 (P0a-3) listed "create application-prod.yml."** **Resolution adopted by this brief: P0a-1 creates `application-prod.yml`** (natural home for A1–A3); **A7/P0a-3 narrows to `docker-compose.prod.yml` + compose-level items**; **P0a-2 *extends* the prod profile** (actuator exposure) — it does not re-create it. Gate: confirm this split (or send me back to the alt where A7 owns the file).

## 2. Zero-regression rationale (important)

`application-prod.yml` is **inert** unless `spring.profiles.active=prod`. CI/tests run the `test` profile; the deployed gate runs `docker`. **Adding this file changes no current behavior** — dev/docker/test/CI are untouched. Risk to existing flows ≈ none; this only *provides* a hardened profile to activate later.

## 3. Files

### Create
- `ecm-core/src/main/resources/application-prod.yml` — overrides over base `application.yml`:
  - `spring.jpa.hibernate.ddl-auto: validate` (A1; base already `validate`, but `docker` overrides to `update` at `application-docker.yml:13` — prod re-pins explicitly).
  - All service credentials as bare `${ENV}` **no default**: `spring.datasource.password` (base `:8`), `spring.elasticsearch` creds (base `:39`), `spring.data.redis.password` (base `:47`), `spring.rabbitmq.password` (base `:59`), **`ecm.odoo.password: ${ECM_ODOO_PASSWORD}`** (base literal `:290`), **`ecm.wps.appkey: ${ECM_WPS_APPKEY}`** (code default `WpsIntegrationService:51`). (A3)
  - `spring.security.oauth2.resourceserver.jwt.issuer-uri` / `jwk-set-uri`: `${...}` **no localhost/keycloak default** (A2 — the real token-validation inputs; base `:72-73` / docker `:32-33` default to `http://keycloak:8080`).
  - **No** `ecm.security.jwt.secret` line (that key is dead — see Modify).
- `ecm-core/src/test/java/com/ecm/core/config/ProdProfileHardeningTest.java` — see §4.

### Modify
- `ecm-core/src/main/resources/application.yml` — **delete the dead `ecm.security.jwt` block** (`secret`+`expiration`, `:166-169`; verified zero consumers) [A2 cleanup]; change the Odoo password literal `:290` from `admin_password` to `${ECM_ODOO_PASSWORD:}` (no secret literal even in base) [A3].
- `ecm-core/src/main/java/com/ecm/core/integration/wps/service/WpsIntegrationService.java:51` — `${ecm.wps.appkey:secret_key}` → `${ecm.wps.appkey:}` (empty default) [A3].
- `ecm-core/src/main/java/com/ecm/core/integration/odoo/OdooIntegrationService.java:32` — `${ecm.odoo.password:admin}` → `${ecm.odoo.password:}` (empty default) [A3]. (`OdooService.java:33` already has no default — leave it.)
- `README.md` — A12 posture wording only. Replace bare "Production Ready" with the assessment posture (architecture production-grade; config pre-production; internal-UAT-now / pilot needs P0a+S1/S2 / external needs P0b+B4), and link `docs/PRODUCTION_READINESS_ASSESSMENT_20260525.md`.

### Explicitly NOT touched
- `application-docker.yml`, `application-dev.yml` (dev/docker behavior unchanged), `SecurityConfig.java` (A4/A5/A6 = P0a-2), `docker-compose*.yml` (P0a-3), any `.env`/secret (S1/S2), `OdooService.java`, property-encryption keys, any business logic.
- **Regression safety (verified):** the credential-default edits change only `@Value` fallback strings (empty default doesn't break bean creation); no test references `secret_key`/`admin_password`/the odoo/wps defaults; the dead `ecm.security.jwt` block has no consumer. `application-prod.yml` is inert unless `spring.profiles.active=prod`, so dev/docker/test/CI behavior is unchanged.

## 4. Test (CI-verifiable, no full boot)

`ProdProfileHardeningTest` — load **only** `application-prod.yml` as a property source (e.g. `YamlPropertySourceLoader` over the classpath resource; **do not** start a Spring context / DB / ES) and assert:
- `spring.jpa.hibernate.ddl-auto` == `validate`.
- datasource/redis/rabbitmq/ES password + `ecm.odoo.password` + `ecm.wps.appkey` are bare `${...}` with **no** `:default` segment.
- `issuer-uri`/`jwk-set-uri` contain no `keycloak`/`localhost` default.
- `ecm.security.jwt` is **absent** from the prod profile.

Plus **source-literal assertions** (read the touched files as text, no boot): base `application.yml` contains no `ecm.security.jwt` block and no `admin_password` literal; `WpsIntegrationService.java` has no `:secret_key}` default; `OdooIntegrationService.java` has no `:admin}` password default.

All **config-content** verification (matrix CI-verifiable = Y, unit). **Full prod-profile boot stays B4** (needs real Keycloak/DB/ES — off-box). The test must not require the stack.

## 5. Verification plan

- `scripts/backend-preflight.sh -Dtest=ProdProfileHardeningTest test` (local, ~12s) green.
- `scripts/backend-preflight.sh` (test-compile) green.
- `git diff --check -- . ':!.env'` clean; no `.env`/`docker-compose*`/`SecurityConfig.java` in the diff.
- CI 7/7 (the new test runs under Backend Verify; `prod` profile remains inert for all other gates; the `@Value`-default + dead-block edits are exercised by existing compile/tests).

## 6. Out of scope (no scope creep)

- A4/A5/A6 (actuator/swagger/CORS gating) → **P0a-2**.
- A7–A11 (docker-compose.prod.yml, ports, image pinning, ES/MinIO/Grafana runtime security, ml-service non-root) → **P0a-3**.
- **S1** (`git rm --cached .env`/`ecm-frontend/.env`) and **S2** (secret rotation/custodian) → owner-confirm, **not in this slice**.
- **P0b** (Keycloak prod, TLS, backup/restore, B4 smoke) → owner/env, not claimed from this box.
- No change to property-encryption keys, no new features.

## 7. Gate decisions

Round 1 (all approved; folded into v2):
- **D1 — approved:** P0a-1 owns `application-prod.yml`; A7/P0a-3 narrows to compose/runtime shape.
- **D2 — approved with the A2 correction:** config-content assertion is the CI proof for A1/A2/A3; full boot stays B4. A2 reframed (jwk/issuer fail-fast + dead-jwt cleanup; no `JWT_SECRET`).
- **D3 — approved (include Odoo):** Odoo covered in both base yaml (`:290`) and code (`OdooIntegrationService:32`).

New for v2 (please confirm):
- **D4:** v2 expands the file set beyond a single new yml — it now also edits base `application.yml` (delete dead jwt block + Odoo literal) and two integration `@Value` defaults (`WpsIntegrationService:51`, `OdooIntegrationService:32`). Confirm this stays within P0a-1 (recommended — it's what makes A3 *real*), or split the source-literal removals into a separate small row.

## 8. Commit cadence (after gate)

1. `feat(core): hardened prod Spring profile + remove weak credential defaults (ddl-auto=validate, no-default creds, dead-jwt cleanup)` + the config-content test.
2. `docs(core): record P0a-1 prod profile verification`.
3. Push, gate CI via `gh run view`; on 7/7, `docs(core): record CI … [skip ci]`.
(A12 README change rides in commit 1 or its own `docs(core)` — implementer's call at commit time.)

## 9. Verification (this brief)

```bash
git status --short                              # M .env + this brief only
git diff --stat -- 'ecm-core/' 'ecm-frontend/'  # empty
```
