# Verification: Skip Queueing Unsupported Previews (Unless Forced)

Date: 2026-02-10

## Prereqs
- Local docker stack running (Keycloak, API, frontend, etc.).
- This repo is checked out at `Athena/`.

## Backend (Unit Test)
Run targeted core tests in an isolated Maven container:

```bash
docker run --rm -v "$(pwd)":/workspace -w /workspace/ecm-core maven:3-eclipse-temurin-17 \
  mvn test -Dtest=PreviewQueueServiceTest
```

Expected:
- Build success.
- Test includes coverage for `UNSUPPORTED + force=false` enqueue returning `queued=false`.

## Frontend (Prebuilt Docker UI Refresh)
If your `docker-compose.override.yml` uses `Dockerfile.prebuilt`, refresh the static build and container:

```bash
bash scripts/rebuild-frontend-prebuilt.sh
```

Expected:
- `react-scripts build` completes successfully.
- `ecm-frontend` container is rebuilt/restarted and available at `http://localhost:5500`.

## E2E (Playwright)
Run full UI regression suite against the docker frontend and API:

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test
```

Expected:
- All tests pass (some may be skipped by design).
- `e2e/search-preview-status.spec.ts` continues to confirm:
  - Unsupported previews show neutral status.
  - Retry actions are hidden for unsupported preview failures.

## Manual Spot Checks (Optional)
1. Open upload dialog and upload a file that is expected to be unsupported for preview (for example a `.bin` / `application/octet-stream`).
2. Verify in the "Uploaded items" list:
  - Chip shows "Preview unsupported".
  - Only "Force rebuild" is available (no "Queue preview" button).

