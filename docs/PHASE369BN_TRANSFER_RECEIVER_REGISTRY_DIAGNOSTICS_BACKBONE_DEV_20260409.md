# Phase 369BN: Transfer Receiver Registry And Diagnostics Backbone

## Goal

Decouple inbound transfer receiver authentication from outbound transfer targets by introducing a dedicated receiver registry, while adding minimal verification and last-access diagnostics for receiver operations.

## Scope

- Add a persistent inbound receiver registry under `transfer_receivers`
- Move receiver credential matching from `TransferTarget` to `TransferReceiverRegistration`
- Add admin APIs to create, update, list, verify, and delete receiver registrations
- Persist receiver verification status and last access diagnostics
- Keep the existing `/api/v1/transfer/receiver/**` contract and outbound `ATHENA_HTTP` client intact
- Add focused service/controller tests

## Design

### Receiver registry model

- `TransferReceiverRegistration` stores:
  - root folder boundary
  - auth mode and credentials
  - enabled flag
  - verification status/message/timestamp
  - last access status/message/timestamp
- The registry is independent from outbound `TransferTarget`
- Receiver auth no longer depends on local outbound replication configuration

### Diagnostics model

- Admin verify uses `FolderService.getFolder(...)` to validate the configured root folder
- Receiver runtime access stores:
  - `SUCCESS` when verify/create/upload completes
  - `FAILED` when credentials match but the requested folder falls outside the configured subtree
- Diagnostics stay lightweight and local to the registry row

### Service boundaries

- `TransferReceiverRegistryService` owns admin CRUD and verify operations
- `TransferReceiverService` owns runtime auth and receiver-side folder/document execution
- Existing execution path remains unchanged:
  - receiver auth resolves an authorized root folder
  - folder/document creation still runs through existing services with the synthetic internal admin context

## Files

- `ecm-core/src/main/java/com/ecm/core/entity/TransferReceiverRegistration.java`
- `ecm-core/src/main/java/com/ecm/core/repository/TransferReceiverRegistrationRepository.java`
- `ecm-core/src/main/java/com/ecm/core/service/TransferReceiverRegistryService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/TransferReceiverRegistryController.java`
- `ecm-core/src/main/java/com/ecm/core/service/transfer/TransferReceiverService.java`
- `ecm-core/src/main/resources/db/changelog/changes/062-create-transfer-receiver-registry-table.xml`
- `ecm-core/src/test/java/com/ecm/core/service/TransferReceiverRegistryServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/TransferReceiverRegistryControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/service/transfer/TransferReceiverServiceTest.java`

## Out of scope

- Retry/backoff orchestration for outbound transfer jobs
- Receiver-side tenant scoping
- Encrypted secret storage or external secret managers
- Frontend/operator surface for receiver registry
