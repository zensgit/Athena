# B1 / B2 — Templated Prod Config Implementation — Verification

Date: 2026-05-26 · Brief: `docs/HARDENING_B1_B2_IMPLEMENTATION_BRIEF_20260526.md` (gate-approved v2) · Matrix §8.3 B1/B2.

## Changes shipped

- **`docker-compose.prod.yml`** — Keycloak block: `command: ["start"]`, `volumes: !reset []` (drops the base `realm-export.json` import mount), `KC_HOSTNAME=${ECM_KEYCLOAK_PUBLIC_HOST:?required}`, `KC_PROXY_HEADERS=xforwarded`, `KC_HTTP_ENABLED=true`. nginx block: `volumes: !override` swapping `nginx.prod.conf` in for the dev conf + mounting `athena-locations.conf` (+ ssl, logs).
- **`nginx/nginx.prod.conf`** (new) — 80 redirect-only; 443 `ssl http2` with hardened headers at server scope (HSTS + CSP without bare `http:` + X-Frame/X-Content-Type/Referrer) and `include /etc/nginx/athena-locations.conf`. `server_name _` placeholder, no real domain, no certs.
- **`nginx/athena-locations.conf`** (new) — business `location` blocks only (`/api/`, upload regex, `/swagger-ui/`, `/actuator/`, `/`, `/health`); **no security headers**.
- **`scripts/b1b2-prod-config-check.sh`** (new, executable) — daemon-free guard.
- **dev `nginx/nginx.conf` UNCHANGED** (still HTTP-only) — no dev breakage.

## Verification — daemon-free, on-box ✅

`./scripts/b1b2-prod-config-check.sh` → **rc=0**. Asserts:
1. Keycloak: `command ["start"]`, no `start-dev`/`--import-realm`, `volumes: !reset []`, `KC_HTTP_ENABLED=true`, fail-fast `KC_HOSTNAME`, `KC_PROXY_HEADERS=xforwarded`.
2. nginx override mounts `nginx.prod.conf` + `athena-locations.conf`.
3. Port-80 server is `301`-redirect-only (no `proxy_pass`, no snippet include).
4. Port-443 server: `listen 443 ssl`, includes the snippet, **HSTS present**, **CSP has no bare `http:`**, X-Frame/X-Content-Type/Referrer present.
5. Snippet = locations only, **no security headers**; key locations present (drift guard: `/api/`, upload, `/`, `/health`, `/actuator/`).
6. No real domain (`server_name _;`), no committed certs under `nginx/ssl`.
7. Dev `nginx/nginx.conf` still HTTP-only (no active `listen 443 ssl`).
8. **Merged `docker compose -f docker-compose.yml -f docker-compose.prod.yml config` validates (rc=0)** with dummy required env — daemon-free.

**Merge proof (`docker compose config`, dummy env):**
- nginx volumes resolve to exactly: `nginx.prod.conf → /etc/nginx/nginx.conf`, `athena-locations.conf`, `ssl`, `logs` — **no dev `nginx.conf`** (`!override` replaced the list).
- keycloak `command: ['start']`, `volumes: []` (import mount gone), `KC_HOSTNAME/KC_HTTP_ENABLED/KC_PROXY_HEADERS` present.

**Negative tests** (guard bites): a security header injected into the snippet → fail; CSP with bare `http:` → fail. Both confirmed; files restored.

## NOT proven here (owner/ops, runtime — B1/B2 NOT claimed done)

Real TLS handshake / cert validity, Keycloak `start` booting with a real hostname, token `iss` matching `issuer-uri`, the frontend redirect against the real IdP, and the B4 full-stack smoke. The operator at cutover: drops real `cert.pem`/`key.pem` into `nginx/ssl`, sets `server_name` + the `ECM_KEYCLOAK_PUBLIC_HOST`/`ECM_JWT_*`/`ECM_SECURITY_CORS_ALLOWED_ORIGINS` env, provisions the realm out-of-band, then runs B4.

## CI note

CI does **not** exercise `docker-compose.prod.yml` or `nginx.prod.conf` (only `-f`-applied; CI uses base + dev override) and the guard is not wired into a CI job, so this slice ships `[skip ci]`; the daemon-free guard is the authoritative verification. The base `docker-compose.yml` and app code are unchanged.

## Scope

Touched only: `docker-compose.prod.yml`, new `nginx/nginx.prod.conf`, new `nginx/athena-locations.conf`, new `scripts/b1b2-prod-config-check.sh`, this doc. No dev `nginx.conf`, no base compose, no app code, no `.env` (S1/S2 untouched).
