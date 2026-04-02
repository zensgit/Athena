# Alfresco Borrowable Features and Athena Parallel Delivery Plan

## Date
2026-03-06

## Scope
Deep-read benchmark against:
- `/reference-projects/alfresco-community-repo`

## High-value borrowable patterns

### 1) Explicit retry protocol signal
- Alfresco reference:
  - `org/alfresco/transform/registry/CombinedConfig.java`
- Pattern:
  - use response header signal (`X-Alfresco-Retry-Needed`) instead of guessing from error strings.
- Athena status:
  - Implemented in Phase157 for CAD render responses.

### 2) Failover transform chain
- Alfresco reference:
  - `org/alfresco/repo/content/transform/LocalFailoverTransform.java`
- Pattern:
  - cascade through multiple engines until one succeeds, preserving first useful failure.
- Athena plan:
  - Day3 implement transform-engine chain abstraction for preview conversion.

### 3) Operator debug log by request id
- Alfresco reference:
  - `org/alfresco/repo/content/transform/TransformerDebugLog.java`
- Pattern:
  - keep compact request-id grouped trace for admin troubleshooting.
- Athena status:
  - Implemented in Phase160:
    - backend trace ring buffer + request-id diagnostics API
    - frontend trace panel with request-id filtering

### 4) Failure policy profiles
- Alfresco reference:
  - `org/alfresco/repo/thumbnail/FailureHandlingOptions.java`
  - `org/alfresco/service/cmr/thumbnail/FailedThumbnailInfo.java`
- Pattern:
  - separate retry count, quiet period, and failure metadata.
- Athena status:
  - Implemented in Phase161:
    - profile registry (`default/cad/pdf/office/image/text`)
    - queue retry/backoff/quiet-period policy execution
    - admin policy list/update API + UI editing panel

### 5) Rendition prevention markers
- Alfresco reference:
  - `org/alfresco/repo/rendition/RenditionPreventionRegistry.java`
- Pattern:
  - block futile re-rendition on known blocked classes/aspects.
- Athena status:
  - Implemented in Phase162:
    - prevention registry + queue auto-block/auto-unblock behavior
    - admin unblock / unblock+requeue APIs
    - diagnostics UI prevention panel + mocked e2e coverage

## What Athena already exceeds now
- Backend reason-scope batch action endpoint with bounded guardrails and aggregated operator feedback.
- UI top-reason actions no longer limited to the currently loaded list.
- Retry decision supports explicit protocol hint and existing category classifier.

## Parallel implementation tracks (next)
- Track A: Transform engine failover + prevention markers.
- Track B: Operator diagnostics UX (trace panel, policy panel, bulk recovery UX).
- Track C: Verification matrix (controller security, queue semantics, mocked E2E, release gate integration).
