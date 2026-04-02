# Phase367ZZAM FileList Effective Preview Semantics Convergence

## Goal

Remove browse-level preview chip rendering that still relied on raw `node.previewStatus` instead of the shared effective preview semantics.

## Problem

`FileList.tsx` still had browse-specific preview logic that:

- read `node.previewStatus?.toUpperCase()` directly
- only treated raw `FAILED` as a failure state
- tooltiped raw `previewFailureReason` without first resolving effective preview status

That left browse weaker than the newer search/preview/admin surfaces, especially for unsupported preview cases.

## Implementation

Updated `ecm-frontend/src/components/browser/FileList.tsx` to use `getEffectivePreviewStatus(...)` in both list and grid renderers.

### Shared effective semantics

- `getPreviewStatusMeta(...)` now resolves through `getEffectivePreviewStatus(...)`.
- `FAILED` and `UNSUPPORTED` both route through `getFailedPreviewMeta(...)`.
- `PENDING` is treated as “no extra chip”, matching the rest of the app.

### Browse renderer convergence

- DataGrid name cell now computes effective preview status and only surfaces failure tooltip/details when the effective state is `FAILED` or `UNSUPPORTED`.
- Card/grid browse view now does the same.

## Outcome

Browse-level preview chips now align with the same effective preview semantics already used in stronger operator surfaces, closing another ordinary-node preview semantics split.
