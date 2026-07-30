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
| Kristen | DanielleZhao-tech, kristen | All frontend development (PRD 1) |
| Viko | MikeyHHH | User and portfolio backend (PRD 2) |
| Tommy | ziyet3, ZiyeTang | Instrument, trade, and holding backend (PRD 3) |
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

The default provider is Alpaca Market Data, using paper-account credentials for
authentication. Twelve Data remains the automatic fallback:

```text
MARKET_DATA_PROVIDER=alpaca
ALPACA_API_BASE_URL=https://paper-api.alpaca.markets/v2
ALPACA_DATA_BASE_URL=https://data.alpaca.markets/v2
ALPACA_API_KEY_ID=replace_with_your_key_id
ALPACA_API_SECRET_KEY=replace_with_your_secret
TWELVE_DATA_API_KEY=replace_with_your_fallback_key
```

The default worker uses Alpaca multi-symbol batches of 50, four concurrent
network tasks, the free IEX feed, and a client-side ceiling of 180 requests per
minute. Alpaca errors and missing symbols fall back to single-symbol Twelve Data
requests spaced eight seconds apart. Set `MARKET_DATA_PROVIDER=twelve-data` to
run Twelve Data directly or `MARKET_DATA_PROVIDER=fixture` for offline demos.

Then start the complete environment:

```bash
docker compose up --build
```

Run the live Twelve Data integration test separately after configuring its key:

```bash
mvn -pl worker -Dtest=TwelveDataLiveIntegrationTest test
```

Service endpoints:

- Frontend: http://localhost:5173
- API: http://localhost:8000
- Swagger UI: http://localhost:8000/docs
- OpenAPI JSON: http://localhost:8000/v3/api-docs

## Start without Docker

Create and configure local environment variables first:

```bash
cp .env.example .env
```

Set the local database credentials in `.env` (for example `DATABASE_TARGET=LOCAL`,
`FLYWAY_ENABLED=true`, and matching `DATABASE_LOCAL_*` values).

Run API and worker in separate terminals so manual sync requests can be processed:

Backend:

```bash
mvn -pl backend spring-boot:run
```

Worker:

```bash
mvn -pl worker spring-boot:run
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

The real-provider synchronization and endpoint comparison is documented in
[docs/MARKET_DATA_PROVIDER_BENCHMARK.md](docs/MARKET_DATA_PROVIDER_BENCHMARK.md).
