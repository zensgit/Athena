# Preview P12 — Failure Reason Summary and One-Click Retry

Date: 2026-02-06

## Goal
Improve preview troubleshooting by surfacing grouped failure reasons and enabling direct retry from each reason group.

## Design
- Group failed preview documents on current results page by `previewFailureReason`.
- Show top failure buckets in the facet sidebar under preview controls.
- Add action buttons per reason bucket:
  - `Retry this reason`
  - `Force rebuild`
- Reuse existing queue API (`queuePreview`) and existing queue-status bookkeeping.

## Files Changed
- `ecm-frontend/src/pages/SearchResults.tsx`
