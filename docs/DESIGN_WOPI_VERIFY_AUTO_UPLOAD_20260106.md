# Design: WOPI Verification Auto-Upload (2026-01-06)

## Goal
- Prevent WOPI verification from failing when no `.xlsx` file exists by uploading a known sample and indexing it.

## Approach
- Read the XLSX sample fixture from the E2E assets.
- Upload the sample into `/Root/Documents` when no matching document is found.
- Trigger search indexing and re-query by filename before proceeding with WOPI checks.

## Files
- scripts/verify-wopi.js
