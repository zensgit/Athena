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
- Startup visibility SLA mocked gate checks:
  - add first-visible SLA assertions for login and browse entry routes and publish samples in gate output.
  - Deliverables:
    - `docs/PHASE94_STARTUP_VISIBILITY_SLA_MOCKED_GATE_DEV_20260223.md`
    - `docs/PHASE94_STARTUP_VISIBILITY_SLA_MOCKED_GATE_VERIFICATION_20260223.md`
- Startup SLA WARN summary + delivery-gate failure hints:
  - convert SLA samples to explicit OK/WARN status lines and surface warning signals in gate startup hints.
  - Deliverables:
    - `docs/PHASE95_STARTUP_SLA_WARN_SUMMARY_AND_GATE_HINTS_DEV_20260223.md`
    - `docs/PHASE95_STARTUP_SLA_WARN_SUMMARY_AND_GATE_HINTS_VERIFICATION_20260223.md`
- Startup SLA drift baseline warnings:
  - add baseline drift analysis for startup visibility metrics and aggregate drift hints in gate failures.
  - Deliverables:
    - `docs/PHASE96_STARTUP_SLA_DRIFT_BASELINE_WARNINGS_DEV_20260223.md`
    - `docs/PHASE96_STARTUP_SLA_DRIFT_BASELINE_WARNINGS_VERIFICATION_20260223.md`

## Post Day 7 Stabilization (Completed on 2026-02-24)
- App crash recovery login-reason handoff hardening:
  - when `AppErrorBoundary` sends user back to login, persist a structured recovery reason and show dedicated login status card copy.
  - Deliverables:
    - `docs/PHASE97_APP_ERROR_RECOVERY_LOGIN_REASON_HANDOFF_DEV_20260224.md`
    - `docs/PHASE97_APP_ERROR_RECOVERY_LOGIN_REASON_HANDOFF_VERIFICATION_20260224.md`
- AppErrorBoundary global noise filtering:
  - ignore non-fatal `ResizeObserver` and abort/canceled rejection noise to reduce false-positive fatal fallback pages.
  - Deliverables:
    - `docs/PHASE98_APP_ERROR_BOUNDARY_GLOBAL_NOISE_FILTERING_DEV_20260224.md`
    - `docs/PHASE98_APP_ERROR_BOUNDARY_GLOBAL_NOISE_FILTERING_VERIFICATION_20260224.md`
- AppErrorBoundary noise filtering mocked gate coverage:
  - add dedicated mocked E2E spec for non-fatal global noise and include it in default phase5 regression set.
  - Deliverables:
    - `docs/PHASE99_APP_ERROR_BOUNDARY_NOISE_FILTER_MOCK_GATE_DEV_20260224.md`
    - `docs/PHASE99_APP_ERROR_BOUNDARY_NOISE_FILTER_MOCK_GATE_VERIFICATION_20260224.md`
- AppErrorBoundary chunk-load recovery guidance:
  - detect dynamic import/chunk load failures, show targeted asset-refresh hint, and use cache-busting reload path.
  - Deliverables:
    - `docs/PHASE100_APP_ERROR_BOUNDARY_CHUNK_LOAD_RECOVERY_DEV_20260224.md`
    - `docs/PHASE100_APP_ERROR_BOUNDARY_CHUNK_LOAD_RECOVERY_VERIFICATION_20260224.md`
- Chunk-load cache-busting reload behavior E2E hardening:
  - add dedicated mocked E2E assertion that `Reload` from chunk-load fallback navigates with `_ecm_reload` cache-busting query.
  - Deliverables:
    - `docs/PHASE101_CHUNK_LOAD_CACHE_BUST_RELOAD_E2E_DEV_20260224.md`
    - `docs/PHASE101_CHUNK_LOAD_CACHE_BUST_RELOAD_E2E_VERIFICATION_20260224.md`
- Startup blank-screen fallback watchdog:
  - add pre-React startup fallback overlay in `index.html` when app shell stays blank beyond timeout, with `Reload` and `Back to Login` recovery actions.
  - Deliverables:
    - `docs/PHASE102_STARTUP_BLANK_SCREEN_FALLBACK_WATCHDOG_DEV_20260224.md`
    - `docs/PHASE102_STARTUP_BLANK_SCREEN_FALLBACK_WATCHDOG_VERIFICATION_20260224.md`
- Startup fallback false-positive guard:
  - add mocked E2E asserting normal startup path does not trigger fallback overlay.
  - Deliverables:
    - `docs/PHASE103_STARTUP_FALLBACK_FALSE_POSITIVE_GUARD_DEV_20260224.md`
    - `docs/PHASE103_STARTUP_FALLBACK_FALSE_POSITIVE_GUARD_VERIFICATION_20260224.md`

## Exit Criteria
1. Storage-restricted auth flows remain recoverable and non-blank.
2. Filename-like queries do not trigger noisy spellcheck suggestions.
3. Gate failures provide explicit startup/auth dependency hints.
