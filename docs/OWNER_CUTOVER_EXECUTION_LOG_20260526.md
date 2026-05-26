# Owner Cutover — Execution Log (2026-05-26)

Companion to `docs/OWNER_CUTOVER_EXECUTION_PLAN_20260526.md`. This is the **honest execution record** of an attempt to run the cutover phases **from the current dev box**.

## Headline — what could and could not be executed here

This box has **no Docker daemon, no real secrets, no TLS certs, no real domain, no Keycloak realm**. Verified this session:

```
docker info  → failed to connect (unix:///Users/.../docker.sock: no such file)
nginx/ssl/*.pem → none
ECM_KEYCLOAK_PUBLIC_HOST → unset
```

Therefore **S2, B1/B2 runtime, B3, and B4 were NOT executed and are NOT claimed done.** They are owner/ops, off-box, on a daemon host with real secrets — exactly as every prior doc states. Only the **daemon-free preflight** (Plan §4 / Phase 0) was actually run. **No execution evidence is fabricated; nothing below is marked done unless it truly ran here.**

## A. Executed on this box ✅ (daemon-free preflight — real results)

| Check | Command | Result |
|---|---|---|
| Repo state | `git rev-parse --short HEAD` / `origin/main` | `c988396` == `c988396`, worktree clean (except ignored `.env`) |
| B1/B2 prod config shape | `./scripts/b1b2-prod-config-check.sh` | **rc=0** — "merged config validates on a clean clone (`--env-file /dev/null`); ecm-core has no env_file" + "B1/B2 prod config shape verified (static)" |
| ml-service non-root shape | `./scripts/ml-service-dockerfile-check.sh` | **rc=0** — "Dockerfile declares non-root (uid 10001) and chowns writable paths" |
| Docker tooling | `docker compose version` | Compose v5.1.1 present (config-only); **daemon absent** |

➡️ The templated prod config + Dockerfile hardening are **shape-valid and ready**. That is the limit of what this box can prove.

## B. BLOCKED here — must run on a daemon host (owner/ops) ⛔

| Phase | Why blocked here | Where it runs |
|---|---|---|
| **S2 secret rotation** | Requires real secret custody/generation; reviewer must never receive/print secret values — this is an owner custody decision, not a runnable command from here | Owner custodian (Vault / cloud SM / Docker-K8s secrets) |
| **B1/B2 runtime cutover** | No daemon, no certs (`nginx/ssl/*.pem`), no real domain, no provisioned `ecm` realm | Daemon host + real domain/cert/realm |
| **B3 backup + restore smoke** | No daemon, no running prod stack, no data | Daemon host (restore smoke under `COMPOSE_PROJECT_NAME=athena_restore_smoke`) |
| **B4 hardened full-stack smoke** | No daemon, no running stack, no prod Keycloak login | Daemon host |

## C. Owner evidence-capture templates (fill on the daemon host; redact all values)

Paste **redacted** outputs back; share key **names**, statuses, and errors only — never secret values. Follow `OWNER_CUTOVER_EXECUTION_PLAN_20260526.md` for the full step list; this is just the receipt skeleton.

### Phase 1 — S2 rotation
```
Custodian mechanism: __________ (Vault / cloud SM / k8s / controlled env)
Rotated (historical):  POSTGRES_PASSWORD[ ] REDIS_PASSWORD[ ] RABBITMQ_PASSWORD[ ]
                       MINIO_ACCESS_KEY/SECRET_KEY→MINIO_ROOT_*[ ] KEYCLOAK_PASSWORD[ ]
                       KEYCLOAK_DB_PASSWORD[ ] COLLABORA_ADMIN_PASSWORD[ ] ODOO_DB_PASSWORD[ if Odoo ]
Provisioned (prod-only): ELASTIC_PASSWORD[ ] MINIO_ROOT_USER/PASSWORD[ ] GF_SECURITY_ADMIN_USER/PASSWORD[ ]
Feature (if deployed): ECM_SECURITY_SECRET_KEY_V1[ key-mgmt, lockstep w/ DB ] CAD/LDAP/ODOO_API/WPS[ ]
Dead key removed:      JWT_SECRET[ ]
History-scrub decision: rotate-only [ ] / filter-repo (separate auth) [ ]
git ls-files .env ecm-frontend/.env → (expect empty): __________
Old values invalidated at: __________ (timestamp)
```

