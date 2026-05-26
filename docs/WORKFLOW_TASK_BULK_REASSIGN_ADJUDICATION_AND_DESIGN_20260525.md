# Workflow Task Bulk Reassign (C2) — Adjudication & Implementation Brief (read-only)

Date: 2026-05-25
Status: **PARKED — ready-to-build, not scheduled.** Gate-reviewed and pre-checked (small, low-risk, primitive exists); **deliberately not implemented** because the value signal is inferred, not requested (refresh-3 pause). No code/test/schema/`.env` written by this document.
Resume trigger: a real operator/admin request, a support ticket, or a product decision naming the offboarding/leave-coverage workflow. On any of those this is a same-day pickup — see §10.
Candidate: **C2** in `docs/PRODUCT_CAPABILITY_DISCOVERY_REFRESH3_20260525.md`.

## 0. Purpose & honesty caveat

Reassign multiple pending workflow tasks from one assignee to another in one action (offboarding / leave-coverage). **Value is inferred, not a captured operator request** (per the refresh-3 caveat) — but the pre-check confirms this is a **small, low-risk** slice: the single-task reassign primitive already exists, so bulk is "loop the existing call with per-row partial success," the proven pattern from the delivered bulk slices.

## 1. Pre-check result (why this is small, not a track)

- **Single-task reassign exists:** `WorkflowController.java:392` `POST /api/v1/workflows/tasks/{taskId}/assign` (`AssignTaskRequest{assignee}`) → `WorkflowService.assignTask(taskId, assignee)` (`:425`).
- `assignTask` (`:425-448`): task missing → `ResourceNotFoundException`; null assignee → `IllegalArgumentException`; target user missing → `ResourceNotFoundException("User not found")`; per-task permission (admin, or current-assignee/owner rules) → `SecurityException`.
- Task inbox to drive selection exists: `WorkflowController.java:142` `GET /tasks`/`/tasks/inbox` with `assignee` filter; frontend `TasksPage.tsx` already lists the inbox (`getTaskInbox`) and has a single-task assign flow with a user picker (`assignmentUsername`, `assignmentOptions`, `assignTask` confirm-action).
- **No `@Transactional` self-call concern:** `WorkflowService` (`:49-51`) is `@Service` without class `@Transactional`, and `assignTask` is not `@Transactional` (Flowable's `taskService` manages persistence). So a bulk method may loop `assignTask` directly — unlike bulk-share, no proxied-orchestrator is needed for transaction correctness.

## 2. Adjudication

Not a closed/forbidden track (not RM preset, not defensive/test-only). New capability on the workflow surface. Proceed — with the honest weak-signal caveat (§0).

## 3. Scope (locked)

Operator selects pending tasks in the inbox and reassigns them all to one target user, with per-row partial success.

## 4. Out of scope

- No change to the single-assign permission contract (`assignTask`'s admin/current-assignee rules are reused as-is, per row).
- No bulk claim / unclaim / delegate / complete / resolve.
- No server-side "reassign everything matching a query" sledgehammer — the operator sends explicit task IDs (D1).
- No scheduler/async, no schema, no Flowable engine changes, no `.env`.

## 5. Decisions for the gate

| # | Decision | Recommendation |
|---|---|---|
| **D1** | Request shape: explicit `taskIds` + target `assignee`, vs `fromAssignee`→`toAssignee` | **Explicit `taskIds` + `assignee`.** Matches the established bulk pattern + frontend multi-select; avoids a blind "reassign all of A" mutation. The "all of user A" case = frontend filters inbox by `assignee=A`, select-all, sends those IDs. |
| **D2** | Target-user validation | **Validate the target `assignee` exists once up front** (reject whole request → 400/404 if missing). Avoids every row failing identically; keeps per-row outcomes task-level. |
| **D3** | Duplicate task IDs | **Dedupe first-seen**; reject null/empty / null-only → 400. |
| **D4** | Per-task permission | **Reuse `assignTask`'s per-task `SecurityException` semantics unchanged** → `NO_PERMISSION` row. Do not loosen/tighten the single-assign contract (same discipline as bulk-share D1). |
| **D5** | Orchestration | Loop `assignTask` per row with per-row try/catch (no proxied orchestrator needed — §1). A dedicated `BulkWorkflowTaskService` is optional for tidiness; a method on `WorkflowService` is acceptable since there's no self-call/`@Transactional` trap. |

## 6. Proposed contract

**Endpoint:** `POST /api/v1/workflows/tasks/bulk-reassign` (under the existing `/api/v1/workflows` controller; same method-level auth posture as the sibling task mutations — confirm at implementation).

**Request:**
```jsonc
{ "taskIds": ["...", "..."], "assignee": "<target-username>" }
```
- Empty/null/null-only `taskIds` → 400; blank `assignee` → 400; target user not found → 404 (validated once, D2).

**Response (per-row partial success, mirrors delivered bulk slices):**
```jsonc
{
  "reassigned": 3, "failed": 1,
  "rows": [
    { "taskId": "...", "status": "REASSIGNED", "errorCategory": null, "message": null },
    { "taskId": "...", "status": "FAILED", "errorCategory": "NO_PERMISSION", "message": "<fixed sanitized copy>" }
  ]
}
```
- Status closed set: `REASSIGNED | FAILED`. ErrorCategory closed set (FAILED only): `TASK_NOT_FOUND` (`ResourceNotFoundException`), `NO_PERMISSION` (`SecurityException`), `INTERNAL_ERROR` (other `RuntimeException`, sanitized — fixed copy + exception class only, never `ex.getMessage()`, never raw `Throwable` to SLF4J).
- Invariants: `REASSIGNED` → null errorCategory/message; `FAILED` → non-null both.

## 7. Affected surfaces

- **Backend:** new `POST /tasks/bulk-reassign` on `WorkflowController` + a `reassignTasksBulk(taskIds, assignee)` method (on `WorkflowService` or a small `BulkWorkflowTaskService`) looping `assignTask`; new request/response records + status/category enums. **No schema.**
- **Frontend:** `TasksPage.tsx` — multi-select on the inbox list + a "Reassign selected…" action reusing the **existing** assignment user picker (`assignmentOptions`); new `workflowService.bulkReassignTasks(taskIds, assignee)` with a status-keyed response guard + sentinel (reuse the workflow service's existing assert pattern). Partial-failure UI: stay open / surface failed rows grouped by category (mirror prior bulk dialogs).
- **Schema:** none.

## 8. Tests (planned)

- Backend service: dedupe; empty/null-only → 400; target-user-missing → 404 once (no per-row); `ResourceNotFoundException`→`TASK_NOT_FOUND`; `SecurityException`→`NO_PERMISSION`; other→`INTERNAL_ERROR` sanitized (probe-leak check); continues after a failed row.
- Backend controller: `POST /tasks/bulk-reassign` shape (`$.rows`), 400 on empty, auth (401/403/admit) per the controller's posture.
- Frontend: service posts exact payload + guard rejects HTML fallback / illegal status / illegal category; TasksPage multi-select + reassign dialog flow (all-reassigned success, partial-failure stays open).

## 9. Memory checklist

- `feedback_brief_paths_must_be_grep_verified` — endpoints/exceptions verified: assign `:392`/`:425`, inbox `:142`, `WorkflowService` non-`@Transactional` `:49-51`.
- `feedback_requiredargsconstructor_arity_breaks_standalone_tests` — if a field is added to `WorkflowController`/`WorkflowService`, grep `new WorkflowController(`/`new WorkflowService(` in tests (standalone fixtures) before pushing.
- `project_backend_preflight_local_verification` — before pushing, run `scripts/backend-preflight.sh` (and `-Dtest=… test`) to catch testCompile/Mockito/fixture errors locally in ~11s; do not rely on the Docker-backed `ecm-core/mvnw`.
- `feedback_sanitize_throwable_cause_for_log_emission` — FAILED rows fixed copy only.
- Verify `@Builder`/entity field **types** (not just existence) when constructing test fixtures (the saved-search `SearchResult.id` String lesson).

## 10. Gate decision — PARKED (2026-05-25)

This was the strongest remaining refresh-3 candidate and is confirmed small/low-risk — **but the value signal is inferred, not requested.** Decision: **park (option b), do not build now.** Rationale: after three discovery refreshes the clearly-shaped gaps are exhausted; building a low-signal feature because it's cheap is "building for continuity," not value. "Small and low-risk" answers *can we?*, not *should we?*

**Ready-to-build on a real signal.** When a resume trigger lands (header), this brief is the durable artifact — the pre-check (§1) already confirmed the single-task `assignTask` primitive exists, no `@Transactional` self-call trap, and the `TasksPage` inbox + user-picker surfaces exist, so implementation is a same-day pickup with zero re-discovery: backend `POST /workflows/tasks/bulk-reassign` looping `assignTask` (§5/§6), frontend multi-select + reassign action, tests per §8.

## 11. Verification (this brief)

```bash
git status --short                              # M .env + this brief only
git diff --stat -- 'ecm-core/' 'ecm-frontend/'  # empty
```
