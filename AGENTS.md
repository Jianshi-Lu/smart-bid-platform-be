# AGENTS.md

> `smart-bid-platform-be` 的 Agent 工作上下文与工程约定。
> 本文件按当前工作区源码整理；具体字段和端点实现以源码为准，较完整的业务说明见 `docs/`。
> 不要把本文件中的配置名称理解为凭据；禁止在代码、文档或提交中写入密码、API Key、Token、个人隐私或真实内部凭据。

## 1. 项目目标与边界

这是一个采购/招投标平台的 Java 单体后端，当前已覆盖：

- 招标经办人、招标公告、投标记录的关系型 CRUD；
- 投标记录的多批次资信文件上传、原始文件持久化、SHA-256 校验和、Markdown 转换及汇总；
- 基于投标记录基础信息和资信材料的同步风险分析，并保存历史分析结果；
- 邮件测试和中标通知书发送；
- `knowledgeingestionagent` 的 Java 到 Python 调用契约和 Controller 展位。

本仓库只实现 Java 服务。前端、Python 转换/知识采集服务、MySQL、MinIO 或本地文件系统、OpenAI 兼容大模型服务和 SMTP 都是外部运行依赖。

当前明确的边界：

- 没有鉴权、租户隔离和权限模型；
- 业务表之间使用逻辑外键，数据库没有 `FOREIGN KEY`、级联删除或父记录存在性保障；
- 文件正文不进入 MySQL，数据库只保存元数据、状态和逻辑存储路径；
- `bid_record.credentials` 是已转换 Markdown 路径的 JSON 投影，不是资信文件事实源；文件事实源是 `credential_file`；
- DDL 使用 Flyway 风格 `V*.sql` 命名，但项目没有启用 Flyway，脚本需要按环境手工执行；
- `knowledgeingestionagent` 当前只搭好 Java 客户端/DTO/Controller 框架，Python 任务服务、定时调度、政策数据库、向量库和前端页面尚未在本仓库实现。

## 2. 构建、启动与配置

### 2.1 命令

项目根目录已经包含 Maven Wrapper，优先使用它，不依赖系统 Maven：

```bash
./mvnw spring-boot:run
./mvnw test
./mvnw clean package
```

Spring Boot 启动类是：

```text
src/main/java/io/gitee/lujianshi/bidwinningbackend/BidWinningBackendApplication.java
```

启动类启用 `@SpringBootApplication`、`@ConfigurationPropertiesScan`、`@EnableAsync`、`@EnableAspectJAutoProxy` 和全局 Mapper 扫描。默认 profile 在 `src/main/resources/application.yml` 中为 `dev`，开发端口为 `8080`。

### 2.2 技术栈

- Java 25；
- Spring Boot 4.0.6、Spring WebMVC Servlet 模型；
- Jakarta Validation；
- MyBatis-Plus 3.5.15 + 手写 MyBatis XML；
- PageHelper 4.1.0，分页使用 ThreadLocal 模式；
- MySQL；
- Fastjson2：用于 MyBatis 的 JSON TypeHandler 和风险分析响应解析；HTTP JSON converter 以当前 Spring Web 依赖实际装配为准；
- Spring AI 2.0.0，OpenAI 兼容模型调用；
- MinIO Java SDK 9.0.3 + OkHttp JVM 实现；
- Spring Mail + Thymeleaf：邮件及 HTML 通知；
- Lombok、AspectJ、DevTools、Actuator。

### 2.3 配置文件与配置类

| 文件/前缀 | 作用 |
|---|---|
| `application.yml` | 应用名、profile、multipart 大小限制、Thymeleaf 基础配置 |
| `application-dev.yml` | 开发端口、MySQL、模型、邮件、日志、文件存储和 Python 地址 |
| `application-test.yml` | 测试环境端口、数据库、邮件、日志 |
| `application-prod.yml` | 生产端口、邮件和日志；数据库、模型、文件存储等生产配置当前未完整呈现，部署前必须确认 |
| `credential-file.converter` | `FileConverterProperties`，绑定 Python `to-markdown` 基础地址 |
| `credential-file.storage` | `FileStorageProperties`，选择 `LOCAL`/`MINIO` 及对应参数 |
| `risk-analysis` | `RiskAnalysisProperties`，当前用于汇总 Markdown 字符上限，开发默认约 128K 字符 |
| `knowledge-ingestion-agent` | `KnowledgeIngestionAgentProperties`，绑定 Python 知识采集服务地址 |

