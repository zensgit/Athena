# P0a-3 — Prod Compose / Runtime Shape (A7/A8/A9/A10) — Implementation Brief (read-only)

Date: 2026-05-26
Status: **read-only brief — no config/compose/Dockerfile/`.env` change by this document.** Revision: **v3** (gate round-2 micro-fix). Approved for implementation pending this fix.
Parent: §8 Hardening Matrix in `docs/PRODUCTION_READINESS_ASSESSMENT_20260525.md`. Covers **A7, A8, A9, A10**. **A11 is split out** (see §0).
Predecessors: P0a-1 (`e4f1b4c`) + P0a-2 (`9820996`) closed.

## Revision history (v1 → v2, all gate findings verified directly)

- **Blocker — A11 split out.** CI builds only `ecm-core ecm-frontend` and starts infra/core/frontend; **ml-service is never built or started** (`ci.yml:163,284,391`; 0 mentions of `ml-service`; dep commented at `docker-compose.yml:80`). So "non-root ml-service is CI-verified" was false, and verifying it needs building ml-service (off-box / a CI change we're not doing). **A11 deferred to its own row P0a-3b** — not in this slice.
- **Blocker — A8 needs `!reset`.** In Compose, an override `ports: []` does **not** remove inherited ports; only `ports: !reset []` does. v2 requires `!reset []` per internal service, and verification uses `docker compose -f docker-compose.yml -f docker-compose.prod.yml config` (the merged config), **not grep**.
- **Medium — A10 must make the prod profile coherent.** Base forces `SPRING_PROFILES_ACTIVE=docker` (`:13`) and gives ES URI without creds (`:22`). The prod override must set ecm-core `SPRING_PROFILES_ACTIVE=prod` **and** provide every no-default env `application-prod.yml` requires (datasource/ES/Redis/Rabbit/JWT/CORS) — else flipping ES `xpack` alone is incoherent and B4 can't exercise prod.
- **Medium — fail-fast secrets.** Compose treats unset `${VAR}` as blank+warning. v2 uses `${VAR:?required}` for all prod-only secrets/required envs (hard failure if missing).
- **Low — A9 CI wording.** CI only pulls images for the services it starts; ml-service/collabora/greenmail/odoo/prometheus/grafana are not all started. A9 is **lint/config-verifiable for all images, runtime-verified only for CI-started services.**

- **v2 → v3 (gate round-2):** Prometheus has **no auth mechanism** (verified: command at `docker-compose.yml` is scrape/tsdb/console/lifecycle only — no `--web.config.file`; no `basic_auth` in `monitoring/prometheus.yml`). So "Prometheus admin creds via env" was invalid — A10 corrected to **Prometheus = `!reset []` only, no auth in v1**. Also added the `docker compose config` dummy-env note (config fail-fasts on `${VAR:?required}` before assertions can run).

## 0. Scope (locked)

- **A7** — add `docker-compose.prod.yml` (prod override; `-f docker-compose.yml -f docker-compose.prod.yml`).
- **A8** — override removes published host ports for every internal service via `ports: !reset []`; only nginx keeps `80/443`.
- **A9** — pin image tags in **base** compose (kill all `:latest`; prefer pinning soft-float tags too).
- **A10** — override makes a coherent prod runtime: ecm-core `SPRING_PROFILES_ACTIVE=prod` + all required prod envs; ES `xpack.security.enabled=true`; **MinIO + Grafana** admin creds via `${VAR:?required}`. **Prometheus: no auth in v1** (it has no auth mechanism today — no `--web.config.file`, no basic_auth in `monitoring/prometheus.yml`); P0a-3 only ensures it is **not externally published** (`ports: !reset []`). Adding `PROMETHEUS_*` creds would be a no-op; external Prometheus access is a separate future item (web-config/basic-auth or behind nginx).
- **A11 (split → P0a-3b, not here):** ml-service non-root Dockerfile, with an explicit `docker compose build ml-service` + health verification path (since CI doesn't cover it).

## 1. Files

### Create
- `docker-compose.prod.yml` — prod override:
  - Every internal service (`postgres`, `postgres-keycloak`, `elasticsearch`, `redis`, `rabbitmq`, `minio`, `keycloak`, `clamav`, `odoo`, `greenmail`, `collabora`, `prometheus`, `grafana`, `ecm-core`, `ecm-frontend` if it publishes): `ports: !reset []`. **nginx keeps `80:80`/`443:443`.** (A8)
  - `elasticsearch.environment`: `xpack.security.enabled=true` + `ELASTIC_PASSWORD=${ELASTIC_PASSWORD:?required}`. (A10)
  - `minio.environment`: `MINIO_ROOT_USER=${MINIO_ROOT_USER:?required}` / `MINIO_ROOT_PASSWORD=${MINIO_ROOT_PASSWORD:?required}`. `grafana`: `GF_SECURITY_ADMIN_USER`/`GF_SECURITY_ADMIN_PASSWORD` `${...:?required}`. **Prometheus: `!reset []` only — no auth env** (no auth mechanism exists; adding `PROMETHEUS_*` would be a no-op). (A10)
  - `ecm-core.environment`: `SPRING_PROFILES_ACTIVE=prod` **and** the no-default envs `application-prod.yml` requires — `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `SPRING_ELASTICSEARCH_URIS/USERNAME/PASSWORD`, `SPRING_DATA_REDIS_HOST/PASSWORD`, `SPRING_RABBITMQ_HOST/USERNAME/PASSWORD`, `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI/JWK_SET_URI`, `ECM_SECURITY_CORS_ALLOWED_ORIGINS` — each `${VAR:?required}`. (A10)

### Modify
- `docker-compose.yml` — pin `minio/minio:latest`→explicit, `prom/prometheus:latest`→explicit, `grafana/grafana:latest`→explicit (A9 must-fix); prefer pinning soft-float `postgres:15-alpine`/`redis:7-alpine`/`rabbitmq:3.12-management-alpine`/`nginx:alpine`/`clamav/clamav:stable`/`odoo:16` to explicit patch versions (A9 recommended).

### NOT touched
- `ml-service/Dockerfile` (A11 → P0a-3b), app code / SecurityConfig / Spring profiles (P0a-1/2 done), `.env`/secret (S1/S2), TLS/Keycloak-prod/backup (P0b), `docker-compose.override.yml` (dev).

## 2. Verification (honest on-box vs B4)

- **A9 (base pins):** in-repo grep asserts no `:latest` remains (all images). **Runtime-pulled by CI only for started services** (postgres/postgres-keycloak/redis/elasticsearch/minio/rabbitmq/clamav/keycloak/ecm-core/ecm-frontend); the rest (collabora/greenmail/odoo/prometheus/grafana/ml-service) are lint/config-only. CI-verifiable = Y for started, config-only for rest.
- **A8 + A10 (override):** verify with **`docker compose -f docker-compose.yml -f docker-compose.prod.yml config`** — assert (a) only `nginx` has `ports:` publishing `80`/`443`; (b) `ecm-core` env has `SPRING_PROFILES_ACTIVE=prod` + the required envs; (c) ES `xpack.security.enabled=true`. **`docker compose config` does not need the daemon** → attempt on-box; if the docker CLI is unavailable here, this drops to B4. **Full runtime boot of the prod override (ES auth, prod profile up) stays B4** (needs daemon + real secrets — off-box).
- `${VAR:?required}` behavior (hard fail on missing) is validated when the override is actually composed → B4 / the on-box `config` run surfaces unset required vars. **To assert ports/env *shape* with `docker compose config`, first export dummy values for every `${VAR:?required}`** (otherwise `config` fail-fasts on the first missing var before any assertion can run); a separate run with a var deliberately unset proves the fail-fast.
- `git diff --check -- . ':!.env'` clean. No Java change.

## 3. Gate decisions (round-1 rulings adopted)

- **D1 — adopted:** pin in **base**; kill all `:latest`; **prefer** pinning soft-float tags too.
- **D2 — adopted:** `ports: !reset []` per internal service + a `docker compose config` assertion that only nginx publishes `80/443` (not grep).
- **D3 — adopted:** override includes a coherent ecm-core prod profile (`SPRING_PROFILES_ACTIVE=prod`) + ES auth + all required envs via `${VAR:?required}`.
- **D4 — adopted (split):** A11 ml-service non-root is **out of this slice** → P0a-3b, which must add an explicit `docker compose build ml-service` + start/health step (its own verification path), since current CI doesn't build ml-service.

## 4. Out of scope

- A11 (ml-service non-root) → P0a-3b. B4 prod-shape smoke / TLS / Keycloak prod / backup-restore → owner/env. S1/S2 → owner. No CI workflow rewrite in this slice.

## 5. Commit cadence (after re-gate)

1. `chore(core): pin compose image tags` (A9, base).
2. `chore(core): add docker-compose.prod.yml (closed ports via !reset, prod profile + ES/MinIO/Grafana security, fail-fast envs)` (A7/A8/A10).
3. `docs(core): record P0a-3 verification` (incl. the `docker compose config` assertion output if runnable on-box, else marked B4); push; gate CI via `gh run view`; on 7/7, `[skip ci]` CI record.

## 6. Verification (this brief)

```bash
git status --short                              # M .env + this brief only
git diff --stat -- 'ecm-core/' 'ecm-frontend/'  # empty
```
