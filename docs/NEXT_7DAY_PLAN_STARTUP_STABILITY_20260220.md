# Next 7-Day Plan: Startup Stability and White-Screen Prevention

## Date
2026-02-20

## Objective
- Eliminate startup-stage blank/boot-stuck experiences.
- Ensure local static proxy runs cannot silently use stale frontend bundles.
- Keep delivery gate behavior deterministic across dev (`:3000`) and static (`http://localhost`) targets.

## Day-by-Day Plan

### Day 1 (Completed)
- Auth bootstrap storage-guard hardening:
  - make startup sessionStorage reads/writes/removes safe under restricted contexts
  - add fatal catch path for bootstrap startup
- Auth/route matrix hardening:
  - add startup recoverability scenario when storage remove throws
- Prebuilt sync stale detection hardening:
  - in `auto`, treat frontend dirty worktree as stale and rebuild static prebuilt

### Day 2 (Completed)
- FileBrowser loading-state watchdog:
  - add “loading too long” warning panel (retry + back to root)
  - avoid infinite spinner-only state when requests hang
- Add targeted mocked E2E for slow/hanging folder list APIs.

### Day 3 (Completed)
- App-level startup watchdog banner:
  - if auth booting exceeds threshold, show actionable recovery controls
  - keep telemetry hook for timeout/fatal-path diagnostics
- Add unit coverage for startup watchdog transitions.

### Day 4
- Network timeout budget alignment:
  - standardize API timeout defaults by operation class (read/upload/download)
  - ensure long-running operations opt-in to larger timeout budgets
- Add regression tests for timeout-to-retry/timeout-to-warning behavior.

### Day 5
- Chaos matrix expansion:
  - storage errors, transient API hangs, auth redirect timing jitter
  - verify terminal states are recoverable (login or keycloak), not blank
- Integrate matrix into smoke scripts where applicable.

### Day 6
- Delivery gate observability:
  - summarize startup-related failures in a dedicated section
  - add quick hints for stale static target / storage restriction / auth timeout

### Day 7
- Release closeout:
  - consolidate verification rollup
  - publish rollback checklist and operator runbook updates
  - freeze baseline for next phase intake

## Exit Criteria
1. No reproducible startup blank/boot-stuck in matrix and p1 smoke baselines.
2. Static proxy smoke runs rebuild automatically when frontend working tree is dirty.
3. Gate summaries clearly identify startup failure category when a stage fails.