开发环境通过环境变量提供外部配置，例如 `MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`、模型 API Key、邮件密码、Python 服务地址、MinIO 地址/密钥/bucket 等。实际值不应写入仓库。

文件存储默认可选本地目录；切换 MinIO 时设置 `CREDENTIAL_FILE_STORAGE_TYPE=MINIO` 或在对应 profile 中配置 `credential-file.storage.type`，同时提供 MinIO endpoint、access key、secret key 和 bucket。`MinioConfiguration` 只在类型为 `MINIO` 时创建单例 `MinioClient`，不会在 Bean 创建阶段主动检查 Bucket。

### 2.4 数据库脚本

当前 DDL 位于 `src/main/resources/sql/ddl/`，版本为 V1-V24。V18、V20 等脚本包含重命名或 `TRUNCATE` 等强操作；修改数据库时必须先确认目标数据库和脚本执行历史，不能把所有脚本无条件重新执行。

## 3. 源码组织与职责

```text
src/main/java/io/gitee/lujianshi/bidwinningbackend/
├── BidWinningBackendApplication.java
├── controller/                         # 通用 Controller、风险分析、全局异常出口
├── credentialfile/                     # 资信文件垂直模块
│   ├── checksum/                       # SHA-256 算法抽象与实现
│   ├── client/                         # Python HTTP 客户端及协议 DTO
│   ├── config/                         # 转换、存储、MinIO 配置
│   ├── controller/                     # 上传、无差别转换入口
│   ├── dto/                            # 上传/转换响应与请求
│   ├── entity/                         # credential_file/batch 实体
│   ├── enums/                          # ConvertStatus、存储类型、汇总状态
│   ├── intake/                         # multipart 到内部 UploadBatch/UploadItem
│   ├── mapper/                         # 文件和批次 Mapper
│   ├── processor/                      # 单文件存储与完整性验证编排
│   ├── service/                        # 上传、转换、Markdown 汇总
│   └── storage/                        # FileStorage、Local、MinIO 适配器
├── dto/                                # 通用业务请求/响应 DTO
├── entity/                             # 核心数据库实体
├── enums/                              # 招标公告状态
├── exception/                          # AppException、AppErrorCode
├── knowledgeingestionagent/            # 政策/资讯采集 Python 对接展位
├── mapper/                             # 核心 Mapper 抽象与接口
├── service/                            # CRUD、邮件、风险分析、状态检查
└── utils/                              # Prompt、模板资源、异步任务工具

src/main/resources/
├── application*.yml                    # 环境配置
├── mapper/*.xml                        # MyBatis SQL 与 resultMap
├── prompts/*.txt                       # 风险分析 Prompt
├── sql/ddl/*.sql                       # 手工数据库脚本
└── templates/*.html                    # Thymeleaf 邮件模板
```

依赖方向应保持为：

```text
HTTP/Controller → DTO → Service → Entity/业务模型
                              ↓
                 Mapper/XML、FileStorage、外部 Client、Spring AI
                              ↓
                     MySQL、文件存储、Python、模型、SMTP
```

基础设施层不能反向调用 Controller。当前有两个已知的架构例外/待改进点：`RiskAnalysisService` 直接依赖 Spring AI `ChatClient`，`BaseService` 同时承载 CRUD 和邮件辅助能力。

## 4. 核心实体、表和关系

### 4.1 业务表

| 表 | Java 实体 | 主要含义 |
|---|---|---|
| `bid_handler_info` | `BidHandlerInfo` | 招标方经办人 |
| `invitation_for_bid` | `InvitationForBid` | 招标公告/Invitation for Bids |
| `invitation_for_bid_status_history` | `InvitationForBidStatusHistory` | 公告状态变更历史 |
| `bid_record` | `BidRecord` | 某公告下某投标人的一次投标记录 |
| `credential_file_batch` | `CredentialFileBatch` | 一个投标记录的一套资信材料上传批次 |
| `credential_file` | `CredentialFile` | 一个批次中的一个原始文件及转换元数据 |
| `risk_analysis` | `RiskAnalysis` | 一次风险分析历史结果 |

