# B1 / B2 — Keycloak prod + TLS/nginx HTTPS (READ-ONLY BRIEF)

Date: 2026-05-26 · Status: **read-only — awaiting gate. No config change until approved; no live execution; no secret values.** (v2 — gate fixes: issuer/JWK independence corrected at top to match §B1.3; `start --import-realm` reframed as bootstrap-only not steady-state; nginx HTTPS requires low-drift include/guard, not "replicate") · Matrix §8.3 B1/B2 of `docs/PRODUCTION_READINESS_ASSESSMENT_20260525.md`.

## Why B1 + B2 are one brief (coupling)

The external HTTPS shape and the IdP are interlocked — they cannot be specified independently:
- The backend's **`issuer-uri`** must equal the token's `iss` claim — i.e. the **public HTTPS issuer** Keycloak emits (driven by `KC_HOSTNAME`). The **`jwk-set-uri`** is **independent**: it may be the public HTTPS URL *or* the compose-internal URL, as long as it serves the same realm's signing keys — it does **not** need to equal the issuer (signature validation is transport-agnostic). See §B1.3.
- The frontend **redirect origins** registered on the Keycloak client must be the public HTTPS origin.
- **Bearer tokens** and the transfer-receiver/WOPI **opaque headers** ride the HTTP transport → require TLS.

So the real coupling is **issuer + frontend redirect origin + TLS transport** (not "JWK must equal the public URL"). A wrong call in one — e.g. internal `http://keycloak:8080` leaking into the issuer — breaks the others. This brief fixes them consistently.

## Current state (verified, repo evidence)

**Keycloak (B1):**
- `docker-compose.yml:241-242` — `quay.io/keycloak/keycloak:23.0.0`, **`command: start-dev --import-realm`** (dev mode: no HTTPS req, lenient hostname). Realm `ecm`; `KC_DB=postgres` (`postgres-keycloak`); `KC_HEALTH_ENABLED=true`; admin bootstrap via `KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD`; published `${KEYCLOAK_PORT:-8180}:8080`.
- App→Keycloak internal: `ECM_KEYCLOAK_URL=http://keycloak:8080`, `ECM_KEYCLOAK_REALM=ecm` (`docker-compose.yml:15-16`).
- Prod override (`docker-compose.prod.yml`) currently only closes Keycloak's ports (`!reset []`) — **no prod hostname/proxy env yet** (this brief defines it).

**TLS / nginx (B2):**
- `nginx/nginx.conf` — **HTTP only**: `listen 80` (`:63`), `server_name localhost ecm.local` (`:64`). The **443 `ssl` server block is entirely commented out** (`:155-164`), referencing `/etc/nginx/ssl/cert.pem` + `key.pem`.
- Compose mounts `./nginx/nginx.conf` and `./nginx/ssl` (`docker-compose.yml:392-393`) — cert mount path already exists.
- Security headers present (X-Frame-Options, X-Content-Type-Options, Referrer-Policy, CSP) but **CSP allows `http:`** (`:71`, mixed-content) and there is **no HSTS**. `proxy_set_header X-Forwarded-Proto $scheme` already set (`:81` etc.) — good for proxy-mode.
- **Plaintext-token risk:** `/api/v1/transfer/receiver/**` (`SecurityConfig:71`) and `/wopi/**` (`:73`) are `permitAll` and authenticated by **dedicated opaque headers in the controller/service layer** (`:70`, `:133-135`), *not* JWT. Over HTTP these credentials are in cleartext → **HTTPS is mandatory in prod**. Same for `/api/v1/share/access/**` share tokens.

## B1 — Keycloak production posture (to specify; not yet changed)

1. **Runtime mode:** the runtime command is **`start`** (production mode: enforces hostname, optimized) **with the prod hostname/proxy/http settings of step 2** — *not* `start-dev`. **Realm import is a bootstrap/provisioning step only, not a runtime state-management strategy.** Do **not** treat `start --import-realm` as the recommended steady-state command. Owner chooses one of:
   - **Out-of-band realm administration** (recommended steady state): provision the realm once, then manage it via the admin API/console; runtime command is plain `start`.
   - **Import-on-start**, *only if* the owner explicitly accepts an "idempotent + sanitized" ops model — and then it must: (a) contain **no secret material** in the committed export, (b) **not overwrite** manual realm changes (import semantics must be understood — `OVERWRITE` vs `IGNORE`/`SKIP`), and (c) the owner owns the realm-export lifecycle (who regenerates/sanitizes it).
2. **Hostname / proxy** (behind nginx TLS): set **three** envs, not two — `KC_HOSTNAME=<public keycloak host>`, `KC_PROXY_HEADERS=xforwarded` (KC 23), **and `KC_HTTP_ENABLED=true`**. The third is **required**: in KC 23 `start` (production) mode the HTTP listener is **off by default**, so with TLS terminating at nginx and Keycloak speaking HTTP behind it, `start` will **refuse to boot** without `KC_HTTP_ENABLED=true` (the classic "set proxy headers, still won't start" gotcha — `KC_HOSTNAME`/`KC_PROXY_HEADERS` only control URL emission + header parsing, not the listener). Net effect: issued tokens carry the **public HTTPS issuer** while Keycloak speaks HTTP internally; the internal `http://keycloak:8080` must NOT appear as issuer. (Implementation will therefore add **three** envs to the currently-empty `docker-compose.prod.yml` Keycloak block.)
3. **Issuer / JWK (the coupling):** backend prod must set `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://<keycloak-public>/realms/ecm` and a `jwk-set-uri` (already `${ECM_JWT_ISSUER_URI:?required}` / `${ECM_JWT_JWK_SET_URI:?required}` in `docker-compose.prod.yml` — B1 supplies the values). The two are **independent**: Spring validates the token's `iss` claim against `issuer-uri` (so `issuer-uri` **must equal** the public URL `KC_HOSTNAME` makes Keycloak emit), while signatures are validated against keys from `jwk-set-uri` (transport-agnostic, so `jwk-set-uri` **may point at the internal URL** for in-cluster fetch). Do not conflate them.
4. **Frontend client:** the `REACT_APP_KEYCLOAK_*` client must be a **public** client (no secret in the bundle — see S2 §3); register redirect URIs / web origins to the public HTTPS frontend origin only.
5. **Admin bootstrap boundary:** `KEYCLOAK_ADMIN`/`_PASSWORD` are **bootstrap-only** — after first boot, create a managed admin, then disable/rotate the bootstrap admin (it is one of the S2 rotation items). Do not leave the dev admin enabled.
6. **DB:** `postgres-keycloak` is a Tier-2 backup target (B3) when Keycloak is deployed.

