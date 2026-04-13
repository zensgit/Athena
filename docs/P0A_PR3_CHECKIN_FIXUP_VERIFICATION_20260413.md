# P0A PR-3 Check-in Fixup Verification

## Verification Scope

本轮验证覆盖的是 `persisted working copy` check-in 语义修复：

1. `checkin-wc + file`
2. property/metadata-only check-in
3. REST controller 到 service 的委托
4. CMIS working-copy check-in 对同一服务入口的复用

## Static Validation Completed

### 1. Diff hygiene

命令：

```bash
git diff --check
```

结果：

- 通过

### 2. Call-site review

已确认：

- `DocumentController.checkinWorkingCopy(...)` 已改为委托 `CheckOutCheckInService.checkin(..., MultipartFile)`
- `CmisContentVersioningService.checkInWorkingCopy(...)` 已移除 direct `versionService.createVersion(...)`
- `CheckOutCheckInService.checkin(...)` 现在负责：
  - working copy 内容持久化
  - working copy ledger 同步
  - 版本创建
  - original 业务状态回写
  - working copy soft delete + detach

## Test Coverage Added/Updated

### Service tests

文件：

- `ecm-core/src/test/java/com/ecm/core/service/CheckOutCheckInServiceTest.java`

覆盖点：

1. 内容变化时创建版本并 detach working-copy 引用
2. 非 working copy 请求抛 `IllegalArgumentException`
3. content + metadata 都未变化时不创建版本
4. metadata-only 变化时会创建版本并真正回写 original properties
5. 上传文件时先持久化 working copy，再调用 `versionService.createVersion(...)`

### Controller tests

文件：

- `ecm-core/src/test/java/com/ecm/core/controller/DocumentControllerWorkingCopyTest.java`

覆盖点：

1. `checkin-wc` 直接委托新 service 重载
2. multipart check-in 会把文件传入 service
3. 非 working copy 请求映射为 `400`

### CMIS tests

文件：

- `ecm-core/src/test/java/com/ecm/core/cmis/CmisContentVersioningServiceTest.java`

覆盖点：

1. AtomPub working-copy check-in 不再直接调用 `versionService.createVersion(...)`
2. AtomPub working-copy check-in 改为委托 `CheckOutCheckInService.checkin(..., upload)`

## Runtime Verification Pending

当前环境缺少：

- `mvn`
- `ecm-core/mvnw`

因此以下命令尚未执行：

```bash
cd ecm-core
mvn test
```

## Recommended Gate Checks Before Closing P0A

在有 Maven 的 CI 或开发机上至少执行：

1. `mvn test -Dtest=CheckOutCheckInServiceTest,DocumentControllerWorkingCopyTest,CmisContentVersioningServiceTest`
2. `mvn test`
3. working-copy check-in 手工回归：
   - 上传新文件后 check-in，确认 original/current version 指向新内容
   - property-only check-in，确认 original properties 已更新
   - CMIS working-copy check-in，确认不再出现双版本写入

## Exit Assessment

从代码路径上看，这次 fixup 已经修复了此前 `PR-3` 的两个阻断问题：

1. 上传文件不再依赖 controller 层隐式持久化
2. metadata/property changes 不再只触发 version，而会真实提交到 original

是否正式关闭 `P0A gate`，仍应以 CI 中的 `mvn test` 结果为准。
