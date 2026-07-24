# 项目架构（bid-winning-backend）

> 项目内部架构文档。API 契约见 [`docs/api.md`](api.md)；数据库结构见 [`docs/database.md`](database.md)。

## 1. 技术栈

| 类别 | 选型 | 版本 |
|------|------|------|
| 运行时 | Java | 25 |
| Web 框架 | Spring Boot | 4.0.6（`spring-boot-starter-webmvc`，Servlet-based） |
| ORM | MyBatis-Plus | 3.5.15（`mybatis-plus-spring-boot4-starter`） |
| 分页 | PageHelper | 4.1.0（`pagehelper-spring-boot-starter`） |
| 邮件 | Spring Boot Starter Mail | — |
| 模板引擎 | Thymeleaf | 3.1.5 |
| AI | Spring AI + OpenAI/DeepSeek Starter | 2.0.0（运行时使用 OpenAI 兼容配置接 LM Studio，本地模型由 `application-dev.yml` 指定） |
| JSON | fastjson2 | 2.0.52 |
| 文件存储 | Java NIO 本地存储抽象 | 开发阶段使用本地目录，预留 MinIO 实现 |
| 工具 | Lombok | 1.18.46 |
| 开发 | DevTools | — |
| 观测 | Spring Boot Actuator | — |

**环境约束**：dev/test 必须设 `MYSQL_URL/MYSQL_USERNAME/MYSQL_PASSWORD` 三个环境变量；dev 还需要 `MAIL_PASSWORD`，并通过 JVM 系统属性提供 `LM_STUDIO_QWEN3.6-27B`；prod 当前只配置了邮件参数，若直接切 prod profile 还需补齐数据源配置。

---

## 2. 启动流程

```
1. main() → SpringApplication.run()
2. 推断应用类型（Servlet-based，因引入了 spring-boot-starter-webmvc）
3. @EnableAsync 开启异步支持
4. @MapperScan 扫描全局 mapper/ 与 credentialfile/mapper/ 包生成 Mapper 代理
5. 启动嵌入式 Tomcat（dev: 8080 / test: 8080 / prod: 80）
6. 扫描 @Component / @Service / @Controller / @RestController / @Configuration 注册为 Bean
7. 初始化 DispatcherServlet，等待 HTTP 请求
```

应用入口：`BidWinningBackendApplication.java`。详细配置见 `application*.yml`。

---

## 3. 项目结构

