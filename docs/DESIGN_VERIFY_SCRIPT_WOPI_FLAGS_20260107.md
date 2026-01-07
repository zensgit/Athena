# Design: verify.sh WOPI Flags (2026-01-07)

## Goal
- Provide CLI flags for controlling WOPI verification without exporting env vars.

## Approach
- Add `--wopi-query=<query>` to set `ECM_VERIFY_QUERY` for the WOPI step.
- Add `--wopi-cleanup` to set `ECM_VERIFY_CLEANUP=1` for auto-upload cleanup.
- Pass the overrides only for the `verify-wopi` command invocation.

## Files
- scripts/verify.sh
