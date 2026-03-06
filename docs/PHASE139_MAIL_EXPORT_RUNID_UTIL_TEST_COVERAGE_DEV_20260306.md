# Phase 139: Mail Export RunId Utility Extraction and Test Coverage

## Date
2026-03-06

## Background
- RunId selection and filename sanitization for diagnostics export were implemented directly in `MailAutomationPage`.
- This logic is easier to regress without isolated unit tests.

## Goals
1. Extract export runId utility logic into a dedicated reusable module.
2. Add focused unit tests for:
   - filename sanitization
   - runId selection precedence (debug vs fetch by timestamp)
3. Keep runtime behavior unchanged.

## Changes
- Added utility module:
  - `ecm-frontend/src/pages/mailAutomationExportUtils.ts`
  - exports:
    - `sanitizeRunIdForFilename`
    - `resolveDiagnosticsExportRunId`
- Updated page integration:
  - `ecm-frontend/src/pages/MailAutomationPage.tsx`
  - replaced inline helpers with utility imports.
- Added unit tests:
  - `ecm-frontend/src/pages/mailAutomationExportUtils.test.ts`
  - covers empty/sanitized values, single-source runId, timestamp precedence, invalid timestamp fallback.

## Compatibility
- No API contract changes.
- No visual/UI behavior changes beyond preserving the same runId export behavior through tested helpers.
