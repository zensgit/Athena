# P0a-1 — Prod Profile Hardening (A1/A2/A3/A12) — Implementation Brief (read-only)

Date: 2026-05-25
Status: **read-only brief — no code/config/`.env`/secret change by this document.** Awaiting gate.
Parent: §8 Hardening Matrix in `docs/PRODUCTION_READINESS_ASSESSMENT_20260525.md`. Covers matrix rows **A1, A2, A3, A12** (the highest-value, lowest-coupling P0a knife).

## 0. Scope (locked)

Create a **hardened `prod` Spring profile** that fixes the schema-mutation and weak-default-secret risks, and correct the README delivery posture. Nothing else.

- **A1** — prod profile sets `spring.jpa.hibernate.ddl-auto: validate` (Liquibase owns schema).
- **A2** — prod profile makes secrets **fail-fast**: `${JWT_SECRET}` with **no default**; OAuth2 issuer/jwk URIs **no localhost default**.
- **A3** — prod profile sources every service credential via env with **no in-file default** (datasource / ES / Redis / RabbitMQ / Odoo).
- **A12** — `README.md` "Production Ready" → honest delivery posture matching the assessment verdict.

## 1. Coupling resolution (gate ratifies here)

A1/A2/A3 are "*the prod profile* sets X" → they need `application-prod.yml`, but matrix **A7 (P0a-3) listed "create application-prod.yml."** **Resolution adopted by this brief: P0a-1 creates `application-prod.yml`** (natural home for A1–A3); **A7/P0a-3 narrows to `docker-compose.prod.yml` + compose-level items**; **P0a-2 *extends* the prod profile** (actuator exposure) — it does not re-create it. Gate: confirm this split (or send me back to the alt where A7 owns the file).

## 2. Zero-regression rationale (important)

`application-prod.yml` is **inert** unless `spring.profiles.active=prod`. CI/tests run the `test` profile; the deployed gate runs `docker`. **Adding this file changes no current behavior** — dev/docker/test/CI are untouched. Risk to existing flows ≈ none; this only *provides* a hardened profile to activate later.

## 3. Files

### Create
- `ecm-core/src/main/resources/application-prod.yml` — overrides over base `application.yml`:
  - `spring.jpa.hibernate.ddl-auto: validate` (A1; base is already `validate`, but `docker` overrides to `update` at `application-docker.yml:13` — prod re-pins explicitly).
  - `spring.datasource.password: ${SPRING_DATASOURCE_PASSWORD}` (no default); same for `username`/`url` as needed. (A3; base default `application.yml:8`.)
  - `spring.elasticsearch` credentials no default (base `:39`); `spring.data.redis.password: ${SPRING_DATA_REDIS_PASSWORD}` (base `:47`); `spring.rabbitmq.password: ${SPRING_RABBITMQ_PASSWORD}` (base `:59`); Odoo password no default (base `:290` — implementer grep-confirms exact key path).
  - `ecm.security.jwt.secret: ${JWT_SECRET}` — **no default** (A2; base `application.yml:168` = `${JWT_SECRET:mySecretKey}`).
  - `spring.security.oauth2.resourceserver.jwt.issuer-uri` / `jwk-set-uri`: `${...}` **no localhost default** (A2; base `:72-73` / docker `:32-33` default to `http://keycloak:8080`).
- `ecm-core/src/test/java/com/ecm/core/config/ProdProfileHardeningTest.java` — see §4.

### Modify
- `README.md` — A12 posture wording only (no other content). Replace any bare "Production Ready" with the assessment's posture (architecture production-grade; config pre-production; internal-UAT-now / pilot needs P0a+S1/S2 / external needs P0b+B4), and link `docs/PRODUCTION_READINESS_ASSESSMENT_20260525.md`.

### Explicitly NOT touched
- `application.yml`, `application-docker.yml`, `application-dev.yml` (base/dev/docker behavior unchanged), `SecurityConfig.java`, `docker-compose*.yml`, any `.env`, any production Java logic.

## 4. Test (CI-verifiable, no full boot)

`ProdProfileHardeningTest` — load **only** `application-prod.yml` as a property source (e.g. `YamlPropertySourceLoader` / `Binder` over the classpath resource; **do not** start a Spring context / DB / ES) and assert:
- `spring.jpa.hibernate.ddl-auto` == `validate`.
- `ecm.security.jwt.secret` raw value == `${JWT_SECRET}` (contains no `:` default segment, i.e. not `${JWT_SECRET:...}`).
- datasource/redis/rabbitmq/ES/Odoo password values are bare `${...}` with no `:default`.
- issuer-uri/jwk-set-uri contain no `http://keycloak` / `localhost` default.

This is **config-content** verification (matrix CI-verifiable = Y, unit). **Full prod-profile boot stays B4** (needs real Keycloak/DB/ES — off-box). The test must not require the stack.

## 5. Verification plan

- `scripts/backend-preflight.sh -Dtest=ProdProfileHardeningTest test` (local, ~12s) green.
- `scripts/backend-preflight.sh` (test-compile) green.
- `git diff --check -- . ':!.env'` clean; no `.env`/`docker-compose`/`SecurityConfig` in the diff.
- CI 7/7 (the new test runs under Backend Verify; `prod` profile remains inert for all other gates).

## 6. Out of scope (no scope creep)

- A4/A5/A6 (actuator/swagger/CORS gating) → **P0a-2**.
- A7–A11 (docker-compose.prod.yml, ports, image pinning, ES/MinIO/Grafana runtime security, ml-service non-root) → **P0a-3**.
- **S1** (`git rm --cached .env`/`ecm-frontend/.env`) and **S2** (secret rotation/custodian) → owner-confirm, **not in this slice**.
- **P0b** (Keycloak prod, TLS, backup/restore, B4 smoke) → owner/env, not claimed from this box.
- No change to property-encryption keys, no new features.

## 7. Gate decisions

- **D1:** confirm §1 coupling resolution (P0a-1 owns `application-prod.yml`; A7 → compose-only).
- **D2:** confirm the §4 test is **config-content assertion** (no context boot), acceptable as the CI proof for A1/A2/A3, with runtime deferred to B4.
- **D3:** confirm A3 covers the Odoo credential too (base `:290`), or scope it out if Odoo is considered optional/integration-only.

## 8. Commit cadence (after gate)

1. `feat(core): add hardened prod Spring profile (ddl-auto=validate, fail-fast secrets)` + the config-content test.
2. `docs(core): record P0a-1 prod profile verification`.
3. Push, gate CI via `gh run view`; on 7/7, `docs(core): record CI … [skip ci]`.
(A12 README change rides in commit 1 or its own `docs(core)` — implementer's call at commit time.)

## 9. Verification (this brief)

```bash
git status --short                              # M .env + this brief only
git diff --stat -- 'ecm-core/' 'ecm-frontend/'  # empty
```
