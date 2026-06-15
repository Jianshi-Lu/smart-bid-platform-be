# AGENTS.md

## Build & Run

- Use `./mvnw` (Maven wrapper included) - do not require system Maven
- Run: `./mvnw spring-boot:run`
- Test: `./mvnw test`
- Package: `./mvnw clean package`

## Tech Stack

- Java 25 (required)
- Spring Boot 4.0.6 with `spring-boot-starter-webmvc` (servlet-based, not reactive)
- Lombok for boilerplate reduction
- DevTools enabled for runtime reloading

## Project Structure

- Single module Maven project
- Standard layout: `src/main/java`, `src/main/resources`, `src/test/java`
- Source root package: `io.gitee.lujianshi.bidwinningbackend`
- Layered architecture:
  - `controller/` - HTTP 路由层（@RestController + @GetMapping / @PostMapping / @PatchMapping / @DeleteMapping）
    - HelloController - 演示 / 异步重定向
    - PlainTextMailController - 纯文本邮件发送
    - BidWinningMailController - HTML 中标通知书邮件发送
    - BidHandlerInfoController - 经办人 CRUD（6 端点已实现：create / getById / patch / delete / list / batch-delete）
    - BidWinningNotificationController - 定标通知 CRUD（6 端点已实现：create / getById / patch / delete / list / batch-delete）
  - `dto/` - 数据传输对象（@Data + @NoArgsConstructor + @AllArgsConstructor）
    - PlainTextMailRequest - 纯文本邮件请求
    - BidWinningMailRequest - 中标通知书请求（extends PlainTextMailRequest）
    - BidWinningListItem - 中标结果条目（独立顶层类）
    - BidHandlerInfoCreateRequest - 创建经办人请求（3 字段 + @NotBlank/@Size）
    - BidHandlerInfoUpdateRequest - 更新经办人请求（PATCH DTO，3 字段仅 @Size，无 @NotBlank）
    - BidWinningNotificationCreateRequest - 创建定标通知请求（17 字段：3 必填 + 14 可选）
    - BidWinningNotificationUpdateRequest - 更新定标通知请求（PATCH DTO，17 字段全部可选）
    - BidHandlerInfoBatchDeleteRequest - 批量删除经办人请求（独立 DTO，1 字段 List<Integer> ids + @NotEmpty + @Size max=500）
    - BidWinningNotificationBatchDeleteRequest - 批量删除定标通知请求（独立 DTO，结构同上）
    - BidWinningSupplierDetailBatchDeleteRequest - 批量删除中标结果明细请求（**预留**，等 SupplierDetail Controller 启用）
    - Result<T> - 通用响应包装（code/message/data + success/error 工厂）
    - Diff<T> - 通用 diff 包装（before/after，用于 PATCH update 端点）
  - `entity/` - 数据库实体（@Data + @NoArgsConstructor + @AllArgsConstructor，纯 POJO）
    - BidHandlerInfo - 经办人/招标负责人
    - BidWinningNotification - 定标通知主表
    - BidWinningSupplierDetail - 中标结果明细
  - `utils/` - 工具类
    - AsyncTaskManager - 延迟异步任务执行器（纯 Java 线程，非 Spring Bean）
  - `templates/` - Thymeleaf 模板文件（src/main/resources/templates/）

## Entry Point

- `BidWinningBackendApplication.java` — Spring Boot 启动入口（纯 bootstrap，无路由）
- Dev server: `http://localhost:8080`
- 配置端口: `server.port=8080`（application-dev.yml）

## Profiles（多环境配置）

- `application.yml` — 公共配置（应用名、Thymeleaf 路径）+ 默认 profile
- `application-dev.yml` — 开发环境（默认，端口 8080，MySQL URL 含默认值）
- `application-test.yml` — 测试环境（端口 8080，MySQL URL 含默认值，DEBUG 日志）
- `application-prod.yml` — 生产环境（端口 80，敏感信息走环境变量）

**切换方式**：
1. 修改 `application.yml` 中 `spring.profiles.active`
2. 启动命令：`./mvnw spring-boot:run -Dspring-boot.run.profiles=test`
3. 环境变量：`export SPRING_PROFILES_ACTIVE=prod`

