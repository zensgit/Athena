# Phase304 Alfresco People Directory and Batch Download Retention（开发设计）

## 1. 目标
- 把已有 people API 真正提升为可用的前端工作台，超越 Alfresco 基础目录检索，直接提供 profile/groups/favorites 联动。
- 把 async batch download 从“controller 内临时状态”升级为带 retention/cleanup 的 registry 模式，避免终态 ZIP 一直堆积。

## 2. 后端实现

### 2.1 BatchDownloadAsyncTaskRegistry
- 文件：`ecm-core/src/main/java/com/ecm/core/service/BatchDownloadAsyncTaskRegistry.java`
- 新增能力：
  - 统一管理 async batch download task registry
  - `register / get / update / snapshot`
  - `cleanupExpiredTerminalTasks()`
- retention 策略：
  - 终态任务保留 24 小时
  - `@Scheduled` 每小时清理一次
  - 清理时同步删除临时 ZIP artifact

### 2.2 BatchDownloadController 收口
- 文件：`ecm-core/src/main/java/com/ecm/core/controller/BatchDownloadController.java`
- 调整：
  - controller 只保留 endpoint 和 async runner 协调
  - registry/retention 逻辑下沉到 `BatchDownloadAsyncTaskRegistry`
  - 原有 API 契约保持不变

### 2.3 后端测试
- 文件：
  - `ecm-core/src/test/java/com/ecm/core/controller/BatchDownloadControllerTest.java`
  - `ecm-core/src/test/java/com/ecm/core/service/BatchDownloadAsyncTaskRegistryTest.java`
- 覆盖：
  - async task lifecycle
  - expired terminal task cleanup
  - active task 不会被误删

## 3. 前端实现

### 3.1 People Directory Workspace
- 文件：`ecm-frontend/src/pages/PeopleDirectoryPage.tsx`
- 能力：
  - people search
  - selected profile summary
  - roles/status 展示
  - groups 列表
  - favorites 列表并可跳转到节点浏览

### 3.2 路由和入口
- 文件：
  - `ecm-frontend/src/App.tsx`
  - `ecm-frontend/src/components/layout/MainLayout.tsx`
  - `ecm-frontend/src/components/layout/MainLayout.menu.test.tsx`
- 调整：
  - 新增 `/people-directory`
  - 账号菜单新增 `People Directory`
  - 菜单测试确保入口不会回归丢失

## 4. 结果
- Athena 现在已有独立的 people workspace，而不只是零散的 approver picker。
- async batch download 已具备 retention/cleanup 生命周期，向 Alfresco download resource 的治理层再推进了一步。
