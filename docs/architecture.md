# 项目架构文档

> 本文档深入分析 bid-winning-backend 项目的内部架构，涵盖 Spring Boot 启动流程、实体类设计、Controller 层、Service 层和工具类的实现细节。
>
> **对外 API 契约**：前端联调请查阅 [`docs/api.md`](api.md)（21 端点完整文档，含请求/响应字段表与 curl 示例）。本文档聚焦于**内部实现**视角。

---

## 1. Spring Boot 启动流程

### 1.1 程序入口：`main()` 方法

```
BidWinningBackendApplication.java:27
```

```text
public static void main(String[] args) {
    SpringApplication.run(BidWinningBackendApplication.class, args);
}
```

这是整个项目的起点。当执行 `./mvnw spring-boot:run` 或在 IDE 中点击 Run 时，JVM 首先执行 `main()` 方法。

`SpringApplication.run()` 做了两件事：
1. 创建 `SpringApplication` 实例（传入 `BidWinningBackendApplication.class`，记作"主配置类"）
2. 调用该实例的 `run()` 方法

---

### 1.2 `SpringApplication.run()` 内部执行流程

`SpringApplication.run()` 的执行分为多个阶段，按顺序如下：

#### 阶段 1：推断应用类型（DeduceFromClasspath）

Spring Boot 通过检查 classpath 中是否存在特定类来推断当前是 Servlet 应用还是 Reactive 应用：

```
Servlet 类存在（jakarta.servlet.Servlet）→ Spring MVC（servlet-based）
Reactor 类存在（reactor.core.publisher.Publisher）→ WebFlux（reactive）
都不存在 → 非 Web 应用
```

本项目引入的是 `spring-boot-starter-webmvc`，属于 **Servlet-based** 应用。

#### 阶段 2：加载自动配置

Spring Boot 4.x 在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（旧版用 `spring.factories`）中注册了大量自动配置类（Auto Configuration）。

这些配置类在启动阶段会被逐一加载、评估、决定是否生效。

#### 阶段 3：创建 `ApplicationContext`（IOC 容器）

```text
// Spring Boot 内部简化逻辑
DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
ApplicationContext context = new AnnotationConfigApplicationContext(beanFactory);
```

实际上 Spring Boot 会根据应用类型创建不同的容器：
- Servlet 应用 → `AnnotationConfigServletWebServerApplicationContext`
- Reactive 应用 → `AnnotationConfigReactiveWebServerApplicationContext`

#### 阶段 4：启动嵌入式 Servlet 容器

Spring Boot 默认使用嵌入式 Tomcat（NioEndpoint）。

容器不仅负责托管 Spring Bean，还负责监听 HTTP 请求并分发给 `DispatcherServlet`。

---

### 1.3 `@SpringBootApplication` 三合一注解原理

```text
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(excludeFilters = { ... })
public @interface SpringBootApplication {
    // ...
}
```

| 注解 | 作用 |
|------|------|
| `@SpringBootConfiguration` | 本质是 `@Configuration`，标记此类为 Java Config 配置类，作为容器 Bean 注册的来源之一 |
| `@EnableAutoConfiguration` | 启用自动配置，让 Spring Boot 根据 classpath 和条件注解（`@Conditional`）自动配置第三方库 |
| `@ComponentScan` | 组件扫描，从主配置类所在包（`io.gitee.lujianshi.bidwinningbackend`）向下扫描，收集 `@Component`、`@Service`、`@Controller` 等标注的类注册为 Spring Bean |

**关键点**：`@ComponentScan` 默认只扫描主配置类所在的包及其子包。这就解释了为什么 `BidWinningBackendApplication` 放在 `io.gitee.lujianshi.bidwinningbackend` 包下，所有子包（controller、service、dto、entity、utils）中的组件都能被自动发现。

---

### 1.4 `@ComponentScan` 组件扫描流程

1. Spring 发现 `@ComponentScan` 后，立即开始扫描
2. 扫描范围：从主配置类所在包向下递归，匹配所有 `.class` 文件
3. 扫描到的类若带有以下注解之一，则注册为 Bean：
   - `@Component`
   - `@Repository`
   - `@Service`
   - `@Controller`
   - `@Configuration`

本项目中注册为 Bean 的类：

| 类 | 注解 |
|----|------|
| `MailService` | `@Service` |
| `TemplateFiller` | `@Service` |
| `ResourceSearcher` | `@Service` |
| `BidHandlerInfoService` | `@Service` |
| `BidWinningNotificationService` | `@Service` |
| `BidWinningSupplierDetailService` | `@Service` |
| `StatusCheckController` | `@RestController`（含 `@Controller`） |
| `BidHandlerInfoController` | `@RestController`（含 `@Controller`） |
| `BidWinningNotificationController` | `@RestController`（含 `@Controller`） |
| `BidWinningSupplierDetailController` | `@RestController`（含 `@Controller`） |
| `CrudResultEmailAspect` | `@Aspect`（AOP 切面，代理 Controller 调用） |
| `BidWinningBackendApplication` | `@Configuration`（隐含） |

`AsyncTaskManager` **没有**任何上述注解，它是一个纯静态工具类，不归 Spring 容器管理。

**Bean 注册时机**：Bean 的创建（instantiation）在容器 refresh 阶段进行。Spring 通过 `BeanFactory` 接口统一管理所有 Bean 的创建、初始化和依赖注入。

---

### 1.5 `@EnableAutoConfiguration` 自动配置详解

`@EnableAutoConfiguration` 是 Spring Boot 最核心的能力之一。它通过 `AutoConfigurationImportSelector` 读取 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（或旧版 `META-INF/spring.factories`）中列出的自动配置类。

对于本项目，以下自动配置类参与了启动过程：

#### 1.5.1 `MailSenderAutoConfiguration`

**配置时机**：检测到 classpath 中存在 `spring-boot-starter-mail` 且存在 `JavaMailSenderImpl` 类时生效。

**做了什么**：
1. 读取 `application.properties` 中以 `spring.mail.` 开头的配置项
2. 创建 `JavaMailSenderImpl` 实例（实现 `JavaMailSender` 接口）
3. 设置 SMTP 参数：`host`、`port`、`username`、`password`、`default-encoding`
4. 配置 `Authenticator`（如果需要）
5. 注册为容器中唯一的 `JavaMailSender` Bean

**关键结果**：
```
JavaMailSender mailSender  ← MailService 通过 @Autowired 注入
```

#### 1.5.2 `ThymeleafAutoConfiguration`

**配置时机**：检测到 classpath 中存在 `spring-boot-starter-thymeleaf` 时生效。

**做了什么**：
1. 读取 `spring.thymeleaf.*` 配置（`prefix`、`suffix`、`mode`、`encoding`）
2. 创建 `SpringTemplateEngine` 实例（Thymeleaf 的核心渲染引擎）
3. 配置 `TemplateResolver`：根据 `prefix` + 模板名 + `suffix` 拼接完整路径
   - `prefix` = `classpath:/templates/`（本项目配置）
   - `suffix` = `.html`（本项目配置）
   - 因此模板名 `"bid-winning-notification"` 解析为 `classpath:/templates/bid-winning-notification.html`
4. 注册为容器中唯一的 `SpringTemplateEngine` Bean

**关键结果**：
```
SpringTemplateEngine templateEngine  ← TemplateFiller 通过 @Autowired 注入
```

#### 1.5.3 `WebMvcAutoConfiguration`

**配置时机**：当引入 `spring-boot-starter-webmvc` 时自动生效。

**做了什么**：
1. 配置 `DispatcherServlet`（Spring MVC 的前端控制器）
2. 注册 `HandlerMapping`（URL 到 Controller 方法的映射表，默认 `RequestMappingHandlerMapping`）
3. 注册 `HandlerAdapter`（用于调用 Controller 方法，默认 `RequestMappingHandlerAdapter`）
4. 注册 `ViewResolver`（视图解析器，用于渲染模板，本项目未使用）
5. 配置 `MessageCodesResolver`（用于绑定错误消息）
6. 添加 `FunctionalBean`（如 `methodParameterNameDiscoverer`）

**关键结果**：
```
DispatcherServlet  ← 所有 HTTP 请求的入口
RequestMappingHandlerMapping  ← /service-status → StatusCheckController.serviceStatus() 的映射关系在此注册
RequestMappingHandlerAdapter  ← 调用 Controller 方法的适配器
```

---

### 1.6 嵌入式 Servlet 容器（Tomcat）启动

Spring Boot 默认使用嵌入式 Tomcat。启动流程如下：

#### 步骤 1：创建 Tomcat 实例

```text
Tomcat tomcat = new Tomcat();
tomcat.setPort(8079);  // 从 application.properties 读取
tomcat.getConnector();  // 初始化 connector（NIO 模式）
```

#### 步骤 2：创建 Context（应用上下文）

Tomcat 为每个 Web 应用创建一个 `StandardContext`，对应本项目的 Servlet 应用。

#### 步骤 3：注册 DispatcherServlet

Spring Boot 将 `DispatcherServlet` 注册到 Tomcat 的 `Context` 中：

```
DispatcherServlet  ←  url-mapping: /
                        ↓
                接收所有 HTTP 请求（POST /send-email, GET /service-status 等）
```

#### 步骤 4：启动 NIO Endpoint

```
NioEndpoint.start()
  → Poller线程 ← 监听 8079 端口的 socket 连接
  → Request 处理线程池
  → CoyoteAdapter.service() ← 将请求传递给 Tomcat pipeline
  → ApplicationFilterChain ← 执行 Filter 链
  → DispatcherServlet.doDispatch() ← Spring MVC 分发
```

Tomcat 使用 NIO 模式（`NioEndpoint`），一个 Poller 线程轮询多个 socket，接收连接后分发到线程池处理，相比传统的 BIO（每连接一线程）更高效。

#### 步骤 5：请求分发给 DispatcherServlet

当 HTTP 请求进入 `DispatcherServlet.doDispatch()` 后，Spring MVC 的请求处理链路如下：

```
DispatcherServlet.doDispatch(HttpServletRequest, HttpServletResponse)
  │
  ├─ 1. HandlerMapping.getHandler(request)
  │     ↓
  │     遍历所有 HandlerMapping 实现（默认 RequestMappingHandlerMapping）
  │     根据 HTTP method + URL 匹配到对应的 HandlerMethod（StatusCheckController.serviceStatus）
  │     返回 HandlerExecutionChain（包含 Handler + Interceptor列表）
  │
  ├─ 2. HandlerAdapter.supports(handler) → RequestMappingHandlerAdapter
  │
  ├─ 3. HandlerAdapter.handle(request, response, handler)
  │     ↓
  │     → ArgumentResolvers：根据参数类型，从 request 中解析参数值
  │        例如：StatusCheckController.sendNotification(BidWinningMailRequest request)
  │        → Spring 通过 data binding 将 form-data 字段映射到 BidWinningMailRequest 对象
  │     → HandlerMethod.invoke()：调用 Controller 方法
  │     → ReturnValueHandlers：将方法返回值转换为响应（直接写入 body 或渲染视图）
  │
  └─ 4. 执行 Interceptor.postHandle()（如果有）
```

---

### 1.7 `@EnableAsync` 异步机制

```text
@EnableAsync   // BidWinningBackendApplication.java:24
public class BidWinningBackendApplication { ... }
```

`@EnableAsync` 的作用是**开启 Spring AOP 代理模式**，使得标注了 `@Async` 的方法能够被异步执行。

#### 工作原理

`@EnableAsync` 会向容器注册一个 `AsyncAnnotationBeanPostProcessor`（一个 `BeanPostProcessor`）。

**关键机制**：
1. `BeanPostProcessor` 在每个 Bean 创建完成后，判断该 Bean 是否有 `@Async` 方法
2. 如果有，Spring 为该 Bean **创建一个 AOP 代理**（`JdkDynamicAopProxy` 或 `CglibAopProxy`）
3. 代理拦截所有 `@Async` 方法调用，将其包装为 `AsyncTask` 提交到线程池执行
4. 调用方立即返回，实际执行在线程池中进行

