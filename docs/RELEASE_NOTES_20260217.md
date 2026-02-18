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