逻辑关系：

```text
bid_handler_info 1 ── * invitation_for_bid
invitation_for_bid 1 ── * bid_record
bid_record 1 ── * credential_file_batch 1 ── * credential_file
bid_record 1 ── * risk_analysis
```

`credential_file_batch.bid_record_id`、`credential_file.bid_record_id`、`credential_file.upload_batch_id` 都是应用层关系。`credential_file` 冗余保存 `bid_record_id`，方便按投标记录查询，但数据库不会自动保证它与所属批次一致。

### 4.2 `bid_record` 的特殊字段

- `bid_no`：同一 `invitation_id + bidder_name` 下从 1 开始递增；数据库唯一键 `uk_attempt (invitation_id, bidder_name, bid_no)` 防止并发同号；
- `credentials`：MySQL `JSON`，Java 映射为 `List<String>`，保存已转换 Markdown object key 列表；由转换成功后整体重算，不是原始文件内容；
- `risk_analysis_status`：`IDLE / ANALYZING / COMPLETED / FAILED`；
- `risk_analysis_started_at`、`risk_analysis_error`：最近一次风险分析的时间和失败原因。

### 4.3 资信文件字段约束

`credential_file` 保存原始文件名、顺序、MIME 类型、大小、SHA-256、存储类型、`original_file_path`、`markdown_file_path`、状态和时间戳。`credential_file_batch.file_count` 是上传时声明的文件数，`converted_file_count` 是批次已完成转换数。

## 5. 状态和不可破坏的约束

### 5.1 招标公告

`BidNoticeStatus` 状态机：

```text
DRAFT → PUBLISHED → CLOSED → AWARDED
  └──────── 任意非 CANCELLED 状态可转 CANCELLED
```

不能自转、`CANCELLED` 是终态。状态更新使用 `UPDATE ... WHERE status = oldStatus`，影响行数不是 1 时返回并发冲突；成功后在同一事务写状态历史。

### 5.2 资信批次和文件

批次、文件共用 `ConvertStatus`：

```text
UPLOADING → UPLOADED → CONVERTING → CONVERTED
                              └──────→ FAILED
```

- 上传时批次先为 `UPLOADING`，所有文件完成存储、回读校验和元数据插入后才封口为 `UPLOADED`；
- 转换接口是无差别重处理入口，不以 `UPLOADED` 作为唯一筛选条件；`UPLOADED`、`FAILED`、`CONVERTED` 可以重新抢占，`CONVERTING` 不会被第二个请求再次抢占；
- 文件和批次的状态抢占通过 Mapper 的条件更新完成，返回行数是是否拿到处理权的依据；
- Python 只负责文件流到 Markdown 的内容转换；Java 负责 original/converted 存取、
  完整性校验、批次/文件循环、数据库状态和批次最终收敛；
- 转换成功后保存 `markdown_file_path`，失败信息进入 `error_message`；单文件失败通常被转换服务收敛到失败状态和汇总响应，不一定抛到全局异常处理器；
- `BidRecordCredentialConversionStatus` 是投标记录级汇总状态：`ALL_CONVERTED / PROCESSING / PARTIAL_FAILED`；
- 汇总和风险分析不能依赖批次响应快照，必须重新查询数据库确认所有批次、所有文件为 `CONVERTED` 且 Markdown 路径非空。

### 5.3 风险分析

风险分析通过 `claimRiskAnalysis` 条件更新抢占同一投标记录的分析权：只有 `IDLE`、`COMPLETED`、`FAILED` 可以进入 `ANALYZING`。成功落库后进入 `COMPLETED`；运行时失败会尽力写回 `FAILED`，允许后续重试。

当前已知限制：Java 进程若在写入 `ANALYZING` 后直接宕机，当前没有超时恢复机制，记录可能长期停留在 `ANALYZING`；风险分析和手动转换也没有形成投标记录级互斥锁，后续修改必须注意二者并发读取/覆盖文件的问题。

