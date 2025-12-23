# Final Regression Summary

Date: 2025-12-23 (local)

## Scope
- Search stale result guard + index cleanup.
- WOPI "Edit Online" menu availability.
- Antivirus (EICAR) validation.
- API + UI regression coverage.

## Results
- Search stale result guard: smoke-verified and documented in `docs/PLAN_DAY5_REGRESSION_REPORT.md`.
- WOPI menu gating: updated frontend to allow Edit Online for PDF/TXT/CSV; Playwright rerun passed.
- Antivirus: ClamAV healthy; EICAR upload correctly rejected (HTTP 400).
- API smoke: full `scripts/smoke.sh` run passed with all core flows (upload/search/tag/category/workflow/trash).

## Commands Run
- `scripts/smoke.sh`
- `npm run e2e`
- `docker compose restart clamav`
- `scripts/verify.sh --no-restart --smoke-only --skip-build`

## Artifacts
- Primary report: `docs/PLAN_DAY5_REGRESSION_REPORT.md`
- Playwright traces/screenshots: `ecm-frontend/test-results/` (see latest run)

## Notes
- ClamAV was initially unavailable; after restart it reported healthy and EICAR rejection succeeded.
