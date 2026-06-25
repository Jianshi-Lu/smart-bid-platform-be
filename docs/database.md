# 数据库结构（bid-winning-backend）

> 4 张表的最终结构（V1-V11）。SQL 脚本位于 `src/main/resources/sql/ddl/`，采用 Flyway 兼容命名，**当前未启用 Flyway**（需手动执行）。
>
> API 契约：[`docs/api.md`](api.md) · 项目架构：[`docs/architecture.md`](architecture.md)

---

## 1. 表结构总览

```
┌──────────────────────────────┐
│ bid_handler_info (V3)        │  经办人/招标负责人
│  ├─ handler_id PK            │
│  ├─ handler_name / company_name / department_name
│  └─ target_email (V6)         │
└──────────────────────────────┘
           │ 1:N（逻辑外键）
           ▼
┌────────────────────────────────────────┐
│ bid_winning_notification (V4)          │  定标通知主表
│  ├─ notification_id PK                │
│  ├─ handler_id (FK)                    │
│  ├─ project_no / purchase_task_no      │
│  ├─ 招标规则（流程/方式/采购方式等）  │
│  ├─ 时间线（报名/技术/商务截止）      │
│  ├─ bidding_status / notes / created_at│
│  └─ target_email (V6)                  │
└────────────────────────────────────────┘
           │ 1:N（逻辑外键）
           ▼
┌──────────────────────────────┐
│ supplier_detail (V10)        │  供应商明细
│  ├─ supplier_id PK          │
│  ├─ supplier_name            │
│  ├─ quote_price (DECIMAL)    │
│  └─ supplier_contact (V10)   │
└──────────────────────────────┘

┌──────────────────────────────────┐
│ risk_analysis (V7→V9)            │  AI 风险分析结果
│  ├─ analysis_id PK              │
│  ├─ supplier_id (V9, FK)         │
│  ├─ supplier_name                │
│  ├─ risk_level / summary / suggestions
│  ├─ raw_response (V11: JSON)     │
│  ├─ prompt_version / analyzed_at  │
└──────────────────────────────────┘
```

**关键变化**：
- V8：`bid_winning_risk_analysis` → `risk_analysis`
- V9：`detail_id` → `supplier_id`；删除 `notification_id` / `risk_score` / `key_points` / `model_name` 字段
- V10：`bid_winning_supplier_detail` → `supplier_detail`；`detail_id` → `supplier_id`；`amount` → `quote_price`；`target_email` → `supplier_contact`；删除 `notification_id`
- V11：`raw_response` 类型 `MEDIUMTEXT` → `JSON`

---

## 2. 表详细结构

### 2.1 `bid_handler_info`（经办人）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `handler_id` | INT | ✅ | 主键，自增 |
| `handler_name` | VARCHAR(50) | ✅ | 经办人姓名 |
| `company_name` | VARCHAR(100) | ✅ | 所属采购公司（V2+） |
| `department_name` | VARCHAR(100) | ✅ | 所属采购部门（V2+） |
| `target_email` | VARCHAR(255) | ❌ | 通知邮件收件人（V6+，已落库） |

**索引**：PK（handler_id）

**演进**：V1（创建 `handler_info`）→ V2（加 company/department）→ V3（重命名 `bid_handler_info`）→ V6（加 `target_email`）

### 2.2 `bid_winning_notification`（定标通知）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `notification_id` | INT | ✅ | 主键，自增 |
| `handler_id` | INT | ✅ | 逻辑外键 → `bid_handler_info.handler_id` |
| `project_no` | VARCHAR(50) | ✅ | 项目编号 |
| `purchase_task_no` | VARCHAR(50) | ✅ | 采购任务编号 |
| `platform_category` | VARCHAR(50) | ❌ | 平台采购类别 |
| `bidding_process` | VARCHAR(50) | ❌ | 招标流程 |
| `bidding_method` | VARCHAR(50) | ❌ | 招标方式 |
| `purchase_method` | VARCHAR(50) | ❌ | 采购方式 |
| `is_fixed_price` | TINYINT(1) | ❌ | 是否固定总价 |
| `target_cost` | DECIMAL(15,2) | ❌ | 目标成本 |
| `document_fee` | DECIMAL(10,2) | ❌ | 标书费 |
| `deposit` | DECIMAL(15,2) | ❌ | 保证金 |
| `credit_report_required` | TINYINT(1) | ❌ | 是否需要征信报告 |
| `registration_deadline` | DATETIME | ❌ | 报名截止时间 |
| `tech_bid_deadline` | DATETIME | ❌ | 技术回标截止时间 |
| `business_bid_deadline` | DATETIME | ❌ | 商务回标截止时间 |
| `bidding_status` | VARCHAR(20) | ❌ | 状态（默认 "已完成"） |
| `notes` | TEXT | ❌ | 备注 |
| `created_at` | DATETIME | ✅ | 创建时间（默认 CURRENT_TIMESTAMP） |
| `target_email` | VARCHAR(255) | ❌ | 通知邮件收件人（V6+，已落库） |

**索引**：PK（notification_id） + `idx_staff_id`（handler_id）

**演进**：V4（创建）→ V6（加 `target_email`）

### 2.3 `supplier_detail`（供应商明细）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `supplier_id` | INT | ✅ | 主键，自增 |
| `supplier_name` | VARCHAR(100) | ✅ | 中标供应商名称 |
| `quote_price` | DECIMAL(15,2) | ✅ | 中标价（元） |
| `supplier_contact` | VARCHAR(255) | ✅ | 联系方式（邮箱/电话/微信，V10 重命名自 `target_email`） |

