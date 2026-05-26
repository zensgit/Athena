# P0a-3 — Prod Compose / Runtime Shape (A7–A11) — Implementation Brief (read-only)

Date: 2026-05-26
Status: **read-only brief — no config/compose/Dockerfile/`.env` change by this document.** Awaiting gate.
Parent: §8 Hardening Matrix in `docs/PRODUCTION_READINESS_ASSESSMENT_20260525.md`. Covers **A7, A8, A9, A10, A11**.
Predecessors: P0a-1 (`e4f1b4c`) + P0a-2 (`9820996`) closed.

## 0. Scope (locked)

Harden the **deployment shape** (compose + ml-service image). No app code.

- **A7** — add `docker-compose.prod.yml` (a prod *override*, applied via `-f docker-compose.yml -f docker-compose.prod.yml`).
- **A8** — in the prod override, **do not publish internal service host ports**; expose only nginx `80/443`.
- **A9** — **pin image tags** (eliminate `:latest`; pin floating tags to explicit versions).
- **A10** — prod override: ES `xpack.security` **on**; MinIO / Grafana / Prometheus non-default creds via env.
- **A11** — `ml-service` image runs as **non-root**.

## 1. Approach + where each lands (so CI vs B4 is honest)

- **A9 image pinning → BASE `docker-compose.yml`** (benefits every env + **CI actually pulls the pinned images**, so it's CI-verified). The three `:latest` are must-fix: `minio/minio:latest` (`:220`), `prom/prometheus:latest` (`:412`), `grafana/grafana:latest` (`:431`). Recommended also: pin the soft-floating tags (`postgres:15-alpine`, `redis:7-alpine`, `rabbitmq:3.12-management-alpine`, `nginx:alpine`, `clamav/clamav:stable`, `odoo:16`, `elasticsearch:8.11.1` already specific) to explicit patch versions.
- **A8 + A10 → NEW `docker-compose.prod.yml` (override)** — inert for CI (CI uses base + dev override), so its **runtime is B4**; config-content is lint/grep-verifiable now. A8: override drops `ports:` for every internal service (keep only nginx). A10: override sets ES `xpack.security.enabled=true` (+ the app then needs ES creds, which the prod Spring profile already env-sources), and routes MinIO/Grafana/Prometheus admin creds through env (no `admin_password`/`minio_secret_key` literals).
- **A11 ml-service non-root → `ml-service/Dockerfile`** (base image change; **CI builds/health-checks the stack**, so non-root is CI-exercised — provided the entrypoint/files are readable as non-root).

## 2. Regression rationale + risks

- `docker-compose.prod.yml` is inert unless explicitly `-f`-composed → zero CI impact (A8/A10).
- A9 base pinning = freezing to (current) explicit versions → reproducibility, no behavior change; CI pulls the pinned tags.
- **A11 is the real risk:** adding `USER` to `ml-service/Dockerfile` can break startup if files/dirs the service writes (model cache, tmp) aren't owned by the non-root user, or if the entrypoint expects root. Must verify the ml-service health check still passes in CI (it runs in Acceptance Smoke / E2E stack). If it can't be made non-root safely in this slice, **split A11 to its own row** rather than risk the green E2E gates.

## 3. Files

### Create
- `docker-compose.prod.yml` — prod override: no internal `ports:` (A8), ES `xpack.security.enabled=true` + `ELASTIC_PASSWORD` env, MinIO `MINIO_ROOT_USER/PASSWORD` env, Grafana `GF_SECURITY_ADMIN_*` env, Prometheus as needed (A10). Documented as `-f docker-compose.yml -f docker-compose.prod.yml`.

### Modify
- `docker-compose.yml` — pin the 3 `:latest` images (+ recommended soft-float pins) (A9).
- `ml-service/Dockerfile` — add a non-root `USER` (+ ensure writable paths are chowned) (A11).

### Explicitly NOT touched
- No app code / `SecurityConfig` / Spring profiles (P0a-1/P0a-2 done), no `.env`/secret (S1/S2), no TLS/Keycloak-prod/backup (P0b), no `docker-compose.override.yml` (dev) behavior.

## 4. Verification (honest CI vs B4 split)

- **A9 (base pins):** CI-verifiable **Y** — the Docker-backed gates (Backend Verify deps aside; Acceptance Smoke / Frontend E2E Core boot the stack) pull the pinned images; a lint/grep assertion confirms no `:latest` remains.
- **A11 (ml-service non-root):** CI-verifiable **Y/partial** — CI builds + health-checks ml-service; if green, non-root works. (Local build = off-box here.)
- **A8 + A10 (prod override):** **config-content Y** (lint + grep: prod override has no internal `ports:`, sets `xpack.security.enabled=true`, creds via `${ENV}` not literals); **runtime = B4** (booting the prod override with ES auth needs Docker + real creds — off-box). Do **not** claim A8/A10 runtime-proven from this box.
- `git diff --check -- . ':!.env'` clean. No Java change → `backend-preflight` not the verifier here; a small compose/grep assertion (script or doc-recorded checks) is the proof.

## 5. Gate decisions

- **D1 (A9 placement/depth):** pin in **base** (recommended, CI-verified) vs prod-override-only? And: kill only `:latest` (minimum) or also pin the soft-float `:alpine`/`:stable`/`:16` tags (recommended)?
- **D2 (A8):** prod override unpublishes **all** internal ports, exposing only nginx `80/443` — confirm no internal service legitimately needs a host port in prod.
- **D3 (A10):** ES `xpack.security.enabled=true` in the prod override (app then authenticates via the prod profile's ES creds) — confirm; full interplay validated in B4.
- **D4 (A11):** attempt ml-service non-root **in this slice** (with chowned writable paths, CI health-check as proof), or **split it out** if it risks the E2E gate?

## 6. Out of scope

- B4 (prod-shape full-stack smoke), TLS, Keycloak prod realm, backup/restore — owner/env.
- S1/S2 (.env untrack + rotation) — owner.
- No new app features; no CI workflow rewrite.

## 7. Commit cadence (after gate)

1. `chore(core): pin compose image tags + ml-service non-root` (A9/A11, base).
2. `chore(core): add docker-compose.prod.yml (closed ports, ES/MinIO/Grafana prod security)` (A7/A8/A10).
3. `docs(core): record P0a-3 verification`; push; gate CI via `gh run view`; on 7/7, `[skip ci]` CI record.
(If D4 splits A11, it gets its own commit/row.)

## 8. Verification (this brief)

```bash
git status --short                              # M .env + this brief only
git diff --stat -- 'ecm-core/' 'ecm-frontend/'  # empty
```
