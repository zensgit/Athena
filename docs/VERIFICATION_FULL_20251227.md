# Full Verification Report (2025-12-27)

## Scope
- Full local verification using `scripts/verify.sh` (restart + health + token + smoke + frontend build + WOPI + E2E).

## Result
- Status: PASS
- Summary: 11 passed, 0 failed, 0 skipped

## Command
- `bash scripts/verify.sh`

## Log Prefix
- `tmp/20251227_161246_*`

## Notes
- E2E RBAC viewer test updated to click the exact "View" button to avoid strict-mode match with "View Online".
