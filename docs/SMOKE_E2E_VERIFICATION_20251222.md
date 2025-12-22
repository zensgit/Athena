# Smoke + E2E Verification Report (2025-12-22)

## Environment
- ECM API: http://localhost:7700
- ECM UI: http://localhost:5500
- Keycloak: http://localhost:8180

## Runs

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

## Notes
- ClamAV was restarted prior to verification and is now healthy.
- EICAR upload rejection confirms antivirus scan enforcement in the upload pipeline.
