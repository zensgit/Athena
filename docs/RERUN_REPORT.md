# Athena ECM 复跑报告（Smoke + UI E2E）

日期：2025-12-18 15:57 CST

## Sprint A 一键验证（verify.sh）复跑（已验证）

日期：2025-12-19 11:59 CST

- ✅ `bash scripts/verify.sh`：全流程 PASS（含重启/健康检查/token/smoke/build/e2e）
- ✅ 日志前缀：`tmp/20251219_115943_*`

## 增量复跑（Sprint 2-4 复核闭环）

日期：2025-12-19 09:35 CST

### 结论

- ✅ Backend：`mvn -DskipTests compile` 通过（Docker Maven）
- ✅ API Smoke：通过（含 Scheduled Rule 手工触发 + tag 验证）
- ✅ Frontend build：通过
- ✅ UI E2E（Playwright）：`7/7` 通过
- ✅ Rule Execution Audit：`/api/v1/analytics/audit/recent` 可见 `RULE_EXECUTED` 与 `SCHEDULED_RULE_BATCH_COMPLETED`
- ⚠ ClamAV：如在 Apple Silicon（arm64）环境拉取失败，需要在 `docker-compose.yml` 的 `clamav` 服务上指定 `platform: linux/amd64`（见下方“二次复跑”已验证可运行）

### 复跑命令（本次）

```bash
# 重建（已将端口固定为 5500/7700）
bash scripts/restart-ecm.sh

# 获取 token（注意：token 可能短时过期，401 时重新执行即可）
bash scripts/get-token.sh admin admin

# API Smoke（使用指定 PDF）
ts=$(date +%Y%m%d-%H%M%S)
log="tmp/smoke.rerun.${ts}.log"
ECM_UPLOAD_FILE="/Users/huazhou/Downloads/J0924032-02上罐体组件v2-模型.pdf" \
ECM_API=http://localhost:7700 \
ECM_TOKEN_FILE=tmp/admin.access_token \
bash scripts/smoke.sh | tee "$log"

# Frontend build
cd ecm-frontend
npm run build

# Playwright E2E
npm run e2e

# 验证 Rule Execution Audit（示例）
TOKEN="$(cat ../tmp/admin.access_token)"
curl -H "Authorization: Bearer ${TOKEN}" \
  http://localhost:7700/api/v1/analytics/audit/recent?limit=200 \
  | jq -r '.[] | select(.eventType|test("RULE_EXECUTED|SCHEDULED_RULE_BATCH")) | [.eventType,.eventTime,.username] | @tsv' \
  | head
```

### 关键修复（复跑中发现并已修复）

- 后端：`DocumentRepository.findModifiedSinceInFolder` 使用 `d.parent.id`（修复启动期 HQL `parentId` 属性不存在导致的 500/启动失败）
- 后端：`VirusScanProcessor` fail-open 不再将上传置为 `400`（ClamAV 不可用时返回 `SKIPPED`，上传仍成功）
- 后端：`RuleEngineService.evaluateAndExecute(..., List<AutomationRule>)` 不再原地排序入参（避免 `List.of(...)` 导致 `UnsupportedOperationException`，Scheduled Rule 可执行）
- Smoke：Advanced Search 增加 `filters.path` 限定测试目录，避免历史数据过多导致分页/排序不稳定；Scheduled Rule 测试增加 `scopeFolderId` 限定测试目录
- 前端：`RulesPage` 行内按钮补齐 `aria-label`（`Edit/Test/Delete`），使 Playwright 可稳定定位
- Playwright：Scheduled Rule 断言从 `getByText('SCHEDULED')` 改为 `getByRole('cell', { name: 'SCHEDULED', exact: true })`；MFA 链接断言从 `button` 改为 `link`

### 本次日志

- API Smoke：`tmp/smoke.rerun.20251219-010456.log`
- Frontend build：`tmp/frontend.build.rerun.20251219-010840.log`
- UI E2E（Playwright）：`tmp/frontend.e2e.rerun.20251219-012839.log`

