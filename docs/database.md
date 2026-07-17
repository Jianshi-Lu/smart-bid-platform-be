# 数据库结构（bid-winning-backend）

> 6 张表的最终结构（V1-V21）。SQL 脚本位于 `src/main/resources/sql/ddl/`，采用 Flyway 兼容命名，**当前未启用 Flyway**（需手动执行）。
>
> API 契约：[`docs/api.md`](api.md) · 项目架构：[`docs/architecture.md`](architecture.md)

---

## 1. 表结构总览

```
┌──────────────────────────────┐
│ bid_handler_info (V3→V13→V18→V19) │  招标经办人
│  ├─ bid_handler_id PK            │
│  ├─ handler_name / company_name / department_name
│  └─ target_email (V6)         │
└──────────────────────────────┘
           │ 1:N（逻辑外键 bid_handler_id）
           ▼
┌────────────────────────────────────────────┐
│ invitation_for_bid (V4→V12→V14→V18)         │  招标公告（Invitation for Bid）
│  ├─ invitation_id PK (V18 短命名)             │
│  ├─ bid_handler_id (FK)                       │
│  ├─ project_no / purchase_task_no            │
│  ├─ 招标规则（流程/方式/采购方式等）            │
│  ├─ 时间线（报名/技术/商务截止）                 │
│  ├─ 采购公司/部门/tendering_party              │
│  ├─ status (DRAFT/PUBLISHED/CLOSED/AWARDED/CANCELLED)│
│  └─ notes / created_at                        │
└────────────────────────────────────────────┘
           │ 1:N（逻辑外键 invitation_id）
           ▼
┌────────────────────────────────────────────────┐
│ invitation_for_bid_status_history (V15→V18)    │  状态变更审计
│  ├─ history_id PK                              │
│  ├─ invitation_id (FK)                         │
│  ├─ from_status / to_status / remark           │
│  └─ created_at                                 │
└────────────────────────────────────────────────┘
           │ 1:N（逻辑外键 invitation_id）
           ▼
┌──────────────────────────────────────────────────┐
│ bid_record (V5→V10→V16→V18: 合并自 tenderer_detail) │  投标记录
│  ├─ bid_record_id PK                            │
│  ├─ invitation_id (V18 新增, FK)                │
│  ├─ bid_no (V18 新增) 投标次数                          │
│  ├─ bid_at (V18 新增) 投标时间                          │
│  ├─ bidder_name (V18 重命名自 tenderer_name)        │
│  ├─ quote_price (DECIMAL)                       │
│  ├─ bidder_contact (V18 重命名自 tenderer_contact) │
│  └─ credentials JSON (V20)                       │
│                                                  │
│ UNIQUE (invitation_id, bidder_name, bid_no) — V18│
│ INDEX (invitation_id) — V18                       │
└──────────────────────────────────────────────────┘
           │ 1:N（逻辑外键 bid_record_id，提交投标后回填）
           ▼
┌─────────────────────────────────────────────┐
│ credential_file (V21)                       │  资信文件
│  ├─ file_id PK                               │
│  ├─ upload_batch_id                          │
│  ├─ bid_record_id (提交后回填)                │
│  ├─ original_file_path                       │
│  ├─ markdown_file_path                       │
│  └─ convert_status (UPLOADED/CONVERTING/    │
│                    CONVERTED/FAILED)         │
└─────────────────────────────────────────────┘

┌────────────────────────────────────────┐
│ risk_analysis (V7→V9→V18)              │  AI 风险分析结果
│  ├─ analysis_id PK                      │
│  ├─ bid_record_id (V18 FK, 旧 tenderer_id) │
│  ├─ bidder_name (V18, 旧 tenderer_name)     │
│  ├─ risk_level / summary / suggestions    │
│  ├─ raw_response (V11: JSON)             │
│  └─ prompt_version / analyzed_at          │
└────────────────────────────────────────┘
```

**关键变化（V18 一次性大重构）**：

- **RENAME TABLE**：
  - `tender_handler_info` → `bid_handler_info`
  - `tender_notice` → `invitation_for_bid`（World Bank 术语：Invitation for Bids）
  - `tender_notice_status_history` → `invitation_for_bid_status_history`
  - `tenderer_detail` → `bid_record`（语义整合：原本"投标人明细"实质就是"投标记录"，合并为同一表）
