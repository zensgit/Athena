# Athena ECM: 追平并超越 Alfresco Community Repo 开发计划

> 基于 2026-03-29 对 Athena 全量代码 (330+ Java, 132+ TS) 与 Alfresco Community Repo (85 模块, 65 服务, 482 WebScript) 深度对比
>
> 目标: 在现有 Athena 优势基础上，补齐所有核心功能缺口，实现对 Alfresco 的全面超越

---

## 一、当前态势总览

### 1.1 Athena 已有覆盖

| 维度 | Athena 现状 | Alfresco 对应 |
|------|-----------|-------------|
| 核心文档管理 (CRUD/版本/回收站/路径导航) | ✅ 完整 | ✅ 完整 |
| 搜索 (全文/分面/高亮/保存) | ✅ Elasticsearch | ✅ Solr |
| 权限 (ACL/继承/模板/动态权限) | ✅ 含诊断/过期 | ✅ 基础 |
| 预览 (Office/PDF/Image/CAD) | ✅ 含诊断/死信/熔断 | ⚠️ 基础 Rendition |
| 工作流 (BPMN 2.0 Flowable) | ✅ 含表单/历史 | ✅ Activiti |
| 规则引擎 (条件/动作/Cron) | ✅ 含 DryRun/幂等 | ✅ 基础 |
| 评论 (嵌套/@提及/Reaction) | ✅ 超越 | ⚠️ 扁平 |
| 标签/分类/收藏 | ✅ 完整 | ✅ 完整 |
| 用户/组管理 | ✅ 含 MFA | ✅ 含头像/配额 |
| 审计日志 | ✅ 含异步导出 | ✅ 含多应用 |

### 1.2 Athena 已超越 Alfresco 的领域 (22 项)

| # | 领域 | Athena 独有能力 |
|---|------|---------------|
| 1 | Preview 诊断治理 | 失败分析、死信队列、策略 Profile、CAD Failover、熔断器、转换追踪 |
| 2 | 异步任务中心 | 统一跨域生命周期、风险评分、治理面板、CSV 导出 |
| 3 | 运维恢复控制面板 | DryRun、策略回滚、历史审计、批量重试、分理由分析 |
| 4 | 搜索驱动批量操作 | 搜索 → DryRun → 批量执行 → 原因分析 → CSV 导出 |
| 5 | 评论系统 | 5 层嵌套、@提及通知、Emoji Reaction、统计面板 |
| 6 | PDF 注解 | 页面级坐标注解持久化与多人协作 |
| 7 | 版本比较 | 行级文本 Diff (JSON/XML/YAML/文本) |
| 8 | 邮件自动化 | IMAP/OAuth 客户端收件 + 规则匹配引擎 + 报告导出 |
| 9 | 规则引擎增强 | DryRun 测试、幂等性账本、执行统计、重排序 |
| 10 | 权限诊断 | explainPermission 决策链可视化 |
| 11 | 权限过期 | 自动清理过期权限 |
| 12 | ERP 集成 | Odoo XML-RPC 双向同步 |
| 13 | 实时协作编辑 | WOPI/Collabora 在线编辑 + WPS 集成 |
| 14 | AI/ML | ML 分类服务 + 条码提取 |
| 15 | OCR | Tesseract OCR 管线集成 |
| 16 | 病毒扫描 | ClamAV 管线集成 |
| 17 | 消息队列 | RabbitMQ 事件总线 |
| 18 | 缓存/调度队列 | Redis 缓存 + 调度队列 |
| 19 | Webhook | 事件订阅推送 |
| 20 | WebDAV | 标准 WebDAV 暴露 |
| 21 | 权限集模板版本化 | 模板版本 Diff 对比 |
| 22 | 数据完整性 | SanityCheck 自动校验框架 |

### 1.3 待补齐的缺口 (26 项功能)

**第一梯队 (核心 ECM 必备) — 8 项 / 28.5 工日:**
1. 文档锁定服务增强 (LockService)
2. Check-Out/Check-In 工作副本
3. 内容模型/数据字典 (Content Model / Data Dictionary)
4. 节点切面系统 (Aspect System)
5. 评分/点赞服务 (Rating Service)
6. 用户偏好服务 (Preference Service)
7. 节点关联增强 (Target/Source Associations)
8. 共享链接增强 (Shared Links)

**第二梯队 (协作与社交) — 4 项 / 18 工日:**
9. 站点/协作空间 (Sites)
10. 用户活动流 (Activity Feed)
11. 关注/订阅 (Subscription/Following)
12. 站点邀请 (Invitation)

**第三梯队 (企业级功能) — 14 项 / 74 工日:**
13–26. 多租户、批量导入、内容归档、预签名 URL、CMIS、策略框架、配额、复制、导入导出、论坛/博客、日历、模板引擎、属性存储、发现 API

---

## 二、并行开发计划：6 轮冲刺 (Sprint)

### Sprint 1: 核心模型与锁定 (并行 3 条线)

**时间: 第 1-3 天**

#### 线路 A: 内容模型 / 数据字典 (Phase 361-363)

> 这是 Alfresco 最核心的架构差距 — 类型/切面/属性/约束定义系统

**后端:**

```
新建 entity:
  ContentModelDefinition.java     — 模型实体 (namespace, prefix, status, xml)
  TypeDefinition.java             — 类型定义 (parent, title, description, mandatory aspects)
  AspectDefinition.java           — 切面定义 (title, properties, mandatory)
  PropertyDefinition.java         — 属性定义 (name, dataType, multiValued, mandatory, defaultValue)
  ConstraintDefinition.java       — 约束 (type: REGEX/LIST/RANGE/LENGTH, parameters JSONB)
  PropertyDataType.java           — 枚举 (TEXT, INT, LONG, FLOAT, DOUBLE, DATE, DATETIME, BOOLEAN, NODEREF, CONTENT, CATEGORY, QNAME, URI, LOCALE, MLTEXT, PERIOD)

新建 repository:
  ContentModelDefinitionRepository.java
  TypeDefinitionRepository.java
  AspectDefinitionRepository.java
  PropertyDefinitionRepository.java
  ConstraintDefinitionRepository.java

新建 service:
  ContentModelService.java        — 模型 CRUD、激活/停用、验证、导入/导出 XML
  DictionaryService.java          — 类型/切面查询、属性定义查询、继承解析

新建 controller:
  ContentModelController.java     — /api/content-models CRUD
  DictionaryController.java       — /api/types, /api/aspects, /api/properties 查询

新建 validation:
  PropertyConstraintValidator.java  — 约束执行器 (REGEX/LIST/RANGE/LENGTH)
  MandatoryPropertyEnforcer.java    — 必填属性校验

DB migration:
  038-create-content-model-tables.xml (5 tables: content_model_definitions, type_definitions, aspect_definitions, property_definitions, constraint_definitions)
```

