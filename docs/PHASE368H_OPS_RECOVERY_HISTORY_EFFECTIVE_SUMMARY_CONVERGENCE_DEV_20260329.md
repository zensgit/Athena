# PHASE368H Ops Recovery History Effective Summary Convergence

## Goal

Continue the `preview / rendition / search` source-of-truth line by extending `Ops Recovery History` from a pure audit-event view into a document-aware operator view.

This phase makes recovery history entries and CSV exports carry the same effective preview semantics already used elsewhere in Athena:

- `previewStatus`
- `previewFailureReason`
- `previewFailureCategory`
- `previewLastUpdated`

## Why This Phase

Before this phase, Athena had already converged:

- batch recovery execution
- dry-run planning
- dry-run CSV export

But `Ops Recovery History` still lagged behind.

History entries only exposed:

- event type
- mode
- actor
- event time
- details

That meant operators could see that a recovery action happened, but could not see the effective preview state of the affected document without manually pivoting elsewhere.

This was especially weak because `AuditLog` already carries:

- `nodeId`
- `nodeName`

So the raw materials for a document-aware history surface already existed.

## Scope

### Backend

Updated [OpsRecoveryController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java):

- `RecoveryHistoryItemDto` now includes:
  - `nodeId`
  - `nodeName`
  - `previewStatus`
  - `previewFailureReason`
  - `previewFailureCategory`
  - `previewLastUpdated`
- `toRecoveryHistoryItem(...)` now resolves the referenced document from `AuditLog.nodeId` and, when it is a live document, derives effective preview semantics through the existing rendition-backed `resolveEffectivePreviewSummary(...)`.
- `buildHistoryCsv(...)` now exports the same document-aware preview fields.

This automatically improves:

- `GET /api/v1/ops/recovery/history`
- `GET /api/v1/ops/recovery/history/export`
- async `HISTORY` export tasks, because they already reuse the same history CSV builder

### Frontend

Updated [opsRecoveryService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/opsRecoveryService.ts):

- `RecoveryHistoryItem` now includes `nodeId`, `nodeName`, and effective preview summary fields.

Updated [PreviewDiagnosticsPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx):

- the `Ops Recovery History` table now shows a dedicated `Node` column
- the table also shows a dedicated `Preview` column with:
  - preview status chip
  - failure category chip
  - failure reason text
  - preview last updated text

This means the history workbench is no longer just an audit trail. It now behaves like a real operator investigation surface.

## Files

### Backend

- `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`

### Frontend

- `ecm-frontend/src/services/opsRecoveryService.ts`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

## Outcome

Athena’s `Ops Recovery History` is now aligned with the rest of the preview/rendition governance line:

- history rows carry document-aware effective preview semantics
- sync CSV export carries the same fields
- async `HISTORY` export inherits the same enriched CSV shape

This closes another operator-facing gap where Athena previously had richer recovery execution semantics than recovery history semantics.