```
src/main/java/io/gitee/lujianshi/bidwinningbackend/
├── BidWinningBackendApplication.java       # Spring Boot 启动入口
├── controller/                             # 5 其他业务 Controller + GlobalExceptionHandler
│   ├── BidRecordController.java            # 投标记录（8 端点：6 标准 CRUD + 1 按公告查询 + 1 AI 风险分析触发）
│   ├── BidHandlerInfoController.java       # 招标经办人（7 端点：6 标准 CRUD + 1 批量新增）
│   ├── InvitationForBidController.java     # 招标公告（8 端点：6 标准 CRUD + 1 批量新增 + 1 状态变更）
│   ├── RiskAnalysisController.java         # 风险分析记录只读查询（2 端点）
│   ├── StatusCheckController.java          # 服务状态 + 邮件测试（3 端点）
│   └── GlobalExceptionHandler.java         # 全局异常 → Result 包装
├── credentialfile/                         # 资信文件普通业务模块（同一 Spring Boot 应用内的垂直切片）
│   ├── controller/FileUploadController.java     # 资信原文件上传 + 删除占位端点
│   ├── dto/                                # UploadRequest / UploadResponse
│   ├── entity/                             # CredentialFile / CredentialFileBatch
│   ├── enums/                              # 文件状态、存储类型枚举
│   ├── mapper/                             # CredentialFileMapper / CredentialFileBatchMapper
│   ├── config/                             # 存储配置模型与 MinIO 客户端配置
│   ├── service/                            # FileUploadService / FileConvertService
│   ├── intake/                             # multipart 转换后的请求期内部模型
│   ├── processor/                          # 单文件存储与验证流水线
│   ├── checksum/                           # 文件校验和计算端口与 SHA-256 实现
│   └── storage/                            # 本地文件与 MinIO 存储适配器
├── dto/                                    # 其他业务 DTO，按业务模块分包
│   ├── common/                             # Result / Diff
│   ├── bidhandler/                         # AddRequest / UpdateRequest / BatchAddRequest / DeleteRequest
│   ├── invitationforbid/                   # 同上 + StatusUpdateRequest / BatchDeleteRequest
│   ├── bidrecord/                          # AddRequest / UpdateRequest / BatchDeleteRequest
│   └── statuscheck/                        # PlainTextMailRequest / BidWinningMailRequest
├── entity/                                 # 其他业务实体
├── enums/                                  # 其他业务枚举
├── mapper/                                 # 其他业务 Mapper + BaseMapper
├── service/                                # BaseService + 业务 Service
│   ├── BaseService.java                    # 通用 CRUD + 邮件通知
│   ├── BidRecordService.java               # add 方法含 3 次重试逻辑 + listByInvitationId
│   ├── BidHandlerInfoService.java
│   ├── InvitationForBidService.java
│   ├── mail/MailService.java               # SMTP 发送
│   ├── riskanalysis/                       # AI 风险分析 + 历史查询
│   │   ├── BidRecordLoader.java            # analyzeRisk 时加载上下文
│   │   └── RiskAnalysisService.java        # OpenAiChatModel + ChatClient.defaultOptions（Structured Output），content/reasoningContent 双路径抽取
│   └── statuscheck/StatusCheckService.java # 中标通知书邮件（异步延迟）
└── utils/                                  # AsyncTaskManager / PromptFiller / ResourceSearcher
```

### 3.1 `credentialfile` 普通业务模块边界

`credentialfile/` 是当前 Spring Boot 应用内的垂直业务模块，不是独立 Maven 子模块，也不是可单独启动的服务。它将资信文件端点所需的 Controller、DTO、实体、枚举、Mapper、批次服务、文件接入模型、处理器、校验和算法及存储适配器收拢在同一个业务根包下。

模块通过 Spring Boot 根包扫描自动装配；Mapper 扫描同时覆盖全局 `mapper/` 和模块内 `credentialfile/mapper/`。模块对外暴露 HTTP Controller，内部通过 `FileStorage` 等接口隔离本地文件系统与 MinIO 实现。`Result`、`BaseMapper` 和 `GlobalExceptionHandler` 属于应用级公共能力，不复制到模块内部。

---

## 4. 7 层开发链路

```
① DB 脚本（src/main/resources/sql/ddl/V*.sql，Flyway 兼容命名，当前未启用 Flyway）
  ↓
② Entity（@TableName + @TableId，主键 @TableId(type = IdType.AUTO)）
  ↓
③ Mapper 接口（@Mapper 注解，extends BaseMapper<T>）
  ↓
④ Mapper XML（namespace = Mapper 接口 FQN，SQL 独立于 Java）
  ↓
⑤ Service(业务 CRUD 型 extends BaseService<T>,子类实现 getMapper() + getIdExtractor();业务流型如 RiskAnalysisService / StatusCheckService 不继承,直接 @Service + 注入依赖)
  ↓
⑥ DTO（@NotBlank / @NotNull / @Size 校验；PATCH 风格 UpdateRequest 仅 @Size）
  ↓
⑦ Controller（@RestController + 路由注解；@Valid 触发校验；统一返 Result<T>）
```

**关键约定**：

