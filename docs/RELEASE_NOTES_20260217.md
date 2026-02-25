# 发布说明（2026-02-17）

## 一、发布概览
- 本次发布聚焦认证恢复稳定性与回归门禁可用性：
  - 前端会话恢复链路加固（401 重试/跳转登录提示一致性）。
  - 新增 Auth Session Recovery 的 Playwright 回归覆盖。
  - Phase5 回归脚本增强：支持自定义本地端口自动拉起静态 SPA 服务。
  - Phase5+Phase6 交付门禁增强：full-stack UI 目标自动探测，优先开发态 `:3000`。
  - Phase5+Phase6 交付门禁增强：静态 full-stack 目标支持 prebuilt 自动同步（可配置）。
  - Search spellcheck 精确查询降噪：文件名/ID 类查询跳过拼写建议请求。
  - 路由兜底增强：未知路径自动回退，避免空白页。
  - P1 smoke 覆盖增强：新增未知路径回退无白屏场景。
  - Full-stack smoke 脚本增强：单独执行时复用 prebuilt 同步策略，减少旧静态包误测。
  - Preview 状态统计一致性增强：带参数的 `application/octet-stream;...` 归类为 unsupported，避免误计入 failed。
  - Auth 恢复可观测性增强：支持结构化调试事件（默认关闭，按需开启）。
  - Search/Advanced Search 恢复增强：搜索失败时提供 Retry + Back to folder 内联操作。
  - Unknown Route 回退稳定性增强：按认证状态回退 + P1 smoke 适配 Keycloak 重定向时序。
  - Settings 诊断增强：新增 Auth Recovery Debug 本地开关并纳入 mocked 回归覆盖。
  - Search 错误恢复增强：引入分类映射，按错误类型智能控制 Retry 行为与提示。
  - Advanced Search 预览失败运维体验增强：批量操作进度、原因分组操作与非重试分组统计。
  - Auth/Route 回归矩阵增强：新增 session-expired、redirect-pause、unknown-route、Keycloak terminal redirect 的独立 E2E 矩阵与 smoke 脚本。
  - Delivery gate 分层增强：新增 `DELIVERY_GATE_MODE`（all/mocked/integration）与失败摘要输出，提升 CI 可诊断性。
  - Failure-injection 覆盖增强：补齐 auth transient/terminal 与 search temporary-failure->retry-success 场景。
  - 7-day 收尾文档增强：新增统一 release summary 与 verification rollup 文档。
  - Integration gate 覆盖增强：默认纳入 Phase70 auth-route 矩阵 smoke stage。
  - Login 恢复提示增强：支持仅 marker 状态（无 `ecm_auth_init_status`）下的 redirect-failure 兜底提示与过期 marker 清理。
  - Startup 稳定性增强：auth bootstrap 存储访问安全化 + 顶层 fatal 兜底，新增 storage 异常矩阵用例。
  - Prebuilt 同步治理增强：`ECM_SYNC_PREBUILT_UI=auto` 下，前端 dirty worktree 视为 stale 并自动重建静态包。
  - FileBrowser 可恢复性增强：新增长加载 watchdog 告警与 `Retry` / `Back to root` 操作，避免 spinner-only 卡住。
  - Mocked 门禁覆盖增强：新增 FileBrowser 长加载 watchdog mocked 场景并纳入 `phase5-regression`。

## 二、主要变更
### 1) 会话恢复与登录提示
- `authService.refreshToken` 增加瞬时错误与终态认证错误分类，避免对网络抖动场景误登出。
- API `401` 不可恢复时统一写入会话过期信号，并重定向到 `/login?reason=session_expired`。
- 登录页统一支持从 `sessionStorage`、`localStorage`、URL query 三路读取会话过期提示，并在展示后清理标记。

### 2) 自动化测试覆盖
- 新增 `ecm-frontend/e2e/auth-session-recovery.mock.spec.ts`：
  - 覆盖查询触发 `401`（初次+重试）后回到登录页。
  - 校验登录页展示 “Your session expired. Please sign in again.”
- 新增 `ecm-frontend/e2e/settings-session-actions.mock.spec.ts`：
  - 覆盖 Settings 页会话工具动作（复制 token / 复制 header / 刷新 token）与提示信息。
  - 校验复制内容准确写入剪贴板（mocked clipboard）。
- 增补前端单元测试：
  - `authService` 刷新失败分类与行为覆盖。
  - `api` 401 重试与失效标记覆盖。
  - `Login` 的会话过期兜底来源覆盖。

### 3) 门禁脚本可用性增强
- `scripts/phase5-regression.sh`
  - 默认（`PHASE5_USE_EXISTING_UI=0`）会启动“独立临时静态 SPA 服务”（随机端口）并以该地址运行 mocked 回归，避免命中旧静态包。
  - 当 `ECM_UI_URL` 指向本地且不可达时，自动在目标端口启动静态 SPA 服务。
  - 不再仅限 `:5500`，支持自定义端口（如 `:5514`、`:5515`）。
  - 如需复用现有 UI，可显式设置 `PHASE5_USE_EXISTING_UI=1`。
- `scripts/phase5-phase6-delivery-gate.sh`
  - 当未显式设置 `ECM_UI_URL_FULLSTACK` 时，自动探测：
    1. `http://localhost:3000`
    2. `http://localhost`
  - 优先开发态地址，降低“误测旧静态包”概率。
  - 新增 `ECM_FULLSTACK_ALLOW_STATIC`（默认 `1`）并透传到 full-stack smoke 脚本：
    - `1`：允许静态目标（兼容现有本地环境）
    - `0`：严格模式，若目标为 static 则直接失败并提示改用 `:3000`
  - 新增严格模式预检查：当 `ECM_FULLSTACK_ALLOW_STATIC=0` 时，在进入 `[1/5]` 前先校验 full-stack 目标，失败即快速退出。
  - 在 CI 环境下若未显式设置 `ECM_FULLSTACK_ALLOW_STATIC`，默认自动取 `0`（更严格）。

- `scripts/phase5-fullstack-smoke.sh` / `scripts/phase6-mail-automation-integration-smoke.sh` / `scripts/phase5-search-suggestions-integration-smoke.sh`
  - 新增 `FULLSTACK_ALLOW_STATIC`（默认 `1`），统一接入 `check-e2e-target.sh`。
- `scripts/phase5-phase6-delivery-gate.sh`
  - `p1 smoke` 入口也接入同一静态目标策略校验，避免策略绕过。

### 4) Spellcheck 精确查询降噪
- `ecm-frontend/src/utils/searchFallbackUtils.ts`
  - 新增 `shouldSkipSpellcheckForQuery`：
    - 文件名（如 `*.bin` / `*.pdf`）或路径/结构化 ID 查询默认跳过 spellcheck。
    - 自然语言查询仍保持 spellcheck 建议能力。
- `ecm-frontend/src/pages/SearchResults.tsx`
  - spellcheck effect 接入 guard：
    - 精确查询不再调用 `/search/spellcheck`。
    - 不展示“Checking spelling suggestions…”与“Did you mean”噪声提示。
- `ecm-frontend/e2e/search-suggestions-save-search.mock.spec.ts`
  - 增加“文件名查询跳过 spellcheck 请求”的 mocked E2E 场景。
- `ecm-frontend/e2e/p1-smoke.spec.ts`
  - 增加“文件名查询不展示拼写建议提示”的 full-stack smoke 场景。
  - 支持可选强校验：`ECM_E2E_ASSERT_SPELLCHECK_SKIP=1` 时断言零 spellcheck 请求。

### 5) 路由空白页防护
- `ecm-frontend/src/App.tsx`
  - 新增通配路由 `path="*"`，统一回退到 `/`（再按现有认证逻辑进入 `/browse/root` 或 `/login`）。
- `ecm-frontend/src/App.test.tsx`
  - 新增未知路径回退测试，确保不出现空白渲染。

### 6) Full-stack 预构建同步与 P1 回归稳定性
- `scripts/phase5-phase6-delivery-gate.sh`
  - 新增 `ECM_SYNC_PREBUILT_UI`（默认 `auto`）：
    - `auto`：当 full-stack 目标为本地静态代理（`http://localhost`）且 prebuilt 落后于源码时，自动触发 prebuilt 重建。
    - `1`：强制重建 prebuilt。
    - `0`：跳过 prebuilt 同步。
  - 新增 prebuilt 新鲜度判断：以 `ecm-frontend/build/asset-manifest.json` 修改时间与前端源码最新提交时间比较。
- `scripts/rebuild-frontend-prebuilt.sh`
  - 默认改为 `--no-deps`，仅重建/重启 `ecm-frontend`，避免误触发 `ecm-core` 等依赖服务重建。
  - 保留 `FRONTEND_REBUILD_WITH_DEPS=1` 作为显式全依赖重建开关。
- `ecm-frontend/e2e/p1-smoke.spec.ts`
  - 新增并稳定化未知路径回退用例：
    - 使用 `expect.poll(() => page.url())` 断言 SPA 同文档路由跳转，避免 `waitForURL` 在某些 same-document 场景下超时误判。

### 7) Standalone full-stack smoke 同步策略复用
- 新增共享脚本 `scripts/sync-prebuilt-frontend-if-needed.sh`：
  - 统一 `ECM_SYNC_PREBUILT_UI` 策略（`auto/1/0`）与 prebuilt 新鲜度判定逻辑。
- `scripts/phase5-fullstack-smoke.sh`
- `scripts/phase6-mail-automation-integration-smoke.sh`
- `scripts/phase5-search-suggestions-integration-smoke.sh`
  - 三个脚本在单独执行时可自动复用 prebuilt 同步策略，避免仅在 delivery gate 内生效。
- `scripts/phase5-phase6-delivery-gate.sh`
  - 继续在 gate 内统一做一次 prebuilt 同步，并在子脚本调用时透传 `ECM_SYNC_PREBUILT_UI=0`，避免重复同步。

### 8) Preview 状态过滤/分面：MIME 参数兼容
- `ecm-core/src/main/java/com/ecm/core/search/PreviewStatusFilterHelper.java`
  - unsupported 信号匹配增强：
    - 对 `mimeType` 保留精确匹配，并新增 `;*` 后缀通配匹配（兼容 `application/octet-stream; charset=...`）
    - unsupported reason 增加 `match_phrase` 辅助匹配，降低分词偏差影响
- `ecm-core/src/test/java/com/ecm/core/search/SearchAclElasticsearchTest.java`
  - 增补回归覆盖：
    - `FAILED` 过滤不应包含 parameterized octet-stream 旧数据
    - `UNSUPPORTED` 过滤应包含 parameterized octet-stream 旧数据
    - previewStatus facets 计数应将 parameterized octet-stream 归类到 `UNSUPPORTED`

