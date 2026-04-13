# P0A PR-3 Check-in Fixup Design

## Context

`PR-3` 的目标是关闭 `P0A-4 Check-in Creates Version`，但原实现仍存在两个 correctness 问题：

1. `checkin-wc + file` 在 controller 里直接改 working copy 实体，依赖隐式持久化上下文，不能保证新上传内容被真正持久化并进入 ledger。
2. working copy 的属性/元数据变更只用于触发 `createVersion()`，但在创建版本分支里没有再回写 original，导致“创建了版本但业务变更丢失”。

此外，`CMIS working-copy checkin` 仍绕过 working-copy service，继续直接调用 `versionService.createVersion()`，会保留双写和语义漂移风险。

## Design Goals

1. 所有 persisted working copy 的 check-in 都通过 `CheckOutCheckInService` 一条链路完成。
2. 上传新文件、创建版本、回写业务元数据、清理 working copy 和 ledger 必须处于同一事务。
3. `content`、`properties`、`metadata`、`description`、`encoding`、`fileExtension` 这些 versionable changes 必须统一判定。
4. 非 working copy 的 `/checkin-wc` 请求应返回 `400`，而不是未处理的 `500`。

## Implemented Changes

### 1. Service owns working-copy content updates

文件：

- `ecm-core/src/main/java/com/ecm/core/service/CheckOutCheckInService.java`

变更：

- 新增 `checkin(UUID, boolean, String, boolean, MultipartFile)` 重载，成为 working-copy check-in 的 authoritative entrypoint。
- controller/CMIS 不再直接改 working copy 的内容字段。
- 新增 `updateWorkingCopyContent(...)`：
  - 调用 `contentService.storeContent(file)`
  - 重算 `mimeType/fileSize/fileExtension/contentHash/textContent`
  - 显式 `documentRepository.save(wc)`
  - 调用 `contentReferenceService.syncOwnerReference(oldContentId, newContentId, WORKING_COPY, wcId)`

### 2. Check-in applies business state after version creation

文件：

- `ecm-core/src/main/java/com/ecm/core/service/CheckOutCheckInService.java`

变更：

- 新增 `hasVersionableMetadataChanges(...)`，统一比较：
  - `properties`
  - `metadata`
  - `description`
  - `encoding`
  - `fileExtension`
- 新增 `applyWorkingCopyState(...)`，在是否创建版本的两条分支之后都执行，确保 original 获得 working copy 的业务变更。

### 3. Version creation uses the effective check-in filename

文件：

- `ecm-core/src/main/java/com/ecm/core/service/CheckOutCheckInService.java`

变更：

- 新增 `resolveVersionFilename(...)`：
  - 优先使用新上传文件名
  - 否则使用 original 名称
  - 若 working copy 已切换扩展名，则用 `original base name + wc.fileExtension`

目的：

- 避免 working-copy check-in 时继续用旧文件名创建版本，导致 MIME/扩展名语义漂移。

### 4. Error contract aligned with REST handler

文件：

- `ecm-core/src/main/java/com/ecm/core/service/CheckOutCheckInService.java`

变更：

- 非 working copy 的 check-in 改抛 `IllegalArgumentException("Node is not a working copy")`

目的：

- 与 `RestExceptionHandler` 的 `400 Bad Request` 语义对齐。

### 5. CMIS path reuses the same service semantics

文件：

- `ecm-core/src/main/java/com/ecm/core/cmis/CmisContentVersioningService.java`

变更：

- `checkInWorkingCopy(...)` 不再直接调用 `versionService.createVersion(...)`
- 若 `contentBase64` 存在，则构造 `MockMultipartFile`
- 委托 `checkOutCheckInService.checkin(wcId, keepCheckedOut, comment, majorVersion, upload)`

目的：

- 避免 `CMIS` 与 REST working-copy check-in 继续分叉。

### 6. Admin version creation remains compatible with working-copy check-in

文件：

- `ecm-core/src/main/java/com/ecm/core/service/VersionService.java`

变更：

- `createVersion(...)` 在文档被 checkout 时允许 `ROLE_ADMIN` 执行

目的：

- 保证管理员接管 working copy check-in 时不会被版本服务拦截。

## Affected Tests

- `ecm-core/src/test/java/com/ecm/core/service/CheckOutCheckInServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/DocumentControllerWorkingCopyTest.java`
- `ecm-core/src/test/java/com/ecm/core/cmis/CmisContentVersioningServiceTest.java`

新增覆盖点：

1. 上传文件时先持久化 working copy，再创建版本
2. property-only check-in 会真正回写 original
3. controller 会把 multipart file 委托给 service
4. CMIS working-copy check-in 不再直接写版本服务

## Non-Goals

1. 本次不改普通 `/documents/{id}/checkin` 非 working-copy 流程
2. 本次不引入新的 Liquibase changeset
3. 本次不重做 NodeService 的 legacy checkout/checkin 模型
