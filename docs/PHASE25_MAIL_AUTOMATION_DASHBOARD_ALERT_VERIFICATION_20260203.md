# Phase 25 Mail Automation Dashboard Alert Verification (2026-02-03)

## Environment
- UI: `http://localhost:3000`
- API: `http://localhost:7700`

## Verification Steps
1. Open Admin Dashboard.
2. Trigger a mail fetch with an intentional error (or use existing failing account).
3. Confirm the Mail Automation card shows the warning line.

## Result
- ✅ Warning line appears when errors are present.
