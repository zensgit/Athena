# Phase 367F: Document Preview Lock Info Consumption

## Goal

Make Athena’s new lock-info query visible in a real operator-facing UI surface without a broad redesign.

This phase consumes the lock diagnostics in document preview so users can immediately see:

- whether they own the lock,
- whether another user holds it,
- whether a prior lock has expired,
- and, for temporary locks, roughly how long remains.

## Delivered

- Added shared frontend lock-info formatting helpers:
  - chip label
  - alert message
  - alert severity
- `DocumentPreview` now loads `nodeService.getLockInfo(nodeId)` alongside node details.
- Added a toolbar chip for active or expired lock state.
- Added a preview-top status alert for lock detail messaging.
- Added focused unit tests for lock-info UI formatting helpers.

## Design

This phase deliberately avoids a larger UI redesign.

Why this slice first:

- `DocumentPreview` already has node detail loading and a status/alert region.
- Lock state matters most while a user is deciding whether they can continue editing or should wait.
- Reusing that surface gives Athena a better operator experience with minimal layout churn.

## Why This Matters

Compared with the benchmark, this slice improves operational detail because the preview surface now exposes:

- caller-relative lock ownership,
- remaining time for temporary locks,
- and explicit stale-lock messaging.

That is more actionable than a generic “locked” badge and turns the backend lock-info work into an immediately visible user-facing improvement.

## Claude Code Usage

Claude Code was used as a parallel design assistant to identify the smallest high-value frontend surface for lock-info consumption. Final implementation and validation were completed in this workspace flow.
