# UI Regression Report - 2025-12-20

## Scope
Run end-to-end UI smoke tests after auth/search/preview optimizations.

## Test Command
- `npx playwright test e2e/ui-smoke.spec.ts`

## Results
- ✅ 8/8 tests passed

## Coverage Highlights
- Browse + upload + search + copy/move + facets + delete + rules
- PDF upload + search + version history + edit online
- RBAC: editor vs viewer access
- Rule automation: auto-tag on upload
- Scheduled rules: CRUD + cron validation + UI configuration
- Security: MFA guidance + audit export + retention
- Antivirus: EICAR flow (skips when ClamAV unavailable)

## Notes
- ClamAV reported `enabled=true` but `available=false`, so EICAR test was skipped as designed.
