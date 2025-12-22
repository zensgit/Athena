# Smoke + E2E Verification Report (2025-12-22)

## Environment
- ECM API: http://localhost:7700
- ECM UI: http://localhost:5500
- Keycloak: http://localhost:8180

## Runs

### 0) One-click Verify (partial)
- Command: `./scripts/verify.sh --no-restart --skip-build`
- Result: ✅ Steps 1-6.5 completed; Step 7 (E2E) continued separately due to CLI timeout
- Evidence: `tmp/20251222_132714_verify.log`
- E2E partial log: `tmp/20251222_132715_e2e-test.log`

### 0b) One-click Verify (full)
- Command: `./scripts/verify.sh --no-restart --skip-build`
- Result: ✅ Passed (9 passed, 0 failed, 2 skipped)
- Evidence: `tmp/20251222_133958_verify-full.log`
- E2E log: `tmp/20251222_133959_e2e-test.log`

### 1) API Smoke
- Command: `ECM_TOKEN_FILE=tmp/admin.access_token ./scripts/smoke.sh`
- Result: ✅ Passed (end-to-end workflow, rules, WOPI, search, tags/categories, trash)
- Evidence: `tmp/20251222_130230_smoke.log`

Key checkpoints from the run:
- Health, metrics, system status, license OK
- Antivirus enabled + EICAR rejection (HTTP 400)
- Rule automation + scheduled rule trigger + audit logs OK
- WOPI: CheckFileInfo/GetFile/Lock/PutFile/Unlock OK
- Search, saved searches, facets, favorites, share link OK
- Workflow approval OK
- Trash move/restore OK

### 2) UI E2E (PDF Preview)
- Command: `npx playwright test e2e/pdf-preview.spec.ts`
- Result: ✅ 2/2 passed
- Evidence: `tmp/20251222_130300_e2e-pdf-preview.log`

### 3) UI E2E (Full Suite)
- Command: `npx playwright test`
- Result: ✅ 12/12 passed
- Evidence: `tmp/20251222_133014_e2e-full.log`

## Notes
- ClamAV was restarted prior to verification and is now healthy.
- EICAR upload rejection confirms antivirus scan enforcement in the upload pipeline.
