# Athena ECM Smoke Test（端口 5500/7700）

## 访问地址
- 前端（Web UI）：`http://localhost:5500/`
- 后端（API Base）：`http://localhost:7700/api/v1`
- Swagger：`http://localhost:7700/swagger-ui.html`
- Keycloak：`http://localhost:8180/`
- Collabora（同源代理，推荐）：`http://localhost:5500/browser/`
- Collabora（直连）：`http://localhost:9980/`

## 默认账号
- 管理员：`admin / admin`
- 编辑：`editor / editor`
- 只读：`viewer / viewer`
- 普通用户：`user / user`

## UI 验证（推荐）
1. 打开 `http://localhost:5500/`，跳转到 Keycloak 登录后返回应用。
2. 进入 `Documents` 文件夹，点击 `Upload` 上传一个 PDF，确认列表出现该文件。
3. 进入 `Search`（顶部放大镜）输入文件名关键字，确认能搜到结果。
4. 在文件行右侧菜单中：
   - `Tags`：创建并添加一个标签（例如 `test-tag`）。
   - `Categories`：选择一个分类并保存。
5. 删除与回收站：
   - 在文件行菜单中 `Delete`，确认文件消失；
   - 打开 `Trash`，确认文件在回收站中；执行 `Restore` 后回到 `Documents` 可再次看到。
6. 规则页面：
   - 管理员登录后，头像菜单应出现 `Rules`；或直接访问 `http://localhost:5500/rules`。

## UI 自动化验证（Playwright，可选）
前提：ECM 全栈已启动（端口 `5500/7700/8180`）。

```bash
cd ecm-frontend
npm install --no-audit --no-fund
npm run e2e:install
ECM_E2E_USERNAME=admin ECM_E2E_PASSWORD=admin npm run e2e
```

## API 验证（可选）
### 0) 创建测试用户（可选）
如果 Keycloak 已经启动且不会重新导入 realm，可以用脚本创建/重置测试用户：
```bash
bash scripts/keycloak/create-test-users.sh
```

### 1) 获取 Token（本地测试用）
```bash
mkdir -p tmp
curl -sS -X POST "http://localhost:8180/realms/ecm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=unified-portal&username=admin&password=admin" \
  | jq -r .access_token > tmp/admin.access_token

chmod 600 tmp/admin.access_token
TOKEN=$(cat tmp/admin.access_token)
```

也可以用脚本：
```bash
bash scripts/get-token.sh admin admin
```

### 2) ML 健康检查
```bash
curl -sS "http://localhost:7700/api/v1/ml/health"
```

### 3) 一键 Smoke（脚本）
```bash
ECM_API=http://localhost:7700 ECM_TOKEN="$(cat tmp/admin.access_token)" bash scripts/smoke.sh
```

### 4) 重新构建并重启（应用配置/前端代理更新后）
```bash
bash scripts/restart-ecm.sh
```