#### 本项目中的 @Async 使用

```text
// Service 层注解读取原理演示
@Async
public void fetchAfterDelay() {
    AsyncTaskManager.execute(() -> { ... }, 3000);
}
```

Spring AOP 代理在调用 `fetchAfterDelay()` 时：
- 不会在当前线程执行
- 而是将任务提交到 `SimpleAsyncTaskExecutor`（`@EnableAsync` 默认使用的执行器）
- 主线程立即返回，3 秒后在新线程中打印"获取地址成功"

**注意**：早期 `HelloService.fetchAfterDelay()` 内部调用了 `AsyncTaskManager.execute()` 形成双重异步。该服务已下线（`StatusCheckController` 仅保留 `serviceStatus` / `sendEmail` / `sendNotification` 三个测试端点），不再存在此问题。

---

### 1.8 Bean 创建顺序与依赖注入

Spring 容器在 refresh 阶段按照一定顺序创建 Bean：

```
1. BeanFactoryPostProcessor（最早，可修改 BeanDefinition）
   ↓
2. BeanPostProcessor（创建 Bean 实例前后的拦截）
   ↓
3. 普通 Bean（按依赖关系排序）
```

本项目的 Bean 创建大致顺序：

| 顺序 | Bean 类 | 依赖 |
|------|---------|------|
| 1 | `JavaMailSenderImpl` | 无（由 `MailSenderAutoConfiguration` 创建） |
| 2 | `SpringTemplateEngine` | 无（由 `ThymeleafAutoConfiguration` 创建） |
| 3 | `MailService` | `JavaMailSender`、`@Value` |
| 4 | `ResourceSearcher` | `ResourcePatternResolver` |
| 5 | `TemplateFiller` | `SpringTemplateEngine`、`ResourceSearcher` |
| 6 | `BaseMapper<T>` | 无（泛型基接口，由具体 Mapper 继承） |
| 7 | `BidHandlerInfoMapper`（extends `BaseMapper<BidHandlerInfo>`） | 无（由 `@MapperScan` 扫描 + XML 代理生成） |
| 8 | `BidWinningNotificationMapper`（extends `BaseMapper<BidWinningNotification>`） | 同上 |
| 9 | `BidWinningSupplierDetailMapper`（extends `BaseMapper<BidWinningSupplierDetail>`） | 同上 |
| 10 | `BidHandlerInfoService`（extends `BaseService<BidHandlerInfo>`） | `BidHandlerInfoMapper` |
| 11 | `BidWinningNotificationService`（extends `BaseService<BidWinningNotification>`） | `BidWinningNotificationMapper` |
| 12 | `BidWinningSupplierDetailService`（extends `BaseService<BidWinningSupplierDetail>`） | `BidWinningSupplierDetailMapper` |
| 13 | `StatusCheckController` | `MailService`、`TemplateFiller` |
| 14 | `BidHandlerInfoController` | `BidHandlerInfoService` |
| 15 | `BidWinningNotificationController` | `BidWinningNotificationService` |
| 16 | `BidWinningSupplierDetailController` | `BidWinningSupplierDetailService` |

`@Autowired` 注入发生在 Bean 创建时，Spring 通过 **AutowiredAnnotationBeanPostProcessor**（一个 `InstantiationAwareBeanPostProcessorAdapter`）实现：

```
Bean 创建 → 检查字段/构造函数上的 @Autowired → 从容器中查找匹配类型的 Bean → 通过反射（Field.set()）注入
```

---

## 2. 实体类（DTO）

### 2.1 `PlainTextMailRequest`

**文件**：`src/main/java/.../dto/mail/PlainTextMailRequest.java`

**Lombok 注解**：

| 注解 | 生成的代码 |
|------|-----------|
| `@Data` | `getTo()`、`setTo()`、`getFrom()`、`setFrom()`、`getSubject()`、`setSubject()`、`getContent()`、`setContent()`、`equals()`、`hashCode()`、`toString()` |
| `@NoArgsConstructor` | `PlainTextMailRequest()` 无参构造器 |
| `@AllArgsConstructor` | `PlainTextMailRequest(to, from, subject, content)` 全参构造器 |

**字段**：

| 字段 | 类型 | 作用 |
|------|------|------|
| `to` | String | 收件人邮箱地址 |
| `from` | String | 发件人邮箱地址（**当前未实际使用**，发件人固定为配置中的 `spring.mail.username`） |
| `subject` | String | 邮件主题 |
| `content` | String | 邮件内容（纯文本，`MailService.sendEmail` 使用） |

**设计意图**：通用邮件数据传输对象，可同时作为请求对象、响应对象或跨层传递对象。本项目用于 `MailService.sendEmail()` 的参数载体。

---

### 2.2 `BidWinningMailRequest`

**文件**：`src/main/java/.../dto/mail/BidWinningMailRequest.java`

```text
public class BidWinningMailRequest extends PlainTextMailRequest {
    private String projectCode;                        // 项目编号
    private String handlerName;                        // 经办人
    private List<BidWinningListItem> bidWinningList;   // 中标结果列表
    private String templateName;                        // 模板文件名（不含 .html 后缀）

    @Data
    public static class BidWinningListItem {
        private String supplierName;    // 供应商名称
        private String bidWinningAmount; // 中标价
    }
}
```

**继承关系**：`BidWinningMailRequest` extends `PlainTextMailRequest`

**继承来的字段**：`to`、`from`、`subject`、`content`（content 在本流程中不使用，由 HTML 模板替代）

**自有字段**：

| 字段 | 类型 | 作用 |
|------|------|------|
| `projectCode` | String | 项目编号，用于填充中标通知书模板中的 `th:text="${projectCode}"` |
| `handlerName` | String | 经办人姓名，用于填充模板中的 `th:text="${handlerName}"` |
| `bidWinningList` | `List<BidWinningListItem>` | 中标结果列表，`th:each` 循环渲染供应商名称 + 中标价 |
| `templateName` | String | 模板文件名（不含 .html 后缀），由用户传入，后端根据此字段搜索并渲染对应模板 |

**设计意图**：
- 使用继承而非组合，是为了复用 `to`/`subject` 等字段，避免重复定义
- `BidWinningMailRequest` 专门用于中标通知书邮件场景，携带模板渲染所需的项目信息
- `bidWinningList` 支持多行中标数据，前端传入 JSON 数组，后端通过 Thymeleaf `th:each` 渲染
- `templateName` 由用户传入，实现动态模板选择；传入值必须与 `classpath:/templates/` 下的实际文件名精确匹配，不存在则抛异常

---

## 2.3 实体类（Entity）

**目录**：`src/main/java/io/gitee/lujianshi/bidwinningbackend/entity/`

**职责**：与数据库表结构一一对应的 Java POJO。**已加 MyBatis-Plus 注解**（`@TableName` + `@TableId`），由 `mybatis-plus-spring-boot4-starter` 自动处理映射。

**共同特点**：

| 特征 | 说明 |
|------|------|
| Lombok 注解 | `@Data` + `@NoArgsConstructor` + `@AllArgsConstructor` |
| 命名风格 | Java 驼峰，对应 DB 下划线（如 `handlerName` ↔ `handler_name`） |
| 主键类型 | `Integer`（包装类，区分 null 与 0） |
| 金额字段 | `BigDecimal`（避免浮点精度问题） |
| 布尔字段 | `Boolean`（包装类，区分 null 与 false） |
| 时间字段 | `LocalDateTime`（Java 8+，线程安全） |

### 2.3.1 `BidHandlerInfo`

**对应表**：`bid_handler_info`（V3 重命名自 `handler_info`）

| Java 字段 | DB 字段 | 类型 | 必填 |
|-----------|---------|------|------|
| `handlerId` | `handler_id` | `Integer` | ✅（PK, AUTO_INCREMENT） |
| `handlerName` | `handler_name` | `String` | ✅ |
| `companyName` | `company_name` | `String` | ✅（V2+） |
| `departmentName` | `department_name` | `String` | ✅（V2+） |

### 2.3.2 `BidWinningNotification`

**对应表**：`bid_winning_notification`（V4 创建）

19 个字段，详见 `docs/database.md` 2.2 节。

### 2.3.3 `BidWinningSupplierDetail`

**对应表**：`bid_winning_supplier_detail`（V5 创建）

| Java 字段 | DB 字段 | 类型 | 必填 |
|-----------|---------|------|------|
| `detailId` | `detail_id` | `Integer` | ✅（PK, AUTO_INCREMENT） |
| `notificationId` | `notification_id` | `Integer` | ✅（FK，关联主表） |
| `supplierName` | `supplier_name` | `String` | ✅ |
| `amount` | `amount` | `BigDecimal` | ✅ |

**与 DTO 的命名差异**（已知问题）：

| Java 字段 | DTO 字段 | DB 字段 |
|-----------|----------|---------|
| `projectNo` | `projectCode` | `project_no` |
| `amount` | `bidWinningAmount` | `amount` |

**解决思路**（待引入 ORM 后处理）：
- 方案 A：Entity 跟 DB 命名，DTO 跟业务命名，加 Mapper 转换层
- 方案 B：Entity 跟 DTO 命名，加 `@TableField("xxx")` 映射

---

### 2.4 `Result<T>` 通用响应包装

**文件**：`src/main/java/.../dto/common/Result.java`

**设计目标**：所有 Controller 端点统一返回 `Result<T>`，前端解析结构一致；HTTP 状态码只表示传输层状态（200/400/500），**业务状态由 `code` 字段表达**。

**字段**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `code` | `Integer` | ✅ | 业务状态码（200=成功，404=资源不存在） |
| `message` | `String` | ✅ | 文字消息（用户友好提示或错误描述） |
| `data` | `T`（泛型） | ❌（删除类操作时为 null） | 业务数据载荷 |

**静态工厂**：

```text
// 成功响应（含数据）
public static <T> Result<T> success(T data, String message)
// e.g. Result.success(entity, "创建成功")

// 业务失败响应
public static <T> Result<T> error(Integer code, String message)
// e.g. Result.error(404, "记录不存在")
```

**使用约定**：

| 场景 | code | message | data |
|------|------|---------|------|
| 成功（含数据） | 200 | 自定义 | 业务对象 |
| 成功（无数据，如删除） | 200 | 自定义 | `null` |
| 业务失败 | 404 或自定义 | 必填 | `null` |

**响应示例**：

```json
{
  "code": 200,
  "message": "创建成功",
  "data": {"handlerId":1,"handlerName":"张三",...}
}
```

**链路位置**：

```
Service（业务结果）→ Controller（包装为 Result）→ Jackson（序列化为 JSON）→ 前端
```

---

### 2.5 `Diff<T>` 通用 diff 包装

**文件**：`src/main/java/.../dto/common/Diff.java`

**设计目标**：用于展示 update 操作前后的实体对比，前端可直观看到"修改前 → 修改后"的完整数据。

**字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `before` | T（泛型） | 修改前的实体（DB 原值） |
| `after` | T（泛型） | 修改后的实体（请求体新值 + path id） |

**使用场景**：PUT /bid-handler-info/{id} 更新端点（返回 `Result<Diff<BidHandlerInfo>>`）。

**响应示例**：

```json
{
  "code": 200,
  "message": "更新成功",
  "data": {
    "before": {"handlerId":1,"handlerName":"张三","companyName":"原公司","departmentName":"原部门"},
    "after":  {"handlerId":1,"handlerName":"李四","companyName":"新公司","departmentName":"新部门"}
  }
}
```

**泛型优势**：未来其他表的 update 端点可复用，无需新建 Diff 子类。

---

### 2.6 批量删除 DTO 模式（每端点独立 DTO + data 返实体列表）

**文件**：
- `src/main/java/.../dto/bidhandlerinfo/BidHandlerInfoBatchDeleteRequest.java`
- `src/main/java/.../dto/bidwinningnotification/BidWinningNotificationBatchDeleteRequest.java`
- `src/main/java/.../dto/bidwinningsupplierdetail/BidWinningSupplierDetailBatchDeleteRequest.java`（**预留**，等 SupplierDetail Controller 启用）