| 维度 | 约定 |
|------|------|
| Mapper 接口 | 继承 `BaseMapper<T>`（7 标准方法），手写 XML |
| Service | 业务 CRUD 型继承 `BaseService<T>`,写方法统一 `@Transactional(rollbackFor = Exception.class)`;业务流型不继承,自行管理事务 |
| DTO 命名 | `AddRequest` / `UpdateRequest`（PATCH 风格，仅 @Size）/ `BatchDeleteRequest` / `BatchAddRequest`（含 @Valid 内嵌）/ `StatusUpdateRequest` / `DeleteRequest` |
| Controller 响应 | `Result<T>`；PATCH → `Result<Diff<T>>`；批量删除 → `Result<List<Entity>>` |
| 业务状态码 | 200 成功 / 400 参数错误 / 404 资源不存在 / 500 服务器错误（GlobalExceptionHandler 统一处理） |
| 注入方式 | `jakarta.annotation.Resource`（field 注入） |
| 外键 | 逻辑外键（DB 层不强制 FK 约束） |
| JSON 序列化 | fastjson2（项目无 Jackson） |
| Bean 名冲突 | `JavaMailSender` Bean 名 `mailSender`，自建类不可叫 `MailSender` |

**复用样板**（新增业务表的最小集）：复制 BidHandlerInfo 7 层结构 → 改名 → 改 SQL/字段即可。

---

## 5. 关键类职责

### 5.1 `BaseMapper<T>`（Mapper 基接口）

定义 7 个标准方法：`insert` / `updateById` / `deleteById` / `selectById` / `selectAll` / `deleteByIds` / `selectByIds`。具体 Mapper 继承后绑定对应 XML。

### 5.2 `BaseService<T>`（Service 基类）

封装 7 个标准 CRUD 方法，写方法统一 `@Transactional(rollbackFor = Exception.class)`。子类实现：
- `getMapper()`：返回绑定的 Mapper 实例
- `getIdExtractor()`：返回 `Function<T, Integer>` 用于主键提取（批量删除用）

提供邮件通知方法：
- `sendEmail(T entity, String targetEmail, String endpoint, String operation)`：4-param 版
- `sendEmail(T entity, String endpoint, String operation)`：2-param 重载，反射从 entity 读 targetEmail
- `sendBatchEmail(List<T> entities, String outerTargetEmail, String endpoint, String operation)`：批量版

### 5.3 `Result<T>`（统一响应包装）

`code` / `message` / `data` 三字段。静态工厂：
- `Result.success(T data, String message)`：成功
- `Result.error(Integer code, String message)`：业务失败

### 5.4 `Diff<T>`（PATCH diff 包装）

`before`（DB 原值）+ `after`（更新后值），泛型适配任意实体。

### 5.5 `GlobalExceptionHandler`（全局异常处理）

`@RestControllerAdvice` + `@ExceptionHandler`：
- `MethodArgumentNotValidException`（@Valid 校验失败）→ 400 + 第一个错误消息
- `MaxUploadSizeExceededException`（单文件或 multipart 请求总体超限）→ 400 + "文件大小超过限制"
- `IllegalArgumentException` → 400 + 异常消息
- 其他 `Exception` → 500 + "服务器内部错误"（不泄漏堆栈）

### 5.6 `AsyncTaskManager`（异步任务）

```java
public static void execute(Runnable task, long delayMillis)
```

`new Thread + .start()` 纯 Java 线程，非 Spring Bean。支持延迟执行。生产环境高频调用建议改用线程池。

### 5.7 `PromptFiller`（AI prompt 模板填充）

- `loadFromResource(String path)`：加载静态模板（无占位符）
- `fillFromResource(String path, Map<String, String> vars)`：加载并替换 `{{key}}` 占位符

### 5.8 `RiskAnalysisService`（AI 风险分析）

构造器注入 `OpenAiChatModel`，构造器里用 `ChatClient.builder(model).defaultOptions(opts).build()` 装载 Structured Output JSON Schema 后缓存为 `final` 字段。`analyzeRisk(Integer bidRecordId)` 同步调 OpenAI 兼容 LM Studio、抽取 `content`（为空时降级读取 `metadata["reasoningContent"]`）、JSON 解析、通过 Spring 代理调用 `persist` 落库。`RiskAnalysisController` 只读暴露 `listAll` 与 `getHistory`，不提供修改/删除入口。

### 5.9 `BidRecordService`（重试逻辑）

