# P0B Move Consistency And ACL Indexing Design

## Scope

本轮 `P0B` 实现覆盖两个 correctness 问题：

1. `PR-4 subtree move consistency`
2. `PR-5 ACL delta indexing`

目标不是做大范围架构重写，而是先把 repository 与索引链路补成“事务内数据一致，事务后搜索可收敛”。

## PR-4: Subtree Move Consistency

### Problem

`NodeService.moveNode(...)` 之前只更新被移动节点本身，子孙节点 `path` 仍停留在旧前缀。

后果：

1. DB 中子孙节点 `path` 与父链失真
2. `EcmEventListener.handleNodeMoved(...)` 虽然会调 `searchIndexService.updateNodeChildren(...)`，但它先从 ES 按新路径前缀查旧文档，move 后会直接漏掉整棵子树

### Design

#### 1. Move writes subtree paths in the same transaction

文件：

- `ecm-core/src/main/java/com/ecm/core/service/NodeService.java`

改动：

- `moveNode(...)` 在保存根节点前显式设置新 `path`
- 保存根节点后调用 `refreshDescendantPaths(...)`
- `refreshDescendantPaths(...)` 递归读取所有未删除直接子节点，并按 `parent.path + "/" + child.name` 重新保存

设计取舍：

- 当前采用“递归保存”而不是 bulk SQL replace
- 原因是 `P0` 优先 correctness，且要兼容当前 JPA 生命周期与实体语义

#### 2. Move event reindexes descendants from database

文件：

- `ecm-core/src/main/java/com/ecm/core/search/SearchIndexService.java`
- `ecm-core/src/main/java/com/ecm/core/event/EcmEventListener.java`

改动：

- 新增 `SearchIndexService.reindexNodeSubtree(...)`
- `handleNodeMoved(...)` 不再调用 `updateNodeChildren(...)`
- 改为：
  - `updateNode(root)`
  - `reindexNodeSubtree(root)`

原因：

- move 后 ES 里的旧子孙文档仍带旧 path，不能再依赖“先按新 path 前缀查 ES，再回源 DB”
- move 场景改成“直接按新 path 前缀查 DB，逐个重写索引”更稳

## PR-5: ACL Delta Indexing

### Problem

`NodeDocument.permissions` 是 `SearchIndexService.applyReadPermissions(...)` 派生出的索引字段，但 `SecurityService` 里的 ACL 写操作原先没有任何索引刷新触发器。

后果：

1. `setPermission/removePermission/applyPermissionSet` 之后，ES 仍保留旧权限
2. 父节点 ACL 变化时，继承权限的子节点在搜索结果中仍可能按旧可见性暴露

### Design

#### 1. ACL mutation emits a transactional domain event

文件：

- `ecm-core/src/main/java/com/ecm/core/service/SecurityService.java`
- `ecm-core/src/main/java/com/ecm/core/event/NodePermissionsChangedEvent.java`

改动：

- 新增 `NodePermissionsChangedEvent(node, username, includeDescendants)`
- 以下 mutation 入口在变更成功后统一发布事件：
  - `setPermission(...)`
  - `applyPermissionSet(...)`
  - `removePermission(...)`
  - `setInheritPermissions(...)`
  - `takeOwnership(...)`
  - `cleanupExpiredPermissions()`

补充修正：

- `setPermissionInternal(...)` 抽成私有方法，避免 `applyPermissionSet/takeOwnership` 内层重复发事件
- `setInheritPermissions(...)` 相同值调用改为 no-op
- `takeOwnership(...)`、`cleanupExpiredPermissions()` 同步加上 `@CacheEvict(permissions)`

#### 2. Event listener reuses existing index rebuild primitives

文件：

- `ecm-core/src/main/java/com/ecm/core/event/EcmEventListener.java`

改动：

- 新增 `handleNodePermissionsChanged(...)`
- 对权限变化执行：
  - `searchIndexService.updateNode(node)`
  - 若 `includeDescendants=true` 且节点是 folder，则 `searchIndexService.updateNodeChildren(node)`

设计取舍：

- 第一版不做 permission diff
- 直接重写 `NodeDocument.permissions`，实现简单、正确性更高

## Test Strategy

### PR-4

- `NodeServiceMoveConsistencyTest`
  - 验证 `moveNode(...)` 会同步更新子文件夹和子文档 `path`
- `SearchIndexServiceSubtreeReindexTest`
  - 验证 move 后的子树索引重建来自 DB，而不是依赖旧 ES path
- `EcmEventListenerPermissionIndexingTest`
  - 验证 move 事件走 `reindexNodeSubtree(...)`

### PR-5

- `SecurityServicePermissionMutationTest`
  - 验证 ACL mutation 会发布 `NodePermissionsChangedEvent`
  - 验证 batched permission update 不会多次发事件
  - 验证 no-op inheritance change 不发事件
  - 验证 expired permission cleanup 会触发刷新
- `EcmEventListenerPermissionIndexingTest`
  - 验证权限变更事件会刷新节点与子树索引

## Non-Goals

1. 本次不实现 ACL diff / changeset 表
2. 本次不做 subtree move 的 SQL 批量优化
3. 本次不重构 `NodeService` 为完整 policy pipeline