**设计目标**：每端点独立 DTO，符合阿里《Java 开发手册》"推荐每 API 入口对应独立 DTO"。

**命名规范**：`<EntityName>BatchDeleteRequest`（实体名 + "BatchDelete" + "Request"）

**字段**（3 个 DTO 结构完全相同，但**类型独立**）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|------|------|------|
| `ids` | `List<Integer>` | ✅ | `@NotEmpty` + `@Size(max=500)` | 待删除的主键 ID 列表 |

**校验规则**：
- `@NotEmpty`：ids 列表不能为 null 或空（`@NotEmpty` 同时校验 null 和 empty）
- `@Size(max=500)`：单次最多 500 条，防止大批量误操作与长 SQL 锁表

**响应语义（p3 升级版 — data 放实体列表）**：

| 场景 | code | message | data |
|------|------|---------|------|
| 成功（含删除的实体） | 200 | "批量删除完成" | `List<Entity>`（实际删除的实体列表，DB 物理顺序） |
| ids 为空（@NotEmpty 失败） | — | — | `MethodArgumentNotValidException` → 由 `GlobalExceptionHandler` 返 400（待实现） |
| ids 超 500（@Size 失败） | — | — | 同上 |
| ids 部分/全部不存在 | 200 | "批量删除完成" | `List<Entity>` 只含**实际存在并被删的**实体 |

**调用示例**：

```http
POST /bid-handler-info/batch-delete
Content-Type: application/json

{ "ids": [1, 2, 3] }     // 假设 2 和 3 存在，1 不存在
```

**响应示例**：

```json
{
  "code": 200,
  "message": "批量删除完成",
  "data": [
    {"handlerId": 2, "handlerName": "李四", "companyName": "...", "departmentName": "..."},
    {"handlerId": 3, "handlerName": "王五", "companyName": "...", "departmentName": "..."}
  ]
}
```

**前端 diff 用法**：
```js
const requested = [1, 2, 3];
const deleted = response.data;                          // 实际删除的实体列表
const deletedIds = deleted.map(item => item.handlerId);  // [2, 3]
const missing = requested.filter(id => !deletedIds.includes(id));  // [1]
```

**p3 升级版的设计取舍**：

| 项 | 决策 | 理由 |
|----|------|------|
| ✅ 返实体列表 | data = `List<Entity>` | 前端展示更友好，无需自己再查"哪些存在" |
| ✅ 多 1 次 SELECT | `selectByIds` 先查存在行 | PK 索引，毫秒级；500 条以内几乎无感 |
| ✅ 删存在行 | `deleteByIds(existingIds)` | 不依赖 SELECT 的"无副作用"假设 |
| ❌ 事务封装 | 不加 `@Transactional` | 罕见 race 自洽：data 返的行 = 实际删的行（数量一致）|
| ❌ 级联删除 | 不做 | 应用层不保证外键关系 |
| ❌ 完整性校验 | 不做 | MySQL IN 自然忽略不存在的 ID |
| ❌ 排序 | 不加 `ORDER BY` | DB 物理顺序，最小实现 |
| ❌ 返回 `failed[]` | 不返 | 前端 diff 自己请求的 ids 即可 |

**race condition 自洽说明**：
- 如果 SELECT 后到 DELETE 前有人删了某行 X，SELECT 已包含 X（因为 SELECT 之前 X 存在）
- DELETE 时 X 已被别人删了，DELETE 不会重复删
- data 返回的 X 行**仍然有效**（X 的数据是我们 SELECT 时拿到的）
- 不会出现"data 返 5 行但实际删 4 行"这种不一致

**不复用 DTO 的理由**（与通用 `BaseMapper` / `BaseService` 策略不同：DTO 层仍坚持每端点独立，因为 DTO 面向 API 契约，Mapper/Service 面向内部实现）：

| 风险 | 说明 |
|------|------|
| 端点耦合 | 改一个端点的 DTO 会影响所有共用 DTO 的端点 |
| OpenAPI/Swagger 失真 | 共用 DTO 导致 schema 合并成 1 个，前端 codegen 失真 |
| 端点独立演化 | 部分端点可能加 `cascade: true`、`reason: "audit"` 等端点专属字段 |
| 命名误导 | 共用 DTO 的名字带"BatchDelete"会暗示绑定特定语义 |

**与 `PartialResult<T>` 的取舍**（未来扩展）：
- `PartialResult<T>` 提供 `successful[]` + `failed[]` 详细反馈，省去前端 diff
- p3 升级版选**实体列表**（更常用），前端要 failed 自己算
- `PartialResult<T>` 后续可加（如某端点专门要 failed 信息）

**链路位置**：

```
Service（SELECT 存在行 → DELETE 它们 → 返实体列表）→ Controller（包装为 Result<List<Entity>>）→ Jackson → 前端
```

**当前使用范围**：
- `BidHandlerInfoController.batchDeleteBidHandlerInfo` ← 用 `BidHandlerInfoBatchDeleteRequest`
- `BidWinningNotificationController.batchDeleteBidWinningNotification` ← 用 `BidWinningNotificationBatchDeleteRequest`
- `BidWinningSupplierDetailController.batchDeleteBidWinningSupplierDetail` ← 用 `BidWinningSupplierDetailBatchDeleteRequest`（**已启用**）

---

## 3. Controller 层

### 3.1 `StatusCheckController`

**文件**：`src/main/java/.../controller/statuscheck/StatusCheckController.java`

**类注解**：`@RestController = @Controller + @ResponseBody`

**含义**：所有方法的返回值直接序列化写入 HTTP Response Body（不经过视图解析器渲染模板）。本 Controller 承担**服务状态探活 + 邮件测试**两类非业务功能。

---

#### 3.1.1 `GET /service-status`

**简述**：Spring 服务存活探活，返回固定字符串。

```text
@GetMapping("/service-status")
public String serviceStatus() {
    return "Service is up and running.";
}
```

| 项目 | 内容 |
|------|------|
| HTTP 方法 | GET |
| 完整 URL | `http://localhost:8080/service-status` |
| 参数 | 无 |
| 返回值 | 字符串 `"Service is up and running."` |

---

#### 3.1.2 `POST /send-email`

**简述**：发送纯文本邮件（同步）

```text
@PostMapping("/send-email")
public String sendEmail(@RequestBody PlainTextMailRequest request) {
    mailService.sendEmail(request);
    return "Email sent successfully.";
}
```

| 项目 | 内容 |
|------|------|
| HTTP 方法 | POST |
| 完整 URL | `http://localhost:8080/send-email` |
| 请求 Content-Type | `application/json` |
| 请求体 | `PlainTextMailRequest`（JSON: `to` / `subject` / `content`） |
| 调用链路 | `StatusCheckController.sendEmail(request)` → `MailService.sendEmail(request)` → `JavaMailSender.send(SimpleMailMessage)` |
| 返回值 | 字符串 `"Email sent successfully."` |

**Spring 参数绑定流程**：

```
HTTP POST JSON body
  ↓
MappingJackson2HttpMessageConverter（Spring MVC 内部）
  ↓
Jackson 反序列化为 PlainTextMailRequest 对象
  ↓
对象传入 Controller 方法
```

---

#### 3.1.3 `POST /send-notification`

**简述**：发送中标通知书 HTML 邮件（异步，延迟 5 秒）

```text
@PostMapping("/send-notification")
public String sendNotification(@RequestBody BidWinningMailRequest request) {
    String htmlContent = templateFiller.processTemplate(request);

    AsyncTaskManager.execute(() -> {
        try {
            mailService.sendHtmlEmail(
                request.getTo(),
                request.getSubject(),
                htmlContent
            );
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }, 5000);
    return "发送成功";
}
```

| 项目 | 内容 |
|------|------|
| HTTP 方法 | POST |
| 完整 URL | `http://localhost:8080/send-notification` |
| 请求 Content-Type | `application/json` |
| 请求体 | `BidWinningMailRequest`（JSON: `to` / `subject` / `projectCode` / `handlerName` / `bidWinningList` / `templateName`） |
| 调用链路 | 见下方详细分解 |
| 返回值 | 字符串 `"发送成功"`（**立即返回**，不等待邮件实际发送） |

**完整调用链路**（模板动态选择 + 异步延迟发送）：

```
Step 1. 接收 BidWinningMailRequest（JSON → @RequestBody 自动反序列化）

Step 2. templateFiller.processTemplate(request)
        ├─ 读取 request.getTemplateName() → 例如 "bid-winning-notification2"
        ├─ resourceSearcher.validateTemplateExists(templateName)
        │   ├─ 扫描 classpath:/templates/*.html
        │   ├─ 精确匹配 "bid-winning-notification2"
        │   └─ 找不到则抛出 IllegalArgumentException，不 fallback
        ├─ 创建 new Context()
        ├─ context.setVariable("projectCode", request.getProjectCode())
        ├─ context.setVariable("handlerName", request.getHandlerName())
        ├─ context.setVariable("bidWinningList", request.getBidWinningList())
        └─ templateEngine.process(templateName, context)
            ├─ TemplateResolver 拼接完整路径
            ├─ 读取 HTML 文件内容
            ├─ 扫描 th:text / th:each 表达式
            └─ 返回渲染后的 HTML 字符串

Step 3. 立即返回 "发送成功"（主线程不受异步任务影响）

Step 4. 异步任务（AsyncTaskManager.execute(..., 5000)，延迟 5 秒后执行）：
        └─ mailService.sendHtmlEmail(to, subject, htmlContent)
            ├─ mailSender.createMimeMessage() 创建 MimeMessage
            ├─ new MimeMessageHelper(mimeMessage, true, "UTF-8")
            ├─ helper.setFrom(senderEmail)
            ├─ helper.setTo(to)
            ├─ helper.setSubject(subject)
            ├─ helper.setText(htmlContent, true)  ← HTML 格式
            └─ mailSender.send(mimeMessage)
```

**模板搜索行为**：
- 搜索范围：`classpath:/templates/*.html`（`ResourcePatternResolver` 扫描）
- 匹配方式：**精确匹配**（传入 `"bid-winning-notification2"` 匹配文件名 `"bid-winning-notification2.html"`）
- 行为安全：**不存在则抛异常**，`IllegalArgumentException: 模板不存在: bid-winning-notification2`，不 fallback 到默认模板

**HTML vs 纯文本邮件的区别**：

| 对比项 | `MailService.sendEmail()`（纯文本） | `MailService.sendHtmlEmail()`（HTML） |
|--------|-------------------------------------|---------------------------------------|
| Spring Mail 类 | `SimpleMailMessage` | `MimeMessage` + `MimeMessageHelper` |
| 内容类型 | `text/plain` | `text/html`（第二个参数 `true`） |
| 模板支持 | 无 | Thymeleaf 渲染后填入 |
| 使用场景 | 简单通知邮件 | 包含样式、表格的正式通知书 |

---

### 3.4 `BidHandlerInfoController`

**文件**：`src/main/java/.../controller/bidhandlerinfo/BidHandlerInfoController.java`

**职责**：经办人 CRUD 端点（**6 端点已实现**），统一返回 `Result<T>` 包装。

**统一响应格式**（所有端点）：

```json
{
  "code": 200,
  "message": "成功消息",
  "data": { ...业务数据，可为 null / Diff<T> / PageInfo<T> }
}
```

- `code` 200 = 成功；400 = 参数错误；404 = 资源不存在
- `data` 删除类操作时为 `null`，PATCH update 端点时为 `Diff<BidHandlerInfo>`，列表查询时为 `PageInfo<BidHandlerInfo>`

#### 端点清单