## 环境变量配置

### 占位符变量（启动时必须提供）

| 变量 | 必填 | 用途 | 示例 |
|------|:----:|------|------|
| `MYSQL_URL` | ✅ | JDBC URL | `jdbc:mysql://10.0.0.100:3306/bid-winning?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true` |
| `MYSQL_USERNAME` | ✅ | DB 用户名 | `jianshi` |
| `MYSQL_PASSWORD` | ✅ | DB 密码 | `Ljs83455012.` |
| `PROD_MAIL_USERNAME` | ⚠️ prod 必填 | 发件邮箱 | `service@company.com` |
| `PROD_MAIL_PASSWORD` | ⚠️ prod 必填 | 邮箱授权码 | `xxxx` |
| `PROD_MAIL_HOST` | ❌ 有默认 `smtp.qq.com` | SMTP 主机 | `smtp.exmail.qq.com` |
| `PROD_MAIL_PORT` | ❌ 有默认 `587` | SMTP 端口 | `465` |

> ⚠️ dev 环境必须设 `MYSQL_*` 三个变量；prod 环境必须设 `PROD_MAIL_USERNAME` + `PROD_MAIL_PASSWORD`（其他 prod mail 变量有默认值）。
> mail.password 在 dev 环境保持明文（开发效率优先），prod 强制走环境变量。

### dev 环境提供方式

dev 环境通过以下任一方式注入 `MYSQL_*` 环境变量：

1. **IDEA Run/Debug Configurations** → Environment variables 逐个添加
2. **Shell 启动**：
   ```bash
   export MYSQL_URL='jdbc:mysql://...' MYSQL_USERNAME=jianshi MYSQL_PASSWORD=xxx
   ./mvnw spring-boot:run
   ```
3. **JVM 系统属性**（最稳）：
   ```
   -DMYSQL_URL=jdbc:mysql://... -DMYSQL_USERNAME=jianshi -DMYSQL_PASSWORD=xxx
   ```

### 常见错误

| 错误 | 现象 | 解决 |
|------|------|------|
| 变量名拼写错误 | 报 `Access denied for user '${MYSQL_USERNAME}'` | 检查 IDEA 环境变量名是否完全一致 |
| 多个 Run Configuration 混淆 | 旧配置仍生效 | 删除或修正 Run Configuration |
| 没点 Apply / OK | 环境变量未保存 | 改完配置点 Apply 再 OK |
| dev 切到 prod | 缺 `${PROD_MAIL_*}` 占位符报错 | 加环境变量或切回 dev profile |
| 缺 `allowPublicKeyRetrieval=true` | MySQL 8 + SSL 关闭时 `Public Key Retrieval is not allowed` | URL 末尾加 `&allowPublicKeyRetrieval=true` |

## API Endpoints

| Method | Path | Content-Type | Description |
|-------|------|-------------|-------------|
| GET | /hello | - | 返回 "Hello World!" |
| GET | /access-url | - | 重定向到 custom.url |
| POST | /fetch-url | - | 异步触发延迟任务，同步返回 URL |
| POST | /send-email | application/x-www-form-urlencoded | 发送纯文本邮件 |
| POST | /send-notification | application/json | 发送中标通知书 HTML 邮件（异步延迟5秒返回"发送成功"） |
| POST | /create-bid-handler-info | application/json | 创建经办人（返回 `Result<BidHandlerInfo>`，code=200） |
| GET | /bid-handler-info/{id} | - | 按主键查询单条经办人（`Result<BidHandlerInfo>`，code=200/404） |
| PATCH | /bid-handler-info/{id} | application/json | 按主键**部分更新**经办人（`Result<Diff<BidHandlerInfo>>` 含 before/after；动态 SQL） |
| DELETE | /bid-handler-info/{id} | - | 按主键删除经办人（`Result<Void>`，code=200/404） |
| GET | /bid-handler-info | - | 列表查询（`Result<PageInfo<BidHandlerInfo>>`，PageHelper 分页；query 参数 `page` / `size` / `sort`） |
| POST | /bid-handler-info/batch-delete | application/json | 批量删除经办人（`Result<List<BidHandlerInfo>>`，data=实际删除的实体列表；p3 升级版） |
| POST | /create-bid-winning-notification | application/json | 创建定标通知（17 字段，code=200） |
| GET | /bid-winning-notification/{id} | - | 按主键查询单条定标通知（code=200/404） |
| PATCH | /bid-winning-notification/{id} | application/json | 按主键**部分更新**定标通知（17 字段，code=200/404） |
| DELETE | /bid-winning-notification/{id} | - | 按主键删除定标通知（code=200/404） |
| GET | /bid-winning-notification | - | 列表查询定标通知（`Result<PageInfo<BidWinningNotification>>`，PageHelper 分页） |
| POST | /bid-winning-notification/batch-delete | application/json | 批量删除定标通知（`Result<List<BidWinningNotification>>`，data=实际删除的实体列表；p3 升级版） |

