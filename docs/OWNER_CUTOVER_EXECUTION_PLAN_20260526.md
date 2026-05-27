# Owner Cutover Execution Plan (2026-05-26)

Status: **plan only**. This document does not rotate secrets, start services, run backups, or claim production readiness. It sequences the remaining owner/ops work after the Athena hardening track.

Primary inputs:

- `docs/HANDOFF_HARDENING_20260526.md` — single entry point and current status.
- `docs/PRODUCTION_READINESS_ASSESSMENT_20260525.md` §8 — canonical readiness matrix.
- `docs/HARDENING_S2_SECRET_ROTATION_INVENTORY_20260526.md` — secret key inventory, no values.
- `docs/HARDENING_B1_B2_IMPLEMENTATION_VERIFICATION_20260526.md` — templated TLS/Keycloak config verification.
- `docs/RUNBOOK_B3_B4_BACKUP_RESTORE_AND_SMOKE_20260526.md` — backup/restore and hardened-config smoke runbook.

## 1. Current Decision

Athena can remain in **internal UAT** for non-real data on a controlled network.

Pilot / production movement should not start with new feature development. It should start with the remaining owner/ops execution:

1. **S2** — rotate compromised historical credentials and decide custodian.
2. **B1/B2** — runtime cutover for Keycloak production mode + TLS.
3. **A11 runtime** — validate `ml-service` non-root behavior and brownfield volume ownership.
4. **B3** — run backup and a restore smoke.
5. **B4** — run hardened-config full-stack smoke.

Pilot gate: **P0a + S1 + S2**.

External/public production gate: **Pilot gate + B1/B2 + A11 runtime if deployed + B3 + B4**.

## 2. Non-Negotiable Boundaries

- Do not commit `.env`, certs, private keys, realm exports with secrets, DB dumps, content archives, screenshots containing secrets, or runtime tokens.
- Do not print secret values in chat, CI logs, or docs.
- Do not claim B1/B2/B3/B4 done from a host without Docker daemon, real secrets, real domains, and real certs.
- Do not pair a Postgres dump with a content archive from a different write-quiesce window.
- Do not change `ECM_SECURITY_SECRET_KEY_V1` for a restored DB unless the data was encrypted under the new key from the beginning.
- Do not use dev `start-dev`, localhost issuer defaults, or the base dev `.env` path for production cutover.

## 3. Roles

| Role | Owns |
|---|---|
| Owner / ops | Secret generation, custodian, domain/cert ownership, Keycloak realm provisioning, Docker-capable execution host, backup destination, smoke execution sign-off. |
| Athena reviewer | Review command output, diagnose failures, update runbooks/docs if execution reveals drift. |
| App maintainer | Fix code/config only if runtime cutover exposes a real repo defect. |

## 4. Required Host Prerequisites

Run the remaining work on a host that has:

- Docker Engine + Docker Compose plugin.
- Network access to pull/publish required images.
- Enough disk space for one full content archive plus one restored copy.
- Access to the target DNS zones and TLS certificate source.
- Access to secret custodian or a secure local env injection mechanism.
- Ability to run a disposable restore-smoke stack separate from production.

Minimum preflight:

```bash
docker version
docker compose version
git rev-parse --short HEAD
git status --short
./scripts/prod-deploy-preflight.sh --env-file /etc/athena/prod.env --require-daemon
./scripts/b1b2-prod-config-check.sh
./scripts/ml-service-dockerfile-check.sh
```

Acceptance: commands return successfully, `git status --short` is clean except ignored local env/cert material, and no secret value is printed.

Production compose requires the Docker Compose **v2 plugin** (`docker compose`). Legacy
`docker-compose` v1 is not supported because `docker-compose.prod.yml` intentionally uses
Compose v2 YAML merge tags such as `!reset` / `!override`. Do not work around this by adding a
`ddl-auto=update` override; production must run the `prod` profile with Hibernate
`ddl-auto=validate`, and schema issues should be fixed with Liquibase migrations before cutover.

## 5. Execution Overview

| Phase | Goal | Blocks |
|---|---|---|
| 0 | Freeze plan and prepare evidence folder | all later phases |
| 1 | S2 secret rotation and custodian decision | pilot gate |
| 2 | B1/B2 runtime cutover rehearsal | public prod gate |
| 3 | A11 ml-service runtime validation | B4 if ML deployed |
| 4 | B3 backup and restore smoke | public prod gate |
| 5 | B4 hardened-config smoke | public prod gate |
| 6 | Final sign-off package | delivery decision |

