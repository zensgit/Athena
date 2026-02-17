# 发布说明（2026-02-17）

## 一、发布概览
- 本次发布聚焦认证恢复稳定性与回归门禁可用性：
  - 前端会话恢复链路加固（401 重试/跳转登录提示一致性）。
  - 新增 Auth Session Recovery 的 Playwright 回归覆盖。
  - Phase5 回归脚本增强：支持自定义本地端口自动拉起静态 SPA 服务。
  - Phase5+Phase6 交付门禁增强：full-stack UI 目标自动探测，优先开发态 `:3000`。

## 二、主要变更
### 1) 会话恢复与登录提示
- `authService.refreshToken` 增加瞬时错误与终态认证错误分类，避免对网络抖动场景误登出。
- API `401` 不可恢复时统一写入会话过期信号，并重定向到 `/login?reason=session_expired`。
- 登录页统一支持从 `sessionStorage`、`localStorage`、URL query 三路读取会话过期提示，并在展示后清理标记。

### 2) 自动化测试覆盖
- 新增 `ecm-frontend/e2e/auth-session-recovery.mock.spec.ts`：
  - 覆盖查询触发 `401`（初次+重试）后回到登录页。
  - 校验登录页展示 “Your session expired. Please sign in again.”
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
  - mocked gate：10 passed
  - full-stack admin smoke：1 passed
  - phase6 mail integration smoke：1 passed
  - phase5 search suggestions integration smoke：1 passed
  - p1 smoke：3 passed，1 skipped（可选 mail preview-run 场景）

## 五、影响与兼容性
- 不涉及后端接口契约破坏。
- 现有登录流程保持兼容；新增 query reason 为向后兼容增强。
- 门禁脚本行为仅在未显式传入 `ECM_UI_URL_FULLSTACK` 时采用自动探测，显式配置优先级不变。
- `phase5-regression` 默认将优先使用临时独立静态服务进行 mocked 回归，减少本地端口污染导致的误报风险；可通过 `PHASE5_USE_EXISTING_UI=1` 关闭。

## 六、相关文档
- `docs/PHASE5_AUTH_SESSION_RECOVERY_DESIGN_20260216.md`
- `docs/PHASE5_AUTH_SESSION_RECOVERY_VERIFICATION_20260216.md`
- `docs/DESIGN_PHASE5_PHASE6_GATE_FULLSTACK_TARGET_AUTODETECT_20260217.md`
- `docs/VERIFICATION_PHASE5_PHASE6_GATE_FULLSTACK_TARGET_AUTODETECT_20260217.md`
