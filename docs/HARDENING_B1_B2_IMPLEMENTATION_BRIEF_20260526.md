# B1 / B2 — Implementation Brief (READ-ONLY — awaiting gate; no config change yet)

Date: 2026-05-26 · Status: **read-only spec. No config/file change until gate-approved.** (v2 — gate fixes: prod Keycloak `volumes: !reset []` to drop the base realm-import mount; nginx snippet = locations-only, hardened headers live in the 443 server not the snippet; open questions ruled) · Parent: `docs/HARDENING_B1_B2_KEYCLOAK_TLS_BRIEF_20260526.md` (design, gate-approved + rulings) · Matrix §8.3 B1/B2.

## Goal & non-goal

Ship **reviewable, daemon-free-verifiable templated prod config + a guard**, so the owner cutover drops from "read docs, hand-edit" to "fill env + drop certs + run B4". **Runtime cutover, real cert/domain, Keycloak realm provisioning, and the B4 smoke remain owner/ops** — this slice does **not** claim B1/B2 runtime done, supplies **no** real domain/secret values, and needs **no** Docker daemon to verify.

## Hard constraint discovered (must not break dev)

`nginx/nginx.conf` is the **single** mounted nginx config (`docker-compose.yml:392`), shared by dev. Dev has **no certs**; enabling `listen 443 ssl` in that shared file would make **dev nginx fail to start** (missing `ssl_certificate`). The base `docker-compose.prod.yml` currently has **no nginx block** (`:78` "intentionally NOT overridden").

➡️ **Design decision (locked): the dev `nginx/nginx.conf` is left untouched. HTTPS is introduced via a prod-only nginx config swapped in by the prod override.** No dev breakage.

## Deliverables (what the implementation slice will create)

### 1. `docker-compose.prod.yml` — Keycloak block (currently only `ports: !reset []`)
Add, per parent rulings (separate subdomain, out-of-band realm, prod `start`):
```yaml
keycloak:
  ports: !reset []
  command: ["start"]                      # NOT start-dev; NOT --import-realm (realm is out-of-band)
  volumes: !reset []                      # drop the base realm-import mount (see below) — out-of-band realm
  environment:
    - KC_HOSTNAME=${ECM_KEYCLOAK_PUBLIC_HOST:?required}
    - KC_PROXY_HEADERS=xforwarded
    - KC_HTTP_ENABLED=true                 # REQUIRED — prod `start` HTTP listener is off by default (KC23)
```
**Why `volumes: !reset []`:** base compose mounts `./keycloak/realm-export.json:/opt/keycloak/data/import/realm.json` (`docker-compose.yml:252`). Plain `start` (no `--import-realm`) would not import it, but leaving the mount in prod muddies the out-of-band-realm boundary (ruling 3). `!reset []` removes the inherited mount so the repo realm export is **not** present in the prod steady state. (If the owner later wants an inert sanitized reference, that is a separate explicit decision — default is no mount.)
- issuer/JWK naming is already on **ecm-core** in the prod override (`ECM_JWT_ISSUER_URI`/`ECM_JWT_JWK_SET_URI` → `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_*`). Operator sets `ECM_JWT_ISSUER_URI=https://<keycloak-subdomain>/realms/ecm` (must equal token `iss`); `ECM_JWT_JWK_SET_URI` may be the internal `http://keycloak:8080/realms/ecm/protocol/openid-connect/certs` (signature check is transport-agnostic — parent §B1.3). The brief does **not** add real hosts; those stay fail-fast env.

### 2. `docker-compose.prod.yml` — nginx block (new)
```yaml
nginx:
  volumes:
    - ./nginx/nginx.prod.conf:/etc/nginx/nginx.conf:ro          # swap prod conf (dev conf untouched)
    - ./nginx/athena-locations.conf:/etc/nginx/athena-locations.conf:ro   # shared location snippet
    - ./nginx/ssl:/etc/nginx/ssl:ro                              # certs dropped here by owner at cutover
```
(ports stay 80/443 per A8 — nginx is the only published service.)

### 3. `nginx/athena-locations.conf` (new — shared snippet, low-drift, ruling §B2.1)
**Contains ONLY the business `location` blocks** — `/api/`, the upload regex `^/api/v1/(documents/upload|nodes/.*/content)`, `/swagger-ui/`, `/actuator/`, `/`, `/health` — copied from today's port-80 server (`nginx/nginx.conf:62-152`) with their `proxy_pass`/`proxy_set_header X-Forwarded-Proto $scheme` etc. **It must NOT contain the `add_header` security headers.** (Today those headers sit at *server* scope, not in the locations; putting them in the shared snippet would either duplicate them into the 80 redirect server or let the prod HTTPS headers drift. Headers belong to the 443 server — deliverable 4.) One file, `include`d by the HTTPS server → no two-block location drift.

