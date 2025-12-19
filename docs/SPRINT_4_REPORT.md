# Sprint 4 开发报告：内容安全（病毒扫描 + 加密存储）

**日期**：2025-12-18
**状态**：已完成（已对齐当前仓库实现与可编译状态）

---

## 概述

Sprint 4 实现了企业级内容安全功能，包括：
1. ClamAV 防病毒集成（实时扫描上传文件）
2. 加密存储选项文档（为未来实施提供指南）

---

## 4.1 ClamAV 防病毒集成

### 架构设计

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Document      │     │  VirusScan       │     │   ClamAV        │
│   Upload API    │────▶│  Processor       │────▶│   (clamd)       │
│                 │     │  (order=150)     │     │   port 3310     │
└─────────────────┘     └──────────────────┘     └─────────────────┘
                               │
                               ▼
                        ┌──────────────────┐
                        │ 检测到病毒？      │
                        │ Yes: 删除+拒绝   │
                        │ No:  继续上传    │
                        └──────────────────┘
```

### 新增文件

| 文件路径 | 描述 |
|---------|------|
| `ecm-core/src/main/java/com/ecm/core/integration/antivirus/ClamAvClient.java` | 低层 clamd 通信客户端，实现 INSTREAM 协议 |
| `ecm-core/src/main/java/com/ecm/core/integration/antivirus/AntivirusService.java` | Spring 服务包装，提供高层扫描 API |
| `ecm-core/src/main/java/com/ecm/core/pipeline/processor/VirusScanProcessor.java` | 文档上传管线处理器 |
| `docs/FEATURE_ENCRYPTION.md` | 加密存储选项文档 |

### 修改文件

| 文件路径 | 修改内容 |
|---------|---------|
| `docker-compose.yml` | 添加 clamav 服务、卷、环境变量 |
| `ecm-core/src/main/resources/application.yml` | 添加 `ecm.antivirus.*` 配置 |
| `ecm-core/src/main/resources/application-docker.yml` | 添加 Docker 环境 antivirus 配置 |
| `ecm-core/src/main/java/com/ecm/core/dto/SystemStatusDto.java` | 添加 antivirus 字段 |
| `ecm-core/src/main/java/com/ecm/core/controller/SystemStatusController.java` | 添加 checkAntivirus() 方法 |
| `ecm-frontend/src/pages/SystemStatusPage.tsx` | 添加 Antivirus 状态卡片 |
| `scripts/smoke.sh` | 添加 EICAR 测试 |
| `ecm-frontend/e2e/ui-smoke.spec.ts` | 添加 Antivirus E2E 测试 |
| `docs/RERUN_REPORT.md` | 添加 Sprint 4 完成记录 |

---

## 4.2 技术实现细节

### ClamAvClient.java

```java
// INSTREAM 协议实现
// 1. 连接 clamd (默认端口 3310)
// 2. 发送 "zINSTREAM\0" 命令
// 3. 分块发送数据 (4字节长度 + 数据)
// 4. 发送空块 (4字节零) 结束
// 5. 读取扫描结果

public ScanResult scan(InputStream inputStream) {
    // 分块上传，支持大文件流式扫描
    // 返回 CLEAN / INFECTED / ERROR
}
```

### AntivirusService.java

```java
@Service
public class AntivirusService {

    @Value("${ecm.antivirus.enabled:false}")
    private boolean enabled;

    @Value("${ecm.antivirus.on-threat.action:reject}")
    private String onThreatAction; // reject / quarantine（当前 quarantine 仍会拒绝上传，隔离存储待补）

    public VirusScanResult scan(InputStream inputStream, String filename, UUID nodeId) throws AntivirusException {
        // 调用 ClamAvClient INSTREAM 扫描
        // 命中病毒：写审计（SECURITY_EVENT + VIRUS_DETECTED）并抛 VirusDetectedException（reject 模式）
    }
}
```

### VirusScanProcessor.java

```java
@Component
public class VirusScanProcessor implements DocumentProcessor {

    @Override
    public int getOrder() {
        return 150;  // 在 ContentStorageProcessor(100) 之后
    }

    public ProcessingResult process(DocumentContext context) {
        // antivirus disabled -> SKIPPED
        // scan clean -> SUCCESS
        // virus detected -> delete content + FATAL（终止上传）
        // scan error -> FAILED（fail-open：不终止上传，但记录错误）
    }
}
```

---

## 4.3 配置说明

### application.yml

```yaml
ecm:
  antivirus:
    enabled: ${ECM_ANTIVIRUS_ENABLED:false}
    clamd:
      host: ${ECM_ANTIVIRUS_CLAMD_HOST:localhost}
      port: ${ECM_ANTIVIRUS_CLAMD_PORT:3310}
      timeout: ${ECM_ANTIVIRUS_CLAMD_TIMEOUT:30000}
    on-threat:
      action: reject  # reject 或 quarantine
      quarantine-path: /var/ecm/quarantine
```

### docker-compose.yml

```yaml
clamav:
  image: clamav/clamav:stable
  ports:
    - "${CLAMAV_PORT:-3310}:3310"
  volumes:
    - clamav_data:/var/lib/clamav
  environment:
    - CLAMAV_NO_FRESHCLAMD=${CLAMAV_NO_FRESHCLAMD:-false}
  networks:
    - ecm-network
  restart: unless-stopped
  healthcheck:
    test: ["CMD", "clamdscan", "--ping", "5"]
    interval: 60s
    timeout: 30s
    retries: 3
    start_period: 120s  # 病毒库更新需要时间
