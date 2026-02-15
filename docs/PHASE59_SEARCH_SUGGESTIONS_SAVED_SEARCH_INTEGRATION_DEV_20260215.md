# Phase 59 - Search Suggestions + Save Search (Integration Smoke) - Development

## Date
2026-02-15

## Goal
Close the Day 6 real-backend gap by adding a deterministic integration E2E for:
- spellcheck "Did you mean" suggestion flow
- Save Search creation from Advanced Search dialog

## Implementation
- Added integration spec:
  - `ecm-frontend/e2e/search-suggestions-save-search.integration.spec.ts`
- Added one-command smoke script:
  - `scripts/phase5-search-suggestions-integration-smoke.sh`
- Extended delivery gate to include this integration smoke:
  - `scripts/phase5-phase6-delivery-gate.sh`

## Spec Coverage
1. Upload and index a real text document with a known target word.
2. Open `Advanced Search` and search with misspelled `Name contains`.
3. Save current criteria as a Saved Search and verify saved query payload via API.
4. Run search and assert "Did you mean" renders.
5. Click suggestion and assert quick-search value updates and results remain visible.
6. Cleanup saved search by id in `finally`.
