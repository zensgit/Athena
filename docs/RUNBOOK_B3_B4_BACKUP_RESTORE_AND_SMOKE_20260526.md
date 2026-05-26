# B3 / B4 — Backup-Restore + Hardened-Config Smoke (READ-ONLY RUNBOOK)

Date: 2026-05-26 · Status: **read-only runbook for owner/ops to execute on a Docker-capable host — NOT executed/claimed-done from this box** (v2 — gate findings folded in: single write-quiesce window for DB+content consistency; `pg_dump -Fc` + `pg_restore --clean --if-exists --no-owner --no-acl` with app stopped) · Matrix §8.3 B3/B4 of `docs/PRODUCTION_READINESS_ASSESSMENT_20260525.md`.

## Posture & constraints

- This pack **does not** run backups, restores, or smoke here; this box has no Docker daemon and no prod secrets. It gives owner/ops a scripted checklist to run against the **hardened** config (`docker-compose.yml` + `docker-compose.prod.yml`).
- **No secret values appear in this doc.** Volume names are the Compose-resolved names (project prefix `athena_`, verified via `docker compose config`).
- Nothing here marks B3/B4 done — execution + a green restore-smoke + a green full-stack smoke remain owner/ops deliverables (P0b).

---

# B3 — Backup / Restore

## B3.0 Key correction — where the document bytes actually live

**Verified (this repo):** the app stores content bytes on the **filesystem**, not MinIO.
- `application.yml:160` `root-path: /var/ecm/content` (+ `temp-path: /var/ecm/temp`, `quarantine-path: /var/ecm/quarantine`); compose injects `ECM_STORAGE_ROOT_PATH=/var/ecm/content` and mounts `ecm_content:/var/ecm/content` (`docker-compose.yml:32,64`).
- **No MinIO/S3 client**: no `io.minio`/`software.amazon.awssdk`/`S3Client` in code, no minio/aws SDK in `ecm-core/pom.xml`. `MINIO_ENDPOINT` is set in compose but **not consumed** by the app — MinIO is currently dormant.

➡️ **The authoritative content-byte backup target is the `athena_ecm_content` volume, NOT MinIO.** Back up MinIO (`athena_minio_data`) as a primary target **only if** a production deployment wires the content path to an underlying MinIO/FUSE/CSI mount. Until then MinIO is not authoritative data.

## B3.1 Backup tiers (priority-ordered)

### Tier 1 — mandatory primary data (lose = data loss, not rebuildable)
| Target | Resolved name | Method | Note |
|---|---|---|---|
| Postgres (core ECM) | `athena_postgres_data` | `pg_dump`/`pg_restore` (logical) | core business metadata — the system of record |
| **Content bytes** | `athena_ecm_content` | volume snapshot / `tar` of `/var/ecm/content` | **authoritative content store** (see B3.0); not rebuildable |
| Secrets / config snapshot | — (not a data store) | custodian-held, out-of-band | restore-required: prod env/secrets, `docker-compose*.yml`, Keycloak realm export, TLS certs, **`ECM_SECURITY_SECRET_KEY_V1` if property encryption is used**. **Never commit secret values to the repo** — record only *where* they are custodied (see S2 inventory) |

> `ecm_temp`/`/var/ecm/temp` and `quarantine` are scratch — **not** backed up.
> **Property-encryption coupling:** if `ECM_SECURITY_SECRET_KEY_V1` is in use, a Postgres restore is undecryptable without the *same* key — the key must be in the secrets snapshot and restored in lockstep.

### Tier 2 — mandatory **iff** the feature is deployed
| Target | Resolved name | When |
|---|---|---|
| Keycloak Postgres | `athena_postgres_keycloak_data` | if Keycloak used (ties to B1) |
| Odoo Postgres + filestore | `athena_postgres_odoo_data` + `athena_odoo_data` | if Odoo integration deployed |
| ML models | `athena_ml_models` | if ML training/model serving used; else **retrainable/optional**. **A11 brownfield chown** (`docker run --rm -v athena_ml_models:/v alpine chown -R 10001:10001 /v`) belongs to this target's restore step — a restored/recreated volume must be re-owned to uid 10001 or `/train` fails (see `HARDENING_P0A3B_*`) |
| Grafana | `athena_grafana_data` | only if dashboards are **UI-created**; if repo/provisioning-managed, downgrade to rebuildable |