Recommended order: **1 → 2 → 3 → 4 → 5 → 6**.

Rationale: S2 feeds every prod env; B1/B2 must be stable before live smoke; A11 affects ML runtime and volume restore; B3 proves recovery; B4 proves the hardened stack end-to-end.

## 6. Phase 0 — Preparation

### Tasks

- Confirm the target commit to deploy.
- Create a local evidence folder outside the repo, for example `~/athena-cutover-evidence/<date>/`.
- Decide environment names: `staging`, `pilot`, or `production`.
- Decide whether this is a rehearsal or real cutover.
- Confirm backup target path and retention policy.

### Commands

```bash
git fetch origin
git rev-parse --short HEAD
git rev-parse --short origin/main
git status --short
```

### Acceptance

- `HEAD` and `origin/main` are the intended commit.
- No unreviewed repo changes.
- Evidence folder exists outside Git.

### Evidence to retain

- Target commit SHA.
- Output of `git status --short`.
- A one-line statement: rehearsal vs real cutover.

## 7. Phase 1 — S2 Secret Rotation

### Goal

Invalidate every credential value that was historically tracked, issue any new prod-only secrets, and define a custodian.

### Inputs

- `docs/HARDENING_S2_SECRET_ROTATION_INVENTORY_20260526.md`
- `docker-compose.prod.yml`
- `ecm-core/src/main/resources/application-prod.yml`

### Tasks

1. Choose custodian mechanism: Docker/Kubernetes secrets, Vault, cloud secret manager, or controlled env injection.
2. Rotate required historical credentials:
   - `POSTGRES_PASSWORD`
   - `REDIS_PASSWORD`
   - `RABBITMQ_PASSWORD`
   - `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` by replacing them with prod `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`
   - `KEYCLOAK_PASSWORD`
   - `KEYCLOAK_DB_PASSWORD`
   - `COLLABORA_ADMIN_PASSWORD`
   - `ODOO_DB_PASSWORD` if Odoo is deployed
3. Provision new prod-only secrets:
   - `ELASTIC_PASSWORD`
   - `MINIO_ROOT_USER`
   - `MINIO_ROOT_PASSWORD`
   - `GF_SECURITY_ADMIN_USER`
   - `GF_SECURITY_ADMIN_PASSWORD`
4. Decide optional integration secrets only if deployed:
   - `ECM_SECURITY_SECRET_KEY_V1` — key-management item, not a routine rotation; if existing data is encrypted, changing it breaks decrypt. See §2 boundary.
   - `ECM_PREVIEW_CAD_AUTH_TOKEN`
   - `ECM_LDAP_BIND_PASSWORD`
   - `ODOO_API_KEY`
   - `ECM_ODOO_PASSWORD`
   - `ECM_WPS_APPKEY`
5. Remove dead `JWT_SECRET` from local/prod env material; do not rotate it.
6. Decide history-scrub policy:
   - Recommended default: rotate values and do **not** rewrite history.
   - History scrub with `git filter-repo` / BFG is separate, high-blast-radius, and needs explicit authorization.

### Prod env names that must exist before B1/B2/B4

Config / non-secret:

- `POSTGRES_DB`
- `POSTGRES_USER`
- `RABBITMQ_USER`
- `ECM_KEYCLOAK_PUBLIC_HOST`
- `ECM_JWT_ISSUER_URI`
- `ECM_JWT_JWK_SET_URI`
- `ECM_SECURITY_CORS_ALLOWED_ORIGINS`

Secret-bearing:

- `POSTGRES_PASSWORD`
- `ELASTIC_PASSWORD`
- `REDIS_PASSWORD`
- `RABBITMQ_PASSWORD`
- `MINIO_ROOT_USER`
- `MINIO_ROOT_PASSWORD`
- `GF_SECURITY_ADMIN_USER`
- `GF_SECURITY_ADMIN_PASSWORD`
- `KEYCLOAK_USER`
- `KEYCLOAK_PASSWORD`
- `KEYCLOAK_DB_USER`
- `KEYCLOAK_DB_PASSWORD`

### Verification

Run shape validation without printing values:

```bash
./scripts/prod-deploy-preflight.sh --env-file /etc/athena/prod.env --require-daemon
```

