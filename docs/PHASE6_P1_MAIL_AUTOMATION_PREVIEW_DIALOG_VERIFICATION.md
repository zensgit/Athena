# Phase 6 P1 Mail Automation Preview Dialog - Verification

## Date
2026-02-15

## Environment
- UI: `http://localhost`
- API: mocked in Playwright route handlers (no backend dependency)

## Automated Test

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost npx playwright test \
  e2e/mail-automation-phase6-p1.mock.spec.ts \
  --project=chromium --workers=1
```

## Verified Scope
- Preview dialog opens from rule row action (`Preview`).
- `Run Preview` renders summary chips and skip reason chips.
- Skip reason labels are human-readable (`already processed`, `mailbox readonly`).
- `Processable` filter switches between:
  - all matches
  - processable-only
  - not-processable
- `Copy JSON` writes preview payload to clipboard and shows success toast.

## Result
- PASS (2026-02-15): `1 passed`
