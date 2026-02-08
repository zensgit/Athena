# PHASE1 P61 - Version Compare (Any Two Versions)

Date: 2026-02-09

## Goal

Enable users to compare **any two** document versions (not just "current vs previous") from the Version History UI, including an inline text diff for text-like documents.

## Current Behavior (Before)

`Version History` supported:

- Compare selected version vs previous version
- Compare selected version vs current version

But once the compare dialog opened, the pair was fixed to the action you clicked (no way to pick an arbitrary pair).

## Proposed / Implemented Behavior

In the `Compare Versions` dialog:

- Add two selectors:
  - **From version** (baseline)
  - **To version** (target)
- Show the direction explicitly: `From -> To`
- Allow changing either selector to compare arbitrary pairs without closing the dialog.

Guardrails:

- If `From == To`, show a warning and disable the diff section.
- If the version list is paged and not fully loaded, show a note: only loaded versions are selectable (user can load more in the underlying history list).

## API / Data Flow

No backend changes required. The compare UI continues to use the existing compare endpoint via `nodeService.getVersionTextDiff()`:

- `GET /api/v1/documents/{documentId}/versions/compare`
  - `fromVersionId=<uuid>`
  - `toVersionId=<uuid>`
  - `includeTextDiff=true`
  - `maxBytes`, `maxLines` (client-side defaults)

Text diff is displayed only when both versions have a text-like mime type.

## UI Changes

File: `ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx`

- Compare dialog now includes From/To selectors.
- Existing context menu actions still open the compare dialog with sensible defaults:
  - "Compare versions": selected vs previous
  - "Compare with current": current vs selected

## Testing Strategy

- Add a Playwright E2E scenario that creates 3 text versions, opens compare dialog from history, then switches the pair to `v1 -> v3` and asserts the diff includes both markers.

