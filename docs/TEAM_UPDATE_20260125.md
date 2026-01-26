# Team Update - 2026-01-25

## Summary
- Mail Automation now supports in-app test connection and fetch summary.
- Storage permission issues fixed (auto-correct on startup).
- Full Playwright E2E regression is green.

## What changed
- Backend:
  - Added `POST /api/v1/integration/mail/accounts/{id}/test`.
  - Manual fetch now returns a summary payload.
  - `mail_accounts.password` nullable for OAuth2; audit log columns added.
  - `ecm-core` entrypoint now fixes `/var/ecm/content` ownership on startup.
- Frontend:
  - “Test connection” button on Mail Automation page.
  - Fetch summary toast after manual trigger.
  - Web Crypto fallback (dev-only) for Keycloak login issues.
- E2E:
  - New `mail-automation.spec.ts` + UI smoke coverage.

## Verification
- Mail Automation E2E: PASS.
- Full Playwright regression: 21/21 PASS.

## Notes / Action Items
- If running locally with dev defaults, use `application-dev.yml` for `ddl-auto=update` and `jodconverter.local.enabled=false`.
- For existing volumes, one-time fix may still be required:
  - `docker exec -u 0 athena-ecm-core-1 chown -R app:app /var/ecm/content`

## Comms Templates

### Slack
```
[Release 2026-01-25] Athena ECM 更新已发布 ✅

Highlights
• Mail Automation 支持“Test connection”与抓取摘要
• /var/ecm/content 权限启动自修复（避免上传失败）
• 全量 Playwright 回归 21/21 PASS

主要变更
• Backend: 新增 /integration/mail/accounts/{id}/test；抓取返回汇总；audit_log 新字段；mail_accounts.password 允许空
• Frontend: Mail Automation 页面新增 Test connection + 抓取摘要 toast
• E2E: 新增 mail-automation.spec + UI smoke 覆盖
• 配置: dev-only 默认迁移到 application-dev.yml
• 文档: release notes + ops/交接补充

注意
• 旧卷可能需要一次性修复权限：
  docker exec -u 0 athena-ecm-core-1 chown -R app:app /var/ecm/content

Tag: release-20260125
```

### Email
Subject: [Release 2026-01-25] Athena ECM Mail Automation & Storage Fixes

```
Hi team,

Release 2026-01-25 已发布（tag: release-20260125）。

Highlights
- Mail Automation 新增 Test connection 与抓取摘要
- ecm-core 启动自动修复 /var/ecm/content 权限
- Playwright 全量回归 21/21 PASS

主要变更
Backend:
- 新增 /api/v1/integration/mail/accounts/{id}/test
- 手动抓取返回摘要（accounts/processed/errors/duration）
- audit_log 新字段；mail_accounts.password 允许为空（OAuth2）
- ecm-core entrypoint 自动修复存储权限

Frontend:
- Mail Automation 页面新增 Test connection 按钮 + 抓取摘要 toast
- Keycloak login Web Crypto dev-only fallback

E2E:
- 新增 mail-automation.spec
- UI smoke 增加 Mail Automation 场景

配置:
- dev-only 默认迁移到 application-dev.yml

注意事项
- 若使用旧 Docker 卷，可能需要一次性修复：
  docker exec -u 0 athena-ecm-core-1 chown -R app:app /var/ecm/content

Thanks!
```