- **列重命名**：
  - `tender_notice.notice_id` → `invitation_for_bid.invitation_id`（PK 短命名）
  - `tender_notice.tender_handler_id` → `invitation_for_bid.bid_handler_id`
  - `tenderer_detail.tenderer_id` → `bid_record.bid_record_id`（**PK 重命名**）
  - `tenderer_detail.tenderer_name` → `bid_record.bidder_name`（World Bank：Bidder）
  - `tenderer_detail.tenderer_contact` → `bid_record.bidder_contact`
  - `risk_analysis.tenderer_id` → `risk_analysis.bid_record_id`（FK 列）
  - `risk_analysis.tenderer_name` → `risk_analysis.bidder_name`
- **`bid_record` 新增 3 列**：`invitation_id` / `bid_no`（同 invitation+bidder 递增）/ `bid_at`
- **`bid_record` 约束**：`UNIQUE (invitation_id, bidder_name, bid_no)` 防并发同号；`INDEX (invitation_id)` 提升按公告查询
- **索引同步**：`risk_analysis.idx_tenderer` → `risk_analysis.idx_bid_record`

更早演进（V1-V17）见底部"§3 SQL 版本管理"。

---

## 2. 表详细结构

### 2.1 `bid_handler_info`（招标经办人）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `bid_handler_id` | INT | ✅ | 主键，自增（V19 重命名自 `tender_handler_id`） |
| `handler_name` | VARCHAR(50) | ✅ | 经办人姓名 |
| `company_name` | VARCHAR(100) | ✅ | 所属采购公司（V2+） |
| `department_name` | VARCHAR(100) | ✅ | 所属采购部门（V2+） |
| `target_email` | VARCHAR(255) | ❌ | 通知邮件收件人（V6+，已落库） |

**索引**：PK（bid_handler_id）

**演进**：V1（创建 `handler_info`）→ V2（加 company/department）→ V3（重命名 `bid_handler_info`）→ V6（加 `target_email`）→ V13（重命名 `tender_handler_info`）→ **V18**（重命名 `bid_handler_info`）→ **V19**（PK 列 `tender_handler_id` → `bid_handler_id`）

### 2.2 `invitation_for_bid`（招标公告，World Bank 术语）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `invitation_id` | INT | ✅ | 主键，自增（V18 重命名自 `notice_id`） |
| `bid_handler_id` | INT | ✅ | 逻辑外键 → `bid_handler_info.bid_handler_id` |
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
| `status` | VARCHAR(20) | ✅ | 状态：DRAFT / PUBLISHED / CLOSED / AWARDED / CANCELLED（默认 DRAFT） |

**索引**：PK（invitation_id） + `idx_staff_id`（bid_handler_id）

**演进**：V4（创建 `tender_notice`）→ V6（加 `target_email`）→ V14（重构字段）→ V15（加 status + 状态变更历史表）→ **V18**（重命名 `invitation_for_bid`，列名同步）

### 2.3 `bid_record`（投标记录，原 tenderer_detail 合并）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `bid_record_id` | INT | ✅ | 主键，自增（V18 重命名自 `tenderer_id`） |
| `invitation_id` | INT | ✅ | 逻辑外键 → `invitation_for_bid.invitation_id`（V18 新增） |
| `bid_no` | INT | ✅ | 投标次数（V18 新增，同 invitation + bidder 下从 1 递增） |
| `bid_at` | DATETIME | ✅ | 投标时间（V18 新增，默认 CURRENT_TIMESTAMP） |
| `bidder_name` | VARCHAR(100) | ✅ | 投标人名称（V18 重命名自 `tenderer_name`） |
| `quote_price` | DECIMAL(15,2) | ✅ | 报价（元） |
| `bidder_contact` | VARCHAR(255) | ✅ | 联系方式（V18 重命名自 `tenderer_contact`） |
| `credentials` | JSON | ❌ | 转换后 Markdown 文件路径数组，顺序与 `credential_file.file_order` 一致（V20） |

**索引 / 约束**：
- PK（bid_record_id）
- `UNIQUE (invitation_id, bidder_name, bid_no)`（V18，防并发同号）
- `INDEX idx_invitation_id (invitation_id)`（V18，按公告查询）

**演进**：
- V5（创建 `bid_winning_supplier_detail`）
- V10（重命名 `supplier_detail`；`amount` → `quote_price`）
- V16（重命名 `tenderer_detail`；`supplier_*` → `tenderer_*`；新增 `credentials`）
- **V18**（重命名 `bid_record`；**PK `tenderer_id` → `bid_record_id`**；新增 `invitation_id`/`bid_no`/`bid_at`；列名 `tenderer_*` → `bidder_*`；加 UNIQUE + INDEX）
- **V20**（`credentials` 从单个字符串路径改为 Markdown 文件路径 JSON 数组；测试数据直接清空）

