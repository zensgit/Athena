# Phase1 P55 Design: Search Preview Failure Governance

Date: 2026-02-08
Owner: Codex
Scope: `ecm-frontend` (search results, advanced search, shared preview failure utils, auth test lint gate)

## Background

The Search and Advanced Search pages both expose failed preview recovery controls. Before this phase, their failure bucketing and action visibility behavior were partially duplicated and inconsistent. In addition, CI was blocked by auth test lint violations (`testing-library/no-wait-for-multiple-assertions`).

## Goals

1. Use one shared preview-failure summary model across Search and Advanced Search.
2. Make retry controls visible only when there are retryable failures.
3. Keep unsupported failures visible in stats but do not present misleading retry actions.
4. Unblock CI lint by fixing test assertion style without changing auth runtime logic.

## Non-goals

1. No backend API changes.
2. No new retry endpoint behavior.
3. No auth flow behavior changes.

## Design Decisions

1. Introduce shared failure summary helpers in `previewStatusUtils.ts`.
2. Normalize reason keys with a single utility, and use a separate label formatter for UI text.
3. Compute per-page failed-preview summary at page level to avoid duplicated bucket code.
4. Keep unsupported preview failures counted and surfaced, but hide retry action controls when retryable count is zero.
5. Replace multi-assertion `waitFor` blocks in tests with single-assertion waits to satisfy lint rule.

## Implementation

### Shared utility layer

File: `ecm-frontend/src/utils/previewStatusUtils.ts`

1. Added shared types:
   - `PreviewFailureLike`
   - `PreviewFailureSummary`
2. Added helper functions:
   - `normalizePreviewFailureReason`
   - `formatPreviewFailureReasonLabel`
   - `summarizeFailedPreviews`
3. Reused existing unsupported detection (`isUnsupportedPreviewFailure`) in summary building.

### Search results page

File: `ecm-frontend/src/pages/SearchResults.tsx`

1. Replaced local reason-bucket logic with `summarizeFailedPreviews(...)`.
2. Added page-level failed-preview summary text:
   - total
   - retryable
   - unsupported
3. Retry buttons now render only when `retryableFailed > 0`.
4. Added explicit unsupported-only informational text when all failures are unsupported.
5. Standardized reason matching and display with normalized reason helpers.
6. Wrapped `displayNodes` with `useMemo` to keep hook dependencies stable.

### Advanced search page

File: `ecm-frontend/src/pages/AdvancedSearchPage.tsx`

1. Replaced local reason-bucket logic with shared summary helpers.
2. Render failed-preview panel based on `totalFailed > 0`.
3. Render retry actions only when `retryableFailed > 0`.
4. Render unsupported-only informational text when appropriate.
5. Standardized retry reason label formatting and reason matching behavior.

### CI lint gate cleanup (auth tests)

Files:
- `ecm-frontend/src/components/auth/Login.test.tsx`
- `ecm-frontend/src/components/auth/PrivateRoute.test.tsx`

1. Converted multi-assertion `waitFor` blocks into single-assertion waits.
2. Preserved existing test intent and assertions.

## Public Interface Impact

1. No REST API signature change.
2. No Redux state shape change.
3. UI behavior change:
   - retry controls no longer shown when current page has only unsupported failed previews.

## Risks and Mitigations

1. Risk: retry controls unexpectedly hidden.
   - Mitigation: explicit summary counters and dedicated Playwright coverage for unsupported-only scenarios.
2. Risk: inconsistent reason grouping caused by whitespace variants.
   - Mitigation: centralized reason normalization and unit tests.