### Tier 3 — optional / rebuildable / RTO-only
| Target | Resolved name | Disposition |
|---|---|---|
| Elasticsearch | `athena_elasticsearch_data` | **derived** — rebuildable from Postgres + content (reindex). Snapshot improves RTO but is **not** sole authority |
| Prometheus | `athena_prometheus_data` | monitoring history; service restore does not depend on it — back up only per audit policy |
| Redis | `athena_redis_data` | treated as cache — not a primary target. Upgrade **only if** preview/OCR/job queues are declared non-losable (`ECM_*_QUEUE_BACKEND=redis`) |
| RabbitMQ | `athena_rabbitmq_data` | in-flight queues — same as Redis |
| ClamAV | `athena_clamav_data` | virus DB re-pullable via freshclam — not backed up |

## B3.2 Backup procedure skeleton (owner/ops, daemon host)

### B3.2.0 HARD RULE — single write-quiesce window (consistency)
Core Postgres and `athena_ecm_content` are **one strongly-consistent backup set**: the DB rows
reference content blobs on the filesystem. Dumping the DB while uploads continue can produce a DB
row pointing at a not-yet-written blob, or an orphan blob — an inconsistent backup. Therefore:

1. **Enter a maintenance / read-only window** — stop writes. Simplest: stop the app, keep storage up:
   `docker compose -f docker-compose.yml -f docker-compose.prod.yml stop ecm-core` (Postgres + the `ecm_content` volume stay available).
2. **Within the same window**, take **both** the core DB dump **and** the `athena_ecm_content` archive.
3. **Restore acceptance must use the same batch** — a DB dump and a content archive taken in the *same* window. Never pair a DB dump with a content archive from a different time.

### B3.2.1 Commands (app stopped, storage up)
```bash
# Core DB — CUSTOM format (-Fc) so restore can use pg_restore --clean --if-exists safely:
docker compose exec -T postgres pg_dump -Fc -U "$POSTGRES_USER" "$POSTGRES_DB" > backup/ecm_pg_$(date +%F).dump
#   (repeat for keycloak/odoo DBs with their own creds if those features are deployed)
# Content bytes (same window) — the volume is quiescent because ecm-core is stopped:
docker run --rm -v athena_ecm_content:/src -v "$PWD/backup":/out alpine \
  tar czf /out/ecm_content_$(date +%F).tgz -C /src .
# Resume: docker compose ... start ecm-core
# ES snapshot (Tier 3, RTO) — register a repo then snapshot via _snapshot API (auth required, A10).
# Secrets/config: exported by the custodian out-of-band; NOT scripted here.
```
> Use `-Fc` (custom format), **not** plain SQL piped to gzip. Plain SQL has no clean restore path
> into a DB where Liquibase/ecm-core already created the schema → `relation already exists` / duplicate
> rows. If a plain dump is unavoidable, it **must** be `pg_dump --clean --if-exists` restored into an
> **empty** DB with ecm-core **not** started.

## B3.3 Restore SMOKE (the B3 acceptance — at least one successful restore on a NON-prod copy)
Use a **single same-window batch** (B3.2.0): one DB dump + the content archive from the same window.

1. On a throwaway host, bring up **Postgres only** — do **not** start ecm-core yet (it must not run Liquibase/create schema before the restore):
   `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d postgres` (real required env).
2. **Restore core DB into the running Postgres** with a clean, idempotent restore (ecm-core still stopped):
   `docker compose exec -T postgres pg_restore --clean --if-exists --no-owner --no-acl -U "$POSTGRES_USER" -d "$POSTGRES_DB" < backup/ecm_pg_<date>.dump`
   (`--clean --if-exists` drops existing objects first → no `relation already exists`; `--no-owner --no-acl` avoids role mismatches on a fresh host.)
