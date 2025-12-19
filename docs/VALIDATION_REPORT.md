# Athena ECM 验证报告（本地 Docker / E2E）

日期：2025-12-19 11:59 CST（最近一次 `scripts/verify.sh` 全量复跑 PASS）  
范围：ECM 后端 API + 前端 UI 端到端（Playwright）+ 关键缺陷修复回归

## Sprint A 一键验证（verify.sh）复跑结论（已验证）

日期：2025-12-19

### 结论

- ✅ `scripts/verify.sh`：全流程一键验证通过（含重启/健康检查/token/smoke/build/e2e）
- ✅ `scripts/verify.sh`：ClamAV `unhealthy` 时自动重启并等待恢复（失败则 WARN）
- ✅ Scheduled Rule E2E：已增强为“专用目录 + scopeFolderId 隔离 + 强断言 tag 命中 + 清理目录”
- ✅ SettingsPage：`npm run build` 不再有 `Link` 未使用的 eslint warning
- ✅ CI Workflow：frontend `npm ci --legacy-peer-deps` + `lint` + `npx -p typescript@5.4.5 tsc --noEmit` + `build` + `npm test`

### 复跑命令与日志

```bash
# 完整一键复跑（包含 docker compose rebuild/recreate）
bash scripts/verify.sh

# 快速复跑（不重启，仅跑 smoke）
bash scripts/verify.sh --no-restart --smoke-only --skip-build
```

最近一次 PASS（含重启）日志前缀：`tmp/20251219_115943_*`

## 运行环境（本机）

- 前端：`http://localhost:5500`
- 后端：`http://localhost:7700`
- Keycloak：`http://localhost:8180`（Realm：`ecm`，Client：`unified-portal`）
- Redis：主机端口 `6390 -> 6379`
- 布局可调：侧边栏支持拖拽调宽（双击复位），Settings 提供 “Compact spacing” 紧凑模式

## 关键修复（阻塞问题）

### 1) 上传后“版本历史为空”

**现象**

- 通过 UI 或 `/api/v1/documents/upload` 上传后，`/api/v1/documents/{id}/versions` 返回空数组，前端 “Version History” 弹窗显示无记录。

**根因**

- Pipeline 上传路径（`UploadController -> DocumentUploadService -> DocumentProcessingPipeline`）只落库 `Document`，未创建初始 `Version`。

**修复**

- 新增 pipeline 处理器：`ecm-core/src/main/java/com/ecm/core/pipeline/processor/InitialVersionProcessor.java`  
  - Order：`420`（在持久化之后、索引之前）
  - 逻辑：对 `versioned=true && currentVersion=null` 的文档创建 `Version #1`（复用已存储的 `contentId`，不重复上传内容），并回写 `documents.current_version_id` / `documents.version_label`。
- 新增 Liquibase 回填：`ecm-core/src/main/resources/db/changelog/changes/012-backfill-document-versions.xml`  
  - 为历史上已存在但无版本记录的文档补齐 `versions` 表（version_number=1），并回写 `documents.current_version_id`。
- 避免接口映射冲突：将旧上传端点改为 legacy：`ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java`（`/upload` -> `/upload-legacy`）

## API 冒烟验证（脚本）

脚本：`scripts/smoke.sh`  
Token：`scripts/get-token.sh`（输出 `tmp/admin.access_token`）

执行命令：

```bash
bash scripts/get-token.sh admin admin
ECM_API=http://localhost:7700 ECM_TOKEN_FILE=tmp/admin.access_token bash scripts/smoke.sh
```

可选：用真实文件替换默认临时文本（例如 PDF）：

```bash
ECM_UPLOAD_FILE="/Users/huazhou/Downloads/J0924032-02上罐体组件v2-模型.pdf" \
  ECM_API=http://localhost:7700 \
  ECM_TOKEN_FILE=tmp/admin.access_token \
  bash scripts/smoke.sh
```

验证项（结果：通过）：

- `/actuator/health`：OK
- RBAC：`GET /api/v1/security/users/current/authorities`：OK（可识别 `ROLE_ADMIN/ROLE_EDITOR`）
- Upload：`POST /api/v1/documents/upload?folderId=<id>`：OK（返回 `documentId`）
- PDF smoke：默认使用有效 PDF（未传 `ECM_UPLOAD_FILE` 时自动生成），覆盖版本历史 + WOPI URL + 搜索链路
- Versions：`GET /api/v1/documents/{id}/versions`：OK（`>= 1`，通常为 `1.0`）
- WOPI URL：`GET /api/v1/integration/wopi/url/{id}`：OK（可生成 `wopiUrl`；若未启用会提示）
- WOPI Health：`GET /api/v1/integration/wopi/health`：OK（可查看 discovery/capabilities 状态）
- WOPI Host（端到端）：`CheckFileInfo/GetFile/LOCK/PutFile/UNLOCK`：OK（PutFile 后版本号递增，如 `1.0 -> 1.1`）
- ML health：`GET /api/v1/ml/health`：OK（若 ML 服务未启用会提示）
- Search：`GET /api/v1/search?q=<filename>`：OK（可检索到上传文档；若偶发延迟，脚本自带重试）
- Advanced Search：`POST /api/v1/search/advanced`：OK（脚本在 `/api/v1/search/index/{documentId}` 后验证可检索）
- Copy/Move：`POST /api/v1/folders/{folderId}/copy` + `POST /api/v1/folders/{folderId}/move`：OK
- Facets：`POST /api/v1/search/index/{documentId}` + `GET /api/v1/search/facets?q=...`：OK（能返回 tags/categories facets）
- Share：创建分享链接：OK（token 写入 `tmp/smoke.share_token`）
- Tag：添加标签：OK
- Category：创建分类并分配：OK
- Trash：移动到回收站并恢复：OK
- Workflow：启动审批→获取任务→完成任务→history：OK
- Admin（Users/Groups）：创建组→成员增删→删除组：OK

