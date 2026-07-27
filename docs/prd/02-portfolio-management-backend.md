<!-- generated-by: gsd-doc-writer -->

# PRD 2：用户与投资组合后端模块

## 1. 分工信息

| 项目 | 内容 |
|---|---|
| 主负责人 | 成员 2（提交前替换为姓名） |
| 负责范围 | 用户上下文、组合 API、MySQL、后端测试、API 文档公共配置 |
| 主要数据表 | `app_user`、`portfolio` |
| 不负责 | 前端页面、交易、行情和估值实现 |
| 接口依据 | [API 接口说明](../API.md) / [OpenAPI 规范](../openapi.yaml) |
| 状态 | 待实现 |

## 2. 模块目标

提供当前演示用户上下文和组合 CRUD，为其他三个后端模块提供统一的组合所有权边界；同时建立 Spring Boot 应用、统一错误体、Swagger UI 和 OpenAPI 导出。

## 3. 功能范围

- 预置演示用户与当前用户依赖。
- 查询、创建、获取、修改、删除空组合和归档组合。
- 用户所有权过滤。
- 活跃组合名称唯一。
- Spring Boot 应用、Spring MVC Controller、Springdoc 元数据和接口文档 URL。
- 统一 `ErrorResponse` 与 request ID。

## 4. 接口

| 方法 | 路径 | 成功状态 |
|---|---|---:|
| GET | `/api/v1/portfolios` | 200 |
| POST | `/api/v1/portfolios` | 201 |
| GET | `/api/v1/portfolios/{portfolioId}` | 200 |
| PATCH | `/api/v1/portfolios/{portfolioId}` | 200 |
| DELETE | `/api/v1/portfolios/{portfolioId}` | 204 |
| POST | `/api/v1/portfolios/{portfolioId}/archive` | 200 |

每个接口的请求、响应、错误和 curl 样例见 [API.md](../API.md)。实现必须与 [openapi.yaml](../openapi.yaml) 一致。

## 5. 业务规则

- 当前用户从服务端请求上下文获取，不接受客户端指定任意用户 ID。
- MVP 组合基准币种固定为 USD。
- 活跃组合名称在同一用户范围内不区分大小写唯一。
- 不属于当前用户的组合统一返回 404。
- 有交易历史的组合不能硬删除，只能归档。
- 归档组合默认不出现在列表。

## 6. MySQL 交付

- `app_user`
- `portfolio`
- 用户外键和查询索引
- 生成列 `active_name`
- 用户范围的活跃名称唯一键

数据库测试必须证明：

- 邮箱唯一且不能为空。
- 组合必须属于有效用户。
- 同一用户不能有两个同名活跃组合。
- 归档后可以创建同名新组合。
- 不同用户可以使用相同名称。

## 7. API 文档配置责任

成员 2 负责配置：

- Swagger UI：`/docs`
- OpenAPI JSON：`/v3/api-docs`
- API 标题、版本、描述与标签顺序
- 请求体 examples、响应 examples 和额外错误响应

Spring MVC 接口必须声明：

- 请求/响应 Java record DTO，禁止直接暴露 JPA 实体
- `@Valid` 与 Bean Validation 字段约束
- 正确的 HTTP 状态码
- Springdoc `@Operation`、`@ApiResponse`、`@Schema` 和 examples
- `@RestControllerAdvice` 统一错误响应

成员 3–5 各自维护其接口 schema 和 examples；成员 2 负责全量 OpenAPI 可生成且可访问。

## 8. 后端测试

### 领域/API 测试

- 组合 CRUD。
- 名称冲突和字段验证。
- 用户隔离与 404。
- 有历史组合删除失败。
- 归档后默认列表过滤。

### 数据库测试

- 外键、唯一键和生成列。
- MySQL 迁移从空库执行。
- 并发创建同名组合时只允许一条成功。

### 接口文档测试

- `/v3/api-docs` 返回 200。
- `/docs` 可访问。
- 所有业务路由都有 tag、成功响应和标准错误响应。
- OpenAPI schema 与仓库 [openapi.yaml](../openapi.yaml) 进行差异检查。

## 9. 验收标准

### AC-PM-01：创建组合

有效请求返回 201，响应符合 `Portfolio` schema，MySQL 写入正确用户。

### AC-PM-02：名称冲突

创建大小写不同但同名的活跃组合时，MySQL 拒绝重复，API 返回 409 `PORTFOLIO_NAME_CONFLICT`。

### AC-PM-03：用户隔离

其他用户访问组合时返回 404，不泄露资源信息。

### AC-PM-04：接口文档

Swagger UI 中可以看到请求和成功/错误响应样例，并可对开发环境接口执行 Try it out。

## 10. 交接

- 向成员 1 提供组合接口与 mock examples。
- 向成员 3–5 提供 `portfolioId` 所有权校验依赖。
- 负责合并四名后端成员的 OpenAPI tags 和错误 schema。

## 11. 完成定义

- 组合接口、MySQL 迁移和测试全部通过。
- Swagger、ReDoc、OpenAPI JSON 均可访问。
- API.md、openapi.yaml 与实际路由一致。