## 6. 关键业务流程

### 6.1 基础 CRUD

经办人、招标公告、投标记录使用：

```text
Controller → DTO 校验 → Service → Mapper 接口 → Mapper XML → MySQL
```

`BaseService<T>` 提供通用新增、更新、按 ID 查询、删除和批量删除；子 Service 实现 Mapper 和 ID 提取器。新增投标记录由 `BidRecordService` 自动生成 `bid_no`/`bid_at`，遇唯一键冲突最多重试 3 次。PATCH 使用非空字段动态更新；批量删除是物理删除，不做级联。

### 6.2 原始资信文件上传

入口：`POST /credential-files/upload`，`multipart/form-data`，字段为 `bidRecordId` 和重复的 `files`。

```text
MultipartFile
  ↓ FileUploadController.toUploadItems()
UploadItem → UploadBatch
  ↓ FileUploadService
创建 credential_file_batch(UPLOADING)
  ↓ 每个 UploadItem
计算请求流 SHA-256
  → StoragePath 生成 original 逻辑路径
  → FileStorage.store()
  → 从存储重新 open()，再次计算 SHA-256 并比对
  → 插入 credential_file(UPLOADED)
  ↓ 全部成功
批次 UPLOADING → UPLOADED
  ↓
返回 uploadBatchId 和文件元数据
```

路径由 `StoragePath` 统一生成，当前形态为：

```text
bid-records/{bidRecordId}/batches/{uploadBatchId}/original/{randomUUID}_{safeFilename}
```

`LocalFileStorage` 将它解释为存储根目录下的相对路径；`MinioFileStorage` 将它解释为 object key。路径会拒绝绝对路径、`.`、`..` 和空片段，原始文件名会去除客户端路径片段和控制字符。原始文件名使用 UUID 前缀，避免同名文件覆盖。

上传 Service 会在事务失败时尽力删除本次已写入的对象；对象存储与数据库不是同一事务，删除失败只作为 suppressed exception 保留。当前上传入口只校验 `bidRecordId` 格式，不主动查询投标记录是否存在；由于没有硬外键，错误 ID 可能形成孤儿批次/文件，属于已知限制。

### 6.3 原始文件转 Markdown

入口：`POST /credential-files/bid-records/{bidRecordId}/convert`。

```text
FileConvertController
  → FileConvertService.convertAllBatches(bidRecordId)
  → 查询该投标记录全部批次（batch_no ASC）
  → Java 条件抢占批次 CONVERTING
  → 查询批次文件（file_order ASC, file_id ASC）
  → Java 条件抢占单文件 CONVERTING
  → FileStorage.open(original_file_path)
  → 读取过程中重新计算并校验 original SHA-256
  → ToMarkdownClient multipart POST Python /convert
       file=<original 文件流>
  → 校验 Python 返回非空 text/markdown 文件
  → Java 生成同批次 converted Markdown 路径
  → FileStorage.store() 上传 Markdown 并校验属性/metadata
  → 重新 open converted，校验 Markdown SHA-256
  → 文件标记 CONVERTED 并保存 markdown_file_path
  → 重新聚合已完成路径，回写 bid_record.credentials JSON
  → 根据最新文件状态收敛批次
  → 重新查询所有批次，生成总体转换响应
```

Java 接收 Python 返回的 Markdown 正文，并独立负责 original/converted 的全部存储
操作。Python 只负责多格式文件流到 Markdown 的内容转换，不持有 MinIO 配置或
object key。converted 路径由 Java 根据 original 路径稳定生成，重复转换覆盖同一
派生对象。

转换成功后 `credentials` 回写通过 `BidRecordMapper.selectByIdForUpdate` 锁定投标记录，再查询所有 `credential_file` 中状态为 `CONVERTED` 且路径非空的记录，去重后整体覆盖 JSON。风险分析仍然按数据库批次/文件重新排序并读取，不把 `credentials` 当唯一事实源。

### 6.4 汇总 Markdown 与风险分析

入口：`POST /bid-record/analyze-risk/{id}`。前端不需要先调用转换接口。