**前端:**

```
新建 pages:
  ContentModelManagerPage.tsx     — 模型管理界面

新建 services:
  contentModelService.ts          — API 调用
  dictionaryService.ts            — 类型/切面查询

新建 components:
  ContentModelEditor.tsx          — 模型编辑器
  TypeDefinitionPanel.tsx         — 类型定义面板
  AspectDefinitionPanel.tsx       — 切面定义面板
  PropertyDefinitionForm.tsx      — 属性定义表单
  ConstraintEditor.tsx            — 约束编辑器
```

**测试:**
```
ContentModelServiceTest.java
DictionaryServiceTest.java
PropertyConstraintValidatorTest.java
```

---

#### 线路 B: 节点切面系统 + 锁定增强 (Phase 364-365)

**切面系统 — 后端:**

```
修改 entity/Node.java:
  增加: Set<String> aspects (ElementCollection, join table node_aspects)
  增加: Map<String, Object> aspectProperties (动态属性存 JSONB)

修改 service/NodeService.java:
  增加: addAspect(nodeId, aspectName) — 附加切面并初始化默认属性
  增加: removeAspect(nodeId, aspectName) — 移除切面及其属性
  增加: hasAspect(nodeId, aspectName) — 检查切面
  增加: getAspects(nodeId) — 列出所有切面
  增加: 切面属性的 get/set 操作

修改 controller/NodeController.java:
  POST   /api/nodes/{nodeId}/aspects          — 添加切面
  DELETE /api/nodes/{nodeId}/aspects/{name}    — 移除切面
  GET    /api/nodes/{nodeId}/aspects           — 列出切面

修改 dto/NodeDto.java:
  增加 aspects 字段

DB migration:
  039-create-node-aspects-table.xml (node_aspects join table)
```

**锁定增强 — 后端:**

```
新建 entity/LockType.java 枚举:
  WRITE_LOCK          — 独占写锁 (阻止其他人写)
  READ_ONLY_LOCK      — 只读锁 (阻止所有人写)
  NODE_LOCK           — 节点锁 (阻止操作)

修改 entity/Node.java:
  增加: LockType lockType
  增加: Instant lockExpiry        — 超时自动解锁
  增加: boolean lockPersistent    — 持久锁 vs 临时锁 (会话级)
  增加: boolean lockDeep          — 是否锁定子节点

新建 service/LockService.java:
  lock(nodeId, LockType, timeout, lifetime, deep)
  unlock(nodeId)
  getLockInfo(nodeId) → LockInfoDto
  checkLock(nodeId) — 内部检查 (写操作前调用)
  batchLock(nodeIds, LockType, timeout)
  batchUnlock(nodeIds)
  suspendLock(nodeId) / resumeLock(nodeId)
  isLocked(nodeId) → boolean
  getLockOwner(nodeId) → String
  cleanupExpiredLocks() — @Scheduled 定期清理

修改 controller/NodeController.java:
  POST   /api/nodes/{nodeId}/lock     — 增加 lockType, timeout, deep 参数
  DELETE /api/nodes/{nodeId}/lock     — 解锁
  GET    /api/nodes/{nodeId}/lock     — 获取锁信息
  POST   /api/nodes/bulk-lock         — 批量锁定
  POST   /api/nodes/bulk-unlock       — 批量解锁

DB migration:
  040-enhance-lock-service.xml
```

**测试:**
```
AspectServiceTest.java
LockServiceTest.java
LockServiceScheduledTest.java
```

---

#### 线路 C: 评分/点赞 + 用户偏好 + 发现 API (Phase 366-368)

**评分/点赞:**

```
新建 entity/Rating.java:
  id, nodeId, userId, scheme (LIKES/FIVE_STAR), score, createdAt

新建 repository/RatingRepository.java:
  findByNodeId, findByNodeIdAndUserId, countByNodeIdAndScheme
  averageScoreByNodeIdAndScheme, deleteByNodeIdAndUserIdAndScheme

新建 service/RatingService.java:
  rate(nodeId, scheme, score)         — 创建/更新评分
  removeRating(nodeId, scheme)        — 删除当前用户评分
  getRatings(nodeId)                  — 获取节点所有评分
  getRatingSummary(nodeId, scheme)    — 平均分/总分/计数
  getUserRating(nodeId, scheme)       — 当前用户评分

新建 controller/RatingController.java:
  GET    /api/nodes/{nodeId}/ratings                    — 列出评分
  POST   /api/nodes/{nodeId}/ratings                    — 添加评分
  DELETE /api/nodes/{nodeId}/ratings/{ratingSchemeId}   — 删除评分
  GET    /api/nodes/{nodeId}/ratings/summary             — 汇总

DB migration:
  041-create-ratings-table.xml
```

**用户偏好:**

```
新建 entity/UserPreference.java:
  id, userId, key (namespaced, e.g. "org.athena.share.favourites"), value (JSONB)

新建 repository/UserPreferenceRepository.java:
  findByUserId, findByUserIdAndKeyStartingWith, findByUserIdAndKey

新建 service/PreferenceService.java:
  getPreferences(personId, filter)     — 按模式查询
  getPreference(personId, key)         — 单个偏好
  setPreference(personId, key, value)  — 设置
  deletePreference(personId, key)      — 删除

新建 controller/PreferenceController.java:
  GET    /api/people/{personId}/preferences              — 列出偏好
  GET    /api/people/{personId}/preferences/{key}        — 获取偏好
  PUT    /api/people/{personId}/preferences/{key}        — 设置偏好
  DELETE /api/people/{personId}/preferences/{key}        — 删除偏好

DB migration:
  042-create-user-preferences-table.xml
```

**发现 API:**

```
新建 controller/DiscoveryController.java:
  GET /api/discovery — 返回:
    repositoryId, edition, version
    installedModules[] — 已安装模块
    capabilities[] — 支持能力 (search, versioning, workflow, preview, etc.)
    status — 系统状态
    license — 许可信息
```

**测试:**
```
RatingServiceTest.java
PreferenceServiceTest.java
DiscoveryControllerTest.java
```

---

### Sprint 2: Check-Out 增强 + 关联增强 + 共享链接 (并行 3 条线)

**时间: 第 4-7 天**

#### 线路 A: Check-Out/Check-In 工作副本 (Phase 369-370)