| Method | Path | 用途 | 返回类型 | 状态 |
|--------|------|------|----------|------|
| POST | `/create-bid-handler-info` | 创建经办人 | `Result<BidHandlerInfo>` | ✅ |
| GET | `/bid-handler-info/{id}` | 按主键查询 | `Result<BidHandlerInfo>` | ✅ |
| **PATCH** | `/bid-handler-info/{id}` | 按主键**部分更新**（返回 diff） | `Result<Diff<BidHandlerInfo>>` | ✅ |
| DELETE | `/bid-handler-info/{id}` | 按主键删除 | `Result<Void>` | ✅ |
| GET | `/bid-handler-info` | 列表查询（PageHelper 分页） | `Result<PageInfo<BidHandlerInfo>>` | ✅ |
| POST | `/bid-handler-info/batch-delete` | 按主键 ID 列表批量删除 | `Result<List<BidHandlerInfo>>` | ✅（p3 升级版）|

**PATCH 语义（RFC 5789）**：
- 调用方传哪些字段，update 端点就更新哪些字段
- 未传的字段（`null`）在 DB 中保持原值，由 XML 动态 SQL（`<set> + <if test="xxx != null">`）实现
- 与 `BidWinningNotificationController` 风格保持一致

---

#### `POST /create-bid-handler-info`

| 项目 | 内容 |
|------|------|
| HTTP 方法 | POST |
| 完整 URL | `http://localhost:8080/create-bid-handler-info` |
| Content-Type | `application/json`（`@RequestBody` 要求） |
| 请求参数 | `BidHandlerInfoCreateRequest`（JSON body，3 字段含 `@NotBlank` / `@Size` 校验注解，**当前未加 @Valid**） |
| 返回值 | `Result<BidHandlerInfo>`（code=200，data=Entity） |

**调用链路**：

```
JSON (handlerName/companyName/departmentName)
  ↓ Jackson 反序列化
BidHandlerInfoCreateRequest
  ↓ 字段拷贝
BidHandlerInfo Entity
  ↓ service.create(entity) → mapper.insert() → XML <insert> → MySQL INSERT + 自增回填
  ↓ 包装为 Result.success(entity, "创建成功")
JSON Response
```

**响应示例**：

```json
{
  "code": 200,
  "message": "创建成功",
  "data": {"handlerId":1,"handlerName":"张三","companyName":"测试","departmentName":"采购部"}
}
```

---

#### `GET /bid-handler-info/{id}`

| 项目 | 内容 |
|------|------|
| HTTP 方法 | GET（无需 body） |
| 完整 URL | `http://localhost:8080/bid-handler-info/1` |
| 请求参数 | `@PathVariable Integer id` |
| 返回值 | `Result<BidHandlerInfo>`（code=200 成功 / code=404 记录不存在） |

**调用链路**：

```
URL {id} → @PathVariable 注入
  → service.getById(id) → mapper.selectById(id) → XML <select> → MySQL SELECT
  → entity 为 null → Result.error(404, "记录不存在")
  → entity 不为 null → Result.success(entity, "查询成功")
```

**响应示例**（成功）：

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {"handlerId":1,"handlerName":"张三","companyName":"测试","departmentName":"采购部"}
}
```

**响应示例**（不存在）：

```json
{"code":404,"message":"记录不存在","data":null}
```

---

#### `PATCH /bid-handler-info/{id}`（带 diff 逻辑，PATCH 语义）

| 项目 | 内容 |
|------|------|
| HTTP 方法 | **PATCH**（非 PUT） |
| 完整 URL | `http://localhost:8080/bid-handler-info/1` |
| Content-Type | `application/json` |
| 请求参数 | `@PathVariable Integer id` + `@RequestBody BidHandlerInfoUpdateRequest`（3 字段，仅 `@Size`，**无 `@NotBlank`**） |
| 返回值 | `Result<Diff<BidHandlerInfo>>`（code=200 成功 / code=404 记录不存在） |

**PATCH 流程**：

```text
// 1. 查原始 entity（diff.before 来源）
BidHandlerInfo before = service.getById(id);
if (before == null) return Result.error(404, "记录不存在");

// 2. 构造新 entity（id 来自 path，3 字段来自 body，null 字段不更新）
BidHandlerInfo after = new BidHandlerInfo();
after.setHandlerId(id);
after.setHandlerName(request.getHandlerName());    // 可能为 null
after.setCompanyName(request.getCompanyName());    // 可能为 null
after.setDepartmentName(request.getDepartmentName()); // 可能为 null

// 3. 调用 service 更新（动态 SQL 只更新非 null 字段）
int rows = service.update(after);
if (rows == 0) return Result.error(404, "记录不存在");

// 4. 包装 diff 返回
return Result.success(new Diff<>(before, after), "更新成功");
```

**调用链路**：

```
URL {id} → @PathVariable
  → service.getById(id)              ← diff.before 来源
  → service.update(newEntity)        ← diff.after 写入 DB（仅 SET 非 null）
  → mapper.updateById(entity)        → XML &lt;update&gt; 动态 SQL（&lt;set&gt; + &lt;if&gt;）
  → 包装 Diff<before, after>
JSON Response
```

**响应示例**：

```json
{
  "code": 200,
  "message": "更新成功",
  "data": {
    "before": {"handlerId":1,"handlerName":"张三","companyName":"原公司","departmentName":"原部门"},
    "after":  {"handlerId":1,"handlerName":"李四","companyName":"新公司","departmentName":"新部门"}
  }
}
```

**部分更新示例**（只传 1 个字段）：

```bash
curl -X PATCH http://localhost:8080/bid-handler-info/1 \
  -H "Content-Type: application/json" \
  -d '{"handlerName": "新名字"}'
```

响应：`data.before.handlerName = "张三"`, `data.after.handlerName = "新名字"`，其他字段保留 DB 原值。

---

#### `DELETE /bid-handler-info/{id}`

| 项目 | 内容 |
|------|------|
| HTTP 方法 | DELETE（无需 body） |
| 完整 URL | `http://localhost:8080/bid-handler-info/1` |
| 请求参数 | `@PathVariable Integer id` |
| 返回值 | `Result<Void>`（code=200 删除成功 / code=404 记录不存在，data 始终为 null） |

**调用链路**：

```
URL {id} → @PathVariable
  → service.delete(id) → mapper.deleteById(id) → SQL DELETE
  → rows = 0 → Result.error(404, "记录不存在")
  → rows = 1 → Result.success(null, "删除成功")
```

---

#### `GET /bid-handler-info`（列表查询 + PageHelper 分页）

| 项目 | 内容 |
|------|------|
| HTTP 方法 | GET（无需 body） |
| 完整 URL | `http://localhost:8080/bid-handler-info?page=1&size=10&sort=handler_id desc` |
| 请求参数 | `@RequestParam(defaultValue="1") int page` / `@RequestParam(defaultValue="3") int size` / `@RequestParam(defaultValue="handler_id desc") String sort` |
| 返回值 | `Result<PageInfo<BidHandlerInfo>>`（code=200 / code=400 参数错误） |

**PageHelper 实现**：

```text
try {
    PageHelper.startPage(page, size, sort);   // ThreadLocal 写入分页参数
    List<BidHandlerInfo> list = service.listAll();  // 触发 SQL，自动加 LIMIT
    return Result.success(new PageInfo<>(list), "查询成功");
} catch (Exception e) {
    return Result.error(400, "分页查询参数错误: " + e.getMessage());
} finally {
    PageHelper.clearPage();  // 关键：清理 ThreadLocal 防线程池污染
}
```

**调用链路**：

```
URL ?page=1&size=10&sort=handler_id desc
  → PageHelper.startPage(page, size, sort)   ← ThreadLocal 写入
  → service.listAll() → mapper.selectAll()   ← PageHelper 自动追加 LIMIT
  → PageInfo 包装（含总数/页数/当前页数据）
  → 包装为 Result.success(pageInfo, "查询成功")
  → finally { PageHelper.clearPage() }        ← 清理 ThreadLocal
JSON Response
```

**响应示例**：

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "total": 8,
    "list": [
      {"handlerId":8,"handlerName":"...","companyName":"...","departmentName":"..."},
      ...
    ],
    "pageNum": 1,
    "pageSize": 3,
    "pages": 3,
    "hasNextPage": true
  }
}
```

---

#### `POST /bid-handler-info/batch-delete`（批量删除，p3 升级版）

| 项目 | 内容 |
|------|------|
| HTTP 方法 | POST |
| 完整 URL | `http://localhost:8080/bid-handler-info/batch-delete` |
| Content-Type | `application/json`（`@RequestBody` + `@Valid` 触发校验） |
| 请求参数 | `BidHandlerInfoBatchDeleteRequest`（`{ "ids": [1, 2, 3] }`，`@NotEmpty` + `@Size(max=500)`） |
| 返回值 | `Result<List<BidHandlerInfo>>`（code=200，data=实际删除的实体列表） |

**调用链路**：

```
URL POST /bid-handler-info/batch-delete + JSON {ids:[1,2,3]}
  → @Valid 触发 @NotEmpty/@Size 校验
  → service.deleteByIds(ids)
    → mapper.selectByIds(ids)         ← 知道哪些 ID 真的存在
    → mapper.deleteByIds(existingIds) ← 真删
  → 返删掉的实体列表
  → 包装为 Result.success(deleted, "批量删除完成")
JSON Response
```

**响应示例**：

```json
{
  "code": 200,
  "message": "批量删除完成",
  "data": [
    {"handlerId": 2, "handlerName": "李四", "companyName": "...", "departmentName": "..."},
    {"handlerId": 3, "handlerName": "王五", "companyName": "...", "departmentName": "..."}
  ]
}
```

**p3 升级版说明**：
- 永远 200，**不**区分"全部不存在"与"部分不存在"
- `data` = 实际删除的**实体列表**（前端展示用，无需自己再查"哪些存在"）
- 不存在的 ID：不在 data 中，前端 diff 自己请求的 ids 即可知道 missing
- **多 1 次 SELECT**（PK 索引，毫秒级），换 response payload 实用性
- **不**做完整性校验 / **不**做级联 / **不**加事务
- 本表 `bid_handler_info` 无外键引用，p3 升级版无副作用
- DB 物理顺序（无 ORDER BY），最小实现

**详细设计**：见 §2.6 批量删除 DTO 模式

---

**待办**：
- ⚠️ `createBidHandlerInfo` / `patchBidHandlerInfo` 未加 `@Valid`，DTO 上 `@NotBlank` / `@Size` 暂未生效（待 `GlobalExceptionHandler` 一起补全）
- 统一异常处理（`GlobalExceptionHandler` 尚未实现）
- 分页 sort 参数未来可加白名单（防 SQL 注入 / 防字段拼错），当前透传给 PageHelper（依赖其解析）

---

### 3.5 `BidWinningNotificationController`

**文件**：`src/main/java/.../controller/bidwinningnotification/BidWinningNotificationController.java`

**职责**：定标通知 CRUD 端点（**6 端点已实现**），统一返回 `Result<T>` 包装。设计风格与 `BidHandlerInfoController` 完全一致。

**统一响应格式**：（同 §3.4）

#### 端点清单

| Method | Path | 用途 | 返回类型 | 状态 |
|--------|------|------|----------|------|
| POST | `/create-bid-winning-notification` | 创建定标通知 | `Result<BidWinningNotification>` | ✅ |
| GET | `/bid-winning-notification/{id}` | 按主键查询 | `Result<BidWinningNotification>` | ✅ |
| **PATCH** | `/bid-winning-notification/{id}` | 按主键**部分更新**（返回 diff） | `Result<Diff<BidWinningNotification>>` | ✅ |
| DELETE | `/bid-winning-notification/{id}` | 按主键删除 | `Result<Void>` | ✅ |
| GET | `/bid-winning-notification` | 列表查询（PageHelper 分页） | `Result<PageInfo<BidWinningNotification>>` | ✅ |
| POST | `/bid-winning-notification/batch-delete` | 按主键 ID 列表批量删除 | `Result<List<BidWinningNotification>>` | ✅（p3 升级版）|

#### 端点详解

**POST /create-bid-winning-notification**

