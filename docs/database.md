# 数据库结构文档

> 本文档描述 bid-winning-backend 项目的数据库表结构。所有表通过 `src/main/resources/sql/ddl/` 下的脚本管理，脚本文件名采用 **Flyway 兼容的版本号格式**（V1__xxx.sql），便于未来切换到 Flyway / Liquibase 等迁移工具时无需改名。
>
> **当前状态**：**未实际启用 Flyway**，表结构需 DBA 或开发者手动执行 `sql/ddl/` 下的脚本。引入 Flyway 的步骤见第 5 节待办。
>
> **对外 API 契约**：前端联调请查阅 [`docs/api.md`](api.md)。本文档聚焦于**表结构与字段**视角。

---

## 1. 表结构总览

```
┌──────────────────────────┐
│ bid_handler_info (V3)    │  经办人/招标负责人表
│  ├─ handler_id PK        │
│  ├─ handler_name         │
│  ├─ company_name         │
│  ├─ department_name      │
│  └─ target_email        │
└──────────────────────────┘
           │ 1:N
           ▼
┌──────────────────────────────────────┐
│ bid_winning_notification (V4)        │  定标通知主表
│  ├─ notification_id PK                │
│  ├─ handler_id FK                     │
│  ├─ project_no / purchase_task_no     │
│  ├─ 招标规则（流程/方式/采购方式等）  │
│  ├─ 时间线（报名/技术/商务截止）      │
│  ├─ bidding_status / notes / created_at│
│  └─ target_email                  │
└──────────────────────────────────────┘
           │ 1:N
           ▼
┌──────────────────────────────────────┐
│ bid_winning_supplier_detail (V5)      │  中标结果明细表
│  ├─ detail_id PK                      │
│  ├─ notification_id FK                │
│  ├─ supplier_name                     │
│  ├─ amount (DECIMAL 15,2)             │
│  └─ target_email                    │
└──────────────────────────────────────┘
           │ 1:N
           ▼
┌──────────────────────────────────────────┐
│ bid_winning_risk_analysis (V7)           │  风险分析结果表
│  ├─ analysis_id PK                       │
│  ├─ detail_id FK                         │
│  ├─ supplier_name / notification_id      │
│  ├─ risk_level / risk_score              │
│  ├─ summary / key_points / suggestions   │
│  ├─ raw_response / model_name            │
│  ├─ prompt_version / analyzed_at         │
└──────────────────────────────────────────┘
```

---

## 2. 表详细结构

### 2.1 `bid_handler_info`（经办人/招标负责人表）

**演进历史**：
- V1：创建 `handler_info`（handler_id + handler_name）
- V2：增加 `company_name`、`department_name` 字段
- V3：重命名为 `bid_handler_info`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `handler_id` | INT | ✅ | 主键，自增 |
| `handler_name` | VARCHAR(50) | ✅ | 经办人姓名 |
| `company_name` | VARCHAR(100) | ✅ | 所属采购公司（V2+） |
| `department_name` | VARCHAR(100) | ✅ | 所属采购部门（V2+） |
| `target_email` | VARCHAR(255) | ❌ | 邮件通知收件人（已落库，V6+） |

**索引**：PK（handler_id）

**字符集**：utf8mb4 / InnoDB

---

### 2.2 `bid_winning_notification`（定标通知主表）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `notification_id` | INT | ✅ | 主键，自增 |
| `handler_id` | INT | ✅ | 外键，关联 `bid_handler_info.handler_id` |
| `project_no` | VARCHAR(50) | ✅ | 项目编号 |
| `purchase_task_no` | VARCHAR(50) | ✅ | 采购任务编号 |
| `platform_category` | VARCHAR(50) | ❌ | 平台采购类别 |
| `bidding_process` | VARCHAR(50) | ❌ | 招标流程 |
| `bidding_method` | VARCHAR(50) | ❌ | 招标方式 |
| `purchase_method` | VARCHAR(50) | ❌ | 采购方式 |
| `is_fixed_price` | TINYINT(1) | ❌ | 是否固定总价（1是 0否） |
| `target_cost` | DECIMAL(15,2) | ❌ | 目标成本 |
| `document_fee` | DECIMAL(10,2) | ❌ | 标书费 |
| `deposit` | DECIMAL(15,2) | ❌ | 保证金 |
| `credit_report_required` | TINYINT(1) | ❌ | 是否需要征信报告（1是 0否） |
| `registration_deadline` | DATETIME | ❌ | 报名截止时间 |
| `tech_bid_deadline` | DATETIME | ❌ | 技术回标截止时间 |
| `business_bid_deadline` | DATETIME | ❌ | 商务回标截止时间 |
| `bidding_status` | VARCHAR(20) | ❌ | 状态（默认 "已完成"） |
| `notes` | TEXT | ❌ | 备注 |
| `created_at` | DATETIME | ✅ | 创建时间（默认 CURRENT_TIMESTAMP） |
| `target_email` | VARCHAR(255) | ❌ | 邮件通知收件人（已落库，V6+） |