```
新建 service/CheckOutCheckInService.java:
  checkout(nodeId)                          — 创建工作副本节点
  checkout(nodeId, destinationFolderId)     — 签出到指定位置
  checkin(workingCopyId, majorVersion, comment) — 签入并创建版本
  checkin(workingCopyId, ..., keepCheckedOut)   — 签入但保持签出
  cancelCheckout(workingCopyId)             — 取消签出，丢弃工作副本
  getWorkingCopy(nodeId)                    — 获取工作副本
  getOriginal(workingCopyId)               — 获取原文档
  isCheckedOut(nodeId)                      — 检查签出状态
  getCheckedOutNodes(userId, pageable)      — 列出用户签出的文档

修改 entity/Document.java:
  增加: UUID workingCopyOf       — 指向原文档 (null = 非工作副本)
  增加: boolean isWorkingCopy    — 是否为工作副本
  增加: UUID checkedOutTo        — 工作副本节点 ID

修改 dto/NodeDto.java:
  增加: workingCopyOf, isWorkingCopy, checkedOutTo 字段

修改 controller/DocumentController.java:
  POST   /api/nodes/{nodeId}/checkout                   — 签出
  POST   /api/nodes/{nodeId}/checkout?destination={id}  — 签出到指定位置
  POST   /api/nodes/{workingCopyId}/checkin             — 签入
  DELETE /api/nodes/{workingCopyId}/cancel-checkout      — 取消
  GET    /api/nodes/{nodeId}/working-copy               — 获取工作副本

前端:
  修改 FileList.tsx — 显示工作副本图标/徽章
  修改 VersionHistoryDialog.tsx — 签出/签入操作
  新建 CheckoutDialog.tsx — 选择签出位置

DB migration:
  043-enhance-checkout-working-copy.xml
```

---

#### 线路 B: 节点关联增强 (Phase 371)

```
修改 entity/DocumentRelation.java:
  增加: String assocType          — 关联类型 (QName 格式, e.g. "cm:references")
  增加: AssocDirection direction  — PEER, CHILD_PRIMARY, CHILD_SECONDARY
  增加: int orderIndex            — 排序

新建 entity/AssocDirection.java 枚举:
  PEER, CHILD_PRIMARY, CHILD_SECONDARY

新建 service/AssociationService.java:
  createAssociation(sourceId, targetId, assocType) — 创建 peer 关联
  removeAssociation(sourceId, targetId, assocType) — 删除关联
  getTargetAssociations(nodeId, assocType)         — 获取目标关联
  getSourceAssociations(nodeId, assocType)         — 获取源关联
  addSecondaryChild(parentId, childId)             — 添加二级子节点
  removeSecondaryChild(parentId, childId)          — 移除二级子节点
  getSecondaryChildren(parentId)                   — 列出二级子节点
  getSecondaryParents(childId)                     — 列出二级父节点

修改 controller/NodeController.java:
  GET    /api/nodes/{nodeId}/targets                — 目标关联
  POST   /api/nodes/{nodeId}/targets                — 创建目标关联
  DELETE /api/nodes/{nodeId}/targets/{targetId}      — 删除
  GET    /api/nodes/{nodeId}/sources                — 源关联
  POST   /api/nodes/{nodeId}/secondary-children     — 添加二级子节点
  GET    /api/nodes/{nodeId}/secondary-children     — 列出
  DELETE /api/nodes/{nodeId}/secondary-children/{childId} — 移除

前端:
  修改 components/dialogs/PropertiesDialog.tsx — 显示关联面板
  新建 components/AssociationsPanel.tsx — 关联管理 UI

DB migration:
  044-enhance-associations.xml
```

---

#### 线路 C: 共享链接增强 (Phase 372)

```
修改 entity/ShareLink.java:
  增加: Instant expiryDate         — 过期时间
  增加: long accessCount           — 访问计数
  增加: Instant lastAccessedAt     — 最后访问
  增加: String password            — 可选密码保护

修改 service/ShareLinkService.java:
  增加: createWithExpiry(nodeId, expiryDate, password)
  增加: emailShareLink(shareId, recipientEmails, message)  — 邮件发送
  增加: getRenditionViaShareLink(shareId, renditionId)      — 通过共享链接访问渲染
  增加: listShareLinks(pageable)                             — 列出所有共享链接
  增加: cleanupExpiredLinks() — @Scheduled 定期清理

修改 controller/ShareLinkController.java:
  POST   /api/shared-links                           — 创建 (增加 expiry, password)
  GET    /api/shared-links                           — 列出所有
  GET    /api/shared-links/{sharedId}                — 获取详情
  GET    /api/shared-links/{sharedId}/content        — 通过共享下载
  GET    /api/shared-links/{sharedId}/renditions     — 列出渲染
  GET    /api/shared-links/{sharedId}/renditions/{id}/content — 下载渲染
  POST   /api/shared-links/{sharedId}/email          — 邮件发送
  DELETE /api/shared-links/{sharedId}                — 删除

前端:
  修改 components/ShareLinkManager.tsx — 增加过期/密码/邮件功能

DB migration:
  045-enhance-share-links.xml
```

---

### Sprint 3: 站点 + 活动流 + 用户增强 (并行 3 条线)

**时间: 第 8-14 天**

#### 线路 A: 站点/协作空间 (Phase 373-375)

> 这是 Alfresco 最核心的协作模型 — 团队协作空间

```
新建 entity:
  Site.java:
    id, siteId (slug), title, description
    visibility (PUBLIC/MODERATED/PRIVATE)
    createdBy, createdDate, modifiedDate
    preset (default: "site-dashboard")

  SiteMember.java:
    id, siteId, personId, role (MANAGER/COLLABORATOR/CONTRIBUTOR/CONSUMER)

  SiteContainer.java:
    id, siteId, containerId (e.g. "documentLibrary"), folderId (指向 Node)

  SiteMembershipRequest.java:
    id, siteId, personId, message, status (PENDING/APPROVED/REJECTED), createdDate

新建 repository:
  SiteRepository.java
  SiteMemberRepository.java
  SiteContainerRepository.java
  SiteMembershipRequestRepository.java

新建 service/SiteService.java:
  createSite(siteId, title, description, visibility)
  updateSite(siteId, title, description, visibility)
  deleteSite(siteId)
  getSite(siteId) / listSites(pageable)
  addMember(siteId, personId, role)
  updateMemberRole(siteId, personId, role)
  removeMember(siteId, personId)
  getMembers(siteId, pageable)
  getContainers(siteId) / getContainer(siteId, containerId)
  createMembershipRequest(siteId, message)
  approveMembershipRequest(requestId)
  rejectMembershipRequest(requestId)
  getUserSites(personId)
  getSiteGroups(siteId)

新建 controller/SiteController.java:
  GET    /api/sites                                      — 列出站点
  POST   /api/sites                                      — 创建站点
  GET    /api/sites/{siteId}                              — 获取详情
  PUT    /api/sites/{siteId}                              — 更新
  DELETE /api/sites/{siteId}                              — 删除
  GET    /api/sites/{siteId}/members                      — 列出成员
  POST   /api/sites/{siteId}/members                      — 添加成员
  PUT    /api/sites/{siteId}/members/{personId}            — 更新角色
  DELETE /api/sites/{siteId}/members/{personId}            — 移除成员
  GET    /api/sites/{siteId}/containers                   — 列出容器
  GET    /api/sites/{siteId}/containers/{containerId}     — 获取容器
  POST   /api/sites/{siteId}/membership-requests/{personId}/approve
  POST   /api/sites/{siteId}/membership-requests/{personId}/reject

前端:
  新建 pages/SitesPage.tsx           — 站点列表
  新建 pages/SiteDetailPage.tsx      — 站点详情 (成员/容器/文档库)
  新建 services/siteService.ts       — API 调用
  新建 components/SiteMembersPanel.tsx
  新建 components/SiteCreateDialog.tsx
  修改 MainLayout.tsx               — 侧边栏增加 Sites 入口

DB migration:
  046-create-sites-tables.xml (sites, site_members, site_containers, site_membership_requests)
```