| 项目 | 内容 |
|------|------|
| 请求 DTO | `BidWinningNotificationCreateRequest`（17 字段：3 必填 + 14 可选）|
| 调用链 | JSON → DTO → Entity（17 字段拷贝）→ service.create() → mapper.insert() → MySQL INSERT + 自增回填 `notificationId` → Result.success(entity, "创建成功") |
| 响应 | `Result<BidWinningNotification>`（code=200）|

**GET /bid-winning-notification/{id}**

| 项目 | 内容 |
|------|------|
| 请求参数 | `@PathVariable Integer id` |
| 调用链 | URL → @PathVariable → service.getById(id) → mapper.selectById(id) → MySQL SELECT → resultMap 映射 |
| 响应 | `Result<BidWinningNotification>`（code=200 成功 / code=404 不存在）|

**PATCH /bid-winning-notification/{id}**

| 项目 | 内容 |
|------|------|
| 请求 DTO | `BidWinningNotificationUpdateRequest`（17 字段，全部可选，无 `@NotBlank`/`@NotNull`）|
| 调用链 | URL → @PathVariable → service.getById() 取 before → 构造 newEntity（id + 17 字段）→ service.update() → mapper.updateById() → XML 动态 SQL（仅 SET 非 null）→ 包装 Diff |
| 响应 | `Result<Diff<BidWinningNotification>>`（code=200 / code=404）|

**DELETE /bid-winning-notification/{id}**

| 项目 | 内容 |
|------|------|
| 请求参数 | `@PathVariable Integer id` |
| 调用链 | URL → @PathVariable → service.delete(id) → mapper.deleteById(id) → MySQL DELETE |
| 响应 | `Result<Void>`（code=200 / code=404）|

**GET /bid-winning-notification**（列表分页）

| 项目 | 内容 |
|------|------|
| 请求参数 | `?pageNum=1&pageSize=10&sort=notification_id desc`（@RequestParam） |
| 调用链 | URL → PageHelper.startPage(pageNum, pageSize, sort) → service.listAll() → PageHelper 加 LIMIT + COUNT → PageInfo 包装 → finally { clearPage() } |
| 响应 | `Result<PageInfo<BidWinningNotification>>`（code=200 / code=400）|

**与 §3.4 的差异点**：

| 维度 | `BidHandlerInfoController` | `BidWinningNotificationController` |
|------|---------------------------|---------------------------------|
| 字段数 | 4 | 19 |
| URL 前缀 | `/bid-handler-info` | `/bid-winning-notification` |
| 业务复杂度 | 简单 CRUD | 含 6 个时间/金额/布尔字段 |
| PATCH 端点 | 3 字段部分更新 | 17 字段部分更新 |
| Mapper 复杂度 | 单表 4 字段 | 19 字段 + 动态 SQL |

#### `POST /bid-winning-notification/batch-delete`（批量删除，p3 升级版）

| 项目 | 内容 |
|------|------|
| HTTP 方法 | POST |
| 完整 URL | `http://localhost:8080/bid-winning-notification/batch-delete` |
| Content-Type | `application/json`（`@RequestBody` + `@Valid` 触发校验） |
| 请求参数 | `BidWinningNotificationBatchDeleteRequest`（`{ "ids": [1, 2, 3] }`，`@NotEmpty` + `@Size(max=500)`） |
| 返回值 | `Result<List<BidWinningNotification>>`（code=200，data=实际删除的实体列表） |

**调用链路**：

```
URL POST /bid-winning-notification/batch-delete + JSON {ids:[1,2,3]}
  → @Valid 触发 @NotEmpty/@Size 校验
  → service.deleteByIds(ids)
    → mapper.selectByIds(ids)         ← 知道哪些 ID 真的存在
    → mapper.deleteByIds(existingIds) ← 真删
  → 返删掉的实体列表
  → 包装为 Result.success(deleted, "批量删除完成")
JSON Response
```

**响应示例**：

```json
{
  "code": 200,
  "message": "批量删除完成",
  "data": [
    {"notificationId": 2, "projectNo": "WB20260325017", "handlerId": 1, ...},
    {"notificationId": 3, "projectNo": "WB20260325018", "handlerId": 1, ...}
  ]
}
```

**p3 升级版 + 逻辑外键注意**：
- 永远 200，**不**区分"全部不存在"与"部分不存在"
- `data` = 实际删除的**实体列表**（19 字段全查）
- **不**做完整性校验 / **不**做级联 / **不**加事务
- ⚠️ **逻辑外键孤儿风险**：`bid_winning_supplier_detail.notification_id` 是逻辑外键，删除本表后明细行会变孤儿。p3 升级版**不**做孤儿清理，由应用层负责（未来可加 Service 跨 Mapper 编排）
- DB 物理顺序（无 ORDER BY），最小实现

**详细设计**：见 §2.6 批量删除 DTO 模式

**待办**：
- ⚠️ `createBidWinningNotification` / `patchBidWinningNotification` 未加 `@Valid`，DTO 上 `@NotBlank` / `@Size` 暂未生效
- 统一异常处理（`GlobalExceptionHandler` 尚未实现）

---

### 3.6 `BidWinningSupplierDetailController`

**文件**：`src/main/java/.../controller/bidwinningsupplierdetail/BidWinningSupplierDetailController.java`

**职责**：中标结果明细 CRUD 端点（**6 端点已实现**），统一返回 `Result<T>` 包装。设计风格与 `BidHandlerInfoController` / `BidWinningNotificationController` 完全一致。

**端点清单**：

| Method | Path | 用途 | 返回类型 | 状态 |
|--------|------|------|----------|------|
| POST | `/create-bid-winning-supplier-detail` | 创建中标结果明细 | `Result<BidWinningSupplierDetail>` | ✅ |
| GET | `/bid-winning-supplier-detail/{id}` | 按主键查询 | `Result<BidWinningSupplierDetail>` | ✅ |
| **PATCH** | `/bid-winning-supplier-detail/{id}` | 按主键**部分更新**（返回 diff） | `Result<Diff<BidWinningSupplierDetail>>` | ✅ |
| DELETE | `/bid-winning-supplier-detail/{id}` | 按主键删除 | `Result<Void>` | ✅ |
| GET | `/bid-winning-supplier-detail` | 列表查询（PageHelper 分页） | `Result<PageInfo<BidWinningSupplierDetail>>` | ✅ |
| POST | `/bid-winning-supplier-detail/batch-delete` | 按主键 ID 列表批量删除 | `Result<List<BidWinningSupplierDetail>>` | ✅（p3 升级版）|

**与 §3.4/§3.5 的差异点**：

| 维度 | `BidHandlerInfoController` | `BidWinningNotificationController` | `BidWinningSupplierDetailController` |
|------|---------------------------|-----------------------------------|--------------------------------------|
| 字段数 | 4 | 19 | **4** |
| URL 前缀 | `/bid-handler-info` | `/bid-winning-notification` | **`/bid-winning-supplier-detail`** |
| PATCH 端点 | 3 字段部分更新 | 17 字段部分更新 | **3 字段部分更新** |
| Mapper 复杂度 | 单表 4 字段 | 19 字段 + 动态 SQL | **4 字段 + PATCH 动态 SQL** |

**待办**：
- ⚠️ 当前未加 `@Valid`，DTO 上 `@NotNull` / `@NotBlank` / `@Size` 暂未生效（待 `GlobalExceptionHandler` 一起补全）
- 分页 sort 参数未来可加白名单（防 SQL 注入 / 防字段拼错）
- 统一异常处理（`GlobalExceptionHandler` 尚未实现）

---

## 4. Service 层

> 业务 Service（`BidHandlerInfoService` / `BidWinningNotificationService` / `BidWinningSupplierDetailService`）统一继承 `BaseService<T>`，详见 [`docs/business-controllers.md`](business-controllers.md)。本节聚焦于邮件相关 Service。

### 4.0 `BaseService<T>` 通用 Service 基类

**文件**：`src/main/java/.../service/BaseService.java`

**设计目标**：消除 3 个业务 Service 的重复 CRUD 代码。封装 7 个标准方法，写方法统一加 `@Transactional(rollbackFor = Exception.class)`。

**继承链路**：
```
BaseService<T>
  ↑ extends
BidHandlerInfoService           — 经办人业务
BidWinningNotificationService   — 定标通知业务（重写 create 设 createdAt）
BidWinningSupplierDetailService — 中标明细业务
```

**调用链路**：
```
Controller → Service（BaseService 方法）→ Mapper（BaseMapper<T>）→ Mapper XML → MySQL
```

**子类职责**：只需实现 `getMapper()` 和 `getIdExtractor()` 两个抽象方法，标准 CRUD 全部继承。

### 4.1 `MailService`

**文件**：`src/main/java/.../service/mail/MailService.java`

> **类名说明**：本类名原计划为 `MailSender`，但因 Spring Boot `MailSenderPropertiesConfiguration` 自动注册的 `JavaMailSender` Bean 名为 `mailSender`（类型 `JavaMailSender`），同名会触发 `BeanDefinitionOverrideException`，故改名为 `MailService`。
>
> 类内部仍注入 `private JavaMailSender mailSender`（Spring Boot 自动配置的 SMTP 客户端）。

| 方法 | 输入 | 输出 | 第三方依赖 | 设计思路 |
|------|------|------|------------|----------|
| `sendEmail(PlainTextMailRequest request)` | `PlainTextMailRequest`（to/subject/content） | void（发送纯文本邮件） | `JavaMailSender`、`SimpleMailMessage`、`senderEmail`（配置值） | 纯文本邮件发送，强制使用配置中的发件人 |
| `sendHtmlEmail(String to, String subject, String htmlContent)` | to（收件人）、subject（主题）、htmlContent（HTML内容） | void（发送 HTML 邮件） | `JavaMailSender`、`MimeMessage`、`MimeMessageHelper`、`senderEmail` | HTML 邮件发送，使用 MimeMessageHelper 简化构造，强制使用配置发件人 |

**`sendEmail()` 内部流程**（纯文本，`SimpleMailMessage`）：

```text
public void sendEmail(PlainTextMailRequest request) {
    SimpleMailMessage message = new SimpleMailMessage();    // 1. 创建邮件消息对象
    message.setFrom(senderEmail);                            // 2. 强制使用配置发件人（防止 from 欺骗）
    message.setTo(request.getTo());                           // 3. 收件人
    message.setSubject(request.getSubject());                // 4. 主题
    message.setText(request.getContent());                   // 5. 纯文本内容
    mailSender.send(message);                                // 6. 通过 SMTP 发送
}
```

**`sendHtmlEmail()` 内部流程**（HTML，`MimeMessage` + `MimeMessageHelper`）：

```text
public void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
    MimeMessage mimeMessage = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
    helper.setFrom(senderEmail);
    helper.setTo(to);
    helper.setSubject(subject);
    helper.setText(htmlContent, true);  // 第二个参数 true = HTML 格式
    mailSender.send(mimeMessage);
}
```

**关键设计决策**：发件人固定使用 `senderEmail`（即 `spring.mail.username`），而非 `request.getFrom()`。原因：
1. 安全：防止发件人地址被伪造（与 SMTP 服务器授权用户不符会报错）
2. 统一：所有发出的邮件都从一个发件地址发出

**`JavaMailSender` 发送流程（底层原理）**：

```
mailSender.send(message)
  ↓
JavaMailSenderImpl.send(SimpleMailMessage | MimeMessage)
  ↓
Session.getInstance(props, auth)         ← 使用配置的 SMTP 属性（host/port/username/password）
  ↓
Transport.send(message)                  ← 通过 TCP socket 连接 smtp.qq.com:587
     ↓
     smtp.qq.com STARTTLS               ← 加密传输
     ↓
     AUTH LOGIN                          ← 使用 username/password 认证
     ↓
     MAIL FROM: <senderEmail>            ← 必须与认证用户一致
     ↓
     RCPT TO: <to>                       ← 收件人地址
     ↓
     DATA                                ← 邮件内容
     ↓
     QUIT
```

**为什么需要 `MimeMessage` + `MimeMessageHelper` 而不是 `SimpleMailMessage`？**