### 2.4 `risk_analysis`（AI 风险分析结果）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `analysis_id` | INT | ✅ | 主键，自增 |
| `bid_record_id` | INT | ✅ | 逻辑外键 → `bid_record.bid_record_id`（V18 重命名自 `tenderer_id`） |
| `bidder_name` | VARCHAR(255) | ✅ | 投标人名称（冗余存储，V18 重命名） |
| `risk_level` | VARCHAR(20) | ✅ | LOW / MEDIUM / HIGH / CRITICAL |
| `summary` | TEXT | ✅ | 风险摘要 |
| `suggestions` | TEXT | ❌ | 建议措施 |
| `raw_response` | JSON | ✅ | 模型原始响应 JSON（V11 由 MEDIUMTEXT 改为 JSON） |
| `prompt_version` | VARCHAR(20) | ❌ | prompt 模板版本号 |
| `analyzed_at` | DATETIME | ✅ | 分析时间（默认 CURRENT_TIMESTAMP） |

**索引**：PK（analysis_id） + `idx_bid_record`（V18 重命名自 `idx_tenderer`）+ `idx_analyzed_at`

**演进**：
- V7（创建 `bid_winning_risk_analysis`，13 字段）
- V8（重命名 `risk_analysis`）
- V9（精简字段）
- V11（`raw_response` 类型 `MEDIUMTEXT` → `JSON`）
- V16（`supplier_*` → `tenderer_*`）
- **V18**（`tenderer_*` → `bidder_*`；`idx_tenderer` → `idx_bid_record`）

### 2.5 `credential_file`（资信文件）

每个原始上传文件对应一条记录；同一次上传的文件通过 `upload_batch_id` 聚合。
文件上传阶段 `bid_record_id` 保持 `NULL`，创建投标记录后再回填。

> **当前实现状态**：`POST /credential-files/upload` 已完成本地原文件落盘、SHA-256 计算和相对路径返回，但尚未执行本表 INSERT。以下字段已经由上传响应提供，下一阶段接入 `CredentialFileMapper` 后再持久化。

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `file_id` | BIGINT | ✅ | 文件记录主键，自增 |
| `upload_batch_id` | CHAR(36) | ✅ | 上传批次 UUID，由 Java 后端生成 |
| `bid_record_id` | INT | ❌ | 逻辑外键 → `bid_record.bid_record_id`，提交投标后回填 |
| `file_order` | INT | ✅ | 文件在上传批次中的顺序 |
| `original_filename` | VARCHAR(255) | ✅ | 用户上传时的原始文件名 |
| `content_type` | VARCHAR(100) | ❌ | 文件 MIME 类型 |
| `file_size` | BIGINT | ✅ | 原始文件大小，字节 |
| `sha256` | CHAR(64) | ✅ | 原始文件 SHA-256 |
| `storage_type` | VARCHAR(20) | ✅ | 当前为 `LOCAL`，后续可扩展 `MINIO` |
| `original_file_path` | VARCHAR(500) | ✅ | 原始文件物理存储相对路径 |
| `markdown_file_path` | VARCHAR(500) | ❌ | 转换后的 Markdown 文件相对路径 |
| `convert_status` | VARCHAR(20) | ✅ | `UPLOADED` / `CONVERTING` / `CONVERTED` / `FAILED` |
| `error_message` | VARCHAR(1000) | ❌ | 转换失败原因 |
| `created_at` | DATETIME | ✅ | 文件记录创建时间 |
| `converted_at` | DATETIME | ❌ | Markdown 转换完成时间 |
| `bound_at` | DATETIME | ❌ | 与投标记录绑定时间 |

当前本地存储路径约定：

```text
{upload_batch_id}/original/{file_uuid}_{安全原文件名}
```

数据库中的 `original_file_path` 保存上述相对路径，存储根目录由运行环境配置，不写入数据库。`sha256` 使用 JDK `MessageDigest(SHA-256)` 对落盘后的实际内容计算。

**索引**：PK（file_id）+ `idx_upload_batch_id` + `idx_bid_record_id` + `idx_convert_status`

**演进**：**V21**（新建资信文件表）

### 2.6 `invitation_for_bid_status_history`（招标公告状态变更历史）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `history_id` | INT | ✅ | 主键，自增 |
| `invitation_id` | INT | ✅ | 逻辑外键 → `invitation_for_bid.invitation_id`（V18 重命名自 `notice_id`） |
| `from_status` | VARCHAR(20) | ❌ | 变更前状态（首次为 null） |
| `to_status` | VARCHAR(20) | ✅ | 变更后状态 |
| `remark` | VARCHAR(255) | ❌ | 变更备注 |
| `created_at` | DATETIME | ✅ | 变更时间（默认 CURRENT_TIMESTAMP） |

