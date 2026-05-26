# P0a-2 — Prod Security Exposure Hardening (A4/A5/A6) — Verification

Date: 2026-05-26
Brief: `docs/HARDENING_P0A2_PROD_SECURITY_BRIEF_20260526.md` (gate-approved D1-D4).
Parent matrix: section 8 of `docs/PRODUCTION_READINESS_ASSESSMENT_20260525.md`.
Prerequisite: P0a-1 closed at `7b7b245`.

## Changes shipped

- **`SecurityConfig.java`** is now default-compatible and config-driven:
  - `ecm.security.exposure.actuator-permit-all` defaults to `true` (current dev/docker/test behavior).
  - `ecm.security.exposure.swagger-permit-all` defaults to `true` (current dev/docker/test behavior).
  - `ecm.security.cors.allowed-origins` is now read from config, defaulting to `*` for base compatibility.
- **`application-prod.yml`** now hardens prod exposure:
  - public actuator is limited to `/actuator/health`; non-health actuator requires `ROLE_ADMIN`.
  - Swagger/OpenAPI publication is disabled with `springdoc.api-docs.enabled=false` and `springdoc.swagger-ui.enabled=false`.
  - CORS requires `ECM_SECURITY_CORS_ALLOWED_ORIGINS` with no fallback wildcard.
  - management web exposure is limited to `health`; health details use `when_authorized`.
- **Tests**:
  - `SecurityConfigProdExposureTest` locks prod-like actuator, Swagger/OpenAPI, CORS, and ordinary API behavior.
  - `ProdProfileHardeningTest` now also asserts the P0a-2 prod profile content.
  - `SecurityConfigProtocolSecurityTest` remains green, proving WOPI/transfer receiver protocol seams stayed intact.

## Local verification

```bash
scripts/backend-preflight.sh -Dtest=SecurityConfigProdExposureTest,ProdProfileHardeningTest,SecurityConfigProtocolSecurityTest test
# Tests run: 21, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS

scripts/backend-preflight.sh
# test-compile — BUILD SUCCESS

git diff --check -- . ':!.env'
# clean
```

## Scope / non-goals

- No `.env`, `ecm-frontend/.env`, secret rotation, or credential custody (S1/S2).
- No `docker-compose.prod.yml`, port publishing, image pinning, ES/MinIO/Grafana runtime security, or ml-service non-root (P0a-3).
- No Keycloak prod realm, TLS, backup/restore, or hardened full-stack smoke (P0b/B4).
- No transfer receiver or WOPI protocol seam changes.
- No frontend or business-controller security policy changes beyond global management/docs/CORS exposure controls.

## CI Follow-Up

```text
Run id:        26430746307
Head SHA:      fca974f
Conclusion:    success (7/7 — gh run view authority)
URL:           https://github.com/zensgit/Athena/actions/runs/26430746307

Jobs:
  - Backend Verify
  - Frontend Build & Test
  - Phase C Security Verification
  - Acceptance Smoke (3 admin pages)
  - Property Encryption Closeout Gate
  - Phase 5 Mocked Regression Gate
  - Frontend E2E Core Gate
```
