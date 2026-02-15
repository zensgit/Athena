# Phase 56 - Permission-Set UX Parity (Verification) - 2026-02-14

## Summary

Verification target:

- Permissions dialog shows:
  - Preset legend ("Permission presets (Alfresco-style)")
  - Per-principal “Effective preset” column
  - Correct labeling for exact preset vs custom subset ("Custom (Closest: Editor)")

Primary verification method:

- Playwright mocked E2E (no backend required)

## Preconditions

- Node/npm available (for `npx`)
- Frontend deps installed in `ecm-frontend/`

## Automated Verification (Mocked E2E)

### 1) Build UI

```bash
cd ecm-frontend
npm run build
```

### 2) Serve static build

```bash
python3 -m http.server 5500 --directory build
```

Keep that process running in a separate terminal.

### 3) Run Playwright test

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 npx playwright test \
  e2e/permissions-dialog-presets.mock.spec.ts \
  --project=chromium --workers=1
```

Expected assertions:

- The dialog renders legend text:
  - `Permission presets (Alfresco-style)`
- Users tab rows:
  - `preset-editor` => `Editor`
  - `custom-user` => `Custom` + `Closest: Editor`
  - `preset-consumer` => `Consumer`
- Groups tab rows:
  - `contributors` => `Contributor`

Result:

- PASS (Playwright mocked E2E)

## Manual Spot Check (Optional)

1. Open the UI.
2. Navigate to any node in File Browser.
3. Open actions menu => `Permissions`.
4. Confirm:
   - The preset legend appears above the Users/Groups tabs.
   - “Effective preset” column shows either a preset chip or `Custom` + `Closest: ...`.

