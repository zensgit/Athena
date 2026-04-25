# P5 PR-141 RM Notification Closeout Preflight Script Design

## Goal

Make the locally provable RM notification closeout checks repeatable with one command.

## Problem

After PR-139, the final acceptance proof is still GitHub Actions. However, several local preconditions are useful before pushing or asking a collaborator to observe CI:

- workflow YAML remains parseable
- gate script remains syntactically valid
- Playwright discovery still finds exactly four notification acceptance flows
- the notification acceptance spec does not regress to bare `response.ok()` assertions
- People preference service contract tests pass
- Records Management preference rollback tests pass

Running these manually is error-prone and easy to report inconsistently.

## Change

Add:

```bash
scripts/p5-rm-notification-closeout-preflight.sh
```

The script runs only checks that do not require Docker, live backend services, or GitHub Actions API access.

## Checks

The preflight performs:

- `.github/workflows/ci.yml` YAML parse
- `scripts/p5-rm-notification-acceptance-gate.sh` syntax check
- bare `response.ok()` assertion scan in `rm-report-preset-schedule.spec.ts`
- `npm run e2e:rm-notification:acceptance -- --list` with exact count check for four tagged tests
- `peopleService.test.ts`
- targeted Records Management notification preference rollback tests

## Boundaries

- this script does not run the live acceptance gate
- this script does not promote the notification lane to accepted
- this script does not require Docker or GitHub network access
- no runtime code changed
