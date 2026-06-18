# AOP 邮件通知切面执行链路（已下线，仅供历史参考）

> ⚠️ **本文档已下线**，仅供参考，不反映当前代码状态。
>
> **当前邮件通知方案**：Service 层显式调用 `BaseService.sendEmail`，详见 [docs/architecture.md §7](architecture.md#7-service-层邮件通知baseservicesendemail)。
>
> **源代码已删除**：`aspect/CrudResultEmailAspect.java` + `annotation/SendEmailOnSuccess.java` 已从代码库移除。

---

## 1. 为什么需要这个切面（横切关注点 Cross-cutting Concerns）

- 邮件通知逻辑如果写在每个 Controller，18 个端点会有 18 段重复代码
- 用 AOP 切面统一处理：Controller 业务代码 0 改动，只需贴一个 `@SendEmailOnSuccess` 注解
- 典型横切场景：日志、鉴权、缓存、通知、限流、脱敏

## 2. 拦截规则（Spring AOP `@AfterReturning` 语义）

- 拦截所有标注 `@SendEmailOnSuccess` 的方法（`pointcut = "@annotation(anno)"`）
- 仅当方法**正常返回**时触发（异常不触发）
- 异常时触发用 `@AfterThrowing`；完全控制用 `@Around`

## 3. 完整执行链路（5 步主线程 + 4 步异步子线程）

### 3.1 主线程时间线（HTTP 请求 → 响应）

| Step | 动作 | 耗时 |
|---|---|---|
| 1 | Controller 业务执行完毕，`service.create(entity)` 落库，组装 `Result<Entity>` | 业务耗时 |
| 2 | 触发 `@AfterReturning` 切面（主线程**未**放行）<br>2.1 反射推断 endpoint/operation + fastjson2 序列化 Result.data + 构造 subject<br>2.2 `AsyncTaskManager.execute(runnable, 3000)` 提交异步任务 | 2.1 同步 ~微秒级<br>2.2 立即返回（new Thread + .start） |
| 3 | 切面方法 return | — |
| 4 | Spring AOP 代理对象放行 → Spring MVC 序列化 JSON → DispatcherServlet 写 HTTP Response | 序列化 + 网络 |
| | **此时接口响应完成（前端收到 200）** | |

### 3.2 后台子线程时间线（延迟 3 秒后，与主线程并行）

| Step | 动作 |
|---|---|
| 5.1 | `ResourceSearcher.validateTemplateExists(templateName)` 模板存在性校验 |
| 5.2 | 构造 Thymeleaf Context（注入 subject/endpoint/operation/timestamp/resultCode/resultMessage/resultDataJson） |
| 5.3 | `SpringTemplateEngine.process(templateName, context)` 渲染 HTML |
| 5.4 | `MailService.sendHtmlEmail(to, subject, html)` SMTP 发送 |
| 5.5 | try-catch 兜底（邮件失败仅日志告警，**不影响**主业务） |

## 4. 关键时序要点

- **主线程 Step 2.2 的 `AsyncTaskManager.execute(...)` 是非阻塞调用**
  - `new Thread(runnable).start()` 内部：构造 Thread 对象（微秒级）+ 调用 .start()（立即返回）
  - 主线程这行代码的执行时间 ≈ 微秒级
  - **不会**等待子线程完成，**不会**阻塞主线程
- **Step 5（后台子线程）可能仍在执行时，前端已经收到响应**
  - 主线程在 Step 4 完成后即返回（HTTP 响应已发）
  - 子线程可能还要花 1-3 秒完成 SMTP 发送
  - 前端**不感知**邮件是否发送成功（异步 + try-catch 兜底）
- **异常隔离**
  - 子线程异常被 try-catch 捕获 → 仅日志告警
  - 不会反向传播到主线程（不会导致 HTTP 500）
  - 不会影响 Result 对象的正确返回

## 5. endpoint / operation 推断规则

切面通过方法名自动推断 HTTP method + operation：

| 方法名前缀 | HTTP method | operation |
|---|---|---|
| `create*`, `batch*` | POST | CREATE / BATCH_DELETE |
| `patch*`, `update*` | PATCH | UPDATE |
| `delete*` | DELETE | DELETE |
| 其他（`get*`/`list*`） | GET | QUERY |

## 6. 收件人邮箱解析（`resolveRecipient`）

优先级：

```
Target-Email-Address HTTP Header  >  注解 @SendEmailOnSuccess.to()  >  配置默认
```

非 HTTP 上下文（如单元测试）会抛 `IllegalStateException` → 静默 fallback 到注解 `to()`。

## 7. 关键设计决策

### 7.1 为什么用 fastjson2 而非 Jackson

- 项目已引入 `com.alibaba.fastjson2:fastjson2:2.0.52`，复用避免新增依赖
- `JSON.toJSONString()` 是 fastjson2 的 API

### 7.2 为什么用 Java 反射（MethodSignature + Method）

- 切面不知道具体拦截哪个方法 → 用反射动态获取方法元信息
- `MethodSignature` 是 AspectJ 提供的类型，包装了 `Method` + 泛型解析

### 7.3 为什么必须 try-catch 兜底

- 切面代码**绝对不能**因为自身报错导致主业务方法回滚或请求失败
- 邮件发失败就失败日志告警，不能让用户的招投标信息保存失败
- 整个 `handleAfterReturning` 被外层 try-catch 包裹
- 异步任务内部也独立 try-catch（内层）

### 7.4 为什么异步发送

- 发邮件涉及 SMTP 网络 I/O，耗时可达 1-3 秒
- 同步发送会拖慢接口响应时间，用户体验差
- 异步后：HTTP 响应立即返回（200 OK），邮件在后台慢慢发
- `AsyncTaskManager` 是项目自实现的轻量级异步工具（`new Thread(...).start()`）
- 生产环境建议改用线程池（`ExecutorService`）复用线程

### 7.5 代理失效场景（防坑）

- 只有通过 Spring 注入的对象调用其方法时，AOP 才会生效
- 类**内部**的方法 A 直接调用方法 B，B 上的注解**不会**触发切面（跳过了"秘书"）
- 解决方案：注入自己的代理（`@Resource private CrudResultEmailAspect self;`），通过 `self.methodB()` 调用

### 7.6 注解实例绑定到方法参数的原理

- Spring AOP 通过反射读取方法上的注解 → 创建注解实例
- `@AfterReturning` 的 `argNames` 参数指定形参名（编译期字节码保留）
- 形参 `SendEmailOnSuccess anno` 直接接收注解实例，无需手动反射

## 8. 技术栈协同

- **Spring AOP / AspectJ**：横向拦截（`@Aspect` + `@AfterReturning`）
- **Java 反射**：动态获取方法元信息（`MethodSignature`）
- **fastjson2**：实体转 JSON 字符串（`JSON.toJSONString`）
- **Thymeleaf**：模板引擎渲染 HTML（`SpringTemplateEngine`）
- **AsyncTaskManager**：异步线程池（`AsyncTaskManager.execute`）
- **JavaMailSender**：SMTP 协议发邮件（`MailService.sendHtmlEmail`）

## 9. 扩展点（其他横切场景可参考本类）

- 操作日志记录切面（`@Before` + MethodSignature）
- 接口限流切面（`@Around` + Redis）
- 敏感数据脱敏切面（`@AfterReturning` + 处理 Result.data）
- 分布式锁切面（`@Around`）

## 10. 默认邮件模板（`crud-result-notification.html`）

模板变量：

| 变量 | 来源 | 示例 |
|---|---|---|
| `subject` | 注解 `subject()` 或自动构造 | `[业务操作通知] POST BidHandlerInfoController#createBidHandlerInfo CREATE - 2026-06-17 10:30:45` |
| `endpoint` | 反射推断 | `POST BidHandlerInfoController#createBidHandlerInfo` |
| `operation` | 反射推断 | `CREATE` / `UPDATE` / `DELETE` / `BATCH_DELETE` / `QUERY` |
| `timestamp` | 当前时间（`LocalDateTime.now()`） | `2026-06-17 10:30:45` |
| `resultCode` | `Result.getCode()` | `200` |
| `resultMessage` | `Result.getMessage()` | `创建成功` |
| `resultDataJson` | fastjson2 序列化 `Result.data` | `{"handlerId":1,"handlerName":"张三",...}` |
