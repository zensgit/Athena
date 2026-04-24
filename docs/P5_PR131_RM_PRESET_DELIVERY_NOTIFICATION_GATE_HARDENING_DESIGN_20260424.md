# P5 PR-131 RM Preset Delivery Notification Gate Hardening Design

## Goal

Make the PR-130 notification acceptance gate harder to accidentally under-run.

## Problems Addressed

- the frontend acceptance script selected tests by long title regex
- changing a test title could silently remove a case from the gate
- the backend test list covered delivery/controller/security behavior but not the activity and inbox materialization tests that own the notification pipeline

## Changes

- add `@rm-notification-acceptance` to the four full-stack notification Playwright tests
- change `e2e:rm-notification:acceptance` to grep the stable tag
- expand backend gate defaults to include:
  - `RmReportPresetDeliveryServiceTest`
  - `RmReportPresetControllerTest`
  - `RmReportPresetControllerSecurityTest`
  - `ActivityServiceTest`
  - `NotificationInboxServiceTest`

## Non-Goals

- no CI workflow attachment in this slice
- no full-stack service bootstrap automation
- no test behavior change beyond title tagging
- no runtime endpoint, table, or migration change

## Subsequent CI Attachment

`PR-132` attaches the hardened gate to GitHub Actions. The `no CI workflow attachment` boundary above remains accurate for the original `PR-131` slice.