### 9) Auth 恢复链路可观测性（调试开关）
- 新增 `ecm-frontend/src/utils/authRecoveryDebug.ts`：
  - `REACT_APP_DEBUG_RECOVERY=1` / `localStorage.ecm_debug_recovery=1` / `?debugRecovery=1` 触发调试输出。
  - 输出前递归脱敏：`token` / `Authorization` / `refreshToken` 等字段会被替换为 `[redacted]`。
- 在以下链路补齐事件日志（默认不输出）：
  - `index.tsx`: bootstrap start/retry/success/failed
  - `authService.ts`: login/logout/refresh decision
  - `api.ts`: 401 receive/retry/redirect + session-expired marking
  - `PrivateRoute.tsx`: auto redirect start/pause/failure
  - `App.tsx`: unknown route fallback redirect

### 10) Search 可恢复错误操作
- `ecm-frontend/src/pages/SearchResults.tsx`
  - 错误提示新增动作：`Retry` / `Back to folder` / `Advanced`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
  - 新增内联搜索错误提示与动作：`Retry` / `Back to folder`
  - 保留 toast 反馈，便于快速感知

### 11) Unknown Route 回退认证态一致性
- `ecm-frontend/src/App.tsx`
  - `RouteFallbackRedirect` 改为按认证态选择目标：
    - 已认证 -> `/browse/root`
    - 未认证 -> `/login`
  - 避免未知路径先落到受保护路由再触发额外认证跳转。
- `ecm-frontend/e2e/p1-smoke.spec.ts`
  - Unknown route smoke 断言增强：
    - 接受 in-app 恢复页可见，或到达 Keycloak 授权页（`client_id=unified-portal`）两种合法终态。
  - 降低认证跳转时序导致的误报。

### 12) Settings: Auth Recovery Debug 本地开关
- `ecm-frontend/src/utils/authRecoveryDebug.ts`
  - 导出本地开关常量与读写 helper（localStorage）。
- `ecm-frontend/src/pages/SettingsPage.tsx`
  - 新增 `Diagnostics` 卡片与 `Enable auth recovery debug logs` 开关。
  - 展示 effective status，并在 env/query 覆盖开启时给出提示。
  - `Copy Debug Info` 增加 debug 开关状态字段。
- `ecm-frontend/e2e/settings-session-actions.mock.spec.ts`
  - 覆盖开关启停、localStorage 持久化与 toast 提示。

### 13) Search 错误分类与恢复映射
- 新增 `ecm-frontend/src/utils/searchErrorUtils.ts`
  - 统一搜索错误分类：`transient / authorization / query / server / unknown`
  - 统一恢复决策：`canRetry` + `hint` + 标准化 `message`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
  - 搜索失败告警改为结构化恢复对象，按分类决定 `Retry` 是否可用。
- `ecm-frontend/src/pages/SearchResults.tsx`
  - Redux 错误消息接入同一恢复映射，告警展示统一 hint，`Retry` 按分类启停。
- 新增单测 `ecm-frontend/src/utils/searchErrorUtils.test.ts`
  - 覆盖状态码/文本分类、消息解析和 retryability。

### 14) Advanced Search 预览失败运维闭环优化
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
  - 新增批量重试/重建过程进度反馈：
    - `processed/total`
    - `queued/skipped/failed`
    - 结束时间显示
  - retryable reason 分组升级：
    - 分组 chip + `Retry` / `Rebuild` 动作
    - `Show all reasons` / `Show fewer reasons`
  - 非可重试场景保留既有治理文案，并新增 `Unsupported/Permanent` 数量展示。
- `ecm-frontend/src/utils/previewStatusUtils.ts`
  - 新增批量进度格式化和非重试分组文案 helper。
- `ecm-frontend/src/utils/previewStatusUtils.test.ts`
  - 增补上述 helper 的单元测试覆盖。

### 15) Auth/Route 独立矩阵回归（Phase70）
- 新增 `ecm-frontend/e2e/auth-route-recovery.matrix.spec.ts`
  - 覆盖四个终态场景：
    - `/login?reason=session_expired` 会话过期提示
    - redirect failure cap 生效后的登录提示（经受保护路由触发）
    - unknown route 回退到登录页且无白屏
    - 登录 CTA 可到达 Keycloak authorize endpoint
- 新增 `scripts/phase70-auth-route-matrix-smoke.sh`
  - 增加 API/Keycloak/UI 可达性预检查
  - 复用 prebuilt 同步策略与 e2e target 检查
  - 一键执行 Phase70 矩阵回归

### 16) Regression Gate 分层与失败摘要（Phase71）
- `scripts/phase5-phase6-delivery-gate.sh`
  - 新增 `DELIVERY_GATE_MODE`：
    - `all`：默认，先 fast mocked，再 integration/full-stack
    - `mocked`：仅跑 fast mocked 层
    - `integration`：仅跑 integration/full-stack 层
  - 新增层级化 stage 汇总输出，明确每层 PASS/FAIL。
  - 失败时输出一行化摘要（failed spec 或首条错误）并保留 stage log 路径。
- `scripts/phase5-regression.sh`
  - 新增 Playwright 失败摘要抽取与日志路径输出。
  - 修复 `set -euo pipefail` 下管道退出码与 macOS `mktemp` 兼容性问题。

### 17) Failure Injection 覆盖扩展（Phase72）
- `ecm-frontend/src/services/authService.test.ts`
  - 新增 refresh failure payload 注入覆盖：
    - transient `503` 保持会话
    - terminal `403` 触发登出与本地会话清理
- `ecm-frontend/src/services/api.test.ts`
  - 新增 API 拦截器注入覆盖：
    - request refresh transient fail 不写入 session-expired 标记
    - 401 重试链路中 terminal refresh throw 触发 session-expired 标记与登录回退
- `ecm-frontend/e2e/auth-session-recovery.mock.spec.ts`
  - 扩展为双场景：
    - 首次 401 + 二次成功（留在搜索页）
    - 连续 401（回退登录并提示 session expired）
- `ecm-frontend/e2e/search-suggestions-save-search.mock.spec.ts`
  - 新增 temporary `503` -> 点击 `Retry` -> 查询恢复成功场景。

### 18) 7-day 交付收尾文档（Phase73）
- 新增 `docs/PHASE73_AUTH_SEARCH_RECOVERY_RELEASE_SUMMARY_20260219.md`
  - 汇总 Day1-Day7 交付内容与关键提交。
- 新增 `docs/PHASE73_AUTH_SEARCH_RECOVERY_VERIFICATION_ROLLUP_20260219.md`
  - 汇总 Day4-Day6 关键验证命令与结果（含正向/受控失败验证）。
- `docs/NEXT_7DAY_PLAN_AUTH_SEARCH_RECOVERY_20260219.md`
  - Day7 标记完成并补充 exit criteria closure check。

### 19) Integration Gate 纳入 Auth/Route Matrix（Phase74）
- `scripts/phase5-phase6-delivery-gate.sh`
  - integration 层新增 stage：`phase70 auth-route matrix smoke`
  - 调用 `scripts/phase70-auth-route-matrix-smoke.sh`
  - 复用现有 gate 环境变量并设置 `ECM_SYNC_PREBUILT_UI=0` 避免重复 prebuilt 同步
- 验证方式：
  - `bash scripts/phase70-auth-route-matrix-smoke.sh`
  - `DELIVERY_GATE_MODE=integration bash scripts/phase5-phase6-delivery-gate.sh`

### 20) Login redirect-failure marker fallback hardening（Phase75）
- `ecm-frontend/src/components/auth/Login.tsx`
  - 新增 redirect-failure 提示构建 helper，统一 cap/cooldown 文案与过期窗口处理
  - 在 `ecm_auth_init_status` 缺失但 marker 存在时，仍显示登录页恢复提示
  - 对超出失败窗口的 marker 执行 best-effort 清理，减少陈旧噪声
- `ecm-frontend/src/components/auth/Login.test.tsx`
  - 增补 marker-only fallback 提示与 stale-marker 清理单测
- `ecm-frontend/e2e/auth-route-recovery.matrix.spec.ts`
  - 增补 `/login` 直达场景的 marker fallback 矩阵用例
- 验证方式：
  - `CI=1 npm test -- --runTestsByPath src/components/auth/Login.test.tsx`
  - `ECM_UI_URL=http://localhost:3000 npx playwright test e2e/auth-route-recovery.matrix.spec.ts --project=chromium --workers=1`
  - `ECM_UI_URL=http://localhost:3000 bash scripts/phase70-auth-route-matrix-smoke.sh`
  - `DELIVERY_GATE_MODE=integration PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh`

### 21) Startup resilience + prebuilt dirty-worktree guard（Phase76）
- `ecm-frontend/src/index.tsx`
  - 启动链路存储读写改为安全调用，避免受限上下文抛异常导致启动卡死
  - 新增 bootstrap 顶层 fatal catch，确保故障时可回落到可恢复 UI
- `ecm-frontend/e2e/auth-route-recovery.matrix.spec.ts`
  - 新增 storage remove 异常场景，验证启动可恢复终态（login 或 keycloak）
- `scripts/sync-prebuilt-frontend-if-needed.sh`
  - `auto` 模式新增 dirty worktree 检测（前端关键路径）
  - stale 输出新增 reason（`dirty_worktree`/`missing_manifest`/`committed_source_newer_than_build`）
- 验证方式：
  - `CI=1 npm test -- --runTestsByPath src/App.test.tsx src/components/auth/Login.test.tsx src/services/authBootstrap.test.ts`
  - `ECM_UI_URL=http://localhost:3000 npx playwright test e2e/auth-route-recovery.matrix.spec.ts --project=chromium --workers=1`
  - `ECM_UI_URL=http://localhost:3000 bash scripts/phase70-auth-route-matrix-smoke.sh`
  - `ECM_SYNC_PREBUILT_UI=1 DELIVERY_GATE_MODE=all PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh`
  - `ECM_SYNC_PREBUILT_UI=auto bash scripts/sync-prebuilt-frontend-if-needed.sh http://localhost`

### 22) FileBrowser loading watchdog + mocked gate coverage（Phase77）
- `ecm-frontend/src/pages/FileBrowser.tsx`
  - 新增长加载 watchdog（默认 12s，可由 `REACT_APP_FILE_BROWSER_LOADING_WATCHDOG_MS` 配置）
  - 告警面板新增恢复动作：
    - `Retry`
    - `Back to root`
  - 同时覆盖初始加载和目录内容加载阶段，避免 spinner-only 卡住
- `ecm-frontend/e2e/filebrowser-loading-watchdog.mock.spec.ts`
  - 新增长加载挂起 mocked 场景，验证 watchdog 提示与恢复动作
