# Phase 369BM: Remote Transfer Receiver/Auth Hardening

## Goal

Harden the remote outbound transfer seam by introducing dedicated receiver endpoints and transfer-specific opaque authentication, instead of calling generic folder/document APIs with generic HTTP auth headers.

## Scope

- Add dedicated transfer receiver endpoints under `/api/v1/transfer/receiver/**`
- Permit those endpoints in Spring Security without routing transfer secrets through JWT bearer decoding
- Validate inbound transfer requests against configured `TransferTarget` credentials
- Restrict receiver operations to the configured target root folder subtree
- Reuse existing folder/document creation pipelines via a temporary internal admin security context
- Update outbound `ATHENA_HTTP` transfer client to use the receiver contract
- Add focused service/controller/client tests

## Design

### Security model

- Receiver endpoints are `permitAll` at the filter chain level
- JWT bearer resolution explicitly ignores `/api/v1/transfer/receiver/**`
- Receiver auth uses opaque custom headers:
  - `X-Athena-Transfer-User`
  - `X-Athena-Transfer-Secret`
- `TransferTarget.AuthType` continues to drive credential shape:
  - `NONE`: no receiver headers
  - `BASIC`: user + secret headers
  - `BEARER`: secret header only

### Receiver authorization

- The receiver matches inbound credentials against enabled local `ATHENA_HTTP` transfer targets
- The requested parent/verify folder must be equal to or under the matched target's `targetFolderId`
- Requests outside the authorized subtree fail with `403`

### Execution model

- `TransferReceiverService` pushes a synthetic admin `SecurityContext`
- Existing services remain the source of truth:
  - `FolderService.createFolder(...)`
  - `DocumentUploadService.uploadDocument(...)`
- This avoids duplicating permission, pipeline, and event logic

### Collision handling

- Folder/document name collisions are resolved on the receiver side
- Duplicate names become:
  - `name (Replica 1)`
  - `file (Replica 1).ext`
- The outbound HTTP client no longer lists remote folder contents to do name probing

## Files

- `ecm-core/src/main/java/com/ecm/core/config/SecurityConfig.java`
- `ecm-core/src/main/java/com/ecm/core/controller/TransferReceiverController.java`
- `ecm-core/src/main/java/com/ecm/core/service/transfer/TransferReceiverService.java`
- `ecm-core/src/main/java/com/ecm/core/service/transfer/TransferReceiverHeaders.java`
- `ecm-core/src/main/java/com/ecm/core/service/transfer/AthenaTransferHttpClient.java`
- `ecm-core/src/test/java/com/ecm/core/service/transfer/TransferReceiverServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/TransferReceiverControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/service/transfer/AthenaTransferHttpClientTest.java`
- `ecm-core/src/test/java/com/ecm/core/service/TransferReplicationServiceTest.java`

## Out of scope

- Receiver-side remote push scheduling/orchestration changes
- New persistent receiver credential model beyond `TransferTarget`
- Full bidirectional transfer protocol
- Remote inbound tenancy scoping rollout