```text
RiskAnalysisController
  → RiskAnalysisService.analyzeRisk()
  → 查询 bid_record 并抢占 ANALYZING
  → 检查批次/文件是否全部 CONVERTED
       未完成：调用 FileConvertService 无差别转换，重新查询
  → CredentialMarkdownAggregationService.aggregate()
       按 batch_no、file_order、file_id 排序
       读取每个 converted Markdown
       编排标题和分隔线，写临时汇总文件
       覆盖 bid-records/{id}/merged/credentials.md
  → 重新读取 merged Markdown
  → BidRecordLoader 加载投标记录基础信息
  → PromptFiller 加载 system/user Prompt 并填充变量
  → Spring AI ChatClient 调用 OpenAI 兼容模型
  → 读取 content；为空时回退到 reasoningContent
  → Fastjson2 解析并校验 riskLevel/summary/suggestions
  → 事务插入 risk_analysis，并将 bid_record 标记 COMPLETED
  → 返回 RiskAnalysis
```

汇总文件是派生对象，固定路径每次覆盖，不会在上一次汇总后继续追加。当前格式化逻辑在 `CredentialMarkdownAggregationService.writeMergedMarkdown()`：

```markdown
# 投标记录资信材料汇总

投标记录 ID：{bidRecordId}

## 批次 {batchNo}

### 文件：{originalFilename}

---

{converted Markdown}

---
```

`risk-analysis.max-merged-markdown-chars` 当前按 Java 字符数限制汇总输入，开发默认约 128K；超限时不调用模型。模型输出使用 JSON Schema 约束，落库的 `raw_response` 是 Java 最终抽取到的原始 JSON 文本，而不是数据库字段重新拼装的摘要。

### 6.5 知识资讯采集对接展位

`knowledgeingestionagent` 是后续综合资讯获取/处理 Agent 的 Java 边界：

- `KnowledgeIngestionAgentClient.startJob()` 调 Python `POST /policy-ingestion/jobs`；
- `getJob()` 调 Python `GET /policy-ingestion/jobs/{jobId}`；
- `KnowledgeIngestionJobRequest` 携带 jobId、时间窗口和可选 callbackUrl；
- `KnowledgeIngestionJobResponse` 携带任务状态、阶段、发现/索引/跳过/失败计数及内嵌 `Document` 结果；
- `KnowledgeIngestionAgentController.run()` 的 `POST /knowledge-ingestion-agent/run` 当前只返回占位成功，不会真正启动任务；
- 当前没有定时任务、Python 实现、政策原文/Markdown 的 Java 持久化编排、向量数据库或前端页面。

## 7. Controller 与外部服务索引

### 7.1 主要 HTTP 入口

| Controller | 入口 |
|---|---|
| `StatusCheckController` | `GET /service-status`、`POST /send-email`、`POST /send-notification` |
| `BidHandlerInfoController` | `/bid-handler-info/*`：新增、批量新增、查询、PATCH、删除、分页、批量删除 |
| `InvitationForBidController` | `/invitation-for-bid/*`：CRUD、批量新增、状态变更 |
| `BidRecordController` | `/bid-record/*`：CRUD、分页、批量删除、按公告查询 |
| `RiskAnalysisController` | `GET /risk-analysis`、`GET /risk-analysis/by-bid-record/{id}`、`POST /bid-record/analyze-risk/{id}` |
| `FileUploadController` | `POST /credential-files/upload`、`DELETE /credential-files/{uploadBatchId}/files/{fileId}` |
| `FileConvertController` | `POST /credential-files/bid-records/{bidRecordId}/convert` |
| `KnowledgeIngestionAgentController` | `POST /knowledge-ingestion-agent/run`（占位） |

完整请求字段和响应示例见 `docs/api.md`；实际 Controller 注解优先于文档中的历史端点数量。

### 7.2 外部调用

