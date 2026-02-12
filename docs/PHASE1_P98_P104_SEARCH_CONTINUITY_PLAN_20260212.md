# Phase 1 Next Plan (P98-P104): Search Continuity + Governance Hardening

Date: 2026-02-12

## Objective

Continue the search continuity track after P97 by prioritizing:
- cross-entry criteria consistency
- preview-failure governance parity
- environment-stable regression gates

## Execution Status (updated 2026-02-12)

- P98: done
  - URL prefill parser shared between `MainLayout` and `SearchDialog`
  - Advanced dialog now supports URL-state fallback when Redux prefill is absent
- P99: done
  - Saved-search parser resilience hardened for malformed fields
- P100: done
  - Advanced Search URL preview status alias normalization aligned with shared parser
  - Unsupported-only failed-preview governance behavior covered by E2E
- P101: done
  - Mail reporting empty-state now includes selected range/account/rule context
- P103: done
  - Backend preview status alias canonicalization + focused unit tests
- P102: done
  - Added E2E target guardrail script `scripts/check-e2e-target.sh`
- P104: done
  - Continuity regression gate passed (`14 passed`)
- Remaining:
  - none

## Priority and Scope

## P98 (Frontend) — SearchDialog URL-state fallback prefill

Problem:
- Under auth redirect/reload edges, `searchPrefill` and Redux state can be empty when opening global advanced search.

Implementation:
- Add URL-state fallback parsing in `SearchDialog` (for `/search` URL params).
- Reuse/centralize parsing with `MainLayout` prefill logic to avoid drift.

Acceptance:
- Global advanced dialog shows current `/search` URL criteria even when Redux criteria is empty.

Verification:
- Playwright: open `/search?...`, clear runtime state, reopen dialog, assert prefill.

## P99 (Frontend) — Saved-search parser resilience for imported payloads

Problem:
- Imported payloads may contain partial/unknown fields; parser should degrade gracefully.

Implementation:
- Add strict field allow-list normalization output.
- Ignore unknown preview status tokens and invalid date values without breaking load flow.

Acceptance:
- Invalid/extra fields do not block saved-search load; valid fields still prefill.

Verification:
- Unit tests for malformed/partial payloads.
- Playwright one mixed-payload scenario.

## P100 (Frontend) — Preview governance parity on advanced page

Problem:
- Users need consistent interpretation between `FAILED` and `UNSUPPORTED` across summary chips, actions, and row labels.

Implementation:
- Audit `AdvancedSearchPage` failed-summary/action enablement and ensure unsupported failures are excluded from retry actions.
- Align chip labels/count logic with `SearchResults` utility behavior.

Acceptance:
- Unsupported failures are visible but excluded from retry counts/actions consistently.

Verification:
- Playwright: preview-status filter + retry button visibility rules.

## P101 (Frontend + API contract check) — Mail reporting date range clarity

Problem:
- Reporting panels can look stale without explicit range semantics.

Implementation:
- Confirm and document explicit range source (`days` selector -> backend params).
- Add UI empty-state clarifier when no data in selected window.

Acceptance:
- Selected range and resulting data window are explicit and reproducible.

Verification:
- Playwright: switch ranges and assert summary window text.

## P102 (Tooling/E2E) — stale-build guardrails

Problem:
- `5500` static build and `3000` dev build can diverge, causing false regression signals.

Implementation:
- Add explicit warning and target selection guidance in verification docs.
- Optionally add helper script to assert target UI build mode before E2E run.

Acceptance:
- E2E runs against intended target with clear diagnostics.

Verification:
- Script output + CI/local run notes.

## P103 (Backend + Frontend) — Search criteria canonicalization endpoint contract

Problem:
- Criteria aliases are currently normalized client-side; backend-side canonical contract improves stability.

Implementation:
- Add/extend backend request normalization for known aliases.
- Keep frontend parser compatibility for legacy data.

Acceptance:
- API accepts canonical and alias variants with identical search result behavior.

Verification:
- Backend tests + one frontend E2E compatibility pass.

## P104 (Regression Gate) — full search continuity suite

Implementation:
- Run and baseline:
  - `e2e/saved-search-load-prefill.spec.ts`
  - `e2e/search-dialog-active-criteria-summary.spec.ts`
  - `e2e/search-dialog-preview-status.spec.ts`
  - any new P98-P103 specs

Acceptance:
- Full suite green on current branch.

Verification command:

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 \
  npx playwright test \
  e2e/saved-search-load-prefill.spec.ts \
  e2e/search-dialog-active-criteria-summary.spec.ts \
  e2e/search-dialog-preview-status.spec.ts \
  --reporter=list
```

## Delivery Sequence

1. P98
2. P99
3. P100
4. P101
5. P102
6. P103
7. P104

## Current Completed Baseline

- P97 completed in this round:
  - `docs/PHASE1_P97_SAVED_SEARCH_JSON_FILTER_ALIAS_NORMALIZATION_DESIGN_20260212.md`
  - `docs/PHASE1_P97_SAVED_SEARCH_JSON_FILTER_ALIAS_NORMALIZATION_VERIFICATION_20260212.md`