### Phase 2 — B1/B2 runtime
```
./scripts/b1b2-prod-config-check.sh → rc: ___
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d → ___
docker compose ... ps → (all healthy?) ___
curl -I http://<app-host>/          → expect 301→https: ___
curl -I https://<app-host>/         → TLS ok, cert subject/issuer/expiry (no key): ___
curl -I https://<app-host>/actuator/health → 200: ___ ; non-health actuator → 401/403: ___
swagger/api-docs → not public: ___
https://<kc-host>/realms/ecm/.well-known/openid-configuration → issuer == public HTTPS: ___
KC_HTTP_ENABLED=true present (not overridden) ___ ; command==start (no start-dev/import-realm) ___
Login → token iss matches ECM_JWT_ISSUER_URI ___
```

### Phase 3 — A11 (if ML deployed)
```
./scripts/ml-service-dockerfile-check.sh → rc: ___
(brownfield) confirm resolved volume name: docker compose ... config --volumes | grep ml_models → ______
docker run --rm -v <resolved_ml_models_volume>:/v alpine chown -R 10001:10001 /v → ___
docker compose ... exec ml-service id → uid=10001 ___
/health → healthy ___ ; /train write path → ok ___ ; restart preserves model ___
```

### Phase 4 — B3 backup + restore smoke
```
Backup window: stop ecm-core at ___; pg_dump -Fc → ecm_pg_<date>.dump (sha ___) ; ecm_content tar (sha ___) ; same-window ✓
Restore (ISOLATED: COMPOSE_PROJECT_NAME=athena_restore_smoke, NOT prod):
  verified target volume == athena_restore_smoke_ecm_content (NOT athena_ecm_content) ___
  pg_restore --clean --if-exists --no-owner --no-acl → rc ___ (no "relation already exists")
  content restored → ___ ; same ECM_SECURITY_SECRET_KEY_V1 if encrypted ___ ; ml chown if restored ___
  reindex ES → ___ ; old doc download checksum match ___ ; search visible ___
```

### Phase 5 — B4 full-stack smoke
```
Compose: docker-compose.yml + docker-compose.prod.yml ; commit SHA ___
login[ ] upload[ ] download-bytes-match[ ] search[ ] preview[ ] permissions-403/allow[ ]
actuator-health-only[ ] swagger-not-public[ ] CORS-reject-unlisted[ ] share/WOPI/transfer-HTTPS-only[ ] ml-uid-10001[ ]
Skipped steps + reason + owner sign-off: __________
```

### Phase 6 — sign-off
```
Commit SHA / Environment / Custodian / S2 done(ts) / B1B2 / A11 / B3 / B4 / residual risks / owner sign-off
```

## D. Failure-triage order (from the plan §13)
1 missing env / bad interpolation · 2 **Keycloak won't boot → KC_HTTP_ENABLED/hostname/proxy, not start-dev/import** · 3 issuer/JWK mismatch · 4 TLS/cert/domain · 5 DB cred/schema restore · 6 content volume · 7 ES auth/reindex · 8 permission/CORS · 9 preview/ML optional.

## E. Honest status

- **On-box preflight: DONE & green.** Templated config + Dockerfile hardening are shape-valid.
- **S2 / B1/B2 / B3 / B4: NOT executed, NOT done** — blocked on daemon + real secrets/domain/certs/realm. Owner runs them on a capable host and returns redacted evidence per §C; the Athena reviewer then diagnoses and records results.
- `.env` untouched; no secret values printed or fabricated.
