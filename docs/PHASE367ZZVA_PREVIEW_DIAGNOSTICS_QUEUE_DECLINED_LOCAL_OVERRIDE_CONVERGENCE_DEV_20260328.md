# Phase367ZZVA Preview Diagnostics Queue Declined Local Override Convergence

## Goal

Make the `queue-declined` operator surface in `PreviewDiagnosticsPage` behave like a responsive control plane instead of waiting for a full multi-panel reload before the table and counts reflect `requeue` or `clear`.

## Problem

After the backend `queue-declined` preview status convergence, the admin page still had an operator-detail gap:

- `Requeue Declined` and `Clear Declined` always waited on `loadFailures()`
- the visible declined table and summary chips stayed stale until that full refresh completed
- the page had no local notion of:
  - updated preview status after requeue
  - locally removed rows after clear

This made the UI feel slower and less precise than the rest of the preview diagnostics workbench.

## Design

### Shared local-override utility

Files:

- `ecm-frontend/src/utils/queueDeclinedUtils.ts`
- `ecm-frontend/src/utils/queueDeclinedUtils.test.ts`

Add a narrow local override model:

- `PreviewQueueDeclinedLocalOverride`

and three helpers:

- `applyQueueDeclinedLocalOverrides(...)`
- `buildQueueDeclinedOverridesFromRequeue(...)`
- `buildQueueDeclinedOverridesFromClear(...)`

Behavior:

1. overlay preview status updates from `requeue`
2. hide locally cleared rows from `clear`
3. recompute `filteredSampledItems`, `forceRequiredCount`, and `categoryCounts`

### Use local overrides in PreviewDiagnosticsPage

File:

- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

Add:

- `queueDeclinedLocalOverrides`
- `effectiveQueueDeclinedSummary`

Then switch these consumers to the derived summary:

- summary chips
- category chips
- category options
- disabled state of declined action buttons
- declined table rows

### Keep background refresh, stop blocking on it

`handleRequeueQueueDeclined()` and `handleClearQueueDeclined()` now:

1. apply local overrides immediately
2. clear stale dry-run output
3. kick off `loadFailures()` in the background with `void loadFailures()`

This preserves eventual full refresh behavior while removing the visible lag from the primary operator path.

## Result

The `queue-declined` admin surface now updates like a real operator console:

- requeue can immediately change visible preview status
- clear can immediately remove rows and adjust counts
- the user no longer waits on a full diagnostics reload before seeing the effect of the action

This closes another UX gap between Athena’s diagnostics workbench and the more mature operator surfaces it is aiming to surpass.
