# Verification: verify.sh WOPI Flags (space form, 2026-01-07)

- Command: `bash scripts/verify.sh --wopi-only --wopi-cleanup --wopi-query __wopi_flag_space_<epoch>`
- Result: pass (3 passed, 0 failed, 7 skipped, exit code 0). Auto-uploaded and cleaned up sample.
- Report: `tmp/20260107_103902_verify-report.md`
- WOPI summary: `tmp/20260107_103902_verify-wopi.summary.log`
- Logs: `tmp/20260107_103902_*`