---

#### 线路 B: 用户活动流 (Phase 376-377)

```
新建 entity:
  Activity.java:
    id, activityType (e.g. "org.athena.documentlibrary.file-created")
    userId (发起人), siteId (可选), nodeId (可选)
    summary (JSON 格式摘要), createdDate

  ActivityFeedControl.java:
    id, userId, siteId, activityType, exclude (boolean)

新建 repository:
  ActivityRepository.java
  ActivityFeedControlRepository.java

新建 service:
  ActivityService.java:
    postActivity(activityType, siteId, nodeId, summary)    — 发布活动
    getUserFeed(userId, siteId, types, pageable)            — 获取活动流
    getSiteFeed(siteId, types, pageable)                    — 站点活动流
    setFeedControl(userId, siteId, activityType, exclude)  — 控制订阅
    getFeedControls(userId)                                 — 查询控制
    cleanupOldActivities(days)                              — 清理旧活动

新建 controller/ActivityController.java:
  GET    /api/people/{personId}/activities            — 用户活动流
  GET    /api/sites/{siteId}/activities               — 站点活动流
  POST   /api/activities                              — 发布活动
  GET    /api/people/{personId}/activity-feed-controls — 获取控制
  POST   /api/people/{personId}/activity-feed-controls — 设置控制

修改 各业务 Service (发布活动事件):
  NodeService.java       — 创建/更新/删除/移动/复制 → postActivity
  CommentService.java    — 评论 → postActivity
  VersionService.java    — 版本创建 → postActivity
  WorkflowService.java   — 流程/任务 → postActivity

前端:
  新建 pages/ActivityFeedPage.tsx    — 活动流页面
  新建 components/ActivityTimeline.tsx — 时间线组件
  新建 services/activityService.ts

DB migration:
  047-create-activities-tables.xml (activities, activity_feed_controls)
```

---

#### 线路 C: 用户头像 + 配额 + 关注/订阅 (Phase 378-380)

**用户头像:**

```
修改 entity/User.java:
  增加: byte[] avatarData / String avatarPath
  增加: String avatarMimeType

修改 controller/PeopleController.java:
  GET    /api/people/{personId}/avatar     — 下载头像
  POST   /api/people/{personId}/avatar     — 上传头像
  DELETE /api/people/{personId}/avatar     — 删除头像

修改 service/UserGroupService.java:
  setAvatar(personId, inputStream, mimeType)
  getAvatar(personId)
  deleteAvatar(personId)
```

**存储配额:**

```
新建 entity/UserQuota.java:
  id, userId, quotaBytes (配额), usedBytes (已用)

修改 entity/User.java:
  增加: Long quotaBytes, Long usedBytes

新建 service/QuotaService.java:
  setQuota(userId, bytes)
  getQuota(userId) → QuotaDto
  checkQuota(userId, additionalBytes) — 检查是否超额
  recalculateUsage(userId) — 重新计算用量
  getTopUsers(limit) — 用量排行

修改 service/ContentService.java / DocumentUploadService.java:
  上传前调用 quotaService.checkQuota()
  上传后更新 usedBytes

DB migration:
  048-add-user-avatar-quota.xml
```

**关注/订阅:**

```
新建 entity/Subscription.java:
  id, userId, targetType (USER/NODE/SITE), targetId, createdDate
  notifyOnChange (boolean)

新建 repository/SubscriptionRepository.java

新建 service/SubscriptionService.java:
  follow(userId, targetType, targetId)
  unfollow(userId, targetType, targetId)
  getFollowers(targetType, targetId)
  getFollowing(userId, targetType)
  isFollowing(userId, targetType, targetId)
  notifyFollowers(targetType, targetId, event)

新建 controller/SubscriptionController.java:
  POST   /api/people/{personId}/following         — 关注
  DELETE /api/people/{personId}/following/{id}     — 取消关注
  GET    /api/people/{personId}/following          — 关注列表
  GET    /api/people/{personId}/followers          — 粉丝列表
  GET    /api/nodes/{nodeId}/followers             — 节点关注者

修改 service/NotificationService.java:
  整合 SubscriptionService — 变更时通知关注者

DB migration:
  049-create-subscriptions-table.xml
```

---

### Sprint 4: 企业级功能第一波 (并行 3 条线)

**时间: 第 15-22 天**

#### 线路 A: 策略/行为框架 (Phase 381-382)

> Alfresco 的 Policy/Behavior 是其可扩展性核心

```
新建 policy/:
  PolicyComponent.java:
    registerClassPolicy(eventType, nodeType, behavior)
    registerPropertyPolicy(eventType, property, behavior)
    registerAssociationPolicy(eventType, assocType, behavior)
    invokePolicy(eventType, nodeRef, context)
    getRegisteredPolicies()

  ClassPolicy.java (接口):
    onCreateNode(nodeRef), onUpdateNode(nodeRef), onDeleteNode(nodeRef)
    onMoveNode(nodeRef, oldParent, newParent)
    onAddAspect(nodeRef, aspect), onRemoveAspect(nodeRef, aspect)

  PropertyPolicy.java (接口):
    onUpdateProperties(nodeRef, before, after)

  AssociationPolicy.java (接口):
    onCreateAssociation(assocRef)
    onDeleteAssociation(assocRef)

  BehaviorFilter.java:
    disableBehavior(nodeRef) / enableBehavior(nodeRef)
    isEnabled(nodeRef)

  PolicyRegistry.java:
    注册表 (Map<EventType, Map<QName, List<Behavior>>>)
    支持 Binding Timing: IMMEDIATE, TRANSACTION_COMMIT

修改 service/NodeService.java:
  在 CRUD 操作中调用 policyComponent.invokePolicy()

内置行为实现:
  behaviors/AuditBehavior.java        — 自动审计
  behaviors/VersionBehavior.java      — 自动版本
  behaviors/NotifyBehavior.java       — 自动通知
  behaviors/QuotaUpdateBehavior.java  — 配额更新
```

---

#### 线路 B: 批量导入 + 预签名 URL (Phase 383-384)

**批量导入:**

