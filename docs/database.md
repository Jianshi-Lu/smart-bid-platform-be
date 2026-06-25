# 数据库结构（bid-winning-backend）

> 4 张表的最终结构（V1-V17）。SQL 脚本位于 `src/main/resources/sql/ddl/`，采用 Flyway 兼容命名，**当前未启用 Flyway**（需手动执行）。
>
> API 契约：[`docs/api.md`](api.md) · 项目架构：[`docs/architecture.md`](architecture.md)

---

## 1. 表结构总览

```
┌──────────────────────────────┐
│ tender_handler_info (V3)        │  经办人/招标负责人
│  ├─ tender_handler_id PK            │
│  ├─ handler_name / company_name / department_name
│  └─ target_email (V6)         │
└──────────────────────────────┘
           │ 1:N（逻辑外键）
           ▼
┌────────────────────────────────────────┐
│ tender_notice (V14)         │  招标公告主表
│  ├─ notice_id PK                │
│  ├─ tender_handler_id (FK)                    │
│  ├─ project_no / purchase_task_no      │
│  ├─ 招标规则（流程/方式/采购方式等）  │
│  ├─ 时间线（报名/技术/商务截止）      │
│  ├─ 采购公司/部门/tendering_party     │
│  ├─ status (DRAFT/IN_PROGRESS/COMPLETED)│
│  └─ notes / created_at                 │
└────────────────────────────────────────┘
           │ 1:N（逻辑外键）
           ▼
┌──────────────────────────────────────────────┐
│ tender_notice_status_history (V15)           │  状态变更审计
│  ├─ history_id PK                            │
│  ├─ notice_id (FK)                           │
│  ├─ from_status / to_status / remark         │
│  └─ created_at                               │
└──────────────────────────────────────────────┘
           │ 1:N（逻辑外键）
           ▼
┌──────────────────────────────┐
│ tenderer_detail (V16)       │  投标人明细
│  ├─ tenderer_id PK          │
│  ├─ tenderer_name            │
│  ├─ quote_price (DECIMAL)    │
│  ├─ tenderer_contact (V16)   │
│  └─ credentials (V16)        │
└──────────────────────────────┘

┌──────────────────────────────────┐
│ risk_analysis (V7→V9)            │  AI 风险分析结果
│  ├─ analysis_id PK              │
│  ├─ tenderer_id (V9→V16, FK)     │
│  ├─ tenderer_name                │
│  ├─ risk_level / summary / suggestions
│  ├─ raw_response (V11: JSON)     │
│  ├─ prompt_version / analyzed_at  │
└──────────────────────────────────┘
```

**关键变化**：
- V8：`bid_winning_risk_analysis` → `risk_analysis`
- V9：`detail_id` → `supplier_id`；删除 `notification_id` / `risk_score` / `key_points` / `model_name` 字段
- V10：`bid_winning_supplier_detail` → `supplier_detail`；`detail_id` → `supplier_id`；`amount` → `quote_price`；`target_email` → `supplier_contact`；删除 `notification_id`
- V16：`supplier_detail` → `tenderer_detail`；`supplier_id` → `tenderer_id`；`supplier_name` → `tenderer_name`；`supplier_contact` → `tenderer_contact`；新增 `credentials`；`risk_analysis` 列同步
- V11：`raw_response` 类型 `MEDIUMTEXT` → `JSON`

---

## 2. 表详细结构

### 2.1 `tender_handler_info`（经办人）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `tender_handler_id` | INT | ✅ | 主键，自增 |
| `handler_name` | VARCHAR(50) | ✅ | 经办人姓名 |
| `company_name` | VARCHAR(100) | ✅ | 所属采购公司（V2+） |
| `department_name` | VARCHAR(100) | ✅ | 所属采购部门（V2+） |
| `target_email` | VARCHAR(255) | ❌ | 通知邮件收件人（V6+，已落库） |

**索引**：PK（tender_handler_id）

**演进**：V1（创建 `handler_info`）→ V2（加 company/department）→ V3（重命名 `bid_handler_info`）→ V13（重命名 `tender_handler_info`）→ V6（加 `target_email`）

