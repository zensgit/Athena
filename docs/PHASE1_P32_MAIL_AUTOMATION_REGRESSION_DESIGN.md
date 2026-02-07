# Mail Automation P32 — Regression Coverage Expansion

Date: 2026-02-06

## Goal
Expand Mail Automation E2E coverage for newly added diagnostics/runtime/linkage behavior and reduce regression risk.

## Design
### E2E Spec Updates
- Extended `ecm-frontend/e2e/mail-automation.spec.ts` with coverage for:
  - Runtime health panel rendering
  - Diagnostics export scope snapshot rendering
  - Mail document "find similar" navigation to search
  - Replay failed processed item flow

### Assertions
- Runtime panel test validates page card and key labels.
- Export snapshot test validates the scope summary text presence.
- Similar action test validates navigation intent into search context.
- Replay test validates replay action path is callable from diagnostics list.

## Files Changed
- `ecm-frontend/e2e/mail-automation.spec.ts`

