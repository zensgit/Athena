# Final Regression Summary

Date: 2026-01-04 (local)

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
- Frontend E2E: full Playwright run passed (15 tests).
- Backend tests: `mvn test` passed (17 tests).

## Commands Run
- `scripts/smoke.sh`
- `npm run e2e`
- `docker compose restart clamav`
- `scripts/verify.sh --no-restart --smoke-only --skip-build`
- `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test`
- `docker run --rm -v "$(pwd)":/workspace -v "$HOME/.m2":/root/.m2 -w /workspace/ecm-core maven:3-eclipse-temurin-17 mvn test`

## Artifacts
- Primary report: `docs/PLAN_DAY5_REGRESSION_REPORT.md`
- Playwright traces/screenshots: `ecm-frontend/test-results/` (see latest run)

## Notes
- ClamAV was initially unavailable; after restart it reported healthy and EICAR rejection succeeded.