## Email

### POST /send-email（纯文本）

- Content-Type: `application/x-www-form-urlencoded`
- 参数: `to`（收件人）、`subject`（主题）、`content`（内容）
- 调用链: PlainTextMailController → PlainTextMailSender.sendEmail() → JavaMailSender.send()

### POST /send-notification（中标通知书 HTML）

- Content-Type: `application/json`
- 参数（JSON body）:
  - `to` — 收件人邮箱
  - `subject` — 邮件主题
  - `projectCode` — 项目编号
  - `handlerName` — 经办人
  - `bidWinningList` — 中标结果列表 `[{supplierName, bidWinningAmount}, ...]`
  - `templateName` — 模板文件名（不含 .html 后缀，如 "bid-winning-notification"）
- 调用链:
  1. BidWinningTemplateFiller.processTemplate() — 渲染模板（Thymeleaf）
  2. **立即返回** "发送成功"（主线程不阻塞）
  3. AsyncTaskManager.execute(..., 5000) — 延迟5秒后异步执行
  4. MailLoggingService.formatMailLog() — 打印 MD 格式日志到控制台
  5. BidWinningMailSender.sendHtmlEmail() — 发送 HTML 邮件
- 模板搜索: 精确匹配 `classpath:/templates/{templateName}.html`，不存在则抛 IllegalArgumentException
- 模板变量: `th:text="${projectCode}"`、`th:text="${handlerName}"`、`th:each` 渲染 bidWinningList

## Async Task

- `AsyncTaskManager.execute(Runnable task, long delayMillis)` — 纯 Java 线程实现的延迟异步任务
- 非 Spring Bean，纯静态工具类（每次调用创建新 Thread）
- 使用场景: 邮件延迟5秒后发送（BidWinningMailController）
- 优势: 无需 `@EnableAsync` + Spring AOP 代理，支持延迟执行
- 注意: 高频调用建议改用线程池（当前实现每次 new Thread 有创建开销）
- finally 兜底: try-catch 块后增加 finally 输出任务完成日志（不影响业务，仅状态记录）

## DTO

### PlainTextMailRequest

- 字段: `to`、`from`、`subject`、`content`
- 使用场景: POST /send-email（纯文本邮件）

### BidWinningMailRequest extends PlainTextMailRequest

- 继承字段: `to`、`from`、`subject`
- 自有字段: `projectCode`、`handlerName`、`bidWinningList`、`templateName`
- 内部类: `BidWinningListItem(supplierName, bidWinningAmount)`
- 使用场景: POST /send-notification（中标通知书 HTML 邮件）

### BidHandlerInfoCreateRequest

- 字段: `handlerName` / `companyName` / `departmentName`（3 字段，含 `@NotBlank` / `@Size` 校验）
- 使用场景: POST /create-bid-handler-info（创建经办人）
- 校验: 当前 `createBidHandlerInfo` 未加 `@Valid`，校验注解暂未生效（待 `GlobalExceptionHandler` 一起补全）

### BidHandlerInfoUpdateRequest