`add(entity)` 必须在事务内做 3 次重试，每次重试前先查 `(invitation_id, bidder_name)` 下的 `MAX(bid_no)` 计算 `bidNo = max + 1`（null 时为 1），再 INSERT。捕获 `DuplicateKeyException`（并发同号）后重试。3 次后仍冲突抛 `IllegalStateException`。

### 5.10 `FileUploadService`（批次编排）

资信文件 Controller 将 multipart 参数转换为 `credentialfile.intake.UploadBatch`；`credentialfile.service.FileUploadService` 为本次请求生成
新的批次 UUID，先创建 `credential_file_batch` 的 `UPLOADING` 记录，再按请求顺序把每个
`UploadItem` 交给 `UploadItemProcessor`。全部文件成功后批次更新为 `UPLOADED`
并封口。本服务只负责批次编排、批次记录状态和批次级补偿，不直接读取 `MultipartFile`、
计算校验和或调用 MinIO SDK。

单个条目失败时，处理器删除当前文件；后续条目失败时，`FileUploadService` 逆序删除本次请求中
此前已经验证成功的文件。当前一个请求就是一个不可变批次，不再支持同一批次追加上传；后续
重新提交资信材料时创建新的 `upload_batch_id`。

每个处理成功的文件立即以本次请求内的 `fileOrder` 写入 `credential_file`；任一文件失败时，
数据库事务回滚批次及文件记录，服务同时补偿删除本次请求已写入的存储对象。

### 5.11 `intake` 与 `UploadItemProcessor`

`intake` 只描述请求生命周期内的后端输入，不负责持久化。`UploadItem.Content.openStream()` 每次调用
必须返回一条新的、可独立关闭的输入流，使单文件处理器能够先计算上传前 SHA-256，再重新打开
同一 multipart 内容执行存储，而不把整个文件加载到 Java 堆。

`UploadItemProcessor` 完成单文件状态转换：

```text
UploadItem
  → 上传前计算 SHA-256
  → 生成 original 逻辑路径与业务 metadata
  → FileStorage.store
  → FileStorage.open
  → 存储后重新计算 SHA-256 并比较
  → VerifiedStoredFileMetadata
```

其中 `StoredFile` 是存储层返回的纯存储结果；`VerifiedStoredFileMetadata` 是处理器完成正文回读校验后
输出的文件元数据，可以安全地写入 `credential_file`。校验失败时，处理器负责删除刚写入的当前
文件，并保留原始异常；这项业务补偿不下沉到本地或 MinIO 存储实现。

### 5.12 `FileStorage`（存储端口）

定义 `store` / `open` / `delete` 三项通用能力。`store` 接收由调用方准备的逻辑路径、
MIME 类型、长度、metadata 和内容流，不解释文件是原始文件还是转换文件。单文件处理器和
后续转换流程都只依赖该接口，
运行时通过 `credential-file.storage.type` 只激活 `LocalFileStorage` 或 `MinioFileStorage`
中的一个实现。

当前原始上传流程由 `UploadItemProcessor` 调用 `StoragePath` 生成以下逻辑目录结构：

```text
{uploadBatchId}/
└── original/
    └── {fileUuid}_{安全原文件名}
```

处理器构造 `bid-record-id`、`upload-batch-id` 和 `sha256` metadata；存储层不理解这些
字段的业务含义，只负责透传并确认存储端回读值与写入请求一致。
本地实现将逻辑路径解释为存储根目录下的相对路径，并使用 `CREATE_NEW` 防止覆盖；MinIO
将其解释为 object key，并在同一次 `putObject` 中透传调用方自定义的文件 metadata。
这里的“调用方”指 Java 后端，而不是投标用户。未来保存 converted
文件时可以生成另一条逻辑路径并复用同一个 `store`，不需要扩展存储接口。接口、响应和
后续数据库都不保存环境相关的本机绝对路径。

### 5.13 `FileChecksumCalculator`（校验和端口）

以 `InputStream` 为输入，不依赖 multipart、本地 `Path` 或具体存储技术。
`Sha256FileChecksumCalculator` 使用 JDK `MessageDigest` 和 `HexFormat` 计算 64 位小写
十六进制校验和。该模块只负责计算，不比较结果，也不判断文件是否完整。