| 对比项 | `SimpleMailMessage` | `MimeMessage` + `MimeMessageHelper` |
|--------|---------------------|-------------------------------------|
| 内容类型 | 纯文本 | 可文本可HTML（`setText(text, true)`） |
| 附件支持 | 无 | 支持（multipart） |
| 内联图片 | 无 | 支持 |
| 多语言/编码 | 弱 | 强（可设置 charset） |
| 使用场景 | 简单邮件 | 正式 HTML 邮件（中标通知书含表格样式） |

**调用方**：
- `StatusCheckController.sendEmail()` → `MailService.sendEmail(request)`
- `StatusCheckController.sendNotification()` → `MailService.sendHtmlEmail(to, subject, htmlContent)`（异步子线程）
- `CrudResultEmailAspect`（AOP 切面） → `MailService.sendHtmlEmail(to, subject, htmlContent)`（异步子线程）

**`@Value` 注入原理**：`AutowiredAnnotationBeanPostProcessor`（实现了 `InstantiationAwareBeanPostProcessor` 接口）在 Bean 初始化后，扫描所有带有 `@Value` 的字段，从 Spring `Environment` 中读取对应的 `application.properties` 配置值，通过反射（`Field.setAccessible(true); field.set(this, value)`）注入。

---

### 4.2 `ResourceSearcher`

**文件**：`src/main/java/.../service/mail/ResourceSearcher.java`

| 方法 | 输入 | 输出 | 第三方依赖 | 设计思路 |
|------|------|------|------------|----------|
| `validateTemplateExists(String templateName)` | templateName（模板文件名，不含 .html 后缀） | void（验证通过则正常返回，失败则抛 `IllegalArgumentException`） | `ResourcePatternResolver`（Spring Core） | 精确搜索 `classpath:/templates/` 目录验证模板文件是否存在，为 `TemplateFiller` 提供模板存在性校验，不存在则抛异常（不 fallback） |

**`validateTemplateExists()` 内部流程**：

```text
public void validateTemplateExists(String templateName) {
    try {
        // 1. 扫描 classpath:/templates/*.html 下的所有模板文件
        Resource[] resources = resourcePatternResolver.getResources("classpath:/templates/*.html");
        //   ↓
        //   PathMatchingResourcePatternResolver.getResources(...)
        //     ↓
        //     ClassLoader.getResources("templates/")  ← 遍历 classpath 下所有 templates 目录

        // 2. 精确匹配
        boolean exists = Arrays.stream(resources)
                .map(this::extractTemplateName)              // 提取文件名（去 .html 后缀）
                .anyMatch(name -> name != null && name.equals(templateName));

        // 3. 找不到则抛异常，不 fallback
        if (!exists) {
            throw new IllegalArgumentException("模板不存在: " + templateName);
        }
    } catch (IOException e) {
        throw new IllegalArgumentException("模板搜索失败: " + templateName, e);
    } finally {
        System.out.println("模板搜索完成: " + templateName);
    }
}
```

**`extractTemplateName()` 内部流程**：

```text
private String extractTemplateName(Resource resource) {
    try {
        String path = resource.getURL().getPath();
        int idx = path.lastIndexOf("/templates/");
        if (idx < 0) return null;
        return path.substring(idx + "/templates/".length()).replace(".html", "");
    } catch (IOException e) {
        return null;
    }
}
```

**设计意图**：
- 使用 `ResourcePatternResolver` 而不是直接读取文件系统，确保在打包成 JAR 后仍能正确扫描
- **精确匹配**：不支持通配符或模糊匹配，必须文件名完全一致
- **行为安全**：模板不存在时直接抛 `IllegalArgumentException`，不 fallback 到默认模板

---

### 4.3 `TemplateFiller`

**文件**：`src/main/java/.../service/mail/TemplateFiller.java`

| 方法 | 输入 | 输出 | 第三方依赖 | 设计思路 |
|------|------|------|------------|----------|
| `processTemplate(BidWinningMailRequest request)` | `BidWinningMailRequest`（含 projectCode + handlerName + bidWinningList + **templateName**） | 渲染后的 HTML 字符串（String） | `SpringTemplateEngine`、`Context`、`ResourceSearcher` | 将 Thymeleaf 模板与动态数据结合，根据 `request.templateName` 动态选择模板文件，模板不存在则抛异常（不 fallback） |

**`processTemplate()` 内部流程**：

```text
public String processTemplate(BidWinningMailRequest request) {
    // Step 1: 读取用户传入的模板文件名（不含 .html 后缀）
    String templateName = request.getTemplateName();  // 例如 "bid-winning-notification2"

    // Step 2: 验证模板是否存在（精确匹配 classpath:/templates/*.html）
    resourceSearcher.validateTemplateExists(templateName);
    //    验证通过 → 继续
    //    验证失败 → 抛出 IllegalArgumentException，不 fallback

    // Step 3: 创建 Thymeleaf 上下文（数据容器）
    Context context = new Context();

    // Step 4: 放入模板变量
    context.setVariable("projectCode", request.getProjectCode());
    context.setVariable("handlerName", request.getHandlerName());
    context.setVariable("bidWinningList", request.getBidWinningList());

    // Step 5: 渲染模板
    return templateEngine.process(templateName, context);
}
```

**模板搜索与选择机制（完整链路）**：

```
用户传入 templateName: "bid-winning-notification2"
        ↓
ResourceSearcher.validateTemplateExists("bid-winning-notification2")
        ↓
扫描 classpath:/templates/*.html → 精确匹配文件名
        ↓
找到 → TemplateResolver 拼接路径 → classpath:/templates/bid-winning-notification2.html
        ↓
读取文件 → Thymeleaf AST 解析 → 渲染
        ↓
找不到 → 抛出 IllegalArgumentException（不 fallback，不使用默认模板）
```

**Thymeleaf 渲染原理（底层）**：

```
templateEngine.process(templateName, context)
  ↓
TemplateResolver.resolveTemplateName(templateName)
  → 拼接完整路径 classpath:/templates/{templateName}.html
  ↓
TemplateParser.parseTemplate()  ← 解析 HTML 为 AST（抽象语法树）
  → 遍历 AST 节点，遇到 th:text="${projectCode}" 节点
  → 遍历 AST 节点，遇到 th:each="item : ${bidWinningList}" 节点
  ↓
Dialect.execute()  ← 执行 OGNL/SpringEL 表达式从 Context 取值
  → context.getVariable("projectCode") → "WB20260325017"
  → bidWinningList 循环渲染每项 supplierName + bidWinningAmount
  ↓
OutputWriter.write(result)  ← 将替换后的 HTML 写入 String
  → 返回完整 HTML 字符串
```

**设计决策**：模板名由用户传入，实现动态模板选择；验证后不使用默认模板，保证用户传入的 `templateName` 始终受控。

**`SpringTemplateEngine` 的 Bean 来源**：`ThymeleafAutoConfiguration` 在容器启动时注册，单例模式。

---

### 4.4 MyBatis-Plus 持久化层（Mapper + XML）

> 详细端到端开发流程（从 DB 到 Controller）见 [`docs/development-flow.md`](development-flow.md)。本节重点说明 Mapper 与 XML 的设计。

**目录结构**：

```
src/main/
├── java/io/gitee/lujianshi/bidwinningbackend/
│   ├── entity/                          ← Java 实体（Plus 注解）
│   │   ├── BidHandlerInfo.java
│   │   ├── BidWinningNotification.java
│   │   └── BidWinningSupplierDetail.java
│   ├── mapper/                          ← Mapper 接口（手写签名）
│   │   └── BidHandlerInfoMapper.java
│   └── service/                          ← 业务层
│       └── BidHandlerInfoService.java
└── resources/
    ├── mapper/                           ← Mapper XML（SQL 独立）
    │   └── BidHandlerInfoMapper.xml
    └── sql/ddl/                          ← 建表脚本（Flyway 兼容命名，**当前未启用 Flyway**）
        ├── V1__create_handler_info.sql
        └── ...
```

**完整链路（7 层自下而上）**：

```
DB 表（建表脚本，Flyway 兼容命名）
  ↓
Entity（@TableName + @TableId）
  ↓
Mapper 接口（手写方法签名，**不继承 BaseMapper**）
  ↓
Mapper XML（namespace 绑定接口，**Java 与 SQL 分离**）
  ↓
Service（注入 Mapper 接口，**不继承 IService**）
  ↓
DTO（@NotBlank 校验）
  ↓
Controller（@Valid 触发校验）
```

**关键设计原则**：

| 决策 | 选择 | 理由 |
|------|------|------|
| Mapper 继承 `BaseMapper` | ❌ 不继承 | Java/SQL 分离，扩展性强 |
| `@Mapper` 注解 | ❌ 不写 | 启动类 `@MapperScan` 统一扫描 |
| Service 继承 `IService` | ❌ 不继承 | 与 Mapper 风格统一，手写 |
| Entity 加 Plus 注解 | ✅ `@TableName` + `@TableId` | 配合启动类 + Mapper 自动映射 |
| SQL 写在 XML | ✅ 必选 | 坚持 Java/SQL 分离 |
| XML 位置 | `resources/mapper/*.xml` | Maven 编译范围外，是资源文件 |
| `namespace` | = Mapper 接口全限定名 | 关键约束，错一位全废 |

**完整链路实例（POST /create-bid-handler-info）**：

1. Postman POST `{handlerName, companyName, departmentName}`
2. Spring MVC 路由 → `BidHandlerInfoController.createBidHandlerInfo()`
3. ⚠️ **当前未加 `@Valid`**，DTO 上的 `@NotBlank` / `@Size` 校验暂未生效（待 `GlobalExceptionHandler` 一起补全）
4. Jackson 反序列化 → `BidHandlerInfoCreateRequest`
5. 字段拷贝：DTO → Entity
6. `BidHandlerInfoService.create(entity)`
7. `BidHandlerInfoMapper.insert(entity)`（**Spring 注入的代理实现**）
8. `BidHandlerInfoMapper.xml <insert>` 标签执行 SQL
9. MySQL 写入 + 自增 ID 回填
10. Jackson 序列化 entity → JSON 返回

**`@MapperScan` 启动类配置**：

```text
@SpringBootApplication
@MapperScan("io.gitee.lujianshi.bidwinningbackend.mapper")
public class BidWinningBackendApplication { ... }
```

**MyBatis-Plus 关键配置**（`application-dev.yml`）：

```yaml
mybatis-plus:
  mapper-locations: classpath:mapper/*.xml    # XML 位置
  type-aliases-package: io.gitee.lujianshi.bidwinningbackend.entity  # 别名
  configuration:
    map-underscore-to-camel-case: true         # 字段映射
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # 打印 SQL
  global-config:
    banner: false
    db-config:
      id-type: auto                             # 主键自增策略
```

**详见**：

- [`docs/development-flow.md`](development-flow.md) — 端到端开发流程详解
- [`docs/database.md`](database.md) — 数据库结构与 Entity 映射
- `entity/BidHandlerInfo.java` — Entity 类注释（链路位置）
- `mapper/BidHandlerInfoMapper.java` — Mapper 接口注释（设计原则）
- `resources/mapper/BidHandlerInfoMapper.xml` — XML 头部注释（SQL 约束）

---

## 5. 工具类

### 5.1 `AsyncTaskManager`

**文件**：`src/main/java/.../utils/AsyncTaskManager.java`

| 方法 | 输入 | 输出 | 线程模型 | 设计思路 |
|------|------|------|----------|----------|
| `execute(Runnable task, long delayMillis)` | `task`（任务逻辑）、`delayMillis`（延迟时间） | void（立即返回） | 每次调用创建 1 个新线程 | 轻量级异步工具，非 Spring Bean，Thread + Runnable 模式 |

**`execute()` 内部流程**：

