# Staging Acceptance Receipt — 2026-05-30

**Host:** `23.254.236.11` · **Commit:** `59260e4b` · **App images:** `ghcr.io/zensgit/athena-*:latest`
**Evidence captured:** 2026-05-30T05:53Z (direct SSH on the staging host)

## Verdict

**Athena staging is usable for internal testing. It is NOT pilot/production evidence.**
The runtime is healthy and the core document path (auth → upload → download) works end-to-end,
but it runs a staging-only security/TLS posture and has a known public static-asset throughput limit.

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
- Acceptable for staging. **Pilot/customer-facing requires a real hostname + trusted cert** (e.g. Let's Encrypt).

### Known limitation — public static-asset throughput (#20)
- `main.js` (~817 KB) serves in ~0.18s from the host locally but **times out from the public client** — the bottleneck is the public network path / VPS egress, **not code**.
- The deployed build also exposed a **source map** (`main.f9687944.js.map`, 13,362,799 bytes). **Update 2026-05-30: fixed** — `GENERATE_SOURCEMAP=false` set in `ecm-frontend/Dockerfile` (`969d97b`), ghcr image rebuilt and staging redeployed; `*.map` now **absent** in the container. The network half (public `main.js` throughput) remains host/provider-side.
- Tracked in #20 (host/provider-side bandwidth/routing + source-map trim).

## Scope statement

This receipt certifies **internal-test readiness only**. Before pilot/production, the following remain required:
- Trusted TLS certificate on a real hostname (not `athena.local`).
- Antivirus **enabled** (production posture; the staging fail-open path is also a noted global gap).
- Resolve public static-asset throughput (#20).
- Standard production cutover — secret rotation (S2), templated prod TLS/Keycloak (B1/B2), backup+restore smoke (B3), hardened full-stack smoke (B4) — per `docs/HANDOFF_HARDENING_20260526.md`.

## Related issues
- **#19** (ClamAV/AV unhealthy) — **closed** under Acceptance (b); authenticated smoke evidence above.
- **#20** (self-signed TLS + slow public JS) — **open**, host/provider-side + source-map trim.
