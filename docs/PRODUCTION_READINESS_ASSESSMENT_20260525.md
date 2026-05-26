# Athena ECM — Production Readiness Assessment

Date: 2026-05-25
Status: **read-only assessment.** No code/config/`.env` change by this document. Findings are grep/code-verified (file:line); items taken from a sub-agent scan were re-verified directly before inclusion.

## Verdict

**Functionally yes; operationally not-yet for external/internet-facing delivery.** Core ECM is genuinely implemented and tested; configuration, secrets, and security defaults are pre-production. Delivery posture (single source: §8.5): **internal UAT with non-real data on a controlled network is deliverable now**; **any pilot involving real data or a non-controlled network requires P0a + S1/S2 first**; external/public production additionally requires P0b + a green hardened-config smoke. "production-ready" in `CLAUDE.md` describes the *architecture*, not the deployment config.

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

No live full-stack smoke has been run **on a hardened, production-shaped config**. CI **does** run a Docker-backed Acceptance Smoke + Frontend E2E Core (currently green) — but on the **dev/CI compose shape**, not the hardened prod profile (TLS, ES security on, internal ports closed, prod secrets). And this dev box has no Docker, so nothing prod-shaped can be exercised here. So: dev/CI-shape live smoke = proven green; **hardened-config live smoke = not yet run** (this is exactly gate item B4). Run B4 on a Docker-capable host against the hardened config before external delivery.

## 6. Recommended next step

Implement a **minimal security-hardening slice** against §3 items 1–5 (the config/secret blockers), then a separate runbook doc. Items 1 (untrack `.env`) and 2 (secret defaults) have ops/secret-rotation implications, so they need explicit owner sign-off, not an autonomous change. The functional deferrals (§4) are product/scope decisions for the buyer conversation, not engineering bugs.

## 7. Non-goals of this document

- No code/config/`.env`/secret change here (read-only assessment).
- Does not itself open the hardening slice — that needs explicit go (touches prod security config + tracked-secret removal).

## 8. Hardening Matrix — single delivery-decision matrix

This is the **canonical delivery-decision artifact** (no separate same-day doc). Each row: **Class** P0a (code/config — implementable + gateable on this box) or P0b (needs a live env and/or owner sign-off — **must not be claimed done from this box**); **Disposition** must-fix / acceptable / customer-precondition; **Verification**; **CI-verifiable?** (Y = unit/compile in CI; partial = needs the Docker-backed DB/stack gate; N = off-box live smoke / manual).

### 8.1 P0a — code/config (implement after matrix gate; I build + CI-verify)

**Reconciliation (2026-05-26): P0a is CLOSED.** All A-rows delivered across four slices —
P0a-1 (A1/A2/A3/A12), P0a-2 (A4/A5/A6), P0a-3 (A7/A8/A9/A10), P0a-3b (A11). A1–A10 + A12 are
**CI/config closed**; **A11 is static-closed, runtime pending B4** (CI never builds ml-service, no
daemon on the build box). Config-only rows (A7/A8/A10) had their merged-compose shape verified
on-box (`docker compose config`); their **full-stack runtime boot remains gate item B4**. Slice
records: `HARDENING_P0A1_*`, `HARDENING_P0A2_*`, `HARDENING_P0A3_*`, `HARDENING_P0A3B_*` verification docs.

