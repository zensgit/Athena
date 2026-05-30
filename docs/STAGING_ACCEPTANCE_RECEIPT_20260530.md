# Staging Acceptance Receipt — 2026-05-30

**Host:** `23.254.236.11` · **Commit:** `59260e4b` · **App images:** `ghcr.io/zensgit/athena-*:latest`
**Evidence captured:** 2026-05-30T05:53Z (direct SSH on the staging host)

## Verdict

**Athena staging is usable for internal testing. It is NOT pilot/production evidence.**
The runtime is healthy and the core document path (auth → upload → download) works end-to-end,
but it runs a staging-only security/TLS posture. Public static-asset throughput had an earlier
slow-path signal; the current browser-sized gzip path is acceptable from this client but still
awaits second-network/provider validation if smoother public access is required.

## Evidence

### Version & images
- Serving repo: `/tmp/Athena.new` @ `59260e4b`.
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
- **#20** (trusted TLS blocked on hostname + public JS throughput validation) — **open**, owner-provider side; source-map trim is done.
