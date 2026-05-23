# WorkflowController Response-Contract Tests

Date: 2026-05-23

## Context

This slice continues the backend response-contract track after the
OpsRecoveryController async-export follow-up. The TODO identifies
WorkflowController as a remaining Top 10 group with broad frontend consumption
from `workflowService`, `TasksPage`, `WorkflowProcessesPage`, and
`StartWorkflowDialog`.

## Scope

Added:

- `ecm-core/src/test/java/com/ecm/core/controller/WorkflowControllerResponseContractTest.java`

Covered JSON endpoints:

- `GET /api/v1/workflows/definitions`
- `GET /api/v1/workflows/tasks/inbox`
- `GET /api/v1/workflows/tasks/{taskId}`
- `GET /api/v1/workflows/processes/browser`
- `GET /api/v1/workflows/processes/{processId}`

Out of scope:

- Binary diagram endpoints.
- Start/submit/update/claim/cancel/delete mutation endpoints.
- Form model endpoints.
- Process task-history/activity/involved/variable/item child endpoints.
- Document workflow history endpoint.
- Controller implementation changes.
- Frontend changes.

## Design

The test uses standalone `MockMvc` with a mocked `WorkflowService`. This is the
same lightweight test shape used by the existing `WorkflowControllerTest`; this
contract slice does not need Spring Security context or async worker behavior.

The slice locks these wire DTOs:

- `ProcessDefinitionResponse`
- `TaskInboxItemResponse`
- `TaskDetailResponse`
- `ProcessBrowserListResponse`
- `ProcessBrowserItemResponse`
- `PagingResponse`
- `ProcessDetailResponse`
- `WorkflowSubmissionSummaryResponse`

The tests lock:

- task inbox nullable fields (`assignee`, `owner`, `delegationState`,
  `dueDate`, `completedAt`) as explicit JSON nulls;
- task detail nullable fields (`owner`, `delegationState`, `dueDate`,
  `processDefinitionSuspended`) as explicit JSON nulls;
- open workflow submission fields (`decision`, `decisionLabel`, `reviewedBy`,
  `reviewedAt`, `comment`) as explicit JSON nulls;
- process browser paging envelope fields;
- process browser/process detail `endTime` and nullable `submissionSummary`
  behavior;
- exact JSON field-name order for every covered DTO/envelope.

## Verification

Local static hygiene:

```bash
git diff --check -- . ':!.env'
```

Result: passed.

Targeted Maven test:

```bash
cd ecm-core
./mvnw -Dtest=WorkflowControllerResponseContractTest test
```

Result: blocked by the local environment before Maven startup:

```text
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock; check if the path is correct and if the daemon is running: dial unix /Users/chouhua/.docker/run/docker.sock: connect: no such file or directory
```

CI is the authoritative execution gate for this slice.