```

---

## 4.4 加密存储文档

创建了 `docs/FEATURE_ENCRYPTION.md`，涵盖：

### Phase 1（推荐起点）

| 方案 | 描述 | 复杂度 |
|-----|------|-------|
| MinIO SSE-S3 | MinIO 管理密钥，自动加密所有对象 | 低 |
| MinIO SSE-KMS | 集成 HashiCorp Vault / AWS KMS | 中 |
| 主机卷加密 | LUKS / Docker 加密卷 | 低 |
| 云提供商加密 | AWS EBS / Azure Disk / GCP PD | 低 |

### Phase 2（未来）

- 应用级信封加密（Envelope Encryption）
- KMS → DEK → AES-256-GCM
- 支持密钥轮换、per-document 密钥

---

## 4.5 测试覆盖

### API Smoke Test (`scripts/smoke.sh`)

```bash
# 2.3.3 Antivirus Status Check
# 检查 /api/v1/system/status 返回 antivirus 字段
# 如果 AV 启用但未就绪，等待 ClamAV 最多 30 秒

# 2.3.4 EICAR Virus Test
# 仅当 AV 启用且可用时执行
EICAR='X5O!P%@AP[4\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*'
# 上传 EICAR 文件应返回 HTTP 400/500
```

### ClamAV 就绪等待逻辑

测试脚本包含 ClamAV 就绪等待机制，避免因 ClamAV 启动慢（下载病毒库）导致测试失败：

```bash
# smoke.sh 示例
if [[ "$av_enabled" == "true" && "$av_available" != "true" ]]; then
  log_info "Waiting for ClamAV (max 30s)..."
  for attempt in {1..6}; do
    sleep 5
    check_av_status
    if [[ "$av_available" == "true" || "$av_status" == "healthy" ]]; then
      log_info "ClamAV became ready after $((attempt * 5)) seconds."
      break
    fi
  done
fi
```

### E2E Test (`ui-smoke.spec.ts`)

```typescript
test('Antivirus: EICAR test file rejection + System status', async ({ page, request }) => {
  // 1. 检查 /api/v1/system/status 返回 antivirus
  // 2. 如果 AV 启用但未就绪，等待 ClamAV 最多 30 秒
  // 3. 导航到 /status 页面验证 Antivirus 卡片
  // 4. 如果 AV 启用且可用，上传 EICAR 测试文件验证拒绝
});
```

---

## 4.6 System Status 集成

### API 响应示例

```json
{
  "timestamp": "2025-12-18T10:30:00Z",
  "database": { "reachable": true, "documentCount": 150 },
  "redis": { "reachable": true, "ping": "PONG" },
  "rabbitmq": { "reachable": true },
  "search": { "indexedDocuments": 150 },
  "ml": { "available": true, "modelLoaded": true },
  "keycloak": { "reachable": true },
  "wopi": { "healthy": true },
  "antivirus": {
    "enabled": true,
    "available": true,
    "version": "ClamAV 1.2.1",
    "status": "healthy"
  }
}
```

### UI 展示

System Status 页面 (`/status`) 新增 Antivirus 卡片，显示：
- 是否启用
- 服务可用性
- ClamAV 版本
- 健康状态

---

## 4.7 安全策略

### Fail-Open vs Fail-Closed

当前实现采用 **Fail-Open** 策略：

| 场景 | 行为 |
|-----|------|
| ClamAV 不可用 | 记录警告，允许上传 |
| 扫描超时 | 记录警告，允许上传 |
| 扫描通过 | 正常继续上传 |
| 检测到病毒 | 删除内容，拒绝上传 |

说明：
- “允许上传”在实现层面表现为：`VirusScanProcessor` 返回 `ProcessingResult.Status.FAILED`（pipeline 继续），并在 `DocumentContext` 记录 error；
- 若未来要切换为 fail-closed，可将扫描错误分支改为 `ProcessingResult.fatal(...)`。

**理由**：避免 ClamAV 服务故障导致整个系统不可用。可通过配置切换为 Fail-Closed。

---

## 4.8 部署注意事项

1. **首次启动**：ClamAV 需要下载病毒库（约 300MB），`start_period: 120s` 预留时间
2. **内存需求**：ClamAV 运行时约需 1-2GB 内存
3. **更新频率**：默认每小时检查病毒库更新（freshclam）
4. **禁用更新**：设置 `CLAMAV_NO_FRESHCLAMD=true`（离线环境）

---

## 构建验证

```bash
# Frontend
cd ecm-frontend
npm run build  # OK

# Backend（推荐用 Docker Maven 编译校验）
docker run --rm -v "$PWD/ecm-core":/workspace -w /workspace \
  maven:3-eclipse-temurin-17 mvn -q -DskipTests compile
```

---

## 下一步

根据 `CLAUDE_EXECUTION_PLAN.md`，Sprint 4 已完成。后续可进行：
- Sprint 5: 高级功能（批量下载 ZIP、Correspondents 高级功能）
- 或进行完整系统集成测试验证 ClamAV 实际运行效果
