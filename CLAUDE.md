# CLAUDE.md

本文件给在本仓库工作的 AI 助手读。说明 novel 的代码结构、关键约定和常用命令。

## 项目定位

`novel` 是一套基于 **Spring Boot 3 + Vue 3** 的前后端分离学习型小说系统的**后端**单体工程。前端代码在另一个仓库 [`novel-front-web`](https://github.com/201206030/novel-front-web)。

后端是 Maven 项目，单模块（artifactId `novel`，version `3.3.0`，groupId `io.github.xxyopen`），Java 17，跑在 Undertow 上。它同时承担三个子系统的 API：

- **门户（front）**：终端用户浏览/阅读小说
- **作家后台（author）**：作者管理作品、章节、收益
- **平台后台（admin）**：运营管理（控制器目录占位，实际接口尚少）

业务文档（含表结构、教程）在外部：<https://docs.xxyopen.com/course/novel>。本仓库的 [README.md](README.md) 详细列出技术选型表。

## 运行环境

| 组件 | 版本 | 必需 | 说明 |
|------|------|------|------|
| JDK | 17 | ✅ | `pom.xml` 中 `java.version=17` |
| Maven | 3.8+ | ✅ | 已带 Maven Wrapper `./mvnw` |
| MySQL | 8.0 | ✅ | 默认 `novel_test` 库，账号 `root/test123456`（见 `application.yml`） |
| Redis | 7.0 | ✅ | 默认 `127.0.0.1:6379`，密码 `123456` |
| Elasticsearch | 8.2 | ⛔ 可选 | `spring.elasticsearch.enabled=false`，开启走 ES 搜索 |
| RabbitMQ | 3.10 | ⛔ 可选 | `spring.amqp.enabled=false`，开启后 ES / Redis 缓存同步走 MQ |
| XXL-JOB | 2.3.1 | ⛔ 可选 | `xxl.job.enabled=false`，开启走分布式定时任务 |

**默认所有可选中间件都关着**。改 `application.yml` 里对应 `enable*` 字段才能打开，对应代码也会启用：注意 `actuator` 已经显式关掉 mail / rabbit / elasticsearch 的 healthcheck，要开中间件记得放开 healthcheck。

## 常用命令

```bash
# 构建（不跑测试，因为单测依赖数据库）
./mvnw clean package -DskipTests

# 跑单测
./mvnw test

# 本地启动（dev profile，默认）
./mvnw spring-boot:run

# 指定 profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod

# Maven 仓库默认走阿里云镜像（pom.xml 里写死了 repositories.aliyun）
```

应用监听 `:8888`，接口前缀 `/api`，Swagger UI（仅 dev profile 开启）：<http://localhost:8888/swagger-ui.html>。

## 包结构（必须遵守）

包根：`io.github.xxyopen.novel`

```
novel/
├── NovelApplication.java        ← 启动类。@MapperScan / @EnableCaching / @EnableScheduling
│
├── controller/                  ← Http 入口。按子系统分子包
│   ├── front/                   ← 门户 API（/api/front/**）
│   ├── author/                  ← 作家后台 API（/api/author/**）
│   └── admin/                   ← 平台后台 API（/api/admin/**，暂为空）
│
├── service/                     ← 业务逻辑接口
│   └── impl/                    ← 实现类
│
├── manager/                     ← 通用业务能力下沉层（重要！）
│   ├── cache/                   ← 二级缓存（Caffeine + Redis）封装
│   ├── dao/                     ← 复用度高的 DAO 组合
│   ├── message/                 ← 消息发送（模版方法：邮件 / 站内信）
│   ├── mq/                      ← AmqpMsgManager 等
│   └── redis/                   ← VerifyCodeManager 等纯 Redis 操作
│
├── dao/                         ← 数据访问层（MyBatis-Plus）
│   ├── entity/                  ← 数据库实体（与表一一对应）
│   └── mapper/                  ← MyBatis Mapper 接口
│                                  XML 在 src/main/resources/mapper/
│
├── dto/                         ← Http 出入参（不直接暴露 entity）
│   ├── req/                     ← 入参（带 javax.validation）
│   ├── resp/                    ← 出参
│   └── es/                      ← ES 索引文档结构
│
└── core/                        ← 业务无关的通用基础设施
    ├── common/                  ← 跨业务模块都可能用到的东西
    │   ├── constant/            ← ErrorCodeEnum / CommonConsts（错误码 5 位字符串，"00000"=OK）
    │   ├── exception/           ← BusinessException / CommonExceptionHandler
    │   ├── req/                 ← PageReqDto
    │   ├── resp/                ← RestResp（统一响应）/ PageRespDto
    │   └── util/                ← 与具体业务无关的工具（IP、图形验证码）
    ├── annotation/              ← @Lock / @Key（分布式锁）
    ├── aspect/                  ← LockAspect（@Lock 切面）
    ├── auth/                    ← AuthStrategy 策略（front/author/admin 三系统）+ UserHolder
    ├── config/                  ← 各种 @Configuration（Cors / Cache / Mybatis / OpenApi / WebMvc / Es / Amqp / Xxl-Job）
    ├── constant/                ← 业务相关常量（ApiRouter / Cache / Database / Amqp 等）
    ├── filter/                  ← XssFilter
    ├── interceptor/             ← AuthInterceptor / FlowLimitInterceptor / TokenParseInterceptor / FileInterceptor
    ├── json/                    ← 自定义 Jackson 序列化器
    ├── listener/                ← RabbitQueueListener
    ├── task/                    ← @Scheduled 定时任务 / XXL-Job 任务
    ├── util/                    ← JwtUtils 等业务相关工具
    └── wrapper/                 ← HttpServletRequestWrapper 装饰器
```

**关键分层约定**：

- `controller` 只做参数校验和转发，**不写业务**
- `service` 实现具体业务流程
- `manager` 是 service 之间复用的下沉层（缓存、消息、第三方平台封装）；service **可以**调用 manager，manager **不能**反向调用 service
- `dao` 只跟数据库打交道，不掺业务

不要绕开这个分层（比如 controller 直接调 mapper）。

## 统一 Http 响应

所有 controller 返回 `RestResp<T>`（`core/common/resp/RestResp.java`）：

```java
return RestResp.ok();          // 成功，无数据
return RestResp.ok(data);      // 成功，带数据
return RestResp.fail(ErrorCodeEnum.USER_LOGIN_EXPIRED);  // 业务失败
// 系统异常由 CommonExceptionHandler 自动处理
```

**抛业务异常**用 `BusinessException(ErrorCodeEnum)`，全局异常处理器会转成 `RestResp.fail`。新错误码加到 `ErrorCodeEnum` 枚举里，**不要新建枚举**。

## 认证授权

策略模式 + 拦截器：

1. **Bean 名称约定**：`FrontAuthStrategy` / `AuthorAuthStrategy` / `AdminAuthStrategy`，对应路径 `/api/front/**` / `/api/author/**` / `/api/admin/**`
2. `AuthInterceptor` 从 URI 中切出子系统名，拼 `"{系统}AuthStrategy"` 从 `Map<String, AuthStrategy>` 里取策略
3. JWT 在 Header `Authorization`（见 `SystemConfigConsts.HTTP_AUTH_HEADER_NAME`），密钥在 `application.yml` 的 `novel.jwt.secret`
4. 当前线程的用户 ID 用 `UserHolder` 存取（ThreadLocal），`AuthInterceptor.afterCompletion` 会清理

**放行接口**：在 `WebConfig.addInterceptors` 里 `excludePathPatterns`（如登录、注册）。**新增受保护接口**：放在 `/api/{front|author|admin}/**` 路径下即可自动被拦截。

## 缓存

- **二级缓存**：方法上加 `@Cacheable(cacheNames = CacheConsts.XXX_CACHE_NAME, cacheManager = CacheConsts.CAFFEINE_CACHE_MANAGER)` 或 `REDIS_CACHE_MANAGER`
- **缓存键前缀**：Redis 统一 `Cache::Novel::`（见 `CacheConsts.REDIS_CACHE_PREFIX`）
- **缓存写在 `manager/cache/`**：service 调用 `*CacheManager`，缓存管理器负责"取不到就回源 DB"
- 缓存配置在 `core/config/CacheConfig.java`

## 数据库 & 分库分表

- ORM：MyBatis-Plus 3.5.3。Mapper 接口在 `dao/mapper`，对应 XML 在 `resources/mapper/`
- 实体类用 `@TableName` 显式指定表名（约定从 Generator 生成）
- 代码生成器：`src/test/java/io/github/xxyopen/novel/generator/Generator.java`。改里面的 `genCode("表名")` 然后跑这一个 main 方法
- **分表**：默认关闭。开 `spring.shardingsphere.enabled=true` 后 `book_content` 按 `chapter_id % 10` 分到 `book_content0..book_content9`。改分片规则在 `application.yml` 而不是代码里

## 消息发送（模版方法）

`manager/message/`：

- `AbstractMessageSender` 是模板，子类只实现"如何渲染标题/内容/发送"
- 已有：`AbstractMailSender`（邮件）/ `AbstractSysNoticeSender`（站内信）
- 具体业务消息：`RegisterMailSender`、`SeckillSystemNoticeSender` 等

加新消息：选合适的抽象基类继承，**不要直接 implements MessageSender**。

## 异步与 MQ

- 默认关闭。开 `spring.amqp.enabled=true` 后，小说信息变更走 `AmqpConsts.BookChangeMq` 交换机
  - `QUEUE_ES_UPDATE` → ES 索引更新
  - `QUEUE_REDIS_UPDATE` → Redis 缓存失效
- 消费者在 `core/listener/RabbitQueueListener.java`
- 生产者用 `manager/mq/AmqpMsgManager`

## 定时任务

- `@Scheduled`：写在 `core/task/`，启动类 `@EnableScheduling` 已开
- XXL-Job：默认关闭。开 `xxl.job.enabled=true` 后任务也在 `core/task/`，用 `@XxlJob` 注解

## 安全要点（不要踩）

- **不直接序列化 entity** 给前端，必经 `dto/resp/`
- **数字字段全部以字符串返回**（`spring.jackson.generator.write-numbers-as-strings=true`），因 JS 64 位整数精度丢失
- XSS 过滤在 `XssFilter`，新加白名单路径走 `novel.xss.excludes`
- Actuator 端点已用 Spring Security 保护，账号在 `application.yml` 的 `spring.security.user`（生产环境必须改）
- `application.yml` 里现有的账号密码、邮箱授权码、ES 凭据**都是示例值**，不要复用到生产

## 编码风格

- 阿里编码规约（仓库 README 有声明）
- 类头注释固定模板：`@author xiongxiaoyang` + `@date YYYY/MM/DD`
- Lombok：`@RequiredArgsConstructor` + `final` 字段做构造器注入（看 controller / service / interceptor 的标准写法）
- 不写字段注入（`@Autowired private XxxService`），统一构造器注入
- 4 空格缩进；Java 文件 UTF-8；行尾 LF
- 异常：业务异常用 `BusinessException`，系统异常让全局处理器处理，不要在 controller / service 里写 `try { ... } catch (Exception e) { return null; }` 这种吞异常的代码

## 提交规范

照搬已有 commit 风格（Conventional Commits 风格，但允许中文 description）：

```
feat: 模版方法模式实现消息发送器
fix(interceptor): handler 执行异常时没有清理掉当前线程保存的用户登录信息
build: 升级 Spring Boot 版本
docs: 调整注释的位置
perf: 仅在 dev 环境生成接口文档
chore: 关闭 mail 的健康检查
```

类型前缀：`feat / fix / build / perf / chore / docs / refactor / test`，可选 scope。

## 分支

- 默认分支：`master`
- 远程：`origin` → `201206030/novel`
- 本次 web session 工作在 `claude/claude-md-docs-*` 这种功能分支上，不直接推 master