- `scripts/phase5-regression.sh`
  - mocked regression 列表新增 watchdog spec，默认门禁覆盖
- 验证方式：
  - `ECM_UI_URL=http://localhost:3000 npx playwright test e2e/filebrowser-loading-watchdog.mock.spec.ts --project=chromium --workers=1`
  - `bash scripts/phase5-regression.sh`
  - `npm run lint`
  - `bash scripts/phase70-auth-route-matrix-smoke.sh`

### 23) Auth boot startup watchdog + recovery actions（Phase78）
- `ecm-frontend/src/components/auth/AuthBootingScreen.tsx`
  - 新增认证启动 watchdog 组件（默认 12s）
  - watchdog 触发后展示恢复动作：
    - `Reload`
    - `Continue to login`
- `ecm-frontend/src/index.tsx`
  - 启动页接入 watchdog 组件
  - 新增 `REACT_APP_AUTH_BOOT_WATCHDOG_MS`
  - `Continue to login` 触发后立即回落应用登录态，避免 spinner-only 停留
  - 增加恢复后竞态保护：watchdog 恢复生效后，迟到的 bootstrap success/error/fatal 结果不再覆盖当前恢复态
  - 新增 debug 事件：
    - `auth.bootstrap.watchdog.triggered`
    - `auth.bootstrap.watchdog.reload`
    - `auth.bootstrap.watchdog.continue_to_login`
    - `auth.bootstrap.skipped_after_watchdog_recovery`
- `ecm-frontend/src/components/auth/AuthBootingScreen.test.tsx`
  - 新增 watchdog 状态迁移与动作回调单测覆盖
- 验证方式：
  - `npm test -- --watch=false --runInBand src/components/auth/AuthBootingScreen.test.tsx src/components/auth/Login.test.tsx src/services/authBootstrap.test.ts`
  - `npm run lint`
  - `bash scripts/phase70-auth-route-matrix-smoke.sh`
  - `bash scripts/phase5-regression.sh`

### 24) API 超时预算分层 + 超时恢复路径（Phase79）
- `ecm-frontend/src/constants/network.ts`
  - 新增读/写/上传/下载四类超时预算配置（env 可覆盖）。
- `ecm-frontend/src/services/api.ts`
  - 请求超时预算按操作类型统一：
    - `get` -> read timeout
    - `post/put/patch/delete` -> write timeout
    - `uploadFile` -> upload timeout
    - `getBlob/downloadFile` -> download timeout
  - 超时恢复策略：
    - 安全方法（GET/HEAD/OPTIONS）超时自动单次重试
    - 终态超时提示统一为 `Request timed out. Please retry.`
  - 增加 timeout 相关 debug 事件（retry start/success/failed + warning）。
- `ecm-frontend/src/services/api.test.ts`
  - 新增 timeout 预算与 timeout retry/warning 单测。
- `ecm-frontend/e2e/filebrowser-loading-watchdog.mock.spec.ts`
  - 调整为“慢响应触发 watchdog”模型，避免与新超时预算冲突并提升回归稳定性。
- 验证方式：
  - `npm test -- --watch=false --runInBand src/services/api.test.ts src/services/authBootstrap.test.ts src/components/auth/AuthBootingScreen.test.tsx`
  - `npm run lint`
  - `ECM_UI_URL=http://localhost npx playwright test e2e/filebrowser-loading-watchdog.mock.spec.ts --project=chromium --workers=1`
  - `bash scripts/phase70-auth-route-matrix-smoke.sh`
  - `bash scripts/phase5-regression.sh`

### 25) Startup Chaos Matrix 扩展（Phase80）
- `ecm-frontend/e2e/auth-route-recovery.matrix.spec.ts`
  - 新增 localStorage 读取受限场景：
    - redirect reason 读取抛错时，`/login?reason=session_expired` 仍可展示会话过期提示
  - 新增登录重定向时序抖动场景：
    - stale `ecm_kc_login_in_progress` marker 可在登录页被清理
- `scripts/phase70-auth-route-matrix-smoke.sh`
  - 直接复用，无需改脚本即可承载扩展后的 8 条矩阵用例

### 26) Delivery Gate 启动诊断提示（Phase81）
- `scripts/phase5-phase6-delivery-gate.sh`
  - 新增 `startup diagnostics hints` 失败提示段
  - 失败日志模式识别并输出快捷建议：
    - static/prebuilt 目标陈旧风险
    - storage 受限症状
    - auth timeout 症状
- 验证：
  - mocked 成功路径保持不变
  - integration 严格静态预检查受控失败时，成功输出 startup hints

### 27) Startup Stability 7-day 收尾（Phase82）
- 新增 `docs/PHASE82_STARTUP_STABILITY_RELEASE_CLOSEOUT_20260220.md`
  - 汇总 Day1-Day7 交付结果与验证命令
  - 包含 rollback checklist
  - 包含 operator runbook 更新要点
  - 标记 startup baseline freeze

### 28) 并行续开发：Auth Boot Watchdog Mocked 回归纳管（2026-02-21）
- `ecm-frontend/src/index.tsx`
  - 增加受控 e2e 启动注入开关：
    - `ecm_e2e_force_auth_boot_hang`
    - `ecm_e2e_auth_boot_watchdog_ms`
  - `Continue to login` 恢复路径增强：
    - 恢复时先将 URL 置为 `/login` 再渲染应用，避免被受保护路由立即拉回 Keycloak
  - 恢复后清理 e2e boot override flags，减少后续污染
- `ecm-frontend/e2e/auth-boot-watchdog-recovery.mock.spec.ts`
  - 新增 mocked 启动恢复场景：
    - 强制 auth boot hang
    - watchdog 告警触发
    - `Continue to login` 终态验证
- `scripts/phase5-regression.sh`
  - mocked 规格新增：
    - `e2e/auth-boot-watchdog-recovery.mock.spec.ts`
  - 默认 mocked gate 变更为 `16` 条 spec
- Full gate 回归确认：
  - `DELIVERY_GATE_MODE=all` 下 fast mocked 与 integration/full-stack 两层均通过

### 29) Watchdog 恢复纳入 Phase70 集成矩阵（Phase83）
- `ecm-frontend/e2e/auth-route-recovery.matrix.spec.ts`
  - 新增 `forced auth boot hang -> watchdog continue-to-login` 集成场景
  - 覆盖点：
    - 启动页 watchdog 出现
    - 点击 `Continue to login` 后恢复到 `/login`
    - 启动 override key 清理
- `scripts/phase70-auth-route-matrix-smoke.sh`
  - 无需改脚本，自动承载扩展后矩阵（由 8 -> 9 case）
- 验证：
  - `bash scripts/phase70-auth-route-matrix-smoke.sh` -> `9 passed`
  - `DELIVERY_GATE_MODE=all PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS

### 30) Auth 存储安全 + Spellcheck 精度加固（Phase84）
- `ecm-frontend/src/components/auth/PrivateRoute.tsx`
  - 认证重定向状态机改为安全 sessionStorage 访问（get/set/remove 全面 try/catch 封装）
  - 避免受限浏览器上下文下 storage 异常影响私有路由恢复链路
- `ecm-frontend/src/components/auth/Login.tsx`
  - 登录页状态读取与清理改为安全 storage 访问
  - 手动登录按钮在 storage 清理抛错场景仍可继续触发登录流程
- `ecm-frontend/src/utils/searchFallbackUtils.ts`
  - spellcheck 判定前增加 token 归一化（剥离包裹引号/配对符号/尾部标点）
  - 降低 filename-like 精确查询被误判为拼写建议候选的概率
- `scripts/phase70-auth-route-matrix-smoke.sh`
  - preflight 检查增加结构化失败提示（backend/keycloak/ui）
  - 失败时输出 target + actionable hint，减少仅 curl 超时报错的信息缺口
- 相关测试更新：
  - `src/components/auth/PrivateRoute.test.tsx`：新增 storage 抛错 fallback 场景
  - `src/components/auth/Login.test.tsx`：新增 storage 清理抛错下手动登录场景
  - `src/utils/searchFallbackUtils.test.ts`：新增 quoted/punctuated filename 场景
- 验证：
  - `npm test -- --watch=false --runInBand src/components/auth/Login.test.tsx src/components/auth/PrivateRoute.test.tsx src/utils/searchFallbackUtils.test.ts` -> PASS
  - `npm run lint` -> PASS
  - `bash scripts/phase5-regression.sh` -> PASS (`16 passed`)
  - `ECM_SYNC_PREBUILT_UI=false FULLSTACK_ALLOW_STATIC=1 bash scripts/phase70-auth-route-matrix-smoke.sh` -> expected fail（backend 不可达），新诊断提示输出正确

### 31) Storage 受限 auth 转场 mocked E2E 纳管（Phase85）
- `ecm-frontend/e2e/auth-storage-restricted-recovery.mock.spec.ts`
  - 新增组合场景：auth 相关 `sessionStorage/localStorage` 读受限时，`/browse/root` 与 `/login?reason=session_expired` 仍可达可操作终态。
- `scripts/phase5-regression.sh`
  - 默认 mocked 列表新增该 spec，回归数 `16 -> 17`。
- 验证：
  - `bash scripts/phase5-regression.sh` -> PASS (`17 passed`)

### 32) Login 统一认证交接状态卡（Phase86）
- `ecm-frontend/src/components/auth/Login.tsx`
  - 新增统一状态模型 `AuthInitNotice { title, detail }`
  - 登录状态卡标题可区分：
    - timeout
    - init error
    - session expired
    - redirect failed / paused
- `ecm-frontend/src/components/auth/Login.test.tsx`
  - 断言改为状态卡内范围检查，避免重复文本二义性。
- 验证：
  - `npm test -- --watch=false --runInBand src/components/auth/Login.test.tsx` -> PASS

### 33) Search 精确文件名模式可视提示（Phase87）
- `ecm-frontend/src/utils/searchFallbackUtils.ts`
  - 新增 `isPrecisionFilenameLikeQuery` 分类函数并复用到 spellcheck skip 判定。
- `ecm-frontend/src/pages/SearchResults.tsx`
  - 新增 `Exact filename mode active` 信息提示（`search-exact-match-mode-alert`）。
- `ecm-frontend/e2e/search-suggestions-save-search.mock.spec.ts`
  - filename-like 场景断言精确模式提示可见，自然拼写场景提示不可见。
- 验证：
  - `bash scripts/phase5-regression.sh` -> PASS (`17 passed`)

### 34) Phase5 回归热点/抖动风险摘要（Phase88）
- `scripts/phase5-regression.sh`
  - 新增运行后摘要：
    - `duration hotspots (top 5)`
    - `flaky-risk candidates (heuristic)`
- 不改变原有通过/失败判定，仅增强可观测性。

### 35) Integration 依赖预检分组诊断（Phase89）
- `scripts/phase5-phase6-delivery-gate.sh`
  - integration 层新增 `integration dependency preflight` stage
  - 失败时输出缺失依赖列表 + 去重 remediation hints，并提前跳过后续集成步骤
- `scripts/phase70-auth-route-matrix-smoke.sh`
  - preflight 失败输出结构化 `label + target + hint`
- 验证：
  - `DELIVERY_GATE_MODE=integration ECM_SYNC_PREBUILT_UI=false PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> expected fail（依赖缺失），分组诊断输出正确
  - `ECM_SYNC_PREBUILT_UI=false FULLSTACK_ALLOW_STATIC=1 bash scripts/phase70-auth-route-matrix-smoke.sh` -> expected fail（依赖缺失），结构化提示正确

