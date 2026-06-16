# 端到端开发流程（从 DB 到 Controller）

> 本文档以 BidHandlerInfo 为例，完整记录"从数据库表到暴露给前端的 Controller"每一层的开发逻辑、产物、调用关系和命名规范。
>
> **对外 API 契约**：前端联调请查阅 [`docs/api.md`](api.md)（22 端点完整文档）。本文档聚焦于**开发流程与模式**视角。

---

## 1. 完整链路总览

```
┌─────────────────────────────────────────────────────────────────────┐
│  ① 数据库表层（MySQL 物理表）                                       │
│     ↓                      
│  ② Entity 层（Java POJO + Plus 注解）                              │
│     ↓  Spring 注入 Mapper 接口                                      │
│  ③ Mapper 接口层（手写方法签名）                                    │
│     ↓  Spring 注入 XML 映射的代理实现                              │
│  ④ Mapper XML 层（SQL 独立于 Java 之外）                            │
│     ↓  Service 注入 Mapper 接口调用                                │
│  ⑤ Service 层（业务逻辑编排）                                       │
│     ↓  Controller 接收 DTO 后转 Entity 传入                        │
│  ⑥ DTO 层（HTTP 请求载体 + 校验注解）                               │
│     ↓  Spring MVC 路由匹配                                          │
│  ⑦ Controller 层（HTTP 入口）                                       │
│     ↓  Jackson 序列化响应 → 返回前端                                │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 2. 各层职责与产物

### ① 数据库表层

**位置**：`src/main/resources/sql/ddl/V*.sql`

**职责**：定义表结构、字段、约束、索引。SQL 脚本采用 **Flyway 兼容的版本号命名**（V1__xxx.sql），便于未来切换到 Flyway / Liquibase 等迁移工具时无需改名。

**注意**：当前**未实际引入 Flyway**，仅借用其命名规范展示规范性。表结构需 DBA 或开发者**手动执行** `sql/ddl/` 下的脚本。未来如需自动化迁移，可引入 Flyway（命名已兼容，无需重命名脚本）。

**本例产物**：

### ② Entity 层

**位置**：`src/main/java/.../entity/BidHandlerInfo.java`

**职责**：Java 类与数据库表的**一一映射**。一个 Entity = 一张表。

**关键注解**：
- `@TableName("bid_handler_info")` — 显式声明表名
- `@TableId(type = IdType.AUTO)` — 主键自增
- `@Data` / `@NoArgsConstructor` / `@AllArgsConstructor`（Lombok 三件套）

**本例产物**：
```java
@Data @NoArgsConstructor @AllArgsConstructor
@TableName("bid_handler_info")
public class BidHandlerInfo {
    @TableId(type = IdType.AUTO)
    private Integer handlerId;
    private String handlerName;
    private String companyName;
    private String departmentName;
}
```

**注意**：
- 字段名是 Java 驼峰 `handlerId`
- DB 列名是下划线 `handler_id`
- yml 配 `map-underscore-to-camel-case: true` 自动映射（XML `<resultMap>` 也可显式声明）

### ③ Mapper 接口层

**位置**：`src/main/java/.../mapper/BidHandlerInfoMapper.java`

**职责**：**只定义方法签名，无实现**。SQL 实现在 XML。

**关键约定**（坚持 Java 与 SQL 分离）：
- **不继承** `BaseMapper<T>`（不用 Plus 自动 CRUD）
- **写** `@Mapper` 注解（显式标注，与启动类 `@MapperScan` 双重保险）
- 启动类 `@MapperScan("io.gitee.lujianshi.bidwinningbackend.mapper")` 统一扫描注册
- import 只保留业务需要的类

**本例产物**：
```java
@Mapper
public interface BidHandlerInfoMapper {
    int insert(BidHandlerInfo entity);
    int updateById(BidHandlerInfo entity);
    int deleteById(Integer handlerId);
    BidHandlerInfo selectById(Integer handlerId);
    List<BidHandlerInfo> selectAll();
}
```

**方法命名规范**：
- `insert` / `updateById` / `deleteById` / `selectById` / `selectAll`

### ④ Mapper XML 层

**位置**：`src/main/resources/mapper/BidHandlerInfoMapper.xml`

**职责**：每个 Mapper 接口方法对应一个 SQL 标签。

**关键约束**：
- `namespace` 必须等于 Mapper 接口的全限定名（**一个字符都不能错**）
- 每个 `<insert>` / `<update>` / `<delete>` / `<select>` 标签的 `id` = Mapper 接口方法名
- `<resultMap>` 定义 Java 字段 ↔ DB 列映射
- `useGeneratedKeys="true" keyProperty="handlerId"` 让自增 ID 回填到 entity

**本例产物**（简化）：
```xml
<mapper namespace="io.gitee.lujianshi.bidwinningbackend.mapper.BidHandlerInfoMapper">

    <resultMap id="BaseResultMap" type="BidHandlerInfo">
        <id     column="handler_id"      property="handlerId"/>
        <result column="handler_name"    property="handlerName"/>
        <result column="company_name"    property="companyName"/>
        <result column="department_name" property="departmentName"/>
    </resultMap>

    <insert id="insert" parameterType="BidHandlerInfo"
            useGeneratedKeys="true" keyProperty="handlerId">
        INSERT INTO bid_handler_info (handler_name, company_name, department_name)
        VALUES (#{handlerName}, #{companyName}, #{departmentName})
    </insert>

    <!-- ... 其他 update/select/insert/delete 标签 ... -->
</mapper>
```

**MyBatis-Plus 配置**（`application-dev.yml`）：
```yaml
mybatis-plus:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: io.gitee.lujianshi.bidwinningbackend.entity
  configuration:
    map-underscore-to-camel-case: true
```

**为什么 XML 放 `resources/` 而非 `src/main/java/`？**
- Maven 编译范围：只编译 `src/main/java/` 下的 `.java`
- XML 是资源文件，由 Maven `<resources>` 阶段原样拷贝到 classpath
- yml 中 `classpath:mapper/*.xml` 才能找到

### ⑤ Service 层

**位置**：`src/main/java/.../service/bidhandlerinfo/BidHandlerInfoService.java`

**职责**：业务逻辑编排。当前只做 CRUD 转发，未来业务逻辑（分组、聚合、复杂条件查询）放这里。

**关键约定**（与 Mapper 风格一致：手写、不继承 Plus）：
- **不继承** `IService<T>`
- **不继承** `ServiceImpl<Mapper, T>`
- 直接 `@Autowired` 注入 Mapper 接口
- Spring 容器在启动时，根据 `@MapperScan` 扫描到 Mapper 接口，并加载 XML 生成代理实现，注入到 Service

**本例产物**：
```java
@Service
public class BidHandlerInfoService {

    @Autowired
    private BidHandlerInfoMapper mapper;

    public int create(BidHandlerInfo entity) {
        return mapper.insert(entity);
    }
    // ... 其他 CRUD 方法 ...
}
```

### ⑥ DTO 层

**位置**：`src/main/java/.../dto/bidhandlerinfo/BidHandlerInfoCreateRequest.java`

**职责**：HTTP 请求/响应的载体，与 Entity 分离。

**为什么分离？**
| DTO | Entity |
|-----|--------|
| HTTP 请求/响应载体 | 数据库映射 |
| 业务字段含义 | 物理表字段 |
| 加 `@NotBlank` / `@Size` 校验 | 不加校验 |
| 字段可业务定制 | 字段固定（对应表列） |
| 不直接暴露给前端（除非要回显） | 不直接暴露给前端 |

**本例产物**：
```java
@Data
public class BidHandlerInfoCreateRequest {

    @NotBlank(message = "经办人姓名不能为空")
    @Size(max = 50, message = "经办人姓名长度不能超过50")
    private String handlerName;

    @NotBlank(message = "所属公司不能为空")
    @Size(max = 100, message = "公司名称长度不能超过100")
    private String companyName;

    @NotBlank(message = "所属部门不能为空")
    @Size(max = 100, message = "部门名称长度不能超过100")
    private String departmentName;
}
```
### ⑦ Controller 层

**位置**：`src/main/java/.../controller/bidhandlerinfo/BidHandlerInfoController.java`

**职责**：HTTP 入口。接收请求 → 校验 → 转换 DTO 到 Entity → 调用 Service → 返回 `Result<T>` 包装。

**RESTful 路由**：

| Method | Path | 作用 | 状态 |
|--------|------|------|------|
| POST | `/create-bid-handler-info` | 创建 | ✅ 已实现 |
| GET | `/bid-handler-info/{id}` | 按 ID 查询 | ✅ 已实现 |
| **PATCH** | `/bid-handler-info/{id}` | 按 ID **部分更新**（带 diff） | ✅ 已实现 |
| DELETE | `/bid-handler-info/{id}` | 按 ID 删除 | ✅ 已实现 |
| GET | `/bid-handler-info` | 列表查询（PageHelper 分页） | ✅ 已实现 |
| POST | `/bid-handler-info/batch-delete` | 按 ID 列表批量删除（p3 升级版，data 返实体列表） | ✅ 已实现 |

**统一返回格式**：所有端点返 `Result<T>`（`code`/`message`/`data`），具体见 `docs/architecture.md` §2.4。

**本例产物**（实际代码，简化）：

```java
@RestController
public class BidHandlerInfoController {

    @Resource
    private BidHandlerInfoService service;

    // 创建
    @PostMapping("/create-bid-handler-info")
    public Result<BidHandlerInfo> createBidHandlerInfo(@RequestBody BidHandlerInfoCreateRequest request) {
        BidHandlerInfo entity = new BidHandlerInfo();
        entity.setHandlerName(request.getHandlerName());
        entity.setCompanyName(request.getCompanyName());
        entity.setDepartmentName(request.getDepartmentName());
        service.create(entity);
        return Result.success(entity, "创建成功");
    }

    // 查询
    @GetMapping("/bid-handler-info/{id}")
    public Result<BidHandlerInfo> getByIdBidHandlerInfo(@PathVariable Integer id) {
        BidHandlerInfo entity = service.getById(id);
        return entity != null
                ? Result.success(entity, "查询成功")
                : Result.error(404, "记录不存在");
    }

    // 更新（PATCH 语义，动态 SQL）
    @PatchMapping("/bid-handler-info/{id}")
    public Result<Diff<BidHandlerInfo>> patchBidHandlerInfo(
            @PathVariable Integer id,
            @RequestBody BidHandlerInfoUpdateRequest request) {
        BidHandlerInfo before = service.getById(id);
        if (before == null) return Result.error(404, "记录不存在");

        BidHandlerInfo after = new BidHandlerInfo();
        after.setHandlerId(id);
        after.setHandlerName(request.getHandlerName());
        after.setCompanyName(request.getCompanyName());
        after.setDepartmentName(request.getDepartmentName());

        int rows = service.update(after);
        if (rows == 0) return Result.error(404, "记录不存在");

        return Result.success(new Diff<>(before, after), "更新成功");
    }

    // 删除
    @DeleteMapping("/bid-handler-info/{id}")
    public Result<Void> deleteBidHandlerInfo(@PathVariable Integer id) {
        int rows = service.delete(id);
        return rows > 0
                ? Result.success(null, "删除成功")
                : Result.error(404, "记录不存在");
    }

    // 列表查询（PageHelper 分页）
    @GetMapping("/bid-handler-info")
    public Result<PageInfo<BidHandlerInfo>> getAllBidHandlerInfo(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "3") int size,
            @RequestParam(defaultValue = "handler_id desc") String sort) {
        try {
            PageHelper.startPage(page, size, sort);
            List<BidHandlerInfo> list = service.listAll();
            return Result.success(new PageInfo<>(list), "查询成功");
        } catch (Exception e) {
            return Result.error(400, "分页查询参数错误: " + e.getMessage());
        } finally {
            PageHelper.clearPage();
        }
    }
}
```

**`@Valid` 触发校验**（**当前未启用，待补全**）：DTO 字段上的 `@NotBlank` / `@Size` 需要在 Controller 方法参数上加 `@Valid` 才生效。当前 `createBidHandlerInfo` / `updateBidHandlerInfo` **未加 `@Valid`**，校验注解暂未生效（待 `GlobalExceptionHandler` 一起补全）。校验失败时预期抛 `MethodArgumentNotValidException`，由 `GlobalExceptionHandler` 统一处理返回 400 + 错误信息。

**注入风格**：使用 `jakarta.annotation.Resource` 字段注入（Spring 6+ 推荐），非 `org.springframework.beans.factory.annotation.Autowired`。

---

## 3. 完整调用链（以 POST /create-bid-handler-info 为例）

```
Postman
  ↓ POST /create-bid-handler-info {handlerName, companyName, departmentName}
Spring DispatcherServlet
  ↓ 路由匹配 → BidHandlerInfoController.createBidHandlerInfo()
  ↓ ⚠️ 当前未加 @Valid，DTO 上的 @NotBlank/@Size 暂未生效
Jackson
  ↓ 反序列化 JSON → BidHandlerInfoCreateRequest
Controller
  ↓ 字段拷贝：DTO → Entity（手动 setXxx）
  ↓ BidHandlerInfoService.create(entity)
  ↓ BidHandlerInfoMapper.insert(entity)
  ↓ Spring 注入的 Mapper 代理实现（由 XML 加载生成）
  ↓ BidHandlerInfoMapper.xml <insert> 标签
  ↓ MyBatis 解析 #{handlerName} 等占位符
  ↓ MySQL JDBC Driver
  ↓ INSERT INTO bid_handler_info ...
  ↓ 自增 ID 回填 entity.handlerId
  ↓ (返回影响行数 1)
Controller
  ↓ 包装为 Result.success(entity, "创建成功")
Jackson
  ↓ 序列化 Result → JSON
Postman
  ↓ 收到 {code:200, message:"创建成功", data:{handlerId:1, handlerName:"张三", ...}}
```

**6 个端点差异点速查**：

| 端点 | 入口 | 调用 Service | 包装 Result |
|------|------|------------|-------------|
| POST | @RequestBody DTO | `create(entity)` | `Result.success(entity, "创建成功")` |
| GET | @PathVariable id | `getById(id)` | `Result.success(entity, "查询成功")` / `Result.error(404, ...)` |
| **PATCH** | @PathVariable id + @RequestBody DTO | `getById(id)` + `update(after)` | `Result.success(new Diff(before, after), "更新成功")` |
| DELETE | @PathVariable id | `delete(id)` | `Result.success(null, "删除成功")` / `Result.error(404, ...)` |
| GET (list) | @RequestParam page/size/sort | `listAll()` (PageHelper 拦截) | `Result.success(pageInfo, "查询成功")` / `Result.error(400, ...)` |
| **POST (batch)** | @RequestBody XxxBatchDeleteRequest | `deleteByIds(ids)` | `Result.success(deleted, "批量删除完成")`（p3 升级版：data=删掉的实体列表）|

---

## 4. 各层关键文件清单

| 层 | 路径 | 说明 |
|---|------|------|
| ① DB 脚本 | `src/main/resources/sql/ddl/V1~V3*.sql` | 建表脚本（Flyway 兼容命名，**当前未启用 Flyway**） |
| ② Entity | `src/main/java/.../entity/BidHandlerInfo.java` | Java ↔ DB 映射 |
| ③ Mapper 接口 | `src/main/java/.../mapper/BidHandlerInfoMapper.java` | 方法签名 |
| ④ Mapper XML | `src/main/resources/mapper/BidHandlerInfoMapper.xml` | SQL 实现 |
| ⑤ Service | `src/main/java/.../service/bidhandlerinfo/BidHandlerInfoService.java` | 业务编排 |
| ⑥ DTO | `src/main/java/.../dto/bidhandlerinfo/BidHandlerInfoCreateRequest.java` | 请求载体 |
| ⑦ Controller | `src/main/java/.../controller/bidhandlerinfo/BidHandlerInfoController.java` | HTTP 入口 |

---

## 5. 复制模式：扩展到其他表

按照 BidHandlerInfo 的 7 层结构，**复制**即可实现 BidWinningNotification、BidWinningSupplierDetail：

### BidWinningNotification（已实现，参考样板）

| 步骤 | 复用点 | 当前状态 |
|------|--------|----------|
| ① DB 脚本 | V4 已有 | ✅ |
| ② Entity | 已有 `BidWinningNotification`（19 字段 + @NotBlank/@NotNull）| ✅ |
| ③ Mapper 接口 | 复制 `BidHandlerInfoMapper.java` 改名 | ✅（含 @Mapper 注解）|
| ④ Mapper XML | 复制 `BidHandlerInfoMapper.xml` 改名 + 改 SQL | ✅（含 PATCH 动态 SQL `<set> + <if>`）|
| ⑤ Service | 复制 `BidHandlerInfoService.java` 改名 | ✅ |
| ⑥ DTO | 复制 `BidHandlerInfoCreateRequest.java` 改名 + 17 字段 | ✅（CreateRequest + UpdateRequest）|
| ⑦ Controller | 复制 `BidHandlerInfoController.java` 改名 + 5 端点 | ✅ |

**BidWinningNotification vs BidHandlerInfo 差异**：
- 字段数 19 vs 4（含 6 个时间/金额/布尔字段）
- URL 前缀 `/bid-winning-notification` vs `/bid-handler-info`
- PATCH 端点 17 字段 vs 3 字段
- DTO 分 2 个（CreateRequest 必填 + UpdateRequest PATCH）
- Service 仍是 5 简单 CRUD 转发

**Controller 复制要点**：
- URL 前缀从 `/bid-handler-info` 改为业务相关
- 复用 `Result<T>` / `Result<Diff<T>>` / `Result<PageInfo<T>>` 包装
- 复用 `Result.success(...)` / `Result.error(400/404, "...")` 工厂
- 列表查询用 `PageHelper.startPage` + `clearPage`（finally 必加）

### BidWinningSupplierDetail（已实现）

| 步骤 | 复用点 | 当前状态 |
|------|--------|----------|
| ① DB 脚本 | V5 已有 | ✅ |
| ② Entity | 已有 `BidWinningSupplierDetail`（4 字段最简）| ✅ |
| ③ Mapper 接口 | 复制 `BidWinningNotificationMapper.java` 改名 | ✅（含 `deleteByIds` + `selectByIds`，共 7 方法）|
| ④ Mapper XML | 复制 `BidWinningNotificationMapper.xml` 改名 + 改 SQL | ✅（含 PATCH 动态 SQL `<set> + <if>`）|
| ⑤ Service | 复制 `BidWinningNotificationService.java` 改名 | ✅（6 方法：create/update/delete/getById/listAll/deleteByIds）|
| ⑥ DTO | 复制 `BidWinningNotificationCreateRequest.java` 改名 + 3 字段 | ✅（CreateRequest + UpdateRequest + BatchDeleteRequest）|
| ⑦ Controller | 复制 `BidWinningNotificationController.java` 改名 | ✅（6 端点）|

---

## 6. 批量删除模式（p3 升级版 + 每端点独立 DTO + data 返实体列表）

> 适用范围：所有需要按主键 ID 列表删除的 Controller（当前已实现：`BidHandlerInfoController` / `BidWinningNotificationController` / `BidWinningSupplierDetailController`）

### 6.1 4 层代码模板（按从上到下顺序）

#### ① Mapper 接口

```java
@Mapper
public interface XxxMapper {
    // ... 其他 5 个方法 ...
    int deleteByIds(@Param("ids") List<Integer> ids);
    List<Xxx> selectByIds(@Param("ids") List<Integer> ids);
}
```

#### ② Mapper XML

```xml
<delete id="deleteByIds" parameterType="java.util.List">
    DELETE FROM xxx
    WHERE pk_column IN
    <foreach collection="ids" item="id" open="(" close=")" separator=",">
        #{id}
    </foreach>
</delete>

<select id="selectByIds" parameterType="java.util.List" resultMap="BaseResultMap">
    SELECT 所有字段
    FROM xxx
    WHERE pk_column IN
    <foreach collection="ids" item="id" open="(" close=")" separator=",">
        #{id}
    </foreach>
</select>
```

#### ③ Service（p3 升级版 — 返删掉的实体列表）

```java
public List<Xxx> deleteByIds(List<Integer> ids) {
    if (ids == null || ids.isEmpty()) {
        return List.of();
    }
    // 1. SELECT 存在的行（PK 索引，毫秒级）
    List<Xxx> existing = mapper.selectByIds(ids);
    // 2. 删掉它们
    if (!existing.isEmpty()) {
        List<Integer> existingIds = existing.stream()
                .map(Xxx::getId)   // 或 getXxxId()
                .toList();
        mapper.deleteByIds(existingIds);
    }
    // 3. 返回删掉的实体行（前端展示用）
    return existing;
}
```

#### ④ Controller

```java
@PostMapping("/xxx/batch-delete")
public Result<List<Xxx>> batchDeleteXxx(@Valid @RequestBody XxxBatchDeleteRequest request) {
    List<Xxx> deleted = service.deleteByIds(request.getIds());
    return Result.success(deleted, "批量删除完成");
}
```

### 6.2 DTO 命名规范（每端点独立）

**命名模式**：`<EntityName>BatchDeleteRequest`（实体名 + "BatchDelete" + "Request"）

| 实体 | DTO | Controller 端点 |
|------|-----|---------------|
| `BidHandlerInfo` | `BidHandlerInfoBatchDeleteRequest` | `POST /bid-handler-info/batch-delete` |
| `BidWinningNotification` | `BidWinningNotificationBatchDeleteRequest` | `POST /bid-winning-notification/batch-delete` |
| `BidWinningSupplierDetail` | `BidWinningSupplierDetailBatchDeleteRequest`（**已启用**） | `POST /bid-winning-supplier-detail/batch-delete`（✅ 已实现） |

**DTO 结构**（3 个文件内容完全相同，但**类型独立**）：

```java
@Data
public class XxxBatchDeleteRequest {
    @NotEmpty(message = "ids 不能为空")
    @Size(max = 500, message = "单次批量删除不能超过 500 条")
    private List<Integer> ids;
}
```

**不复用 DTO 的理由**：

| 风险 | 说明 |
|------|------|
| 端点耦合 | 改一个端点的 DTO 会影响所有共用 DTO 的端点 |
| OpenAPI/Swagger 失真 | 共用 DTO 导致 schema 合并成 1 个，前端 codegen 失真 |
| 端点独立演化 | 部分端点可能加 `cascade: true`、`reason: "audit"` 等端点专属字段 |
| 命名误导 | 共用 DTO 的名字带"BatchDelete"会暗示绑定特定语义 |
| 阿里规范 | 《Java 开发手册》"推荐每 API 入口对应独立 DTO" |

### 6.3 响应格式（p3 升级版 — data 放实体列表）

| 场景 | code | message | data |
|------|------|---------|------|
| 任意 ids（部分/全部/全空） | 200 | "批量删除完成" | `List<Entity>`（实际删除的实体列表，DB 物理顺序） |

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

### 6.4 设计取舍（p3 升级版）

| 决策 | 理由 |
|------|------|
| ✅ 返实体列表 | data = `List<Entity>`，前端展示更友好，无需自己再查"哪些存在" |
| ✅ 多 1 次 SELECT | `selectByIds` 先查存在行，PK 索引，毫秒级；500 条以内几乎无感 |
| ✅ 删存在行 | `deleteByIds(existingIds)`，不依赖 SELECT 的"无副作用"假设 |
| ❌ 事务封装 | 不加 `@Transactional`；罕见 race 自洽：data 返的行 = 实际删的行（数量一致）|
| ❌ 级联删除 | 不做；应用层不保证外键关系 |
| ❌ 完整性校验 | 不做；MySQL IN 自然忽略不存在的 ID |
| ❌ 排序 | 不加 `ORDER BY`；DB 物理顺序，最小实现 |
| ❌ 返回 `failed[]` | 不返；前端 diff 自己请求的 ids 即可 |
| ❌ 共享 DTO | 避免端点间耦合，符合阿里规范"每 API 入口对应独立 DTO" |

**race condition 自洽说明**：
- 如果 SELECT 后到 DELETE 前有人删了某行 X，SELECT 已包含 X（因为 SELECT 之前 X 存在）
- DELETE 时 X 已被别人删了，DELETE 不会重复删
- data 返回的 X 行**仍然有效**（X 的数据是我们 SELECT 时拿到的）
- 不会出现"data 返 5 行但实际删 4 行"这种不一致

### 6.5 未来扩展点（升级路径）

- **升级 A**：引入 `PartialResult<T>` 通用 DTO（多 1 次 SELECT，返回 successful[] + failed[]），省去前端 diff
- **升级 B**：对有外键引用的表，加 Service 跨 Mapper 编排（先删明细再删主表，加 `@Transactional`）
- **升级 C**：加异步批处理（任务队列拆分 1000+ 的大批量删除）
- **升级 D**：某端点专属字段（如 `cascade: true`），直接在对应 XxxBatchDeleteRequest 加字段

详见 [docs/architecture.md §2.6 批量删除 DTO 模式](architecture.md#26-批量删除-dto-模式每端点独立-dto--data-返实体列表)。

---

## 7. 后续待办

- [x] 复制模式实现 `BidWinningSupplierDetail` 完整链路（Service / DTO / Controller，6 端点含批量删除 p3 升级版）
- [x] AOP 切面（`@SendEmailOnSuccess` + `CrudResultEmailAspect` + crud-result 模板）— 详见 [docs/architecture.md §7](architecture.md#7-aop-切面业务操作后邮件通知)
- [ ] 新增 `GlobalExceptionHandler` 统一处理 `MethodArgumentNotValidException`（含 `@Valid` 校验失败的 400 响应）
- [ ] 给所有 Controller 端点加 `@Valid` 注解（当前未启用，DTO 上 `@NotBlank` / `@Size` 校验失效）
- [ ] 新增 `MybatisPlusConfig`（分页插件 + 自动填充处理器）
- [ ] 单元测试（Mapper / Service / Controller）
- [x] API 文档（`docs/api.md`）

---

## 8. AOP 切面开发模式（4 步标准开发）

> 适用范围：所有需要**横切关注点**（Cross-cutting Concerns）的功能，包括：
> - 操作日志记录、邮件通知、短信通知
> - 接口限流、敏感数据脱敏、分布式锁
> - 接口耗时统计、统一异常处理

### 8.1 何时用 AOP（适用场景判断）

当你发现某段逻辑（如日志、鉴权、缓存、通知）**散落在多个 Controller 或 Service** 中，且代码**高度重复**时，就该用切面。

**反例**（不要用 AOP）：
- 核心业务逻辑（算钱、扣库存）→ 写在 Service
- 一次性逻辑（仅 1 个端点用到）→ 写在 Controller

### 8.2 标准 4 步开发流程

#### Step 1：定义注解（打标签）

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)   // 必须 RUNTIME
public @interface SendEmailOnSuccess {
    String to();
    String subject() default "";
    String templateName() default "crud-result";
}
```

**关键**：`@Retention(RetentionPolicy.RUNTIME)` 必填，否则 Spring AOP 反射读不到。

#### Step 2：定义切面类（写逻辑）

```java
@Aspect
@Component
public class CrudResultEmailAspect {
    @AfterReturning(
        pointcut = "@annotation(anno)",     // 拦截带注解的方法
        returning = "result",                 // 绑定返回值
        argNames = "joinPoint, result, anno"  // 形参名（编译期保留）
    )
    public void handleAfterReturning(JoinPoint joinPoint, Object result,
                                     SendEmailOnSuccess anno) {
        // 切面逻辑
    }
}
```

#### Step 3：确定增强时机（Advice）

| Advice | 时机 | 典型场景 |
|--------|------|---------|
| `@Before` | 方法执行前 | 鉴权、参数校验 |
| `@AfterReturning` | 方法正常返回后 | 邮件通知、成功日志 |
| `@AfterThrowing` | 方法抛异常后 | 异常告警收集 |
| `@Around` | 环绕（最强）| 耗时统计、分布式锁、限流 |

**本项目用 `@AfterReturning`**，因为邮件通知仅在成功时发。

#### Step 4：启动类开启 AOP

```java
@SpringBootApplication
@EnableAsync
@EnableAspectJAutoProxy   // ← 新增
@MapperScan("...")
public class BidWinningBackendApplication { }
```

### 8.3 5 步传参与验证流程（以 CrudResultEmailAspect 为例）

代理对象调用 `handleAfterReturning(JoinPoint, Object, SendEmailOnSuccess)` 传入 3 个参数：

| # | 参数 | 验证/使用 |
|---|------|----------|
| 1 | `JoinPoint joinPoint` | `MethodSignature` + `Method` 反射推断 endpoint |
| 2 | `Object result` | `instanceof Result<?>` 验证（防御性编程）|
| 3 | `SendEmailOnSuccess anno` | `anno.to()` / `anno.subject()` / `anno.templateName()` |

### 8.4 主线程 / 后台子线程分离的精确时序（以 CrudResultEmailAspect 为例）

**核心理解**：AOP 切面方法在主线程中执行（~微秒级），但实际的副作用（邮件发送）在后台子线程中执行（1-3 秒）。

```
┌─ 主线程（HTTP 请求处理）──────────────────────────────────┐
│ Step 1. Controller 业务执行完毕，return Result             │
│ Step 2. 触发 @AfterReturning 切面（主线程<b>未</b>放行）    │
│   ├─ 2.1 切面前半段（~微秒级）                              │
│   │   → instanceof 验证 + 反射推断 + JSON 序列化 + 构造主题 │
│   └─ 2.2 AsyncTaskManager.execute(...)                     │
│       → 主线程仅 new Thread + .start()，<b>瞬间</b>完成     │
│ Step 3. 切面方法全部执行完毕（return）                      │
│ Step 4. Spring 代理对象放行                                │
│   → Spring MVC 序列化 Result 为 JSON                       │
│   → TCP 发送回前端 → <b>此时接口响应完成</b>                │
└──────────────────────────────────────────────────────────┘

┌─ 后台子线程（与主线程 Step 3-4 <b>并行</b>）────────────┐
│ Step 5. sleep(0) + 邮件发送全流程                          │
│   → 可能在主线程 Step 4 <b>之后</b>才完成                 │
│   → 前端<b>不感知</b>邮件是否发送成功                      │
└──────────────────────────────────────────────────────────┘
```

**为什么这样设计**：

| 模式 | 主线程耗时 | 接口响应 |
|------|----------|---------|
| 同步发送 | 1-3 秒（SMTP 阻塞主线程）| 慢 |
| **异步发送（当前实现）** | **~微秒级**（仅 new Thread）| **快（200 OK）** |

**关键：Step 2.2 的 `AsyncTaskManager.execute(...)` 是非阻塞调用**
- 内部执行 `new Thread(runnable).start()`
- 构造 Thread 对象（微秒级）+ 调用 .start()（**立即返回**，不等待子线程）
- 主线程这行代码 ≈ 微秒级完成 → 继续往下走

---

### 8.5 防坑指南（核心设计规范）

| 规范 | 原因 |
|------|------|
| **try-catch 兜底** | 切面代码**绝对不能**因自身报错导致主业务回滚 |
| **耗时操作异步化** | SMTP / HTTP / DB 等网络 I/O 必须丢线程池 |
| **防代理失效** | 类内部 A→B 调用，B 上的注解**不会**触发切面（跳过了"秘书"）|
| **弱化强类型** | `instanceof` 校验避免 ClassCastException |
| **argNames 绑定** | 编译期字节码保留形参名，让 Spring 能正确注入 |

### 8.6 完整文件清单（参考实现）

| # | 路径 | 角色 |
|---|------|------|
| 1 | `annotation/SendEmailOnSuccess.java` | 注解 |
| 2 | `aspect/CrudResultEmailAspect.java` | 切面 |
| 3 | `templates/crud-result-notification.html` | 邮件模板 |
| 4 | `BidWinningBackendApplication.java` | 加 `@EnableAspectJAutoProxy` |
| 5 | 3 业务 Controller × 6 端点 | 贴 `@SendEmailOnSuccess` |
| 6 | `pom.xml` | 加 `org.aspectj:aspectjweaver` |

**Spring Boot 4 注意**：`spring-boot-starter-aop` starter 在 SB4 已**不存在**，需直接引 `aspectjweaver`（parent BOM 4.0.6 管理版本）。

详细执行链路 / 5 步传参 / 技术栈协同：见 [docs/architecture.md §7](architecture.md#7-aop-切面业务操作后邮件通知)
