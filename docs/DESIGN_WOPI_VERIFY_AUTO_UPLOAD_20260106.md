# Design: WOPI Verification Auto-Upload (2026-01-06)

## Goal
- Prevent WOPI verification from failing when no `.xlsx` file exists by uploading a known sample and indexing it.
- Avoid leaving sample documents behind when running ad-hoc verification.

## Approach
- Read the XLSX sample fixture from the E2E assets.
- Upload the sample into `/Root/Documents` when no matching document is found.
- Trigger search indexing and re-query by filename before proceeding with WOPI checks.
- Retry search briefly before uploading to allow indexing to catch up.
- Support `ECM_VERIFY_CLEANUP=1` to delete the uploaded sample after verification completes.

## Files
- scripts/verify-wopi.js
