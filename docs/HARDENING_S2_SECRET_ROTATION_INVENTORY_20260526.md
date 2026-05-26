# S2 — Secret Rotation Inventory (READ-ONLY)

Date: 2026-05-26 · Status: **read-only inventory, not rotation** (v2 — gate findings folded in: §1.5 secret-capable non-`.env` envs incl. property-encryption key / CAD token / LDAP bind / Odoo API key; `ODOO_ADMIN_PASSWORD` reclassified; frontend public-by-design) · Matrix §8.2 S2 of `docs/PRODUCTION_READINESS_ASSESSMENT_20260525.md`.

## What this is / is NOT

- **IS:** an inventory of credential **key names** that were tracked in `.env` / `ecm-frontend/.env` (now untracked by S1, `83d6935`) and therefore remain reachable in git history → must be treated as **compromised** and rotated. Plus where each is consumed and how prod injects it.
- **IS NOT:** rotation. No secret value was read, printed, copied, or committed. No new secret generated. `.env` is **not** added back to Git. Key names were extracted with a line-start-to-first-`=` regex (`^[A-Za-z_][A-Za-z0-9_]*=`) so value bytes never reached output.
- **Custodian + rotation order + whether to scrub history are owner/ops decisions** — listed in §4, not decided here.

## 1. Secrets to rotate (credential-bearing keys exposed in history)

Counts are literal key-name occurrences (no values). "prod inject" = how `docker-compose.prod.yml` / `application-prod.yml` source it in the hardened profile.

| Key (dev `.env`) | Kind | base compose | prod override | app yml | code | Prod injection (as-shipped) | Rotate? |
|---|---|---|---|---|---|---|---|
| `POSTGRES_PASSWORD` | DB | 4 | 1 | — | — | `SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD:?required}` | **Yes** |
| `REDIS_PASSWORD` | cache | 2 | 1 | 2 | — | `SPRING_DATA_REDIS_PASSWORD=${REDIS_PASSWORD:?required}` | **Yes** |
| `RABBITMQ_PASSWORD` | broker | 2 | 1 | 2 | — | `SPRING_RABBITMQ_PASSWORD=${RABBITMQ_PASSWORD:?required}` | **Yes** |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | object store | 2 / 2 | — | — | — | **NAME MISMATCH** — prod requires `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` (different keys); see §2 | **Yes (rotate + rename)** |
| `KEYCLOAK_PASSWORD` | IdP admin | 2 | — | — | — | not set by prod override (Keycloak prod = **B1**); rotate when B1 done | **Yes (with B1)** |
| `KEYCLOAK_DB_PASSWORD` | IdP DB | 2 | — | — | — | not set by prod override (ties to B1) | **Yes (with B1)** |
| `COLLABORA_ADMIN_PASSWORD` | editor | 1 | — | — | — | not in prod override; collabora ports closed (A8) — internal only | **Yes** |
| `ODOO_DB_PASSWORD` | optional ERP | 2 | — | — | — | optional integration (not forced by prod profile, P0a-1 D4) | Yes **iff Odoo used** |
| `ODOO_ADMIN_PASSWORD` | historical/local | 0 | — | — | — | **no current repo consumer** (zero refs in compose/app/code; the app reads `ECM_ODOO_PASSWORD` instead — see §1.5) | Rotate **only if deployed outside this repo** |
| `ECM_WPS_APPKEY` | optional WPS | 1 | — | — | — | optional integration (empty default, P0a-1) | Yes **iff WPS used** |
| `ECM_WPS_APPID` | WPS identifier | 1 | — | — | — | identifier (not strictly secret, but rotate with APPKEY) | with APPKEY |

## 1.5 Secret-capable envs NOT present in tracked `.env` — provision / confirm if feature used

These keys are **not** in the historical tracked `.env` (no leaked value to rotate), but they are
**secret-capable** and consumed by config/compose — owner/ops must **provision** them when the
feature is deployed (and confirm they are never committed). Two groups:

**(a) Feature / optional secrets referenced in `application.yml`** (verified: `grep -rln`, key names only):

| Key | For | Refs | Action |
|---|---|---|---|
| `ECM_SECURITY_SECRET_KEY_V1` | **property-encryption key** (encrypts stored config/properties) | `application.yml` | **provision before enabling property encryption**; losing/changing it breaks decrypt of existing data — treat as a key-management item, not a routine rotation |
| `ECM_PREVIEW_CAD_AUTH_TOKEN` | CAD preview render auth token | `application.yml`, `docker-compose.yml` | provision if CAD preview (`ECM_PREVIEW_CAD_ENABLED=true`) is used |
| `ECM_LDAP_BIND_PASSWORD` | LDAP/AD bind password | `application.yml` | provision if LDAP sync is used |
| `ODOO_API_KEY` | Odoo API key | `application.yml` | provision if Odoo integration is used |
| `ECM_ODOO_PASSWORD` | Odoo password (app-side key the code actually reads; P0a-1 set empty default) | `application.yml` | provision if Odoo used — **this**, not `ODOO_ADMIN_PASSWORD`, is the live key |

**(b) Infra secrets the prod override requires** (`docker-compose.prod.yml`, `${VAR:?required}`) — new secrets to issue + hold:

