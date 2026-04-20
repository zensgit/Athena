# E2E Preview/Search Regression Gate — Bug Fix Development & Verification

## Date
2026-04-20

## Context

After the `CI_POST_PUSH_FIXES` and `E2E_RUNTIME_BUGFIX` reports left CI at 5/6 green, the only remaining failure was `Frontend E2E Core Gate`. That job runs two test groups in sequence:

1. **Core E2E suite** (primary tests listed in `ci.yml`) — 17/17 passing
2. **Preview/search regression gate** (`npm run e2e:preview-search:regression`) — 5 failing

This report documents the investigation and fix of all 5 remaining failures.

---

## Failure Inventory

| Test | File | Line | Root Cause |
|------|------|------|------------|
| "hides retry actions when failed previews are all unsupported" | `advanced-search-fallback-governance.spec.ts` | 41 | Mock wrong endpoint + strict-mode match on `Retryable 0` / `Unsupported 2` |
| "advanced search supports hide fallback results and retry backoff messaging" | `advanced-search-fallback-governance.spec.ts` | 123 | Mock wrong endpoint |
| "advanced search suppresses stale fallback by default for exact binary-like query and supports opt-in reveal" | `advanced-search-fallback-governance.spec.ts` | 223 | Mock wrong endpoint |
| "Phase 5 full-stack: admin pages render (Mail Automation + Preview Diagnostics)" | `phase5-fullstack-admin-smoke.spec.ts` | 5 | `getByRole('button', { name: 'Refresh' })` matched 3 buttons |
| "Advanced search preview status facet counts reflect full result set" | `search-preview-status.spec.ts` | 235 | ES facet aggregation lagged — got `Unsupported (11)` vs expected `Unsupported (12)` |

All 5 are **test bugs**, not product bugs. Confirmed by reading each test's source and the Playwright accessibility snapshots in `test-results/` artifact dirs.

---

## Root-Cause Analysis

### Bug 1 — Wrong mock endpoint (3 tests)

The 3 `advanced-search-fallback-governance` tests mock POST `/api/v1/search/faceted` via `page.route()`:

```typescript
await page.route('**/api/v1/search/faceted', async (route) => { … });
```

But `AdvancedSearchPage` actually calls POST `/api/v1/search/query` (seen in `nodeService.ts:1396`):

```typescript
const response = await api.post<SearchQueryEnvelopeResponse>('/search/query', payload);
```

The route interceptor never fired; real backend results came through, and the expected mocked unsupported docs were never rendered, so `getByText('Preview issues on current page: 2')` timed out.

### Bug 2 — Strict-mode match on compound text (:41 only)

Even after the endpoint fix, the first test still failed at:

```typescript
await expect(page.getByText('Retryable 0')).toBeVisible();
await expect(page.getByText('Unsupported 2')).toBeVisible();
```

The DOM renders these strings in two places:

1. Combined summary line: `"Preview issues on current page: 2 • Retryable 0 • Unsupported 2"`
2. Standalone badges in the preview-status panel: `"Unsupported 2 • Permanent 0"` etc.

Playwright's default `getByText` substring + case-insensitive match hits both, triggering strict-mode violation on `.toBeVisible()`.

### Bug 3 — Multiple "Refresh" buttons (phase5 admin smoke)

The Preview Diagnostics page has 3 buttons whose accessible name contains "Refresh":

| Button text | Match |
|-------------|-------|
| `Refresh` | ✓ |
| `Refresh history` | ✓ (substring) |
| `Auto Refresh Off` | ✓ (substring) |

`page.getByRole('button', { name: 'Refresh' })` defaults to substring match, so it matches all 3 → strict-mode failure on `.toBeVisible()`.

### Bug 4 — Eventually-consistent facet aggregation (search-preview-status:235)

Test uploads 12 binary docs, triggers `/preview` for each (all return `UNSUPPORTED`), re-indexes them, waits for search to find 12 results, then verifies the `Unsupported (12)` facet button appears.

`waitForSearchIndex` confirms **result count** = 12, but does **not** guarantee facet aggregation has caught up to include all 12 with the `UNSUPPORTED` preview status. The CI run saw `Unsupported (11)` — one doc's status still propagating.

The race is inherent to Elasticsearch: `findByName` refreshes before the facet aggregation builds.

---

## Fixes Applied

### Fix 1 — Correct endpoint URL (3 tests)

```diff
- await page.route('**/api/v1/search/faceted', async (route) => { … });
+ await page.route('**/api/v1/search/query', async (route) => { … });
```

Applied via `sed` to all 6 occurrences across the 3 tests. Response shape (`{ results: { content: [...] }, facets: {...} }`) already matches `SearchQueryEnvelopeResponse`, no body changes needed.