### 36) Resilience continuation 7-day 收尾（Phase90）
- 新增：
  - `docs/PHASE90_RESILIENCE_CONTINUATION_RELEASE_CLOSEOUT_20260221.md`
- `docs/NEXT_7DAY_PLAN_RESILIENCE_CONTINUATION_20260221.md`
  - Day2-Day7 已标记完成并挂接交付文档。

### 37) FolderTree 根节点加载看门狗与重试恢复（Phase91）
- `ecm-frontend/src/components/browser/FolderTree.tsx`
  - 根节点加载链路新增 `loading watchdog` 超时提示与 `Retry` 操作。
  - 根加载失败终态新增独立错误面板与重试按钮（避免空白/卡死感知）。
  - 支持测试环境 watchdog 覆盖键：`ecm_e2e_folder_tree_watchdog_ms`。
- `ecm-frontend/e2e/folder-tree-root-watchdog.mock.spec.ts`
  - 新增 mocked 场景：根节点请求慢响应触发 watchdog，点击重试后成功恢复树节点显示。
- `scripts/phase5-regression.sh`
  - mocked 回归用例集合新增 `folder-tree-root-watchdog`，总执行结果更新为 `18 passed`（按当前集合）。
- 验证：
  - `bash scripts/phase5-regression.sh` -> PASS (`18 passed`)
  - `DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS
  - `ECM_SYNC_PREBUILT_UI=false FULLSTACK_ALLOW_STATIC=1 bash scripts/phase70-auth-route-matrix-smoke.sh` -> expected fail（依赖缺失），结构化提示正确
  - `DELIVERY_GATE_MODE=integration ECM_SYNC_PREBUILT_UI=false PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> expected fail（依赖缺失），分组诊断输出正确

### 38) App 全局崩溃恢复 mocked 回归纳管（Phase92）
- `ecm-frontend/e2e/app-error-boundary-recovery.mock.spec.ts`
  - 新增场景：强制 render crash 后进入 `AppErrorBoundary`，点击 `Back to Login` 可恢复到登录页并清理 crash 标记。
- `scripts/phase5-regression.sh`
  - mocked 回归新增 `app-error-boundary-recovery` 场景，当前集合执行结果 `19 passed`。
- 验证：
  - `bash scripts/phase5-regression.sh` -> PASS (`19 passed`)
  - `DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS
  - `npm test -- --watch=false --runInBand src/components/layout/AppErrorBoundary.test.tsx src/components/auth/Login.test.tsx src/components/auth/PrivateRoute.test.tsx src/utils/searchFallbackUtils.test.ts` -> PASS (`37 tests`)
  - `npm run lint` -> PASS

### 39) Unknown Route No-Blank mocked 回归纳管（Phase93）
- `ecm-frontend/e2e/route-fallback-no-blank.mock.spec.ts`
  - 新增双场景：
    - 未登录访问未知路由 -> 回退到 `/login`，登录页可见，无错误边界页
    - 已登录访问未知路由 -> 回退到 `/browse/root`，浏览壳与空文件夹态可见，无错误边界页
- `scripts/phase5-regression.sh`
  - mocked 集合新增 `route-fallback-no-blank`，当前回归总结果 `21 passed`。
- 验证：
  - `bash scripts/phase5-regression.sh` -> PASS (`21 passed`)
  - `DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS
  - `npm test -- --watch=false --runInBand src/App.test.tsx src/components/layout/AppErrorBoundary.test.tsx` -> PASS (`6 tests`)
  - `npm run lint` -> PASS

### 40) Startup 可见性 SLA mocked 回归纳管（Phase94）
- `ecm-frontend/e2e/startup-visibility-sla.mock.spec.ts`
  - 新增双场景：
    - `Startup SLA: login route visible under threshold (mocked)`
    - `Startup SLA: browse root visible under threshold (mocked)`
  - 支持阈值环境变量：
    - `ECM_E2E_STARTUP_LOGIN_SLA_MS`（默认 12000）
    - `ECM_E2E_STARTUP_BROWSE_SLA_MS`（默认 15000）
  - 用例输出 `startup_sla:*` 实测样本，便于快速诊断。
- `scripts/phase5-regression.sh`
  - mocked 集合新增 `startup-visibility-sla` spec。
  - 汇总新增 `phase5_regression: startup SLA samples` 段落。
- 验证：
  - `bash scripts/phase5-regression.sh` -> PASS (`23 passed`)
  - `DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS
  - `npm test -- --watch=false --runInBand src/App.test.tsx src/components/layout/AppErrorBoundary.test.tsx` -> PASS (`6 tests`)
  - `npm run lint` -> PASS

### 41) Startup SLA OK/WARN 摘要与 Gate 失败提示聚合（Phase95）
- `scripts/phase5-regression.sh`
  - 新增 `phase5_regression: startup SLA status` 聚合段，输出 `OK/WARN`：
    - `WARN` 条件：超过阈值或接近阈值（`>= 80%`）。
  - 新增 `phase5_regression: startup SLA warning count: N`。
  - 解析加固：仅识别严格结构化 `startup_sla:*` 样本行，避免失败堆栈源码行误判。
- `scripts/phase5-phase6-delivery-gate.sh`
  - 失败提示聚合增加 startup SLA warning 信号：
    - 检测 `phase5_regression: startup SLA warning count: [1-9]+`
    - 输出提示：
      - `Startup visibility SLA warnings detected. Review 'phase5_regression: startup SLA status' for near-threshold routes.`
- 验证：
  - `bash scripts/phase5-regression.sh` -> PASS（含 `startup SLA status` 与 warning count 输出）
  - `DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS
  - 受控失败验证：
    - `ECM_E2E_STARTUP_LOGIN_SLA_MS=100 ECM_E2E_STARTUP_BROWSE_SLA_MS=100 DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh`
    - expected FAIL，且 gate failure hints 成功输出 startup SLA warning 提示

### 42) Startup SLA 基线漂移告警与 Gate 漂移提示（Phase96）
- `scripts/phase5-regression.sh`
  - 新增 `phase5_regression: startup SLA drift vs baseline` 聚合段。
  - 新增 `phase5_regression: startup SLA drift warning count: N`。
  - baseline 支持 env 覆盖：
    - `ECM_STARTUP_SLA_BASELINE_LOGIN_VISIBLE_MS` / `ECM_STARTUP_SLA_BASELINE_LOGIN_MS`
    - `ECM_STARTUP_SLA_BASELINE_BROWSE_VISIBLE_MS` / `ECM_STARTUP_SLA_BASELINE_BROWSE_MS`
  - 默认 baseline：
    - `login_visible=1500ms`
    - `browse_visible=1800ms`
- `scripts/phase5-phase6-delivery-gate.sh`
  - 失败提示聚合新增 drift warning 信号检测：
    - `phase5_regression: startup SLA drift warning count: [1-9]+`
  - 新增提示：
    - `Startup latency drift warnings detected. Compare against baseline and investigate runtime variance/regression.`
- 验证：
  - `bash scripts/phase5-regression.sh` -> PASS（含 drift summary / drift warning count）
  - `DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS
  - 受控失败验证：
    - `ECM_E2E_STARTUP_LOGIN_SLA_MS=100 ECM_E2E_STARTUP_BROWSE_SLA_MS=100 ECM_STARTUP_SLA_BASELINE_LOGIN_MS=100 ECM_STARTUP_SLA_BASELINE_BROWSE_MS=100 DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh`
    - expected FAIL，且 gate failure hints 成功输出 startup drift warning 提示

### 43) App 崩溃恢复登录原因透传与登录页状态卡（Phase97）
- `ecm-frontend/src/constants/auth.ts`
  - 新增恢复状态常量：`AUTH_INIT_STATUS_APP_RECOVERY='app_recovery'`。
- `ecm-frontend/src/components/layout/AppErrorBoundary.tsx`
  - `Back to Login` 动作增强：
    - 写入 `sessionStorage.ecm_auth_init_status=app_recovery`
    - 清理登录进行中标记与 redirect reason
    - 跳转 `/login?reason=app_recovery`
- `ecm-frontend/src/components/auth/Login.tsx`
  - 登录页状态卡新增 `app_recovery` 分支：
    - 标题：`Recovered from unexpected app error`
    - 文案：提示运行时错误后已回到登录页，请重新登录。
  - 兼容来源：`sessionStorage` / `localStorage` reason / query reason。
- 测试覆盖：
  - `ecm-frontend/src/components/auth/Login.test.tsx`
    - 新增 `app_recovery` 状态与 query reason 两个单测场景。
  - `ecm-frontend/e2e/app-error-boundary-recovery.mock.spec.ts`
    - 新增断言：`Back to Login` 后登录状态卡显示 app recovery 提示文案。
- 验证：
  - `CI=1 npm test -- --runInBand --watch=false src/components/auth/Login.test.tsx src/components/layout/AppErrorBoundary.test.tsx` -> PASS
  - `npx playwright test e2e/app-error-boundary-recovery.mock.spec.ts --project=chromium --workers=1` -> PASS
  - `bash scripts/phase5-regression.sh` -> PASS

### 44) AppErrorBoundary 全局噪音过滤（Phase98）
- `ecm-frontend/src/components/layout/AppErrorBoundary.tsx`
  - 新增全局非致命噪音过滤：
    - `ResizeObserver loop limit exceeded`
    - `ResizeObserver loop completed with undelivered notifications`
    - `AbortError` / canceled(`ERR_CANCELED`) 异步拒绝
  - 对上述噪音仅记录 warning，不再切换到全页 fatal fallback。
- `ecm-frontend/src/components/layout/AppErrorBoundary.test.tsx`
  - 新增两个回归用例：
    - `ResizeObserver` 噪音不触发 fallback
    - abort-like unhandled rejection 不触发 fallback
- 验证：
  - `CI=1 npm test -- --runInBand --watch=false src/components/layout/AppErrorBoundary.test.tsx src/components/auth/Login.test.tsx` -> PASS (`20 tests`)
  - `npm run lint` -> PASS
  - `bash scripts/phase5-regression.sh` -> PASS (`23 passed`)

### 45) AppErrorBoundary 噪音过滤纳入 mocked 门禁（Phase99）
- 新增 `ecm-frontend/e2e/app-error-boundary-noise-filter.mock.spec.ts`
  - 覆盖：
    - `ResizeObserver` 全局噪音不触发 fatal fallback
    - abort/canceled unhandled rejection 不触发 fatal fallback
- `scripts/phase5-regression.sh`
  - 默认 mocked spec 集合新增 `app-error-boundary-noise-filter`。
  - 回归总用例数：`23 -> 25`。
- 验证：
  - `ECM_UI_URL=http://localhost:5500 npx playwright test e2e/app-error-boundary-noise-filter.mock.spec.ts --project=chromium --workers=1` -> PASS (`2 passed`)
  - `bash scripts/phase5-regression.sh` -> PASS (`25 passed`)