```text
public static void execute(Runnable task, long delayMillis) {
    new Thread(() -> {                              // 1. 创建新线程（Lambda 实现 Runnable）
        try {
            Thread.sleep(delayMillis);               // 2. 子线程睡眠 delayMillis 毫秒（不阻塞主线程）
            task.run();                               // 3. 睡眠结束后执行 task.run()（业务逻辑）
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();      // 4. InterruptedException 处理
            throw new RuntimeException(e);            //   - 恢复中断状态
        }                                            //   - 转换为未检查异常上抛
        } finally {
            System.out.println("异步任务执行完成: delayMillis=" + delayMillis);
        }                                            // 5. finally 兜底日志（无论成功失败都执行）
    }).start();                                      // 6. 启动线程，立即返回（不等待子线程执行）
}
```

**为什么 `start()` 后立即返回？**

`Thread.start()` 的语义是"启动线程并立即返回"，JVM 会异步调度新线程执行 `run()` 方法。调用方主线程不受影响，继续执行后续代码。

**`InterruptedException` 处理说明**：

```
主线程调用 execute() 后继续执行
    ↓
new Thread 子线程执行 Thread.sleep(delayMillis)
    ↓
如果主线程调用 Thread.interrupt() 或线程被中断
    ↓
Thread.sleep() 抛出 InterruptedException
    ↓
catch 块：Thread.currentThread().interrupt()
    → 将线程的中断标志设为 true（JVM 规范要求的正确处理方式）
    → throw new RuntimeException(e)
    → 上抛异常通知调用方
```

**与 `@Async` 的区别**：

| 对比项 | `AsyncTaskManager.execute()` | `@Async` |
|--------|-----------------------------| --------|
| 管理方式 | 每次调用创建新线程 | 委托给线程池（`SimpleAsyncTaskExecutor`） |
| 延迟执行 | 支持（通过 `Thread.sleep()`） | 不支持延迟，需要手动 sleep |
| Spring 集成 | 无（纯 Java 标准库） | 需要 `@EnableAsync` + Spring AOP 代理 |
| 线程复用 | 无（每次新线程，有创建开销） | 线程池复用 |
| 是否阻塞调用方 | 否（立即返回） | 否（立即返回） |

**生产环境建议**：高频调用时改用 `ExecutorService` 线程池，避免大量线程创建开销。

---

## 6. 配置与模板

### 6.1 多环境配置（application.yml 系列）

**文件结构**：

```
src/main/resources/
├── application.yml       # 公共配置 + 默认 profile = dev
├── application-dev.yml   # 开发环境
├── application-test.yml  # 测试环境
└── application-prod.yml  # 生产环境
```

**公共配置**（`application.yml`）：

```yaml
spring:
  application:
    name: bid-winning-backend
  thymeleaf:
    prefix: classpath:/templates/
    suffix: .html
  profiles:
    active: dev   # 默认启动 dev 环境
```

#### 6.1.1 各环境配置

| Profile | 端口 | 日志路径 | 备注 |
|---------|------|----------|------|
| `dev`（开发） | `8080` | `logs/app.log` | 默认环境，明文密码，QQ SMTP |
| `test`（测试） | `8080` | `logs/test.log` | 日志级别 DEBUG，密码用 `${TEST_MAIL_PASSWORD:default}` 占位符 |
| `prod`（生产） | `80` | `logs/prod.log` | 日志级别 INFO，敏感信息全部走环境变量（`${PROD_MAIL_PASSWORD}`） |

#### 6.1.2 配置示例（dev）

```yaml
server:
  address: 0.0.0.0
  port: 8079

custom:
  url: https://baidu.com

spring:
  datasource:
    url: ${MYSQL_URL}        # 环境变量注入（必填，详见 §6.4）
    username: ${MYSQL_USERNAME}
    password: ${MYSQL_PASSWORD}
  mail:
    host: smtp.qq.com
    port: 587
    username: 1649681371@qq.com       # dev 明文（开发效率优先）
    password: xhixpbmbkfyhchbb       # dev 明文
    default-encoding: UTF-8
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
            required: true

logging:
  file:
    name: logs/app.log
```

#### 6.1.3 环境切换方式

**方式 1：默认 profile（推荐）**
修改 `application.yml` 中的 `spring.profiles.active` 切换。

**方式 2：启动时指定**
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=test
```

**方式 3：环境变量**
```bash
export SPRING_PROFILES_ACTIVE=prod
./mvnw spring-boot:run
```

#### 6.1.4 关键配置项对照

| 配置项 | dev | test | prod |
|--------|-----|------|------|
| `server.port` | `8080` | `8080` | `80` |
| `spring.datasource.url` | `${MYSQL_URL}` | `${MYSQL_URL}` | `${MYSQL_URL}` |
| `spring.datasource.username` | `${MYSQL_USERNAME}` | `${MYSQL_USERNAME}` | `${MYSQL_USERNAME}` |
| `spring.datasource.password` | `${MYSQL_PASSWORD}` | `${MYSQL_PASSWORD}` | `${MYSQL_PASSWORD}` |
| `spring.mail.username` | `1649681371@qq.com` | `${TEST_MAIL_USERNAME:...}` | `${PROD_MAIL_USERNAME}` |
| `spring.mail.password` | `xhixpbmbkfyhchbb`（明文） | `${TEST_MAIL_PASSWORD:...}`（占位符） | `${PROD_MAIL_PASSWORD}`（环境变量，强制） |
| `custom.url` | `https://baidu.com` | `https://test.example.com` | `https://prod.example.com` |
| `logging.level.root` | `INFO`（默认） | `DEBUG` | `INFO` |

**关于 SMTP 授权码**：授权码是 QQ 邮箱为第三方客户端提供的专用密码，不能用 QQ 登录密码代替。授权码在 QQ 邮箱设置 → 账户 → POP3/SMTP服务 中生成。

**安全建议**：生产环境 `application-prod.yml` 应加入 `.gitignore`，或提交到独立私有仓库，敏感信息通过环境变量/CI 注入。

---

### 6.2 `pom.xml` 关键依赖

**文件**：`pom.xml`

| 依赖 | 版本 | 传递引入的核心类库 | 作用 |
|------|------|-------------------|------|
| `spring-boot-starter-webmvc` | 由 parent 管理 | spring-webmvc + spring-context + tomcat-embed | Web 框架 + Servlet 容器 |
| `spring-boot-starter-mail` | 由 parent 管理 | spring-context-support + jakarta.mail | 邮件发送支持 |
| `spring-boot-starter-thymeleaf` | 由 parent 管理 | spring-boot-starter-thymeleaf + thymeleaf-spring6 | 模板引擎 |
| `fastjson2` | 2.0.52 | 阿里 JSON 序列化库 | JSON 处理（额外引入，本项目未直接使用） |
| `lombok` | 由 parent 管理 | 无（编译时注解处理） | 自动生成 getter/setter |
| `copy_down` | 1.1 | io.github.furstenheim:copy_down | HTML → Markdown 转换（**已下线**，早期 `MailLoggingService` 使用） |

**依赖传递链**：

```
spring-boot-starter-mail
  ├─ spring-boot-starter → spring-boot → spring-boot-autoconfigure
  ├─ spring-context-support → JavaMailSender
  └─ jakarta.mail-api → MimeMessage / MimeMessageHelper

spring-boot-starter-webmvc
  ├─ spring-webmvc → DispatcherServlet / HandlerMapping / HandlerAdapter
  ├─ spring-context → BeanFactory / ApplicationContext / @Autowired
  └─ tomcat-embed-core → 嵌入式 Tomcat
```

---

### 6.3 `bid-winning-notification.html` 模板

**文件**：`src/main/resources/templates/bid-winning-notification.html`

**Thymeleaf 变量**：

| 模板变量 | 数据来源 | 占位写法 | 最终效果 |
|---------|----------|----------|----------|
| `${projectCode}` | `BidWinningMailRequest.projectCode` | `<span th:text="${projectCode}">000001</span>` | 渲染后显示具体项目编号 |
| `${handlerName}` | `BidWinningMailRequest.handlerName` | `<span th:text="${handlerName}">张三</span>` | 渲染后显示具体经办人 |

**设计决策**：使用 `<span th:text="${var}">默认值</span>` 而不是 `<input th:value="${var}">`：
1. `<input>` 元素在 Outlook 等邮件客户端中渲染效果差
2. `<span>` 是行内元素，渲染自然
3. `th:text` 覆盖标签内文本内容，标签本身不会显示

**为什么使用 `<table>` 布局**：HTML 邮件客户端（Outlook、Apple Mail 等）对 CSS float/flexbox 支持不一致，使用 `<table>` 做布局是最稳妥的兼容性方案。

---

### 6.4 环境变量配置（外置化敏感信息）

**12-Factor App 原则**：配置与代码分离。敏感信息（DB 密码、邮件授权码）不应写入代码仓库，应通过环境变量在启动时注入。

#### 6.4.1 占位符解析顺序

Spring 解析 `${XXX}` 占位符时按以下顺序查找：

```
1. OS 环境变量（IDEA Run Configuration / Shell `export`）
   ↓
2. JVM 系统属性（-D 参数，如 -DMYSQL_USERNAME=jianshi）
   ↓
3. application-*.yml 中显式设置的值
   ↓
4. application-*.yml 中 ${XXX:default} 的默认值
```

**匹配规则**：Spring 把环境变量名转 `MYSQL_USERNAME` → 找 `myql.username` / `MYSQL_USERNAME`（大小写不敏感，下划线/点互转）。

#### 6.4.2 占位符清单

| 占位符 | 必填场景 | 用途 | 典型值 |
|--------|----------|------|--------|
| `${MYSQL_URL}` | dev/test/prod | JDBC URL | `jdbc:mysql://10.0.0.100:3306/bid-winning?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true` |
| `${MYSQL_USERNAME}` | dev/test/prod | DB 用户名 | `jianshi` |
| `${MYSQL_PASSWORD}` | dev/test/prod | DB 密码 | — |
| `${PROD_MAIL_USERNAME}` | prod | 发件邮箱 | `service@company.com` |
| `${PROD_MAIL_PASSWORD}` | prod | 邮箱授权码 | — |
| `${PROD_MAIL_HOST}` | prod（可选，有默认 `smtp.qq.com`） | SMTP 主机 | `smtp.exmail.qq.com` |
| `${PROD_MAIL_PORT}` | prod（可选，有默认 `587`） | SMTP 端口 | `465` |

> 完整清单与启动方式详见 `AGENTS.md` → "环境变量配置" 章节。

#### 6.4.3 注入方式

| 方式 | 适用场景 | 示例 |
|------|----------|------|
| IDEA Run Configuration | 本地开发 | Run → Edit Configurations → Environment variables |
| Shell `export` | CLI 启动 | `export MYSQL_PASSWORD=xxx && ./mvnw spring-boot:run` |
| JVM `-D` 系统属性 | CLI 启动 | `./mvnw spring-boot:run -DMYSQL_PASSWORD=xxx` |
| Docker / K8s Secrets | 容器化部署 | `env: MYSQL_PASSWORD_FILE: /run/secrets/db_password` |

#### 6.4.4 安全红线

- ❌ **不要**把生产密码 commit 到 yml
- ❌ **不要**把 `.env` / `.env.properties` 文件 commit（已加 `.gitignore`）
- ✅ **必须**用占位符 + 外部注入
- ⚠️ dev 环境可临时明文（开发效率优先），prod **强制**走环境变量
- ⚠️ `allowPublicKeyRetrieval=true` 仅适用于 dev/test，**prod 应配 `useSSL=true` + 正确证书**

#### 6.4.5 常见错误速查

| 报错 | 根因 | 解决 |
|------|------|------|
| `Access denied for user '${MYSQL_USERNAME}'@'10.0.0.1'` | 占位符没解析（env var 名拼错/未设） | 检查 IDEA 配置中的 env var 名 |
| `Public Key Retrieval is not allowed` | MySQL 8 + `caching_sha2_password` + SSL 关闭 | URL 加 `&allowPublicKeyRetrieval=true` |
| `Failed to determine a suitable driver class` | yml 没配 `spring.datasource` | 补 `application-{profile}.yml` 的 datasource 段 |