### 2.2 `tender_notice`（招标公告）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `notice_id` | INT | ✅ | 主键，自增 |
| `tender_handler_id` | INT | ✅ | 逻辑外键 → `tender_handler_info.tender_handler_id` |
| `project_no` | VARCHAR(50) | ✅ | 项目编号 |
| `purchase_task_no` | VARCHAR(50) | ✅ | 采购任务编号 |
| `platform_category` | VARCHAR(50) | ❌ | 平台采购类别 |
| `tender_process` | VARCHAR(50) | ❌ | 招标流程 |
| `tender_method` | VARCHAR(50) | ❌ | 招标方式 |
| `purchase_method` | VARCHAR(50) | ❌ | 采购方式 |
| `is_fixed_price` | TINYINT(1) | ❌ | 是否固定总价 |
| `has_document_fee` | TINYINT(1) | ❌ | 标书费（1有 0无） |
| `deposit` | DECIMAL(15,2) | ❌ | 保证金 |
| `credit_report_required` | TINYINT(1) | ❌ | 是否需要征信报告 |
| `registration_deadline` | DATETIME | ❌ | 报名截止时间 |
| `technical_tender_deadline` | DATETIME | ❌ | 技术回标截止时间 |
| `business_tender_deadline` | DATETIME | ❌ | 商务回标截止时间 |
| `notes` | TEXT | ❌ | 备注 |
| `created_at` | DATETIME | ✅ | 创建时间（默认 CURRENT_TIMESTAMP） |
| `purchase_company` | VARCHAR(100) | ❌ | 采购公司 |
| `purchase_department` | VARCHAR(100) | ❌ | 采购部门 |
| `tendering_party` | VARCHAR(100) | ❌ | 招标主体 |
| `status` | VARCHAR(20) | ✅ | 状态：DRAFT / IN_PROGRESS / COMPLETED（默认 DRAFT） |

**索引**：PK（notice_id） + `idx_staff_id`（tender_handler_id）

**演进**：V4（创建）→ V6（加 `target_email`）→ V14（重构字段）→ V15（加 status + 状态变更历史表）

### 2.3 `tenderer_detail`（投标人明细）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `tenderer_id` | INT | ✅ | 主键，自增 |
| `tenderer_name` | VARCHAR(100) | ✅ | 投标人名称 |
| `quote_price` | DECIMAL(15,2) | ✅ | 报价（元） |
| `tenderer_contact` | VARCHAR(255) | ✅ | 联系方式（邮箱/电话/微信，V16 重命名自 `supplier_contact`） |
| `credentials` | VARCHAR(500) | ❌ | 资信材料文件链接（V16 新增） |

**索引**：PK（tenderer_id）

**演进**：
- V5（创建 `bid_winning_supplier_detail`，含 `detail_id` / `notification_id` / `supplier_name` / `amount` / `target_email`）
- V10（重命名 `supplier_detail`；`detail_id` → `supplier_id`；`amount` → `quote_price`；`target_email` → `supplier_contact`；删除 `notification_id`）
- V16（重命名 `tenderer_detail`；`supplier_id` → `tenderer_id`；`supplier_name` → `tenderer_name`；`supplier_contact` → `tenderer_contact`；新增 `credentials`）

### 2.4 `risk_analysis`（AI 风险分析结果）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `analysis_id` | INT | ✅ | 主键，自增 |
| `tenderer_id` | INT | ✅ | 逻辑外键 → `tenderer_detail.tenderer_id`（V9 重命名自 `detail_id`，V16 重命名） |
| `tenderer_name` | VARCHAR(255) | ✅ | 投标人名称（冗余存储，V16 重命名） |
| `risk_level` | VARCHAR(20) | ✅ | LOW / MEDIUM / HIGH / CRITICAL |
| `summary` | TEXT | ✅ | 风险摘要 |
| `suggestions` | TEXT | ❌ | 建议措施 |
| `raw_response` | JSON | ✅ | 模型原始响应 JSON（V11 由 MEDIUMTEXT 改为 JSON） |
| `prompt_version` | VARCHAR(20) | ❌ | prompt 模板版本号 |
| `analyzed_at` | DATETIME | ✅ | 分析时间（默认 CURRENT_TIMESTAMP） |

**索引**：PK（analysis_id） + `idx_tenderer`（tenderer_id，V9 重命名，V16 同步，V17 索引名同步） + `idx_analyzed_at`

### 2.5 `tender_notice_status_history`（招标公告状态变更历史）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `history_id` | INT | ✅ | 主键，自增 |
| `notice_id` | INT | ✅ | 逻辑外键 → `tender_notice.notice_id` |
| `from_status` | VARCHAR(20) | ❌ | 变更前状态（首次为 null） |
| `to_status` | VARCHAR(20) | ✅ | 变更后状态 |
| `remark` | VARCHAR(255) | ❌ | 变更备注 |
| `created_at` | DATETIME | ✅ | 变更时间（默认 CURRENT_TIMESTAMP） |