## B2 — TLS / nginx HTTPS enforcement (to specify; not yet changed)

1. **Enable HTTPS without server-block drift.** The 443 block needs `listen 443 ssl http2`, `ssl_certificate /etc/nginx/ssl/cert.pem`, `ssl_certificate_key /etc/nginx/ssl/key.pem`, `ssl_protocols TLSv1.2 TLSv1.3`. But **do not just "uncomment and replicate"** the long HTTP `location` set — two hand-copied server blocks drift over time (a `location` added to one, missed in the other). The implementation slice must pick a **low-drift** approach:
   - **Recommended:** extract the shared `location` blocks (`/api/`, the upload regex, `/swagger-ui/`, `/actuator/`, `/`, `/health`) into an **`include`d snippet** consumed by the HTTPS server; the port-80 server becomes a thin **HTTP→HTTPS redirect** block (no business locations).
   - **Or:** accept duplication **only with a guard** — a test/grep check asserting the key locations (`/api/`, upload, WOPI/share, `/actuator/`, `/`) exist under the 443 server. "Replicate" alone is not acceptable.
2. **Redirect/​block HTTP:** port-80 server returns `301`→https (or blocks all but ACME challenge). No plaintext `/api/**`.
3. **Harden headers for HTTPS:** tighten CSP (`:71`) to drop bare `http:` (https/self only); **add HSTS** (`Strict-Transport-Security: max-age=…; includeSubDomains`). Keep the existing X-Frame/X-Content-Type/Referrer headers.
4. **Cert source:** mount real certs into `./nginx/ssl` (owner input: Let's Encrypt vs corporate CA). `server_name` → real domain (not `localhost ecm.local`).
5. **Plaintext-token endpoints now safe:** with HTTPS enforced, the transfer-receiver/WOPI/share opaque headers (above) are no longer in cleartext. This is the **primary security reason** B2 is must-fix for any non-controlled network.
6. **WOPI / CORS implications:** `ECM_WOPI_PUBLIC_URL` / `ECM_WOPI_HOST_URL` and the prod `ECM_SECURITY_CORS_ALLOWED_ORIGINS` (A6) must all use the public **HTTPS** origin; Collabora is internal-only after A8, reached via the same TLS front door. Mixed http/https WOPI origins will break the editor iframe.

## Explicit owner inputs (decisions, not defaults — none decided here)

1. **Domain name(s):** public frontend host + public Keycloak host (same domain/subpath vs separate subdomain).
2. **Cert source:** Let's Encrypt (ACME automation) vs corporate/internal CA; renewal owner.
3. **Keycloak realm/client ownership:** who owns the `ecm` realm export, client list, redirect URIs; is realm managed by `--import-realm` or out-of-band.
4. **Secret custodian:** Keycloak admin + DB creds rotation (S2) and where prod secrets live.
5. **Realm provisioning mode:** import-on-start vs externally administered.

## Scope / non-goals

- **Read-only brief.** No change to `nginx/nginx.conf`, `docker-compose*.yml`, or Keycloak config in this slice — those land **only after gate approval**, as a separate implementation slice, and even then the **runtime cutover is owner/ops (off-box, real certs/domain) — B1/B2 cannot be claimed done from this box.**
- No secret values; no live Keycloak/TLS bring-up here.
- Does not implement B3 (backup) or B4 (smoke) — but B4's login step depends on B1, and B3 lists `postgres-keycloak`.

## Gate rulings (2026-05-26 — accepted; bind the implementation slice)

1. **Implementation product = templated prod config + guards, NOT waiting on real domain/certs.** The implementation slice ships reviewable artifacts: an nginx HTTPS `include`/snippet (low-drift, §B2.1) + a `docker-compose.prod.yml` Keycloak env block, parameterized with **fail-fast env** like `${ECM_PUBLIC_HOST:?required}` / `${ECM_KEYCLOAK_PUBLIC_HOST:?required}` — **no real values filled, runtime NOT claimed done** (cutover with real certs/domain stays owner/ops B-cutover).
2. **Domain shape = separate Keycloak subdomain (default).** App and Keycloak on distinct hosts → cleaner issuer / redirect / CORS. Path-routed Keycloak (rejected default) would add proxy-path + issuer-path + redirect-URI complexity.
3. **Realm management = out-of-band (default).** Plain `start` steady state; the repo keeps at most a **sanitized reference export** (no secrets) if useful. **Do not** make import-on-start the steady state (avoids overwriting manual config and turning realm lifecycle into a Git-file side effect).

## Original open questions (now resolved by the rulings above)

1. ~~templated vs doc-only~~ → ruling 1 (templated + guards, fail-fast env).
2. ~~single host vs subdomain~~ → ruling 2 (separate Keycloak subdomain).
3. ~~`--import-realm` vs out-of-band~~ → ruling 3 (out-of-band; sanitized reference export only).
