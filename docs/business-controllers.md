# 业务 Controller 通用说明

> 涵盖 `BidHandlerInfoController` / `BidWinningNotificationController` / `BidWinningSupplierDetailController` 三个业务 Controller 的统一行为规范。

---

## 1. 统一响应包装 `Result<T>`

所有端点统一返回 `io.gitee.lujianshi.bidwinningbackend.dto.common.Result<T>`：

```json
{
  "code": 200,
  "message": "创建成功",
  "data": <业务对象>
}
```

业务状态码约定：

| code | 含义 | data |
|---|---|---|
| 200 | 成功 | 业务对象 / `Diff<T>` / `PageInfo<T>` |
| 400 | 参数错误（如 sort 字段名拼错） | `null` |
| 404 | 资源不存在 | `null` |

## 2. 7 层开发链路

自下而上：

| 层 | 实体 | 说明 |
|---|---|---|---|
| ① | DB 表 | MySQL `bid_handler_info` / `bid_winning_notification` / `bid_winning_supplier_detail` |
| ② | Entity | `@TableName` + `@TableId(type = AUTO)`，字段驼峰 ↔ DB 下划线 |
| ③ | Mapper 接口 | 继承 `BaseMapper<T>`（项目自建泛型基接口，7 标准方法） |
| ④ | Mapper XML | namespace = 接口 FQN，SQL 独立维护 |
| ⑤ | Service | 继承 `BaseService<T>`（项目自建泛型基类，`@Transactional` 写方法）；子类实现 `getMapper()` + `getIdExtractor()` |
| ⑥ | DTO | `<Entity>CreateRequest`（含 `@NotBlank`/`@NotNull`/`@Size` 校验）<br>`<Entity>UpdateRequest`（仅 `@Size`，PATCH 语义）<br>`<Entity>BatchDeleteRequest`（每端点独立 DTO，含 `@NotEmpty` + `@Size(max=500)`） |
| ⑦ | Controller | 本目录 3 个类 |

## 3. 端点模板（6 端点 × 3 Controller = 18 端点）

| Method | Path | 用途 | DTO | 返回 |
|---|---|---|---|---|
| POST | `/create-<entity>` | 创建 | `CreateRequest` | `Result<Entity>` |
| GET | `/<entity>/{id}` | 按主键单条查询 | — | `Result<Entity>` |
| PATCH | `/<entity>/{id}` | 按主键部分更新 | `UpdateRequest` | `Result<Diff<Entity>>`（含 before/after） |
| DELETE | `/<entity>/{id}` | 按主键删除 | — | `Result<Void>` |
| GET | `/<entity>` | 列表查询（分页） | query: `page` / `size` / `sort` | `Result<PageInfo<Entity>>` |
| POST | `/<entity>/batch-delete` | 按主键列表批量删除 | `BatchDeleteRequest` | `Result<List<Entity>>`（data 返实际删除的实体列表） |

### 3.1 `<entity>` 命名

| Controller | `<entity>` |
|---|---|
| `BidHandlerInfoController` | `bid-handler-info` |
| `BidWinningNotificationController` | `bid-winning-notification` |
| `BidWinningSupplierDetailController` | `bid-winning-supplier-detail` |

## 4. PATCH 语义（RFC 5789）

- 用于**部分更新**：调用方传哪些字段就更新哪些字段
- 未传的字段（`null`）在 DB 中保持原值
- XML 动态 SQL（`<set> + <if test="xxx != null">`）实现
- 三表风格一致

## 5. 分页机制（PageHelper）

- 库：`com.github.pagehelper:pagehelper-spring-boot-starter:4.1.0`
- 请求参数：`page` / `size`（默认 1 / 3-10） / `sort`（默认 `<pk> desc`，**空格分隔**）
- SQL 拦截：PageHelper 用 ThreadLocal 在 `startPage` 后拦截下一条 SELECT，自动追加 LIMIT
- ThreadLocal 清理：`finally { PageHelper.clearPage() }` 防止线程池污染
- 异常处理：catch 任何异常返 `Result.error(400, "分页查询参数错误: ...")`
- 已知坑：`Page<Object>` **不**实现 `AutoCloseable` → 用 `//noinspection try` 而非 try-with-resources