```
新建 entity/ImportJob.java:
  id, status (PENDING/RUNNING/COMPLETED/FAILED)
  sourcePath, targetFolderId, totalFiles, processedFiles, failedFiles
  errorLog (TEXT), startedAt, completedAt, userId

新建 service/BulkImportService.java:
  startImport(sourcePath, targetFolderId, options)  — 启动导入
  getImportStatus(jobId) → ImportJob                 — 查询状态
  cancelImport(jobId)                                — 取消
  listImportJobs(pageable) → Page<ImportJob>         — 列表

  options:
    replaceExisting (boolean)
    batchSize (int)
    skipMetadata (boolean)
    metadataMapping (Map<String, String>)
    fileFilter (glob pattern)

  内部:
    多线程扫描目录 → 创建节点 → 上传内容 → 索引
    进度追踪 → 错误记录 → 支持断点续传

新建 controller/BulkImportController.java:
  POST   /api/bulk-import                — 启动导入
  GET    /api/bulk-import/{jobId}        — 查询状态
  DELETE /api/bulk-import/{jobId}        — 取消
  GET    /api/bulk-import                — 列出任务

前端:
  新建 pages/BulkImportPage.tsx          — 导入界面 (拖拽/选择/进度)
  新建 components/ImportProgressPanel.tsx — 进度面板
```

**预签名 URL:**

```
新建 service/DirectAccessUrlService.java:
  generateDirectAccessUrl(nodeId, attachment, validSeconds)
  generateDirectAccessUrl(nodeId, versionId, attachment, validSeconds)

  支持:
    MinIO/S3 presigned URL
    本地文件系统: 生成 token-based URL
    可配置有效期 (默认 300s)

修改 controller/NodeController.java:
  POST /api/nodes/{nodeId}/direct-access-url    — 请求预签名 URL
    body: { attachment: boolean, expiresIn: 300 }
    返回: { contentUrl: "https://...", expiresAt: "..." }
```

---

#### 线路 C: 节点级审计 + 全局属性存储 (Phase 385-386)

**节点级审计查询:**

```
修改 repository/AuditLogRepository.java:
  findByNodeId(nodeId, pageable) — 按节点查审计

修改 service/AuditService.java:
  getNodeAuditEntries(nodeId, pageable)

修改 controller/NodeController.java:
  GET /api/nodes/{nodeId}/audit-entries    — 节点审计条目

前端:
  修改 PropertiesDialog.tsx — 增加审计标签页
```

**全局属性存储:**

```
新建 entity/GlobalAttribute.java:
  id, key1 (String), key2 (String, nullable), key3 (String, nullable)
  value (JSONB), createdDate, modifiedDate

新建 repository/GlobalAttributeRepository.java:
  findByKey1, findByKey1AndKey2, findByKey1AndKey2AndKey3

新建 service/AttributeService.java:
  setAttribute(key1, key2, key3, value)
  getAttribute(key1, key2, key3) → Serializable
  removeAttribute(key1, key2, key3)
  exists(key1, key2, key3)
  getAttributes(callback, key1Pattern)

新建 controller/AttributeController.java:
  GET/PUT/DELETE /api/attributes/{key1}/{key2?}/{key3?}

DB migration:
  050-create-global-attributes-table.xml
```

---

### Sprint 5: 企业级功能第二波 (并行 3 条线)

**时间: 第 23-30 天**

#### 线路 A: 多租户 (Phase 387-389)

> 架构级改造 — 为 SaaS 部署做准备

```
新建 entity/Tenant.java:
  id, tenantDomain, tenantName, enabled, rootNodeId
  quotaBytes, createdDate

新建 config:
  TenantContext.java           — ThreadLocal 租户上下文
  TenantFilter.java            — Servlet Filter (从 JWT/header 提取租户)
  TenantAwareInterceptor.java  — Hibernate 过滤器

修改 所有 Entity (或使用 @TenantId 注解):
  增加 tenantId 字段 + Hibernate @Filter

新建 service/TenantService.java:
  createTenant(domain, name, adminUser)
  enableTenant(domain) / disableTenant(domain)
  deleteTenant(domain)
  getTenant(domain) / listTenants()
  setTenantQuota(domain, bytes)

新建 controller/TenantAdminController.java:
  POST/GET/PUT/DELETE /api/admin/tenants

DB migration:
  051-add-tenant-id-columns.xml (所有表增加 tenant_id)
  052-create-tenants-table.xml
```

---

#### 线路 B: 内容归档/恢复 + 导入导出 (Phase 390-391)

**内容归档:**

```
新建 entity:
  增加 Node.java:
    ArchiveStatus archiveStatus (LIVE/ARCHIVED/RESTORING)
    Instant archivedDate, String archivedBy
    String archiveStoreTier (HOT/WARM/COLD/GLACIER)

新建 service/ContentArchiveService.java:
  archiveNode(nodeId, storageTier)         — 归档到冷存储
  restoreNode(nodeId)                       — 恢复到热存储
  getArchiveStatus(nodeId)                  — 查询状态
  listArchivedNodes(pageable)               — 列出归档节点
  setArchivePolicy(folderId, policy)        — 设置归档策略 (按日期/大小)
  executeArchivePolicy()                    — @Scheduled 执行策略

修改 controller/NodeController.java:
  POST /api/nodes/{nodeId}/archive           — 归档
  POST /api/nodes/{nodeId}/restore           — 恢复
  GET  /api/nodes/{nodeId}/archive-status    — 状态
```

**ACP 导入/导出:**

```
新建 service:
  ExporterService.java:
    exportNodes(nodeIds, outputStream)       — 导出为 ACP (ZIP + manifest.xml)
    exportFolder(folderId, recursive)        — 导出文件夹
    exportSite(siteId)                       — 导出站点

  ImporterService.java:
    importPackage(inputStream, targetFolderId, options)
    options: replaceExisting, importAcl, remapUsers

新建 controller/ImportExportController.java:
  POST /api/export                           — 导出
  POST /api/import                           — 导入
  GET  /api/import/{jobId}/status            — 导入状态

ACP 格式:
  manifest.xml — 节点元数据 (类型/属性/权限/关联)
  content/ — 文件内容
  metadata/ — 版本历史
```

---

#### 线路 C: 内容复制 + 审计增强 (Phase 392-393)

**内容复制 (Replication):**

```
新建 entity:
  ReplicationDefinition.java:
    id, name, description, enabled
    sourcePath, targetEndpoint, targetPath
    schedule (Cron), lastRunDate, lastStatus
    filterType (NONE/MODIFIED_SINCE/TAG)

新建 service/ReplicationService.java:
  createDefinition(name, source, target, schedule)
  executeReplication(definitionId)    — 手动执行
  getDefinition(id) / listDefinitions()
  getStatus(definitionId)
  cancelReplication(definitionId)

新建 controller/ReplicationController.java:
  POST/GET/PUT/DELETE /api/replication-definitions
  POST /api/replication-definitions/{id}/execute

DB migration:
  053-create-replication-definitions-table.xml
```

