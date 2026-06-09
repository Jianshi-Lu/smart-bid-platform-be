# 数据库结构文档

> 本文档描述 bid-winning-backend 项目的数据库表结构，所有表均通过 `src/main/resources/sql/ddl/` 下的 Flyway 命名规范脚本管理。

---

## 1. 表结构总览

```
┌──────────────────────────┐
│ bid_handler_info (V3)    │  经办人/招标负责人表
│  ├─ handler_id PK        │
│  ├─ handler_name         │
│  ├─ company_name         │
│  └─ department_name      │
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
│  └─ bidding_status / notes / created_at│
└──────────────────────────────────────┘
           │ 1:N
           ▼
┌──────────────────────────────────────┐
│ bid_winning_supplier_detail (V5)      │  中标结果明细表
│  ├─ detail_id PK                      │
│  ├─ notification_id FK                │
│  ├─ supplier_name                     │
│  └─ amount (DECIMAL 15,2)             │
└──────────────────────────────────────┘
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

**索引**：
- PK（detail_id）
- `idx_notification_id`（notification_id）

**业务关系**：
- 一条定标通知可对应多条中标明细（一对多）
- 与 `BidWinningMailRequest.bidWinningList` 字段一一对应

---

## 3. SQL 脚本版本管理

| 版本 | 文件 | 操作 |
|------|------|------|
| V1 | `V1__create_handler_info.sql` | 创建 `handler_info` 表 |
| V2 | `V2__update_handler_info_structure.sql` | 加 `company_name`、`department_name` 字段 |
| V3 | `V3__update_handler_info_to_bid_handler_info.sql` | 重命名为 `bid_handler_info` |
| V4 | `V4__create_bid_winning_notification.sql` | 创建 `bid_winning_notification` 表 |
| V5 | `V5__create_bid_winning_supplier_detail.sql` | 创建 `bid_winning_supplier_detail` 表 |

**执行顺序**：V1 → V2 → V3 → V4 → V5

---

## 4. 与 Java 实体的映射

当前 Java 代码中**尚未建立 JPA/MyBatis 实体类**，仅 `BidWinningMailRequest` DTO 与 `bid_winning_notification` + `bid_winning_supplier_detail` 业务对应：

| Java 字段 | 数据库表 | 数据库字段 |
|-----------|----------|------------|
| `BidWinningMailRequest.projectCode` | `bid_winning_notification` | `project_no` ⚠️ 命名不一致 |
| `BidWinningMailRequest.handlerName` | `bid_handler_info` | `handler_name` |
| `BidWinningMailRequest.bidWinningList[].supplierName` | `bid_winning_supplier_detail` | `supplier_name` |
| `BidWinningMailRequest.bidWinningList[].bidWinningAmount` | `bid_winning_supplier_detail` | `amount` ⚠️ 命名不一致 |

**已知问题**：
- `projectCode` vs `project_no`：Java 驼峰 vs DB 下划线（建议加 `@Column` 映射）
- `bidWinningAmount` vs `amount`：Java 详细命名 vs DB 简短命名（建议加 `@Column` 映射）

---

## 5. 待办

- [ ] 添加 `flyway-core` 依赖到 `pom.xml`
- [ ] 在 `application.yml` 配置 `spring.flyway.enabled: true`
- [ ] 创建 Java 实体类（`BidHandlerInfo`、`BidWinningNotification`、`BidWinningSupplierDetail`）
- [ ] 创建 MyBatis/JPA Mapper
- [ ] 解决 Java 字段与 DB 字段命名不一致问题