---

## 7. AOP 切面（业务操作后邮件通知）

### 7.1 为什么需要 AOP（横切关注点 Cross-cutting Concerns）

邮件通知是典型的**横切关注点**：
- 18 个业务 Controller 端点都需要"操作成功后发邮件"
- 如果写在每个 Controller，会重复 18 段
- AOP 切面统一处理：Controller 业务代码 **0 改动**，只需贴一个 `@SendEmailOnSuccess` 注解

**其他横切场景**（可参考本类扩展）：
- 操作日志记录（`@Before` + MethodSignature）
- 接口限流（`@Around` + Redis）
- 敏感数据脱敏（`@AfterReturning` + 处理 Result.data）
- 分布式锁（`@Around`）

### 7.2 为什么 `@SendEmailOnSuccess` 注解能自动拦截

**注解本身只是"静态标记"（元数据），没有任何执行能力**。生效靠 **Spring AOP 动态代理机制** + **Bean 生命周期**：

1. **收集标记**：Spring Boot 启动扫描所有类
   - 看到 `@Aspect` → 知道 `CrudResultEmailAspect` 是切面
   - 看到 `pointcut = "@annotation(anno)"` → 记下规则："谁的方法上贴了 `@SendEmailOnSuccess`，就拦截谁"
2. **生成代理对象（"秘书"）**：Spring 看到 Controller 方法贴了注解 → 在内存里动态生成 Controller 的代理对象
3. **偷梁换柱**：HTTP 请求实际打到"秘书"上，不是原始"老板"
4. **拦截执行**：
   - "秘书"让"老板"执行核心业务（落库 + 组装 Result）
   - "老板"返回 Result → "秘书"看到标签 → 触发 `@AfterReturning` 切面逻辑（发邮件）
   - "秘书"把 Result 返回给前端

### 7.3 注解设计要点

| 元注解 | 取值 | 原因 |
|--------|------|------|
| `@Target(ElementType.METHOD)` | METHOD | 限制只能贴方法（编译期 IDE 拦截误用）|
| `@Retention(RetentionPolicy.RUNTIME)` | **RUNTIME** | **必须 RUNTIME**，否则 Spring AOP 反射读不到（CLASS/SOURCE 都会丢失）|

**为什么 RUNTIME 必需**：
- CLASS：编译进 `.class` 但 JVM 加载时丢弃 → AOP 失效
- SOURCE：编译器直接丢弃 → AOP 失效
- 只有 RUNTIME 才能让 Spring 通过 `Method.getAnnotation(...)` 读取到注解实例

### 7.4 完整业务执行生命周期（5 步精确时序 + 主/子线程分离）

主线程与异步线程**分离**的完整链路（**核心理解：AOP 切面在主线程中执行，但邮件发送在后台子线程异步执行**）：

```
┌─ 主线程（HTTP 请求处理）─────────────────────────────────────┐
│                                                              │
│ Step 1. Controller 内部业务执行完毕，构造 Result 对象，return │
│         → service.create(entity) 落库                        │
│         → 组装 Result<Entity> 对象                            │
│         → 走到 return（此时 Result 已构造，<b>还没</b>返回前端）│
│                                                              │
│ Step 2. 触发 @AfterReturning 切面（主线程<b>未</b>放行）       │
│         ┌─ 2.1 切面前半段（同步，~微秒级）                     │
│         │    → Spring AOP 注入 3 个参数：                    │
│         │       JoinPoint / Object result / SendEmailOnSuccess│
│         │    → instanceof Result<?> 验证                      │
│         │    → 反射推断 endpoint/operation                    │
│         │    → fastjson2 序列化 Result.data                  │
│         │    → 构造邮件主题 subject                           │
│         │                                                    │
│         └─ 2.2 切面后半段：AsyncTaskManager.execute(...) 提交  │
│              ├─ 主线程：new Thread(runnable).start()         │
│              │   ├─ 构造 Thread 对象（微秒级）                │
│              │   └─ 调用 .start()（<b>立即返回</b>，不等待子线程）│
│              │   → 主线程这行代码"瞬间"完成                    │
│              │                                                │
│              └─ 后台子线程<b>已启动</b>（与主线程并行）        │
│                  └─ 此时主线程继续往下走                       │
│                                                              │
│ Step 3. 切面方法全部执行完毕（handleAfterReturning return）     │
│                                                              │
│ Step 4. Spring AOP 代理对象放行                              │
│         → Spring MVC 拿到原始 Result 对象                    │
│         → Jackson/fastjson 序列化为 JSON                     │
│         → DispatcherServlet 写入 HTTP Response               │
│         → TCP 发送回前端                                     │
│         → <b>此时接口响应完成</b>（前端收到 200 OK + JSON）   │
│                                                              │
│ === 主线程生命周期结束（HTTP 请求处理完毕）===                │
└──────────────────────────────────────────────────────────────┘

┌─ 后台子线程（与主线程 Step 3-4 <b>并行</b>）─────────────────┐
│                                                              │
│ Step 5. 后台子线程并行执行（sleep(0) + 邮件发送全流程）         │
│         ├─ 子线程从 Thread.sleep(0) 醒来（几乎立即）          │
│         ├─ 5.1 模板存在性校验                                │
│         ├─ 5.2 构造 Thymeleaf Context                         │
│         ├─ 5.3 SpringTemplateEngine.process() 渲染 HTML        │
│         ├─ 5.4 MailService.sendHtmlEmail() SMTP 发送 │
│         ├─ try-catch 兜底（失败仅日志告警）                    │
│         └─ 子线程生命周期结束                                │
│                                                              │
│ 关键：步骤 5 可能在主线程 Step 4 <b>之后</b>才完成              │
│      → 前端<b>不感知</b>邮件是否发送成功                      │
│      → 子线程异常被隔离，不影响主线程的 Result 返回           │
└──────────────────────────────────────────────────────────────┘
```

**关键时序要点**：

| 要点 | 说明 |
|------|------|
| **Step 2.2 非阻塞** | `new Thread(...).start()` 内部仅构造 Thread 对象（微秒级）+ 调用 .start()（立即返回），主线程这行代码 ≈ 微秒级完成，**不会**等待子线程 |
| **Step 5 异步并行** | 后台子线程可能还要花 1-3 秒完成 SMTP 发送，此时前端已经收到 200 OK 响应 |
| **异常隔离** | 子线程异常被 try-catch 捕获 → 仅日志告警 → 不会反向传播到主线程 → 不会导致 HTTP 500 |
| **用户感知** | 前端调用接口后只看到 `Result.success(...)`，**不感知**邮件是否发送成功，邮件是后台"附带"的副作用 |

**对比同步 vs 异步**：

| 模式 | 主线程耗时 | 邮件失败影响 | 接口响应 |
|------|----------|------------|---------|
| 同步发送（不用 AsyncTaskManager） | 1-3 秒（被 SMTP 阻塞）| HTTP 500 风险 | 慢 |
| **异步发送（当前实现）** | **~微秒级** | **仅日志告警** | **快（200 OK）** |

### 7.5 5 步传参与验证（同步，主线程）

代理对象调用 `handleAfterReturning(JoinPoint, Object, SendEmailOnSuccess)` 传入 3 个参数：

| # | 参数 | 来源 | 验证/使用 |
|---|------|------|-----------|
| 1 | `JoinPoint joinPoint` | Spring AOP 自动注入 | `MethodSignature` + `Method` 反射推断 endpoint |
| 2 | `Object result` | `returning = "result"` 绑定 | `instanceof Result<?>` 验证（防御性编程）|
| 3 | `SendEmailOnSuccess anno` | 反射读取方法注解 | `anno.to()` / `anno.subject()` / `anno.templateName()` |

**5 步同步处理**：
1. `instanceof Result<?>` 验证（弱化强类型依赖）
2. 反射提取 method 签名，推断 endpoint/operation
3. fastjson2 序列化 `Result.data` 为 JSON
4. 构造 subject（注解覆盖 or 默认格式）
5. `AsyncTaskManager.execute(...)` 提交异步任务

### 7.6 4 步异步发送（工作线程，0 ms 延迟）

异步任务内部独立 try-catch 兜底：

1. `ResourceSearcher.validateTemplateExists()` 模板存在性校验
2. 构造 Thymeleaf Context 注入变量（subject/endpoint/operation/timestamp/result*）
3. `SpringTemplateEngine.process(templateName, context)` 渲染 HTML
4. `MailService.sendHtmlEmail(to, subject, html)` SMTP 发送

### 7.7 技术栈协同

| 技术 | 角色 |
|------|------|
| **Spring AOP / AspectJ** | 横向拦截（`@Aspect` + `@AfterReturning`）|
| **Java 反射** | 动态获取方法元信息（`MethodSignature`）|
| **fastjson2** | 实体转 JSON 字符串（`JSON.toJSONString`）|
| **Thymeleaf** | 模板引擎渲染 HTML（`SpringTemplateEngine`）|
| **AsyncTaskManager** | 异步线程池（`AsyncTaskManager.execute`）|
| **JavaMailSender** | SMTP 协议发邮件（`MailService`）|

### 7.8 关键设计规范（防坑指南）

| 规范 | 原因 |
|------|------|
| **必须 try-catch 兜底** | 切面代码绝对不能因自身报错导致主业务回滚；邮件失败就失败日志告警 |
| **耗时操作必须异步** | SMTP 网络 I/O 耗时 1-3 秒；同步会拖慢接口响应 |
| **防代理失效** | 只有 Spring 注入对象调用方法时 AOP 才生效；类内部 A→B 调用 B 上的注解不会触发（跳过了"秘书"）|
| **弱化强类型依赖** | `instanceof Result<?>` 校验避免 ClassCastException；不要预设拦截方法一定返回某类型 |
| **参数名绑定** | `@AfterReturning(argNames = "joinPoint, result, anno")` 让编译器字节码保留形参名 |

### 7.9 完整文件清单

| # | 路径 | 角色 |
|---|------|------|
| 1 | `annotation/SendEmailOnSuccess.java` | 注解（贴在方法上）|
| 2 | `aspect/CrudResultEmailAspect.java` | 切面（拦截注解方法）|
| 3 | `templates/crud-result-notification.html` | 邮件 HTML 模板 |
| 4 | `BidWinningBackendApplication.java` | 启动类加 `@EnableAspectJAutoProxy` |
| 5 | 3 业务 Controller × 6 端点 | 贴 `@SendEmailOnSuccess(to = "ops@company.com")` |
| 6 | `pom.xml` | 加 `org.aspectj:aspectjweaver`（Spring Boot 4 没有 starter-aop）|

---

## 附录：关键术语表

| 术语 | 全称 | 解释 |
|------|------|------|
| IOC | Inversion of Control | 控制反转，Spring 容器负责 Bean 的创建和依赖注入 |
| AOP | Aspect-Oriented Programming | 面向切面编程，@Async 基于 AOP 代理实现 |
| Servlet | Java Servlet API | Java Web 标准接口，处理 HTTP 请求 |
| MIME | Multipurpose Internet Mail Extensions | 互联网邮件扩展格式，支持 HTML 邮件 |
| Thymeleaf | Thymeleaf Template Engine | Spring Boot 默认模板引擎 |
| STARTTLS | STARTTLS SMTP Extension | SMTP 命令，用于在明文连接上升级为 TLS 加密 |
| DTO | Data Transfer Object | 数据传输对象，用于层间数据传递 |
| OGNL | Object-Graph Navigation Language | 对象图导航语言，Thymeleaf 表达式求值引擎之一 |
| Bean Post Processor | BeanPostProcessor | Spring 框架钩子，在 Bean 创建/销毁前后执行拦截 |
| DispatcherServlet | DispatcherServlet | Spring MVC 前端控制器，所有 HTTP 请求的入口 |
| HandlerMapping | HandlerMapping | URL 到 Controller 方法的映射注册表 |
| HandlerAdapter | HandlerAdapter | 调用 Controller 方法并处理返回值的适配器 |
