# Athena ECM Next Iteration Suggestions (2026-02-05)

## P0 (Stability / Ops)
- Add CI guard to skip Phase C mail verification when `.env.mail` is intentionally absent and log a clear reason.
- Add structured logging for mail diagnostics runs and expose run ID in UI.
- Add retry/backoff metrics for preview queue and surface last failure reason in UI.

## P1 (Productivity / UX)
- Permission template compare: allow side‑by‑side diff view with filter by authority/type.
- Add “Export JSON” alongside CSV for permission template diffs.
- Search highlight: include file path and creator in snippet card when available.
- Preview retry: allow per‑file “Retry now” and “Retry later” actions.

## P2 (Automation / Governance)
- Schedule mail reporting exports to a chosen folder.
- Add permission template rollback button in history dialog (admin‑only).
- Add audit event for permission template edits and compare exports.

## Tech Debt / Cleanup
- Consolidate rollup docs into a single index with anchors for easier navigation.
- Normalize API responses for search highlights and explainability fields.
- Add integration test for permission template version snapshots.
