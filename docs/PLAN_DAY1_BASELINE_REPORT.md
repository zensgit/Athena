# Day 1 Baseline Verification Report

Date: 2025-12-22 (local)

## Environment
- API base: `http://localhost:7700/api/v1`
- Keycloak: `http://localhost:8180/realms/ecm`
- Frontend: `http://localhost:5500`

## Health Checks
- Keycloak OIDC config: `200`
- System status: `200`
  - Database: reachable
  - Redis: reachable
  - RabbitMQ: reachable
  - Search: enabled (`ecm_documents`, docCount: 950)
  - ML: healthy (modelLoaded=false)
  - WOPI/Collabora: reachable (capabilities OK)
  - Antivirus: enabled but unavailable (ClamAV daemon not responding)

## API Smoke
- Roots: `200` (selected root `uploads` id `d47a22e5-4aae-4bae-a9b1-8b045ba8f2a0`)
- Create folder: `201`
  - Folder id: `25581018-3954-4a75-a954-678201eb5840`
  - Path: `/uploads/api-smoke-20251222_232843`
- Upload: `200`
  - File: `day1-baseline-20251222_232843-file.txt`
  - Document id: `a930aa5e-7a26-4515-bd9b-6ca55798f22c`
- Preview: `200`
  - Supported: true
  - PageCount: 1
- Search: `200`
  - Query: `day1-baseline-20251222_232843-file.txt`
  - Match id: `a930aa5e-7a26-4515-bd9b-6ca55798f22c`

## Artifacts
- `tmp/day1-baseline-20251222_232843-summary.txt`
- `tmp/day1-baseline-20251222_232843-*.json`
- `tmp/day1-baseline-20251222_232843-search-file.json`
- `tmp/day1-baseline-20251222_232843-search-file-summary.txt`

## Notes
- ClamAV is configured but currently unavailable; if AV is required, start the daemon and recheck `/api/v1/system/status`.
