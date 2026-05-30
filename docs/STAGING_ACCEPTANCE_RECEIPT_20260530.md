# Staging Acceptance Receipt — 2026-05-30

**Host:** `23.254.236.11` · **Commit:** `527bb33` · **App images:** `ghcr.io/zensgit/athena-*:latest`
**Evidence captured:** 2026-05-30T11:10Z (direct SSH on the staging host)

## Verdict

**Athena staging is usable for internal testing. It is NOT pilot/production evidence.**
The runtime is healthy and the core document path (auth → upload → download) works end-to-end,
but it runs a staging-only security/TLS posture. Public static-asset throughput had an earlier
slow-path signal; the current browser-sized gzip path is acceptable from this client but still
awaits second-network/provider validation if smoother public access is required.

## Evidence

### Version & images
- Serving repo: `/tmp/Athena.new` @ `527bb33`.
- App services run the **published ghcr images** (cut over from local-build, reversible):
  `ecm-core` / `ecm-frontend` / `ml-service` = `ghcr.io/zensgit/athena-*:latest`.

### Container health
- **17 containers running; no unhealthy running container.**
- `athena-clamav-1`: `Exited (143)` — stopped **by design** (see Antivirus).

### HTTPS / health / API auth
- internal `/actuator/health` → **200**
- public `https /health` → **200**
- public `https /api/v1/nodes` → **401** (auth enforced; nginx reaches backend)

### Antivirus — disabled by design (staging only)
- `ECM_ANTIVIRUS_ENABLED=false`, **persisted in the staging `.env`** (survives restart/cutover; `printenv` on ecm-core → `false`).
- Upload pipeline **SKIPs cleanly** (not fail-open); `clamav` stopped.
- **Authenticated upload/download smoke PASS** (closes #19): temp Keycloak user (realm `ecm`, client `unified-portal`) → `POST /api/v1/documents/upload` returned `documentId` → `GET /api/v1/nodes/{id}/content` returned **byte-identical** content; temp user deleted afterward.
- Production keeps AV **on**. Staging accepts **unscanned uploads by design**.

### TLS — staging-only posture
- Self-signed: `subject=issuer=CN=athena.local`.
- No real staging hostname is available yet. Trusted TLS is therefore **blocked on owner-provided DNS/hostname**; Let's Encrypt and normal browser-trusted cert issuance require a hostname (or DNS/provider-managed equivalent), not the bare IP.
- Acceptable for internal staging. **Pilot/customer-facing requires a real hostname + trusted cert** (e.g. Let's Encrypt, Cloudflare Tunnel/CDN, or another owner-approved TLS front door).

### No-domain auth workaround — same-origin Keycloak route
- Implemented and deployed after the source-map slice:
  - frontend default auth URL now uses same-origin on non-localhost deployments (`80820ce`);
  - nginx prod config proxies `/realms/...` and Keycloak static `/resources/...` to the Keycloak container (`80820ce`);
  - Keycloak public port is explicitly set so the public issuer is stable (`527bb33`).
- Public OIDC discovery via `https://23.254.236.11/realms/ecm/.well-known/openid-configuration` returns **200** and issuer `https://23.254.236.11:443/realms/ecm`.
- Backend resource-server issuer is aligned to the same value; `/actuator/health` returns **200**.
- Authenticated smoke PASS: temporary Keycloak user → token issued through the same-origin `/realms` route → token `iss` matched `https://23.254.236.11:443/realms/ecm` → authenticated `GET /api/v1/folders/roots` returned **200**; temp smoke users cleaned up.
- This solves the no-domain **login/API wiring** problem for internal staging. It does **not** create browser-trusted TLS; bare-IP/self-signed remains staging-only.

### Known limitation — public static-asset throughput (#20)
- The deployed build previously exposed a **source map** (`main.f9687944.js.map`, 13,362,799 bytes). **Fixed 2026-05-30:** `GENERATE_SOURCEMAP=false` set in `ecm-frontend/Dockerfile` (`969d97b`), ghcr image rebuilt and staging redeployed; `*.map` now **absent** in the container.
- Browser-sized gzip path recheck: `main.39bfbe72.js` transfers as ~819 KB compressed. Ten public-client samples completed in **1.50-1.74s** (avg **1.61s**, ~511 KB/s), while host-local gzip baseline was **0.18s**. Earlier severe slow-path/timeout observations are therefore **intermittent or route-specific**, not a stable frontend/nginx/code failure.
- Tracked in #20. Remaining work is ops-side: owner-provided hostname/TLS front door plus a second external-network/provider check if smoother public access is required.

## Scope statement

This receipt certifies **internal-test readiness only**. Before pilot/production, the following remain required:
- Owner-provided hostname or TLS front door, then a trusted TLS certificate (not `athena.local`; bare-IP TLS remains staging-only).
- Antivirus **enabled** (production posture; the staging fail-open path is also a noted global gap).
- Complete #20 owner-side validation: hostname/TLS plus at least one second external-network/provider check for static-asset throughput.
- Standard production cutover — secret rotation (S2), templated prod TLS/Keycloak (B1/B2), backup+restore smoke (B3), hardened full-stack smoke (B4) — per `docs/HANDOFF_HARDENING_20260526.md`.

## Related issues
- **#19** (ClamAV/AV unhealthy) — **closed** under Acceptance (b); authenticated smoke evidence above.
- **#20** (trusted TLS blocked on hostname + public JS throughput validation) — **partially mitigated**: source-map trim and no-domain same-origin auth wiring are done; trusted TLS still requires owner hostname/TLS front door.
