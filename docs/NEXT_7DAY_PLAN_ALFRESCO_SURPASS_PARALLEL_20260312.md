# Next 7-Day Parallel Plan (Alfresco Surpass) - 2026-03-12

## Context
- Baseline completed in Athena stream-B:
  - Phase282: async lifecycle governance parity (`TIMED_OUT/EXPIRED`, actor/timestamps).
  - Phase283: terminal retry governance for rendition/ops export task centers.
  - Phase284: start-time active-task dedup for rendition/ops export task centers.
- Reference baseline:
  - `/reference-projects/alfresco-community-repo/remote-api/src/main/java/org/alfresco/rest/api/*`
  - `/reference-projects/alfresco-community-repo/repository/src/main/java/org/alfresco/repo/{rule,action,audit,search,rendition,workflow,security,version}/*`

## Parallel Team Topology (4 tracks)
- Track A (Async governance + control plane):
  - Owner scope: `PreviewDiagnosticsController`, `OpsRecoveryController`, related frontend task centers.
- Track B (Search + rendition intelligence):
  - Owner scope: `SearchController`, preview diagnostics/search UI, async search export/task center.
- Track C (Rules/actions/workflow-lite):
  - Owner scope: new rule/action endpoints/services + execution audit.
- Track D (Security/audit/compliance):
  - Owner scope: ACL/permission observability, audit query/export APIs, retention and cleanup policy.

## Day 2 (Async Governance Consolidation)
- A1: Standardize all async create/retry endpoints to `202 + Location` for rendition/ops centers.
- A2: Add bulk terminal retry APIs for rendition/ops task centers (`retry-terminal`, `retry-terminal/by-task-ids`).
- A3: Add dry-run retry planning APIs with reason breakdown + CSV export.
- D1: Add structured async governance audit events for A1/A2/A3.
- Exit criteria:
  - backend controller tests for accepted/location semantics + dedup/retry/bulk retry.
  - frontend task-center flow supports start/retry/bulk retry/dry-run with stable toasts.

## Day 3 (Node API Parity Sprint)
- B1: Add node relations parity slice: parents/children/sources/targets summary endpoints.
- B2: Add node rendition + version relation summary endpoints (with pagination + filters).
- C1: Add action-definition discoverability endpoint for node-context operations.
- Exit criteria:
  - API contract tests + frontend integration panel smoke in mocked e2e.

## Day 4 (Rules + Action Engine)
- C2: Implement folder-scoped rule set CRUD (enable/disable, priority/order, dry-run execute).
- C3: Implement action execution pipeline with idempotency key + per-run result ledger.
- D2: Add rule/action audit timeline and export.
- Exit criteria:
  - rule lifecycle tests + execution idempotency tests + audit export tests.

## Day 5 (Security + Compliance Depth)
- D3: ACL diagnostics enrichment (effective grants, inheritance chain, deny/allow explanation).
- D4: Retention cleanup jobs for async task centers and recovery ledgers with policy profiles.
- A4: Add cross-center task governance dashboard API (health + backlog + timeout risk).
- Exit criteria:
  - security tests for role-gated governance operations.
  - retention policy unit/integration coverage.

## Day 6 (Search & Rendition Surpass)
- B3: Search explainability package (`matchedIn`, ranking diagnostics, reason chips).
- B4: Rendition preflight route scoring + fallback recommendation APIs.
- A5: Auto-remediation hooks (retry schedule recommendation + no-op guard for non-retryable).
- Exit criteria:
  - mocked e2e for explainability and remediation hints.
  - backend tests validating non-retryable hard guards.

## Day 7 (Hardening + Delivery Gate)
- A6: Concurrency/soak verification for async centers (active dedup + bulk retry contention).
- D5: Release-grade verification rollup + failure budget report.
- B5/C4: UX polish and API docs/OpenAPI consistency pass.
- Exit criteria:
  - targeted Maven/Jest/Playwright gates all green.
  - release notes + docs index + development/verification MD finalized.

## Immediate Executable Backlog (no further clarification needed)
1. Phase285: `202 + Location` semantics for rendition/ops async start/retry.
2. Phase286: rendition/ops async `retry-terminal` + `retry-terminal/by-task-ids`.
3. Phase287: rendition/ops async retry dry-run + CSV.
4. Phase288: unified async governance summary dashboard API/UI.

## Validation Gate (daily)
- Backend:
  - `mvn -Dtest=*PreviewDiagnostics*SecurityTest,*OpsRecovery*SecurityTest test`
- Frontend:
  - `npm run lint -- --max-warnings=0`
  - `npm run build`
- Mocked e2e:
  - `npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts`