**索引**：PK（history_id） + `idx_invitation_id`（V18 重命名自 `idx_notice_id`）

**演进**：V15（新建）→ **V18**（表名+列名+索引名同步重命名）

### 2.6 前置检查（执行 V18 前必跑）

```sql
-- 现有 tenderer_detail 数据需清空（V18 合并为 bid_record，数据无迁移）
TRUNCATE TABLE risk_analysis;
TRUNCATE TABLE tenderer_detail;
```

**V18 DDL 关键约束**：`bid_record` 的列重命名与新增列必须分两步执行（`ALTER TABLE bid_record` 拆为 2 条），原因：

- `tenderer_id` 是 PK，拆 Step 4 单独 `CHANGE` 为 `bid_record_id` 后，Step 5 的 `ADD COLUMN invitation_id ... AFTER bid_record_id` 才有合法锚点；
- Step 5 的 `UNIQUE (invitation_id, bidder_name, bid_no)` 引用 Step 4 已 `CHANGE` 的 `bidder_name`，同一 ALTER 内引用同 ALTER 内 CHANGE 的列在 MySQL 8 下虽可能通过，但拆开更稳。

具体见 `V18__rename_to_bid_convention.sql` 的 Step 4 / Step 5 注释。

---

## 3. SQL 脚本版本管理

| 版本 | 文件 | 操作 |
|------|------|------|
| V1 | `V1__create_handler_info.sql` | 创建 `handler_info` 表 |
| V2 | `V2__update_handler_info_structure.sql` | 加 `company_name` / `department_name` |
| V3 | `V3__update_handler_info_to_bid_handler_info.sql` | 重命名为 `bid_handler_info` |
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
| **V18** | `V18__rename_to_bid_convention.sql` | **全面 bid 化重命名 + bid_record 合并 + 3 列新增** |
| **V19** | `V19__rename_primary_key_of_bid_handler_info.sql` | **`bid_handler_info` PK 列 `tender_handler_id` → `bid_handler_id`** |
| **V20** | `V20__change_bid_record_credentials_to_json.sql` | **`bid_record.credentials` 改为 Markdown 路径 JSON 数组；清空测试数据** |
| **V21** | `V21__create_credential_file.sql` | **新建 `credential_file` 资信文件表** |

**执行顺序**：V1 → V2 → ... → V21

**V18 前置**：先 TRUNCATE `risk_analysis` 和 `tenderer_detail`（旧数据无迁移）。

---

## 4. Entity 映射

| Java Entity | 数据库表 | 主键 | 主键 Java 字段 |
|-------------|----------|------|----------------|
| `BidHandlerInfo` | `bid_handler_info` | `bid_handler_id` | `bidHandlerId` |
| `InvitationForBid` | `invitation_for_bid` | `invitation_id` | `invitationId` |
| `BidRecord` | `bid_record` | `bid_record_id` | `bidRecordId` |
| `CredentialFile` | `credential_file` | `file_id` | `fileId` |
| `RiskAnalysis` | `risk_analysis` | `analysis_id` | `analysisId` |
| `InvitationForBidStatusHistory` | `invitation_for_bid_status_history` | `history_id` | `historyId` |

Entity 字段命名遵循 Java 驼峰 ↔ DB 下划线（`map-underscore-to-camel-case: true`），主键用 `Integer` 包装类（区分 null 与 0），金额用 `BigDecimal`，布尔用 `Boolean`，时间用 `LocalDateTime`。

---

## 5. 与 Java 实体的命名差异

DTO 字段命名（业务视角）与 Entity 字段命名（DB 视角）基本一致。差异点：

| Entity 字段 | DTO 字段 | DB 字段 | 备注 |
|------------|----------|---------|------|
| `InvitationForBid.projectNo` | `BidWinningMailRequest.projectCode` | `project_no` | DTO 跨实体复用（mail 模板） |
| `InvitationForBid.bidHandlerId` | `BidWinningMailRequest.handlerName`（无 ID 概念）| `bid_handler_id` | mail 模板只需名字 |
| `BidRecord.bidderName` | `BidWinningListItem.bidderName` | `bidder_name` | 中标结果项（V18 重命名） |
| `BidRecord.quotePrice` | `BidWinningListItem.bidWinningAmount` | `quote_price` | 中标结果项 |

差异源自 `BidWinningMailRequest` 跨实体复用 mail 模板（`templates/bid-winning-notification*.html`），不影响业务 Service。