**索引**：
- PK（notification_id）
- `idx_staff_id`（handler_id）

**外键关系**：
- `handler_id` → `bid_handler_info.handler_id`（**逻辑外键**，未在数据库层加 FK 约束）

---

### 2.3 `bid_winning_supplier_detail`（中标结果明细表）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `detail_id` | INT | ✅ | 主键，自增 |
| `notification_id` | INT | ✅ | 外键，反向关联主表 |
| `supplier_name` | VARCHAR(100) | ✅ | 中标供应商名称 |
| `amount` | DECIMAL(15,2) | ✅ | 中标价（元） |
| `target_email` | VARCHAR(255) | ❌ | 邮件通知收件人（已落库，V6+） |

**索引**：
- PK（detail_id）
- `idx_notification_id`（notification_id）

**业务关系**：
- 一条定标通知可对应多条中标明细（一对多）
- 与 `BidWinningMailRequest.bidWinningList` 字段一一对应（用于 `/send-notification` 邮件模板）

---

### 2.4 `bid_winning_risk_analysis`（风险分析结果表）

**演进历史**：
- V7：创建 `bid_winning_risk_analysis` 表

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `analysis_id` | INT | ✅ | 主键，自增 |
| `detail_id` | INT | ✅ | 逻辑外键，关联 `bid_winning_supplier_detail.detail_id` |
| `supplier_name` | VARCHAR(255) | ✅ | 供应商名称（冗余存储） |
| `notification_id` | INT | ✅ | 逻辑外键，冗余存储便于查询 |
| `risk_level` | VARCHAR(20) | ✅ | 风险等级：LOW / MEDIUM / HIGH / CRITICAL |
| `risk_score` | DECIMAL(3,2) | ❌ | 风险评分 0.00-1.00 |
| `summary` | TEXT | ✅ | 风险摘要（模型输出） |
| `key_points` | TEXT | ❌ | 关键风险点 JSON 数组 |
| `suggestions` | TEXT | ❌ | 建议措施 |
| `raw_response` | MEDIUMTEXT | ✅ | 模型原始响应（便于审计/重放） |
| `model_name` | VARCHAR(50) | ✅ | 模型名称，如 deepseek-chat |
| `prompt_version` | VARCHAR(20) | ❌ | prompt 模板版本号 |
| `analyzed_at` | DATETIME | ✅ | 分析时间（默认 CURRENT_TIMESTAMP） |

**索引**：
- PK（analysis_id）
- `idx_detail`（detail_id）
- `idx_notification`（notification_id）
- `idx_analyzed_at`（analyzed_at）

**业务关系**：
- 一条中标明细可对应多条分析记录（历史追溯）
- 当前仅支持独立端点触发分析，创建中标明细时不会自动触发

---

## 3. SQL 脚本版本管理

| 版本 | 文件 | 操作 |
|------|------|------|
| V1 | `V1__create_handler_info.sql` | 创建 `handler_info` 表 |
| V2 | `V2__update_handler_info_structure.sql` | 加 `company_name`、`department_name` 字段 |
| V3 | `V3__update_handler_info_to_bid_handler_info.sql` | 重命名为 `bid_handler_info` |
| V4 | `V4__create_bid_winning_notification.sql` | 创建 `bid_winning_notification` 表 |
| V5 | `V5__create_bid_winning_supplier_detail.sql` | 创建 `bid_winning_supplier_detail` 表 |
| V6 | `V6__add_target_email.sql` | 三个业务表各加 `target_email` 字段 |
| V7 | `V7__create_bid_winning_risk_analysis.sql` | 创建 `bid_winning_risk_analysis` 表 |

**执行顺序**：V1 → V2 → V3 → V4 → V5 → V6 → V7

---

## 4. 与 Java 实体的映射

项目已在 `src/main/java/io/gitee/lujianshi/bidwinningbackend/entity/` 下建立 4 个 Entity 类（纯 POJO）：

