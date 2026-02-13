# Phase 1 P65 - Weekly Regression + Release Gate (Design) - 2026-02-09

## Goal

Provide a repeatable “one command” verification entry point that can act as:

- A fast local gate before merging changes
- A weekly regression runbook (including E2E and integrations like WOPI)

## Approach

Use the existing verification harness:

- `scripts/verify.sh`
  - Supports fast modes (`--no-restart`, `--smoke-only`, `--skip-wopi`, `--skip-build`)
  - Writes consolidated logs + markdown reports to `tmp/`
  - Optionally runs WOPI and Playwright E2E

Additionally, keep smaller focused checks as building blocks:

- API smoke: `scripts/smoke.sh`
- Focused E2E specs under `ecm-frontend/e2e/`

## References / Primary Docs

- Verification record: `docs/VERIFICATION_WEEKLY_REGRESSION_RUNBOOK_20260209.md`
- Harness: `scripts/verify.sh`