## 6. 批量删除（p3 升级版）

- 永远 200，**不**区分"全部不存在"与"部分不存在"
- `data` = 实际删除的实体列表（前端展示用）
- 不存在的 ID：不在 data 中，前端 diff 自己请求的 ids 即可知道 missing
- **多 1 次 SELECT**（PK 索引，毫秒级），换 response payload 实用性
- **不**做完整性校验 / **不**做级联 / **不**做孤儿清理
- DB 物理顺序（无 ORDER BY），最小实现
- `BidWinningSupplierDetail` 与 `BidWinningNotification` 是逻辑外键关系，删除主表后明细行变孤儿

## 7. 字段对照表（Controller `<entity>` ↔ 主键）

| Controller | 主键 | DB 列 |
|---|---|---|
| `BidHandlerInfoController` | `handlerId` | `handler_id` |
| `BidWinningNotificationController` | `notificationId` | `notification_id` |
| `BidWinningSupplierDetailController` | `detailId` | `detail_id` |

## 8. 待办

- 当前 `create` / `patch` 端点未加 `@Valid` 校验，DTO 上 `@NotBlank` / `@NotNull` / `@Size` 暂未生效（待 `GlobalExceptionHandler` 一起补全）
- 批量删除 DTO 已加 `@Valid`
- 分页 sort 参数未来可加白名单（防 SQL 注入 / 防字段拼错），当前透传给 PageHelper
- 统一异常处理（`GlobalExceptionHandler` 尚未实现）

## 9. AOP 邮件通知

- 大多数端点（3 Controller × 6 端点 = 18 端点中 17 个）标注 `@SendEmailOnSuccess(to = "ops@company.com")`
- 由 `CrudResultEmailAspect` 切面拦截，业务代码 0 改动
- 收件人覆盖：HTTP Header `Target-Email-Address` 优先于注解 `to()`
- 完整执行链路详见 `docs/aop-execution-chain.md`

## 10. 试点：DTO 传参邮件通知（仅 `BidHandlerInfoController.create`）

> 试点方案，**仅 `BidHandlerInfoController.createBidHandlerInfo` 一个端点**应用此模式。
> 其他 17 个端点继续走 AOP 邮件通知体系。

### 10.1 试点设计

- **脱离 AOP 体系**：`createBidHandlerInfo` 不再标注 `@SendEmailOnSuccess`
- **收件人由 DTO 传入**：`BidHandlerInfoCreateRequest.targetEmail` 字段（可选，null/空 = 不发）
- **Service 层显式调用**：`BidHandlerInfoService.notifyEmail(entity, targetEmail)` 渲染 `crud-result` 模板 + `MailService.sendHtmlEmail`
- **复用现有组件**：`crud-result` Thymeleaf 模板 + `ResourceSearcher.validateTemplateExists` + `SpringTemplateEngine` + `MailService`，与 AOP 用同一套链路

### 10.2 收件人优先级（试点端点）

```
targetEmail (DTO JSON body)  >  无 fallback，直接跳过
```

即试点端点**不读 HTTP Header**，收件人**只能**从 JSON body 传。如不传则不发邮件。

### 10.3 请求示例

```bash
curl -X POST http://localhost:8080/create-bid-handler-info \
  -H "Content-Type: application/json" \
  -d '{
    "handlerName":"张三",
    "companyName":"测试采购公司",
    "departmentName":"采购部",
    "targetEmail":"alice@example.com"
  }'
```

### 10.4 试点价值

- 验证 DTO 传参收件人可行性（替代 Header 方式）
- 验证 Service 层显式调用邮件链路（替代 AOP 自动触发）
- 试点结果稳定后可推广到其他 2 个 Controller
