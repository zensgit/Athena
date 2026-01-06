# Verification: WOPI Auto-Upload Fallback (2026-01-06)

- `bash scripts/verify.sh --wopi-only`
- Result: pass (verify-wopi). Auto-uploaded `verify-wopi-sample.xlsx` when no XLSX document was found.
- Logs: `tmp/20260106_224544_verify-wopi.log`

## Re-run after cleanup
- `bash scripts/verify.sh --wopi-only`
- Result: pass (verify-wopi). Used existing XLSX document after index rebuild.
- Logs: `tmp/20260106_230821_verify-wopi.log`

## Auto-upload with cleanup flag
- `ECM_VERIFY_QUERY=__wopi_auto_upload_<epoch> ECM_VERIFY_CLEANUP=1 bash scripts/verify.sh --wopi-only`
- Result: pass (verify-wopi). Auto-uploaded `verify-wopi-sample.xlsx` and deleted it after verification.
- Logs: `tmp/20260106_232258_verify-wopi.log`