### 4. `nginx/nginx.prod.conf` (new)
- Reuse the existing `http{}` preamble (resolver, mime, gzip, upstreams `ecm_backend`/`ecm_frontend`).
- **Port 80 server:** redirect-only — `return 301 https://$host$request_uri;`. **No business locations, no security headers** (nothing to protect on a pure redirect). No ACME passthrough in v1 (ruling below).
- **Port 443 server:** `listen 443 ssl http2;` + `ssl_certificate /etc/nginx/ssl/cert.pem; ssl_certificate_key /etc/nginx/ssl/key.pem; ssl_protocols TLSv1.2 TLSv1.3;` then **the hardened `add_header` security headers at server scope** (see next), then `include /etc/nginx/athena-locations.conf;`.
- **Hardened headers live in the 443 server block (NOT the snippet):** CSP **without bare `http:`** (https/self/data/blob only); **HSTS** `Strict-Transport-Security: max-age=31536000; includeSubDomains`; keep X-Frame-Options/X-Content-Type-Options/Referrer-Policy. (Use `add_header ... always` so they apply to proxied responses.)
- `server_name` is **not** a real domain — use `_` (catch-all) or a placeholder comment; the operator sets the real name at cutover. No real domain literal committed.

## Guard — `scripts/b1b2-prod-config-check.sh` (no daemon)

Static + `docker compose config` (daemon-free) assertions; consistent with `scripts/ml-service-dockerfile-check.sh` (E1/P0a-3b pattern). Must assert:
1. **Keycloak prod posture** in `docker-compose.prod.yml`: `command:` is `start` (not `start-dev`, **no `--import-realm`**); `volumes: !reset []` (**no realm-import mount inherited** — out-of-band); `KC_HTTP_ENABLED=true`; `KC_HOSTNAME=${ECM_KEYCLOAK_PUBLIC_HOST:?required}`; `KC_PROXY_HEADERS=xforwarded`.
2. **nginx override** mounts `nginx.prod.conf` + `athena-locations.conf`.
3. **80 server has NO business locations** — grep the 80 `server{}` of `nginx.prod.conf` contains a `301`/redirect and **no** `proxy_pass`/`location /api`.
4. **443 server holds the hardened headers** — `listen 443 ssl`, `include .../athena-locations.conf`, **HSTS present in the 443 server block**, **CSP has no bare `http:`**, X-Frame/X-Content-Type/Referrer present.
5. **Snippet = locations only, no headers** — `athena-locations.conf` contains the business set present in today's HTTP server (`/api/`, upload regex, `/`, `/health`, `/actuator/`, `/swagger-ui/`) (drift guard) **and contains no `add_header` security-header lines** (those must be in the 443 server, guard 4).
6. **No real domain/secret literals** — no committed cert/key bytes (the `ssl/` dir holds none in repo), no hard-coded public hostname (hosts are `${...}` env or operator-set at cutover).
7. **Dev untouched** — `nginx/nginx.conf` still HTTP-only (443 block still commented; no `listen 443 ssl` active).
8. **Merged config parses** — `docker compose -f docker-compose.yml -f docker-compose.prod.yml config` with dummy required env → rc=0 (daemon-free), and Keycloak `command` resolves to `start`.

## Verification posture

- All guard checks run **on this box, no daemon** (text + `docker compose config`). This is the CI-gateable/local part.
- **NOT proven here (owner/ops, runtime):** real TLS handshake, real cert validity, Keycloak `start` actually booting with a real hostname, token `iss` matching, B4 full-stack smoke. These stay owner/ops — the slice will not claim them.

## Scope / non-goals

- Touch only: `docker-compose.prod.yml` (keycloak+nginx blocks), new `nginx/nginx.prod.conf`, new `nginx/athena-locations.conf`, new `scripts/b1b2-prod-config-check.sh`, a verification doc. **No change to `nginx/nginx.conf` (dev), base `docker-compose.yml`, or app code.**
- No real domain, no certs, no secret values, no realm export committed (out-of-band per ruling).
- No `.env` change (S1/S2 untouched).

## Gate rulings (2026-05-26 — accepted; bind the implementation slice)

1. **Snippet path = `/etc/nginx/athena-locations.conf`** (not `conf.d/`, to avoid nginx's automatic `conf.d/*.conf` include semantics — the snippet is `include`d explicitly by the 443 server only).
2. **ACME = no challenge passthrough in v1** — port 80 is pure redirect; cert automation (Let's Encrypt etc.) is owner/cert-tooling, added separately at cutover.
3. **Guard = standalone `scripts/b1b2-prod-config-check.sh`** (matches the P0a-3b `ml-service-dockerfile-check.sh` pattern).

## Original open questions (now resolved by the rulings above)

1. ~~snippet path `/etc/nginx/` vs `conf.d/`~~ → `/etc/nginx/athena-locations.conf`.
2. ~~ACME passthrough now?~~ → no, pure redirect in v1.
3. ~~guard standalone vs folded~~ → standalone `scripts/b1b2-prod-config-check.sh`.
