# Hardening Handoff — single entry point (2026-05-26)

One page to resume or hand off the production-hardening track. **Canonical decision matrix:** §8 of
`docs/PRODUCTION_READINESS_ASSESSMENT_20260525.md` (this doc summarizes + adds the owner cutover checklist).

## Delivery posture (unchanged)

- **Internal UAT, non-real data, controlled network: deliverable now.**
- **Pilot (real data / non-controlled network):** needs **S2** on top of the delivered P0a + S1.
- **External / public production:** needs **all P0b + B4** on the hardened config.

## Status at a glance

| Track | Status | Evidence / where |
|---|---|---|
| **P0a A1–A10, A12** | ✅ CI/config closed | matrix §8.1; `HARDENING_P0A{1,2,3}_*` |
| **P0a A11** (ml-service non-root) | ✅ static closed / ⏳ runtime → B4 | `HARDENING_P0A3B_*`; `scripts/ml-service-dockerfile-check.sh` |
| **S1** (untrack `.env`) | ✅ done (`83d6935`) — history NOT scrubbed | matrix §8.2 |
| **S2** (secret rotation) | ⏳ owner/ops — inventory ready | `HARDENING_S2_SECRET_ROTATION_INVENTORY_20260526.md` |
| **B1/B2** (Keycloak prod + TLS) | ✅ templated config + static guard done / ⏳ runtime cutover | `HARDENING_B1_B2_IMPLEMENTATION_VERIFICATION_20260526.md`; `scripts/b1b2-prod-config-check.sh` |
| **B3/B4** (backup-restore + smoke) | ⏳ owner/ops — runbook ready | `RUNBOOK_B3_B4_BACKUP_RESTORE_AND_SMOKE_20260526.md` |

**Nothing below is claimed done from the dev box** (no Docker daemon, no real certs/domain/secrets).

## Owner / ops to-do (all require a daemon host + real secrets)

1. **S2 — rotate** every credential listed in the S2 inventory §1 (DB/Redis/RabbitMQ/MinIO/Keycloak/Collabora + optional Odoo/WPS). Required because S1 left historical values in git history. Decide custodian + whether to scrub history (rotation alone is sufficient; scrub is optional, separate, high-blast-radius).
2. **A11 runtime** — build/run ml-service, confirm uid 10001 + `/train` writes the volume; brownfield: `chown` the resolved `athena_ml_models` volume (runbook in `HARDENING_P0A3B_*`).
3. **B1/B2 cutover** — fill env + drop certs + provision realm (checklist below), then verify TLS/login.
4. **B3** — run backups + at least one restore-smoke (single write-quiesce window; `pg_restore --clean`).
5. **B4** — green hardened-config full-stack smoke (login→upload→search→preview→permissions).

## Cutover checklist — B1/B2 (the templated config is ready; fill these in)

Bring-up: `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d` — every `${VAR:?required}` must be supplied or it fails fast.

**(1) Required env to set** (all fail-fast; values from the rotated/provisioned secrets — never commit them):
- Infra: `POSTGRES_DB` `POSTGRES_USER` `POSTGRES_PASSWORD` · `ELASTIC_PASSWORD` · `REDIS_PASSWORD` · `RABBITMQ_USER` `RABBITMQ_PASSWORD`
- Object/dashboards: `MINIO_ROOT_USER` `MINIO_ROOT_PASSWORD` · `GF_SECURITY_ADMIN_USER` `GF_SECURITY_ADMIN_PASSWORD`
- Keycloak (base interpolation): `KEYCLOAK_USER` `KEYCLOAK_PASSWORD` · `KEYCLOAK_DB_USER` `KEYCLOAK_DB_PASSWORD`
- Auth / public shape: `ECM_KEYCLOAK_PUBLIC_HOST` (separate KC subdomain) · `ECM_JWT_ISSUER_URI=https://<kc-subdomain>/realms/ecm` (must equal token `iss`) · `ECM_JWT_JWK_SET_URI` (public **or** internal `http://keycloak:8080/realms/ecm/protocol/openid-connect/certs`) · `ECM_SECURITY_CORS_ALLOWED_ORIGINS=https://<app-host>`

**(2) Feature-only env** (set only if that feature is deployed — see S2 inventory §1.5): `ECM_SECURITY_SECRET_KEY_V1` (property encryption — **key-management, restore in lockstep with DB**), `ECM_PREVIEW_CAD_AUTH_TOKEN`, `ECM_LDAP_BIND_PASSWORD`, `ODOO_API_KEY` / `ECM_ODOO_PASSWORD` (+ `MINIO_*`/`ODOO_DB_*`).

**(3) Certs** — drop real `cert.pem` + `key.pem` into `nginx/ssl/` (mounted read-only). Set the real domain: replace `server_name _;` in `nginx/nginx.prod.conf` with the app host. (No certs are committed in repo.)

**(4) Keycloak realm** — provision the `ecm` realm **out-of-band** (admin API/console); prod runs plain `start` (no `--import-realm`). Register the frontend **public** client redirect URIs / web origins to the public HTTPS app origin. Disable/rotate the bootstrap admin after first boot.

**(5) Verify** — run the static guard first (`scripts/b1b2-prod-config-check.sh` → rc=0 confirms shape), then the runtime checks in `HARDENING_B1_B2_*_VERIFICATION` §"NOT proven here": TLS handshake, Keycloak boots with the real hostname, token `iss` validates, `/api/**` + transfer-receiver/WOPI/share over HTTPS only.

## B3/B4 cutover (detail in the runbook)

- **B3:** enter a write-quiesce window (stop `ecm-core`), back up core Postgres (`pg_dump -Fc`) + `athena_ecm_content` (tar) in the **same** window; restore-smoke on a throwaway host (Postgres-only up → `pg_restore --clean --if-exists` → restore content → key/chown → start app → reindex → checksum). Authoritative content store is **`athena_ecm_content`**, not MinIO.
- **B4:** the 8-step hardened-config smoke (`RUNBOOK_B3_B4_*` §B4.2); reuse `ecm-frontend/e2e/frontend-acceptance-smoke.spec.ts` as a starting point.

## Doc index (canonical)

- Decision matrix + posture: `PRODUCTION_READINESS_ASSESSMENT_20260525.md` §8
- Owner execution plan: `OWNER_CUTOVER_EXECUTION_PLAN_20260526.md` (S2 → B1/B2 → A11 → B3 → B4, with rollback/evidence/triage)
- P0a: `HARDENING_P0A1_*`, `HARDENING_P0A2_*`, `HARDENING_P0A3_*`, `HARDENING_P0A3B_*` (brief + verification each)
- S2 inventory: `HARDENING_S2_SECRET_ROTATION_INVENTORY_20260526.md`
- B1/B2: `HARDENING_B1_B2_KEYCLOAK_TLS_BRIEF_*` (design), `HARDENING_B1_B2_IMPLEMENTATION_BRIEF_*` (impl spec), `HARDENING_B1_B2_IMPLEMENTATION_VERIFICATION_*`
- B3/B4: `RUNBOOK_B3_B4_BACKUP_RESTORE_AND_SMOKE_20260526.md`
- Guards: `scripts/b1b2-prod-config-check.sh`, `scripts/ml-service-dockerfile-check.sh`, `scripts/backend-preflight.sh`

## Boundaries (still in effect)

- `.env` stays untracked/untouched; never print secret values. S2 rotation + history-scrub decision = owner/ops.
- Don't claim P0b/A11-runtime done from a box without a daemon + real secrets.
- Commit/push only when asked; gate CI on `gh run view` conclusion.
