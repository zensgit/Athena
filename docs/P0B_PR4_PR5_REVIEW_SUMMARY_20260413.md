# P0B PR-4 / PR-5 Review Summary

## Scope

本次评审覆盖两个内核修复：

1. `PR-4 subtree move consistency`
2. `PR-5 ACL delta indexing`

## Change Summary

### PR-4

- `NodeService.moveNode(...)` 现在会递归刷新整棵子树的 `path`
- `EcmEventListener.handleNodeMoved(...)` 改为：
  - `updateNode(root)`
  - `reindexNodeSubtree(root)`
- `SearchIndexService.reindexNodeSubtree(...)` 从数据库重新加载子树节点并重写索引，不再依赖旧 ES path

### PR-5

- 新增 `NodePermissionsChangedEvent`
- `SecurityService` 的 ACL mutation 入口在成功变更后会发布权限变更事件
- `EcmEventListener.handleNodePermissionsChanged(...)` 在事务提交后刷新节点索引，并在 folder 场景下刷新子树索引
- `takeOwnership(...)` 与 `cleanupExpiredPermissions()` 现在也会触发 permissions cache eviction 和索引收敛

## Why This Is Safer

### Move consistency

修复前：

- move 只更新根节点
- 子孙节点 `path` 可能长期保持旧前缀
- move 后索引刷新依赖 ES 旧路径匹配，容易直接漏掉子树

修复后：

- DB 中父子链与 `path` 同步更新
- 索引重建直接以 DB 新路径为准

### ACL indexing

修复前：

- `NodeDocument.permissions` 是派生字段，但 ACL 写操作不触发索引刷新
- 搜索结果可能继续按旧权限暴露文档

修复后：

- ACL 变更成为事务后事件
- 节点及其继承子树可以在提交后主动收敛索引

## Tests Added

- `NodeServiceMoveConsistencyTest`
- `SearchIndexServiceSubtreeReindexTest`
- `SecurityServicePermissionMutationTest`
- `EcmEventListenerPermissionIndexingTest`

## Verification Status

已完成：

- 代码审阅
- `git diff --check`
- Docker Maven wrapper：`ecm-core/mvnw`
- 定向测试：
  - `./ecm-core/mvnw -B -Dstyle.color=never test -Dtest=NodeServiceMoveConsistencyTest,SearchIndexServiceSubtreeReindexTest,SecurityServicePermissionMutationTest,EcmEventListenerPermissionIndexingTest`
- 全量测试：
  - `./ecm-core/mvnw -B -Dstyle.color=never test`
- 全量结果：
  - `Tests run: 1400, Failures: 0, Errors: 0, Skipped: 11`
- 设计/验证 MD 补齐

## Merge Recommendation

建议状态：`Ready to merge`

建议流程：

1. 合并 `PR-4`
2. 合并 `PR-5`
3. 关闭 `P0B`
4. 进入 `P1` 实施

## Residual Risks To Watch

1. 大子树 move 目前采用递归逐节点保存，正确性优先，但性能还不是最终形态
2. ACL 刷新第一版是整节点重写，不是 diff-based；这对 `P0` 是对的，但后续可以再优化吞吐
3. `updateNodeChildren(...)` 在 ACL 事件里仍然沿用现有实现，功能上能收敛权限，但不是最终的高性能版本

## Final Review Conclusion

在当前代码、定向测试和全量后端测试结果下，本轮 `P0B` 的两个 gate 已满足：

1. move subtree 一致性已由事务内 path 重写和事务后子树重建索引覆盖
2. ACL 变化后的搜索可见性已由 domain event + index refresh 链路覆盖

结论：

- `PR-4`: approve
- `PR-5`: approve
- `P0B`: close
