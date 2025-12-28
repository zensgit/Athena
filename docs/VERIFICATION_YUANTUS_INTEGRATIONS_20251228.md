# Yuantus Integration Verification (2025-12-28)

## Summary
- Athena: OK (service-account token via client credentials)
- Dedup Vision: OK
- CAD ML: OK (direct `/api/v1/health` reachable)

## Environment
- Yuantus API: http://127.0.0.1:7910
- Keycloak: http://localhost:8180/realms/ecm
- Athena: http://host.docker.internal:7700/api/v1
- DedupCAD Vision: http://host.docker.internal:8100
- CAD ML: http://cad-ml-api:8000

## Actions
- Started dedupcad-vision services with port overrides:
  - vision-api: 8100 -> 8000
  - redis: 16380 -> 6379
  - minio: 19110 -> 9000, 19111 -> 9001
- Restarted Yuantus `api` and `worker` to load env overrides.
- Generated Yuantus and Athena tokens and called `/api/v1/integrations/health`.

## Results
- `cad_ml.ok=true` after routing to `cad-ml-api:8000` (health payload returned).
- `dedup_vision.ok=true` and health reports `status=healthy`.
- `athena.ok=true` after enabling client-credentials token flow.

## Notes / Next
- cad-ml platform exposes `/health` (HTTP 200) but not `/api/v1/health`.
- Options:
  1. Update Yuantus CadMLClient health path or make it configurable.
  2. Add `/api/v1/health` route (or proxy) in cad-ml platform.

## Follow-up (Direct Network Fix)
- Added `/api/v1/health` route in cad-ml platform (alias to `/health`).
- Connected Yuantus `api`/`worker` containers to `cad-ml-network`.
- Updated Yuantus CAD ML base URL to `http://cad-ml-api:8000`.
- Added `YUANTUS_ATHENA_SERVICE_TOKEN` env var for Athena health checks (token sourced from Keycloak; not stored in repo).
- Added Keycloak service account client (`yuantus-service`) and client-credentials flow to mint Athena tokens.
- Athena client secret is provided via Docker secret at `/run/secrets/athena_client_secret` (host file `~/.config/yuantus/athena_client_secret`).

### Updated Results
- `cad_ml.ok=true` with full health payload.
- `dedup_vision.ok=true`.
- `athena.ok=true` with service-account token (client credentials).

## Re-Verification (2025-12-28, PM)
### Checks
- Authenticated `POST /api/v1/auth/login` to obtain Yuantus token (tenant-1/org-1).
- `GET /api/v1/integrations/health` with Bearer token + tenant/org headers.
- `scripts/verify_integrations_athena.sh` with `VERIFY_CLIENT_CREDENTIALS=1`.

### Results
- `integrations/health`: `ok=true` with `athena/cad_ml/dedup_vision` all OK.
- Script output: `ALL CHECKS PASSED` (client-credentials path).

## Re-Verification After Restart (2025-12-28, PM)
### Restarted Containers
- `yuantus-api-1`, `yuantus-worker-1`
- `cad-ml-api`, `dedupcad-vision-api`
- `athena-ecm-core-1`

### Checks
- Authenticated `POST /api/v1/auth/login` to obtain Yuantus token (tenant-1/org-1).
- `GET /api/v1/integrations/health` with Bearer token + tenant/org headers.
- `scripts/verify_integrations_athena.sh` with `VERIFY_CLIENT_CREDENTIALS=1`.

### Results
- `integrations/health`: `ok=true` with `athena/cad_ml/dedup_vision` all OK.
- Script output: `ALL CHECKS PASSED` (client-credentials path).

## Athena API Smoke (2025-12-28, PM)
### Steps
- Refreshed Keycloak token: `bash scripts/get-token.sh admin admin`.
- Ran `ECM_API=http://localhost:7700 ECM_TOKEN_FILE=tmp/admin.access_token ./scripts/smoke.sh`.

### Result
- `scripts/smoke.sh` completed successfully (health, upload, search, WOPI edit, rules, scheduled rules, tags, categories, workflow, trash).