| Java 组件 | 外部目标 | 调用职责 |
|---|---|---|
| `LocalFileStorage` | Java 本地文件系统 | original/converted/merged 的本地持久化 |
| `MinioFileStorage` | MinIO | 与本地实现相同的 `store/open/delete` 抽象 |
| `ToMarkdownClient` | Python `to-markdown` | multipart 发送单个原文件流并接收 Markdown 文件 |
| `KnowledgeIngestionAgentClient` | Python knowledge ingestion agent | 启动/查询资讯采集任务 |
| `RiskAnalysisService` | OpenAI 兼容模型/LM Studio | 结构化风险分析 |
| `MailService` | SMTP | 纯文本和 HTML 邮件 |

### 7.3 存储抽象约束

新增业务代码应依赖 `FileStorage`，不要直接在 Service 中调用 NIO 或 MinIO SDK。`FileStorage.store()` 目标已存在时按实现约定覆盖，适合覆盖 `merged/credentials.md`；原始路径由 UUID 保证唯一。所有逻辑路径必须经过 `StoragePath`/规范化边界，不能接收客户端绝对路径作为存储目标。

## 8. 统一响应与异常流向

Controller 正常业务响应使用 `Result<T>`：

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {}
}
```

失败响应也使用 `Result`，稳定错误码由 `AppErrorCode` 提供，例如：

```text
COMMON.*
DATABASE.*
BID_RECORD.*
BID_HANDLER.*
INVITATION.*
CREDENTIAL_FILE.*
RISK_ANALYSIS.*
KNOWLEDGE_INGESTION_AGENT.*
MAIL.*
SYSTEM.*
```

`AppException` 承载错误码、module、operation、stage、details 和对外消息；`GlobalExceptionHandler` 负责将它以及参数校验异常、Spring DAO 异常、文件过大、非法参数和未知异常转换为 HTTP 状态 + `Result`。不向前端返回堆栈、SQL、内部 IP 或存储凭据。`Result` 目前保留 `traceId` 字段，但全局处理器不会生成或填充它；错误响应会生成 `errorId`。

重要边界：

- 基础 CRUD 中主动构造的“记录不存在”会进入全局处理器；未被 Service 转换的 DAO 异常通常映射为 `DATABASE.*`；
- 文件上传的存储、校验和、数据库失败通常包装为 `CREDENTIAL_FILE.*` 后进入全局处理器；
- 转换流程对单文件 Python 失败进行捕获、写 `FAILED` 并继续处理其他文件，最终多为 HTTP 200 + 转换汇总状态，前端必须检查 `data.status`；
- 异步邮件在线程中执行，失败不会回到原 HTTP 请求，也不会进入该请求的全局异常响应；
- `GlobalExceptionHandler` 只负责统一出口，不负责文件补偿、状态修复、重试或业务回滚。

## 9. 开发约定

### 9.1 Mapper、Service、DTO

| 维度 | 约定 |
|---|---|
| Mapper | 接口继承项目自定义 `BaseMapper<T, ID>`，自定义查询写在对应 `src/main/resources/mapper/*.xml` |
| CRUD Service | `BidHandlerInfoService`、`InvitationForBidService`、`BidRecordService` 继承 `BaseService<T>` |
| 业务流 Service | `RiskAnalysisService`、`FileUploadService`、`FileConvertService`、`CredentialMarkdownAggregationService` 等直接编排业务，不强行继承 CRUD 基类 |
| 事务 | 写操作使用 `@Transactional(rollbackFor = Exception.class)`；同类调用需要事务代理时使用当前代码中的 Spring 自注入/代理方式，新增代码应谨慎复用 |
| DTO | 创建请求常用 `AddRequest`，更新请求常用 `UpdateRequest`，批量操作使用 `BatchAddRequest`/`BatchDeleteRequest`；新增 DTO 需与实际 HTTP 语义匹配，不机械套命名 |
| Controller | 业务 JSON/multipart 入口优先返回 `Result<T>`；PATCH 返回 `Diff<T>`；保留邮件端点当前直接返回字符串的既有兼容行为 |
| 注入 | 现有代码主要使用 `jakarta.annotation.Resource`，新增代码应保持模块一致性 |
| 外键 | 不要假设数据库会自动校验或级联；需要存在性、归属和删除保护时必须显式写在 Service |

### 9.2 JavaDoc

Controller、Service、Component 和公共 API 方法应有类级/方法级 JavaDoc。JavaDoc 使用 `/** ... */`，置于声明之前；注释解释业务背景、设计原因、状态边界、异常条件和线程安全约束，不重复代码字面含义。显式参数、返回值和异常应使用 `@param`、`@return`、`@throws`；签名或业务规则变化时同步更新注释。简单 getter/setter 可以省略，关键私有方法按复杂度补充说明。

### 9.3 异步任务

`AsyncTaskManager.execute(Runnable, delayMillis)` 当前使用原生 `new Thread().start()`，主要服务于 `/send-notification` 的延迟邮件发送。它不是 Spring Bean，也不是可靠任务队列；高频、需要重试或需要持久化状态的任务不能直接复用该工具，应先明确新的调度/任务模型。

### 9.4 端到端改动顺序

涉及新持久化字段或表时，按以下顺序检查：

```text
DDL（V*.sql，手工执行）
  → Entity
  → Mapper 接口
  → Mapper XML/resultMap
  → Service/事务/状态规则
  → DTO 校验
  → Controller
  → 单元/集成测试与 docs
