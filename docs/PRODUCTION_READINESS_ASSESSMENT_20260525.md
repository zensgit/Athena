# Athena ECM — Production Readiness Assessment

Date: 2026-05-25
Status: **read-only assessment.** No code/config/`.env` change by this document. Findings are grep/code-verified (file:line); items taken from a sub-agent scan were re-verified directly before inclusion.

## Verdict

**Functionally yes; operationally not-yet for external/internet-facing delivery.** Core ECM is genuinely implemented and tested; configuration, secrets, and security defaults are pre-production. **Internal/pilot deploy behind a trusted network is reasonable after the ~1–3 day hardening pass in §3.** "production-ready" in `CLAUDE.md` describes the *architecture*, not the deployment config.

## 1. What is production-grade (real, tested)

- Core ECM flows implemented (not stubs): upload/download (`ContentService` — NIO + SHA-256 dedup), versioning (`VersionService`), folders + smart folders (`FolderService`), Elasticsearch full-text + faceted search, per-node ACL (`SecurityService`), CMIS (10 capabilities under `cmis/`), records management, legal holds, transfer/replication.
- Test footprint: **311** backend `*Test.java`, **135** frontend `*.test.{ts,tsx}`, **78** Playwright e2e specs; **CI 7/7 green** (Backend Verify, Frontend Build & Test, Phase C Security, Frontend E2E Core, Acceptance Smoke, Phase 5 Mocked, Property-Encryption Closeout).
- Schema management: **96 Liquibase changesets** under `ecm-core/src/main/resources/db/changelog/` wired via `db.changelog-master.xml`.
- Containerised: 3 `docker-compose*.yml` + 4 Dockerfiles (ecm-core multi-stage, non-root, actuator healthcheck), Prometheus/Grafana, nginx with security headers + rate limiting.
- Property (metadata) encryption: real AES-GCM with key versions (`NodePropertyEncryptionService`).

## 2. Correction to an over-stated risk

A scan claimed live Google OAuth credentials were exposed in `.env.mail`. **Verified false as a repo risk:** `.env.mail` is **not git-tracked and never in history** (gitignored). The **tracked `.env` contains 0 OAuth-secret lines** — it holds infra *dev defaults*. So there is **no live-credential leak in the repository**. (If a local `.env.mail` holds real Gmail creds, that's local-machine hygiene — rotate if ever shared — but not a repo/history leak.)

## 3. Must-fix before any real / internet-facing deploy (verified)

| # | Issue | Sev | Evidence | Fix |
|---|---|---|---|---|
| 1 | `.env` **and** `ecm-frontend/.env` are git-tracked (in `.gitignore:106` but committed before ignore) with dev-default creds | High | `git ls-files` → `.env`, `ecm-frontend/.env` | `git rm --cached` both; keep only `*.example`; confirm history has no real secret; rotate the dev defaults for any shared env |
| 2 | Weak base-config defaults incl. `JWT_SECRET:mySecretKey` (forgeable tokens if env unset) + `ecm_password`/`elastic_password`/etc. | Critical | `application.yml:168` (JWT), `:8/:39/:47/:59/:290` (service pwds) | Require `JWT_SECRET` with **no default** (fail-fast); externalise all service passwords via env, no in-file defaults in the prod profile |
| 3 | `/actuator/**` and `/swagger-ui/**`, `/v3/api-docs/**` are `permitAll()` | High | `SecurityConfig.java:48-49` | In prod: restrict actuator to `health` (+`show-details: when-authorized`) / gate behind ADMIN; disable Swagger |
| 4 | CORS `setAllowedOrigins(List.of("*"))` | Med-High | `SecurityConfig.java:84` | Restrict to known frontend origin(s) per environment |
| 5 | No `application-prod.yml` / `docker-compose.prod.yml`; ES `xpack.security.enabled=false`; TLS not enforced | High | (absent) + compose | Add a prod profile (auth-on, ES security on, no debug logging) + TLS termination; enforce HTTPS for `/api/**` and `/api/v1/transfer/receiver/**` (plaintext creds otherwise) |
| 6 | `ml-service` container runs as root | Med | `ml-service/Dockerfile` (no `USER`) | Add a non-root user |
| 7 | No deployment runbook (README prod section is ~4 bullets, stale "2024") | Med | `README.md` | Write env-var reference, TLS/secrets/backup runbook |

CSRF disabled (`SecurityConfig.java:44`) is acceptable for a stateless JWT API.

## 4. Functional deferrals — scope-by-buyer (documented decisions, not bugs)

- **Content-at-rest encryption deferred** (`docs/adr/ADR-003`): blobs written plaintext (`ContentService`); at-rest protection delegated to storage backend SSE. Blocker only if the buyer needs app-layer envelope encryption; must be documented either way.
- **Per-tenant physical storage isolation deferred** (`docs/adr/ADR-001`): global shared dedup tree. Blocker for strict data-residency / hard-isolation (HIPAA/GDPR-residency/ITAR) buyers.
- **Transfer v1 excludes** delete propagation, permission-delta sync, alien-node handling (`CLAUDE.md`). Risk of orphaned/over-permissioned nodes on the target if relied on for full sync.
- **"Alfresco compatibility" overstated**: `AlfrescoContentService.java:103` (direct output stream) and `:125` (transformation) throw `UnsupportedOperationException`; `RuleEngineService` throws on unsupported action types. A drop-in-Alfresco buyer hits these.

## 5. Unverified (must close before go-live)

No **live full-stack smoke** (login → upload → search → permissions) has been run on a recorded machine — this box has no Docker, so verification has been unit/contract/CI-mocked only (consistent with the `CLAUDE.md` handoff note). CI green ≠ live-stack proven. Run a full-stack acceptance on a Docker-capable host before delivery.

## 6. Recommended next step

Implement a **minimal security-hardening slice** against §3 items 1–5 (the config/secret blockers), then a separate runbook doc. Items 1 (untrack `.env`) and 2 (secret defaults) have ops/secret-rotation implications, so they need explicit owner sign-off, not an autonomous change. The functional deferrals (§4) are product/scope decisions for the buyer conversation, not engineering bugs.

## 7. Non-goals of this document

- No code/config/`.env`/secret change here (read-only assessment).
- Does not itself open the hardening slice — that needs explicit go (touches prod security config + tracked-secret removal).