### 46) AppErrorBoundary 分包加载失败恢复提示（Phase100）
- `ecm-frontend/src/components/layout/AppErrorBoundary.tsx`
  - 新增错误分类：`generic | chunk_load`。
  - 新增 chunk-load 识别（`Loading chunk ... failed` / `ChunkLoadError` / dynamic import 失败等）。
  - chunk-load 情况下 fallback 增加专用提示：
    - `Application files may be outdated after an update. Reload to fetch the latest assets.`
  - chunk-load 情况下 `Reload` 改为 cache-busting 导航（追加 `_ecm_reload=<ts>`）。
- `ecm-frontend/src/components/layout/AppErrorBoundary.test.tsx`
  - 新增：
    - chunk-load 提示文案回归测试
    - cache-busting URL helper 测试
- 新增 `ecm-frontend/e2e/app-error-boundary-chunk-load-recovery.mock.spec.ts`
  - 覆盖 chunk-load-like unhandled rejection 时的恢复提示展示。
- `scripts/phase5-regression.sh`
  - mocked 集合纳入上述新 spec。
  - 回归总用例数：`25 -> 26`。
- 验证：
  - `CI=1 npm test -- --runInBand --watch=false src/components/layout/AppErrorBoundary.test.tsx src/components/auth/Login.test.tsx` -> PASS (`22 tests`)
  - `npm run lint` -> PASS
  - `bash scripts/phase5-regression.sh` -> PASS (`26 passed`)

### 47) Chunk-load cache-busting reload 行为 E2E 加固（Phase101）
- `ecm-frontend/e2e/app-error-boundary-chunk-load-recovery.mock.spec.ts`
  - 新增场景：
    - `App error boundary: chunk-load reload uses cache-busting query (mocked)`
  - 断言点击 fallback `Reload` 后 URL 包含 `_ecm_reload=<ts>`，并回到可用登录页。
- `scripts/phase5-regression.sh`
  - 复用同一 spec（无需新增脚本项），因 spec 增加一条用例，mocked 总数 `26 -> 27`。
- 验证：
  - `ECM_UI_URL=http://localhost:5500 npx playwright test e2e/app-error-boundary-chunk-load-recovery.mock.spec.ts --project=chromium --workers=1` -> PASS (`2 passed`)
  - `bash scripts/phase5-regression.sh` -> PASS (`27 passed`)

### 48) 启动白屏静态兜底 watchdog（Phase102）
- `ecm-frontend/public/index.html`
  - 新增 pre-React 启动 watchdog：
    - 启动超时且页面仍空白时，显示全屏兜底卡片。
    - 提供 `Reload` / `Back to Login` 操作，降低白屏不可恢复风险。
  - 新增 E2E 开关：
    - `ecm_e2e_force_bootstrap_blank`
    - `ecm_e2e_bootstrap_fallback_ms`
- `ecm-frontend/src/index.tsx`
  - 新增 `markAppRendered()` 与静态 watchdog 对接，`renderAuthBooting/renderApp` 后标记已渲染以取消兜底定时器。
- 新增 `ecm-frontend/e2e/bootstrap-startup-fallback.mock.spec.ts`
  - 覆盖强制白屏场景下的 fallback 展示与 `Back to Login` 恢复路径。
- `scripts/phase5-regression.sh`
  - 纳入 `bootstrap-startup-fallback` mocked spec。
  - mocked 总数：`27 -> 28`。
- 验证：
  - `ECM_UI_URL=http://localhost:5500 npx playwright test e2e/bootstrap-startup-fallback.mock.spec.ts --project=chromium --workers=1` -> PASS (`1 passed`)
  - `npm run lint` -> PASS
  - `bash scripts/phase5-regression.sh` -> PASS (`28 passed`)

### 49) 启动 fallback 误触发防护（Phase103）
- `ecm-frontend/e2e/bootstrap-startup-fallback.mock.spec.ts`
  - 新增反向场景：
    - `Startup fallback: normal startup does not show fallback overlay`
  - 校验普通登录启动路径在超时窗口后仍不出现 fallback overlay。
- `scripts/phase5-regression.sh`
  - 继续复用同一 spec，默认 mocked 用例总数 `28 -> 29`。
- 验证：
  - `ECM_UI_URL=http://localhost:5500 npx playwright test e2e/bootstrap-startup-fallback.mock.spec.ts --project=chromium --workers=1` -> PASS (`2 passed`)
  - `bash scripts/phase5-regression.sh` -> PASS (`29 passed`)

### 50) Recovery 事件摘要与 gate 提示联动（Phase104）
- `ecm-frontend/e2e/app-error-boundary-chunk-load-recovery.mock.spec.ts`
  - 新增结构化 marker：
    - `recovery_event:chunk_load_hint_shown`
    - `recovery_event:chunk_load_reload_cache_bust`
- `ecm-frontend/e2e/bootstrap-startup-fallback.mock.spec.ts`
  - 新增结构化 marker：
    - `recovery_event:startup_fallback_overlay_shown`
    - `recovery_event:startup_fallback_back_to_login`
    - `recovery_event:startup_fallback_not_shown_normal`
- `scripts/phase5-regression.sh`
  - 新增 recovery marker 聚合输出：
    - `phase5_regression: recovery events`
    - `phase5_regression: recovery guard status`
    - `phase5_regression: recovery guard warning count: N`
- `scripts/phase5-phase6-delivery-gate.sh`
  - startup hints 新增 recovery-guard 缺失信号检测与提示语。
- 验证：
  - `bash scripts/phase5-regression.sh` -> PASS (`29 passed`，`recovery guard warning count: 0`)
  - `DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS

### 51) Recovery guard 在 fail-fast 场景下固定输出（Phase105）
- `scripts/phase5-regression.sh`
  - recovery 聚合逻辑改为始终输出（不再依赖 `recovery_event` 是否存在）。
  - 即使无 marker，也会固定打印：
    - `phase5_regression: recovery events`
    - `phase5_regression: recovery guard status`
    - `phase5_regression: recovery guard warning count: N`
  - 无 marker 时显示 ` - (none)`，并对期望事件逐条输出 `WARN missing event`。
- 交付影响：
  - 让 `phase5-phase6-delivery-gate` 在早失败/截断日志场景仍可稳定消费 recovery guard 警告信号。
- 验证：
  - `bash scripts/phase5-regression.sh` -> PASS (`29 passed`，输出 recovery guard 三行摘要)
  - `DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS

### 52) 启动恢复原因拆分（Phase106）
- `ecm-frontend/src/constants/auth.ts`
  - 新增 `AUTH_INIT_STATUS_STARTUP_RECOVERY = 'startup_recovery'`。
- `ecm-frontend/public/index.html`
  - 启动 fallback 的 `Back to Login` 改为写入 `sessionStorage.ecm_auth_init_status=startup_recovery`，并跳转：
    - `/login?reason=startup_recovery`
- `ecm-frontend/src/components/auth/Login.tsx`
  - 登录恢复提示新增 startup 分支（与 app runtime crash 恢复分离）：
    - 标题：`Recovered from startup timeout`
    - 说明：`App startup took too long and switched to sign-in recovery. Please sign in again.`
- `ecm-frontend/src/components/auth/Login.test.tsx`
  - 新增 startup recovery（session/query）提示回归测试。
- `ecm-frontend/e2e/bootstrap-startup-fallback.mock.spec.ts`
  - 更新断言：回登录 URL reason 为 `startup_recovery`，并校验启动恢复专属提示。
- 验证：
  - `CI=1 npm test -- --runTestsByPath src/components/auth/Login.test.tsx` -> PASS (`17 passed`)
  - `bash scripts/phase5-regression.sh` -> PASS (`29 passed`)
  - `DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS

### 53) 启动 fallback reload cache-bust 加固（Phase107）
- `ecm-frontend/public/index.html`
  - 启动 fallback `Reload` 动作改为优先跳转到带 `_ecm_reload=<ts>` 的 URL（无法构造 URL 时回退 `window.location.reload()`）。
- `ecm-frontend/e2e/bootstrap-startup-fallback.mock.spec.ts`
  - 新增场景：
    - `Startup fallback: reload uses cache-busting query and restores login shell`
  - 校验点击 `Reload` 后 URL 包含 `_ecm_reload=<ts>` 且登录页可见。
  - 新增结构化 marker：
    - `recovery_event:startup_fallback_reload_cache_bust`
- `scripts/phase5-regression.sh`
  - recovery guard 期望事件集合新增 `startup_fallback_reload_cache_bust`。
- 验证：
  - `bash scripts/phase5-regression.sh` -> PASS (`30 passed`，`startup_fallback_reload_cache_bust` 已计入 recovery events)
  - `DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS

### 54) AppErrorBoundary 恢复事件纳入 recovery guard（Phase108）
- `ecm-frontend/e2e/app-error-boundary-recovery.mock.spec.ts`
  - 新增结构化 marker：
    - `recovery_event:app_error_overlay_shown`
    - `recovery_event:app_error_back_to_login`
- `scripts/phase5-regression.sh`
  - recovery guard 期望事件集合新增：
    - `app_error_overlay_shown`
    - `app_error_back_to_login`
- 交付影响：
  - recovery telemetry 覆盖从 startup/chunk 扩展到 runtime app-error 恢复路径，缺失检测更完整。