**审计增强:**

```
修改 service/AuditService.java:
  增加: 审计应用 (AuditApplication) 概念
  增加: 按路径 prefix 启停审计
  增加: 按时间范围/ID 范围清理
  增加: 审计条目分页查询增强

修改 controller/AnalyticsController.java:
  GET /api/audit-applications                             — 列出审计应用
  PUT /api/audit-applications/{appId}                     — 启用/停用
  GET /api/audit-applications/{appId}/audit-entries       — 按应用查条目
  DELETE /api/audit-applications/{appId}/audit-entries     — 按范围清理

DB migration:
  054-create-audit-applications-table.xml
```

---

### Sprint 6: 协作功能 + 剩余企业级 (并行 3 条线)

**时间: 第 31-40 天**

#### 线路 A: 讨论/论坛 + 博客 + 日历 (Phase 394-396)

**讨论/论坛:**

```
新建 entity:
  DiscussionTopic.java: id, siteId, title, createdBy, createdDate, status, tags
  DiscussionReply.java: id, topicId, parentReplyId, content, createdBy, createdDate

新建 service/DiscussionService.java
新建 controller/DiscussionController.java:
  GET/POST /api/sites/{siteId}/discussions
  GET/PUT/DELETE /api/discussions/{topicId}
  GET/POST /api/discussions/{topicId}/replies
```

**博客:**

```
新建 entity:
  BlogPost.java: id, siteId, title, content, status (DRAFT/PUBLISHED), publishedDate, author, tags

新建 service/BlogService.java
新建 controller/BlogController.java:
  GET/POST /api/sites/{siteId}/blog/posts
  GET/PUT/DELETE /api/blog/posts/{postId}
  GET /api/sites/{siteId}/blog/posts/drafts
  GET /api/sites/{siteId}/blog/posts/published
```

**日历:**

```
新建 entity:
  CalendarEvent.java: id, siteId, title, description, location, startDate, endDate, allDay, recurrence, createdBy

新建 service/CalendarService.java
新建 controller/CalendarController.java:
  GET/POST /api/sites/{siteId}/calendar/events
  GET/PUT/DELETE /api/calendar/events/{eventId}
  GET /api/sites/{siteId}/calendar/events?from=&to=

前端:
  新建 pages/DiscussionPage.tsx, BlogPage.tsx, CalendarPage.tsx
  新建 services/discussionService.ts, blogService.ts, calendarService.ts
```

---

#### 线路 B: 模板/脚本引擎 + 表单引擎 (Phase 397-398)

**模板引擎:**

```
新建 service/TemplateService.java:
  processTemplate(templatePath, model)       — FreeMarker 渲染
  processTemplateString(template, model)     — 字符串模板
  registerExtension(name, extension)         — 注册扩展

  内置模板变量:
    document — 当前文档
    person — 当前用户
    companyhome — 根节点
    userhome — 用户主页
    date — 当前日期
    node — 泛型节点

新建 controller/TemplateController.java:
  POST /api/templates/execute                 — 执行模板
  GET  /api/templates                         — 列出模板
```

**脚本引擎:**

```
新建 service/ScriptService.java:
  executeScript(scriptPath, model)           — 执行脚本
  executeScriptString(script, model)         — 字符串脚本

  执行引擎: GraalJS (安全沙箱)
  内置 API 对象:
    companyhome, userhome, person, document
    search, actions, logger, utils

新建 controller/ScriptController.java:
  POST /api/scripts/execute                   — 执行脚本 (需 ADMIN)
```

**动态表单引擎:**

```
新建 entity/FormDefinition.java:
  id, name, fields (JSONB), validationRules (JSONB), layout (JSONB)

新建 service/FormService.java:
  getFormForType(typeId)                     — 根据类型生成表单
  getFormForWorkflowTask(taskId)             — 工作流任务表单
  submitForm(formId, data)                   — 提交表单数据
  validateForm(formId, data)                 — 验证

新建 controller/FormController.java:
  GET  /api/forms/types/{typeId}              — 类型表单
  GET  /api/forms/tasks/{taskId}              — 任务表单
  POST /api/forms/submit                      — 提交
```

---

#### 线路 C: 站点邀请 + IMAP 服务端 (Phase 399-400)

**站点邀请:**

```
新建 entity/Invitation.java:
  id, siteId, inviteType (NOMINATED/MODERATED)
  inviterUserId, inviteeUserId, inviteeEmail
  role, status (PENDING/ACCEPTED/REJECTED/CANCELLED)
  createdDate, respondedDate
  ticketToken (邮件链接)

新建 service/InvitationService.java:
  invite(siteId, inviteeEmail, role)          — 发送邀请
  acceptInvitation(token)                      — 接受
  rejectInvitation(token)                      — 拒绝
  cancelInvitation(invitationId)               — 取消
  listPendingInvitations(siteId)               — 待处理
  listUserInvitations(userId)                  — 用户的邀请

新建 controller/InvitationController.java:
  POST /api/sites/{siteId}/invitations
  GET  /api/sites/{siteId}/invitations
  PUT  /api/invitations/{invitationId}/accept
  PUT  /api/invitations/{invitationId}/reject
  DELETE /api/invitations/{invitationId}
```

**IMAP 服务端 (仓库暴露):**

```
新建 imap/:
  ImapServer.java                — Netty-based IMAP server
  ImapMailboxManager.java        — 映射 Athena 文件夹到 IMAP 邮箱
  ImapMessageMapper.java         — 映射文档到 IMAP 消息
  ImapConfig.java                — 配置 (端口, TLS, 映射规则)

配置:
  application.yml:
    athena.imap.enabled: true
    athena.imap.port: 143
    athena.imap.host: 0.0.0.0
    athena.imap.folder-mapping: "/Sites/{site}/documentLibrary"
```

---

## 三、CMIS 协议支持 (独立专项)

**时间: Sprint 5-6 并行 / 15 工日**

> CMIS 1.0/1.1 是 ECM 行业标准协议 — 完整支持可确保与第三方系统互操作

```
引入依赖:
  Apache Chemistry OpenCMIS Server 库

新建 cmis/ package:
  CmisServiceFactory.java          — OpenCMIS SPI 工厂
  AthenaCmisService.java           — CMIS 服务实现 (桥接 Athena API)
  CmisTypeManager.java             — Athena 类型 ↔ CMIS 类型映射
  CmisPermissionMapping.java       — 权限映射
  CmisQueryConverter.java          — CMIS SQL → Elasticsearch DSL
  CmisObjectFactory.java           — Athena Node → CMIS Object
  CmisNavigationService.java       — 层级导航
  CmisDiscoveryService.java        — 搜索
  CmisVersioningService.java       — 版本
  CmisRelationshipService.java     — 关联
  CmisAclService.java              — ACL
  CmisMultiFilingService.java      — 多父级 (二级子节点)
  CmisRenditionProvider.java       — Rendition 暴露

绑定:
  AtomPub binding: /api/cmis/atom11
  Browser binding: /api/cmis/browser

测试:
  CmisComplianceTest.java (使用 TCK)
```