## 二次增量复跑（ClamAV 实跑 + Keycloak 测试用户）

日期：2025-12-19 16:55 CST

### 结论

- ✅ ClamAV：`clamav/clamav:stable` 在 Apple Silicon 通过 `platform: linux/amd64` 启动成功，`/system/status` 显示 `available=true status=healthy`
- ✅ API Smoke：EICAR 上传被拒绝（HTTP 400，`Eicar-Test-Signature`）
- ✅ UI E2E（Playwright）：`7/7` 通过（含 EICAR 用例）
- ✅ Keycloak：`scripts/keycloak/create-test-users.sh` 可创建 `editor/viewer`（改用容器内 `kcadm.sh`，避免 host 侧 `HTTPS required`）

### 关键修复（本次新增）

- Compose：`clamav` 服务增加 `platform: linux/amd64`，arm64 环境可拉取并运行
- Smoke：EICAR 测试前补齐 `root_id` 解析，避免 `set -u` 未定义变量并确保真实上传验证
- Playwright：System Status 的 Antivirus 断言限定在 Antivirus 卡片内，避免 strict-mode 多匹配失败

### 本次日志

- API Smoke：`tmp/smoke.rerun.20251219-083609.log`
- Frontend build：`tmp/frontend.build.rerun.20251219-083742.log`
- UI E2E（Playwright）：`tmp/frontend.e2e.rerun.20251219-084236.log`

## 环境

- 前端：`http://localhost:5500`
- 后端：`http://localhost:7700`
- Keycloak：`http://localhost:8180`（Realm：`ecm`，Client：`unified-portal`）
- Collabora：`http://localhost:9980`
- Redis：主机端口 `6390 -> 6379`

## 执行命令

```bash
# 0) 重建并重启（本次包含 /system/status）
bash scripts/restart-ecm.sh

# 0.1) 创建 Keycloak 测试用户（RBAC 回归用，可重复执行）
bash scripts/keycloak/create-test-users.sh

# 1) 获取 token
bash scripts/get-token.sh admin admin

# 2) API Smoke（使用指定 PDF）
ts=$(date +%Y%m%d-%H%M%S)
log="tmp/smoke.rerun.${ts}.log"
ECM_UPLOAD_FILE="/Users/huazhou/Downloads/J0924032-02上罐体组件v2-模型.pdf" \
ECM_API=http://localhost:7700 \
ECM_TOKEN_FILE=tmp/admin.access_token \
bash scripts/smoke.sh | tee "$log"

# 3) System Status（可选）
TOKEN="$(cat tmp/admin.access_token)"
curl -H "Authorization: Bearer ${TOKEN}" http://localhost:7700/api/v1/system/status | jq .

# 4) UI E2E（Playwright，可选）
cd ecm-frontend
npm run e2e
```

## 结果摘要

### API Smoke（通过）

- Root folder id：`d47a22e5-4aae-4bae-a9b1-8b045ba8f2a0`
- 本次创建临时文件夹：`smoke-folder-1766037219`（`cfd8c788-eb49-4579-a62b-48e1882b33e8`，结束后已永久递归删除）
- 上传文件：`/Users/huazhou/Downloads/J0924032-02上罐体组件v2-模型.pdf`
- Document id：`71392314-e511-4750-a727-3b43de8bfb17`
- Version history：`count=1`（`1.0`）
- WOPI：`CheckFileInfo/GetFile/LOCK/PutFile/UNLOCK` 全部 OK
- 版本递增：`1.0 -> 1.1`（PutFile 后 `count=2`）
- ML health：OK
- Search：可检索到上传文档
- License：`GET /api/v1/system/license`：OK（`edition=Community`）
- Favorites：`POST/GET(check)/POST(batch/check)/GET(list)/DELETE`：OK
- Saved Searches：`POST/GET(list)/GET(execute)/DELETE`：OK
- Correspondent：创建 `smoke-corr-1766037219`；并对上传文档执行手工设置（`PATCH /api/v1/nodes/{id}` 更新 `correspondentId`）OK；`/search/advanced` 过滤（`filters.correspondents`）OK；`/search/facets` 返回 `correspondent` facets OK
- Copy/Move：`/api/v1/folders/{folderId}/copy` + `/api/v1/folders/{folderId}/move`：OK
- Facets：`/api/v1/search/index/{documentId}` + `/api/v1/search/facets?q=...`：OK（可返回 tag/category facets）
- Share/Tag/Category/Trash restore：全部 OK
- Admin（Users/Groups）：创建组→加入/移除成员→删除组：OK
- Workflow：启动审批→获取任务→完成任务→history：OK
- 运行日志：`tmp/smoke.rerun.20251218-135338.log`

