# Verification: Folder-Scoped Search Default (2026-02-10)

## Environment
- UI: `http://localhost:5500`
- API: `http://localhost:7700`

## Steps

### 1) Rebuild frontend container
Command:
```bash
docker compose up -d --build ecm-frontend
```
Result: ✅ container rebuilt and started

### 2) Playwright E2E: folder-scoped search
Command:
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/folder-scoped-search.spec.ts
```
Result: ✅ `1 passed`

### 3) Playwright E2E: UI smoke (single test)
Command:
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/ui-smoke.spec.ts -g "UI smoke: browse + upload + search + copy/move + facets + delete + rules"
```
Result: ✅ `1 passed`

## Notes
This verifies that Advanced Search opened from `/browse/:folderId` pre-fills scope reliably (even if `currentNode` is not yet loaded) and avoids global-search performance flakes.