**索引**：PK（history_id） + `idx_notice_id`（notice_id）

**演进**：V15（新建）

**演进**：
- V7（创建 `bid_winning_risk_analysis`，13 字段）
- V8（重命名为 `risk_analysis`）
- V9（精简字段：`detail_id` → `supplier_id`；删除 `notification_id` / `risk_score` / `key_points` / `model_name`）
- V16（`supplier_id` → `tenderer_id`；`supplier_name` → `tenderer_name`）
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
| V13 | `V13__rename_to_tender_handler_info.sql` | 重命名 `tender_handler_info` + `tender_handler_id` |
| V4 | `V4__create_tender_notice.sql` | 创建 `tender_notice` |
| V5 | `V5__create_bid_winning_supplier_detail.sql` | 创建 `bid_winning_supplier_detail` |
| V6 | `V6__add_target_email.sql` | 三个业务表加 `target_email` |
| V7 | `V7__create_bid_winning_risk_analysis.sql` | 创建 `bid_winning_risk_analysis` |
| V8 | `V8__rename_bid_winning_risk_analysis_to_risk_analysis.sql` | 重命名 `risk_analysis` |
| V9 | `V9__simplify_risk_analysis_and_rename_detail_id.sql` | 精简字段；`detail_id` → `supplier_id` |
| V10 | `V10__restructure_supplier_detail.sql` | 重构 `supplier_detail` |
| V11 | `V11__change_raw_response_to_json.sql` | `raw_response` 改 JSON |
| V12 | `V12__rename_to_tender_notice.sql` | 重命名 `tender_notice` + `notice_id` |
| V13 | `V13__rename_to_tender_handler_info.sql` | 重命名 `tender_handler_info` + `tender_handler_id` |
| V14 | `V14__restructure_tender_notice.sql` | 重构 `tender_notice` 字段（对标招标公告业务场景） |
| V15 | `V15__add_tender_notice_status.sql` | `tender_notice` 加 status + 新建 `tender_notice_status_history` 表 |
| V16 | `V16__rename_supplier_to_tenderer.sql` | 重命名 `tenderer_detail` + `credentials` + `risk_analysis` 列同步 |
| V17 | `V17__rename_idx_supplier_to_idx_tenderer.sql` | `risk_analysis` 索引名 `idx_supplier` → `idx_tenderer`（V16 漏改） |

**执行顺序**：V1 → V2 → ... → V17

---

## 4. Entity 映射

| Java Entity | 数据库表 | 主键 | 主键 Java 字段 |
|-------------|----------|------|----------------|
| `TenderHandlerInfo` | `tender_handler_info` | `tender_handler_id` | `tenderHandlerId` |
| `TenderNotice` | `tender_notice` | `notice_id` | `noticeId` |
| `TendererDetail` | `tenderer_detail` | `tenderer_id` | `tendererId` |
| `RiskAnalysis` | `risk_analysis` | `analysis_id` | `analysisId` |
| `TenderNoticeStatusHistory` | `tender_notice_status_history` | `history_id` | `historyId` |

Entity 字段命名遵循 Java 驼峰 ↔ DB 下划线（`map-underscore-to-camel-case: true`），主键用 `Integer` 包装类（区分 null 与 0），金额用 `BigDecimal`，布尔用 `Boolean`，时间用 `LocalDateTime`。

---

## 5. 与 Java 实体的命名差异

DTO 字段命名（业务视角）与 Entity 字段命名（DB 视角）基本一致。唯一差异点：

| Entity 字段 | DTO 字段 | DB 字段 | 备注 |
|------------|----------|---------|------|
| `TenderNotice.projectNo` | `BidWinningMailRequest.projectCode` | `project_no` | DTO 跨实体复用（mail 模板） |
| `TenderNotice.tenderHandlerId` | `BidWinningMailRequest.handlerName`（无 ID 概念）| `tender_handler_id` | mail 模板只需名字 |
| `TendererDetail.tendererName` | `BidWinningListItem.tendererName` | `tenderer_name` | 中标结果项 |
| `TendererDetail.quotePrice` | `BidWinningListItem.bidWinningAmount` | `quote_price` | 中标结果项 |

差异源自 `BidWinningMailRequest` 跨实体复用 mail 模板（`templates/tender-notice.html`），不影响业务 Service。
