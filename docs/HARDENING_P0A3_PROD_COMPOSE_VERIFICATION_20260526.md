# P0a-3 — Prod Compose / Runtime Shape (A7/A8/A9/A10) — Verification

Date: 2026-05-26
Brief: `docs/HARDENING_P0A3_PROD_COMPOSE_BRIEF_20260526.md` (v3, gate-approved). Matrix §8 of `docs/PRODUCTION_READINESS_ASSESSMENT_20260525.md`.
Scope: **A7/A8/A9/A10**. **A11 split → P0a-3b** (CI never builds ml-service).

## Changes shipped

- **`docker-compose.prod.yml` (new, `af6f060`)** — prod override (`-f docker-compose.yml -f docker-compose.prod.yml`):
  - **A8** every internal service `ports: !reset []`; only **nginx** publishes `80/443`.
  - **A10** `ecm-core` → `SPRING_PROFILES_ACTIVE=prod` + all no-default envs `application-prod.yml` requires (datasource/ES/Redis/Rabbit/JWT/CORS) as fail-fast `${VAR:?required}`; Elasticsearch `xpack.security.enabled=true` (http basic-auth, ports closed, no TLS cert mgmt in v1); MinIO + Grafana admin creds as `${VAR:?required}`. Prometheus = ports-closed only (no auth mechanism today; external access deferred).
- **`docker-compose.yml` image pins (`4382ea7`, A9)** — the three `:latest` → gate-verified tags: `minio/minio:RELEASE.2025-09-07T16-13-09Z`, `prom/prometheus:v3.11.3`, `grafana/grafana:12.4.3-security-02`. Soft-float tags (alpine/stable/16) intentionally untouched (no scope creep into image upgrades).

## Verification (on-box vs B4 — honest)

On-box (docker CLI/Compose **v5.1.1** present; `docker compose config` needs no daemon):

```
docker compose -f docker-compose.yml -f docker-compose.prod.yml config   (dummy required envs)
  → rc=0; only nginx publishes ports (published count: {nginx: 2}); ecm-core SPRING_PROFILES_ACTIVE=prod;
    elasticsearch xpack.security.enabled="true"
docker compose ... config   with ELASTIC_PASSWORD unset
  → rc=1: "required variable ELASTIC_PASSWORD is missing a value: required"   (fail-fast proven)
grep -c ':latest' docker-compose.yml          → 0
docker compose -f docker-compose.yml config   → rc=0  (base still parses after pins)
git diff docker-compose.yml                    → exactly the 3 image lines
```

- **A8/A10 config shape: on-box verified** (above). **Full runtime boot** of the prod override (ES auth up, prod profile healthy) = **gate item B4** (needs daemon + real secrets — off-box).
- **A9:** `:latest` eliminated (verified). **minio** is CI-started → its pinned tag is CI-validated. **prometheus/grafana** are not CI-started → config-verified only; runtime confirmation = B4.
- **No CI impact expected:** `docker-compose.prod.yml` is not auto-merged (only `-f`-applied), so CI (base + dev override) doesn't see it; the A9 pins are exercised by CI for the services it starts (incl. minio).

## A11 deferral (P0a-3b)

ml-service non-root is **not in this slice** — CI builds only `ecm-core ecm-frontend` and never builds/starts ml-service (`ci.yml:163/284/391`; dep commented `docker-compose.yml:80`). P0a-3b must add an explicit `docker compose build ml-service` + start/health verification path.

## Scope / non-goals

- No app code / SecurityConfig / Spring profile change (P0a-1/P0a-2). No `.env`/secret (S1/S2). No TLS / Keycloak-prod / backup (P0b). No soft-float image upgrades. No CI workflow rewrite.

## CI Follow-Up

```
Run id:        <pending — A9-head run>
Head SHA:      <pending>
Conclusion:    <pending — gh run view authority per feedback_gh_run_watch_unreliable>
```
(Override-only run `26434162067` on `af6f060` was in-flight and is superseded by the A9-head run.)