```

不要只改 Entity 或 Controller 而遗漏 XML。JSON 字段要同步确认 TypeHandler、数据库列类型和请求响应序列化。

## 10. 测试与验证

当前测试位于 `src/test/java`，包含：

- Spring context 加载测试和 `GlobalExceptionHandler` 测试；
- 文件 checksum、intake、上传处理器、上传 Service；
- Local/MinIO 存储、存储路径和条件装配测试；
- Python multipart 客户端协议和 Java 单文件转换闭环测试；
- Markdown 汇总 Service 测试；
- Controller 文件上传测试。

完整资信链路的 Postman 集合在：

```text
tests/postman/credential-file-flow.postman_collection.json
```

典型联调前提是 Java 服务、Python `to-markdown` 服务、Java 可访问的文件存储和
已存在的 `bid_record`。Python 不访问文件存储，因此无需与 Java 共享 MinIO 配置或
本地目录；生产配置为 `MINIO` 时由 Java 的 `MinioFileStorage` 完成全部对象操作。

## 11. 已实现、未完成与待确认事项

### 已实现

- 核心 CRUD、公告状态机和状态历史；
- 投标记录序号并发冲突重试；
- original 文件上传、Local/MinIO 存储切换、SHA-256 双读校验；
- Java 读取并校验 original，multipart 调用 Python 单文件转换；
- Java 生成 converted 路径、上传并回读校验 Markdown，再回写
  `credential_file` 和 `bid_record.credentials`；
- 投标记录级 Markdown 汇总覆盖写入；
- 风险分析状态抢占、自动触发必要转换、结构化模型输出、风险结果落库；
- 统一错误码和全局错误响应框架；
- 知识采集 Java 对接 DTO/Client/Controller 展位。

### 未完成或已知限制

- `DELETE /credential-files/{uploadBatchId}/files/{fileId}` 仍是占位实现，不会真实校验归属、删除对象或更新数据库；
- 上传不会自动把原始文件转换为 Markdown；风险分析入口会在需要时自动调用转换；
- 风险分析 `ANALYZING` 没有超时恢复；
- 手动转换与风险分析没有投标记录级互斥；
- 无鉴权、租户隔离、数据库硬外键、级联删除和孤儿数据清理；
- 资讯采集的 Python 服务、定时任务、LangChain 编排、原文/Markdown 持久化、去重、向量化、向量数据库和前端 UI 尚未实现；
- 生产 profile 的数据库、模型和文件存储配置完整性待部署前确认；
- Python 的标题/OCR 格式化细节不属于本仓库；converted 覆盖、MinIO 权限和存储
  完整性由 Java 的 `FileStorage` 实现负责。

### 参考资料

- `docs/architecture.md`：模块、依赖、状态和一致性边界；
- `docs/business-process.md`：端点级业务流程和异常清单；
- `docs/database.md`：表结构、字段和逻辑关系；
- `docs/api.md`：HTTP 请求/响应约定；
- `docs/structured-output.md`：Spring AI Structured Output 和模型响应解析；
- `tests/postman/README.md`：资信文件端到端联调前提。