- 验证：
  - `bash scripts/phase5-regression.sh` -> PASS (`30 passed`，recovery events 包含 app_error 两项，`recovery guard warning count: 0`)
  - `DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS

### 55) Delivery gate recovery 缺失事件提示增强（Phase109）
- `scripts/phase5-phase6-delivery-gate.sh`
  - `print_startup_failure_hints` 新增 missing-event 抽取逻辑：
    - 从 `phase5_regression` 输出中解析 `WARN missing event: <name>`
    - 去重后输出最多 8 个缺失事件名
  - recovery guard 警告提示升级为：
    - 有名称时：`Missing events: ...`
    - 无名称时：保持原有通用提示（向后兼容）
- 验证：
  - `bash -n scripts/phase5-phase6-delivery-gate.sh` -> PASS
  - `DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS（通过路径无回归）

### 56) Login 恢复参数精准清理与 cache-bust 断言对齐（Phase110）
- `ecm-frontend/src/components/auth/Login.tsx`
  - 新增临时恢复参数清理 helper，只移除：
    - `reason`
    - `_ecm_reload`
  - 保留其余 query 参数与 hash，避免误清空登录页上下文参数。
- `ecm-frontend/src/components/auth/Login.test.tsx`
  - 新增回归：
    - `reason` 清理但保留 `source` + hash
    - `_ecm_reload` 清理但保留其他 query
- `ecm-frontend/e2e/bootstrap-startup-fallback.mock.spec.ts`
- `ecm-frontend/e2e/app-error-boundary-chunk-load-recovery.mock.spec.ts`
  - reload cache-bust 场景断言更新为：
    - 先命中 `_ecm_reload`
    - 再确认最终 URL 已清理该参数
- 验证：
  - `CI=1 npm test -- --runTestsByPath src/components/auth/Login.test.tsx` -> PASS (`19 passed`)
  - `bash scripts/phase5-regression.sh` -> PASS (`30 passed`)
  - `DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS

### 57) Auth boot watchdog 恢复事件纳入 recovery guard（Phase111）
- `ecm-frontend/e2e/auth-boot-watchdog-recovery.mock.spec.ts`
  - 新增结构化 marker：
    - `recovery_event:auth_boot_watchdog_alert_shown`
    - `recovery_event:auth_boot_watchdog_continue_login`
- `scripts/phase5-regression.sh`
  - recovery guard 期望事件集合新增：
    - `auth_boot_watchdog_alert_shown`
    - `auth_boot_watchdog_continue_login`
- 交付影响：
  - recovery telemetry 覆盖补齐 auth boot watchdog 恢复路径，guard 缺失检测更完整。
- 验证：
  - `bash scripts/phase5-regression.sh` -> PASS (`30 passed`，recovery events 包含 auth_boot_watchdog 两项，`recovery guard warning count: 0`)
  - `DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS

### 58) Auth storage 受限恢复事件纳入 recovery guard（Phase112）
- `ecm-frontend/e2e/auth-storage-restricted-recovery.mock.spec.ts`
  - 新增结构化 marker：
    - `recovery_event:auth_storage_restricted_browse_recovered`
    - `recovery_event:auth_storage_restricted_login_notice_visible`
- `scripts/phase5-regression.sh`
  - recovery guard 期望事件集合新增：
    - `auth_storage_restricted_browse_recovered`
    - `auth_storage_restricted_login_notice_visible`
- 交付影响：
  - recovery telemetry 覆盖补齐 storage-restricted auth 恢复路径，guard 缺失检测完整性进一步提升。
- 验证：
  - `bash scripts/phase5-regression.sh` -> PASS (`30 passed`，recovery events 包含 auth_storage_restricted 两项，`recovery guard warning count: 0`)
  - `DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS

### 59) FileBrowser/FolderTree watchdog 恢复事件纳入 recovery guard（Phase113）
- `ecm-frontend/e2e/filebrowser-loading-watchdog.mock.spec.ts`
  - 新增结构化 marker：
    - `recovery_event:filebrowser_watchdog_alert_shown`
    - `recovery_event:filebrowser_watchdog_retry_recovered`
- `ecm-frontend/e2e/folder-tree-root-watchdog.mock.spec.ts`
  - 新增结构化 marker：
    - `recovery_event:folder_tree_watchdog_alert_shown`
    - `recovery_event:folder_tree_watchdog_retry_recovered`
- `scripts/phase5-regression.sh`
  - recovery guard 期望事件集合新增上述 4 项 watchdog 事件。
- 交付影响：
  - recovery telemetry 覆盖补齐文件浏览与目录树 watchdog 恢复路径，guard 可见性更完整。
- 验证：
  - `bash scripts/phase5-regression.sh` -> PASS (`30 passed`，recovery events 包含 filebrowser/folder_tree watchdog 四项，`recovery guard warning count: 0`)
  - `DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS

### 60) Route fallback 恢复事件纳入 recovery guard（Phase114）
- `ecm-frontend/e2e/route-fallback-no-blank.mock.spec.ts`
  - 新增结构化 marker：
    - `recovery_event:route_fallback_unauth_login_visible`
    - `recovery_event:route_fallback_auth_browse_visible`
- `scripts/phase5-regression.sh`
  - recovery guard 期望事件集合新增：
    - `route_fallback_unauth_login_visible`
    - `route_fallback_auth_browse_visible`
- 交付影响：
  - recovery telemetry 覆盖补齐 unknown-route 回退恢复路径，guard 对无白屏回退链路可见性更完整。
- 验证：
  - `bash scripts/phase5-regression.sh` -> PASS (`30 passed`，recovery events 包含 route_fallback 两项，`recovery guard warning count: 0`)
  - `DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS

### 61) Auth session 恢复事件纳入 recovery guard（Phase115）
- `ecm-frontend/e2e/auth-session-recovery.mock.spec.ts`
  - 新增结构化 marker：
    - `recovery_event:auth_session_transient_retry_success`
    - `recovery_event:auth_session_terminal_redirect_login`
- `scripts/phase5-regression.sh`
  - recovery guard 期望事件集合新增：
    - `auth_session_transient_retry_success`
    - `auth_session_terminal_redirect_login`
- 交付影响：
  - recovery telemetry 覆盖补齐 auth-session recoverable/terminal 两类结果，guard 事件完整性进一步提升。
- 验证：
  - `bash scripts/phase5-regression.sh` -> PASS (`30 passed`，recovery events 包含 auth_session 两项，`recovery guard warning count: 0`)
  - `DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS

### 62) Search recoverable 恢复事件纳入 recovery guard（Phase116）
- `ecm-frontend/e2e/search-suggestions-save-search.mock.spec.ts`
  - 在 temporary failure recovery 场景新增结构化 marker：
    - `recovery_event:search_recoverable_error_alert_shown`
    - `recovery_event:search_recoverable_retry_success`
- `scripts/phase5-regression.sh`
  - recovery guard 期望事件集合新增：
    - `search_recoverable_error_alert_shown`
    - `search_recoverable_retry_success`
- 交付影响：
  - recovery telemetry 覆盖补齐搜索可恢复错误从告警到重试成功的闭环路径。
- 验证：
  - `bash scripts/phase5-regression.sh` -> PASS (`30 passed`，recovery events 包含 search_recoverable 两项，`recovery guard warning count: 0`)
  - `DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS

### 63) App error noise 忽略事件纳入 recovery guard（Phase117）
- `ecm-frontend/e2e/app-error-boundary-noise-filter.mock.spec.ts`
  - 新增结构化 marker：
    - `recovery_event:app_error_noise_resize_observer_ignored`
    - `recovery_event:app_error_noise_abort_rejection_ignored`
- `scripts/phase5-regression.sh`
  - recovery guard 期望事件集合新增：
    - `app_error_noise_resize_observer_ignored`
    - `app_error_noise_abort_rejection_ignored`
- 交付影响：
  - recovery telemetry 覆盖补齐 app-error 全局噪声误报抑制路径，guard 能更早发现噪声过滤回归。
- 验证：
  - `bash scripts/phase5-regression.sh` -> PASS (`30 passed`，recovery events 包含 app_error_noise 两项，`recovery guard warning count: 0`)
  - `DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS

### 64) Recovery guard strict 模式与 unexpected-event 检测（Phase118）
- `scripts/phase5-regression.sh`
  - 新增 `PHASE5_RECOVERY_GUARD_STRICT`（默认 `0`）：
    - `0`: 保持兼容，仅输出告警
    - `1`: guard 有告警时非零退出（严格模式）
  - recovery guard 新增 unexpected-event 检测（观测到但不在 expected 列表中的事件）。
  - `recovery guard warning count` 现在聚合 missing + unexpected 两类告警。
- 交付影响：
  - 在不破坏默认行为前提下，支持按需开启更严格的 telemetry guard 约束。
- 验证：
  - `PHASE5_RECOVERY_GUARD_STRICT=1 bash scripts/phase5-regression.sh` -> PASS (`30 passed`，`recovery guard warning count: 0`)
  - `PHASE5_RECOVERY_GUARD_STRICT=1 DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS

### 65) Delivery gate recovery 提示扩展 unexpected-event 明细（Phase119）
- `scripts/phase5-phase6-delivery-gate.sh`
  - `print_startup_failure_hints` 扩展：
    - 继续解析并提示 missing events
    - 新增解析并提示 unexpected events
  - 提示文案支持三种组合：
    - Missing + Unexpected
    - Missing only
    - Unexpected only
- 交付影响：
  - 当 strict recovery guard 失败时，交付门禁能直接提供更完整的异常事件诊断线索。
- 验证：
  - `bash -n scripts/phase5-phase6-delivery-gate.sh` -> PASS
  - `PHASE5_RECOVERY_GUARD_STRICT=1 DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS

### 66) Recovery expected-events 外置清单 + 清单一致性校验（Phase120）
- 新增清单文件：
  - `ecm-frontend/e2e/recovery-events.expected.txt`
  - 统一维护 mocked regression recovery guard 的 expected event 集合。
- `scripts/phase5-regression.sh`
  - 新增参数：
    - `PHASE5_RECOVERY_EVENTS_FILE`（默认 `e2e/recovery-events.expected.txt`）
    - `PHASE5_RECOVERY_REGISTRY_STRICT`（默认跟随 `PHASE5_RECOVERY_GUARD_STRICT`）
  - 在回归执行前新增清单一致性检查：
    - 比对 `PHASE5_SPECS` 中观测到的 `recovery_event:*` 与清单内容
    - 输出 mismatch 明细（marker missing from file / file entry not found in specs）
    - strict 模式下可 fail-fast。
  - recovery guard expected set 改为从清单文件加载，并输出实际来源与条目数。
- 交付影响：
  - recovery guard 维护从脚本硬编码迁移为文件驱动，减少后续事件扩展时的遗漏风险。
