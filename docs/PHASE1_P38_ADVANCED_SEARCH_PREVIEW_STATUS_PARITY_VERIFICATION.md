# Phase 1 P38: Advanced Search Preview Status Parity Verification

## Date
2026-02-07

## Files Verified

- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- `ecm-frontend/e2e/search-preview-status.spec.ts`

## Verification Commands

1. Frontend build

```bash
cd ecm-frontend
npm run build
```

Result:

- `Compiled successfully`

2. Rebuild frontend runtime container

```bash
cd /Users/huazhou/Downloads/Github/Athena
docker compose up -d --build ecm-frontend
```

Result:

- `athena-ecm-frontend-1 Started`

3. Playwright regression for preview status spec

```bash
cd ecm-frontend
npx playwright test e2e/search-preview-status.spec.ts
```

Result:

- `3 passed`
  - `Search preview status filters are visible and selectable`
  - `Preview failure shows info hint in search results`
  - `Advanced search supports preview retry actions for failed previews`

## Functional Checks Covered

- Advanced Search shows preview status panel with status count chips.
- Clicking `Failed (n)` applies current-page preview status filter.
- Filter scope hint is visible.
- Retry controls (`Retry failed previews`, reason-level retry, per-card retry icon) remain functional.
- Queue attempt hint remains visible after retry action.

## Notes

- Historical flakiness from Keycloak UI redirect during e2e login was addressed inside this spec by token-seeded session setup.
- No backend API schema change was required for P38.

