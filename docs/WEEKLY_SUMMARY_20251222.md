# Weekly Summary (2025-12-22)

## Highlights
- Scheduled rule execution now produces audit entries and is validated in smoke tests.
- PDF preview is resilient: client PDF failures fall back to server-rendered previews.
- Phase C security checks expanded with share-link password, access-limit, IP restriction, expiry, and deactivation coverage.

## Verification
- `./scripts/verify.sh --no-restart --smoke-only --skip-build --skip-wopi`
  - Evidence: `tmp/20251222_082247_smoke-test.log`
  - Evidence: `tmp/20251222_083501_verify-phase-c.log`
- `npx playwright test e2e/pdf-preview.spec.ts`
  - Evidence: Playwright output `2 passed`
- `ECM_TOKEN_FILE=tmp/admin.access_token ./scripts/smoke.sh`
  - Evidence: `tmp/20251222_130230_smoke.log`
- `npx playwright test e2e/pdf-preview.spec.ts`
  - Evidence: `tmp/20251222_130300_e2e-pdf-preview.log`
- `./scripts/verify.sh --no-restart --skip-build`
  - Evidence: `tmp/20251222_132714_verify.log`
  - Evidence: `tmp/20251222_132715_e2e-test.log` (partial; CLI timeout during E2E)
- `npx playwright test`
  - Evidence: `tmp/20251222_133014_e2e-full.log`

## New/Updated Docs
- `docs/PHASE_C_STEP3_DESIGN.md`
- `docs/PHASE_C_STEP3_VERIFICATION.md`
- `docs/PHASE_C_STEP4_DESIGN.md`
- `docs/PHASE_C_STEP4_VERIFICATION.md`
- `docs/PHASE_C_STEP5_DESIGN.md`
- `docs/PHASE_C_STEP5_VERIFICATION.md`
- `docs/PREVIEW_STABILITY_STEP1_DESIGN.md`
- `docs/PREVIEW_STABILITY_STEP1_VERIFICATION.md`
- `docs/PREVIEW_STABILITY_STEP2_DESIGN.md`
- `docs/PREVIEW_STABILITY_STEP2_VERIFICATION.md`
- `docs/ANTIVIRUS_UPLOAD_SCAN_VERIFICATION.md`
- `docs/SMOKE_E2E_VERIFICATION_20251222.md`

## Notes / Next Steps
- When CAD preview service is available, re-run `scripts/smoke_test_cad_preview.sh` and attach output to docs.
