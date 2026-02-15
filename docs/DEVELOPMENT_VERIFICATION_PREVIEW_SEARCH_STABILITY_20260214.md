# Development & Verification: Preview/Search Stability (2026-02-14)

## 1. Development Changes

### 1.1 Runtime startup fix
- File: `ecm-core/entrypoint.sh`
- Change summary:
  - Added `JAVA_BIN` resolution with absolute default `/opt/java/openjdk/bin/java`.
  - Added fallback to `command -v java`.
  - Launch now uses resolved absolute path under `su` execution.

### 1.2 Docker compose configurability
- File: `docker-compose.yml`
- Change summary:
  - `JODCONVERTER_LOCAL_ENABLED` changed from hardcoded `true` to `${JODCONVERTER_LOCAL_ENABLED:-true}`.
  - Enables lightweight runtime startup by explicit override when LibreOffice is intentionally skipped.

### 1.3 E2E regression alignment
- File: `ecm-frontend/e2e/search-fallback-criteria.spec.ts`
- Change summary:
  - Added governed fallback handling (`Hide previous results` when visible).
  - Preserved no-stale-result assertions.
  - Kept empty-state checks and indexing warning removal checks.

### 1.4 Regression script entrypoint
- File: `ecm-frontend/package.json`
- Change summary:
  - Added `e2e:preview-search:regression` script to run the full preview/search governance pack in one command.

### 1.5 Dual restart modes
- File: `scripts/restart-ecm.sh`
- Change summary:
  - Added `--mode fast|full` (`--fast`, `--full`) and help output.
  - `fast` mode builds `ecm-core` with `SKIP_LIBREOFFICE=true` and runs with `JODCONVERTER_LOCAL_ENABLED=false`.
  - `full` mode preserves local conversion behavior.

### 1.6 CI integration
- File: `.github/workflows/ci.yml`
- Change summary:
  - Added `Run preview/search regression gate` step in `frontend_e2e_core`.
  - Reused `npm run e2e:preview-search:regression`.
  - Increased `frontend_e2e_core` timeout to 120 minutes.

## 2. Execution Log (Local)

### 2.1 Build and startup sequence
1. Built `ecm-core/ecm-frontend` images with `SKIP_LIBREOFFICE=true` for faster local loop.
2. Started dependency services (postgres/elasticsearch/redis/rabbitmq/minio/keycloak/collabora/clamav).
3. Started app services.
4. Identified core restart root causes via container logs.
5. Applied fixes and restarted core with:
   - `JODCONVERTER_LOCAL_ENABLED=false docker compose ... up -d --no-deps ecm-core`

### 2.2 Health checks
- Frontend: `http://127.0.0.1:5500` returned `200`.
- Backend health: `http://localhost:7700/actuator/health` returned `{"status":"UP"}`.
- `docker compose ps` showed `athena-ecm-core-1` as `healthy`.

## 3. Verification Results

### 3.1 Targeted regression runs
1. `npx playwright test e2e/phase5-fullstack-admin-smoke.spec.ts e2e/search-preview-status.spec.ts --reporter=line`
   - Result: `8 passed`.

2. `npx playwright test e2e/search-fallback-criteria.spec.ts e2e/advanced-search-fallback-governance.spec.ts --reporter=line`
   - First run found stale-fallback assertion mismatch.
   - After test fix: stable pass.

3. Final combined verification:
   - `npx playwright test e2e/phase5-fullstack-admin-smoke.spec.ts e2e/search-preview-status.spec.ts e2e/advanced-search-fallback-governance.spec.ts e2e/search-fallback-criteria.spec.ts --reporter=line`
   - Result: `12 passed (40.1s)`.

4. Scripted run verification:
   - `npm run e2e:preview-search:regression -- --reporter=line`
   - Result: `12 passed (39.7s)`.

5. Dual-mode script validation:
   - `bash scripts/restart-ecm.sh --help`
   - `bash -n scripts/restart-ecm.sh`
   - Result: usage output correct, shell syntax valid.

## 4. Acceptance Status
- Core container startup stability: PASS.
- Frontend availability: PASS.
- Search/preview fallback governance regression pack: PASS.
- Phase-5 admin smoke path: PASS.

## 5. Notes
- Local fast profile (`SKIP_LIBREOFFICE=true` + `JODCONVERTER_LOCAL_ENABLED=false`) is for developer loop acceleration.
- Default compose behavior remains conversion-enabled unless explicitly overridden.