**索引**：PK（supplier_id）

**演进**：
- V5（创建 `bid_winning_supplier_detail`，4 字段含 `detail_id` / `notification_id` / `supplier_name` / `amount` / `target_email`）
- V10（重命名 `supplier_detail`；`detail_id` → `supplier_id`；`amount` → `quote_price`；`target_email` → `supplier_contact`；删除 `notification_id` 字段）

> ⚠️ V10 删除了 `notification_id` 后，`supplier_detail` 与 `bid_winning_notification` 的关联断开。背景模块未来需重新建立关联。

### 2.4 `risk_analysis`（AI 风险分析结果）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `analysis_id` | INT | ✅ | 主键，自增 |
| `supplier_id` | INT | ✅ | 逻辑外键 → `supplier_detail.supplier_id`（V9 重命名自 `detail_id`） |
| `supplier_name` | VARCHAR(255) | ✅ | 供应商名称（冗余存储） |
| `risk_level` | VARCHAR(20) | ✅ | LOW / MEDIUM / HIGH / CRITICAL |
| `summary` | TEXT | ✅ | 风险摘要 |
| `suggestions` | TEXT | ❌ | 建议措施 |
| `raw_response` | JSON | ✅ | 模型原始响应 JSON（V11 由 MEDIUMTEXT 改为 JSON） |
| `prompt_version` | VARCHAR(20) | ❌ | prompt 模板版本号 |
| `analyzed_at` | DATETIME | ✅ | 分析时间（默认 CURRENT_TIMESTAMP） |

**索引**：PK（analysis_id） + `idx_supplier`（supplier_id，V9 重命名） + `idx_analyzed_at`

**演进**：
- V7（创建 `bid_winning_risk_analysis`，13 字段）
- V8（重命名为 `risk_analysis`）
- V9（精简字段：`detail_id` → `supplier_id`；删除 `notification_id` / `risk_score` / `key_points` / `model_name`）
- V11（`raw_response` 类型 `MEDIUMTEXT` → `JSON`）

**前置检查**（执行 V11 前必跑）：
```sql
SELECT COUNT(*) FROM risk_analysis WHERE NOT JSON_VALID(raw_response);
-- 结果必须为 0
```

---

## 3. SQL 脚本版本管理

| 版本 | 文件 | 操作 |
|------|------|------|
| V1 | `V1__create_handler_info.sql` | 创建 `handler_info` 表 |
| V2 | `V2__update_handler_info_structure.sql` | 加 `company_name` / `department_name` |
| V3 | `V3__update_handler_info_to_bid_handler_info.sql` | 重命名为 `bid_handler_info` |
| V4 | `V4__create_bid_winning_notification.sql` | 创建 `bid_winning_notification` |
| V5 | `V5__create_bid_winning_supplier_detail.sql` | 创建 `bid_winning_supplier_detail` |
| V6 | `V6__add_target_email.sql` | 三个业务表加 `target_email` |
| V7 | `V7__create_bid_winning_risk_analysis.sql` | 创建 `bid_winning_risk_analysis` |
| V8 | `V8__rename_bid_winning_risk_analysis_to_risk_analysis.sql` | 重命名 `risk_analysis` |
| V9 | `V9__simplify_risk_analysis_and_rename_detail_id.sql` | 精简字段；`detail_id` → `supplier_id` |
| V10 | `V10__restructure_supplier_detail.sql` | 重构 `supplier_detail` |
| V11 | `V11__change_raw_response_to_json.sql` | `raw_response` 改 JSON |

**执行顺序**：V1 → V2 → ... → V11

---

## 4. Entity 映射

| Java Entity | 数据库表 | 主键 | 主键 Java 字段 |
|-------------|----------|------|----------------|
| `BidHandlerInfo` | `bid_handler_info` | `handler_id` | `handlerId` |
| `BidWinningNotification` | `bid_winning_notification` | `notification_id` | `notificationId` |
| `SupplierDetail` | `supplier_detail` | `supplier_id` | `supplierId` |
| `RiskAnalysis` | `risk_analysis` | `analysis_id` | `analysisId` |

Entity 字段命名遵循 Java 驼峰 ↔ DB 下划线（`map-underscore-to-camel-case: true`），主键用 `Integer` 包装类（区分 null 与 0），金额用 `BigDecimal`，布尔用 `Boolean`，时间用 `LocalDateTime`。

---

## 5. 与 Java 实体的命名差异

DTO 字段命名（业务视角）与 Entity 字段命名（DB 视角）基本一致。唯一差异点：

| Entity 字段 | DTO 字段 | DB 字段 | 备注 |
|------------|----------|---------|------|
| `BidWinningNotification.projectNo` | `BidWinningMailRequest.projectCode` | `project_no` | DTO 跨实体复用（mail 模板） |
| `BidWinningNotification.handlerId` | `BidWinningMailRequest.handlerName`（无 ID 概念）| `handler_id` | mail 模板只需名字 |
| `SupplierDetail.supplierName` | `BidWinningListItem.supplierName` | `supplier_name` | 中标结果项 |
| `SupplierDetail.quotePrice` | `BidWinningListItem.bidWinningAmount` | `quote_price` | 中标结果项 |

差异源自 `BidWinningMailRequest` 跨实体复用 mail 模板（`templates/bid-winning-notification.html`），不影响业务 Service。
