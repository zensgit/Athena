# Phase300 Alfresco Comments + People + Renditions（验证记录）

## 1. 验证命令

### 1.1 后端定向测试
```bash
cd ecm-core
mvn -q -Dtest=CommentControllerTest,CommentControllerSecurityTest,PeopleControllerTest,PeopleControllerSecurityTest,NodeControllerRelationsTest test
```

### 1.2 后端编译
```bash
cd ecm-core
mvn -q -DskipTests compile
```

### 1.3 前端 lint
```bash
cd ecm-frontend
npx eslint src/pages/AdvancedSearchPage.tsx src/services/nodeService.ts src/services/peopleService.ts
```

### 1.4 前端构建
```bash
cd ecm-frontend
npm run -s build
```

## 2. 结果
- 后端定向测试：通过
- 后端编译：通过
- 前端 lint：通过
- 前端 production build：通过

## 3. 覆盖点
- Comments：
  - 节点评论 CRUD / tree / search / statistics
  - 用户评论与被提及评论分页
  - comment endpoints 认证校验
- People：
  - people search / get
  - groups 返回排序
  - favorites 在“当前用户”与“其他用户”路径上的分支选择
  - people endpoints 认证校验
- Renditions：
  - 文档节点返回 `preview` / `thumbnail` 两类虚拟 rendition 资源
  - 失败状态下 failure reason/category 回传
  - folder 节点空页语义
- 前端：
  - `nodeService` 新增 rendition relation 契约通过类型检查
  - `AdvancedSearchPage` 成功并行接入 rendition detail 渲染
  - `peopleService` 成功纳入编译链路

## 4. 已知边界
- `PeopleController` 的 groups/favorites 当前基于本地仓库聚合，优先覆盖当前本地身份模型场景。
- Keycloak 模式下如需完全对齐“远端 people/group membership”，下一阶段需要把 `UserGroupBackend` 扩展为可查询单用户 group memberships。