### System Status（通过）

- API：`GET http://localhost:7700/api/v1/system/status`：OK（DB/Redis/RabbitMQ/Search/ML/Keycloak/WOPI 聚合状态）
- UI：`http://localhost:5500/status`：OK（展示聚合 JSON，支持 Refresh/Copy JSON）
- Sanity Checks：`POST http://localhost:7700/api/v1/system/sanity/run?fix=false`：OK（report-only，admin only）
- Correspondents：`GET http://localhost:7700/api/v1/correspondents`：OK（列表接口）

### UI E2E（通过）

- Playwright：`3 passed`（`ecm-frontend/e2e/ui-smoke.spec.ts`）
- 运行日志：`tmp/playwright.rerun.20251218-144605.log`
- 覆盖新增：面包屑 Root 去重（避免 `Root > Root > Documents`）；用户菜单可见 `Rules/Admin Dashboard`；RBAC（editor 可访问 `/rules`，viewer 不可访问 `/rules`/`/admin`，admin-only API 返回 403，authorities 分别包含 `ROLE_EDITOR`/`ROLE_VIEWER`）；Batch Download（多选后 `Download selected` 生成 ZIP）；Saved Searches（Save Search + `/saved-searches` 页面回填/执行/删除）；`/correspondents`（创建/编辑对应人，页面支持 Search 过滤）；Properties → Correspondent 下拉选择；SearchResults → Correspondent facet 过滤；Favorites（星标列 Add/Remove + `/favorites` 页面）；Admin Dashboard License 展示

### UI 验证（MCP 手工回归）

- Keycloak 登录：`admin/admin`：OK
- 目录树/列表：可见 Root/Documents；列表可加载：OK
- 搜索：Advanced Search（Name contains=模型）能返回 PDF：OK
- `/rules`：页面可打开并展示 rules：OK
- `/status`：页面可打开并展示 System Status：OK

### Frontend Unit Tests（通过）

- `CI=true npm test -- --watchAll=false`：OK（5 tests passed）
- 运行日志：`tmp/jest.rerun.20251218-155751.log`
- 覆盖新增：账号菜单 Tags/Categories 入口（admin/editor 可见，viewer 不可见）；侧边栏开关状态持久化（刷新不再重置）；viewer 禁用 Upload/New Folder；文件列表写操作对 viewer 隐藏/降级（Edit→View，隐藏 Delete/Copy/Move/Tags/Categories/Share/ML/Approval）；Properties Dialog 编辑入口对 viewer 隐藏；Permissions Dialog 禁用修改（继承/权限开关/增删主体）

## 增量复跑（MCP 手工 + Network 验证）

日期：2025-12-18 16:25 CST

由于本 CLI 环境对 `localhost`/Docker socket 连接存在限制，本轮复跑使用 MCP（Chrome DevTools）完成 UI 驱动的 API 回归，并通过 Network 请求状态码核实关键接口返回 `200`。

### 覆盖与结果（通过）