### Acceptance

- New credential values exist in custodian.
- Old values are invalidated or scheduled for invalidation in a defined window.
- `prod-deploy-preflight` succeeds with env supplied by the custodian or secure shell environment.
- No `.env` file is added back to Git.
- No secret values are copied into evidence.

### Evidence to retain

- Custodian name/type, not values.
- Rotation completion timestamp.
- A table of key names marked rotated/provisioned/not-used.
- `git ls-files .env ecm-frontend/.env` output showing empty.

## 8. Phase 2 — B1/B2 Keycloak + TLS Runtime Cutover

### Goal

Prove the templated production config actually boots with real domain/cert/realm and HTTPS-only access.

### Inputs

- `docker-compose.prod.yml`
- `nginx/nginx.prod.conf`
- `nginx/athena-locations.conf`
- `scripts/b1b2-prod-config-check.sh`
- `docs/HARDENING_B1_B2_IMPLEMENTATION_VERIFICATION_20260526.md`

### Tasks

1. Set real app hostname and Keycloak hostname.
2. Put real `cert.pem` and `key.pem` under `nginx/ssl/` on the deployment host only.
3. Optionally replace `server_name _;` in deployment config with the real app host. `_` is a catch-all and can work for a single-host deployment; setting the real name is a hardening/clarity step for multi-host or SNI-sensitive deployments. If this is a repo edit, make it a reviewed environment-specific overlay, not a secret-bearing commit.
4. Provision Keycloak `ecm` realm out-of-band.
5. Configure frontend public client:
   - public client, no frontend secret
   - redirect URI points to HTTPS app origin
   - web origins pinned to HTTPS app origin
6. Confirm:
   - `ECM_JWT_ISSUER_URI` equals token `iss`
   - `ECM_JWT_JWK_SET_URI` serves the same realm signing keys; it may be public HTTPS or internal `http://keycloak:8080/.../certs`
7. Confirm the prod override still sets `KC_HTTP_ENABLED=true`; do not override it away. Keycloak 23 `start` behind nginx TLS termination needs the internal HTTP listener enabled.
8. Start production stack:

```bash
./scripts/prod-deploy-preflight.sh --env-file /etc/athena/prod.env --require-daemon
./scripts/b1b2-prod-config-check.sh
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
```

### Runtime checks

```bash
curl -I http://<app-host>/
curl -I https://<app-host>/
curl -I https://<app-host>/actuator/health
curl -I https://<kc-host>/realms/ecm/.well-known/openid-configuration
```

Expected:

- HTTP app host redirects to HTTPS.
- HTTPS handshake succeeds with the expected certificate.
- `/actuator/health` is reachable.
- Non-health actuator paths require auth.
- Swagger/OpenAPI routes are not exposed in prod.
- Keycloak realm metadata returns public HTTPS issuer.

### Acceptance

- Keycloak runs with `command: ["start"]`, not `start-dev`.
- No `--import-realm` steady-state mount is used.
- Login obtains token with expected issuer.
- Backend validates token through prod issuer/JWK settings.
- Plain HTTP does not carry app/API credentials.

### Rollback

- Stop prod stack.
- Restore previous DNS/cert routing.
- Revert to internal UAT posture if login or TLS validation fails.
- Do not fall back to `start-dev` for real-data pilot/prod.

### Evidence to retain

- `docker compose ps` output.
- Redacted Keycloak realm metadata showing issuer URL only.
- HTTP→HTTPS redirect headers.
- TLS certificate subject/issuer/expiry, no private key.

## 9. Phase 3 — A11 ml-service Runtime Validation

### Goal

Prove `ml-service` runs as non-root uid 10001 and can write model data after the Dockerfile hardening.

Run this phase only if ML training/model serving is deployed.

### Inputs

- `ml-service/Dockerfile`
- `scripts/ml-service-dockerfile-check.sh`
- `docs/HARDENING_P0A3B_MLSERVICE_NONROOT_BRIEF_20260526.md`

### Tasks

1. Run static guard:

```bash
./scripts/ml-service-dockerfile-check.sh
```

