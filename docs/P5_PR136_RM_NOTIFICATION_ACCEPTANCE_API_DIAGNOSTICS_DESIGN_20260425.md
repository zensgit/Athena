# P5 PR-136 RM Notification Acceptance API Diagnostics Design

## Goal

Improve CI failure diagnostics for the RM notification acceptance Playwright flows without changing product behavior or test coverage.

## Problem

`rm-report-preset-schedule.spec.ts` used many bare API assertions:

```typescript
expect(response.ok()).toBeTruthy();
```

When CI fails, that pattern hides the failing request URL, status, and response body. This is especially costly now that the RM notification acceptance gate runs in CI and exercises backend APIs before browser assertions.

## Change

The spec now uses a shared `expectApiOk(response, context)` helper for APIRequestContext responses.

On failure, the helper reports:

- caller-provided operation context
- HTTP status and status text
- response URL
- up to 2000 characters of response body

## Coverage

The helper is applied to:

- folder and document setup APIs
- report preset creation
- schedule status and execution ledger reads
- notification inbox reads
- preference writes
- schedule save and admin trigger calls in notification acceptance flows

## Boundaries

- no runtime endpoint changed
- no Playwright acceptance case changed
- no assertion was weakened
- browser response wait predicates are unchanged
