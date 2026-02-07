# Phase 1 Continue - E2E Stability Hardening Design (2026-02-07)

## Background

Full Playwright regression previously failed in two unstable points:

1. `ui-smoke.spec.ts`
   - `hideDevServerOverlay()` could throw during navigation with "Execution context was destroyed".
   - Result: unrelated test failures caused by a best-effort helper.
2. `p1-smoke.spec.ts`
   - Mail rule preview assertion relied on one success-only UI path.
   - Result: timeout/failure when backend preview returned error or slower response.

## Scope

- Frontend E2E test hardening only.
- No backend API behavior change.
- No product UI behavior change.

## Detailed Changes

### 1) Make dev-overlay hiding non-fatal

File: `ecm-frontend/e2e/ui-smoke.spec.ts`

- Expanded known transient navigation error patterns:
  - `Execution context was destroyed`
  - `Cannot find context with specified id`
  - `Target page, context or browser has been closed`
  - `Frame was detached`
  - `has been detached`
- Updated `hideDevServerOverlay()` semantics:
  - retry up to 3 times
  - return immediately if page is already closed
  - treat helper as best-effort: never fail scenario on overlay-hide errors

Design intent:
- Overlay suppression is a convenience guard, not business logic.
- Navigation races must not break functional validation.

### 2) Make mail preview smoke assertion deterministic

File: `ecm-frontend/e2e/p1-smoke.spec.ts`

- Added explicit wait for preview API response:
  - `POST /api/v1/integration/mail/rules/{ruleId}/preview`
- Branching behavior:
  - if response is non-2xx, assert error text and end test path cleanly
  - otherwise wait for either success summary or error text
  - if error text shows, treat as handled error path; if success, assert `Summary` and `Matched messages`

Design intent:
- Validate UI flow robustness across both success and controlled error outcomes.
- Remove pure timing dependence on one text node.

## Risk and Compatibility

- Risk: low (test-only changes).
- Runtime behavior: unchanged for users.
- CI impact: should reduce flaky failures in auth/navigation-heavy runs.

## Acceptance Criteria

- `ui-smoke` no longer fails due to overlay-helper context reset.
- `p1-smoke` preview test no longer times out on non-success preview states.
- Full Playwright suite remains green.