- Keycloak 登录：`admin/admin`：OK（可进入 `http://localhost:5500/browse/*`）
- Folder：在 `Documents` 下创建 `mcp-api-rerun-1766045274566`：OK
- Upload（真实 PDF）：上传 `/Users/huazhou/Downloads/J0924032-02上罐体组件v2-模型.pdf` 到新目录：OK（列表可见，版本标识 `1.0`）
- Search：Advanced Search `Name contains=J0924032`：OK（Search Results 可返回 PDF）
- Trash：删除 PDF → `http://localhost:5500/trash` 可见 → Restore：OK（恢复后回到原目录）
- Upload（小文件 + Network）：上传 `tmp/mcp-api-upload-20251218-162411.txt`：OK；Network 观察到 `POST /api/v1/documents/upload?folderId=cc9c66ea-1d8e-41db-a3b4-131298bcf1ff` 返回 `200`

### 前端回归（通过）

- Jest：`CI=true npm test -- --watchAll=false`：OK（日志：`tmp/jest.rerun.20251218-171728.log`）
- Build：`npm run build`：OK（日志：`tmp/build.rerun.20251218-171728.log`）
- RBAC 深入口加固：Tag/Category/Share/Move/Copy/Upload/New Folder dialogs 对 viewer 做只读 guard（按钮禁用/写操作隐藏/handler 保护）

## 增量复跑（修复 Keycloak 登录回跳 URL 闪跳）

日期：2025-12-18 18:03 CST

### 背景

当浏览器地址在 `http://localhost:3000/` 与 `http://localhost:3000/#state=...&code=...` 之间不断闪跳时，通常是：

- Keycloak 回调参数（`code/state/...`）未被正常处理或清理；
- `PrivateRoute` 在 render 阶段重复触发 `keycloak.login()`（React StrictMode 在开发环境会触发双 render），导致不断生成新的 `state/code`；
- 或 Keycloak Client 未配置当前前端端口的 `Web origins/Redirect URIs`，token 交换阶段被阻断，从而进入循环。

### 修复内容

- 将 `keycloak.login()` 从 `render` 移到 `useEffect`，并使用 `sessionStorage` 旗标去重，避免重复触发登录跳转。
- 生成 `redirectUri` 时清理 `code/state/session_state/iss`（query/hash 两种形式）避免“越跳越长”的 URL。

相关改动：

- `ecm-frontend/src/components/auth/PrivateRoute.tsx`
- `ecm-frontend/src/index.tsx`

### 回归（通过）

- Keycloak clientId 默认值对齐：前端默认 `unified-portal`（避免缺少 env 时误用 `ecm-api`）
- Jest：`CI=true npm test -- --watchAll=false`：OK（日志：`tmp/jest.rerun.20251218-211503.log`）
- Build：`npm run build`：OK（日志：`tmp/build.rerun.20251218-205740.log`）
- API Smoke：`bash scripts/smoke.sh`：OK（日志：`tmp/smoke.rerun.20251218-210443.log`，新增覆盖 `/api/v1/analytics/dashboard` + `/api/v1/analytics/audit/recent`）
- UI E2E（Playwright）：`npm run e2e`：OK（日志：`tmp/playwright.rerun.20251218-205953.log`）
- 部署：`docker compose up -d --build ecm-frontend`（`http://localhost:5500` 已更新到新 bundle）

## Sprint 1 规则自动化闭环复跑（通过）

日期：2025-12-18 22:15 CST

### 关键结论

- 规则触发器已在上传管线中生效：`DOCUMENT_CREATED` 触发后可自动 `ADD_TAG`（Smoke + Playwright 双验证）。
- 若出现“规则未触发/标签未自动应用”，优先确认后端容器是否已用最新代码重建（本次曾因旧镜像未包含 `RuleTriggerProcessor` 导致失败，执行 `bash scripts/restart-ecm.sh` 后复跑通过）。

### 本次复跑日志

