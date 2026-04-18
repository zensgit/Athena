# P4 PR-72 RM Activity Breakdown Full-Window Drilldown Verification

## Scope Verified

- `RM Activity Breakdown` exposes a card-level full-window audit shortcut
- clicking the shortcut drills into the existing `Records Audit` table
- drilldown uses the first visible bucket `fromDay` and the last visible bucket `toDay` as a closed interval

## Commands

### Page regression

```bash
cd ecm-frontend
CI=true npm test -- --watchAll=false --runInBand --runTestsByPath src/pages/RecordsManagementPage.test.tsx --testNamePattern="full breakdown shortcut" --forceExit
```

Result:

- `Test Suites: 1 passed, 1 total`
- `Tests: 1 passed, 62 skipped, 63 total`

### Production build

```bash
cd ecm-frontend
npm run build
```

Result:

- build passed
- existing repo warnings remain unchanged:
  - `src/components/share/ShareLinkManager.tsx`
  - `src/pages/AdminDashboard.tsx`

### Static diff check

```bash
git diff --check
```

Result:

- passed

## Notes

- `Claude Code CLI` can be attempted in principle, but this machine still reports `Not logged in · Please run /login`, so this slice was implemented and verified locally