### 5.14 资信文件上传与完整性校验调用链

```text
POST /credential-files/upload (multipart/form-data)
  ↓
FileUploadController
  └─ multipart → UploadBatch + List<UploadItem>
  ↓
FileUploadService
  ├─ 生成/校验 UploadBatchContext
  └─ 按顺序遍历 UploadItem
       ↓
     UploadItemProcessor
       ├─ FileChecksumCalculator → 上传前 SHA-256
       ├─ StoragePath + 调用方 metadata
       ├─ FileStorage.store
       │    ├─ Local Files.copy
       │    └─ MinIO putObject + statObject
       ├─ FileStorage.open / MinIO getObject(matchETag)
       ├─ FileChecksumCalculator → 存储后 SHA-256
       ├─ 比较存前与存后校验和；不一致时删除当前文件
       └─ VerifiedStoredFileMetadata
       ↓
UploadResponse
       ↓
Result.success(data, "上传成功")
```

**当前边界**：上传链已真实落盘、重新读取正文完成 SHA-256 比对，并写入
`credential_file`；批次全部成功后更新为 `UPLOADED`。删除端点仍为占位，Python Markdown
转换由独立转换入口触发。

---

## 6. 异常处理链路

```
Controller 业务方法
  ↓ 业务异常
抛 IllegalArgumentException
  ↓
@RestControllerAdvice GlobalExceptionHandler.handleIllegalArgument
  ↓
返 Result.error(400, e.getMessage())
  ↓
前端收到 400 业务码

@Valid 校验失败
  ↓
Spring 抛 MethodArgumentNotValidException
  ↓
GlobalExceptionHandler.handleValidation
  ↓
返 Result.error(400, 第一个校验错误消息)

multipart 文件超过 150MB 或请求总体超过 200MB
  ↓ Controller 执行前即解析失败
Spring 抛 MaxUploadSizeExceededException
  ↓
GlobalExceptionHandler.handleMaxUploadSizeExceeded
  ↓
返 Result.error(400, "文件大小超过限制")

未捕获异常
  ↓
GlobalExceptionHandler.handleUnknown
  ↓
返 Result.error(500, "服务器内部错误")
```

---

## 7. 异步任务（AsyncTaskManager）

### 7.1 使用场景

| 场景 | 调用方 | 延迟 |
|------|--------|------|
| 中标通知书 HTML 邮件 | `StatusCheckService.sendNotification` | 5000ms（让前端展示"发送中"） |
| CRUD 结果通知邮件 | `BaseService.sendEmail` 内部 | 0ms（CRUD 后立即异步发） |
| AI 风险分析（未来） | — | — |

### 7.2 时序

```
主线程:
  ① Controller 业务执行 → service.add(entity) 落库
  ② service.sendEmail(entity) → 反射读 targetEmail + 构造 Thymeleaf Context
  ③ AsyncTaskManager.execute(runnable, 0)
       ├─ new Thread(runnable).start()  ← 构造 + 启动，瞬间完成
       └─ 主线程继续
  ④ 包装 Result.success() 并 return
  ⑤ HTTP 响应完成（前端收到 200）

子线程（与主线程并行）:
  ⑥ ResourceSearcher.validateTemplateExists
  ⑦ SpringTemplateEngine.process 渲染 HTML
  ⑧ MailService.sendHtmlEmail SMTP 发送
  ⑨ try-catch 兜底，失败仅日志告警，不影响主业务
```

**关键点**：
- 主线程不阻塞（`new Thread().start()` 内部构造+启动约微秒级）
- 子线程异常隔离（try-catch 兜底）
- targetEmail 为 null/空时主线程直接 return，不提交异步任务

---

## 8. 邮件通知（Service 层 BaseService.sendEmail）

> **历史方案（AOP 切面）已废弃**。当前由 Service 层显式调用，统一由 `BaseService.sendEmail` 编排。

### 8.1 触发流程

