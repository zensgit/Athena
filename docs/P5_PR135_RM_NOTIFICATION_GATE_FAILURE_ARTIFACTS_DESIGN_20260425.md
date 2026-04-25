# P5 PR-135 RM Notification Gate Failure Artifacts Design

## Goal

Make CI failures in the RM notification acceptance gate easier to diagnose when they occur during backend targeted tests.

## Problem

`frontend_e2e_core` already uploads Playwright output, test results, and Docker logs on failure. After the RM notification gate was attached, the same job can now fail during backend targeted Maven tests before Playwright starts.

Without Surefire reports in the artifact bundle, a backend targeted-test failure would require digging through raw job logs instead of downloading structured test reports.

## Change

The `frontend_e2e_core` failure artifact bundle now includes:

```text
ecm-core/target/surefire-reports
```

## Boundaries

- no runtime behavior changed
- no test selection changed
- no CI job topology changed
- no artifact name changed
