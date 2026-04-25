# P5 PR-144D RM Notification Preflight CI Portability Fix Design

## Goal

Make the RM notification closeout preflight run on the GitHub-hosted frontend runner without requiring extra host tools.

## Problem

GitHub Actions run `24935937705` failed in `Frontend Build & Test` at the `Run RM notification closeout preflight` step.

The job log showed:

```text
scripts/p5-rm-notification-closeout-preflight.sh: line 18: rg: command not found
p5_rm_notification_closeout_preflight: missing CI workflow wiring: Run RM notification closeout preflight
```

The preflight script worked locally because `rg` was installed, but the CI workflow did not install ripgrep. Adding a CI install step would make the fast preflight slower and add another external dependency.

## Change

`scripts/p5-rm-notification-closeout-preflight.sh` no longer depends on `rg`.

Replacement strategy:

- workflow line lookup uses `awk`
- fixed-string file checks use `grep -F`
- regex assertion checks use `grep -E`
- backend Java test source lookup uses `find` plus `grep -E`

This keeps the preflight within tools already available on the Ubuntu GitHub-hosted runner.

## Boundaries

- this fixes the fast frontend preflight failure mode only
- this does not change workflow semantics
- this does not replace the live Docker-backed acceptance gate
- this does not promote the notification lane to accepted
- this keeps `PR-145` reserved for email delivery after P0 acceptance
