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
