# Design: E2E Version History Actions (2026-01-06)

## Goal
- Extend UI E2E coverage to validate version history actions: download a specific version and restore to a previous version.

## Approach
- Use API to create a test document and add a new version.
- Drive the UI to open the Version History dialog, download the latest version, and restore an older version.
- Verify download request hits the version download endpoint and restoration toast appears.

## Files
- ecm-frontend/e2e/version-share-download.spec.ts