```
Controller 业务方法末尾:
  service.add(entity);
  service.sendOperationEmail(entity);   ← 1
  return Result.success(entity, "创建成功");

  1. 子 Service.sendOperationEmail
     → this.sendEmail(entity, "xxx/create", "新增")
     → BaseService.sendEmail 反射 + 异步
```

### 8.2 接入新端点（3 步）

**Step 1**：DTO 加 `targetEmail` 字段（必填）
```java
@NotBlank private String targetEmail;
```

**Step 2**：子 Service 加便利方法
```java
public void sendOperationEmail(Xxx entity) {
    this.sendEmail(entity, "xxx/create", "新增");
}
```

**Step 3**：Controller 业务方法末尾显式调用
```java
service.add(entity);
service.sendOperationEmail(entity);
```

### 8.3 当前已接入端点

| Controller | 端点 | 触发方法 |
|---|---|---|
| `BidHandlerInfoController` | `POST /bid-handler-info/add` | `service.sendOperationEmail(entity)` |
| `BidHandlerInfoController` | `POST /bid-handler-info/batch-add` | `service.sendBatchAddEmail(created, request.getTargetEmail())` |
| 其他 26 个端点 | — | 未发送 |

### 8.4 邮件模板

`templates/crud-email-template.html`，变量：

| 变量 | 来源 |
|------|------|
| `subject` | `BaseService.sendEmail` 内部构造 |
| `formName` | `endpoint` 参数 |
| `actionName` | `operation` 参数 |
| `operateTime` | `LocalDateTime.now()` |
| `status` | `operation + " 成功"` |
| `data` | `PropertyDescriptor` 反射遍历 entity 字段（排除 targetEmail） |

### 8.5 收件人解析

`targetEmail` 来自 `entity.targetEmail`（已落库），由 `BaseService.sendEmail` 反射读取。`null`/空字符串时跳过发邮件。

---

## 9. 关键设计决策（防坑指南）

