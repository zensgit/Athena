# P5 PR-92: RM Report Preset Execute Endpoint Verification

## Implemented

- Added `POST /api/v1/records/report-presets/{presetId}/execute`
- Reused existing RM report controller methods for execution
- Reused existing CSV builders for report DTO kinds
- Added explicit bad-request handling for:
  - missing required preset params
  - invalid datetime/integer preset params
  - `csv` requested for summary-only preset kinds

## Tests

Ran:

```bash
cd ecm-core
./mvnw -B -Dstyle.color=never test -Dtest=RecordsManagementControllerTest,RmReportPresetServiceTest
```

Result:

- `BUILD SUCCESS`
- `Tests run: 80, Failures: 0, Errors: 0, Skipped: 0`

Added / verified execute-specific coverage in `RecordsManagementControllerTest`:

- execute preset returns JSON for family report kind
- execute preset returns CSV for contributor event-type report kind
- execute preset rejects CSV for summary-only kinds
- execute preset rejects missing required datetime params

## Static checks

Ran:

```bash
git diff --check
```

Result:

- passed

## Residual limits

- execute still returns existing report DTOs only; it does not persist any run
  history
- `ACTIVITY_FAMILY_HIGHLIGHTS` and `ACTIVITY_FAMILY_MIX` remain JSON-only
- scheduled execution / delivery remains deferred to a later slice