- 验证：
  - `bash -n scripts/phase5-regression.sh` -> PASS
  - `PHASE5_RECOVERY_GUARD_STRICT=1 PHASE5_RECOVERY_REGISTRY_STRICT=1 bash scripts/phase5-regression.sh` -> PASS (`30 passed`)
  - `PHASE5_RECOVERY_GUARD_STRICT=1 PHASE5_RECOVERY_REGISTRY_STRICT=1 DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS

### 67) Delivery gate 增加 mocked registry 快速预检阶段（Phase121）
- `scripts/phase5-regression.sh`
  - 新增 `PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY`（默认 `0`）：
    - `1` 时仅执行 recovery registry 校验并提前返回，不跑 build/Playwright。
- `scripts/phase5-phase6-delivery-gate.sh`
  - fast 层新增 stage：
    - `mocked recovery registry preflight`
  - 顺序调整为：
    1. registry preflight
    2. mocked regression gate
  - preflight 失败时，明确跳过 mocked regression 并快速失败。
- 交付影响：
  - registry 漂移场景将以更低成本、更明确阶段定位失败原因。
- 验证：
  - `PHASE5_RECOVERY_GUARD_STRICT=1 PHASE5_RECOVERY_REGISTRY_STRICT=1 PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY=1 bash scripts/phase5-regression.sh` -> PASS
  - `PHASE5_RECOVERY_GUARD_STRICT=1 PHASE5_RECOVERY_REGISTRY_STRICT=1 DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS（fast 层含 preflight + mocked 两个 PASS）

### 68) Delivery gate startup hints 支持 registry mismatch 明细（Phase122）
- `scripts/phase5-phase6-delivery-gate.sh`
  - `print_startup_failure_hints` 新增 registry mismatch 解析：
    - `WARN marker missing from events file: ...`
    - `WARN events file entry not found in specs: ...`
  - 增加专用提示文案：
    - Missing in events file
    - Stale entries
    - Mixed case（missing + stale）
- 交付影响：
  - registry preflight 失败时，交付门禁可直接输出更具体的修复线索。
