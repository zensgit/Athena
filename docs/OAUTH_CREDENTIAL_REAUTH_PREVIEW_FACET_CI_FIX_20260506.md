# OAuth Reauth Follow-up CI Fix - Preview Facet E2E Stability

Date: 2026-05-06

## Context

Commit `7c17e14` shipped the OAuth credential admin reauthorization control and passed the backend, frontend, Phase C, property-encryption, acceptance, and Phase 5 mocked CI gates.

The only failed gate was the preview/search regression slice inside Frontend E2E Core:

- Spec: `ecm-frontend/e2e/search-preview-status.spec.ts`
- Test: `Advanced search preview status facet counts reflect full result set`
- Failure: expected `Unsupported (12)`, but the browser still saw a split such as `Unsupported (10)` plus `Processing (2)`.

This is unrelated to the OAuth admin code path. The failure is an E2E readiness race: the test waited for the 12 uploaded documents to appear in search results, but it did not wait for the advanced-search facet aggregation to observe the terminal preview status for all 12 documents.

## Design

The fix keeps the product assertion intact. It does not relax the UI expectation.

Changes:

- The facet-count test now indexes each uploaded binary document with `refresh=true`.
- After all uploads, it runs a query-level reindex with `refresh=true` for the unique test token.
- Before entering the browser, it polls `/api/v1/search/query` with `include: ['results', 'facets']` and `facets: ['previewStatus']`.
- The browser assertion only runs after the API-level envelope reports both:
  - `totalElements >= 12`
  - `previewStatus.UNSUPPORTED >= 12`

This pins the test to the same backend envelope used by `AdvancedSearchPage`, so a later UI failure would indicate a real rendering or state propagation regression rather than Elasticsearch refresh lag.

## Verification

Repository hygiene:

```bash
git diff --check
```

Result: passed.

Frontend project lint:

```bash
cd ecm-frontend
npm run lint
```

Result: passed.

Playwright spec discovery:

```bash
cd ecm-frontend
npx playwright test e2e/search-preview-status.spec.ts \
  --grep "Advanced search preview status facet counts reflect full result set" \
  --project=chromium \
  --list
```

Result: the targeted test was discovered:

```text
[chromium] › search-preview-status.spec.ts › Advanced search preview status facet counts reflect full result set
Total: 1 test in 1 file
```

Local full E2E execution was not attempted because this checkout did not have the API/UI stack running:

```text
127.0.0.1:7700 unavailable
127.0.0.1:5500 unavailable
127.0.0.1:3000 unavailable
```

Remote CI validation:

```text
Run: 25438220258
Commit: 2db5166
Result: success
Frontend E2E Core Gate: success
Run preview/search regression gate: success
```

This confirms the previous run `25436297704` failure was closed by the facet-readiness fix.

## Files Changed

- `ecm-frontend/e2e/search-preview-status.spec.ts`
- `docs/OAUTH_CREDENTIAL_REAUTH_PREVIEW_FACET_CI_FIX_20260506.md`
