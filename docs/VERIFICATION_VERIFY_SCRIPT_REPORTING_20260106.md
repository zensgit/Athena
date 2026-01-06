# Verification: verify.sh Reporting (2026-01-06)

- Command: `ECM_VERIFY_QUERY=__wopi_auto_upload_<epoch> ECM_VERIFY_CLEANUP=1 bash scripts/verify.sh --wopi-only`
- Result: pass (3 passed, 0 failed, 7 skipped). Wrote report + WOPI summary artifacts.
- Report: `tmp/20260106_232258_verify-report.md`
- WOPI summary: `tmp/20260106_232258_verify-wopi.summary.log`
- Logs: `tmp/20260106_232258_*`
