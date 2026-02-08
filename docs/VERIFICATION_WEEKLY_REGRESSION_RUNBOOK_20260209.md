# Verification: Weekly Regression Runbook (2026-02-09)

This document records a verification run of the weekly regression entry point.

## Run (Fast Gate)

Command executed:

```bash
./scripts/verify.sh --no-restart --smoke-only --skip-wopi --skip-build
```

Purpose:

- Verify core services are healthy
- Validate API smoke tests
- Validate Phase C security verification

Result:

- Status: **PASSED**
- Steps: `Passed=6`, `Failed=0`, `Skipped=4`
- Artifacts (local):
  - Report: `tmp/20260209_035841_verify-report.md`
  - Logs: `tmp/20260209_035841_*.log`

## Recommended Weekly Full Run

For the complete weekly regression (includes WOPI + full Playwright E2E):

```bash
./scripts/verify.sh
```

