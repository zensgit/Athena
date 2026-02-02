# Phase 1 P1 - Version Label Policy (Development)

Date: 2026-01-31

## Status
Already implemented in core; added unit coverage for semantic and calendar policies.

## Implementation Overview
- `VersionLabelService` supports:
  - `semantic`/`semver` (major.minor from document)
  - `calendar` (date format with optional sequence)
- Configuration:
  - `ecm.versioning.label-policy` (default: `semantic`)
  - `ecm.versioning.calendar.format` (default: `yyyy.MM.dd`)
  - `ecm.versioning.calendar.include-sequence` (default: `true`)
  - `ecm.versioning.calendar.sequence-separator` (default: `.`)

## Files Touched
- `ecm-core/src/test/java/com/ecm/core/service/VersionLabelServiceTest.java`