---

## 四、前端全面增强计划

### 4.1 新增页面

| # | 页面 | 路由 | 描述 |
|---|------|------|------|
| 1 | SitesPage.tsx | /sites | 站点列表与管理 |
| 2 | SiteDetailPage.tsx | /sites/:id | 站点详情 (文档库/成员/讨论/博客/日历) |
| 3 | ActivityFeedPage.tsx | /activities | 个人活动流 |
| 4 | ContentModelManagerPage.tsx | /admin/content-models | 内容模型管理 |
| 5 | BulkImportPage.tsx | /admin/bulk-import | 批量导入 |
| 6 | DiscussionPage.tsx | /sites/:id/discussions | 讨论论坛 |
| 7 | BlogPage.tsx | /sites/:id/blog | 博客 |
| 8 | CalendarPage.tsx | /sites/:id/calendar | 日历 |
| 9 | ReplicationPage.tsx | /admin/replication | 内容复制管理 |
| 10 | TenantAdminPage.tsx | /admin/tenants | 租户管理 (super admin) |

### 4.2 新增组件

| # | 组件 | 用途 |
|---|------|------|
| 1 | SiteMembersPanel.tsx | 站点成员管理 |
| 2 | SiteCreateDialog.tsx | 创建站点对话框 |
| 3 | ActivityTimeline.tsx | 活动时间线 |
| 4 | ContentModelEditor.tsx | 模型编辑器 |
| 5 | TypeDefinitionPanel.tsx | 类型定义 |
| 6 | AspectDefinitionPanel.tsx | 切面定义 |
| 7 | PropertyDefinitionForm.tsx | 属性定义 |
| 8 | ConstraintEditor.tsx | 约束编辑 |
| 9 | RatingStars.tsx | 五星评分组件 |
| 10 | LikeButton.tsx | 点赞按钮 |
| 11 | AssociationsPanel.tsx | 关联管理面板 |
| 12 | ShareLinkDialogEnhanced.tsx | 增强共享对话框 (过期/密码/邮件) |
| 13 | ImportProgressPanel.tsx | 导入进度 |
| 14 | UserAvatarUpload.tsx | 头像上传 |
| 15 | QuotaBar.tsx | 配额进度条 |
| 16 | FollowButton.tsx | 关注按钮 |
| 17 | NodeAuditTab.tsx | 节点审计标签页 |

### 4.3 新增 Services

| # | 文件 | API 对接 |
|---|------|---------|
| 1 | siteService.ts | Sites API |
| 2 | activityService.ts | Activity Feed API |
| 3 | contentModelService.ts | Content Model API |
| 4 | dictionaryService.ts | Dictionary API |
| 5 | ratingService.ts | Rating API |
| 6 | preferenceService.ts | User Preferences API |
| 7 | subscriptionService.ts | Following API |
| 8 | associationService.ts | Node Associations API |
| 9 | bulkImportService.ts | Bulk Import API |
| 10 | replicationService.ts | Replication API |
| 11 | directAccessService.ts | Direct Access URL API |
| 12 | calendarService.ts | Calendar API |
| 13 | discussionService.ts | Discussion API |
| 14 | blogService.ts | Blog API |
| 15 | quotaService.ts | Quota API |

### 4.4 Store 增强

```
修改 store/slices/nodeSlice.ts:
  增加: aspects, ratings, associations 状态

新建 store/slices/siteSlice.ts:
  站点列表、当前站点、成员

新建 store/slices/activitySlice.ts:
  活动流数据、分页

修改 store/slices/uiSlice.ts:
  增加: siteSidebar, activityPanel 状态
```

---

## 五、数据库迁移汇总

| 编号 | Migration 文件 | 表/字段 |
|------|---------------|---------|
| 038 | create-content-model-tables | content_model_definitions, type_definitions, aspect_definitions, property_definitions, constraint_definitions |
| 039 | create-node-aspects-table | node_aspects (join table) |
| 040 | enhance-lock-service | node 表增加 lock_type, lock_expiry, lock_persistent, lock_deep |
| 041 | create-ratings-table | ratings |
| 042 | create-user-preferences-table | user_preferences |
| 043 | enhance-checkout-working-copy | document 表增加 working_copy_of, is_working_copy |
| 044 | enhance-associations | document_relations 增加 assoc_type, direction, order_index |
| 045 | enhance-share-links | share_links 增加 expiry_date, access_count, password |
| 046 | create-sites-tables | sites, site_members, site_containers, site_membership_requests |
| 047 | create-activities-tables | activities, activity_feed_controls |
| 048 | add-user-avatar-quota | users 增加 avatar/quota 字段 |
| 049 | create-subscriptions-table | subscriptions |
| 050 | create-global-attributes-table | global_attributes |
| 051 | add-tenant-id-columns | 所有表增加 tenant_id |
| 052 | create-tenants-table | tenants |
| 053 | create-replication-definitions | replication_definitions |
| 054 | create-audit-applications | audit_applications |
| 055 | create-discussion-tables | discussion_topics, discussion_replies |
| 056 | create-blog-posts-table | blog_posts |
| 057 | create-calendar-events-table | calendar_events |
| 058 | create-invitations-table | invitations |
| 059 | create-form-definitions-table | form_definitions |
| 060 | create-import-jobs-table | import_jobs |

---

## 六、测试计划

### 6.1 后端单元测试 (每个 Service 对应 1 个测试类)

| 测试类 | 覆盖功能 |
|--------|---------|
| ContentModelServiceTest | 模型 CRUD、验证、激活/停用 |
| DictionaryServiceTest | 类型/切面/属性查询、继承解析 |
| PropertyConstraintValidatorTest | 约束执行 (REGEX/LIST/RANGE/LENGTH) |
| AspectServiceTest | 切面附加/移除/查询 |
| LockServiceTest | 锁类型/超时/批量/继承/定期清理 |
| CheckOutCheckInServiceTest | 签出/签入/取消/工作副本 |
| RatingServiceTest | 评分 CRUD/汇总/方案 |
| PreferenceServiceTest | 偏好 CRUD/模式查询 |
| AssociationServiceTest | 关联 CRUD/方向/二级子节点 |
| ShareLinkEnhancedTest | 过期/密码/邮件/Rendition |
| SiteServiceTest | 站点 CRUD/成员/容器/申请 |
| ActivityServiceTest | 活动发布/Feed/控制 |
| SubscriptionServiceTest | 关注/取消/通知 |
| QuotaServiceTest | 配额设置/检查/重算 |
| PolicyComponentTest | 策略注册/调用/过滤 |
| BulkImportServiceTest | 导入/进度/取消 |
| DirectAccessUrlServiceTest | URL 生成/验证 |
| AttributeServiceTest | 全局属性 CRUD |
| TenantServiceTest | 租户 CRUD/隔离 |
| ContentArchiveServiceTest | 归档/恢复/策略 |
| ExporterServiceTest | ACP 导出 |
| ImporterServiceTest | ACP 导入 |
| ReplicationServiceTest | 复制定义/执行 |
| DiscussionServiceTest | 讨论 CRUD |
| BlogServiceTest | 博客 CRUD |
| CalendarServiceTest | 日历事件 CRUD |
| InvitationServiceTest | 邀请流程 |
| TemplateServiceTest | 模板渲染 |
| ScriptServiceTest | 脚本执行 (沙箱) |
| FormServiceTest | 表单生成/验证 |

