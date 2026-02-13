# PHASE1 P61 - Version Compare (Any Two Versions) Verification

Date: 2026-02-09

## Preconditions

- Docker stack running:
  - UI: `http://localhost:5500`
  - API: `http://localhost:7700`

If you changed frontend/backend code, rebuild:

```bash
bash scripts/restart-ecm.sh
```

## Automated Verification (Playwright)

Run targeted E2E tests:

```bash
cd ecm-frontend
npx playwright test e2e/version-compare-any-two.spec.ts e2e/version-share-download.spec.ts
```

### Expected

- `Version compare: can select any two versions` passes:
  - creates 3 versions of a text document
  - opens Version History -> Compare dialog
  - selects `From=v1` and `To=v3`
  - asserts unified diff contains `- v1Marker` and `+ v3Marker`

### Result

- `3 passed` (includes regression coverage from `version-share-download.spec.ts`).