| # | Item | Disposition | Evidence | Verification | CI-verifiable? | Status (2026-05-26) |
|---|---|---|---|---|---|---|
| A1 | `ddl-auto: update` → `validate` (Liquibase owns schema; `update` lets Hibernate auto-alter) | must-fix | `application-docker.yml` (jpa ddl-auto) | prod profile uses `validate`; startup against migrated DB passes | partial (Docker-backed gate) | ✅ closed — P0a-1 (`application-prod.yml` ddl-auto=validate; config test) |
| A2 | `JWT_SECRET` fail-fast, **no** `mySecretKey` default | must-fix | `application.yml:168` | prod profile has no default; missing env → startup fails; unit test on config | Y (unit) | ✅ closed — P0a-1 (reframed to issuer/jwk no-default; dead `ecm.security.jwt` key removed) |
| A3 | Remove in-file service-password defaults in prod profile | must-fix | `application.yml:8,39,47,59,290` | prod profile sources all via env, no literals | Y (grep/config test) | ✅ closed — P0a-1 (infra creds no-default; Odoo/WPS literals removed) |
| A4 | Actuator → `health` only (+`show-details: when-authorized`) / gate | must-fix | `SecurityConfig.java:48`, mgmt exposure | prod: non-health actuator 401/403; security test | Y (WebMvc test) | ✅ closed — P0a-2 (gated exposure flags + WebMvc test) |
| A5 | Disable Swagger / `/v3/api-docs` in prod | must-fix | `SecurityConfig.java:49` | prod: docs routes 404/403 | Y | ✅ closed — P0a-2 (springdoc disabled in prod) |
| A6 | CORS pinned to known origin(s), not `*` | must-fix | `SecurityConfig.java:84` | prod config rejects unlisted origin | Y | ✅ closed — P0a-2 (CORS env-pinned, fail-fast on unset) |
| A7 | Add `application-prod.yml` + `docker-compose.prod.yml` (app auth-on, no debug logging; service-security handled in A10) | must-fix | (absent) | files exist + lint; profile boots (full stack validated in B4) | partial | ✅ closed (config) — P0a-3 (both files exist, parse); runtime boot → **B4** |
| A8 | Don't publish internal service ports; expose only nginx/TLS | must-fix | `docker-compose.yml` `ports:` | prod compose maps only 80/443 | N (manual/compose review) | ✅ closed (config) — P0a-3 (`!reset []`; `compose config` shows only nginx publishes) |
| A9 | Pin image tags (no floating/`latest`) | must-fix | `docker-compose.yml` `image:` | every image has an explicit version/digest | Y (lint/grep) | ✅ closed — P0a-3 (3 `:latest` pinned; `:latest`=0; CI 7/7 green) |
| A10 | ES `xpack.security` on; MinIO/Grafana creds via env; **Prometheus not externally published in v1** (no auth mechanism today — closed via A8 `!reset []`; external Prometheus access is a separate future item) | must-fix | `docker-compose.yml` (ES `xpack.security.enabled=false`, Grafana admin) | prod compose config/lint (Y); **full validation in B4** — turning ES security on affects app credentials + compose startup + the E2E stack, so ordinary CI cannot fully prove it | config-only Y / runtime N (B4) | ✅ closed (config) — P0a-3 (ES security on, MinIO/Grafana creds env + fail-fast verified; Prometheus ports closed, no auth in v1); runtime → **B4** |
| A11 | `ml-service` runs as non-root | must-fix | `ml-service/Dockerfile` (no `USER`) | image runs as non-root `id` | Y (build) | ⏳ **static closed / runtime pending B4** — P0a-3b (uid 10001 + static guard green); CI never builds ml-service → runtime + brownfield `athena_ml_models` chown = owner/B4 |
| A12 | README "Production Ready" → honest delivery posture | must-fix | `README.md` | wording matches this doc's verdict | review | ✅ closed — P0a-1 (README posture corrected, links this doc) |

### 8.2 Confirm-required — tracked-secret removal (separate; NOT mixed with A-rows)

