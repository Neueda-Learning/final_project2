# Portfolio Manager

## Basic information

### Short description

Portfolio Manager is a full-stack portfolio-management MVP for stocks and ETFs.
It enables users to manage portfolios, record trades, track holdings, synchronize
market data, and review portfolio valuations and analytics.

### Technology

- Frontend: React, TypeScript, and Vite
- Backend: Java 21 and Spring Boot 4.1.0
- Database: MySQL 8
- Market data: Independent Spring-based synchronization worker
- Infrastructure: Docker and Docker Compose
- API documentation: OpenAPI and Swagger UI

### Team

| Member | GitHub | Responsibility |
| --- | --- | --- |
| Kristen | DanielleZhao-tech | All frontend development (PRD 1) |
| Viko | MikeyHHH | User and portfolio backend (PRD 2) |
| Tommy | ziyet3 | Instrument, trade, and holding backend (PRD 3) |
| Jermaine | zqq7695zq | Market-data synchronization backend (PRD 4) |
| Leon | N1rVana96 | Valuation analysis and backend integration (PRD 5) |





## Project structure

```text
frontend/             React and Vite client
backend/              Spring MVC API, domain model, repositories, and services
worker/               Scheduled market-data synchronization process
db/                   Database initialization and seed data
docs/                 Architecture, product requirements, OpenAPI, and schema docs
e2e/                  End-to-end test workspace
infra/                Container and deployment configuration
```

## Start with Docker

Copy the environment file:

```bash
cp .env.example .env
```

The default provider is the live Twelve Data REST API. Configure a real key before
starting:

```text
MARKET_DATA_PROVIDER=twelve-data
TWELVE_DATA_API_KEY=replace_with_your_api_key
```

Then start the complete environment:

```bash
docker compose up --build
```

The `fixture` provider is intended only for automated tests and offline demos. Run
the live provider integration test separately after configuring a key:

```bash
mvn -pl worker -Dtest=TwelveDataLiveIntegrationTest test
```

Service endpoints:

- Frontend: http://localhost:5173
- API: http://localhost:8000
- Swagger UI: http://localhost:8000/docs
- OpenAPI JSON: http://localhost:8000/v3/api-docs

## Start without Docker

Backend:

```bash
mvn -pl backend spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

## Validate

```bash
mvn test
cd frontend
npm test -- --run
npm run lint
npm run build
```

## Project plan and progress

- Project plan document: [PROJECT_PLAN.md](PROJECT_PLAN.md)
- This tracker is updated from repository milestones and delivery progress.

The canonical API contract is [docs/openapi.yaml](docs/openapi.yaml). The database
definition is [docs/database/schema.sql](docs/database/schema.sql). The current
baseline uses Spring Boot 4.1.0 and Java 21.