- 字段: 与 `BidHandlerInfoCreateRequest` 相同的 3 字段（独立 DTO，未来可加 `version` / `expectedUpdatedAt` 等）
- 使用场景: **PATCH** /bid-handler-info/{id}（按主键 ID **部分更新**）
- 注意: ID 来自 URL `@PathVariable`，**不在 DTO 中**（RESTful 风格）
- 校验: 仅有 `@Size`，**无 `@NotBlank`**（PATCH 语义下 null 是合法值，表示"不修改"）

### BidWinningNotificationCreateRequest

- 字段: 17 字段（3 必填 + 14 可选）
  - 必填: `handlerId`（@NotNull）/ `projectNo`（@NotBlank + @Size(50)）/ `purchaseTaskNo`（@NotBlank + @Size(50)）
  - 可选: platformCategory / biddingProcess / biddingMethod / purchaseMethod（@Size(50)）/ isFixedPrice / creditReportRequired（Boolean）/ targetCost / documentFee / deposit（BigDecimal）/ registrationDeadline / techBidDeadline / businessBidDeadline（LocalDateTime）/ biddingStatus（@Size(20)）/ notes
- 使用场景: POST /create-bid-winning-notification（创建定标通知）
- 校验: 当前 `createBidWinningNotification` 未加 `@Valid`，校验注解暂未生效

### BidWinningNotificationUpdateRequest

- 字段: 17 字段（PATCH 风格，**全部可选**）
- 使用场景: **PATCH** /bid-winning-notification/{id}（按主键 ID 部分更新）
- 注意: 排除 `notificationId`（由 URL `@PathVariable` 传入）/ `createdAt`（DB 维护）
- 校验: 仅有 `@Size`，**无 `@NotBlank`/`@NotNull`**（PATCH 语义下 null=不修改）

### 批量删除 DTO（每端点独立 DTO 模式）

- 命名规范: `<EntityName>BatchDeleteRequest`（实体名 + "BatchDelete" + "Request"）
- 当前 3 个 DTO（结构完全相同，但**类型独立**）:
  - `BidHandlerInfoBatchDeleteRequest` — 批量删除经办人（专用 `/bid-handler-info/batch-delete`）
  - `BidWinningNotificationBatchDeleteRequest` — 批量删除定标通知（专用 `/bid-winning-notification/batch-delete`）
  - `BidWinningSupplierDetailBatchDeleteRequest` — **预留**，等 SupplierDetail Controller 启用
- 字段: `ids`（`List<Integer>`，待删除主键 ID 列表）
- 校验: `@NotEmpty`（ids 不能为空）+ `@Size(max=500)`（单次上限 500 条，防大批量误操作与长 SQL 锁表）
- 响应: 永远 200，`Result<List<Entity>>` data=实际删除的**实体列表**（p3 升级版；多 1 次 SELECT，PK 索引，毫秒级）
- 设计取舍: 不做级联 / 不加事务 / 不做完整性校验（应用层不保证外键关系）
- 排序: DB 物理顺序（无 ORDER BY），最小实现
- **不复用 DTO 的理由**: 避免端点间耦合（改一个端点的 DTO 不应影响其他端点），符合阿里《Java 开发手册》"推荐每 API 入口对应独立 DTO"

### Result<T>（通用响应包装）

- 字段: `code`（业务状态码）/ `message`（文字消息）/ `data`（业务数据载荷）
- 静态工厂:
  - `Result.success(data, message)` — 成功响应（code=200）
  - `Result.error(code, message)` — 业务失败（code 自定义，如 400/404）
- 使用场景: 所有 `BidHandlerInfoController` 和 `BidWinningNotificationController` 端点（统一返回结构）
- 业务码约定: 200=成功, 400=参数错误, 404=资源不存在
- 链路位置: Service（业务结果）→ Controller（包装为 Result）→ Jackson（序列化为 JSON）→ 前端

### Diff<T>（通用 diff 包装）

