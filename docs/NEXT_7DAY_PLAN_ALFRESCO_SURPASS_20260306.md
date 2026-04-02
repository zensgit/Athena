# Next 7-Day Plan: Alfresco Surpass Track

## Date
2026-03-06

## Goal
- Deeply benchmark Alfresco community repo patterns and deliver Athena-native features that are easier to operate, faster to diagnose, and safer to batch-recover.
- Keep each day split into parallel lanes so backend/frontend/verification can move concurrently.

## Reference code studied (Alfresco)
- `reference-projects/alfresco-community-repo/repository/src/main/java/org/alfresco/transform/registry/CombinedConfig.java`
- `reference-projects/alfresco-community-repo/repository/src/main/java/org/alfresco/transform/registry/TransformServiceRegistryImpl.java`
- `reference-projects/alfresco-community-repo/repository/src/main/java/org/alfresco/repo/content/transform/LocalFailoverTransform.java`
- `reference-projects/alfresco-community-repo/repository/src/main/java/org/alfresco/repo/content/transform/RemoteTransformerClient.java`
- `reference-projects/alfresco-community-repo/repository/src/main/java/org/alfresco/repo/content/transform/TransformerDebugLog.java`
- `reference-projects/alfresco-community-repo/repository/src/main/java/org/alfresco/repo/thumbnail/FailureHandlingOptions.java`
- `reference-projects/alfresco-community-repo/repository/src/main/java/org/alfresco/service/cmr/thumbnail/FailedThumbnailInfo.java`
- `reference-projects/alfresco-community-repo/repository/src/main/java/org/alfresco/repo/rendition/RenditionPreventionRegistry.java`

## 7-day execution plan

### Day 1: Reason-scope batch recovery (Completed)
- Lane A (Backend)
  - Add `POST /api/v1/preview/diagnostics/failures/queue-by-reason`.
  - Add reason-window repository methods and bounded scan guardrail.
- Lane B (Frontend)
  - Move top-reason Retry/Force from current-list-only to backend reason-scope queue.
- Lane C (Verification)
  - Security/controller tests + mocked Playwright update.

### Day 2: Explicit retry-hint pipeline (Completed)
- Lane A (Backend)
  - Add explicit `retryNeeded` signal to preview result.
  - Honor retry hint before string-based failure classification.
- Lane B (Backend integration)
  - Parse CAD render response headers (`X-Ecm-Retry-Needed`, `X-Alfresco-Retry-Needed`).
- Lane C (Verification)
  - Queue service test proving retry scheduling when retry hint is true.

### Day 3: Transformer failover chain (Completed)
- Lane A
  - Implement CAD endpoint chain resolver + ordered failover attempts (primary + fallback URLs).
- Lane B
  - Add admin diagnostics API exposing active chain and per-endpoint recent outcomes.
- Lane C
  - Add/extend deterministic tests and mocked UI assertions for new diagnostics surface.

### Day 4: Debug trace and operator observability (Completed)
- Lane A
  - Add request-id keyed preview transform debug buffer (inspired by `TransformerDebugLog`).
- Lane B
  - Add UI panel for recent transform traces with request id filtering.
- Lane C
  - Add verification spec that captures and asserts trace lifecycle (start/fail/retry/success).

### Day 5: Failure policy profiles (Completed)
- Lane A
  - Introduce policy profiles (quiet period, retry count, backoff slope) mapped by mime/type class.
- Lane B
  - Admin UI for policy editing and live preview impact explanation.
- Lane C
  - Verify no-policy-regression defaults and strict caps.

### Day 6: Rendition prevention and protection (Completed)
- Lane A
  - Add prevention markers to avoid repeated futile generation on known blocked classes.
- Lane B
  - Add one-click unblock/reprocess admin actions.
- Lane C
  - Stress test against repeated queue storms.
- Delivered:
  - prevention registry + queue auto-block/unblock semantics.
  - admin APIs for blocked-list diagnostics and unblock/requeue actions.
  - diagnostics UI panel with operator controls.
  - queue storm guard tests (blocked-hit counter + non-force skip behavior).

### Day 7: Release hardening and closeout
- Lane A
  - Rollup API docs and migration notes.
- Lane B
  - Final UI usability pass + operator runbook.
- Lane C
  - Full regression + smoke + rollback checklist + release notes.
- Delivered (current progress)
  - CAD failover diagnostics expanded with circuit-breaker config/state visibility.
  - Redis+TTL hardening for rendition prevention registry (memory fallback retained).
  - Dead-letter recovery loop delivered:
    - dead-letter capture + replay-batch API
    - dead-letter Redis persistence + TTL + backend-mode diagnostics
    - operator panel chips for backend/TTL visibility
    - auto-replay policy (category allowlist + cooldown + capped batch)
    - CSV export + audit traceability for dead-letter operations
    - Redis contention/idempotency stress test for concurrent enqueue
  - Day7 one-command gate script:
    - `scripts/phase164-preview-day7-delivery-gate.sh`
  - verification docs updated with backend/frontend/mocked-e2e pass records.

## Parallelization matrix
- Backend Core: queueing, retry, failover, policy.
- Frontend Ops UI: diagnostics controls, debug panels, policy forms.
- Verification/Tooling: unit/integration/e2e and regression-gate updates.

## Success criteria
- Mean-time-to-recover preview failures reduced (reason-scope batch actions over full diagnostics window).
- Retry correctness improved (explicit hint > weak text matching).
- Operator visibility improved (clear reason grouping and actionable batch outcomes).