| Key | For | Note |
|---|---|---|
| `ELASTIC_PASSWORD` | Elasticsearch | dev ran ES with security off; prod turns `xpack.security` on (A10) → brand-new secret |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | MinIO | replaces dev `MINIO_ACCESS_KEY`/`SECRET_KEY` names |
| `GF_SECURITY_ADMIN_USER` / `GF_SECURITY_ADMIN_PASSWORD` | Grafana | not in dev `.env`; prod requires (A10) |
| `ECM_SECURITY_CORS_ALLOWED_ORIGINS` | app CORS | config, not a secret — but required `${VAR:?required}` |
| `ECM_JWT_ISSUER_URI` / `ECM_JWT_JWK_SET_URI` | auth | config, not secret; point at the prod Keycloak (B1) |

## 2. Dead / no-rotate findings

- **`JWT_SECRET` — DEAD, remove not rotate.** Zero references in base compose, prod override, `application*.yml`, and Java/ml-service code (confirmed P0a-1: real auth is `jwk-set-uri`/`NimbusJwtDecoder`). It should be **deleted from `.env`**, not rotated. (Value is still in history, but it gates nothing.)
- **MinIO key-name mismatch** (above): prod uses `MINIO_ROOT_*`; the dev `MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY` will not feed prod. Rotation here = issue new `MINIO_ROOT_*` secrets; the old dev keys are dead in prod but still compromised in history.
- **`ODOO_ADMIN_PASSWORD` — no current repo consumer.** Zero references in compose/app/code (verified `grep -rln`); the app's live Odoo key is `ECM_ODOO_PASSWORD` (§1.5). It exists only in the historical/local `.env`; rotate only if it is wired into a deployment outside this repo.

## 3. Non-secret config keys (NO rotation — listed for completeness)

Ports / hosts / URLs / flags / names — not credentials:

- Root `.env`: `*_PORT` (COLLABORA/ECM_API/ECM_FRONTEND/KEYCLOAK/MINIO/MINIO_CONSOLE/POSTGRES/RABBITMQ/REDIS/ELASTICSEARCH), `*_HOST` (ELASTICSEARCH/RABBITMQ/REDIS), `*_URL`/`*_ENDPOINT`/`*_DOMAIN` (ML_SERVICE_URL, MINIO_ENDPOINT, ECM_API_BASE_URL, ECM_WOPI_*, ECM_WPS_DOMAIN, COLLABORA_DOMAIN), `*_ENABLED` flags (ECM_OCR_*, ECM_PREVIEW_CAD_*, ECM_WOPI_ENABLED, ECM_WPS_ENABLED, JODCONVERTER_LOCAL_ENABLED), names/non-secret (MINIO_BUCKET, POSTGRES_DB, ECM_PREVIEW_CAD_TIMEOUT_MS, JODCONVERTER_LOCAL_OFFICE_HOME), `*_USER`/`*_USERNAME` identifiers (COLLABORA_ADMIN_USER, KEYCLOAK_USER, KEYCLOAK_DB_USER, POSTGRES_USER, ODOO_DB_USER, RABBITMQ_USER — not secrets, but rotate **paired** with their password if org policy treats the pair as one credential).
- `ecm-frontend/.env`: `GENERATE_SOURCEMAP`, `REACT_APP_API_URL`, `REACT_APP_KEYCLOAK_CLIENT_ID`, `REACT_APP_KEYCLOAK_REALM`, `REACT_APP_KEYCLOAK_URL` — **public by design: every `REACT_APP_*` is baked into the public JS bundle at build time, so none are secret and none must ever hold secret material.** No rotation; the Keycloak client/realm/url just need to point at the prod IdP (B1). The Keycloak client referenced here must be a **public** client — if a *confidential* client (with a secret) is configured, that is a Keycloak configuration error to fix on the IdP side, **not** a frontend env rotation item.

## 4. Owner / ops decisions (NOT decided here)

1. **Custodian:** who holds prod secrets, and via what mechanism (Docker/K8s secrets, Vault, cloud secret manager)? Prod injection is already env-shaped (`${VAR:?required}`) so any env-backed store fits.
2. **Rotation order / blast radius:** DB/Redis/RabbitMQ rotations require coordinated app restart; ES (`ELASTIC_PASSWORD`) is a fresh provision tied to A10; Keycloak creds tie to **B1**.
3. **History scrub?** S1 did not rewrite history — decide whether to `git filter-repo`/BFG the historical `.env` values (rewrites remote history; high blast radius; separate explicit authorization) **or** rely on rotation alone (recommended: rotation invalidates the leaked values, scrub is optional cleanliness).
4. **Optional integrations:** rotate Odoo/WPS only if those integrations are actually deployed.
5. **Provision-new** (§1 second table): `ELASTIC_PASSWORD`, `MINIO_ROOT_*`, `GF_SECURITY_ADMIN_*` have no historical value — issue fresh.

## 5. Verification of THIS inventory (for gate)

- Zero values: every extraction used the `^KEY=`-anchored regex; only key names, file names, occurrence counts, and recommendations appear above.
- Coverage: cross-referenced against `docker-compose.yml`, `docker-compose.prod.yml`, `application-prod.yml`/`application*.yml` (`ecm-core/src/main/resources/`), and code (`ecm-core/src/main/java`, `ml-service/app`). v2 additionally swept config for **secret-capable env names not in tracked `.env`** (`ECM_SECURITY_SECRET_KEY_V1`, `ECM_PREVIEW_CAD_AUTH_TOKEN`, `ECM_LDAP_BIND_PASSWORD`, `ODOO_API_KEY`, `ECM_ODOO_PASSWORD`) and confirmed `ODOO_ADMIN_PASSWORD` has zero current consumers.
- No mutation: `.env` not re-added to Git, no secret changed/generated.