- 字段: `before`（修改前的实体）/ `after`（修改后的实体）
- 使用场景: PATCH update 端点（`BidHandlerInfoController.patchBidHandlerInfo` / `BidWinningNotificationController.patchBidWinningNotification`）返回 before/after 对比
- 设计意图: 前端可直观看到"修改前 → 修改后"的完整数据
- 泛型优势: 不同表可共用，Jackson 按 `T` 推断具体类型

## Entity（数据库实体）

- 数据库表映射（详见 `docs/database.md`）
- MyBatis-Plus 注解: `@TableName` + `@TableId(type = AUTO)`
- 字段命名：Java 驼峰 ↔ DB 下划线（依赖 Plus 的下划线-驼峰映射）

### BidHandlerInfo

- 对应表: `bid_handler_info`
- 字段: `handlerId` / `handlerName` / `companyName` / `departmentName`
- 完整链路（Mapper + XML + Service + Controller）已实现

### BidWinningNotification

- 对应表: `bid_winning_notification`
- 字段: 19 个字段（详见 `docs/database.md` 2.2 节）
- 完整 7 层链路: ✅ 已实现（DB → Entity → Mapper → XML → Service → DTO → Controller）

### BidWinningSupplierDetail

- 对应表: `bid_winning_supplier_detail`
- 字段: `detailId` / `notificationId` / `supplierName` / `amount`
- Mapper 状态: ✅ 已实现，Service / DTO / Controller 待补

## Mapper（持久化层）

- **位置**: `src/main/java/.../mapper/`
- **设计原则**: 手写接口 + XML 分离（Java 与 SQL 解耦）
- **写** `@Mapper` 注解（显式标注 + 启动类 `@MapperScan` 双重保险）
- **不继承** `BaseMapper<T>`（不用 Plus 自动 CRUD）
- **SQL 独立** 在 `src/main/resources/mapper/*.xml`
- 启动类 `@MapperScan("io.gitee.lujianshi.bidwinningbackend.mapper")` 统一扫描注册

### BidHandlerInfoMapper

- 接口: `mapper/BidHandlerInfoMapper.java`
- XML: `resources/mapper/BidHandlerInfoMapper.xml`
- 方法: `insert` / `updateById` / `deleteById` / `selectById` / `selectAll`
- 对应表: `bid_handler_info`
- 状态: ✅ 完整 7 层 + 4 个 Controller 端点已实现

### BidWinningNotificationMapper

- 接口: `mapper/BidWinningNotificationMapper.java`
- XML: `resources/mapper/BidWinningNotificationMapper.xml`
- 方法: 5 个（同上模式）
- 对应表: `bid_winning_notification`（19 字段）
- 状态: ✅ 完整 7 层 + 5 个 Controller 端点已实现
- 特色: `updateById` 用动态 SQL（`<set> + <if test="xxx != null">`）实现 PATCH 语义

### BidWinningSupplierDetailMapper

- 接口: `mapper/BidWinningSupplierDetailMapper.java`
- XML: `resources/mapper/BidWinningSupplierDetailMapper.xml`
- 方法: 5 个（同上模式）
- 对应表: `bid_winning_supplier_detail`（4 字段，最简实现）
- 状态: ✅ Mapper 已实现，Service / DTO / Controller 待补

## 端到端开发流程

> 完整链路（从数据库到 Controller）详见 [`docs/development-flow.md`](docs/development-flow.md)

**7 层链路（自下而上）**：

1. **DB 脚本**（V1__xxx.sql，Flyway 兼容命名，**当前未启用 Flyway**）
2. **Entity**（@TableName + @TableId）
3. **Mapper 接口**（手写方法签名）
4. **Mapper XML**（namespace 绑定接口，SQL 独立）
5. **Service**（注入 Mapper，**不继承 IService**）
6. **DTO**（@NotBlank 校验）
7. **Controller**（@Valid 触发校验）

## Testing

- `@SpringBootTest` for integration tests
- Default test verifies context loads

## Lombok

- Annotation processor enabled in maven-compiler-plugin
- IDEs need Lombok plugin to resolve compile-time annotations
- 常用注解: `@Data`（getter/setter/equals/hashCode/toString）、`@NoArgsConstructor`、`@AllArgsConstructor`