| # | Item | Disposition | Evidence | Verification | CI-verifiable? | Status (2026-05-26) |
|---|---|---|---|---|---|---|
| S1 | `git rm --cached .env ecm-frontend/.env` (already in `.gitignore:106`/`*.env:110`; committed before ignore) | must-fix — **explicit owner confirm** | `git ls-files` → `.env`, `ecm-frontend/.env` | files untracked; `git ls-files` clean; `.example` retained | Y (git check) | ✅ **done 2026-05-26 (`83d6935`)** — both untracked (`git ls-files` empty), local files retained, now gitignored. **Does NOT scrub history** → values still reachable in prior commits, treat as compromised until S2 |
| S2 | Rotate the dev-default creds + decide custodian (who holds prod secrets) | must-fix — **owner/ops decision, not autonomous** | tracked `.env` dev defaults; **read-only inventory: `docs/HARDENING_S2_SECRET_ROTATION_INVENTORY_20260526.md`** (keys to rotate, prod-name bridge, provision-if-used secrets, owner decisions) | new secrets issued + injected via env; old invalidated | N (ops) | ⏳ pending — owner/ops; **required** because S1 leaves the historical values in git history. Inventory ready (read-only, not rotation); rotation itself remains owner/ops |

Rationale for separating S1/S2: untracking is mechanical, but *rotation* and custodianship are owner/ops calls — kept out of the A-slice so a routine config PR never silently implies secrets were rotated.

### 8.3 P0b — needs live env / owner sign-off (do NOT claim done from this box)

| # | Item | Disposition | Verification | CI-verifiable? |
|---|---|---|---|---|
| B1 | Keycloak `start-dev` → production realm/clients/HTTPS | must-fix (prod) | prod Keycloak boots; tokens validated by issuer/jwk URIs | N (env) |
| B2 | TLS certs + HTTPS enforced for `/api/**` and `/api/v1/transfer/receiver/**` (plaintext creds otherwise) | must-fix (prod) | TLS terminates; HTTP redirected/blocked | N (env) |
| B3 | Backup + restore **runbook** and at least one **restore smoke** | must-fix (prod) | documented procedure + a successful restore on a non-prod copy | N (env) |
| B4 | **Production-shape full-stack smoke** (login → upload → search → preview → permissions → backup/restore) on a Docker-capable host | must-fix (prod) | green run on the **hardened** config (not dev compose) | N (off-box) |

### 8.4 Functional deferrals — customer-conversation (scope, not hardening)

| # | Item | Disposition | Evidence |
|---|---|---|---|
| C1 | Content-at-rest encryption deferred (storage-SSE only) | customer-precondition (blocker iff app-layer encryption required) | `docs/adr/ADR-003`, `ContentService` |
| C2 | No per-tenant physical storage isolation (shared dedup) | customer-precondition (blocker iff hard data-residency/isolation) | `docs/adr/ADR-001` |
| C3 | Transfer v1: no delete propagation / permission-delta / alien-node | acceptable for v1; customer-precondition if full sync expected | `CLAUDE.md` |
| C4 | Alfresco compat: transformation + direct-stream are stubs | must-fix iff Alfresco drop-in; else acceptable | `AlfrescoContentService.java:103,125` |

### 8.5 Gate

- **Internal UAT — non-real data, controlled network: deliverable now** (pre-hardening), with C1–C4 disclosed.
- **Pilot — real data and/or non-controlled network: requires P0a + S1/S2 first** (not "now").
- **External / public production:** gated on **all P0a + S1/S2 + P0b**, then a green **B4** smoke on the hardened config.
- **P0a status (2026-05-26): DELIVERED.** A1–A10 + A12 CI/config-closed; A11 static-closed, runtime pending B4 (see §8.1 reconciliation). Config-only rows' (A7/A8/A10) full-stack boot remains B4.
- **S1 done 2026-05-26 (`83d6935`)** — env files untracked (history not scrubbed; see §8.2).
- **Next actions — all owner-side, none autonomous:** (1) **S2** secret rotation + custodian — owner/ops (required: S1 leaves historical values in git history); (2) **A11 runtime** + brownfield `athena_ml_models` chown on a daemon host; (3) **B4** hardened-config full-stack smoke. Pilot gate (real data / non-controlled network) unblocks once **S2** lands on top of the delivered P0a + S1; external/public production additionally needs P0b + B4.
