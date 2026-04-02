# Phase 362C: Batch Download Preflight Decision Diagnostics

## Goal

Make batch-download preflight decisions explicit so operators and UI flows can distinguish between:

- fully ready selections,
- partially executable selections,
- and blocked selections.

The aim is to improve download applicability transparency beyond a plain `executable=true|false` check and move Athena closer to, and in this slice beyond, Alfresco-style archive applicability rigor.

## Delivered

- Added preflight decision semantics to batch-download preflight:
  - `READY`
  - `PARTIAL`
  - `BLOCKED`
- Added a primary reason classifier for the dominant preflight outcome:
  - `NONE`
  - `DUPLICATE_REFERENCES`
  - `MISSING_NODES`
  - `DELETED_NODES`
  - `FORBIDDEN_NODES`
  - `EMPTY_FOLDERS`
  - `NO_READABLE_FILES`
- Extended `/api/v1/nodes/download/batch-async/preflight` response without removing existing fields.
- Updated `FileBrowser` toast messaging to reflect explicit preflight decision state instead of only checking `executable`.

## Design

This slice keeps the existing async batch-download contract intact and adds **decision transparency** rather than changing queue/start behavior.

Why:

- Athena already had structured item-level preflight diagnostics.
- What it lacked was a compact top-level applicability decision suitable for UI and operator flows.
- Alfresco’s download creation path is stricter about archive applicability and hard rejections; Athena now keeps its richer structured warnings while also surfacing a clear top-level decision model.

## API Additions

`BatchDownloadPreflightResponse` now includes:

- `decision`
- `primaryReason`

Existing fields such as `executable`, `message`, `warnings`, and `items` are preserved for compatibility.

## Why This Matters

This gives Athena a clearer operator-facing answer to:

- can this selection run,
- will it run partially,
- and what is the main reason anything is being skipped or blocked.

That is a detail-level governance improvement over the previous Athena contract and supports more reliable UI behavior than a bare boolean gate.

## Claude Code Usage

Claude Code was used as a parallel design assistant to compare Athena batch-download semantics against Alfresco `DownloadsImpl` and to validate the smallest safe applicability-focused slice. Final implementation and validation were completed in this workspace flow.
