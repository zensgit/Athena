# Verification: Preview Queue Search Index Sync (2026-02-10)

## Environment
- Docker compose stack
- API: `http://localhost:7700`
- UI: `http://localhost:5500`

## Steps

### 1) Backend compile (containerized Maven)
Command:
```bash
docker run --rm -v "$(pwd)":/workspace -w /workspace/ecm-core maven:3-eclipse-temurin-17 mvn -q -DskipTests package
```
Result: ✅ exit code `0`

### 2) Unit test (targeted)
Command:
```bash
docker run --rm -v "$(pwd)":/workspace -w /workspace/ecm-core maven:3-eclipse-temurin-17 mvn -q -Dtest=PreviewQueueServiceTest test
```
Result: ✅ exit code `0`

### 3) Rebuild/restart API container
Command:
```bash
docker compose up -d --build ecm-core
```
Result: ✅ container rebuilt and started

Health check:
```bash
docker inspect -f '{{.State.Health.Status}}' athena-ecm-core-1
```
Result: ✅ `healthy`

### 4) Playwright E2E (targeted)
Command:
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/search-preview-status.spec.ts
```
Result: ✅ `4 passed`

## Notes
This change verifies that preview queue status transitions (processing/retrying/failed) are indexed via `SearchIndexService.updateDocument(...)` so that the search UI preview-status filters and totals remain consistent.