- 验证：
  - `bash -n scripts/phase5-phase6-delivery-gate.sh` -> PASS
  - `PHASE5_RECOVERY_GUARD_STRICT=1 PHASE5_RECOVERY_REGISTRY_STRICT=1 DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS

### 69) Recovery registry 同步生成模式（Phase123）
- `scripts/phase5-regression.sh`
  - 新增 `PHASE5_RECOVERY_REGISTRY_SYNC`（默认 `0`）：
    - `1` 时从 `PHASE5_SPECS` 中扫描 `recovery_event:*` 并重写 `PHASE5_RECOVERY_EVENTS_FILE`。
  - 输出同步摘要（文件路径 + 事件数），随后执行既有 registry 校验。
- `ecm-frontend/e2e/recovery-events.expected.txt`
  - 使用生成头注释 + 排序后的 canonical 列表。
- 交付影响：
  - 新增 recovery marker 后可一键重建清单，减少手工维护遗漏。
- 验证：
  - `PHASE5_RECOVERY_REGISTRY_SYNC=1 PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY=1 bash scripts/phase5-regression.sh` -> PASS
  - `PHASE5_RECOVERY_GUARD_STRICT=1 PHASE5_RECOVERY_REGISTRY_STRICT=1 DELIVERY_GATE_MODE=mocked PW_WORKERS=1 bash scripts/phase5-phase6-delivery-gate.sh` -> PASS

## 三、提交记录
- `eb31c92` feat(frontend): harden auth session recovery and add e2e coverage
- `388c254` chore(scripts): auto-start phase5 regression server on custom localhost ports
- （本次继续）phase5-phase6 delivery gate full-stack UI auto-detect

## 四、验证结果
- 前端单测：通过（关键 auth/session 用例全绿）
- 前端 lint：通过
- 前端 build：通过
- 新增 auth session recovery Playwright：通过
- `phase5-phase6-delivery-gate.sh`：通过
  - mocked gate：12 passed
  - full-stack admin smoke：1 passed
  - phase6 mail integration smoke：1 passed
  - phase5 search suggestions integration smoke：1 passed
  - p1 smoke：5 passed，1 skipped（可选 mail preview-run 场景）

## 五、影响与兼容性
- 不涉及后端接口契约破坏。
- 现有登录流程保持兼容；新增 query reason 为向后兼容增强。
- 门禁脚本行为仅在未显式传入 `ECM_UI_URL_FULLSTACK` 时采用自动探测，显式配置优先级不变。
- `phase5-regression` 默认将优先使用临时独立静态服务进行 mocked 回归，减少本地端口污染导致的误报风险；可通过 `PHASE5_USE_EXISTING_UI=1` 关闭。
- full-stack 门禁增加静态目标策略开关，默认保持兼容；可按需启用严格模式提高分支准确性。

## 六、相关文档
- `docs/PHASE5_AUTH_SESSION_RECOVERY_DESIGN_20260216.md`
- `docs/PHASE5_AUTH_SESSION_RECOVERY_VERIFICATION_20260216.md`
- `docs/DESIGN_PHASE5_PHASE6_GATE_FULLSTACK_TARGET_AUTODETECT_20260217.md`
- `docs/VERIFICATION_PHASE5_PHASE6_GATE_FULLSTACK_TARGET_AUTODETECT_20260217.md`
- `docs/PHASE59_SPELLCHECK_FILENAME_GUARD_DEV_20260217.md`
- `docs/PHASE59_SPELLCHECK_FILENAME_GUARD_VERIFICATION_20260217.md`
- `docs/DESIGN_SETTINGS_SESSION_ACTIONS_MOCKED_20260218.md`
- `docs/VERIFICATION_SETTINGS_SESSION_ACTIONS_MOCKED_20260218.md`
- `docs/DESIGN_ROUTE_FALLBACK_NO_BLANK_PAGE_20260218.md`
- `docs/VERIFICATION_ROUTE_FALLBACK_NO_BLANK_PAGE_20260218.md`
- `docs/PHASE61_GATE_PREBUILT_SYNC_AND_ROUTE_FALLBACK_SMOKE_DEV_20260218.md`
- `docs/PHASE61_GATE_PREBUILT_SYNC_AND_ROUTE_FALLBACK_SMOKE_VERIFICATION_20260218.md`
- `docs/PHASE62_FULLSTACK_SMOKE_PREBUILT_SYNC_REUSE_DEV_20260218.md`
- `docs/PHASE62_FULLSTACK_SMOKE_PREBUILT_SYNC_REUSE_VERIFICATION_20260218.md`
- `docs/PHASE63_PREVIEW_STATUS_MIME_PARAMETER_UNSUPPORTED_DEV_20260218.md`
- `docs/PHASE63_PREVIEW_STATUS_MIME_PARAMETER_UNSUPPORTED_VERIFICATION_20260218.md`
- `docs/PHASE64_AUTH_RECOVERY_OBSERVABILITY_DEV_20260218.md`
- `docs/PHASE64_AUTH_RECOVERY_OBSERVABILITY_VERIFICATION_20260218.md`
- `docs/PHASE65_SEARCH_RECOVERABLE_ERROR_ACTIONS_DEV_20260218.md`
- `docs/PHASE65_SEARCH_RECOVERABLE_ERROR_ACTIONS_VERIFICATION_20260218.md`
- `docs/PHASE66_ROUTE_FALLBACK_AUTH_AWARE_RECOVERY_DEV_20260219.md`
- `docs/PHASE66_ROUTE_FALLBACK_AUTH_AWARE_RECOVERY_VERIFICATION_20260219.md`
- `docs/PHASE67_SETTINGS_AUTH_RECOVERY_DEBUG_TOGGLE_DEV_20260219.md`
- `docs/PHASE67_SETTINGS_AUTH_RECOVERY_DEBUG_TOGGLE_VERIFICATION_20260219.md`
- `docs/PHASE68_SEARCH_ERROR_TAXONOMY_RECOVERY_MAPPING_DEV_20260219.md`
- `docs/PHASE68_SEARCH_ERROR_TAXONOMY_RECOVERY_MAPPING_VERIFICATION_20260219.md`
- `docs/PHASE69_PREVIEW_FAILURE_OPERATOR_LOOP_DEV_20260219.md`
- `docs/PHASE69_PREVIEW_FAILURE_OPERATOR_LOOP_VERIFICATION_20260219.md`
- `docs/PHASE70_AUTH_ROUTE_E2E_MATRIX_DEV_20260219.md`
- `docs/PHASE70_AUTH_ROUTE_E2E_MATRIX_VERIFICATION_20260219.md`
- `docs/PHASE71_REGRESSION_GATE_LAYERING_DEV_20260219.md`
- `docs/PHASE71_REGRESSION_GATE_LAYERING_VERIFICATION_20260219.md`
- `docs/PHASE72_FAILURE_INJECTION_COVERAGE_DEV_20260219.md`
- `docs/PHASE72_FAILURE_INJECTION_COVERAGE_VERIFICATION_20260219.md`
- `docs/PHASE73_AUTH_SEARCH_RECOVERY_RELEASE_SUMMARY_20260219.md`
- `docs/PHASE73_AUTH_SEARCH_RECOVERY_VERIFICATION_ROLLUP_20260219.md`
- `docs/PHASE74_GATE_INTEGRATION_AUTH_ROUTE_MATRIX_DEV_20260220.md`
- `docs/PHASE74_GATE_INTEGRATION_AUTH_ROUTE_MATRIX_VERIFICATION_20260220.md`
- `docs/PHASE78_AUTH_BOOT_WATCHDOG_DEV_20260220.md`
- `docs/PHASE78_AUTH_BOOT_WATCHDOG_VERIFICATION_20260220.md`
- `docs/PHASE79_API_TIMEOUT_BUDGET_ALIGNMENT_DEV_20260220.md`
- `docs/PHASE79_API_TIMEOUT_BUDGET_ALIGNMENT_VERIFICATION_20260220.md`
- `docs/PHASE80_STARTUP_CHAOS_MATRIX_EXPANSION_DEV_20260220.md`
- `docs/PHASE80_STARTUP_CHAOS_MATRIX_EXPANSION_VERIFICATION_20260220.md`
- `docs/PHASE81_DELIVERY_GATE_STARTUP_HINTS_DEV_20260220.md`
- `docs/PHASE81_DELIVERY_GATE_STARTUP_HINTS_VERIFICATION_20260220.md`
- `docs/PHASE82_STARTUP_STABILITY_RELEASE_CLOSEOUT_20260220.md`
- `docs/REPORT_STARTUP_PARALLEL_EXECUTION_20260221.md`
- `docs/VERIFICATION_STARTUP_PARALLEL_EXECUTION_20260221.md`
- `docs/VERIFICATION_STARTUP_FULL_DELIVERY_GATE_ALL_20260221.md`
- `docs/PHASE83_AUTH_BOOT_WATCHDOG_MATRIX_INTEGRATION_DEV_20260221.md`
- `docs/PHASE83_AUTH_BOOT_WATCHDOG_MATRIX_INTEGRATION_VERIFICATION_20260221.md`
- `docs/PHASE84_AUTH_STORAGE_SAFETY_AND_SPELLCHECK_PRECISION_DEV_20260221.md`
- `docs/PHASE84_AUTH_STORAGE_SAFETY_AND_SPELLCHECK_PRECISION_VERIFICATION_20260221.md`
- `docs/PHASE85_AUTH_STORAGE_RESTRICTED_MOCK_E2E_DEV_20260221.md`
- `docs/PHASE85_AUTH_STORAGE_RESTRICTED_MOCK_E2E_VERIFICATION_20260221.md`
- `docs/PHASE86_LOGIN_AUTH_HANDOFF_STATUS_CARD_DEV_20260221.md`
- `docs/PHASE86_LOGIN_AUTH_HANDOFF_STATUS_CARD_VERIFICATION_20260221.md`
- `docs/PHASE87_SEARCH_EXACT_MATCH_MODE_VISIBILITY_DEV_20260221.md`
- `docs/PHASE87_SEARCH_EXACT_MATCH_MODE_VISIBILITY_VERIFICATION_20260221.md`
- `docs/PHASE88_PHASE5_REGRESSION_HOTSPOT_SUMMARY_DEV_20260221.md`
- `docs/PHASE88_PHASE5_REGRESSION_HOTSPOT_SUMMARY_VERIFICATION_20260221.md`
- `docs/PHASE89_INTEGRATION_PREFLIGHT_GROUPED_DIAGNOSTICS_DEV_20260221.md`
- `docs/PHASE89_INTEGRATION_PREFLIGHT_GROUPED_DIAGNOSTICS_VERIFICATION_20260221.md`
- `docs/PHASE90_RESILIENCE_CONTINUATION_RELEASE_CLOSEOUT_20260221.md`
- `docs/NEXT_7DAY_PLAN_RESILIENCE_CONTINUATION_20260221.md`
- `docs/PHASE91_FOLDER_TREE_ROOT_LOADING_WATCHDOG_DEV_20260222.md`
- `docs/PHASE91_FOLDER_TREE_ROOT_LOADING_WATCHDOG_VERIFICATION_20260222.md`
- `docs/PHASE92_APP_ERROR_BOUNDARY_RECOVERY_E2E_DEV_20260223.md`
- `docs/PHASE92_APP_ERROR_BOUNDARY_RECOVERY_E2E_VERIFICATION_20260223.md`
- `docs/PHASE93_ROUTE_FALLBACK_NO_BLANK_MOCK_E2E_DEV_20260223.md`
- `docs/PHASE93_ROUTE_FALLBACK_NO_BLANK_MOCK_E2E_VERIFICATION_20260223.md`
- `docs/PHASE94_STARTUP_VISIBILITY_SLA_MOCKED_GATE_DEV_20260223.md`
- `docs/PHASE94_STARTUP_VISIBILITY_SLA_MOCKED_GATE_VERIFICATION_20260223.md`
- `docs/PHASE95_STARTUP_SLA_WARN_SUMMARY_AND_GATE_HINTS_DEV_20260223.md`
- `docs/PHASE95_STARTUP_SLA_WARN_SUMMARY_AND_GATE_HINTS_VERIFICATION_20260223.md`
- `docs/PHASE96_STARTUP_SLA_DRIFT_BASELINE_WARNINGS_DEV_20260223.md`
- `docs/PHASE96_STARTUP_SLA_DRIFT_BASELINE_WARNINGS_VERIFICATION_20260223.md`
- `docs/PHASE97_APP_ERROR_RECOVERY_LOGIN_REASON_HANDOFF_DEV_20260224.md`
- `docs/PHASE97_APP_ERROR_RECOVERY_LOGIN_REASON_HANDOFF_VERIFICATION_20260224.md`
- `docs/PHASE98_APP_ERROR_BOUNDARY_GLOBAL_NOISE_FILTERING_DEV_20260224.md`
- `docs/PHASE98_APP_ERROR_BOUNDARY_GLOBAL_NOISE_FILTERING_VERIFICATION_20260224.md`
- `docs/PHASE99_APP_ERROR_BOUNDARY_NOISE_FILTER_MOCK_GATE_DEV_20260224.md`
- `docs/PHASE99_APP_ERROR_BOUNDARY_NOISE_FILTER_MOCK_GATE_VERIFICATION_20260224.md`
- `docs/PHASE100_APP_ERROR_BOUNDARY_CHUNK_LOAD_RECOVERY_DEV_20260224.md`
- `docs/PHASE100_APP_ERROR_BOUNDARY_CHUNK_LOAD_RECOVERY_VERIFICATION_20260224.md`
- `docs/PHASE101_CHUNK_LOAD_CACHE_BUST_RELOAD_E2E_DEV_20260224.md`
- `docs/PHASE101_CHUNK_LOAD_CACHE_BUST_RELOAD_E2E_VERIFICATION_20260224.md`
- `docs/PHASE102_STARTUP_BLANK_SCREEN_FALLBACK_WATCHDOG_DEV_20260224.md`
- `docs/PHASE102_STARTUP_BLANK_SCREEN_FALLBACK_WATCHDOG_VERIFICATION_20260224.md`
- `docs/PHASE103_STARTUP_FALLBACK_FALSE_POSITIVE_GUARD_DEV_20260224.md`
- `docs/PHASE103_STARTUP_FALLBACK_FALSE_POSITIVE_GUARD_VERIFICATION_20260224.md`
- `docs/PHASE104_RECOVERY_EVENT_TELEMETRY_SUMMARY_DEV_20260224.md`
- `docs/PHASE104_RECOVERY_EVENT_TELEMETRY_SUMMARY_VERIFICATION_20260224.md`
- `docs/PHASE105_RECOVERY_GUARD_FAILFAST_SUMMARY_DEV_20260224.md`
- `docs/PHASE105_RECOVERY_GUARD_FAILFAST_SUMMARY_VERIFICATION_20260224.md`
- `docs/PHASE106_STARTUP_RECOVERY_REASON_SPLIT_DEV_20260224.md`
- `docs/PHASE106_STARTUP_RECOVERY_REASON_SPLIT_VERIFICATION_20260224.md`
- `docs/PHASE107_STARTUP_FALLBACK_RELOAD_CACHE_BUST_DEV_20260224.md`
- `docs/PHASE107_STARTUP_FALLBACK_RELOAD_CACHE_BUST_VERIFICATION_20260224.md`
- `docs/PHASE108_APP_ERROR_RECOVERY_EVENT_COVERAGE_DEV_20260224.md`
- `docs/PHASE108_APP_ERROR_RECOVERY_EVENT_COVERAGE_VERIFICATION_20260224.md`
- `docs/PHASE109_GATE_RECOVERY_HINT_MISSING_EVENTS_DEV_20260224.md`
- `docs/PHASE109_GATE_RECOVERY_HINT_MISSING_EVENTS_VERIFICATION_20260224.md`
- `docs/PHASE110_LOGIN_TRANSIENT_QUERY_CLEANUP_DEV_20260224.md`
- `docs/PHASE110_LOGIN_TRANSIENT_QUERY_CLEANUP_VERIFICATION_20260224.md`
- `docs/PHASE111_AUTH_BOOT_RECOVERY_EVENT_COVERAGE_DEV_20260224.md`
- `docs/PHASE111_AUTH_BOOT_RECOVERY_EVENT_COVERAGE_VERIFICATION_20260224.md`
- `docs/PHASE112_AUTH_STORAGE_RECOVERY_EVENT_COVERAGE_DEV_20260224.md`
- `docs/PHASE112_AUTH_STORAGE_RECOVERY_EVENT_COVERAGE_VERIFICATION_20260224.md`
- `docs/PHASE113_FILE_TREE_WATCHDOG_RECOVERY_EVENTS_DEV_20260224.md`
- `docs/PHASE113_FILE_TREE_WATCHDOG_RECOVERY_EVENTS_VERIFICATION_20260224.md`
- `docs/PHASE114_ROUTE_FALLBACK_RECOVERY_EVENT_COVERAGE_DEV_20260225.md`
- `docs/PHASE114_ROUTE_FALLBACK_RECOVERY_EVENT_COVERAGE_VERIFICATION_20260225.md`
- `docs/PHASE115_AUTH_SESSION_RECOVERY_EVENT_COVERAGE_DEV_20260225.md`
- `docs/PHASE115_AUTH_SESSION_RECOVERY_EVENT_COVERAGE_VERIFICATION_20260225.md`
- `docs/PHASE116_SEARCH_RECOVERABLE_EVENT_COVERAGE_DEV_20260225.md`
- `docs/PHASE116_SEARCH_RECOVERABLE_EVENT_COVERAGE_VERIFICATION_20260225.md`
- `docs/PHASE117_APP_ERROR_NOISE_EVENT_COVERAGE_DEV_20260225.md`
- `docs/PHASE117_APP_ERROR_NOISE_EVENT_COVERAGE_VERIFICATION_20260225.md`
- `docs/PHASE118_RECOVERY_GUARD_STRICT_MODE_DEV_20260225.md`
- `docs/PHASE118_RECOVERY_GUARD_STRICT_MODE_VERIFICATION_20260225.md`
- `docs/PHASE119_GATE_RECOVERY_HINT_UNEXPECTED_EVENTS_DEV_20260225.md`
- `docs/PHASE119_GATE_RECOVERY_HINT_UNEXPECTED_EVENTS_VERIFICATION_20260225.md`
- `docs/PHASE120_RECOVERY_EVENT_REGISTRY_EXTERNALIZATION_DEV_20260225.md`
- `docs/PHASE120_RECOVERY_EVENT_REGISTRY_EXTERNALIZATION_VERIFICATION_20260225.md`
- `docs/PHASE121_MOCKED_REGISTRY_PREFLIGHT_STAGE_DEV_20260225.md`
- `docs/PHASE121_MOCKED_REGISTRY_PREFLIGHT_STAGE_VERIFICATION_20260225.md`
- `docs/PHASE122_GATE_REGISTRY_MISMATCH_HINTS_DEV_20260225.md`
- `docs/PHASE122_GATE_REGISTRY_MISMATCH_HINTS_VERIFICATION_20260225.md`
- `docs/PHASE123_RECOVERY_REGISTRY_SYNC_MODE_DEV_20260225.md`
- `docs/PHASE123_RECOVERY_REGISTRY_SYNC_MODE_VERIFICATION_20260225.md`
