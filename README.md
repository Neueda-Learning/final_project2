# Portfolio Manager

面向股票与 ETF 的投资组合管理 MVP。项目采用 React + TypeScript 前端、
Java 21 + Spring Boot 后端、独立 Spring 行情同步 worker 和 MySQL 8 数据库。

## 项目结构

```text
frontend/             React/Vite 客户端
backend/              Spring MVC API、Spring Data JPA 领域模型与服务
worker/               独立 Spring 定时行情同步进程
db/                   数据库初始化和种子数据
docs/                 架构、PRD、OpenAPI 与数据库设计
e2e/                  端到端测试占位
infra/                容器镜像与部署配置
```

## 本地启动

复制环境变量：

```bash
cp .env.example .env
```

使用 Docker Compose 启动完整环境：

```bash
docker compose up --build
```

服务地址：

- 前端：http://localhost:5173
- API：http://localhost:8000
- Swagger：http://localhost:8000/docs
- OpenAPI JSON：http://localhost:8000/v3/api-docs

## 不使用 Docker

后端：

```bash
mvn -pl backend spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

## 校验

```bash
mvn test
cd frontend && npm run lint && npm run build
```

权威接口契约见 [docs/openapi.yaml](docs/openapi.yaml)，数据库设计见
[docs/database/schema.sql](docs/database/schema.sql)。

当前基线使用 Spring Boot 4.1.0 与 Java 21。Swagger UI 位于 `/docs`，
运行时 OpenAPI JSON 位于 `/v3/api-docs`。