最近一次执行（2025-12-17 22:02 CST）：

- 上传文件：`/Users/huazhou/Downloads/J0924032-02上罐体组件v2-模型.pdf`
- Root folder id：`d47a22e5-4aae-4bae-a9b1-8b045ba8f2a0`
- Document id：`66153c61-08ed-42e0-9637-31084f99f00a`
- 版本递增：`1.0 -> 1.1`（WOPI PutFile）
- 运行日志：`tmp/smoke.rerun.20251217-220029.log`（同时覆盖为 `tmp/smoke.latest.log`）

## 前端端到端（Playwright）

测试：`ecm-frontend/e2e/ui-smoke.spec.ts`  
配置：`ecm-frontend/playwright.config.ts`

执行命令：

```bash
cd ecm-frontend
npm run e2e
```

覆盖的核心流程（结果：通过）：

- Keycloak 登录
- 浏览目录（Root -> Documents）与面包屑展示（Root 不重复）
- 上传文件并在列表出现
- PDF smoke：上传 PDF → 版本历史 → 在线编辑 → 搜索
- 在线编辑入口（Edit Online -> `/editor/:id`，iframe `src` 含 `WOPISrc`）
- 搜索（Advanced Search + Quick Search 重试）
- Copy/Move：在 UI 中执行 Copy/Move（对话框选择目标目录），并验证列表变化
- Facets：通过 API 校验 tags/categories facets 可见（含重试）
- 标签：创建 + 绑定到文档
- 分类：创建 + 勾选绑定
- 分享：创建 share link
- 权限弹窗：打开/关闭
- 版本历史：弹窗可见，且至少存在 “Current”
- ML Suggestions：弹窗打开/关闭
- 删除 -> 回收站恢复
- `/rules` 页面可打开
- Settings -> Session：可查看 token 过期时间，并一键复制 access token / Authorization header（便于测试 API）

布局验证（新增）：

- Settings -> Layout -> `Compact spacing (reduce paddings)`：开启后主内容 padding 与列表密度更紧凑
- 侧边栏分隔条常显，可拖拽调整宽度（支持键盘方向键调整）
- 侧边栏标题右侧 Pin：一键开关 “选中目录后自动收起侧边栏”

回收站一致性修复（新增）：

- 修复 `/api/v1/nodes/{id}` 软删除未写入 `deleted_at/deleted_by` 导致 Trash 页面排序/恢复不稳定的问题（现 UI 删除后可稳定在 Trash 顶部看到并恢复）。

## 实际文件上传验证（示例）

- 文件：`/Users/huazhou/Downloads/J0924032-02上罐体组件v2-模型.pdf`
- 上传：`POST /api/v1/documents/upload`
- 结果：上传成功；版本历史 `versions=1`，`versionLabel=1.0`

## 已知注意点 / 风险

- Search 索引存在“最终一致性”，首次上传后立即搜索可能短暂查不到（已在脚本与 E2E 中增加重试）。
- `scripts/get-token.sh` 可能未设置可执行位；用 `bash scripts/get-token.sh ...` 运行即可。

## 下一步建议（可选）

- 增加 API 层对版本信息的返回一致性（例如 `NodeDto` 已提供 `currentVersionLabel`，前端可统一显示该字段）。
- 对“大文件上传/断点续传/并发上传”做专项压测与失败恢复验证。
- 将 E2E 拆分为更细的测试用例（Browse/Upload/Search/Permissions/Editor 等），提高定位效率。

## RBAC 一致性修复（补充）

为避免“菜单未显示 Rules，但直接访问 `/rules` 仍可打开”的不一致行为，已加强前端路由守卫：当路由配置 `requiredRoles` 时，即使 Redux 中 `user` 尚未加载，也会基于 Keycloak token 解析的角色进行校验。

- 代码：`ecm-frontend/src/components/auth/PrivateRoute.tsx`

## 安全性修复（补充）

- `/api/v1/security/users/current` 改为返回 `UserDto`，避免返回实体导致的超大响应与循环引用，同时避免泄露密码哈希。
- `User.password` 标记为 `WRITE_ONLY`（只允许写入，不会被序列化输出）。