### 6.2 前端测试

每个新页面/组件对应 `.test.tsx` 文件 (Jest + React Testing Library)

### 6.3 E2E 测试

每个 Sprint 完成后增加 Playwright E2E 测试:
- `e2e/content-model.spec.ts`
- `e2e/sites.spec.ts`
- `e2e/activity-feed.spec.ts`
- `e2e/ratings.spec.ts`
- `e2e/associations.spec.ts`
- `e2e/bulk-import.spec.ts`

---

## 七、完成后功能覆盖率预期

| 维度 | 当前 | 完成后 | 目标 |
|------|:----:|:------:|:----:|
| 核心服务接口 | ~35 / 65 (~54%) | 65 / 65 (100%) | ✅ 全覆盖 |
| REST 端点 | ~150 | ~300+ | ✅ 超越 |
| 核心业务功能 | ~30 / 52 (~58%) | 52 / 52 (100%) | ✅ 全覆盖 |
| 企业级功能 | ~40% | ~95% | ✅ 接近全覆盖 |
| Athena 独有超越 | 22 项 | 22+ 项 | ✅ 保持优势 |

### 功能覆盖矩阵 (完成后)

```
                          Alfresco    Athena (完成后)
核心文档管理                 ✅           ✅ + 增强锁定/工作副本
内容模型/数据字典            ✅           ✅ + JSONB 灵活属性
版本管理                    ✅           ✅ + 行级 Diff
权限/安全                   ✅           ✅ + 诊断/过期/模板版本
搜索                        ✅           ✅ + 批量操作/DryRun/统计
预览/渲染                   ✅           ✅ + CAD/诊断/死信/熔断
工作流                      ✅           ✅ + 完整 Flowable
规则/自动化                 ✅           ✅ + DryRun/幂等/统计
审计                        ✅           ✅ + 异步导出/分类
站点/协作空间                ✅           ✅ (新增)
活动流                      ✅           ✅ (新增)
关注/订阅                   ✅           ✅ (新增)
评论                        ✅ (扁平)    ✅ (嵌套/Reaction/提及)
标签/分类/收藏               ✅           ✅
评分/点赞                   ✅           ✅ (新增)
讨论/论坛                   ✅           ✅ (新增)
博客                        ✅           ✅ (新增)
日历                        ✅           ✅ (新增)
多租户                      ✅           ✅ (新增)
批量导入                    ✅           ✅ (新增)
内容归档                    ✅           ✅ (新增)
ACP 导入/导出                ✅           ✅ (新增)
策略/行为框架                ✅           ✅ (新增)
CMIS 协议                   ✅           ✅ (新增)
WebDAV                      ✅           ✅
邮件自动化                  ⚠️           ✅ (超越)
ERP 集成                    ❌           ✅ (Odoo)
在线编辑                    ❌ (社区版)   ✅ (WOPI/Collabora/WPS)
AI/ML/OCR                   ❌ (社区版)   ✅
病毒扫描                    ❌ (社区版)   ✅ (ClamAV)
Webhook                     ❌           ✅
Redis 缓存/队列             ❌           ✅
RabbitMQ 事件总线           ❌           ✅
PDF 注解                    ❌           ✅
异步任务中心                 ❌           ✅
运维恢复面板                 ❌           ✅
```

---

## 八、开发量汇总与时间线

| Sprint | 时间 | 并行线路 | 交付功能 | 工日 |
|:------:|:----:|:-------:|---------|:----:|
| **1** | D1-D3 | A: 内容模型 / B: 切面+锁定 / C: 评分+偏好+发现 | 6 项核心功能 | 9 |
| **2** | D4-D7 | A: 工作副本 / B: 关联增强 / C: 共享链接 | 3 项核心功能 | 12 |
| **3** | D8-D14 | A: 站点 / B: 活动流 / C: 头像+配额+订阅 | 5 项协作功能 | 21 |
| **4** | D15-D22 | A: 策略框架 / B: 批量导入+预签名 / C: 审计+属性 | 5 项企业功能 | 24 |
| **5** | D23-D30 | A: 多租户 / B: 归档+导入导出 / C: 复制+审计增强 | 4 项企业功能 | 24 |
| **6** | D31-D40 | A: 论坛+博客+日历 / B: 模板+脚本+表单 / C: 邀请+IMAP | 7 项功能 | 30 |
| **专项** | D23-D40 | CMIS 协议支持 | 1 项协议 | 15 |
| | | | **总计** | **~120 工日** |
| | | | **并行 3 线 → 实际** | **~40 天** |

---

## 九、风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 内容模型复杂度高 | 可能延期 | 第一阶段先实现类型/属性/约束，切面在 Sprint 1B 同步开发 |
| 多租户改造侵入性大 | 影响已有功能 | 使用 Hibernate @Filter 而非物理修改表结构，确保向后兼容 |
| CMIS 合规性验证 | 第三方系统兼容 | 使用 Apache Chemistry TCK 测试套件 |
| 站点系统数据隔离 | 权限泄露 | 站点权限与 ACL 系统集成，自动创建站点组 |
| 脚本引擎安全 | 远程代码执行 | 使用 GraalJS 沙箱，限制 API 访问范围，仅 ADMIN 可执行 |
| 数据库迁移量大 | 生产升级风险 | 每个 Sprint 独立迁移，支持回滚 |

---

## 十、质量门禁

每个 Sprint 交付需满足:

- [ ] 所有新 Service 有对应单元测试 (覆盖率 > 80%)
- [ ] 所有新 Controller 有安全测试 (权限校验)
- [ ] 新增 DB 迁移可正向/反向执行
- [ ] 前端新页面有 E2E 冒烟测试
- [ ] API 文档同步更新 (Swagger/OpenAPI)
- [ ] 不引入 OWASP Top 10 安全漏洞
- [ ] 现有测试套件全部通过

---

*Generated: 2026-03-29*
*Target: Complete Alfresco feature parity + retain 22 unique Athena advantages*