2. Resolve the actual ML volume name before chown. The default project name gives `athena_ml_models`, but a different Compose project name changes it.

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml config | grep -n "ml_models" || true
```

3. If an old root-owned ML models volume exists, run brownfield chown against the resolved volume name:

```bash
docker run --rm -v <resolved_ml_models_volume>:/v alpine chown -R 10001:10001 /v
```

4. Build and run ml-service in the target compose/runtime shape.
5. Confirm container identity:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec ml-service id
```

6. Exercise health and write path:

```bash
curl -f http://<ml-service-internal-or-proxied-health>/health
# Then run the existing training/write path used by the deployment.
```

### Acceptance

- Container reports uid 10001.
- `/health` returns healthy.
- `/train` or equivalent write path can create/update model files in the mounted volume.
- Restart preserves the model if persistence is expected.

### Rollback

- If `/health` is healthy but `/train` fails with permission errors, rerun volume chown and retest.
- If non-root mode breaks a required runtime path, stop rollout and file a repo issue with exact path and errno.

### Evidence to retain

- Static guard output.
- `id` output.
- `/health` status.
- Write-path pass/fail result without payload secrets.

## 10. Phase 4 — B3 Backup + Restore Smoke

### Goal

Prove the system can be restored from a consistent backup batch.

### Inputs

- `docs/RUNBOOK_B3_B4_BACKUP_RESTORE_AND_SMOKE_20260526.md`

### Backup tasks

1. Enter maintenance / write-quiesce window.
2. Stop `ecm-core`, keep Postgres and volumes available:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml stop ecm-core
```

3. Take core Postgres custom dump:

```bash
mkdir -p backup
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T postgres \
  pg_dump -Fc -U "$POSTGRES_USER" "$POSTGRES_DB" > backup/ecm_pg_<date>.dump
```

4. Take content archive in the same window:

```bash
docker run --rm -v athena_ecm_content:/src -v "$PWD/backup":/out alpine \
  tar czf /out/ecm_content_<date>.tgz -C /src .
```

5. Repeat for Keycloak/Odoo/Grafana/ML only if deployed per runbook tiers.
6. Restart app after backup:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml start ecm-core
```

### Restore smoke tasks

Run on a throwaway host, not production.

**Destructive restore guard:** the restore commands below clean the target DB and delete the target content volume before extracting the archive. Never run them against the production Docker daemon with the production Compose project name. Use either:

- a separate host; or
- a separate Compose project name, for example `COMPOSE_PROJECT_NAME=athena_restore_smoke`, so restored volumes are named differently.

If using the same daemon, export the restore project name before any restore command:

```bash
export COMPOSE_PROJECT_NAME=athena_restore_smoke
RESTORE_COMPOSE="docker compose -p athena_restore_smoke -f docker-compose.yml -f docker-compose.prod.yml"
```

Before any `pg_restore --clean` or `rm -rf /dst/*`, verify the target project/volume names are restore-only, not production:

```bash
docker compose -p athena_restore_smoke -f docker-compose.yml -f docker-compose.prod.yml config --volumes
docker volume ls | grep ecm_content
```

1. Bring up Postgres only.
2. Restore DB with clean restore:

```bash
$RESTORE_COMPOSE exec -T postgres \
  pg_restore --clean --if-exists --no-owner --no-acl \
  -U "$POSTGRES_USER" -d "$POSTGRES_DB" < backup/ecm_pg_<date>.dump
```

3. Restore content volume from same-window archive into the restore-smoke volume:

```bash
docker run --rm -v athena_restore_smoke_ecm_content:/dst -v "$PWD/backup":/in alpine \
  sh -c 'rm -rf /dst/* && tar xzf /in/ecm_content_<date>.tgz -C /dst'
```

4. Restore the same `ECM_SECURITY_SECRET_KEY_V1` if property encryption is enabled.
5. Apply the resolved ML models volume chown if ML model volume is restored.
6. Start the stack.
7. Reindex ES or restore same-window ES snapshot if that route is chosen.
8. Verify old document download checksum and search visibility.

### Acceptance

- DB restore completes without `relation already exists` / duplicate-row failure.
- A previously uploaded document downloads with identical bytes.
- Search returns the restored document after reindex.
- App starts cleanly under hardened config.

### Rollback

- Restore smoke is non-prod; failure does not affect production.
- If restore fails, keep backup artifacts and logs, do not overwrite the production backup set.

### Evidence to retain

- Backup timestamps for DB dump and content archive proving same window.
- Checksums of backup artifacts.
- `pg_restore` exit code.
- Restored document checksum comparison.
- Reindex completion evidence.