3. **Restore the content volume** from the *same-batch* `tar` into `athena_ecm_content` (e.g. `docker run --rm -v athena_ecm_content:/dst -v "$PWD/backup":/in alpine sh -c 'rm -rf /dst/* && tar xzf /in/ecm_content_<date>.tgz -C /dst'`).
4. **If property encryption on:** provide the *same* `ECM_SECURITY_SECRET_KEY_V1` before starting the app (DB rows are undecryptable under a different key).
5. **If ml_models restored:** run the A11 chown step (`docker run --rm -v athena_ml_models:/v alpine chown -R 10001:10001 /v`).
6. **Now start the app:** `docker compose ... up -d` (the rest). Reindex ES (derived) rather than trusting an old snapshot, unless the snapshot is from the same window.
7. Verify: app boots, a previously-uploaded document **downloads with identical bytes** (checksum), and search returns it after reindex. ✅ = B3 restore-smoke passed.

---

# B4 — Hardened-config full-stack smoke

## B4.1 What & where
- **Goal:** prove the **hardened** config (prod profile + prod override, not dev compose) serves the core flow end-to-end with real secrets. Off-box, daemon-required → owner/ops.
- **Bring-up:** `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d` with all `${VAR:?required}` env supplied (fail-fast proven in P0a-3). Only nginx 80/443 published (A8); ES security on (A10).

## B4.2 Smoke checklist (login → upload → search → preview → permissions)
| # | Step | Pass criteria |
|---|---|---|
| 1 | **Login** via prod Keycloak (B1) | token issued; `issuer-uri`/`jwk-set-uri` validate it (prod profile, no localhost default) |
| 2 | **Upload** a document | stored to `/var/ecm/content` (`athena_ecm_content`); content id returned |
| 3 | **Search** for it | returns the doc (ES security on, authenticated to ES per A10) |
| 4 | **Preview** | rendition/preview served (Collabora/WOPI internal-only after A8) |
| 5 | **Permissions** | a non-authorized user is denied (403); authorized user allowed |
| 6 | **Actuator/Swagger** are locked | `/actuator/**` non-health → 401/403; `/v3/api-docs` & swagger-ui disabled (A4/A5 prod) |
| 7 | **CORS** | a request from an unlisted origin is rejected (A6, `ECM_SECURITY_CORS_ALLOWED_ORIGINS`) |
| 8 | **ml-service** (if deployed) | runs as uid 10001 (`docker compose exec ml-service id`); `/train` writes the volume (A11 runtime) |

## B4.3 Reuse existing assets
- Playwright acceptance smoke already covers 3 admin pages: `ecm-frontend/e2e/frontend-acceptance-smoke.spec.ts` (`/admin/tenants`, `/admin/transfer-replication`, `/admin/cmis-explorer`). Run it against the hardened stack as a starting point, then extend with steps 1–8.
- Steps 6/7 already have unit/WebMvc coverage (P0a-2 `SecurityConfigProdExposureTest`) — B4 confirms them in a live prod-shaped boot.

## B4.4 Acceptance
B4 passes only on a **green run against the hardened config** (not dev compose). It is the gate for external/public production (with P0b B1/B2/B3). Until then, internal-UAT-on-controlled-network remains the only deliverable posture (§8.5).

---

## Verification of THIS runbook (for gate)
- No secret values; volume names are Compose-resolved (`athena_` prefix). No live execution from this box (no daemon).
- B3.0 content-store correction is repo-evidence-backed (`application.yml:160`, `docker-compose.yml:32,64`, no S3 client/dep).
- Backup tiers follow the owner's priority layering; MinIO is explicitly conditional, ES/Prometheus/Redis/RabbitMQ/ClamAV correctly placed as derived/optional.
- Nothing marked done — B3 restore-smoke and B4 full-stack smoke remain owner/ops execution.