### Fix 2 — Add `.first()` to ambiguous text locators

```typescript
// "Retryable 0" and "Unsupported 2" each appear in both a combined summary
// line and a standalone badge, so use .first() to avoid strict-mode match.
await expect(page.getByText('Retryable 0').first()).toBeVisible({ timeout: 60_000 });
await expect(page.getByText('Unsupported 2').first()).toBeVisible({ timeout: 60_000 });
```

### Fix 3 — Exact match for `Refresh`

```diff
- await expect(page.getByRole('button', { name: 'Refresh' })).toBeVisible();
+ // Page has multiple "Refresh history" / "Auto Refresh" buttons too — use exact match
+ await expect(page.getByRole('button', { name: 'Refresh', exact: true }).first()).toBeVisible();
```

### Fix 4 — Retry loop on facet count propagation

```typescript
const unsupportedFacet = page.getByRole('button', { name: `Unsupported (${totalDocs})` }).first();
let seen = false;
for (let attempt = 0; attempt < 5; attempt += 1) {
  try {
    await expect(unsupportedFacet).toBeVisible({ timeout: 15_000 });
    seen = true;
    break;
  } catch {
    // Re-trigger search to pick up the latest facet counts
    await searchButton.click();
  }
}
expect(seen).toBe(true);
```

5 attempts × 15s = 75s worst-case wait, comfortably inside the 360s test timeout.

---

## Local Verification

### Fix 1+2 combined (advanced-search, 3 tests)

```bash
npx playwright test e2e/advanced-search-fallback-governance.spec.ts \
  --project=chromium --workers=1
```

```
✓ :41 hides retry actions when failed previews are all unsupported (2.2s)
✓ :125 advanced search supports hide fallback results and retry backoff messaging (5.9s)
✓ :225 advanced search suppresses stale fallback by default for exact binary-like query (7.9s)
3 passed (16.7s)
```

### Fix 3 (phase5 admin smoke, 1 test)

Passed as part of the Fix 1+2+3 combined run earlier (3 passed, 1 failed was the :41 text issue resolved separately).

### Fix 4 (facet count race, 1 test)

```bash
npx playwright test e2e/search-preview-status.spec.ts --grep "facet counts reflect" \
  --project=chromium --workers=1
```

```
✓ :235 Advanced search preview status facet counts reflect full result set (3.4s)
1 passed (4.1s)
```

---

## Files Modified

| File | Change |
|------|--------|
| `ecm-frontend/e2e/advanced-search-fallback-governance.spec.ts` | `search/faceted` → `search/query` ×6; `.first()` on `Retryable 0` and `Unsupported 2` |
| `ecm-frontend/e2e/phase5-fullstack-admin-smoke.spec.ts` | `{ name: 'Refresh', exact: true }` + `.first()` |
| `ecm-frontend/e2e/search-preview-status.spec.ts` | Retry loop around `Unsupported (N)` facet assertion |

No product code changes. Purely test hygiene.

---

## Commits

| Commit | Content |
|--------|---------|
| `e2913e2` | Initial 3-file fix: endpoint URL + exact Refresh + facet retry |
| `5c47ec3` | Follow-up: `.first()` on Retryable/Unsupported badges for :41 |

---

## Out of Scope

### `search-preview-status.spec.ts:192`

This test failed **locally only** — not in the CI failure list. Its error context showed search returned unrelated preview-related test data (older e2e residue). The test expects a unique filename card to appear; this is flaky against accumulated local test data.

Not touching in this commit to keep scope tight. Will surface again if CI hits it.

---

## Expected CI Outcome

After these 2 commits:

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ |
| Frontend Build & Test | ✅ |
| Phase C Security Verification | ✅ |
| Acceptance Smoke (3 admin pages) | ✅ |
| **Frontend E2E Core Gate** | **✅** (was 5/17 regression-gate fails; now 0) |
| Phase 5 Mocked Regression Gate | 🚫 cancelled on concurrency, or ✅ |

Target: **first 6/6 all-green CI run on this repo** for the combined P0A + P0B + P1 + P2 + P3 + P4 + P5 backlog.

---

## Session-Level Summary (since last handoff)

This was the 3rd phase of this session. Combined commits pushed:

1. **Phase 1 — CI configuration fixes** (9 commits): unused imports, Phase C port, health-wait timeouts, 503-tolerant health check, Liquibase migration JOIN
2. **Phase 2 — Runtime bug fixes** (2 commits): `/checkin` 500 edge case, `pdf-preview` dialog-scoped click
3. **Phase 3 — Test hygiene fixes** (2 commits, this report): regression-gate endpoint + strict-mode + race

Total session: **13 commits** pushed to `origin/main`. CI trajectory: **0/6 → 5/6 → targeting 6/6**.