## 11. Phase 5 — B4 Hardened Full-Stack Smoke

### Goal

Prove the hardened stack supports core product use end-to-end.

### Required smoke path

| Step | Check | Pass criteria |
|---|---|---|
| 1 | Login | User logs in via prod Keycloak; backend accepts token. |
| 2 | Upload | Document upload succeeds and returns document metadata. |
| 3 | Download | Downloaded bytes match uploaded bytes. |
| 4 | Search | Uploaded document appears in search after indexing. |
| 5 | Preview | Preview/rendition path succeeds or returns expected queued/diagnostic state. |
| 6 | Permissions | Unauthorized user receives 403; authorized user succeeds. |
| 7 | Actuator/Swagger | `/actuator/health` allowed; non-health actuator and Swagger docs not public. |
| 8 | CORS | Unlisted origin rejected; configured origin allowed. |
| 9 | Share/WOPI/transfer permitAll paths | Accessible only through their intended opaque-token/controller checks and over HTTPS. |
| 10 | ml-service if deployed | uid 10001 and write path verified. |

### Suggested automation

Start with:

- `ecm-frontend/e2e/frontend-acceptance-smoke.spec.ts`

Then add hardened-config checks for auth, CORS, actuator, preview, and permissions.

### Acceptance

- All required smoke steps pass against `docker-compose.yml + docker-compose.prod.yml`.
- Smoke output is tied to the deployment commit SHA.
- Any skipped step has an explicit reason and owner sign-off.

### Rollback

- If login/auth fails: stop cutover, inspect Keycloak issuer/JWK/redirect origin first.
- If upload/download fails: inspect content volume mount and DB/content restore consistency.
- If search fails: inspect ES credentials and reindex.
- If preview fails: classify as preview/rendition issue; do not mark B4 green unless preview behavior is accepted by owner.

### Evidence to retain

- Commit SHA.
- Compose file list used.
- Smoke command output.
- Screenshots or logs with secrets redacted.
- Explicit pass/fail table.

## 12. Phase 6 — Final Sign-Off Package

Create a short owner-facing receipt outside the repo or as a future doc-only commit after review.

### Required fields

| Field | Value |
|---|---|
| Commit SHA | |
| Environment | |
| Custodian | |
| S2 rotation complete | yes/no + timestamp |
| B1/B2 runtime cutover | yes/no + evidence path |
| A11 runtime | yes/no/not deployed + evidence path |
| B3 restore smoke | yes/no + evidence path |
| B4 full-stack smoke | yes/no + evidence path |
| Known residual risks | |
| Owner sign-off | |

### Delivery posture after sign-off

- If S2 only is complete: pilot can begin, assuming controlled owner acceptance.
- If S2 + B1/B2 + B3 + B4 are complete: external/public production posture can be considered, subject to owner risk acceptance and any customer-specific C1-C4 deferrals.

## 13. Failure Triage Order

Use this order to avoid chasing symptoms:

1. Missing env / bad Compose interpolation.
2. Keycloak container fails to start: check `KC_HTTP_ENABLED=true`, `KC_HOSTNAME`, `KC_PROXY_HEADERS=xforwarded`, and that the prod override did not reintroduce `start-dev` or `--import-realm`.
3. Keycloak issuer/JWK mismatch.
4. TLS/cert/domain mismatch.
5. DB credential or schema restore issue.
6. Content volume mismatch.
7. ES auth/reindex issue.
8. Permission/CORS/SecurityConfig behavior.
9. Preview/ML optional service behavior.

## 14. What Codex Can Help With Next

Codex can help review outputs and update docs/runbooks. Codex should not:

- receive or print secret values;
- rotate secrets directly unless explicitly authorized in a secure workflow;
- claim runtime gates without evidence from a daemon host;
- mark S2/B1/B2/B3/B4 done based only on local static checks.

Best next collaboration mode:

1. Owner runs a phase on the daemon host.
2. Owner shares redacted command output and pass/fail symptoms.
3. Codex reviews, diagnoses, and proposes the smallest fix or doc update.

## 15. Plan Verification

This plan was created from existing repository docs and does not mutate runtime state.

Expected worktree impact:

- One new documentation file: `docs/OWNER_CUTOVER_EXECUTION_PLAN_20260526.md`.
- No code/config/schema/test/frontend changes.
