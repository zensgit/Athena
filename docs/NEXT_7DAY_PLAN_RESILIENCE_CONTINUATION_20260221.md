# Next 7-Day Plan: Resilience Continuation (Post-Phase83)

## Date
2026-02-21

## Objective
- Continue eliminating high-friction recovery gaps around auth transitions and search precision.
- Keep mocked gate deterministic while improving integration diagnostics/operator feedback.

## Day-by-Day Plan

### Day 1 (Completed)
- Auth/session storage safety hardening:
  - `PrivateRoute` safe storage access wrappers.
  - `Login` safe storage access wrappers.
- Spellcheck precision extension:
  - normalize punctuated/quoted filename-like query tokens before spellcheck decision.
- Gate diagnostics polish:
  - `phase70` preflight endpoint checks now emit actionable hints.

### Day 2 (Completed)
- Add mocked E2E for storage-restricted auth transitions:
  - verify no blank/stuck state when both `sessionStorage` and `localStorage` reads are partially restricted.
  - Deliverables:
    - `docs/PHASE85_AUTH_STORAGE_RESTRICTED_MOCK_E2E_DEV_20260221.md`
    - `docs/PHASE85_AUTH_STORAGE_RESTRICTED_MOCK_E2E_VERIFICATION_20260221.md`

### Day 3 (Completed)
- Add login route “auth handoff watchdog” UX copy refinement:
  - explicitly differentiate timeout vs redirect-failed vs session-expired in one status card.
  - Deliverables:
    - `docs/PHASE86_LOGIN_AUTH_HANDOFF_STATUS_CARD_DEV_20260221.md`
    - `docs/PHASE86_LOGIN_AUTH_HANDOFF_STATUS_CARD_VERIFICATION_20260221.md`

### Day 4 (Completed)
- Search exact-match mode visibility:
  - add UI chip when filename-like precision mode is active, so operators understand why spellcheck is skipped.
  - Deliverables:
    - `docs/PHASE87_SEARCH_EXACT_MATCH_MODE_VISIBILITY_DEV_20260221.md`
    - `docs/PHASE87_SEARCH_EXACT_MATCH_MODE_VISIBILITY_VERIFICATION_20260221.md`

### Day 5 (Completed)
- Expand regression gate summary:
  - include per-spec duration hotspots and top flaky candidates in mocked layer.
  - Deliverables:
    - `docs/PHASE88_PHASE5_REGRESSION_HOTSPOT_SUMMARY_DEV_20260221.md`
    - `docs/PHASE88_PHASE5_REGRESSION_HOTSPOT_SUMMARY_VERIFICATION_20260221.md`

### Day 6 (Completed)
- Integration smoke environment diagnostics:
  - preflight checks for backend/keycloak/ui with grouped remediation output.
  - Deliverables:
    - `docs/PHASE89_INTEGRATION_PREFLIGHT_GROUPED_DIAGNOSTICS_DEV_20260221.md`
    - `docs/PHASE89_INTEGRATION_PREFLIGHT_GROUPED_DIAGNOSTICS_VERIFICATION_20260221.md`

### Day 7 (Completed)
- Release closeout:
  - rollup verification + rollback checklist + runbook delta.
  - Deliverable:
    - `docs/PHASE90_RESILIENCE_CONTINUATION_RELEASE_CLOSEOUT_20260221.md`

## Post Day 7 Stabilization (Completed on 2026-02-22)
- Folder tree root-loading watchdog + retry:
  - avoid long blank/spinner-only state when root folder bootstrap is slow or transiently stalled.
  - Deliverables:
    - `docs/PHASE91_FOLDER_TREE_ROOT_LOADING_WATCHDOG_DEV_20260222.md`
    - `docs/PHASE91_FOLDER_TREE_ROOT_LOADING_WATCHDOG_VERIFICATION_20260222.md`

## Post Day 7 Stabilization (Completed on 2026-02-23)
- App error boundary crash-recovery mocked E2E:
  - cover forced render failure and verify `Back to Login` recovery path avoids persistent blank/fatal shell.
  - Deliverables:
    - `docs/PHASE92_APP_ERROR_BOUNDARY_RECOVERY_E2E_DEV_20260223.md`
    - `docs/PHASE92_APP_ERROR_BOUNDARY_RECOVERY_E2E_VERIFICATION_20260223.md`
- Unknown-route no-blank fallback mocked E2E:
  - cover wildcard route fallback for both unauthenticated and authenticated sessions to prevent runtime blank-page regressions.
  - Deliverables:
    - `docs/PHASE93_ROUTE_FALLBACK_NO_BLANK_MOCK_E2E_DEV_20260223.md`
    - `docs/PHASE93_ROUTE_FALLBACK_NO_BLANK_MOCK_E2E_VERIFICATION_20260223.md`

## Exit Criteria
1. Storage-restricted auth flows remain recoverable and non-blank.
2. Filename-like queries do not trigger noisy spellcheck suggestions.
3. Gate failures provide explicit startup/auth dependency hints.
