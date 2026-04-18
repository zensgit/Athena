# P4 PR-41 to PR-46 Staging Validation

## Goal

Validate the RM analytics lane from `PR-41` through `PR-46` on a realistic environment before treating it as a merged milestone.

## Preconditions

- backend deployed with `PR-41 ~ PR-46`
- frontend deployed with matching RM page changes
- audit data exists for at least:
  - declarations
  - undeclarations
  - category assignments
  - governance-change RM events
  - at least one `OTHER` RM audit event
- admin user available

## Validation Areas

### 1. Records Audit Baseline

- open RM admin page
- confirm `Records Audit` loads without analytics interaction
- verify filters still work:
  - `Family`
  - `Event Type`
  - `Username`
  - `From`
  - `To`
- verify `Family` includes `Other`

### 2. PR-41 Range Drilldown

- from an analytics card that uses date ranges, open audit drilldown
- verify:
  - `from` and `to` are both populated
  - audit banner matches the clicked source
  - results stay within the requested closed interval

### 3. PR-42 Contributors

- verify `RM Activity Contributors` loads
- click a contributor row
- confirm audit is filtered by:
  - `username`
  - current analytics window range

### 4. PR-43 Contributor Family Drilldown

- click contributor family counters
- confirm audit is filtered by:
  - `family`
  - `username`
  - window range

### 5. PR-44 Event Hotspots

- verify `RM Activity Event Hotspots` loads
- click `Review event audit`
- confirm audit is filtered by:
  - `eventType`
  - window range
- verify no `username` is injected

### 6. PR-45 Family Mix

- verify `RM Activity Family Mix` loads
- confirm at least one staging environment row can show `Other`
- click `Review family audit`
- confirm audit is filtered by:
  - `family`
  - window range
- confirm `family=OTHER` returns evidence if such data exists

### 7. PR-46 Family Highlights

- verify `RM Activity Family Highlights` loads
- confirm current and previous windows are shown
- click:
  - `Review current audit`
  - `Review previous audit`
- confirm both open the same audit table with:
  - same `family`
  - different date ranges

## Failure Isolation Checks

These checks matter because the analytics slices were intentionally isolated:

- simulate or observe one analytics endpoint failing
- confirm RM page core surfaces still remain usable:
  - summary cards
  - file plans
  - categories
  - records table
  - records audit

## Data Quality Checks

- compare at least one clicked analytics count against returned audit rows or a paginated subset
- verify `OTHER` family only contains RM audit rows outside the named four families
- verify no analytics drilldown opens an empty second evidence surface

## Signoff Criteria

The lane is ready to merge as a milestone if:

- all analytics cards load
- all drilldowns land in `Records Audit`
- `Other` works end-to-end
- no analytics card failure breaks the RM page
- no obvious range mismatch or stale evidence is observed
