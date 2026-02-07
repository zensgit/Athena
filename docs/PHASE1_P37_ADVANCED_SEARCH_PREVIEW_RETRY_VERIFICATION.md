# Phase 1 P37: Advanced Search Preview Retry Verification

## Date
2026-02-07

## Code Changes Verified

- `ecm-core/src/main/java/com/ecm/core/search/FacetedSearchService.java`
- `ecm-core/src/test/java/com/ecm/core/search/SearchAclFilteringTest.java`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- `ecm-frontend/e2e/search-preview-status.spec.ts`

## Verification Runs

1. Backend targeted test

Command:

```bash
cd ecm-core
mvn -Dtest=SearchAclFilteringTest test
```

Result:

- `BUILD SUCCESS`
- `Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`

2. Frontend production build

Command:

```bash
cd ecm-frontend
npm run build
```

Result:

- `Compiled successfully.`

3. Playwright targeted new scenario

Command:

```bash
cd ecm-frontend
npx playwright test e2e/search-preview-status.spec.ts --grep "Advanced search supports preview retry actions for failed previews"
```

Result:

- `1 passed`

4. Playwright full spec (`search-preview-status`)

Command:

```bash
cd ecm-frontend
npx playwright test e2e/search-preview-status.spec.ts
```

Result:

- `3 passed`
- Includes:
  - Search preview status filters visible/selectable
  - Preview failure hint and retry in search results
  - Advanced search retry parity flow

## Manual Behavior Confirmation

- Advanced Search cards display failed preview state.
- Failed preview cards expose retry icon action.
- Batch retry and reason-group retry controls are visible when failed items exist.
- Queue attempt metadata appears after retry.

## Notes

- Earlier failures were caused by Keycloak UI redirect timing in e2e login.
- Final verified spec uses token-seeded session bypass inside `search-preview-status.spec.ts`, making this flow deterministic for CI/local validation.