| Java Entity | 数据库表 |
|-------------|----------|
| `BidHandlerInfo` | `bid_handler_info` |
| `BidWinningNotification` | `bid_winning_notification` |
| `BidWinningSupplierDetail` | `bid_winning_supplier_detail` |
| `BidWinningRiskAnalysis` | `bid_winning_risk_analysis` |

### 4.1 Entity ↔ DTO 字段对应

DTO 字段命名（业务视角）与 Entity 字段命名（DB 视角）存在差异：

| Entity 字段（DB 对齐） | DTO 字段（业务对齐） | DB 字段 |
|----------------------|---------------------|---------|
| `BidWinningNotification.projectNo` | `BidWinningMailRequest.projectCode` | `project_no` ⚠️ |
| `BidWinningNotification.handlerId` | `BidWinningMailRequest.handlerName`（无 ID 概念） | `handler_id` |
| `BidWinningNotification.handlerId` | `BidHandlerInfo.handlerId` | `handler_id` |
| `BidWinningSupplierDetail.supplierName` | `BidWinningListItem.supplierName` | `supplier_name` |
| `BidWinningSupplierDetail.amount` | `BidWinningListItem.bidWinningAmount` | `amount` ⚠️ |

**已知命名差异**（待 ORM 引入后处理）：
- `projectNo` vs `projectCode`
- `amount` vs `bidWinningAmount`

**解决思路**：
- 方案 A：Entity 跟 DB 命名，DTO 跟业务命名，加 Mapper 转换层
- 方案 B：Entity 跟 DTO 命名，加 `@TableField("xxx")` 映射

---

## 5. 待办

- [ ] 添加 `flyway-core` 依赖到 `pom.xml`（**当前未引入**）
- [ ] 在 `application.yml` 配置 `spring.flyway.enabled: true`（**当前未启用**）
- [x] 创建 Java 实体类（`BidHandlerInfo`、`BidWinningNotification`、`BidWinningSupplierDetail`、`BidWinningRiskAnalysis`）
- [x] 加 MyBatis-Plus 注解（`@TableName` + `@TableId`）
- [x] 创建 Mapper 接口 + XML（`BidHandlerInfo` / `BidWinningNotification` / `BidWinningSupplierDetail` / `BidWinningRiskAnalysis` 四个 Mapper 均已实现）
- [x] 引入 MyBatis-Plus 框架
- [x] **BidHandlerInfo 端到端 7 层链路打通 + 5 个 Controller 端点**（POST create / GET getById / PATCH update / DELETE delete / GET list）
- [x] **BidWinningNotification 端到端 7 层链路打通 + 5 个 Controller 端点**（PATCH 语义 + 动态 SQL + 17 字段 DTO）
- [x] **BidWinningSupplierDetail 端到端 7 层链路打通 + 6 个 Controller 端点**（PATCH 语义 + 动态 SQL + 3 字段 DTO + 批量删除 p3 升级版）
- [x] 新增 `GlobalExceptionHandler` 统一异常处理（`@Valid` 校验失败统一 400 响应）
- [ ] 新增 `MybatisPlusConfig`（分页插件 + 自动填充）
- [ ] 解决 Java 字段与 DB 字段命名不一致问题（`projectNo` vs `projectCode` 等）
- [ ] 给所有 Controller 端点加 `@Valid` 注解（当前未启用，DTO 校验失效）

---

## 6. 端到端开发流程

> DB 表是开发链路的起点。从建表 SQL 到暴露给前端的 Controller，每一层产物和约定详见 [`docs/development-flow.md`](development-flow.md)。

**链路总览（7 层自下而上）**：

```
DB 表（V*.sql，Flyway 兼容命名，当前未启用 Flyway）
  ↓
Entity（@TableName + @TableId）
  ↓
Mapper 接口（手写签名，不继承 BaseMapper）
  ↓
Mapper XML（namespace 绑定，SQL 独立）
  ↓
Service（注入 Mapper，不继承 IService）
  ↓
DTO（@NotBlank / @Size 校验）
  ↓
Controller（@Valid 触发校验）
```

**当前已实现**：
- `BidHandlerInfo` 完整 7 层 + 6 个 Controller 端点（含 `POST /bid-handler-info/batch-delete`，p3 升级版返实体列表）
- `BidWinningNotification` 完整 7 层 + 6 个 Controller 端点（PATCH 风格 + 17 字段动态 SQL + 批量删除）
- `BidWinningSupplierDetail` 完整 7 层 + 6 个 Controller 端点（PATCH 风格 + 3 字段动态 SQL + 批量删除）
