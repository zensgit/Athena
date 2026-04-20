# P5 PR-86 PR-83 Review And Name-Reuse Fix Verification

Date: 2026-04-20

## Scope Verified

- `PR-83` milestone claim was reviewed at the code level, not just from CI status
- the one blocking correctness issue found in `PR-83` has been fixed locally
- current CI milestone state has been rechecked against GitHub Actions metadata

## Code Review Outcome

Reviewed files:

- `ecm-core/src/main/java/com/ecm/core/controller/RmReportPresetController.java`
- `ecm-core/src/main/java/com/ecm/core/service/RmReportPresetService.java`
- `ecm-core/src/main/java/com/ecm/core/entity/RmReportPreset.java`
- `ecm-core/src/main/java/com/ecm/core/repository/RmReportPresetRepository.java`
- `ecm-core/src/main/resources/db/changelog/changes/082-create-rm-report-presets.xml`
- `ecm-core/src/test/java/com/ecm/core/service/RmReportPresetServiceTest.java`

Finding confirmed:

- soft-delete followed by recreate with the same preset name would collide with the unconditional `(owner, name)` database uniqueness rule

Follow-up fix confirmed:

- deleted presets now rewrite their stored `name`
- the original visible name becomes reusable for the same owner

## Local Validation

Command:

```bash
cd ecm-core && ./mvnw -B -Dstyle.color=never test -Dtest=RmReportPresetServiceTest
```

Result:

- `BUILD SUCCESS`
- `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`

Static check:

```bash
git diff --check
```

Result:

- passed

## CI Milestone Recheck

### Confirmed historical milestone

GitHub Actions run `24668239066`:

- `Backend Verify`: `success`
- `Frontend Build & Test`: `success`
- `Phase C Security Verification`: `success`
- `Acceptance Smoke (3 admin pages)`: `success`
- `Frontend E2E Core Gate`: `success`
- `Phase 5 Mocked Regression Gate`: `cancelled`

This supports the previously claimed milestone:

- 5 core jobs green
- the remaining job was superseded, not failed

### Current rerun state

GitHub Actions run `24669169102` on `16648b3ef979b607875e8227da653ba9e6a0afce`:

- `Backend Verify`: `success`
- `Frontend Build & Test`: `success`
- `Phase C Security Verification`: `success`
- `Acceptance Smoke (3 admin pages)`: `success`
- `Frontend E2E Core Gate`: `success`
- `Phase 5 Mocked Regression Gate`: `in_progress`

### Current PR-83 run state

GitHub Actions run `24669859344` on `b44ea1848e7a98fc69649bf94d4a94668551b9d3`:

- `Backend Verify`: `success`
- `Frontend Build & Test`: `success`
- `Phase C Security Verification`: `success`
- `Acceptance Smoke (3 admin pages)`: `success`
- `Frontend E2E Core Gate`: `success`
- `Phase 5 Mocked Regression Gate`: `in_progress`

## Verification Outcome

- the major milestone claim is confirmed
- `PR-83` is not blocked by a new broad review failure
- one real persistence correctness issue was found and fixed locally
- final closeout for the active CI runs still depends on the remaining mocked regression gate finishing
