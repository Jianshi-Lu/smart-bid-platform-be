# 端到端开发流程（从 DB 到 Controller）

> 本文档以 BidHandlerInfo 为例，完整记录"从数据库表到暴露给前端的 Controller"每一层的开发逻辑、产物、调用关系和命名规范。

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
- **不写** `@Mapper` 注解（启动类 `@MapperScan` 统一扫描）
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

**位置**：`src/main/java/.../service/BidHandlerInfoService.java`

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

**位置**：`src/main/java/.../dto/BidHandlerInfoCreateRequest.java`

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

**位置**：`src/main/java/.../controller/BidHandlerInfoController.java`

**职责**：HTTP 入口。接收请求 → 校验 → 转换 DTO 到 Entity → 调用 Service → 返回响应。

**RESTful 路由**：

| Method | Path | 作用 | 状态 |
|--------|------|------|------|
| POST | `/create-bid-handler-info` | 创建 | ✅ 已实现 |
| GET | `/bid-handler-info/{id}` | 按 ID 查询 | ⏳ 待实现 |
| GET | `/bid-handler-info` | 查全部 | ⏳ 待实现 |
| PUT | `/bid-handler-info/{id}` | 按 ID 更新 | ⏳ 待实现 |
| DELETE | `/bid-handler-info/{id}` | 按 ID 删除 | ⏳ 待实现 |

**本例产物**（实际代码）：
```java
@RestController
public class BidHandlerInfoController {

    @Resource
    private BidHandlerInfoService service;

    @PostMapping("/create-bid-handler-info")
    public BidHandlerInfo createBidHandlerInfo(@RequestBody BidHandlerInfoCreateRequest request) {
        BidHandlerInfo entity = new BidHandlerInfo();
        entity.setHandlerName(request.getHandlerName());
        entity.setCompanyName(request.getCompanyName());
        entity.setDepartmentName(request.getDepartmentName());
        service.create(entity);
        return entity;
    }
}
```

**`@Valid` 触发校验**（**当前未启用，待补全**）：DTO 字段上的 `@NotBlank` / `@Size` 需要在 Controller 方法参数上加 `@Valid` 才生效。当前 `createBidHandlerInfo` **未加 `@Valid`**，校验注解暂未生效（待 `GlobalExceptionHandler` 一起补全）。校验失败时预期抛 `MethodArgumentNotValidException`，由 `GlobalExceptionHandler` 统一处理返回 400 + 错误信息。

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
  ↓ 直接返回 entity 对象（含回填的 handlerId）
Jackson
  ↓ 序列化 entity → JSON
Postman
  ↓ 收到 {handlerId:1, handlerName:"张三", ...}
```

---

## 4. 各层关键文件清单

| 层 | 路径 | 说明 |
|---|------|------|
| ① DB 脚本 | `src/main/resources/sql/ddl/V1~V3*.sql` | 建表脚本（Flyway 兼容命名，**当前未启用 Flyway**） |
| ② Entity | `src/main/java/.../entity/BidHandlerInfo.java` | Java ↔ DB 映射 |
| ③ Mapper 接口 | `src/main/java/.../mapper/BidHandlerInfoMapper.java` | 方法签名 |
| ④ Mapper XML | `src/main/resources/mapper/BidHandlerInfoMapper.xml` | SQL 实现 |
| ⑤ Service | `src/main/java/.../service/BidHandlerInfoService.java` | 业务编排 |
| ⑥ DTO | `src/main/java/.../dto/BidHandlerInfoCreateRequest.java` | 请求载体 |
| ⑦ Controller | `src/main/java/.../controller/BidHandlerInfoController.java` | HTTP 入口 |

---

## 5. 复制模式：扩展到其他表

按照 BidHandlerInfo 的 7 层结构，**复制**即可实现 BidWinningNotification、BidWinningSupplierDetail：

| 步骤 | 复用点 |
|------|--------|
| ① DB 脚本 | V4 / V5 已有 |
| ② Entity | 已有 `BidWinningNotification` / `BidWinningSupplierDetail` |
| ③ Mapper 接口 | 复制 `BidHandlerInfoMapper.java` 改名 |
| ④ Mapper XML | 复制 `BidHandlerInfoMapper.xml` 改名 + 改 SQL |
| ⑤ Service | 复制 `BidHandlerInfoService.java` 改名 |
| ⑥ DTO | 复制 `BidHandlerInfoCreateRequest.java` 改名 + 调整字段 |
| ⑦ Controller | 复制 `BidHandlerInfoController.java` 改名（注意：现 URL 前缀是 `/bid-handler-info`，复制时按业务改 URL） |

---

## 6. 后续待办

- [ ] 复制模式实现 `BidWinningNotification` 完整链路
- [ ] 复制模式实现 `BidWinningSupplierDetail` 完整链路
- [ ] 新增 `GlobalExceptionHandler` 统一处理 `MethodArgumentNotValidException`
- [ ] 新增 `MybatisPlusConfig`（分页插件 + 自动填充处理器）
- [ ] 单元测试（Mapper / Service / Controller）
- [ ] API 文档（`docs/api.md`）