- API Smoke：`tmp/smoke.rerun.20251218-221305.log`（包含 Rule Automation：create rule → upload → verify auto-tag）
- Frontend Jest：`tmp/jest.rerun.20251218-221435.log`
- Frontend Build：`tmp/build.rerun.20251218-221510.log`
- UI E2E（Playwright）：`tmp/playwright.rerun.20251218-221543.log`（含 `Rule Automation: auto-tag on document upload` 用例）

## Sprint 4 内容安全（病毒扫描 + 加密存储）

日期：2025-12-18

### 4.1 ClamAV 防病毒集成（完成）

**Docker Compose 更新**：
- 添加 `clamav` 服务（`clamav/clamav:stable` 镜像）
- 端口映射：`3310:3310`
- 持久化卷：`clamav_data:/var/lib/clamav`
- 健康检查：`clamdscan --ping 5`（60s 间隔，120s 启动延迟）
- 添加 `ecm-core` 环境变量：`ECM_ANTIVIRUS_ENABLED`、`ECM_ANTIVIRUS_CLAMD_HOST/PORT`

**后端配置**：
- `application.yml`/`application-docker.yml` 添加 `ecm.antivirus.*` 配置
- 默认禁用（`enabled: false`），Docker 环境启用

**新增组件**：
- `ClamAvClient.java`：低层 clamd INSTREAM 协议通信（分块上传、PING/VERSION/SCAN）
- `AntivirusService.java`：Spring 服务包装，支持 `reject`/`quarantine` 两种威胁处理模式
- `VirusScanProcessor.java`：文档上传管线处理器（order=150，在 ContentStorageProcessor 之后运行）
  - 扫描存储后的内容
  - 发现病毒则删除内容并终止上传
  - Fail-open 策略：扫描错误仅记录警告，不阻止上传

**SystemStatus 集成**：
- `SystemStatusDto` 添加 `antivirus` 字段
- `SystemStatusController` 添加 `checkAntivirus()` 方法（返回 enabled/available/version/status）
- `SystemStatusPage.tsx` 添加 Antivirus 卡片展示

### 4.2 加密存储文档（完成）

**文档**：`docs/FEATURE_ENCRYPTION.md`

- **Phase 1（推荐）**：基础设施级加密
  - MinIO SSE-S3（MinIO 管理密钥，自动加密）
  - MinIO SSE-KMS（HashiCorp Vault/AWS KMS 外部密钥）
  - 主机/卷级加密（LUKS、Docker 加密卷）
  - 云提供商加密（AWS EBS、Azure Disk、GCP Persistent Disk）

- **Phase 2（未来）**：应用级信封加密设计
  - KMS → DEK → AES-256-GCM 内容加密
  - 密钥轮换、性能考量、搜索兼容性

### 4.3 测试更新（完成）

**API Smoke（`scripts/smoke.sh`）**：
- 添加 `2.3.3 Antivirus Status Check` 检查节
- 添加 `2.3.4 EICAR Virus Test`：仅当 AV 启用且可用时执行 EICAR 文件上传拒绝测试

**UI E2E（`ecm-frontend/e2e/ui-smoke.spec.ts`）**：
- 添加 `Antivirus: EICAR test file rejection + System status` 测试用例
- 检查 `/api/v1/system/status` 返回 antivirus 字段
- 检查 `/status` 页面展示 Antivirus 状态
- 当 AV 启用时上传 EICAR 测试文件验证拒绝

### 构建验证

- Frontend Build：`npm run build`：OK（无类型错误）
- 新增文件：
  - `ecm-core/src/main/java/com/ecm/core/integration/antivirus/ClamAvClient.java`
  - `ecm-core/src/main/java/com/ecm/core/integration/antivirus/AntivirusService.java`
  - `ecm-core/src/main/java/com/ecm/core/pipeline/processor/VirusScanProcessor.java`
  - `docs/FEATURE_ENCRYPTION.md`
