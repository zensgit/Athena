# P0B Move Consistency And ACL Indexing Verification

## Verification Scope

本轮验证覆盖：

1. 移动文件夹后 DB 子树 `path` 一致性
2. move 事件后的索引重建路径
3. ACL 写操作后的索引刷新触发
4. 继承权限场景下的子树刷新

## Static Validation Completed

### 1. Diff hygiene

命令：

```bash
git diff --check
```

结果：

- 通过

### 2. Code-path review

已人工核对以下链路：

- `NodeService.moveNode(...) -> refreshDescendantPaths(...)`
- `EcmEventListener.handleNodeMoved(...) -> SearchIndexService.reindexNodeSubtree(...)`
- `SecurityService ACL mutations -> NodePermissionsChangedEvent`
- `EcmEventListener.handleNodePermissionsChanged(...) -> updateNode / updateNodeChildren`

## Runtime Validation Completed

当前仓库已补上 Docker Maven wrapper：

- `ecm-core/mvnw`

因此本轮验证直接在仓库内执行：

```bash
./ecm-core/mvnw -B -Dstyle.color=never test -Dtest=NodeServiceMoveConsistencyTest,SearchIndexServiceSubtreeReindexTest,SecurityServicePermissionMutationTest,EcmEventListenerPermissionIndexingTest
./ecm-core/mvnw -B -Dstyle.color=never test
```

结果：

- `P0B` 定向测试：通过
- 全量后端测试：通过
- 全量统计：`Tests run: 1400, Failures: 0, Errors: 0, Skipped: 11`

## Tests Added

### PR-4 subtree move consistency

- `ecm-core/src/test/java/com/ecm/core/service/NodeServiceMoveConsistencyTest.java`
- `ecm-core/src/test/java/com/ecm/core/search/SearchIndexServiceSubtreeReindexTest.java`

覆盖点：

1. move 后子文件夹与子文档 `path` 被重写到新前缀
2. move 后子树索引重建来自 DB 新路径

### PR-5 ACL delta indexing

- `ecm-core/src/test/java/com/ecm/core/service/SecurityServicePermissionMutationTest.java`
- `ecm-core/src/test/java/com/ecm/core/event/EcmEventListenerPermissionIndexingTest.java`

覆盖点：

1. `setPermission(...)` 会发布权限变更事件
2. `applyPermissionSet(...)` 只发布一次刷新事件
3. `setInheritPermissions(...)` 相同值调用不误发事件
4. `cleanupExpiredPermissions()` 会对受影响节点发刷新事件
5. 权限变更事件会刷新节点及其子树索引
6. move 事件会走 `reindexNodeSubtree(...)` 而不是旧的索引前缀扫描

## Recommended Manual / CI Checks

### Move consistency

1. 创建 `folder -> subfolder -> document` 三层结构
2. move 根 folder
3. 校验：
   - DB 中三层 `path` 都更新
   - ES 中子孙文档 `path` 与 DB 一致

### ACL delta indexing

1. 对 folder 设置新的 `READ` authority
2. 校验：
   - folder 的 `NodeDocument.permissions` 更新
   - 继承权限的子节点 `permissions` 也更新
3. 再移除该 authority，确认搜索可见性回收

## Exit Assessment

从代码路径上看，这轮 `P0B` 已补上两个核心缺口：

1. move 不再只更新根节点，而是同步整棵子树 path
2. ACL mutation 不再静默结束，而会在事务后驱动索引收敛

结合已完成的定向测试和全量 `mvn test` 结果，当前建议：

- `PR-4`: ready to merge
- `PR-5`: ready to merge
- `P0B gate`: 可以关闭