| 决策 | 理由 |
|------|------|
| **Mapper XML 而非注解 SQL** | 坚持 Java/SQL 分离，便于 DBA 维护 |
| **业务 Service 分类** | 业务 CRUD 型(3 个:BidHandlerInfo/InvitationForBid/BidRecord)继承 `BaseService` 消除重复;业务流型(2 个:RiskAnalysis/StatusCheck)不继承,因业务逻辑独立,避免被通用 CRUD API 污染(同 `StatusCheckService` 先例) |
| **DTO 每端点独立** | 避免端点间耦合，符合阿里《Java 开发手册》 |
| **PATCH 语义** | 调用方传哪些字段就更新哪些字段，null = 不修改 |
| **批量删除 data 返实体列表** | 前端 diff 友好，多 1 次 SELECT（PK 索引，毫秒级）|
| **不级联删除** | 应用层不保证外键关系，明细行可能变孤儿（业务层负责）|
| **PageHelper.startPage + finally clearPage** | ThreadLocal 写入分页参数，避免线程池污染 |
| **Page<Object> 不用 try-with-resources** | PageHelper 的 Page 不实现 AutoCloseable，用 `//noinspection try` |
| **fastjson2 而非 Jackson** | 项目已引入复用，避免新增依赖 |
| **MailService 而非 MailSender** | 避免与 Spring Boot 自动配置的 `JavaMailSender`（Bean 名 mailSender）同名冲突 |
| **AI OpenAiChatModel 注入 + ChatClient.defaultOptions** | 符合 Spring AI 2.0 官方推荐；注入 `OpenAiChatModel`，在 `ChatClient.builder(...).defaultOptions(opts).build()` 中装载 Structured Output JSON Schema 约束，缓存为 final 字段 |
| **Hybrid Reasoning 模型 fallback** | `extractRawContent` 优先读 `AssistantMessage.getText()`，为空时降级 `metadata["reasoningContent"]`（Spring AI 2.0 `OpenAiChatModel.internalCall` 内部已把 LM Studio 的 `reasoning_content` 透传至此，key 为 camelCase）；适用 huihui-qwen 系 Hybrid Reasoning 模型把 JSON 输出塞 reasoning 字段的场景。详见 [`docs/structured-output.md` §9.1](structured-output.md#91-hybrid-reasoning-模型-fallback已实施) |
| **bid_record 投标次数服务端生成** | bidNo = max(bid_no)+1，3 次重试防并发同号，UNIQUE 兜底 |
| **不暴露 bid_no 给客户端** | Client 只传 invitationId / bidderName / quotePrice / bidderContact，服务端算 bidNo |
| **请求接入与持久化分离** | `intake` 只承载请求期内部模型，`storage` 才负责本地落盘或 MinIO 对象上传 |
| **批次与单文件流程分离** | `FileUploadService` 处理批次顺序和历史文件补偿，`UploadItemProcessor` 处理当前文件完整流水线 |
| **文件存储使用端口 + 适配器** | 单文件处理器依赖 `FileStorage`，通过配置在本地 NIO 和 MinIO SDK 实现之间切换 |
| **校验和算法独立于存储** | `FileChecksumCalculator` 只消费输入流，SHA-256 使用 JDK 标准库封装，可复用于原文件、Markdown 与一致性校验 |
| **状态对象明确生命周期** | `UploadItem` 是请求期输入，`StoredFile` 是纯存储结果，`VerifiedStoredFileMetadata` 是完成正文回读校验后可落库的文件元数据 |
| **文件路径保存相对路径** | 不把 `/Users/...` 等环境根目录写入 API/数据库，切换 Linux 或 MinIO 时无需迁移历史路径 |

---

## 10. 新增业务表的复用模式

以新增 `Xxx` 表为例：

1. **DB**：`sql/ddl/V{N}__create_xxx.sql`
2. **Entity**：`entity/Xxx.java`（@TableName + @TableId + Lombok 三件套）
3. **Mapper 接口**：`mapper/XxxMapper.java extends BaseMapper<Xxx>`（@Mapper 注解）
4. **Mapper XML**：`resources/mapper/XxxMapper.xml`（namespace = FQN，7 个标准 SQL 标签 + 可选 `<selectByXxxId>`）
5. **Service**:`service/XxxService.java` — 业务 CRUD 型 `extends BaseService<Xxx>`(getMapper + getIdExtractor 两个方法);业务流型不继承,直接 `@Service` + 注入依赖
6. **DTO**：`dto/xxx/{AddRequest, UpdateRequest, BatchDeleteRequest}.java`
7. **Controller**：`controller/XxxController.java`（6 标准 CRUD 端点：add / getById / patch / delete / list / batch-delete；可选 batch-add / status / AI 子端点）
8. **AI 风险分析（如需要）**：`service/riskanalysis/XxxRiskAnalysisService.java`（参考 RiskAnalysisService）

---

## 11. 启动命令速查

```bash
# dev（默认）
export MYSQL_URL='jdbc:mysql://...:3306/bid-winning?...' \
       MYSQL_USERNAME=jianshi \
       MYSQL_PASSWORD=xxx \
       MAIL_PASSWORD=xxx
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-DLM_STUDIO_QWEN3.6-27B=lm-studio"

# test
./mvnw spring-boot:run -Dspring-boot.run.profiles=test

# prod
export SPRING_PROFILES_ACTIVE=prod
export MYSQL_URL=... MYSQL_USERNAME=... MYSQL_PASSWORD=...
export PROD_MAIL_USERNAME=... PROD_MAIL_PASSWORD=...
./mvnw spring-boot:run
```

详细配置见 AGENTS.md "环境变量配置" 章节。

---

## 12. 关联文档

- AI Structured Output 链路说明:[`docs/structured-output.md`](structured-output.md) — Spring AI + LM Studio + 本地模型 Structured Output 端到端链路、GBNF grammar 原理、调优手段
- 完整架构图(6 tab, C4 L1~L3 + 分层 + 时序 + ER):[`docs/architecture-diagram.drawio`](architecture-diagram.drawio) — 用 draw.io 打开,6 tab 全景
